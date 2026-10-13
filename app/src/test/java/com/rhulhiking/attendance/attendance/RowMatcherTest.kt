package com.rhulhiking.attendance.attendance

import com.rhulhiking.attendance.sheets.TabColumns
import org.junit.Assert.assertEquals
import org.junit.Test

class RowMatcherTest {
    private val roster = Roster.from(
        TabColumns(
            ids = listOf("101096712", "202020202", "303030303", "303030303", "404040404", "404040404"),
            names = listOf("Ann", "", "Cat", "Cat B", "Dan", ""),
            statuses = listOf("Signed Up", "HERE", "Signed Up", "Signed Up", "HERE", "Signed Up"),
        ),
    )

    @Test fun `found and signed up needs write`() = assertEquals(
        MatchResult.NeedsWrite("101096712", "Ann", listOf(1), 1),
        RowMatcher.match(roster, "101096712", "HERE"),
    )

    @Test fun `any status other than present needs write`() {
        val r = Roster.from(TabColumns(listOf("1"), listOf("A"), listOf("Maybe")))
        assertEquals(MatchResult.NeedsWrite("1", "A", listOf(1), 1), RowMatcher.match(r, "1", "HERE"))
    }

    @Test fun `already here with blank name falls back to id`() = assertEquals(
        MatchResult.AlreadyHere("202020202", "202020202", 1),
        RowMatcher.match(roster, "202020202", "HERE"),
    )

    @Test fun `present value comparison ignores case and whitespace`() = assertEquals(
        MatchResult.AlreadyHere("202020202", "202020202", 1),
        RowMatcher.match(roster, "202020202", " here "),
    )

    @Test fun `not found`() = assertEquals(MatchResult.NotFound("999"), RowMatcher.match(roster, "999", "HERE"))

    @Test fun `empty id is invalid`() = assertEquals(MatchResult.InvalidId, RowMatcher.match(roster, "", "HERE"))

    @Test fun `duplicates all written`() = assertEquals(
        MatchResult.NeedsWrite("303030303", "Cat", listOf(3, 4), 2),
        RowMatcher.match(roster, "303030303", "HERE"),
    )

    @Test fun `duplicates partly here writes only the rest`() = assertEquals(
        MatchResult.NeedsWrite("404040404", "Dan", listOf(6), 2),
        RowMatcher.match(roster, "404040404", "HERE"),
    )

    @Test fun `rows already written in batch count as present`() = assertEquals(
        MatchResult.AlreadyHere("303030303", "Cat", 2),
        RowMatcher.match(roster, "303030303", "HERE", alreadyWritten = setOf(3, 4)),
    )
}
