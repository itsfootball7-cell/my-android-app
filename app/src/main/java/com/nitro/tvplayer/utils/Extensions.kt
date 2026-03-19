package com.nitro.tvplayer.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable

/**
 * Load image URL with automatic letter-placeholder fallback.
 * If image fails, shows the first letter of [fallbackText] on a colored background.
 */
fun ImageView.loadUrl(url: String?, fallbackText: String = "") {
    if (url.isNullOrBlank()) {
        if (fallbackText.isNotBlank()) setLetterPlaceholder(fallbackText)
        return
    }
    Glide.with(context)
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?,
                target: Target<Drawable>?, isFirstResource: Boolean
            ): Boolean {
                if (fallbackText.isNotBlank()) setLetterPlaceholder(fallbackText)
                return true
            }
            override fun onResourceReady(
                resource: Drawable?, model: Any?,
                target: Target<Drawable>?, dataSource: DataSource?,
                isFirstResource: Boolean
            ) = false
        })
        .into(this)
}

/** Creates a bitmap with the first letter of [name] centered on a colored circle */
private fun ImageView.setLetterPlaceholder(name: String) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val size   = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background color derived from name hash
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

    setImageBitmap(bitmap)
}

fun View.visible() { visibility = View.VISIBLE }
fun View.gone()    { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
