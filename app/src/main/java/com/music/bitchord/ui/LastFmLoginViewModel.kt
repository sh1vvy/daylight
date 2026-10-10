package com.music.bitchord.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LastFmLoginState(
    val awaitingApproval: Boolean = false,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
)

/** Request tokens survive rotation; the authorized session is stored only in AuthStore. */
class LastFmLoginViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(LastFmLoginState(awaitingApproval = validToken() != null))
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun start(openBrowser: (String) -> Unit) {
        if (job?.isActive == true) return
        _state.value = _state.value.copy(loading = true, error = null, connected = false)
        job = viewModelScope.launch {
            try {
                configure()
                val token = validToken() ?: LastFM.getToken().getOrThrow().token.also {
                    require(it.isNotBlank())
                    saved[TOKEN] = it
                    saved[ISSUED_AT] = System.currentTimeMillis()
                }
                _state.value = _state.value.copy(awaitingApproval = true)
                openBrowser(LastFM.getAuthUrl(token))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Couldn’t open Last.fm. Please try again.")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun finish(automatic: Boolean = false) {
        if (job?.isActive == true) return
        val token = validToken() ?: run {
            _state.value = LastFmLoginState(error = "The sign-in expired. Please start again.")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                configure()
                val session = LastFM.getSession(token).getOrThrow().session
                require(session.key.isNotBlank() && session.name.isNotBlank())
                AppSettings.setLastfmSessionKey(session.key)
                AppSettings.setLastfmUsername(session.name)
                AppSettings.setLastfmScrobbleEnabled(true)
                AppSettings.setLastfmNowPlaying(true)
                AppSettings.setLastfmEnabled(true)
                clearToken()
                _state.value = LastFmLoginState(connected = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LastFM.LastFmException) {
                when (e.code) {
                    14 -> if (!automatic) _state.value = _state.value.copy(
                        error = "Approve Daylight in your browser, then finish connecting.",
                    )
                    4, 15 -> {
                        clearToken()
                        _state.value = LastFmLoginState(error = "The sign-in expired. Please start again.")
                    }
                    else -> _state.value = _state.value.copy(error = "Couldn’t connect to Last.fm. Please try again.")
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Couldn’t connect to Last.fm. Please try again.")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        clearToken()
        _state.value = LastFmLoginState()
    }

    private fun configure() = LastFM.configure(
        endpoint = LastFM.DEFAULT_API_ENDPOINT,
        apiKey = AppSettings.lastfmApiKey.value,
        secret = AppSettings.lastfmSecret.value,
        sessionKey = null,
    )

    private fun validToken(): String? {
        val age = System.currentTimeMillis() - (saved.get<Long>(ISSUED_AT) ?: 0)
        return saved.get<String>(TOKEN)?.takeIf { it.isNotBlank() && age in 0 until 3_600_000 }
    }

    private fun clearToken() { saved.remove<String>(TOKEN); saved.remove<Long>(ISSUED_AT) }

    companion object {
        private const val TOKEN = "lastfm_request_token"
        private const val ISSUED_AT = "lastfm_request_token_issued_at"
    }
}
