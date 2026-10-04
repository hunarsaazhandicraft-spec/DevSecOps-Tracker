package com.devsecops.tracker.data

import java.util.TimeZone
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LocalDateTest {

    private var savedTz: TimeZone? = null

    @Before fun setUp() {
        savedTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    @After fun tearDown() {
        savedTz?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun utcEvening_becomesNextLocalDayInKolkata() {
        assertEquals(LocalDate.parse("2026-09-30"), localDateOf("2026-09-29T20:00:00+00:00"))
    }

    @Test
    fun plainDate_staysTheSame() {
        assertEquals(LocalDate.parse("2026-09-29"), localDateOf("2026-09-29"))
    }

    @Test
    fun blankOrNull_isNull() {
        assertNull(localDateOf(null))
        assertNull(localDateOf(""))
    }
}
