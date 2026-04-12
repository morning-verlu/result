package cn.verlu.cloud.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cn.verlu.cloud.db.CloudDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Desktop SQLite：不能用「只传 schema」的 [JdbcSqliteDriver] 单独解决问题——旧代码曾手动 [SqlSchema.create] 建表
 * 但未写入 `PRAGMA user_version`，驱动会误判为空库再次 create，触发 table already exists。
 * 这里显式读 user_version，并兼容「有表但 version 仍为 0」的遗留文件。
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // ProGuard 打包后 JDBC ServiceLoader 可能失效，显式加载驱动保证 release 可用。
        Class.forName("org.sqlite.JDBC")
        val dbPath = desktopDbPath()
        Files.createDirectories(dbPath.parent)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.toAbsolutePath()}", Properties())
        synchronizeJvmSqliteSchema(driver)
        return driver
    }
}

private fun desktopDbPath(): Path {
    val appData = System.getenv("APPDATA")?.trim().orEmpty()
    return if (appData.isNotEmpty()) {
        Path.of(appData, "VerluCloud", "cloud.db")
    } else {
        Path.of(System.getProperty("user.home"), ".verlu-cloud", "cloud.db")
    }
}

private fun synchronizeJvmSqliteSchema(driver: SqlDriver) {
    val target = CloudDatabase.Schema.version
    val current = readPragmaUserVersion(driver)
    when {
        current == 0L -> {
            try {
                CloudDatabase.Schema.create(driver)
            } catch (e: Exception) {
                // JDBC SQLite 抛的是 java.sql.SQLException 等，不依赖 org.sqlite 的类是否在 compile classpath
                val msg = e.message.orEmpty()
                if (!msg.contains("already exists", ignoreCase = true)) throw e
            }
            driver.execute(null, "PRAGMA user_version = $target", 0)
        }
        current < target -> {
            CloudDatabase.Schema.migrate(driver, current, target)
        }
        else -> Unit
    }
}

private fun readPragmaUserVersion(driver: SqlDriver): Long {
    val result: QueryResult<Long> = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor: SqlCursor ->
            val hasRow = cursor.next().asValueOrThrow<Boolean>()
            val v = if (hasRow) cursor.getLong(0) ?: 0L else 0L
            QueryResult.Value(v)
        },
        parameters = 0,
        binders = null,
    )
    return result.asValueOrThrow()
}

private fun <T> QueryResult<T>.asValueOrThrow(): T =
    when (this) {
        is QueryResult.Value -> value
        else -> error("Expected synchronous SQLDelight driver on JVM desktop")
    }
