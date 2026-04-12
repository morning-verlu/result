package cn.verlu.cloud.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import cn.verlu.cloud.db.CloudDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(CloudDatabase.Schema, AndroidPlatformContext.require(), "cloud.db")
}
