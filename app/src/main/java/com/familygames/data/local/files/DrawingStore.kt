package com.familygames.data.local.files

import android.graphics.Bitmap
import com.familygames.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Finished colouring pages in `filesDir/drawings/` (docs/12 M18): at most 50, the oldest go first. */
class DrawingStore(filesDir: File, private val dispatchers: DispatcherProvider) {
    private val dir = File(filesDir, DIR)
    private val files = MutableStateFlow<List<File>>(emptyList())

    /** Newest first. */
    val drawings: StateFlow<List<File>> = files.asStateFlow()

    suspend fun refresh() = withContext(dispatchers.io) {
        files.value = list()
    }

    suspend fun save(bitmap: Bitmap) = withContext(dispatchers.io) {
        dir.mkdirs()
        val target = File(dir, "drawing_${System.currentTimeMillis()}.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        list().drop(MAX).forEach { it.delete() }
        files.value = list()
    }

    suspend fun delete(file: File) = withContext(dispatchers.io) {
        if (file.parentFile == dir) file.delete()
        files.value = list()
    }

    private fun list(): List<File> = dir.listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.name }

    companion object {
        const val DIR = "drawings"
        const val MAX = 50
    }
}
