package com.damianogiusti.stickermaker.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.damianogiusti.stickermaker.data.db.StickerDatabase
import com.damianogiusti.stickermaker.domain.repository.Clock
import java.util.Properties

fun createInMemoryDatabase(): StickerDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
    StickerDatabase.Schema.create(driver)
    return StickerDatabase(driver)
}

class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
}
