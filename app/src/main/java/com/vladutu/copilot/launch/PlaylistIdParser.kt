package com.vladutu.copilot.launch

object PlaylistIdParser {

    private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{10,}$")
    private val LIST_PARAM = Regex("[?&]list=([A-Za-z0-9_-]+)")

    fun parse(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        LIST_PARAM.find(trimmed)?.groupValues?.get(1)?.let { return it }

        return if (ID_PATTERN.matches(trimmed)) trimmed else null
    }
}
