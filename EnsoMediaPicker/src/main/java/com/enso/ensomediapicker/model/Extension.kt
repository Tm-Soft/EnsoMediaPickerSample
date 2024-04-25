package com.enso.ensomediapicker.model

internal enum class Extension(val value: String) {
    PNG("png"),
    JPEG("jpeg"),
    JPG("jpg"),
    GIF("gif"),
    BMP("bmp"),
    WEBP("webp"),
    MP4("mp4"),
    MOV("mov"),
    UNKNOWN("unknown");

    companion object {
        operator fun invoke(string: String): Extension {
            return values().find { string.contains(it.value) } ?: UNKNOWN
        }
    }
}