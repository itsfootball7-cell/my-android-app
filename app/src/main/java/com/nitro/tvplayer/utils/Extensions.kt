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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

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
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>?,    // nullable — matches Glide 4.16
                isFirstResource: Boolean
            ): Boolean {
                if (fallbackText.isNotBlank()) setLetterPlaceholder(fallbackText)
                return true
            }

            override fun onResourceReady(
                resource: Drawable?,          // nullable — matches Glide 4.16
                model: Any?,
                target: Target<Drawable>?,    // nullable
                dataSource: DataSource?,      // nullable
                isFirstResource: Boolean
            ): Boolean = false
        })
        .into(this)
}

private fun ImageView.setLetterPlaceholder(name: String) {
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
    setImageBitmap(bitmap)
}

fun View.visible()   { visibility = View.VISIBLE }
fun View.gone()      { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
