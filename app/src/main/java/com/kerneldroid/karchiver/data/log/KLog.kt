package com.kerneldroid.karchiver.data.log

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KLog {

    private const val MAX_LINES = 4000

    private val lines = ArrayDeque<String>()
    private val lock = Any()
    private val stamp = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun d(tag: String, msg: String) = log(Log.DEBUG, "D", tag, msg, null)

    fun i(tag: String, msg: String) = log(Log.INFO, "I", tag, msg, null)

    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Log.WARN, "W", tag, msg, tr)

    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Log.ERROR, "E", tag, msg, tr)

    private fun log(priority: Int, level: String, tag: String, msg: String, tr: Throwable?) {
        val detail = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
        Log.println(priority, tag, detail)
        val text = "${stamp.get()!!.format(Date())} $level $tag: $detail"
        synchronized(lock) {
            lines.addLast(text)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun dump(): String = synchronized(lock) { lines.joinToString("\n") }
}
