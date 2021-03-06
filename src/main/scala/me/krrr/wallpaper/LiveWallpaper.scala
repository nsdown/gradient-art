package me.krrr.wallpaper

import android.content.{SharedPreferences, Context}
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.drawable.{BitmapDrawable, TransitionDrawable}
import android.graphics.{Canvas, Bitmap, PixelFormat}
import android.os.{SystemClock, Handler}
import android.preference.PreferenceManager
import android.service.wallpaper.WallpaperService
import android.util.{Log, DisplayMetrics}
import android.view.View.MeasureSpec
import android.view.{WindowManager, LayoutInflater, SurfaceHolder}
import android.widget.TextView
import org.json.{JSONArray, JSONException}
import GradientArtDrawable.Filter

import scala.util.Random


class LiveWallpaper extends WallpaperService {
    private val handler = new Handler

    def onCreateEngine() = new GraEngine

    class GraEngine extends Engine with OnSharedPreferenceChangeListener {
        val aniDuration = 1000
        private val pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext)
        private val changeTask = new Runnable {
            def run() = { doDrawingAnimated(); scheduleChangeTask() }
        }
        private var changeTaskNextRun = -1L
        private val (view, nameLabel, subLabel, gra) = {
            // inflate view and set text shadows
            val view = LayoutInflater.from(
                getApplicationContext).inflate(R.layout.main, null)
            val nameLabel = view.findViewById(R.id.gra_name).asInstanceOf[TextView]
            val subLabel = view.findViewById(R.id.gra_subname).asInstanceOf[TextView]
            val gra = view.findViewById(R.id.gra_view).asInstanceOf[GradientArtView].gra

            val metrics = new DisplayMetrics
            val wm = getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager]
            wm.getDefaultDisplay.getMetrics(metrics)
            val radius = metrics.density * 2
            Log.d("LWPService", "Shadow radius: " + radius)
            nameLabel.setShadowLayer(radius, 0, 0, 0xEE111111)
            subLabel.setShadowLayer(radius, 0, 0, 0xEE222222)

            (view, nameLabel, subLabel, gra)
        }
        val patterns = {
            val i_stream = getResources.openRawResource(R.raw.uigradients)
            val json_s = io.Source.fromInputStream(i_stream).mkString
            try new JSONArray(json_s) catch { case e: JSONException => null }
        }

        private def scheduleChangeTask(delay: Long = -1) {
            val _delay = if (delay == -1) pref.getString("period", "1800000").toLong else delay
            handler.postDelayed(changeTask, _delay)
            changeTaskNextRun = System.currentTimeMillis() + _delay
        }

        override def onSurfaceCreated(holder: SurfaceHolder) {
            holder.setFormat(PixelFormat.RGBA_8888)
            pref.registerOnSharedPreferenceChangeListener(this)
            // for some unknown reasons, onVisibilityChanged will be called three
            // times initially: show, hide, show. So readSettings first, then
            // first draw will be done in onSurfaceChanged
            fromSettings()
        }

        override def onSurfaceChanged(holder: SurfaceHolder, format: Int,
                                      width: Int, height: Int) {
            layoutView(width, height)
            doDrawing()
        }

        override def onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(changeTask)
            pref.unregisterOnSharedPreferenceChangeListener(this)
        }

        override def onVisibilityChanged(visible: Boolean) = visible match {
            case true =>
                if (changeTaskNextRun > 0) {
                    val diff = changeTaskNextRun - System.currentTimeMillis
                    if (diff >= 0) {
                        scheduleChangeTask(diff)
                    } else {
                        val period = pref.getString("period", "1800000").toLong
                        val nextAniDelay = period - System.currentTimeMillis % period
                        if (nextAniDelay < aniDuration) doDrawing()  // avoid two animations overlap
                        else doDrawingAnimated()
                        scheduleChangeTask(nextAniDelay)
                    }
                } else
                    scheduleChangeTask()
            case false =>
                handler.removeCallbacks(changeTask)
        }

        private def layoutView(w: Int, h: Int) {
            view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
        }

        // no need to call fromSettings before call this
        def doDrawingAnimated() {
            val (w, h) = (view.getWidth, view.getHeight)
            val before = Bitmap.createBitmap( w,h, Bitmap.Config.ARGB_8888)
            val after = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(new Canvas(before))
            fromSettings()
            view.draw(new Canvas(after))
            val a = new BitmapDrawable(getResources, before)
            val b = new BitmapDrawable(getResources, after)
            val td = new TransitionDrawable(Array(a, b))
            Array(a, b, td).foreach(_.setBounds(0, 0, w, h))
            td.startTransition(aniDuration)

            val startTime = SystemClock.uptimeMillis + 20  // +20 for unavoidable delay
            new Runnable {
                def run() {
                    if (SystemClock.uptimeMillis - startTime <= aniDuration) {
                        handler.postDelayed(this, 20)  // 50FPS
                        val holder = getSurfaceHolder
                        val canvas = holder.lockCanvas()  // assume it's not null
                        td.draw(canvas)
                        holder.unlockCanvasAndPost(canvas)
                    } else {
                        Array(before, after).foreach(_.recycle())
                        System.gc()
                    }
                }
            }.run()
        }

        def doDrawing() {
            val holder = getSurfaceHolder
            val canvas = holder.lockCanvas()  // assume it's not null
            view.draw(canvas)
            holder.unlockCanvasAndPost(canvas)
        }

        // Select a color randomly, set gradientDrawable and TextViews
        def fromSettings() {
            val idx = pref.getString("filter", "0").toInt
            gra.filter = Filter(if (idx == -1) Random.nextInt(Filter.maxId) else idx)

            try {
                val entry = patterns.getJSONObject(Random.nextInt(patterns.length))
                if (entry.has("color"))
                    gra.setColor(entry.getString("color"))
                else
                    gra.setColors(Array("color1", "color2").map(entry.getString))

                var (name, subName) = ("", "")
                if (pref.getBoolean("show_name", true)) {
                    name = entry.getString("name")
                    subName = if (entry.has("sub_name")) entry.getString("sub_name") else ""
                }
                nameLabel.setText(name)
                subLabel.setText(subName)
            } catch {
                case e@(_: JSONException | _: NullPointerException) =>
                    nameLabel.setText("Failed to parse JSON")
                    subLabel.setText(e.toString)
            }
            layoutView(view.getWidth, view.getHeight)
        }

        def onSharedPreferenceChanged(pref: SharedPreferences, key: String) = key match {
            case "period" =>
                handler.removeCallbacks(changeTask)
                scheduleChangeTask()
            case _ =>
                // assume wallpaper is invisible now
                // force animated redrawing after become visible
                changeTaskNextRun = 1
        }
    }

}
