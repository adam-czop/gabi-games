package com.familygames.data.seed

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.familygames.R
import com.familygames.data.local.db.CategoryDao
import com.familygames.data.local.db.CategoryEntity
import com.familygames.data.local.db.FamilyGamesDatabase
import com.familygames.data.local.db.PhotoDao
import com.familygames.data.local.db.PhotoEntity
import com.familygames.data.local.files.MediaStorage
import com.familygames.data.local.prefs.SettingsDataStore
import com.familygames.domain.model.Aspect
import com.familygames.util.Clock

data class BuiltInPhoto(val asset: String, @param:StringRes val label: Int) {
    val filePath: String get() = "${MediaStorage.ASSET_PREFIX}builtin/$asset.webp"
    val thumbPath: String get() = "${MediaStorage.ASSET_PREFIX}builtin/thumbs/$asset.webp"
}

data class BuiltInCategory(@param:StringRes val name: Int, val photos: List<BuiltInPhoto>)

/** Illustrations drawn by tools/builtin_art.py (Q9) so the games work before the parent adds photos. */
object BuiltInContent {
    /** Bump when categories or photos are added; the seeder then adds only what is missing. */
    const val VERSION = 3 // 2: Pojazdy, 3: countable flag (docs/12 M17)

    val categories = listOf(
        BuiltInCategory(
            R.string.builtin_category_home,
            listOf(
                BuiltInPhoto("dom_kanapa", R.string.builtin_sofa),
                BuiltInPhoto("dom_lozko", R.string.builtin_bed),
                BuiltInPhoto("dom_lampa", R.string.builtin_lamp),
                BuiltInPhoto("dom_wanna", R.string.builtin_bathtub),
                BuiltInPhoto("dom_stol", R.string.builtin_table),
                BuiltInPhoto("dom_krzeslo", R.string.builtin_chair),
                BuiltInPhoto("dom_okno", R.string.builtin_window),
                BuiltInPhoto("dom_drzwi", R.string.builtin_door),
            ),
        ),
        BuiltInCategory(
            R.string.builtin_category_animals,
            listOf(
                BuiltInPhoto("zwierzeta_kot", R.string.builtin_cat),
                BuiltInPhoto("zwierzeta_pies", R.string.builtin_dog),
                BuiltInPhoto("zwierzeta_krowa", R.string.builtin_cow),
                BuiltInPhoto("zwierzeta_kaczka", R.string.builtin_duck),
                BuiltInPhoto("zwierzeta_kon", R.string.builtin_horse),
                BuiltInPhoto("zwierzeta_swinka", R.string.builtin_pig),
                BuiltInPhoto("zwierzeta_kura", R.string.builtin_hen),
                BuiltInPhoto("zwierzeta_owca", R.string.builtin_sheep),
            ),
        ),
        BuiltInCategory(
            R.string.builtin_category_toys,
            listOf(
                BuiltInPhoto("zabawki_mis", R.string.builtin_teddy),
                BuiltInPhoto("zabawki_pilka", R.string.builtin_ball),
                BuiltInPhoto("zabawki_klocki", R.string.builtin_blocks),
                BuiltInPhoto("zabawki_auto", R.string.builtin_car),
                BuiltInPhoto("zabawki_lalka", R.string.builtin_doll),
                BuiltInPhoto("zabawki_bebenek", R.string.builtin_drum),
                BuiltInPhoto("zabawki_rakieta", R.string.builtin_rocket),
                BuiltInPhoto("zabawki_kaczuszka", R.string.builtin_rubber_duck),
            ),
        ),
        BuiltInCategory(
            R.string.builtin_category_vehicles,
            listOf(
                BuiltInPhoto("pojazdy_koparka", R.string.builtin_excavator),
                BuiltInPhoto("pojazdy_traktor", R.string.builtin_tractor),
                BuiltInPhoto("pojazdy_kombajn", R.string.builtin_combine),
                BuiltInPhoto("pojazdy_samochod", R.string.builtin_passenger_car),
                BuiltInPhoto("pojazdy_autobus", R.string.builtin_bus),
                BuiltInPhoto("pojazdy_straz", R.string.builtin_fire_truck),
                BuiltInPhoto("pojazdy_wywrotka", R.string.builtin_dump_truck),
                BuiltInPhoto("pojazdy_pociag", R.string.builtin_train),
            ),
        ),
    )
}

/**
 * Inserts the built-in categories and illustrations (docs/02 §4). Idempotent: a category is found again
 * by its illustrations (so a renamed built-in category is not duplicated) and a photo is inserted only
 * if its asset path is not in the database yet.
 */
class DefaultContentSeeder(
    private val resources: Resources,
    private val db: FamilyGamesDatabase,
    private val categoryDao: CategoryDao,
    private val photoDao: PhotoDao,
    private val settings: SettingsDataStore,
    private val clock: Clock,
) {

    /** Seeds when the stored seed version is older than [BuiltInContent.VERSION]. Returns true if it ran. */
    suspend fun seedIfNeeded(): Boolean {
        if (settings.seedVersion() >= BuiltInContent.VERSION) return false
        seed()
        settings.setSeedVersion(BuiltInContent.VERSION)
        return true
    }

    suspend fun seed() = db.withTransaction {
        val now = clock.now()
        BuiltInContent.categories.forEach { def ->
            val name = resources.getString(def.name)
            val categoryId = def.photos.firstNotNullOfOrNull { photoDao.findCategoryIdByPath(it.filePath) }
                ?: categoryDao.findByName(name)?.id
                ?: categoryDao.insert(
                    CategoryEntity(name = name, sortOrder = categoryDao.nextSortOrder(), isBuiltIn = true, createdAt = now),
                )
            var sortOrder = photoDao.nextSortOrder(categoryId)
            def.photos.forEach { photo ->
                if (!photoDao.existsByPath(photo.filePath)) {
                    photoDao.insert(
                        PhotoEntity(
                            categoryId = categoryId,
                            filePath = photo.filePath,
                            thumbPath = photo.thumbPath,
                            aspect = Aspect.SQUARE,
                            label = resources.getString(photo.label),
                            sortOrder = sortOrder++,
                            createdAt = now,
                            countable = true,
                        ),
                    )
                }
            }
        }
        // All built-in illustrations are things, not people, so "Ile jest?" can count them (docs/12 M17).
        photoDao.markCountable(BuiltInContent.categories.flatMap { c -> c.photos.map { it.filePath } })
    }
}
