package com.rhulhiking.attendance.sheets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

@Serializable
internal data class SpreadsheetMeta(val sheets: List<SheetMeta> = emptyList())

@Serializable
internal data class SheetMeta(val properties: SheetProperties = SheetProperties())

@Serializable
internal data class SheetProperties(val title: String = "")

@Serializable
internal data class ValueRange(
    val range: String? = null,
    val majorDimension: String? = null,
    val values: List<List<JsonElement>> = emptyList(),
)

@Serializable
internal data class BatchGetResponse(val valueRanges: List<ValueRange> = emptyList())

@Serializable
internal data class BatchUpdateRequest(val valueInputOption: String, val data: List<ValueRange>)

@Serializable
internal data class ApiErrorEnvelope(val error: ApiError? = null)

@Serializable
internal data class ApiError(val code: Int = 0, val message: String = "", val status: String = "")

/** Text of an UNFORMATTED_VALUE cell. Numbers such as 1.01096712E8 become plain digits. */
internal fun JsonElement.cellText(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.toBigDecimalOrNull()
        ?.stripTrailingZeros()?.let(BigDecimal::toPlainString) ?: content
    else -> toString()
}
