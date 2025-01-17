/*
 * Copyright 2022 Bloomberg Finance L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloomberg.selekt.android

import android.database.sqlite.SQLiteDatabaseLockedException
import com.bloomberg.selekt.commons.deleteDatabase
import com.bloomberg.selekt.NULL
import com.bloomberg.selekt.Pointer
import com.bloomberg.selekt.SQLOpenOperation
import com.bloomberg.selekt.SQL_OK
import com.bloomberg.selekt.SQL_OPEN_CREATE
import com.bloomberg.selekt.SQL_OPEN_READWRITE
import com.bloomberg.selekt.SQL_ROW
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class SQLiteTransactionTest {
    @get:Rule
    val timeoutRule = DisableOnDebug(Timeout(10L, TimeUnit.SECONDS))

    private val file = File.createTempFile("test-sqlite-transaction", ".db").also { it.deleteOnExit() }

    private var db: Pointer = NULL

    @Before
    fun setUp() {
        db = openConnection()
    }

    @After
    fun tearDown() {
        try {
            assertEquals(SQL_OK, SQLite.closeV2(db))
        } finally {
            if (file.exists()) {
                assertTrue(deleteDatabase(file))
            }
        }
    }

    @Test
    fun attemptCheckpointWhileReading() {
        assertEquals(SQL_OK, SQLite.exec(db, "PRAGMA journal_mode=WAL"))
        assertEquals(SQL_OK, SQLite.exec(db, "CREATE TABLE 'Foo' (bar INT)"))
        assertEquals(SQL_OK, SQLite.exec(db, "INSERT INTO 'Foo' VALUES (42)"))
        prepareStatement(db, "SELECT * FROM Foo").usePreparedStatement {
            assertEquals(SQL_ROW, SQLite.step(it))
            openConnection().useConnection { otherDb ->
                SQLite.exec(otherDb, "BEGIN IMMEDIATE TRANSACTION")
                SQLite.exec(otherDb, "INSERT INTO 'Foo' VALUES (43)")
                SQLite.exec(otherDb, "END")
                assertThatExceptionOfType(SQLiteDatabaseLockedException::class.java).isThrownBy {
                    SQLite.walCheckpointV2(otherDb, null, 1)
                }
            }
        }
    }

    @Test
    fun attemptSimultaneousTransactions() {
        assertEquals(SQL_OK, SQLite.exec(db, "PRAGMA journal_mode=WAL"))
        assertEquals(SQL_OK, SQLite.exec(db, "CREATE TABLE 'Foo' (bar INT)"))
        assertEquals(SQL_OK, SQLite.exec(db, "INSERT INTO 'Foo' VALUES (42)"))
        assertEquals(SQL_OK, SQLite.exec(db, "BEGIN IMMEDIATE TRANSACTION"))
        openConnection().useConnection { otherDb ->
            assertThatExceptionOfType(SQLiteDatabaseLockedException::class.java).isThrownBy {
                SQLite.exec(otherDb, "BEGIN IMMEDIATE TRANSACTION")
            }
        }
    }

    private fun openConnection(flags: SQLOpenOperation = SQL_OPEN_READWRITE or SQL_OPEN_CREATE): Pointer {
        val holder = LongArray(1)
        assertEquals(SQL_OK, SQLite.openV2(file.absolutePath, flags, holder))
        return holder.first().also { assertNotEquals(NULL, it) }
    }
}
