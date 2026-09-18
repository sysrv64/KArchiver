package com.kerneldroid.karchiver.data.elevation

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val LsLongLine = Regex("""^(\S+)\s+\d+\s+\S+\s+\S+\s+([\d,]+)\s+(.*)$""")

internal fun parseLsLong(dir: File, output: String, now: Long = System.currentTimeMillis()): List<ElevatedEntry> {
    val result = ArrayList<ElevatedEntry>()
    for (raw in output.split('\n')) {
        val line = raw.trimEnd('\r')
        if (line.isBlank()) continue
        if (line.startsWith("total")) continue
        val match = LsLongLine.matchEntire(line) ?: continue
        val perms = match.groupValues[1]
        val type = perms.firstOrNull() ?: continue
        if (type !in "dl-cpsb?-") continue
        val size = match.groupValues[2].substringBefore(',').toLongOrNull() ?: 0L
        val parsed = splitDateAndName(match.groupValues[3]) ?: continue
        val isDir = type == 'd'
        var name = parsed.third
        if (type == 'l') {
            val arrow = name.indexOf(" -> ")
            if (arrow >= 0) name = name.substring(0, arrow)
        }
        if (name.isEmpty() || name == "." || name == "..") continue
        result.add(
            ElevatedEntry(
                file = File(dir, name),
                isDirectory = isDir,
                size = if (isDir) 0L else size,
                modified = parseLsDate(parsed.first, parsed.second, now),
                mode = permissionsToMode(perms)
            )
        )
    }
    return result
}

private fun splitDateAndName(rest: String): Triple<String, String, String>? {
    val text = rest.trimStart()
    if (text.isEmpty()) return null
    if (text.length >= 10 && text[4] == '-' && text[7] == '-') {
        val date = text.substring(0, 10)
        val after = text.substring(10).trimStart()
        val time = after.substringBefore(' ')
        val name = after.substringAfter(' ', "").trimStart()
        return Triple(date, time, name)
    }
    val first = text.substringBefore(' ')
    val rem1 = text.substringAfter(' ', "")
    if (rem1.isEmpty()) return null
    val second = rem1.substringBefore(' ')
    val rem2 = rem1.substringAfter(' ', "")
    if (rem2.isEmpty()) return null
    val third = rem2.substringBefore(' ')
    val name = rem2.substringAfter(' ', "").trimStart()
    return Triple("$first $second", third, name)
}

internal fun permissionsToMode(perms: String): Int {
    val p = perms.take(10)
    var mode = 0
    for (i in 0 until 9) {
        val c = p.getOrNull(i + 1) ?: continue
        val set = when (c) {
            'r', 'w', 'x', 's', 't' -> true
            else -> false
        }
        if (set) mode = mode or (1 shl (8 - i))
    }
    if (p.getOrNull(3) == 's' || p.getOrNull(3) == 'S') mode = mode or 0x800
    if (p.getOrNull(6) == 's' || p.getOrNull(6) == 'S') mode = mode or 0x400
    if (p.getOrNull(9) == 't' || p.getOrNull(9) == 'T') mode = mode or 0x200
    return mode
}

private fun parseLsDate(date: String, time: String, now: Long): Long {
    return try {
        if (date.contains('-')) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$date $time")?.time ?: 0L
        } else if (time.length == 4 && time.all { it.isDigit() }) {
            SimpleDateFormat("MMM d yyyy", Locale.US).parse("$date $time")?.time ?: 0L
        } else {
            val parsed = SimpleDateFormat("MMM d HH:mm", Locale.US).parse("$date $time") ?: return 0L
            val value = Calendar.getInstance().apply { timeInMillis = parsed.time }
            val reference = Calendar.getInstance().apply { timeInMillis = now }
            val future = value.get(Calendar.MONTH) > reference.get(Calendar.MONTH) ||
                (value.get(Calendar.MONTH) == reference.get(Calendar.MONTH) &&
                    value.get(Calendar.DAY_OF_MONTH) > reference.get(Calendar.DAY_OF_MONTH))
            if (future) value.add(Calendar.YEAR, -1)
            value.timeInMillis
        }
    } catch (_: Exception) {
        0L
    }
}
