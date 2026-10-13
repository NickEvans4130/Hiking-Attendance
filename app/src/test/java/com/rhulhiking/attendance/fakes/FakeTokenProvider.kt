package com.rhulhiking.attendance.fakes

import com.rhulhiking.attendance.auth.AccessTokenProvider

class FakeTokenProvider : AccessTokenProvider {
    private var counter = 1
    val invalidated = mutableListOf<String>()
    override suspend fun accessToken() = "token-$counter"
    override suspend fun invalidate(token: String) {
        invalidated += token
        counter++
    }
}
