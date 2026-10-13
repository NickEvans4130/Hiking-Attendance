package com.rhulhiking.attendance.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ColumnConfigTest {
    @Test fun `defaults are valid`() = assertNull(ColumnConfig().validate())
    @Test fun `status may not overlap id or name`() {
        assertNotNull(ColumnConfig(statusColumn = "B").validate())
        assertNotNull(ColumnConfig(statusColumn = "c").validate())
        assertNotNull(ColumnConfig(statusColumn = "A").validate())
    }
    @Test fun `invalid letters rejected`() = assertNotNull(ColumnConfig(idColumn = "2").validate())
    @Test fun `blank present value rejected`() = assertNotNull(ColumnConfig(presentValue = " ").validate())
    @Test fun `normalized uppercases and trims`() =
        assertEquals(ColumnConfig(idColumn = "AB", presentValue = "HERE"), ColumnConfig(idColumn = " ab ", presentValue = " HERE ").normalized())
}
