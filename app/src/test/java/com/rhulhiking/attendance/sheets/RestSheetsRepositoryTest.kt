package com.rhulhiking.attendance.sheets

import com.rhulhiking.attendance.fakes.FakeTokenProvider
import com.rhulhiking.attendance.settings.ColumnConfig
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RestSheetsRepositoryTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenProvider()
    private val sleeps = mutableListOf<Long>()
    private lateinit var repo: RestSheetsRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        repo = RestSheetsRepository(OkHttpClient(), tokens, server.url("/")) { sleeps += it }
    }

    @After fun tearDown() = server.shutdown()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test fun `list tabs requests only titles`() = runTest {
        server.enqueue(ok("""{"sheets":[{"properties":{"title":"Members"}},{"properties":{"title":"It's a Hike"}}]}"""))
        assertEquals(listOf("Members", "It's a Hike"), repo.listTabs("abc"))
        val req = server.takeRequest()
        assertEquals("/v4/spreadsheets/abc", req.requestUrl!!.encodedPath)
        assertEquals("sheets.properties.title", req.requestUrl!!.queryParameter("fields"))
        assertEquals("Bearer token-1", req.getHeader("Authorization"))
    }

    @Test fun `read columns requests only id name and status columns unformatted`() = runTest {
        server.enqueue(
            ok(
                """{"valueRanges":[
                  {"range":"'It''s a Hike'!B1:B3","majorDimension":"COLUMNS","values":[[1.01096712E8,"", "202"]]},
                  {"range":"'It''s a Hike'!C1:C2","majorDimension":"COLUMNS","values":[["Ann","Bob"]]},
                  {"range":"'It''s a Hike'!M2:M3","majorDimension":"COLUMNS","values":[["HERE","Signed Up"]]}
                ]}""",
            ),
        )
        val cols = repo.readColumns("abc", "It's a Hike", ColumnConfig())
        assertEquals(listOf("101096712", "", "202"), cols.ids)
        assertEquals(listOf("Ann", "Bob"), cols.names)
        assertEquals(listOf(null, "HERE", "Signed Up"), cols.statuses)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v4/spreadsheets/abc/values:batchGet", url.encodedPath)
        assertEquals(listOf("'It''s a Hike'!B:B", "'It''s a Hike'!C:C", "'It''s a Hike'!M:M"), url.queryParameterValues("ranges"))
        assertEquals("UNFORMATTED_VALUE", url.queryParameter("valueRenderOption"))
        assertEquals("COLUMNS", url.queryParameter("majorDimension"))
    }

    @Test fun `empty columns are tolerated`() = runTest {
        server.enqueue(ok("""{"valueRanges":[{"range":"'H'!B1:B1000"},{"range":"'H'!C1:C1000"},{"range":"'H'!M1:M1000"}]}"""))
        val cols = repo.readColumns("abc", "H", ColumnConfig())
        assertTrue(cols.ids.isEmpty())
    }

    @Test fun `write status uses RAW and single cells in the status column`() = runTest {
        server.enqueue(ok("{}"))
        repo.writeStatus("abc", "It's a Hike", "M", listOf(3, 7, 3), "HERE")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v4/spreadsheets/abc/values:batchUpdate", req.requestUrl!!.encodedPath)
        assertEquals(
            """{"valueInputOption":"RAW","data":[{"range":"'It''s a Hike'!M3","values":[["HERE"]]},{"range":"'It''s a Hike'!M7","values":[["HERE"]]}]}""",
            req.body.readUtf8(),
        )
    }

    @Test fun `append walk in writes only three cells`() = runTest {
        server.enqueue(ok("{}"))
        repo.appendWalkIn("abc", "It's a Hike", ColumnConfig(), "2026-10-13 09:00:00", "999")
        val req = server.takeRequest()
        assertEquals("'It''s a Hike'!A:M:append", req.requestUrl!!.pathSegments.last())
        assertEquals("USER_ENTERED", req.requestUrl!!.queryParameter("valueInputOption"))
        assertEquals("INSERT_ROWS", req.requestUrl!!.queryParameter("insertDataOption"))
        assertEquals(
            """{"values":[["2026-10-13 09:00:00","999",null,null,null,null,null,null,null,null,null,null,"HERE"]]}""",
            req.body.readUtf8(),
        )
    }

    private suspend fun expectError(code: Int, body: String = "{}"): SheetsException {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        try {
            repo.listTabs("abc")
        } catch (e: SheetsException) {
            return e
        }
        fail("expected exception"); throw AssertionError()
    }

    @Test fun `403 means no edit access`() = runTest {
        assertTrue(expectError(403, """{"error":{"code":403,"message":"The caller does not have permission","status":"PERMISSION_DENIED"}}""") is SheetsException.NoAccess)
    }

    @Test fun `403 service disabled means api not enabled`() = runTest {
        assertTrue(expectError(403, """{"error":{"code":403,"message":"Google Sheets API has not been used in project 1 before","status":"PERMISSION_DENIED","details":[{"reason":"SERVICE_DISABLED"}]}}""") is SheetsException.ApiDisabled)
    }

    @Test fun `404 means not found`() = runTest { assertTrue(expectError(404) is SheetsException.NotFound) }

    @Test fun `400 unparseable range means missing tab`() = runTest {
        assertTrue(expectError(400, """{"error":{"code":400,"message":"Unable to parse range: 'Gone'!B:B"}}""") is SheetsException.NotFound)
    }

    @Test fun `429 backs off and retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        server.enqueue(ok("""{"sheets":[]}"""))
        assertEquals(emptyList<String>(), repo.listTabs("abc"))
        assertEquals(2, sleeps.size)
        assertTrue(sleeps[0] in 1000..1250)
        assertEquals(2000L, sleeps[1])
    }

    @Test fun `429 that never clears becomes RateLimited`() = runTest {
        repeat(RestSheetsRepository.MAX_RETRIES + 1) { server.enqueue(MockResponse().setResponseCode(429)) }
        try { repo.listTabs("abc"); fail() } catch (e: SheetsException.RateLimited) { assertTrue(e.isTransient) }
        assertEquals(RestSheetsRepository.MAX_RETRIES, sleeps.size)
    }

    @Test fun `5xx retried then reported as transient server error`() = runTest {
        repeat(RestSheetsRepository.MAX_RETRIES + 1) { server.enqueue(MockResponse().setResponseCode(503)) }
        try { repo.listTabs("abc"); fail() } catch (e: SheetsException.Server) { assertTrue(e.isTransient) }
    }

    @Test fun `401 refreshes token once and retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(ok("""{"sheets":[]}"""))
        repo.listTabs("abc")
        assertEquals(listOf("token-1"), tokens.invalidated)
        server.takeRequest()
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `repeated 401 asks the user to sign in again`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        try { repo.listTabs("abc"); fail() } catch (e: SheetsException.AuthRequired) { }
    }

    @Test fun `connection failure is a transient network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try { repo.listTabs("abc"); fail() } catch (e: SheetsException.Network) { assertTrue(e.isTransient) }
    }
}
