package com.familygames.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.familygames.domain.model.Aspect

@Database(
    entities = [CategoryEntity::class, PhotoEntity::class, GameResultEntity::class, SequenceEntity::class, SequenceStepEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FamilyGamesDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun photoDao(): PhotoDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun sequenceDao(): SequenceDao

    companion object {
        const val NAME = "familygames.db"

        fun build(context: Context): FamilyGamesDatabase =
            Room.databaseBuilder(context, FamilyGamesDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}

/** Every schema change gets an explicit migration — family photos must survive app updates. */
val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

/** docs/12 M10.4: every v2 column and table in one migration, even for games built later. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE photos ADD COLUMN promptAudioPath TEXT")
        db.execSQL("ALTER TABLE photos ADD COLUMN countable INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sequences` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sequence_steps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sequenceId` INTEGER NOT NULL, `photoId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sequenceId`) REFERENCES `sequences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`photoId`) REFERENCES `photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sequence_steps_sequenceId` ON `sequence_steps` (`sequenceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sequence_steps_photoId` ON `sequence_steps` (`photoId`)")
    }
}

/**
 * ADR-030: voice recordings are replaced by text read by the synthesizer. Adds the question text and forgets the
 * recordings (their files are removed by [com.familygames.data.local.files.MediaStorage.deleteLegacyAudio]).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE photos ADD COLUMN promptText TEXT")
        db.execSQL("UPDATE photos SET audioPath = NULL, promptAudioPath = NULL")
    }
}

class Converters {
    @TypeConverter
    fun aspectToString(aspect: Aspect): String = aspect.name

    @TypeConverter
    fun stringToAspect(value: String): Aspect = Aspect.valueOf(value)
}
