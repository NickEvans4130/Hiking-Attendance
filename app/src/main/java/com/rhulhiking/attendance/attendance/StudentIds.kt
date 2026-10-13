package com.rhulhiking.attendance.attendance

import java.math.BigDecimal

object StudentIds {
    private val NUMBER = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Canonical form for comparing a card value with a sheet value: trimmed, digits only, no
     * leading zeros. Numeric renderings such as `1.01096712E8` or `101096712.0` (a sheet storing
     * IDs as numbers) are expanded first so the exponent/fraction digits don't leak in.
     * Returns "" when there is no usable ID.
     */
    fun normalize(raw: String?): String {
        if (raw == null) return ""
        val compact = raw.trim().replace(WHITESPACE, "")
        val digits = expandNumber(compact) ?: compact.filter { it in '0'..'9' }
        return digits.trimStart('0')
    }

    /** True when the cell looks like a number (an ID), as opposed to header text like "Student ID". */
    fun isNumeric(raw: String?): Boolean {
        if (raw == null) return false
        val compact = raw.trim().replace(WHITESPACE, "")
        return compact.isNotEmpty() && NUMBER.matches(compact)
    }

    private fun expandNumber(s: String): String? {
        if (!NUMBER.matches(s) || (!s.contains('.') && !s.contains('e', ignoreCase = true))) return null
        return runCatching {
            BigDecimal(s).stripTrailingZeros().let { bd ->
                // A genuine fraction isn't an ID; fall back to plain digit stripping.
                if (bd.scale() > 0) null else bd.toBigIntegerExact().abs().toString()
            }
        }.getOrNull()
    }
}
