package com.megasmsrouter.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun hostOf(url: String): String = url.trim().toHttpUrlOrNull()?.host ?: "-"

fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date(millis))
