package com.familygames.domain.model

/** Crop ratio chosen in S10 (docs/02 §1). */
enum class Aspect { SQUARE, FOUR_THREE }

/** Where an image lives. User photos are files in app storage; built-in illustrations ship in assets. */
sealed interface PhotoSource {
    /** Absolute path inside the app's private storage. */
    data class LocalFile(val path: String) : PhotoSource

    /** Path inside the APK assets, e.g. `builtin/dom_kanapa.webp`. */
    data class Asset(val path: String) : PhotoSource
}

data class Photo(
    val id: Long,
    val categoryId: Long,
    val image: PhotoSource,
    val thumb: PhotoSource,
    val aspect: Aspect,
    val label: String?,
    /** Absolute path of the parent's voice recording, if any. */
    val audioPath: String?,
    val useInMemory: Boolean,
    val useInPuzzle: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    /** Absolute path of the recorded question for this photo (docs/12 M10.3). */
    val promptAudioPath: String? = null,
    val countable: Boolean = false,
) {
    /** Built-in illustrations cannot be deleted, only hidden from games (docs/03 S09). */
    val isBuiltIn: Boolean get() = image is PhotoSource.Asset
}

/** A photo that has already been stored by MediaStorage; paths are relative to app storage. */
data class NewPhoto(
    val categoryId: Long,
    val filePath: String,
    val thumbPath: String,
    val aspect: Aspect,
    val label: String? = null,
    val audioPath: String? = null,
    val useInMemory: Boolean = true,
    val useInPuzzle: Boolean = true,
    val promptAudioPath: String? = null,
    val countable: Boolean = false,
)

data class Category(
    val id: Long,
    val name: String,
    val coverPhotoId: Long?,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: Long,
) {
    companion object {
        const val MAX_NAME_LENGTH = 24
    }
}

/** Category with the counts S02/S09 need and the thumbnail shown on its chip. */
data class CategorySummary(
    val category: Category,
    val photoCount: Int,
    val memoryCount: Int,
    val puzzleCount: Int,
    val cover: PhotoSource?,
)

/** M16 · a "Po kolei" sequence: 3–5 photos in the order of the steps. */
data class PhotoSequence(val id: Long, val name: String, val steps: List<Photo>) {
    companion object {
        const val MIN_STEPS = 3
        const val MAX_STEPS = 5
    }
}
