package dev.brahmkshatriya.echo.extension.utils

import dev.brahmkshatriya.echo.common.models.Date

fun parseDate(input: String): Date? {
    return try {
        val parts = input.split("-")
        Date(
            year = parts[0].toInt(),
            month = parts.getOrNull(1)?.toIntOrNull() ?: 1,
            day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        )
    } catch (e: Exception) {
        null
    }
}