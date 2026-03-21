package com.nitro.tvplayer.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions

/**
 * Load an image URL into this ImageView.
 * If the URL is blank or fails to load, shows a letter avatar using [fallbackText].
 * Does NOT use RequestListener to avoid Glide version signature conflicts.
 */
fun ImageView.loadUrl(url: String?, fallbackText: String = "") {
    if (url.isNullOrBlank()) {
        if (fallbackText.isNotBlank()) setLetterPlaceholder(fallbackText)
        else setImageDrawable(null)
        return
    }

    val placeholder: Drawable? = if (fallbackText.isNotBlank()) makeLetterDrawable(fallbackText) else null

    Glide.with(context)
        .load(url)
        .apply(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(placeholder)
                .error(placeholder)
        )
        .into(this)
}

/** Convenience overload — same as loadUrl but name is explicit */
fun ImageView.loadIcon(url: String?, fallbackText: String = "") = loadUrl(url, fallbackText)

private fun ImageView.setLetterPlaceholder(name: String) {
    setImageBitmap(makeLetterBitmap(name))
}

private fun makeLetterDrawable(name: String): android.graphics.drawable.BitmapDrawable? {
    return try {
        android.graphics.drawable.BitmapDrawable(
            android.content.res.Resources.getSystem(),
            makeLetterBitmap(name)
        )
    } catch (e: Exception) { null }
}

private fun makeLetterBitmap(name: String): Bitmap {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val size   = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val colors = listOf(
        0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(),
        0xFFC62828.toInt(), 0xFF00695C.toInt(), 0xFF4527A0.toInt(),
        0xFF283593.toInt(), 0xFF558B2F.toInt(), 0xFF0277BD.toInt()
    )
    val bgColor = colors[Math.abs(name.hashCode()) % colors.size]
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textSize  = size * 0.45f
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(letter, size / 2f, textY, textPaint)
    return bitmap
}

fun View.visible()   { visibility = View.VISIBLE }
fun View.gone()      { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
