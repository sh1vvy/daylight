package com.music.bitchord

import androidx.lifecycle.SavedStateHandle
import com.music.bitchord.ui.LastFmLoginViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmLoginStateTest {
    @Test fun pendingApprovalSurvivesViewModelRecreationAndCancelErasesTheRequestToken() {
        val saved = pendingToken(System.currentTimeMillis())
        val recreated = LastFmLoginViewModel(saved)
        assertTrue(recreated.state.value.awaitingApproval)

        recreated.cancel()

        assertFalse(recreated.state.value.awaitingApproval)
        assertFalse(recreated.state.value.loading)
        assertNull(saved.get<String>("lastfm_request_token"))
        assertNull(saved.get<Long>("lastfm_request_token_issued_at"))
        assertFalse(LastFmLoginViewModel(saved).state.value.awaitingApproval)
    }

    @Test fun expiredOrFutureDatedTokensCannotResumeAnAuthorization() {
        val now = System.currentTimeMillis()
        listOf(now - 3_600_001, now + 60_000).forEach { issuedAt ->
            val login = LastFmLoginViewModel(pendingToken(issuedAt))
            assertFalse(login.state.value.awaitingApproval)

            // An expired request must fail locally, without contacting Last.fm.
            login.finish(automatic = true)

            assertFalse(login.state.value.awaitingApproval)
            assertFalse(login.state.value.loading)
            assertFalse(login.state.value.connected)
            assertNotNull(login.state.value.error)
        }
    }

    private fun pendingToken(issuedAt: Long) = SavedStateHandle(
        mapOf("lastfm_request_token" to "test-request-token", "lastfm_request_token_issued_at" to issuedAt),
    )
}
