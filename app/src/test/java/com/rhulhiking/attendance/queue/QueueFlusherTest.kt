package com.rhulhiking.attendance.queue

import com.rhulhiking.attendance.attendance.CheckInService
import com.rhulhiking.attendance.fakes.FakeClock
import com.rhulhiking.attendance.fakes.FakeSheetsRepository
import com.rhulhiking.attendance.fakes.InMemoryPendingQueue
import com.rhulhiking.attendance.settings.ColumnConfig
import com.rhulhiking.attendance.sheets.SheetsException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFlusherTest {
    private val repo = FakeSheetsRepository()
    private val queue = InMemoryPendingQueue()
    private val flusher = QueueFlusher(queue, CheckInService(repo, FakeClock()))
    private val cols = ColumnConfig()
    private val hikeA = "Hike A"
    private val hikeB = "It's Hike B"

    @Test fun `synced entries are written and deleted`() = runTest {
        repo.signUp(hikeA, "111", "Ann")
        repo.signUp(hikeA, "222", "Bob")
        queue.enqueue("s", hikeA, "111", 1)
        queue.enqueue("s", hikeA, "222", 2)

        val result = flusher.flush(cols)

        assertEquals(FlushResult(2, 0, FlushResult.Status.Done), result)
        assertEquals("HERE", repo.cell(hikeA, 1, "M"))
        assertEquals("HERE", repo.cell(hikeA, 2, "M"))
        assertTrue(queue.all.isEmpty())
    }

    @Test fun `one read and one write per tab`() = runTest {
        repo.signUp(hikeA, "111", "Ann")
        repo.signUp(hikeA, "222", "Bob")
        repo.signUp(hikeB, "333", "Cat")
        queue.enqueue("s", hikeA, "111", 1)
        queue.enqueue("s", hikeB, "333", 2)
        queue.enqueue("s", hikeA, "222", 3)

        flusher.flush(cols)

        assertEquals(2, repo.reads)
        assertEquals(listOf(hikeA to listOf(1, 2), hikeB to listOf(1)), repo.statusWrites.map { it.first to it.second })
    }

    @Test fun `already here at sync time is deleted without writing`() = runTest {
        repo.signUp(hikeA, "111", "Ann", "HERE")
        queue.enqueue("s", hikeA, "111", 1)
        assertEquals(1, flusher.flush(cols).synced)
        assertTrue(repo.statusWrites.isEmpty())
        assertTrue(queue.all.isEmpty())
    }

    @Test fun `not found is kept for a human and not added as walk in`() = runTest {
        repo.signUp(hikeA, "111", "Ann")
        queue.enqueue("s", hikeA, "999", 1)

        val result = flusher.flush(cols)

        assertEquals(FlushResult(0, 1, FlushResult.Status.Done), result)
        assertTrue(repo.appends.isEmpty())
        val kept = queue.all.single()
        assertEquals(PendingCheckIn.State.NEEDS_ATTENTION, kept.state)
        assertEquals(QueueFlusher.NOT_SIGNED_UP, kept.note)
        // Not retried on the next flush.
        assertEquals(FlushResult(0, 0, FlushResult.Status.Done), flusher.flush(cols))
        assertEquals(1, queue.observeNeedsAttention("s", hikeA).first().size)
    }

    @Test fun `duplicate sign up rows all marked at sync`() = runTest {
        repo.signUp(hikeA, "111", "Ann")
        repo.signUp(hikeA, "0111", "Ann again")
        queue.enqueue("s", hikeA, "111", 1)
        flusher.flush(cols)
        assertEquals(listOf(1, 2), repo.statusWrites.single().second)
    }

    @Test fun `same id queued twice writes once and clears both`() = runTest {
        repo.signUp(hikeA, "111", "Ann")
        queue.addRaw("s", hikeA, "111")
        queue.addRaw("s", hikeA, "111")
        assertEquals(2, flusher.flush(cols).synced)
        assertEquals(1, repo.statusWrites.size)
        assertTrue(queue.all.isEmpty())
    }

    @Test fun `network failure keeps everything and asks to retry`() = runTest {
        repo.signUp(hikeA, "111", "Ann")
        queue.enqueue("s", hikeA, "111", 1)
        repo.failWith = SheetsException.Network()

        assertEquals(FlushResult.Status.Retry, flusher.flush(cols).status)
        assertEquals(PendingCheckIn.State.PENDING, queue.all.single().state)

        repo.failWith = null
        assertEquals(FlushResult(1, 0, FlushResult.Status.Done), flusher.flush(cols))
        assertTrue(queue.all.isEmpty())
    }

    @Test fun `rate limiting and server errors also retry`() = runTest {
        queue.enqueue("s", hikeA, "111", 1)
        repo.failWith = SheetsException.RateLimited()
        assertEquals(FlushResult.Status.Retry, flusher.flush(cols).status)
        repo.failWith = SheetsException.Server(503)
        assertEquals(FlushResult.Status.Retry, flusher.flush(cols).status)
        assertEquals(1, queue.all.size)
    }

    @Test fun `auth required stops and keeps entries`() = runTest {
        queue.enqueue("s", hikeA, "111", 1)
        repo.failWith = SheetsException.AuthRequired()
        assertEquals(FlushResult.Status.NeedsAuth, flusher.flush(cols).status)
        assertEquals(PendingCheckIn.State.PENDING, queue.all.single().state)
    }

    @Test fun `deleted tab parks its entries but other tabs still sync`() = runTest {
        repo.signUp(hikeB, "333", "Cat")
        queue.enqueue("s", "Deleted hike", "111", 1)
        queue.enqueue("s", hikeB, "333", 2)

        val result = flusher.flush(cols)

        assertEquals(FlushResult(1, 1, FlushResult.Status.Done), result)
        val parked = queue.all.single()
        assertEquals("Deleted hike", parked.tab)
        assertEquals(PendingCheckIn.State.NEEDS_ATTENTION, parked.state)
        assertFalse(parked.note.isNullOrBlank())
    }

    @Test fun `queue de-duplicates the same student on the same tab`() = runTest {
        assertTrue(queue.enqueue("s", hikeA, "111", 1))
        assertFalse(queue.enqueue("s", hikeA, "111", 2))
        assertTrue(queue.enqueue("s", hikeB, "111", 3))
    }
}
