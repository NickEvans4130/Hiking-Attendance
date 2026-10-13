package com.rhulhiking.attendance.attendance

import com.rhulhiking.attendance.sheets.TabColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterTest {

    @Test fun `no header row - data starts at row 1`() {
        val roster = Roster.from(TabColumns(listOf("101", "102"), listOf("Ann", "Bob"), listOf("Signed Up", "HERE")))
        assertFalse(roster.hasHeader)
        assertEquals(listOf(1, 2), roster.entries.map { it.row })
    }

    @Test fun `text in row 1 id cell is treated as header`() {
        val roster = Roster.from(TabColumns(listOf("Student ID", "101"), listOf("Name", "Ann"), listOf("Status", "Signed Up")))
        assertTrue(roster.hasHeader)
        assertEquals(listOf(2), roster.entries.map { it.row })
        assertEquals("Ann", roster.entries.single().name)
    }

    @Test fun `numeric double in row 1 is data not header`() {
        assertFalse(Roster.detectHeader(listOf("1.01096712E8")))
        assertFalse(Roster.detectHeader(listOf("101096712")))
        assertFalse(Roster.detectHeader(emptyList()))
        assertTrue(Roster.detectHeader(listOf("Student number")))
    }

    @Test fun `blank id rows skipped and short columns tolerated`() {
        val roster = Roster.from(TabColumns(listOf("101", null, "", "103"), listOf("Ann"), emptyList()))
        assertEquals(listOf(1, 4), roster.entries.map { it.row })
        assertEquals("", roster.entries[1].name)
        assertEquals("", roster.entries[1].status)
    }

    @Test fun `first row offset applied`() {
        val roster = Roster.from(TabColumns(listOf("101"), listOf("Ann"), listOf(""), firstRow = 5))
        assertEquals(5, roster.entries.single().row)
    }

    @Test fun `summary merges duplicates and counts distinct people`() {
        val roster = Roster.from(
            TabColumns(
                ids = listOf("101", "102", "0102", "103"),
                names = listOf("Ann", "", "Bob", ""),
                statuses = listOf("HERE", "Signed Up", "here ", "Signed Up"),
            ),
        )
        val s = roster.summary("HERE")
        assertEquals(3, s.total)
        assertEquals(2, s.hereCount)
        assertEquals(listOf("103", "Ann", "Bob"), s.attendees.map { it.displayName })
    }
}
