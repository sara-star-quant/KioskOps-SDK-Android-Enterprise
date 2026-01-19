package com.peterz.kioskops.sdk.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class RingLog(context: Context, private val maxLines: Int = 2000) {
  private val lock = Any()
  private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val dir = File(context.filesDir, "kioskops_logs").apply { mkdirs() }
  private val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

  fun i(tag: String, msg: String) = add("I", tag, msg, null)
  fun w(tag: String, msg: String, t: Throwable? = null) = add("W", tag, msg, t)
  fun e(tag: String, msg: String, t: Throwable? = null) = add("E", tag, msg, t)

  private fun add(level: String, tag: String, msg: String, t: Throwable?) {
    val time = fmt.format(Date())
    val full = buildString {
      append(time).append(" ").append(level).append("/").append(tag).append(": ").append(msg)
      if (t != null) {
        append(" | ").append(t::class.java.simpleName).append(": ").append(t.message)
      }
    }
    synchronized(lock) {
      lines.add(full)
      if (lines.size > maxLines) {
        lines.subList(0, lines.size - maxLines).clear()
      }
    }
  }

  fun exportToFile(): File {
    val f = File(dir, "kioskops_log_${System.currentTimeMillis()}.log")
    val snapshot: List<String> = synchronized(lock) { lines.toList() }
    f.writeText(snapshot.joinToString("\n"))
    return f
  }
}
