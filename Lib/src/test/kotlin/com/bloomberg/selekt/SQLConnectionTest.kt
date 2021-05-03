/*
 * Copyright 2021 Bloomberg Finance L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloomberg.selekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.Rule
import org.junit.jupiter.api.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.stubbing.Answer
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val databaseConfiguration = DatabaseConfiguration(
    evictionDelayMillis = 5_000L,
    maxConnectionPoolSize = 1,
    maxSqlCacheSize = 5,
    timeBetweenEvictionRunsMillis = 5_000L
)

internal class SQLConnectionTest {
    @get:Rule
    val timeoutRule = DisableOnDebug(Timeout(10L, TimeUnit.SECONDS))

    @Mock lateinit var sqlite: SQLite

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(sqlite.openV2(any(), any(), any())).thenAnswer {
            requireNotNull(it.arguments[2] as? LongArray)[0] = DB
            0
        }
    }

    @Test
    fun exceptionInConstruction() {
        whenever(sqlite.busyTimeout(any(), any())) doThrow IllegalStateException()
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null)
        }
    }

    @Test
    fun constructionChecksNull() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 0L
            0
        }
        assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
            SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null)
        }
    }

    @Test
    fun prepareChecksNull() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 0L
            0
        }
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).apply {
            assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
                prepare("INSERT INTO Foo VALUES (?)")
            }
        }
    }

    @Test
    fun isAutoCommit1() {
        whenever(sqlite.getAutocommit(any())) doReturn 1
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertTrue(it.isAutoCommit)
        }
    }

    @Test
    fun isAutoCommit2() {
        whenever(sqlite.getAutocommit(any())) doReturn 2
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertTrue(it.isAutoCommit)
        }
    }

    @Test
    fun isAutoCommitFalse() {
        whenever(sqlite.getAutocommit(any())) doReturn 0
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertFalse(it.isAutoCommit)
        }
    }

    @Test
    fun checkpointDefault() {
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            it.checkpoint()
            verify(sqlite, times(1)).walCheckpointV2(eq(DB), isNull(), eq(SQLCheckpointMode.PASSIVE()))
        }
    }

    @Test
    fun prepareChecksArgumentCount() {
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer<Any> {
            (it.arguments[2] as LongArray)[0] = 42L
            SQL_OK
        }
        whenever(sqlite.bindParameterCount(any())) doReturn 1
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
                it.executeForChangedRowCount("SELECT * FROM 'Foo' WHERE bar=?", emptyArray<Any>())
            }
        }
    }

    @Test
    fun connectionRejectsUnrecognisedColumnType() {
        whenever(sqlite.columnType(any(), any())) doReturn -1
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
                it.executeForCursorWindow("INSERT INTO Foo VALUES (42)", emptyArray<Int>(), mock())
            }
        }
    }

    @Test
    fun executeForLastInsertedRowIdChecksDone() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_ROW
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertEquals(-1L, it.executeForLastInsertedRowId("INSERT INTO Foo VALUES (42)"))
        }
    }

    @Test
    fun executeForLastInsertedRowIdChecksChanges() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_DONE
        whenever(sqlite.changes(any())) doReturn 0
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertEquals(-1L, it.executeForLastInsertedRowId("INSERT INTO Foo VALUES (42)"))
        }
    }

    @Test
    fun executeForChangedRowCountChecksDone() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_ROW
        whenever(sqlite.changes(any())) doReturn 0
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertEquals(-1, it.executeForChangedRowCount("INSERT INTO Foo VALUES (42)"))
        }
    }

    @Test
    fun executeForBlobReadOnly() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.blobOpen(any(), any(), any(), any(), any(), any(), any())) doAnswer Answer {
            (it.arguments[6] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_ROW
        SQLConnection("file::memory:", sqlite, databaseConfiguration, SQL_OPEN_READONLY, CommonThreadLocalRandom, null).use {
            assertTrue(it.executeForBlob("main", "Foo", "bar", 42L).readOnly)
        }
    }

    @Test
    fun batchExecuteForChangedRowCountChecksDone() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_ROW
        whenever(sqlite.changes(any())) doReturn 0
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertEquals(-1, it.executeForChangedRowCount("INSERT INTO Foo VALUES (42)", sequenceOf(emptyArray<Int>())))
        }
    }

    @Test
    fun connectionChecksWindowAllocation() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_ROW
        whenever(sqlite.columnCount(any())) doReturn 1
        whenever(sqlite.columnType(any(), any())) doReturn -1
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
                it.executeForCursorWindow("SELECT * FROM Foo", emptyArray<Int>(), mock())
            }.withMessage("Failed to allocate a window row.")
        }
    }

    @Test
    fun connectionChecksSqlColumnType() {
        whenever(sqlite.openV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 42L
            0
        }
        whenever(sqlite.prepareV2(any(), any(), any())) doAnswer Answer {
            (it.arguments[2] as LongArray)[0] = 43L
            0
        }
        whenever(sqlite.step(any())) doReturn SQL_ROW
        whenever(sqlite.columnCount(any())) doReturn 1
        whenever(sqlite.columnType(any(), any())) doReturn -1
        val cursorWindow = mock<ICursorWindow>().apply {
            whenever(allocateRow()) doReturn true
        }
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
                it.executeForCursorWindow("SELECT * FROM Foo", emptyArray<Int>(), cursorWindow)
            }.withMessage("Unrecognised column type for column 0.")
        }
    }

    @Test
    fun releaseMemory() {
        SQLConnection("file::memory:", sqlite, databaseConfiguration, 0, CommonThreadLocalRandom, null).use {
            it.releaseMemory()
            verify(sqlite, times(1)).databaseReleaseMemory(any())
        }
    }

    private companion object {
        const val DB = 1L
    }
}
