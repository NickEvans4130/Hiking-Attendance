package com.rhulhiking.attendance.attendance

import com.rhulhiking.attendance.fakes.FakeClock
import com.rhulhiking.attendance.fakes.FakeSheetsRepository
import com.rhulhiking.attendance.settings.ColumnConfig
import com.rhulhiking.attendance.sheets.SheetsException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInServiceTest {
    private val tab = "It's a Hike"
    private val target = HikeTarget("sheet", tab)
    private val cols = ColumnConfig()
    private val repo = FakeSheetsRepository()
    private val service = CheckInService(repo, FakeClock())

    @Test fun `signed up student is marked HERE`() = runTest {
        repo.signUp(tab, "101096712", "Ann")
        val batch = service.checkIn(target, cols, "0101096712")
        assertEquals(listOf(CheckInOutcome.CheckedIn("101096712", "Ann", 1)), batch.outcomes)
        assertEquals("HERE", repo.cell(tab, 1, "M"))
        assertEquals(1, batch.roster.summary("HERE").hereCount)
    }

    @Test fun `already here is not written again`() = runTest {
        repo.signUp(tab, "101096712", "Ann", "HERE")
        val batch = service.checkIn(target, cols, "101096712")
        assertEquals(listOf(CheckInOutcome.AlreadyHere("101096712", "Ann", 1)), batch.outcomes)
        assertTrue(repo.statusWrites.isEmpty())
    }

    @Test fun `not found writes nothing`() = runTest {
        repo.signUp(tab, "101096712", "Ann")
        assertEquals(listOf(CheckInOutcome.NotFound("999")), service.checkIn(target, cols, "999").outcomes)
        assertTrue(repo.statusWrites.isEmpty())
    }

    @Test fun `header row is never matched or written`() = runTest {
        repo.signUp(tab, "Student ID", "Name", "Status")
        repo.signUp(tab, "101096712", "Ann")
        service.checkIn(target, cols, "101096712")
        assertEquals("Status", repo.cell(tab, 1, "M"))
        assertEquals("HERE", repo.cell(tab, 2, "M"))
    }

    @Test fun `duplicates all marked in one write`() = runTest {
        repo.signUp(tab, "101096712", "Ann")
        repo.signUp(tab, "222", "Bob")
        repo.signUp(tab, "1.01096712E8", "")
        val batch = service.checkIn(target, cols, "101096712")
        assertEquals(listOf(CheckInOutcome.CheckedIn("101096712", "Ann", 2)), batch.outcomes)
        assertEquals(listOf(Triple(tab, listOf(1, 3), "HERE")), repo.statusWrites)
    }

    @Test fun `rows appended by the form mid-session are found because every check-in re-reads`() = runTest {
        repo.signUp(tab, "111", "Ann")
        service.checkIn(target, cols, "111")
        repo.signUp(tab, "222", "Late Larry")
        assertEquals(CheckInOutcome.CheckedIn("222", "Late Larry", 1), service.checkIn(target, cols, "222").outcomes.single())
        assertEquals(2, repo.reads)
        assertEquals("HERE", repo.cell(tab, 2, "M"))
    }

    @Test fun `only the status column is ever written`() = runTest {
        repo.signUp(tab, "111", "Ann")
        val before = repo.tabs.getValue(tab).map { it.toList() }
        service.checkIn(target, cols, "111")
        val after = repo.tabs.getValue(tab)
        before.zip(after).forEach { (b, a) ->
            b.indices.filter { it != 12 }.forEach { i -> assertEquals(b[i], a[i]) }
        }
    }

    @Test fun `configurable columns and present value`() = runTest {
        val custom = ColumnConfig(idColumn = "D", nameColumn = "E", statusColumn = "F", presentValue = "Present")
        repo.addRow(tab, "D" to "555", "E" to "Eve", "F" to "Signed Up")
        assertEquals(CheckInOutcome.CheckedIn("555", "Eve", 1), service.checkIn(target, custom, "555").outcomes.single())
        assertEquals("Present", repo.cell(tab, 1, "F"))
    }

    @Test fun `batch with the same id twice writes once`() = runTest {
        repo.signUp(tab, "111", "Ann")
        val batch = service.checkInAll(target, cols, listOf("111", "111", "222", ""))
        assertEquals(
            listOf(
                CheckInOutcome.CheckedIn("111", "Ann", 1),
                CheckInOutcome.AlreadyHere("111", "Ann", 1),
                CheckInOutcome.NotFound("222"),
                CheckInOutcome.InvalidId(""),
            ),
            batch.outcomes,
        )
        assertEquals(1, repo.statusWrites.size)
    }

    @Test fun `walk in appends only timestamp id and status`() = runTest {
        repo.signUp(tab, "111", "Ann")
        val batch = service.addWalkIn(target, cols, "0999")
        assertEquals(listOf(CheckInOutcome.WalkInAdded("999")), batch.outcomes)
        val row = repo.appends.single().second
        assertEquals(setOf(0, 1, 12), row.indices.filter { row[it] != null }.toSet())
        assertEquals("999", row[1])
        assertEquals("HERE", row[12])
        assertTrue(row[0]!!.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")))
        assertEquals(2, batch.roster.summary("HERE").total)
    }

    @Test fun `walk in for someone who signed up meanwhile checks them in instead`() = runTest {
        repo.signUp(tab, "999", "Zed")
        val batch = service.addWalkIn(target, cols, "999")
        assertEquals(CheckInOutcome.CheckedIn("999", "Zed", 1), batch.outcomes.single())
        assertTrue(repo.appends.isEmpty())
    }

    @Test(expected = SheetsException.Network::class)
    fun `network errors propagate for the caller to queue`() = runTest {
        repo.signUp(tab, "111", "Ann")
        repo.failWith = SheetsException.Network()
        service.checkIn(target, cols, "111")
    }
}
