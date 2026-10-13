package com.rhulhiking.attendance.attendance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentIdsTest {
    @Test fun `plain id unchanged`() = assertEquals("101096712", StudentIds.normalize("101096712"))
    @Test fun `trims whitespace`() = assertEquals("101096712", StudentIds.normalize("  101096712 \n"))
    @Test fun `drops leading zeros`() = assertEquals("101096712", StudentIds.normalize("000101096712"))
    @Test fun `strips non digits`() = assertEquals("101096712", StudentIds.normalize("ID: 1010-96712"))
    @Test fun `internal spaces removed`() = assertEquals("101096712", StudentIds.normalize("1010 96712"))
    @Test fun `scientific notation double`() = assertEquals("101096712", StudentIds.normalize("1.01096712E8"))
    @Test fun `lowercase exponent with sign`() = assertEquals("101096712", StudentIds.normalize("1.01096712e+8"))
    @Test fun `trailing point zero`() = assertEquals("101096712", StudentIds.normalize("101096712.0"))
    @Test fun `exponent with trailing zeros`() = assertEquals("100000000", StudentIds.normalize("1.0E8"))
    @Test fun `null and blank are empty`() {
        assertEquals("", StudentIds.normalize(null))
        assertEquals("", StudentIds.normalize("   "))
        assertEquals("", StudentIds.normalize("Student ID"))
        assertEquals("", StudentIds.normalize("0000"))
    }
    @Test fun `card and sheet forms compare equal`() =
        assertEquals(StudentIds.normalize(" 0101096712"), StudentIds.normalize("1.01096712E8"))

    @Test fun `numeric detection`() {
        assertTrue(StudentIds.isNumeric("101096712"))
        assertTrue(StudentIds.isNumeric(" 101096712 "))
        assertTrue(StudentIds.isNumeric("1.01096712E8"))
        assertFalse(StudentIds.isNumeric("Student ID"))
        assertFalse(StudentIds.isNumeric("Student ID (9 digits)"))
        assertFalse(StudentIds.isNumeric(""))
        assertFalse(StudentIds.isNumeric(null))
    }
}
