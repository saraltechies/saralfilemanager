package com.saraltechies.saralfilemanager.files

import java.util.Locale

object Sizes {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    /** 1024-based, like the phone's own storage settings: "512 B", "1.5 MB", "23.4 GB", "120 GB". */
    fun format(bytes: Long): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, pattern, value, units[unit])
    }
}
