package com.nitro.tvplayer.utils

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.nitro.tvplayer.R

fun View.visible()   { visibility = View.VISIBLE }
fun View.gone()      { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun Context.toast(msg: String) =
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun ImageView.loadUrl(url: String?) {
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.placeholder_channel)
        .error(R.drawable.placeholder_channel)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(this)
}

fun String.formatServerUrl(): String {
    var clean = trim()
    if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
        clean = "http://$clean"
    }
    if (clean.endsWith("/")) clean = clean.dropLast(1)
    return clean
}

fun String.buildStreamUrl(username: String, password: String, streamId: Int, ext: String = "ts"): String {
    return "$this/$username/$password/$streamId.$ext"
}
