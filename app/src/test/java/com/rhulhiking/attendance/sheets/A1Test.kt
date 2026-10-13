package com.rhulhiking.attendance.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class A1Test {
    @Test fun `tab with spaces is quoted`() = assertEquals(
        "'Stepping Stones and Headley Heath Circular'!B:B",
        A1.column("Stepping Stones and Headley Heath Circular", "B"),
    )

    @Test fun `apostrophes are doubled`() = assertEquals("'It''s a Hike'!B:B", A1.column("It's a Hike", "B"))

    @Test fun `multiple and leading apostrophes`() = assertEquals("'''Bob''s'' walk'!M3", A1.cell("'Bob's' walk", "M", 3))

    @Test fun `exclamation mark inside tab name stays quoted`() = assertEquals("'Hike! Go'!C:C", A1.column("Hike! Go", "C"))

    @Test fun `cell and column range`() {
        assertEquals("'Hike'!M12", A1.cell("Hike", "m", 12))
        assertEquals("'Hike'!A:M", A1.columns("Hike", "A", "M"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `row zero rejected`() { A1.cell("Hike", "M", 0) }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid column rejected`() { A1.column("Hike", "B2") }

    @Test fun `column letters round trip`() {
        assertEquals(0, A1.columnIndex("A"))
        assertEquals(12, A1.columnIndex("M"))
        assertEquals(25, A1.columnIndex("Z"))
        assertEquals(26, A1.columnIndex("AA"))
        assertEquals(701, A1.columnIndex("ZZ"))
        for (i in 0..2000) assertEquals(i, A1.columnIndex(A1.columnLetters(i)))
    }

    @Test fun `column validation`() {
        assertTrue(A1.isValidColumn("b"))
        assertTrue(A1.isValidColumn(" AA "))
        assertFalse(A1.isValidColumn(""))
        assertFalse(A1.isValidColumn("1"))
        assertFalse(A1.isValidColumn("ABCD"))
    }

    @Test fun `start row parsed from returned range`() {
        assertEquals(1, A1.startRow("'It''s a Hike'!B1:B57"))
        assertEquals(3, A1.startRow("'Hike'!\$B\$3:\$B\$9"))
        assertNull(A1.startRow("'Hike'!B:B"))
    }
}
