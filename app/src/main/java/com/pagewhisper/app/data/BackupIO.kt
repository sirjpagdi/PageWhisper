package com.pagewhisper.app.data

import com.pagewhisper.app.BuildConfig
import java.util.Base64

/** Connects BackupCore to the real library on this device. */
object BackupIO {
    fun exportJson(): String = BackupCore.build(
        appVersion = BuildConfig.VERSION_NAME,
        reading = Repo.reading.value,
        settings = Repo.settings.value,
        novels = Repo.novels.value.filter { !Books.isBookUrl(it.url) }, // imported books stay on the phone
        chapters = Repo.chapters.value,
        pron = Repo.pron.value,
        coverDataUrl = { id ->
            val f = Repo.coverFile(id)
            if (f.exists()) "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(f.readBytes()) else null
        },
    )

    private fun saveCoverFromDataUrl(id: String, dataUrl: String) {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return
        runCatching { Repo.saveCover(id, Base64.getDecoder().decode(dataUrl.substring(comma + 1))) }
    }

    /** Adds what is missing and keeps what is here. Nothing is deleted. */
    fun applyMerge(b: CleanBackup) {
        val covers = Repo.coverIds().associateWith { "file" }
        val m = BackupCore.merge(LocalData(Repo.novels.value, covers, Repo.chapters.value, Repo.pron.value), b)
        for ((id, v) in m.covers) if (v.startsWith("data:")) saveCoverFromDataUrl(id, v)
        Repo.replaceAll(m.novels, m.chapters, m.pron, reading = null, settings = null)
    }

    /** Puts the backup in place of everything on this device. */
    fun applyReplace(b: CleanBackup) {
        Repo.novels.value.forEach { n -> Books.parse(n.url)?.let { Books.delete(it.first) } } // books are not in a backup
        Repo.clearAllCovers()
        Repo.clearAllTocs() // chapter lists are not in a backup; they are fetched again
        val novels = b.novels.map { n ->
            n.cover?.let { saveCoverFromDataUrl(n.id, it) }
            com.pagewhisper.app.model.Novel(n.id, n.title, n.url, n.key, n.chapter, n.updated)
        }
        Repo.replaceAll(novels, b.chapters, b.pron, b.reading, b.settings ?: com.pagewhisper.app.model.AppSettings())
    }
}
