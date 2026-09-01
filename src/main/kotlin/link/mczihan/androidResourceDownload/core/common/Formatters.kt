package link.mczihan.androidResourceDownload.core.common

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return "--"
    if (bytes < 1_024L) return "$bytes B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024.0 && unitIndex < units.lastIndex) {
        value /= 1_024.0
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

fun formatDate(epochMillis: Long?): String {
    if (epochMillis == null) return "--"
    // 使用中文格式，与安卓端一致：2026年8月29日 下午9:42
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val hour = zoned.hour
    val period = if (hour < 12) "上午" else "下午"
    val displayHour = ((hour + 11) % 12) + 1
    val minute = zoned.minute.toString().padStart(2, '0')
    return "${zoned.year}年${zoned.monthValue}月${zoned.dayOfMonth}日 $period $displayHour:$minute"
}
