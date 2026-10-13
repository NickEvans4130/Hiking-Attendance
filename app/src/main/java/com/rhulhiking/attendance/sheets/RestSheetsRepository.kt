package com.rhulhiking.attendance.sheets

import com.rhulhiking.attendance.auth.AccessTokenProvider
import com.rhulhiking.attendance.settings.ColumnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * Sheets API v4 over REST. Only ever requests the configured ID, name and status columns, and
 * only writes the status column (plus walk-in rows).
 */
class RestSheetsRepository(
    private val client: OkHttpClient,
    private val tokens: AccessTokenProvider,
    private val baseUrl: HttpUrl,
    private val sleep: suspend (Long) -> Unit,
) : SheetsRepository {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun listTabs(spreadsheetId: String): List<String> {
        val url = spreadsheetUrl(spreadsheetId).newBuilder()
            .addQueryParameter("fields", "sheets.properties.title")
            .build()
        val body = execute { Request.Builder().url(url).get() }
        return json.decodeFromString<SpreadsheetMeta>(body).sheets.map { it.properties.title }
    }

    override suspend fun readColumns(spreadsheetId: String, tab: String, columns: ColumnConfig): TabColumns {
        val ranges = listOf(
            A1.column(tab, columns.idColumn),
            A1.column(tab, columns.nameColumn),
            A1.column(tab, columns.statusColumn),
        )
        val url = spreadsheetUrl(spreadsheetId).newBuilder()
            .addPathSegment("values:batchGet")
            .apply { ranges.forEach { addQueryParameter("ranges", it) } }
            .addQueryParameter("majorDimension", "COLUMNS")
            .addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")
            .build()
        val body = execute { Request.Builder().url(url).get() }
        val valueRanges = json.decodeFromString<BatchGetResponse>(body).valueRanges
        val cols = ranges.indices.map { i -> valueRanges.getOrNull(i)?.alignedFromRow1().orEmpty() }
        return TabColumns(ids = cols[0], names = cols[1], statuses = cols[2], firstRow = 1)
    }

    override suspend fun writeStatus(
        spreadsheetId: String,
        tab: String,
        statusColumn: String,
        rows: List<Int>,
        value: String,
    ) {
        if (rows.isEmpty()) return
        val request = BatchUpdateRequest(
            valueInputOption = "RAW",
            data = rows.distinct().map { row ->
                ValueRange(range = A1.cell(tab, statusColumn, row), values = listOf(listOf(JsonPrimitive(value))))
            },
        )
        val url = spreadsheetUrl(spreadsheetId).newBuilder().addPathSegment("values:batchUpdate").build()
        execute { Request.Builder().url(url).post(json.encodeToString(request).toRequestBody(JSON)) }
    }

    override suspend fun appendWalkIn(
        spreadsheetId: String,
        tab: String,
        columns: ColumnConfig,
        timestamp: String,
        studentId: String,
    ) {
        val cells = mapOf(
            A1.columnIndex(columns.timestampColumn) to timestamp,
            A1.columnIndex(columns.idColumn) to studentId,
            A1.columnIndex(columns.statusColumn) to columns.presentValue,
        )
        val first = cells.keys.min()
        val last = cells.keys.max()
        // Null cells are skipped by the API, so only these three cells are written.
        val row: List<JsonElement> = (first..last).map { i -> cells[i]?.let(::JsonPrimitive) ?: JsonNull }
        val range = A1.columns(tab, A1.columnLetters(first), A1.columnLetters(last))
        val url = spreadsheetUrl(spreadsheetId).newBuilder()
            .addPathSegment("values")
            .addPathSegment("$range:append")
            // USER_ENTERED so the timestamp becomes a real date like the form's. Every value is
            // app-generated (digits-only ID, fixed timestamp format, configured present value).
            .addQueryParameter("valueInputOption", "USER_ENTERED")
            .addQueryParameter("insertDataOption", "INSERT_ROWS")
            .build()
        val body = json.encodeToString(ValueRange(values = listOf(row)))
        execute { Request.Builder().url(url).post(body.toRequestBody(JSON)) }
    }

    private fun spreadsheetUrl(spreadsheetId: String): HttpUrl = baseUrl.newBuilder()
        .addPathSegments("v4/spreadsheets")
        .addPathSegment(spreadsheetId)
        .build()

    /** Pads a COLUMNS-major range so index 0 is sheet row 1. */
    private fun ValueRange.alignedFromRow1(): List<String?> {
        val cells = values.firstOrNull().orEmpty().map { it.cellText() }
        val start = range?.let(A1::startRow) ?: 1
        return List(start - 1) { null } + cells
    }

    private suspend fun execute(build: () -> Request.Builder): String {
        var attempt = 0
        var refreshedToken = false
        while (true) {
            val token = tokens.accessToken()
            val request = build().header("Authorization", "Bearer $token").build()
            val (code, body, retryAfter) = try {
                client.newCall(request).await().use { r ->
                    Triple(r.code, withContext(Dispatchers.IO) { r.body.string() }, r.header("Retry-After"))
                }
            } catch (e: IOException) {
                throw SheetsException.Network(e)
            }

            when {
                code in 200..299 -> return body
                code == 401 && !refreshedToken -> {
                    // Expired or revoked token: drop it and re-authorise transparently, once.
                    refreshedToken = true
                    tokens.invalidate(token)
                }
                code == 401 -> throw SheetsException.AuthRequired()
                code == 429 || code >= 500 -> {
                    if (attempt >= MAX_RETRIES) {
                        throw if (code == 429) SheetsException.RateLimited() else SheetsException.Server(code)
                    }
                    sleep(backoffMillis(attempt, retryAfter))
                    attempt++
                }
                else -> throw mapError(code, body)
            }
        }
    }

    private fun mapError(code: Int, body: String): SheetsException {
        val error = runCatching { json.decodeFromString<ApiErrorEnvelope>(body).error }.getOrNull()
        val message = error?.message.orEmpty()
        return when {
            code == 403 && (body.contains("SERVICE_DISABLED") || message.contains("has not been used")) ->
                SheetsException.ApiDisabled()
            code == 403 && body.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT") -> SheetsException.AuthRequired()
            code == 403 && message.contains("protected", ignoreCase = true) -> SheetsException.ProtectedCell()
            code == 403 -> SheetsException.NoAccess()
            code == 404 -> SheetsException.NotFound()
            // A range naming a tab that doesn't exist is a 400, not a 404.
            code == 400 && message.contains("Unable to parse range") -> SheetsException.NotFound()
            code == 400 && message.contains("exceeds grid limits") -> SheetsException.OutsideGrid()
            else -> SheetsException.Unexpected(code, message.ifEmpty { "no details" })
        }
    }

    private fun backoffMillis(attempt: Int, retryAfter: String?): Long {
        retryAfter?.toLongOrNull()?.let { return (it * 1000).coerceIn(0, MAX_BACKOFF_MS) }
        return (BASE_BACKOFF_MS shl attempt) + Random.nextLong(0, 250)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
        })
    }

    companion object {
        val DEFAULT_BASE_URL = "https://sheets.googleapis.com/"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
