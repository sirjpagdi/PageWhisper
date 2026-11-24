package com.pagewhisper.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/** A small JSON file with atomic writes. The library is small, so plain files are enough (no database needed). */
class JsonFile<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: T,
) {
    @Synchronized
    fun read(): T = try {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else default
    } catch (e: Exception) {
        default
    }

    @Synchronized
    fun write(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
