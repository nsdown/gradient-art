package me.krrr.wallpaper

import android.graphics._
import android.graphics.drawable.GradientDrawable

import scala.util.Random


class GradientArtDrawable {
    // simple Drawable that only support draw method, no setBounds
    private var gd = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        Array(0xFFE6DADA, 0xFF274046))  // named "Metal", for default
    private var _degree = 30f
    private var cache: Bitmap = null

    import GradientArtDrawable.Filter._
    var filter = NO_FILTER

    def degree = _degree
    def degree_=(deg: Float) = {
        if (!(0 <= deg && deg <= 90))
            throw new Exception("Invalid degree")
        _degree = deg
        emptyCache()
    }

    def draw(canvas: Canvas) =
        if (cache != null && cache.getWidth == canvas.getWidth &&
                cache.getHeight == canvas.getHeight) {
            drawCache(canvas)
        } else {
            cache = Bitmap.createBitmap(canvas.getWidth,
                canvas.getHeight, Bitmap.Config.ARGB_8888)
            canvas.save()
            filter match {
                case NO_FILTER =>
                    drawRotateGra(new Canvas(cache))
                case TAQUIN =>
                    val temp = Bitmap.createBitmap(canvas.getWidth,
                        canvas.getHeight, Bitmap.Config.ARGB_8888)
                    drawRotateGra(new Canvas(temp))
                    taquin(temp, new Canvas(cache))
                    // temp will not be gc before it be created again without the two calls
                    temp.recycle()
                    System.gc()
            }
            canvas.restore()
            drawCache(canvas)
        }

    def emptyCache() =
        cache = null

    private def drawCache(canvas: Canvas) {
        val rect = new Rect(0, 0, cache.getWidth, cache.getHeight)
        canvas.drawBitmap(cache, rect, rect, null)
    }

    private def drawRotateGra(canvas: Canvas) {
        val (w, h) = (canvas.getWidth, canvas.getHeight)
        val _deg = _degree / 180.0 * math.Pi
        val (deg_sin, deg_cos) = (math.sin(_deg), math.cos(_deg))
        val new_w = deg_sin * h + deg_cos * w
        val new_h = deg_cos * h + deg_sin * w

        canvas.translate((deg_sin * deg_sin * w).toFloat,
                         -(deg_sin * deg_cos * w).toFloat)
        canvas.rotate(_degree)
        gd.setBounds(0, 0, new_w.toInt, new_h.toInt)
        gd.draw(canvas)
    }

    private def taquin(src: Bitmap, dst: Canvas) = {
        val n = 7
        val (block_w, block_h) = (src.getWidth.toFloat / n, src.getHeight.toFloat / n)
        val paint = new Paint
        val (src_rect, dst_rect) = (new Rect, new Rect)
        val border_rect = new Rect(0, 0, block_w.toInt, block_h.toInt)
        val border_w = Array(block_w, block_h).min * 0.04f

        val border_bmp = {
            val bmp = Bitmap.createBitmap(block_w.toInt, block_h.toInt,
                Bitmap.Config.ARGB_8888)
            val c = new Canvas(bmp)
            // use path to draw parallelogram, light part first.
            // there should be simpler way to do this...
            paint.setARGB(30, 255, 255, 255)
            val path = new Path
            path.lineTo(block_w, 0)
            path.lineTo(block_w, block_h*0.7f)
            path.lineTo(block_w-border_w, block_h*0.7f-border_w)
            path.lineTo(block_w-border_w, border_w)
            path.lineTo(border_w, border_w)
            path.moveTo(0, 0)
            c.drawPath(path, paint)
            path.reset()
            // dark part
            paint.setARGB(30, 0, 0, 0)
            path.lineTo(0, block_h)
            path.lineTo(block_w, block_h)
            path.lineTo(block_w, block_h*0.7f)
            path.lineTo(block_w-border_w, block_h*0.7f-border_w)
            path.lineTo(block_w-border_w, block_h-border_w)
            path.lineTo(border_w, block_h-border_w)
            path.lineTo(border_w, border_w)
            path.lineTo(0, 0)
            c.drawPath(path, paint)
            paint.reset()
            bmp
        }

        for (dst_i <- 0 until n * n) {
            // copy block
            val src_i = Random.nextInt(n * n)  // random from [0, n**2]
            val (src_x, src_y) = ((src_i % n) * block_w, src_i / n * block_h)
            val (dst_x, dst_y) = ((dst_i % n) * block_w, dst_i / n * block_h)
            src_rect.set(src_x.toInt, src_y.toInt, (src_x+block_w).toInt, (src_y+block_h).toInt)
            dst_rect.set(dst_x.toInt, dst_y.toInt, (dst_x+block_w).toInt, (dst_y+block_h).toInt)
            dst.drawBitmap(src, src_rect, dst_rect, paint)
            // draw border
            dst.drawBitmap(border_bmp, border_rect, dst_rect, paint)
        }
    }

    private def colorStrToInt(s: String): Int =
        // eg. convert "#FFCCFF" to 0xFFFFCCFF (argb)
        Integer.parseInt(s.drop(1), 16) + 0xFF000000

    def setColor(color: String) = {
        gd.setColor(colorStrToInt(color))
        emptyCache()
    }

    def setColors(colors: Array[String]) = {
        gd = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            colors.map(colorStrToInt))
        emptyCache()
    }
}


object GradientArtDrawable {
    object Filter extends Enumeration {
        val NO_FILTER, TAQUIN = Value
    }
}
