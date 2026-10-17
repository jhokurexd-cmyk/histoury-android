package com.histoury.app.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max

/**
 * Draws marker pictures at runtime, so no image files are needed and every
 * colour follows the app theme. Each distinct [MarkerIcon] value becomes one
 * style image, registered once per style under [key].
 */
internal object MarkerIcons {

    fun key(icon: MarkerIcon): String = when (icon) {
        is MarkerIcon.Pin -> "histoury-pin-${Integer.toHexString(icon.color)}"
        is MarkerIcon.NumberedPin -> "histoury-num-${icon.number}-${Integer.toHexString(icon.color)}"
        is MarkerIcon.Arrow -> "histoury-arrow-${Integer.toHexString(icon.color)}"
        is MarkerIcon.Photo -> "histoury-photo-${Integer.toHexString(icon.url.hashCode())}-${Integer.toHexString(icon.color)}"
    }

    /** True for icons whose anchor is their centre rather than a bottom tip. */
    fun isCentered(icon: MarkerIcon): Boolean = icon is MarkerIcon.Arrow

    fun bitmap(icon: MarkerIcon, density: Float): Bitmap = when (icon) {
        is MarkerIcon.Pin -> teardropPin(icon.color, density)
        // Same pixel sizes as the Google version of these icons, which were
        // drawn in raw pixels.
        is MarkerIcon.NumberedPin -> numberedPin(icon.number, icon.color)
        is MarkerIcon.Arrow -> navigationArrow(icon.color)
        // The placeholder; HistouryMapState swaps in the real photo once loaded.
        is MarkerIcon.Photo -> photoPin(null, icon.color, density)
    }

    /** Diameter of a photo pin's circle, in dp. Photos are requested at this size. */
    const val PHOTO_PIN_DP = 46f

    /**
     * A round site photo: coloured outer ring, thin white ring, the photo
     * centre-cropped inside, and a short pointer whose tip marks the spot.
     * With no [photo] it draws a soft tinted placeholder of the same shape,
     * so the pin doesn't jump when the photo arrives.
     */
    fun photoPin(photo: Bitmap?, fillColor: Int, density: Float): Bitmap {

        val pad = 2f * density                 // room for the shadow
        val diameter = PHOTO_PIN_DP * density
        val tail = 9f * density
        val radius = diameter / 2f

        val width = ceil(diameter + pad * 2).toInt()
        val height = ceil(pad + diameter + tail).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cx = width / 2f
        val cy = pad + radius
        val tipY = height.toFloat()

        val outline = Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
            moveTo(cx - radius * 0.36f, cy + radius * 0.88f)
            lineTo(cx, tipY)
            lineTo(cx + radius * 0.36f, cy + radius * 0.88f)
            close()
        }

        // Soft shadow so the pin lifts off light and dark maps alike.
        paint.color = Color.argb(70, 0, 0, 0)
        canvas.save()
        canvas.translate(0f, density)
        canvas.drawCircle(cx, cy, radius + density * 0.5f, paint)
        canvas.restore()

        // Coloured ring + pointer, then a white ring inside it.
        paint.color = fillColor
        canvas.drawPath(outline, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius - 2.5f * density, paint)

        val photoRadius = radius - 4.5f * density
        if (photo != null && photo.width > 0 && photo.height > 0) {
            // Centre-crop: scale so the shorter side fills the circle.
            val scale = max(photoRadius * 2f / photo.width, photoRadius * 2f / photo.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - photo.width * scale / 2f, cy - photo.height * scale / 2f)
            }
            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
            paint.isFilterBitmap = true
            canvas.drawCircle(cx, cy, photoRadius, paint)
            paint.shader = null
        } else {
            // Placeholder: a light tint of the pin colour with a small dot.
            paint.color = blend(fillColor, Color.WHITE, 0.82f)
            canvas.drawCircle(cx, cy, photoRadius, paint)
            paint.color = fillColor
            canvas.drawCircle(cx, cy, photoRadius * 0.26f, paint)
        }

        return bitmap
    }

    /** Mixes [a] toward [b] by [t] (0 = all a, 1 = all b). */
    private fun blend(a: Int, b: Int, t: Float): Int {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            mix(Color.red(a), Color.red(b)),
            mix(Color.green(a), Color.green(b)),
            mix(Color.blue(a), Color.blue(b))
        )
    }

    /** A classic map pin: round head, pointed tip, white centre dot. */
    private fun teardropPin(fillColor: Int, density: Float): Bitmap {

        val width = (30 * density).toInt().coerceAtLeast(24)
        val height = (42 * density).toInt().coerceAtLeast(34)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val radius = width / 2f - density
        val cx = width / 2f
        val cy = radius + density

        val outline = Path().apply {
            // Head
            addCircle(cx, cy, radius, Path.Direction.CW)
            // Tip
            moveTo(cx - radius * 0.62f, cy + radius * 0.72f)
            lineTo(cx, height.toFloat() - density)
            lineTo(cx + radius * 0.62f, cy + radius * 0.72f)
            close()
        }

        // Thin dark edge so the pin reads on light and dark maps alike.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(70, 0, 0, 0)
        canvas.save()
        canvas.translate(0f, density * 0.6f)
        canvas.drawPath(outline, paint)
        canvas.restore()

        paint.color = fillColor
        canvas.drawPath(outline, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * 0.38f, paint)

        return bitmap
    }

    /** Matches the itinerary's numbered pins on the Google map. */
    private fun numberedPin(number: Int, fillColor: Int): Bitmap {

        val diameter = 84
        val pointerHeight = 26
        val ring = 7f

        val bitmap = Bitmap.createBitmap(diameter, diameter + pointerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = diameter / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = fillColor
        val pointer = Path().apply {
            moveTo(center - 15f, diameter - 12f)
            lineTo(center + 15f, diameter - 12f)
            lineTo(center, (diameter + pointerHeight).toFloat())
            close()
        }
        canvas.drawPath(pointer, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center, paint)

        paint.color = fillColor
        canvas.drawCircle(center, center, center - ring, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(number.toString(), center, center - textOffset, textPaint)

        return bitmap
    }

    /** Matches the navigation arrow on the Google map. Points "up"; rotated by the layer. */
    private fun navigationArrow(fillColor: Int): Bitmap {

        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f

        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(center, center + 2f, 40f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, 38f, paint)

        paint.color = fillColor
        canvas.drawCircle(center, center, 32f, paint)

        val arrow = Path().apply {
            moveTo(center, center - 20f)
            lineTo(center + 15f, center + 16f)
            lineTo(center, center + 8f)
            lineTo(center - 15f, center + 16f)
            close()
        }
        paint.color = Color.WHITE
        canvas.drawPath(arrow, paint)

        return bitmap
    }
}
