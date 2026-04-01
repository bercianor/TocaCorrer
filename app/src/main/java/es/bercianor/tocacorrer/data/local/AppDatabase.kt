package es.bercianor.tocacorrer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import es.bercianor.tocacorrer.data.local.dao.WorkoutDao
import es.bercianor.tocacorrer.data.local.dao.GpsPointDao
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint

@Database(
    entities = [Workout::class, GpsPoint::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao
    abstract fun gpsPointDao(): GpsPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * HOW TO ADD FUTURE MIGRATIONS:
         *
         * When you bump the database version (e.g., from 1 to 2), add a new Migration
         * object and include it in the addMigrations() call below.
         *
         * Example:
         *   val MIGRATION_1_2 = object : Migration(1, 2) {
         *       override fun migrate(database: SupportSQLiteDatabase) {
         *           database.execSQL("ALTER TABLE workouts ADD COLUMN newColumn TEXT NOT NULL DEFAULT ''")
         *       }
         *   }
         *
         * Then add it: .addMigrations(MIGRATION_1_2)
         *
         * DO NOT use fallbackToDestructiveMigration() — it would delete all user data
         * if a migration is missing. Always provide an explicit Migration instead.
         */

        /**
         * Migration from version 1 to 2:
         * Adds the phase_index column to gps_points to disambiguate repeated phase letters
         * (e.g. Run → Rest → Run) and drive groupByPhase() correctly in GPX export.
         * Old rows receive phase_index = 0 (acceptable degradation: they export as before).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE gps_points ADD COLUMN phase_index INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Migration from version 2 to 3:
         * Adds an index on the startTime column of the workouts table to speed up
         * date-range queries used by statistics and the weekly chart.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_startTime ON workouts (startTime)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tocacorrer_database"
                )
                    // Migrations are declared explicitly above.
                    // Add new migrations here when bumping the schema version.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
