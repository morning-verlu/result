package cn.verlu.sync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BatteryLevelEntity::class,
        ScreenTimeReportEntity::class,
        TemperatureLevelEntity::class,
        WeatherSnapshotEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batteryLevelDao(): BatteryLevelDao
    abstract fun screenTimeReportDao(): ScreenTimeReportDao
    abstract fun temperatureLevelDao(): TemperatureLevelDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE battery_levels ADD COLUMN deviceModel TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS screen_time_reports (
                        userId TEXT NOT NULL,
                        deviceKey TEXT NOT NULL,
                        period TEXT NOT NULL,
                        deviceModel TEXT NOT NULL DEFAULT '',
                        totalForegroundMs INTEGER NOT NULL,
                        topAppsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY (userId, deviceKey, period)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE screen_time_reports ADD COLUMN deviceFriendlyName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS battery_levels_new (
                        deviceKey TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        batteryPercent INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deviceModel TEXT NOT NULL DEFAULT '',
                        deviceFriendlyName TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO battery_levels_new
                    (deviceKey, userId, batteryPercent, updatedAt, deviceModel, deviceFriendlyName)
                    SELECT ('legacy:' || userId), userId, batteryPercent, updatedAt, deviceModel, ''
                    FROM battery_levels
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE battery_levels")
                db.execSQL("ALTER TABLE battery_levels_new RENAME TO battery_levels")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS screen_time_reports_new (
                        userId TEXT NOT NULL,
                        period TEXT NOT NULL,
                        deviceModel TEXT NOT NULL DEFAULT '',
                        deviceFriendlyName TEXT NOT NULL DEFAULT '',
                        totalForegroundMs INTEGER NOT NULL,
                        topAppsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY (userId, period)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO screen_time_reports_new (userId, period, deviceModel, deviceFriendlyName, totalForegroundMs, topAppsJson, updatedAt)
                    SELECT userId, period, deviceModel, deviceFriendlyName, totalForegroundMs, topAppsJson, updatedAt
                    FROM screen_time_reports s
                    WHERE s.rowid = (
                        SELECT s2.rowid FROM screen_time_reports s2
                        WHERE s2.userId = s.userId AND s2.period = s.period
                        ORDER BY s2.updatedAt DESC, s2.rowid DESC LIMIT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE screen_time_reports")
                db.execSQL("ALTER TABLE screen_time_reports_new RENAME TO screen_time_reports")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS battery_levels_new (
                        userId TEXT NOT NULL PRIMARY KEY,
                        batteryPercent INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deviceModel TEXT NOT NULL DEFAULT '',
                        deviceFriendlyName TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO battery_levels_new (userId, batteryPercent, updatedAt, deviceModel, deviceFriendlyName)
                    SELECT userId, batteryPercent, updatedAt, deviceModel, deviceFriendlyName
                    FROM battery_levels b
                    WHERE b.rowid = (
                        SELECT b2.rowid FROM battery_levels b2
                        WHERE b2.userId = b.userId
                        ORDER BY b2.updatedAt DESC, b2.rowid DESC LIMIT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE battery_levels")
                db.execSQL("ALTER TABLE battery_levels_new RENAME TO battery_levels")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS temperature_levels (
                        userId TEXT NOT NULL PRIMARY KEY,
                        temperature INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deviceModel TEXT NOT NULL DEFAULT '',
                        deviceFriendlyName TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weather_snapshots (
                        userId TEXT NOT NULL PRIMARY KEY,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        cityLabel TEXT NOT NULL,
                        temp TEXT NOT NULL,
                        feelsLike TEXT NOT NULL,
                        textDesc TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        obsTime TEXT NOT NULL,
                        apiUpdateTime TEXT NOT NULL,
                        forecastJson TEXT NOT NULL,
                        deviceFriendlyName TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
