package com.music.bitchord.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.R
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.auth.profileId
import com.music.bitchord.auth.sessionId
import com.music.bitchord.auth.adjacentProfile
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.BoundedRequestCache
import com.music.bitchord.data.SearchRequestKey
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.DownloadedListingFallback
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SPOTIFY_MISSING_PREFIX
import com.music.bitchord.data.model.SPOTIFY_PENDING_PREFIX
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyImporter
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.spotify.SpotifyTrack
import com.music.bitchord.data.library.CollectionMetadataStore
import com.music.bitchord.data.library.CollectionSnapshot
import com.music.bitchord.data.library.likedMetadataScope
import com.music.bitchord.data.library.spotifyMetadataScope
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.PlaybackTracker
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.auth.CapturedSession
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.AccountChannel
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.CreatorProvider
import com.music.bitchord.data.model.CreatorProfilePage
import com.music.bitchord.data.model.PlaylistCreator
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.withoutRepeatsOf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SongMenu
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.StreamChoice
import com.music.bitchord.ui.screens.CACHE_FOLDER_BROWSE_ID
import com.music.bitchord.ui.screens.matchesSearch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** What the Search tab searches: YouTube Music, or the Local Music folder on this device. */
enum class SearchSource { YOUTUBE, LIBRARY }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val authStore = AuthStore(app)

    private val _signedIn = MutableStateFlow(authStore.isSignedIn)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _home = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val home: StateFlow<UiState<List<HomeShelf>>> = _home.asStateFlow()

    /**
     * Token for the next page of Home shelves; null once there's nothing
     * more. Declared here rather than by [loadMoreHome] because [init] calls
     * [loadHome] synchronously up to its first suspension point — a property
     * declared after [init] would still be null when that runs.
     */
    private var homeContinuation: String? = null
    private val _homeQuickRecommendations = MutableStateFlow<List<ShelfItem>>(emptyList())
    val homeQuickRecommendations = _homeQuickRecommendations.asStateFlow()
    private var homeRecommendationsJob: Job? = null
    private var homeRecommendationSeed: String? = null


    /** Titles already on screen, so a later page can't repeat a shelf. */
    private val homeSeenTitles = mutableSetOf<String>()

    private val _homeLoadingMore = MutableStateFlow(false)

    /**
     * Requests still filling out the first Play page. The core feed and each
     * supplement browse run in parallel and publish as they land, so the list
     * goes Success while several shelves are still on the wire — counted here
     * so the feed can say more is coming instead of just ending mid-load.
     */
    private val _homePendingShelves = MutableStateFlow(0)

    /** True whenever shelves are still due at the end of the feed, from either source. */
    val homeLoadingMore: StateFlow<Boolean> =
        combine(_homeLoadingMore, _homePendingShelves) { paging, pending -> paging || pending > 0 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _homeRecentlyPlayedLoading = MutableStateFlow(false)
    val homeRecentlyPlayedLoading: StateFlow<Boolean> = _homeRecentlyPlayedLoading.asStateFlow()
    private val homeLoadGeneration = AtomicLong(0L)

    private val _explore = MutableStateFlow<UiState<List<MoodGenreSection>>>(UiState.Loading)
    val explore: StateFlow<UiState<List<MoodGenreSection>>> = _explore.asStateFlow()

    private val _selectedMoodGenre = MutableStateFlow<MoodGenre?>(null)
    val selectedMoodGenre: StateFlow<MoodGenre?> = _selectedMoodGenre.asStateFlow()

    private val _moodGenreShelves = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val moodGenreShelves: StateFlow<UiState<List<HomeShelf>>> = _moodGenreShelves.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    val results: StateFlow<UiState<List<SearchResult>>?> = _results.asStateFlow()

    /** The mixed YouTube Music result page is the fast, useful default. */
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _searchLoadingMore = MutableStateFlow(false)
    val searchLoadingMore: StateFlow<Boolean> = _searchLoadingMore.asStateFlow()

    /** Increments once per first-page request so the UI can reset its list. */
    private val _searchScrollReset = MutableStateFlow(0)
    val searchScrollReset: StateFlow<Int> = _searchScrollReset.asStateFlow()

    /** True while a committed search is in flight — gates suggestion/media callbacks. */
    private var searchSubmitted = false

    /**
     * What the search page offers while a query is being typed, led by the
     * query itself.
     *
     * Non-empty *is* the signal that the field is mid-edit, so the screen
     * needs no second flag: these rows are shown in place of the results
     * whenever there are any, and cleared the moment a search is actually run
     * — see [submitSearch], [searchFor].
     *
     * Element 0 is always the raw text as typed. It's put there by the
     * keystroke itself rather than taken from the response, so the row the
     * thumb is already heading for is correct before the network answers, and
     * stays correct if it never does — YouTube's list never contains the
     * half-typed text, only completions of it.
     */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /**
     * Live media results shown alongside typeahead suggestions. Populated by a
     * lightweight search that runs in parallel with text completions; the UI
     * renders these as playable track cards and browse items below the text
     * suggestion rows. Cleared when the user commits to a search or empties
     * the field.
     */
    private val _typeaheadResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val typeaheadResults: StateFlow<List<SearchResult>> = _typeaheadResults.asStateFlow()

    /** Where the Search tab looks — see [setSearchSource]. */
    private val _searchSource = MutableStateFlow(SearchSource.YOUTUBE)
    val searchSource: StateFlow<SearchSource> = _searchSource.asStateFlow()

    /** Every track in the Local Music folder, read when the Library source is picked. */
    private val _librarySongs = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)

    /**
     * The Library source's answer for the current query: the Local Music
     * folder narrowed by the same match its own filter box uses, so the two
     * places never disagree about what is on the device. Null while the field
     * is empty — there is nothing asked yet.
     */
    val libraryResults: StateFlow<UiState<List<Song>>?> = combine(_librarySongs, _query) { state, query ->
        val term = query.trim()
        when {
            term.isEmpty() -> null
            state is UiState.Success -> UiState.Success(state.data.filter { it.matchesSearch(term) })
            else -> state
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The search pipeline's own state. Declared here, above [init], because
    // that is where the collector is started from and a property declared
    // below it would still be null when it runs. See [startSearchPipeline].

    /**
     * Buffered so an emission is never lost to a collector that happens to be
     * mid-search, and [BufferOverflow.DROP_OLDEST] because when two arrive
     * together the later one is the one meant.
     */
    private val searchRequests = MutableSharedFlow<SearchRequest?>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * The same arrangement as [searchRequests], for the typeahead — where the
     * drop policy earns its keep rather than just being safe: this one really
     * does take a keystroke each, and a fast typist's backlog should collapse
     * to the prefix they ended on instead of being worked through a letter at
     * a time.
     */
    private val suggestRequests = MutableSharedFlow<SuggestRequest?>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val newestRequestId = AtomicLong(0L)

    /** Recent result pages, bounded by both entry count and retained row count. */
    private data class SearchCacheEntry(
        val rows: List<SearchResult>,
        val continuation: String?,
    )

    private data class SearchSession(
        val key: SearchRequestKey,
        val requestId: Long,
        val continuation: String?,
    )

    private data class SuggestRequest(val input: String, val key: SearchRequestKey)

    private val searchCache = BoundedRequestCache<SearchRequestKey, SearchCacheEntry>(
        viewModelScope, ttlMs = 120_000L, maxEntries = 12, maxWeight = 600,
        weightOf = { it.rows.size },
    )
    private var searchSession: SearchSession? = null
    private var activeSearchKey: SearchRequestKey? = null
    private var searchPaginationJob: Job? = null

    /** Synced lyrics for whatever is playing; null while unknown or absent. */
    private val _lyrics = MutableStateFlow<List<LyricLine>?>(null)
    val lyrics: StateFlow<List<LyricLine>?> = _lyrics.asStateFlow()

    /** Which of the four databases [lyrics] came from, for the panel's credit. */
    private val _lyricsSource = MutableStateFlow<LyricsSource?>(null)
    val lyricsSource: StateFlow<LyricsSource?> = _lyricsSource.asStateFlow()

    /**
     * Whether the lookup for the current track has finished. [lyrics] alone
     * can't tell "still looking" apart from "looked, found nothing" — both
     * are null — and the player needs that distinction to show "Lyrics not
     * available" only once it actually means that.
     */
    private val _lyricsChecked = MutableStateFlow(false)
    val lyricsChecked: StateFlow<Boolean> = _lyricsChecked.asStateFlow()
    private val _lyricsMissing = MutableStateFlow(false)
    val lyricsMissing: StateFlow<Boolean> = _lyricsMissing.asStateFlow()

    private var lyricsJob: Job? = null
    private val manualLyricsJobs = mutableMapOf<LyricsSource, Job>()

    private val _lyricsProviderStates = MutableStateFlow(
        LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED },
    )
    val lyricsProviderStates: StateFlow<Map<LyricsSource, LyricsProviderState>> =
        _lyricsProviderStates.asStateFlow()

    /** Completed hits are retained for the playing track so choosing one is instant. */
    private val lyricsProviderResults = ConcurrentHashMap<LyricsSource, LyricsRepository.Result>()

    private data class LyricsRequest(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val album: String?,
        val isExplicit: Boolean?,
    )

    private var currentLyricsRequest: LyricsRequest? = null
    private var lyricsGeneration = 0L
    private var selectedLyricsSource: LyricsSource? = null

    /**
     * What the loaded lyrics are for. Both the track *and* the settings that
     * chose them, so switching a source on or off re-runs the lookup rather
     * than leaving the last answer sitting on a player that would now find a
     * different one.
     */
    private data class LyricsLookupKey(
        val videoId: String,
        val sources: Set<LyricsSource>,
        val isExplicit: Boolean?,
        val order: List<LyricsSource>,
        val syllableSync: Boolean,
        val localUri: String?,
    )
    private var lyricsFor: LyricsLookupKey? = null

    /**
     * Called as the playing track changes; cheap no-op when already loaded.
     *
     * [localUri] is the file this track plays from when it is on the device,
     * and it is tried before the network: a downloaded track had its lyrics
     * fetched once already and written into its own file (see `LyricsTag`), so
     * asking the same servers again is a round trip to arrive at a string that
     * is on disk — and one that fails outright with the connection off, which
     * is what made a downloaded song show nothing offline.
     */
    fun loadLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        localUri: String? = null,
        isExplicit: Boolean? = null,
    ) {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        val key = LyricsLookupKey(videoId, sources, isExplicit, AppSettings.lyricsSourceOrder.value,
            AppSettings.prioritizeSyllableSync.value, localUri)
        if (lyricsFor == key) return
        // The duration lands a beat after the track, and a database match needs
        // it. Turned away here rather than inside the job below: claiming the
        // lookup first and giving it up asynchronously means the very re-trigger
        // that carries the duration can arrive while the claim still stands, be
        // dropped as a duplicate, and leave the track marked as being looked up
        // by nobody — which is what left a paused track loading for ever, since
        // pausing is when the duration is most likely to arrive a frame late.
        if (localUri == null && durationMs <= 0L && sources.isNotEmpty()) {
            if (lyricsFor?.videoId != videoId) {
                lyricsGeneration += 1
                selectedLyricsSource = null
                currentLyricsRequest = null
                lyricsJob?.cancel()
                manualLyricsJobs.values.forEach(Job::cancel)
                manualLyricsJobs.clear()
                lyricsProviderResults.clear()
                _lyricsProviderStates.value = LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED }
                lyricsFor = null
                _lyrics.value = null
                _lyricsSource.value = null
                _lyricsChecked.value = false
                _lyricsMissing.value = false
            }
            return
        }
        lyricsFor = key
        lyricsGeneration += 1
        val generation = lyricsGeneration
        currentLyricsRequest = LyricsRequest(videoId, title, artist, durationMs, album, isExplicit)
        selectedLyricsSource = null
        manualLyricsJobs.values.forEach(Job::cancel)
        manualLyricsJobs.clear()
        lyricsProviderResults.clear()
        _lyricsProviderStates.value =
            LyricsSource.entries.associateWith { LyricsProviderState.NOT_FETCHED }
        _lyrics.value = null
        _lyricsSource.value = null
        _lyricsMissing.value = false
        lyricsJob?.cancel()
        if (sources.isEmpty()) {
            // Switched off, or every source unticked. Nothing to look up, and
            // nothing to say about it — the player drops the lyric strip
            // rather than reporting a track with no lyrics.
            _lyricsChecked.value = true
            return
        }
        _lyricsChecked.value = false
        lyricsJob = viewModelScope.launch {
            // The file first, and without the duration gate below: a length is
            // only needed to *match* a track against a stranger's database, and
            // nothing is being matched here — these lyrics were written into
            // this exact file, for this exact recording.
            if (localUri != null) {
                EmbeddedLyrics.forUri(getApplication(), localUri)?.let { embedded ->
                    if (generation != lyricsGeneration) return@launch
                    _lyrics.value = embedded
                    // No source to name: what the file records is the lyrics,
                    // not which of the eight services they came from months ago.
                    _lyricsSource.value = null
                    _lyricsChecked.value = true
                    return@launch
                }
            }
            if (durationMs <= 0L) {
                // Duration arrives a beat after the track does; wait for it.
                lyricsFor = null
                return@launch
            }
            val found = LyricsRepository.lyrics(
                videoId, title, artist, durationMs, album, sources,
                AppSettings.lyricsSourceOrder.value, AppSettings.prioritizeSyllableSync.value,
                isExplicit = isExplicit,
                onSourceStarted = { source -> providerStarted(generation, source) },
                onSourceResult = { source, result ->
                    providerFinished(generation, source, result)
                },
                onSourceCancelled = { source -> providerCancelled(generation, source) },
                onSourceFailed = { source -> providerFailed(generation, source) },
                onLookupFinished = { missing ->
                    if (generation == lyricsGeneration) _lyricsMissing.value = missing
                },
            )
            if (generation != lyricsGeneration) return@launch
            val selected = selectedLyricsSource?.let(lyricsProviderResults::get) ?: found
            if (selected != null) _lyricsMissing.value = false
            _lyrics.value = selected?.lines
            _lyricsSource.value = selected?.source
            _lyricsChecked.value = true
        }
    }

    /**
     * Selects a provider from the player drawer. Completed hits are applied
     * from memory; misses are inert; only an untouched provider goes online.
     */
    fun selectLyricsProvider(source: LyricsSource) {
        val request = currentLyricsRequest ?: return
        when (_lyricsProviderStates.value[source]) {
            LyricsProviderState.FOUND -> {
                selectedLyricsSource = source
                lyricsProviderResults[source]?.let(::showLyricsResult)
            }
            LyricsProviderState.FETCHING -> {
                // Apply it as soon as the already-running automatic attempt completes.
                selectedLyricsSource = source
            }
            LyricsProviderState.NOT_FETCHED, null -> {
                selectedLyricsSource = source
                fetchLyricsProvider(request, lyricsGeneration, source)
            }
            LyricsProviderState.NOT_FOUND -> Unit
        }
    }

    private fun fetchLyricsProvider(
        request: LyricsRequest,
        generation: Long,
        source: LyricsSource,
    ) {
        if (manualLyricsJobs[source]?.isActive == true) return
        manualLyricsJobs[source] = viewModelScope.launch {
            LyricsRepository.lyrics(
                videoId = request.videoId,
                title = request.title,
                artist = request.artist,
                durationMs = request.durationMs,
                album = request.album,
                sources = setOf(source),
                order = listOf(source),
                prioritizeSyllableSync = false,
                isExplicit = request.isExplicit,
                onSourceStarted = { provider -> providerStarted(generation, provider) },
                onSourceResult = { provider, result ->
                    providerFinished(generation, provider, result)
                },
                onSourceCancelled = { provider -> providerCancelled(generation, provider) },
                onSourceFailed = { provider -> providerFailed(generation, provider) },
            )
        }
    }

    private fun providerStarted(generation: Long, source: LyricsSource) {
        if (generation != lyricsGeneration) return
        _lyricsProviderStates.update { it + (source to LyricsProviderState.FETCHING) }
    }

    private fun providerFailed(generation: Long, source: LyricsSource) {
        if (generation != lyricsGeneration) return
        _lyricsProviderStates.update { it + (source to LyricsProviderState.NOT_FETCHED) }
    }

    private fun providerFinished(
        generation: Long,
        source: LyricsSource,
        result: LyricsRepository.Result?,
    ) {
        if (generation != lyricsGeneration) return
        if (result == null) {
            _lyricsProviderStates.update { it + (source to LyricsProviderState.NOT_FOUND) }
            return
        }
        lyricsProviderResults[source] = result
        _lyricsProviderStates.update { it + (source to LyricsProviderState.FOUND) }
        if (selectedLyricsSource == source) showLyricsResult(result)
    }

    private fun providerCancelled(generation: Long, source: LyricsSource) {
        if (generation != lyricsGeneration) return
        _lyricsProviderStates.update { states ->
            if (states[source] == LyricsProviderState.FETCHING) {
                states + (source to LyricsProviderState.NOT_FETCHED)
            } else {
                states
            }
        }
        // A tap may have selected a provider while the priority race was still
        // using it. If that race then cancels the loser, honour the tap with a
        // dedicated request instead of leaving the row stuck at "Fetching".
        if (selectedLyricsSource == source) {
            currentLyricsRequest?.let { fetchLyricsProvider(it, generation, source) }
        }
    }

    private fun showLyricsResult(result: LyricsRepository.Result) {
        _lyricsMissing.value = false
        _lyrics.value = result.lines
        _lyricsSource.value = result.source
        _lyricsChecked.value = true
    }

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    /**
     * The channels this login can act as. Empty until the picker asks for
     * them — it is one more request per sign-in and nothing else on the
     * settings page needs the answer.
     */
    private val _channels = MutableStateFlow<List<AccountChannel>>(emptyList())
    val channels: StateFlow<List<AccountChannel>> = _channels.asStateFlow()

    private val _channelsLoading = MutableStateFlow(false)
    val channelsLoading: StateFlow<Boolean> = _channelsLoading.asStateFlow()

    /**
     * The chosen channel's [AccountChannel.key], or null while the app is
     * acting as whichever channel YouTube Music serves by default.
     */
    private val _selectedChannelKey = MutableStateFlow(
        authStore.activeSession?.activeProfileId ?: authStore.channelPageId ?: authStore.channelDataSyncId,
    )
    val selectedChannelKey: StateFlow<String?> = _selectedChannelKey.asStateFlow()

    private val _selectedChannelName = MutableStateFlow(
        authStore.activeSession?.profiles?.firstOrNull { it.profileId == authStore.activeProfileId }?.name
            ?: authStore.channelName,
    )
    val selectedChannelName: StateFlow<String?> = _selectedChannelName.asStateFlow()

    /** Source of truth for Google sessions and their YouTube identities. */
    private val _googleAccounts = MutableStateFlow(authStore.sessions)
    val googleAccounts: StateFlow<List<GoogleAccountSession>> = _googleAccounts.asStateFlow()
    private val _activeAccountId = MutableStateFlow(authStore.activeSession?.accountId)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()
    private val _activeProfileId = MutableStateFlow(authStore.activeProfileId)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _history = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val history: StateFlow<UiState<List<Song>>> = _history.asStateFlow()

    private val _library = MutableStateFlow<UiState<LibraryPage>>(UiState.Loading)
    val library: StateFlow<UiState<LibraryPage>> = _library.asStateFlow()
    private val latestLibraryRequest = LatestLibraryRequest()
    private var likedSnapshot: CollectionSnapshot? = null
    private var likedRevision = 0L
    val playedSpotifyCollections = combine(AppSettings.spotifySpdcToken, CollectionMetadataStore.changes) { cookie, _ ->
        val scope = spotifyMetadataScope(cookie)
        scope to CollectionMetadataStore.read(scope).filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null to emptyList())

    /** In-memory cache is partitioned by account and profile; it is never shared. */
    private data class ListenerSnapshot(
        val account: Account?, val library: UiState<LibraryPage>,
        val history: UiState<List<Song>>, val playlists: List<UserPlaylist>,
        val owned: Map<String, Boolean>,
    )
    private val listenerCache = mutableMapOf<String, ListenerSnapshot>()

    /**
     * Album / artist / playlist pages, as a stack — opening an artist from an
     * album page and pressing back returns to the album, not to search.
     */
    private val _detailStack = MutableStateFlow<List<DetailPage>>(emptyList())
    val detailStack: StateFlow<List<DetailPage>> = _detailStack.asStateFlow()
    private val detailJobs = mutableMapOf<Long, Job>()
    private val detailInstanceIds = AtomicLong(0L)

    private val _releaseLibrary = MutableStateFlow<Map<String, LibraryState>>(emptyMap())

    /**
     * Library state of releases that are only a card on some page — the artist
     * page's top release — keyed by browse id. They have no page of their own on
     * the stack to carry it, so it is read off the release and kept here.
     */
    val releaseLibrary: StateFlow<Map<String, LibraryState>> = _releaseLibrary.asStateFlow()

    /** Reads whether the release [browseId] is saved, once; a no-op if already known. */
    fun loadReleaseLibrary(browseId: String) {
        if (browseId in _releaseLibrary.value) return
        viewModelScope.launch {
            YtMusicRepository.releaseLibraryState(browseId).getOrNull()?.let { state ->
                _releaseLibrary.value += (browseId to state)
            }
        }
    }

    /** Saves the release [browseId] to the library or takes it out, as [toggleLibrary] does for a page. */
    fun toggleReleaseLibrary(browseId: String) {
        if (!requireSignIn()) return
        viewModelScope.launch {
            val current = _releaseLibrary.value[browseId]
                ?: YtMusicRepository.releaseLibraryState(browseId).getOrNull()
                ?: return@launch
            val target = !current.saved
            _releaseLibrary.value += (browseId to current.copy(saved = target))
            if (YtMusicRepository.setSaved(current.playlistId, target).isSuccess) {
                libraryStale = true
            } else {
                _releaseLibrary.value += (browseId to current)
            }
        }
    }


    /** Set once per launch if GitHub has a release newer than this build. */
    val updateAvailable: StateFlow<AppUpdateChecker.UpdateInfo?> = AppUpdateChecker.available

    // ---- Ratings, library and playlists -------------------------------------

    /**
     * Ratings this session has set, which win over whatever the library feed
     * last said.
     *
     * Kept apart from the library rather than folded into it because the two
     * answer different questions: Liked Music is what YouTube knew when the
     * page was fetched, and this is what the user has done since. Layering
     * them ([likeStatuses]) means a tap shows immediately without the library
     * having to be re-fetched, and a later refresh can't undo it.
     */
    /** Every rating known for this account: the library's, then this session's. */
    val likeStatuses: StateFlow<Map<String, LikeStatus>> =
        combine(_library, LikeState.overrides) { library, overrides ->
            val liked = (library as? UiState.Success)?.data?.likedSongs
                ?.associate { it.videoId to LikeStatus.LIKE }
                .orEmpty()
            liked + overrides
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun likeStatusOf(videoId: String): LikeStatus =
        likeStatuses.value[videoId] ?: LikeStatus.INDIFFERENT

    /**
     * Sets (or clears) the thumbs rating on [videoId].
     *
     * Written to the screen first and rolled back if YouTube refuses. A rating
     * is a one-tap, low-stakes action taken while a song is playing; waiting
     * on a round trip before the heart fills reads as the tap not having
     * registered, and people tap again.
     */
    fun setLike(videoId: String, status: LikeStatus) {
        val previous = likeStatusOf(videoId)
        val localDislikeChange = status == LikeStatus.DISLIKE || previous == LikeStatus.DISLIKE
        if (!localDislikeChange && !requireSignIn()) return
        val identity = listenerKey()
        if (previous == status) return
        LikeState.set(videoId, status)
        // Dislike is a local playback preference too, including when signed out.
        // An unavailable network must not bring a rejected song back into the mix.
        if (!_signedIn.value || videoId.startsWith("local:")) return
        viewModelScope.launch {
            YtMusicRepository.rate(videoId, status).fold(
                onSuccess = {
                    if (identity != listenerKey()) return@fold
                    likedRevision++
                    likedSyncJob?.cancel()
                    likedSnapshot = likedSnapshot?.copy(updatedAt = 0)
                    likedSnapshot?.let { CollectionMetadataStore.save(likedMetadataScope(identity), it) }
                    // Liked Music is now out of date either way.
                    libraryStale = true
                    if (status != LikeStatus.LIKE) dropFromLikedLists(videoId)
                    // Clearing the heart means forgetting the song, not
                    // demoting it — see [forgetFromLibrary].
                    val unliked = previous == LikeStatus.LIKE &&
                        status == LikeStatus.INDIFFERENT
                    if (unliked) forgetFromLibrary(videoId)
                },
                onFailure = {
                    if (identity == listenerKey() && !localDislikeChange) LikeState.set(videoId, previous)
                },
            )
        }
    }

    /**
     * Takes an un-liked track out of the library as well, and reports whether
     * it did.
     *
     * Liking and saving are two independent flags on YouTube's side, and
     * clearing only the first leaves the song saved — still feeding the
     * Library tab's Artists shelf, still in the library feeds, with nowhere
     * left in this app to reach it and finish the job. Clearing the heart
     * reads as "forget this song", so it clears both.
     *
     * The token is fetched here rather than taken from [songMenu] because the
     * heart in the player never opens a menu, so there is often nothing
     * cached to take. One extra request, on an action nobody performs in bulk.
     * A song that was never saved has no removal token and this is a no-op.
     */
    private suspend fun forgetFromLibrary(videoId: String): Boolean {
        val menu = YtMusicRepository.songMenu(videoId).getOrNull() ?: return false
        val token = menu.removeFromLibraryToken?.takeIf { menu.inLibrary } ?: return false
        if (YtMusicRepository.setLibraryStatus(token).isFailure) return false
        // The menu may be the one on screen; don't leave it offering a
        // removal that has already happened.
        _songMenu.value = _songMenu.value?.copy(inLibrary = false)
        return true
    }

    /**
     * Takes an un-liked track out of the lists that exist *because* it was
     * liked — the Library tab's Liked Music section, and the Liked Music page
     * itself if it happens to be open.
     *
     * Marking the library stale isn't enough on its own: that only acts when
     * the tab is next opened, and un-liking is nearly always done from inside
     * one of these two lists, looking straight at the row. Leaving it there
     * reads as the tap not having worked — the menu says "Like" again while
     * the song sits in Liked Music.
     *
     * Only ever removes. A track liked from somewhere else doesn't get spliced
     * into a list that YouTube orders for itself; the next fetch places it.
     */
    private fun dropFromLikedLists(videoId: String) {
        likedRevision++
        likedSyncJob?.cancel()
        val identity = listenerKey()
        likedSnapshot?.let { saved ->
            val changed = saved.copy(songs = saved.songs.filterNot { it.videoId == videoId }, updatedAt = 0)
            likedSnapshot = changed
            viewModelScope.launch { CollectionMetadataStore.save(likedMetadataScope(identity), changed) }
        }
        com.music.bitchord.playback.DaylightMixRepository.remove(videoId)
        val library = (_library.value as? UiState.Success)?.data
        if (library != null && library.likedSongs.any { it.videoId == videoId }) {
            _library.value = UiState.Success(
                library.copy(likedSongs = library.likedSongs.filterNot { it.videoId == videoId }),
            )
        }
        _detailStack.value = _detailStack.value.map { page ->
            val songs = (page.songs as? UiState.Success)?.data
            if (page.browseId != YtMusicRepository.LIKED_MUSIC || songs == null) {
                page
            } else {
                page.copy(songs = UiState.Success(songs.filterNot { it.videoId == videoId }))
            }
        }
    }

    /** The heart: liked becomes neutral, anything else becomes liked. */
    fun toggleLike(videoId: String) = setLike(
        videoId,
        if (likeStatusOf(videoId) == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE,
    )

    /** As [toggleLike], for the thumb-down. */
    fun toggleDislike(videoId: String): LikeStatus? {
        val previous = likeStatusOf(videoId)
        setLike(
            videoId,
            if (previous == LikeStatus.DISLIKE) {
                LikeStatus.INDIFFERENT
            } else {
                LikeStatus.DISLIKE
            },
        )
        return previous
    }

    /**
     * Saves the album or playlist [browseId] to the library, or takes it out.
     *
     * Written to the screen first and rolled back if YouTube refuses, for the
     * same reason [setLike] is: it is one tap on a page the user is looking at,
     * and a control that waits on a round trip before it changes reads as a tap
     * that missed.
     *
     * A page with no [DetailPage.library] is one YouTube never offered to save
     * — a local page, an auto-playlist, a generated mix — and the UI has no
     * control on it to have been tapped, so this is a no-op rather than a guess.
     */
    fun toggleLibrary(browseId: String) {
        if (!requireSignIn()) return
        val current = _detailStack.value.firstOrNull { it.browseId == browseId }?.library ?: return
        val target = !current.saved
        setSavedOnPage(browseId, target)
        viewModelScope.launch {
            if (YtMusicRepository.setSaved(current.playlistId, target).isSuccess) {
                // The Library tab's Albums/Playlists shelf is now out of date.
                libraryStale = true
            } else {
                setSavedOnPage(browseId, current.saved)
            }
        }
    }

    /**
     * Subscribes to the artist page [browseId]'s channel, or unsubscribes.
     *
     * The release equivalent is [toggleLibrary], and this behaves the same way:
     * optimistic, rolled back on refusal, and a no-op on a page whose header
     * never offered a subscribe button — a signed-out response among them.
     */
    fun toggleSubscription(browseId: String) {
        if (!requireSignIn()) return
        val current = _detailStack.value
            .firstOrNull { it.browseId == browseId }?.subscription ?: return
        val target = !current.subscribed
        setSubscribedOnPage(browseId, target)
        viewModelScope.launch {
            if (YtMusicRepository.setSubscribed(current.channelId, target).isSuccess) {
                // The Library tab's Subscriptions shelf is now out of date.
                libraryStale = true
            } else {
                setSubscribedOnPage(browseId, current.subscribed)
            }
        }
    }

    /** As [setSavedOnPage], for the artist header's subscribe button. */
    private fun setSubscribedOnPage(browseId: String, subscribed: Boolean) {
        _detailStack.value = _detailStack.value.map { page ->
            val subscription = page.subscription
            if (page.browseId != browseId || subscription == null) {
                page
            } else {
                page.copy(subscription = subscription.copy(subscribed = subscribed))
            }
        }
    }

    /**
     * Restates whether a page is saved. By id rather than by index: the user may
     * have pushed or popped pages while the write was in flight.
     */
    private fun setSavedOnPage(browseId: String, saved: Boolean) {
        _detailStack.value = _detailStack.value.map { page ->
            val library = page.library
            if (page.browseId != browseId || library == null) {
                page
            } else {
                page.copy(library = library.copy(saved = saved))
            }
        }
    }

    /**
     * The open track menu's account state, or null while it is still being
     * fetched. Only one menu can be open at a time, so one slot is enough.
     */
    private val _songMenu = MutableStateFlow<SongMenu?>(null)
    val songMenu: StateFlow<SongMenu?> = _songMenu.asStateFlow()

    private var songMenuJob: Job? = null

    /** In-flight liked-library continuation sync — see [syncLikedMusic]. */
    private var likedSyncJob: Job? = null

    /**
     * Loads the account state behind an opening track menu — the library
     * tokens, and any rating the response happens to state.
     *
     * The rating is only ever taken when it *adds* something: a LIKE or a
     * DISLIKE the library couldn't have told us, such as a disliked track or
     * one liked past the tenth page of Liked Music. An INDIFFERENT is
     * discarded.
     *
     * That asymmetry is not fussiness. This lookup reads a watch queue, and a
     * watch queue routinely renders a liked track with no rating on it at all;
     * believing that silence downgraded songs sitting in Liked Music to
     * "not liked" a beat after their menu opened — the label changing under
     * the user, with no request sent and nothing removed.
     */
    fun loadSongMenu(videoId: String?) {
        songMenuJob?.cancel()
        _songMenu.value = null
        if (videoId == null || !_signedIn.value) return
        songMenuJob = viewModelScope.launch {
            val menu = YtMusicRepository.songMenu(videoId).getOrNull() ?: return@launch
            _songMenu.value = menu
            LikeState.rememberStated(videoId, menu.likeStatus)
        }
    }

    /** The account's own playlists, for the picker and the library tab. */
    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()
    private val pendingPlaylistCreations = PendingPlaylistCreations()

    private val _playlistsLoading = MutableStateFlow(false)
    val playlistsLoading: StateFlow<Boolean> = _playlistsLoading.asStateFlow()

    /** Re-fetched rather than cached for the session: playlists are edited here. */
    fun loadPlaylists(videoId: String? = null) {
        if (!_signedIn.value || _playlistsLoading.value) return
        val identity = listenerKey()
        _playlistsLoading.value = true
        viewModelScope.launch {
            YtMusicRepository.userPlaylists(videoId).onSuccess {
                if (identity == listenerKey()) _playlists.value = pendingPlaylistCreations.mergeOwnPlaylists(identity, it)
            }
            if (identity == listenerKey()) _playlistsLoading.value = false
        }
    }

    /**
     * The library feed's Playlists shelf, rewritten by [edit].
     *
     * Every playlist edit has to do this by hand, because the library tab reads
     * `_library` and nothing else — [playlists] is the picker's list, not the
     * tab's — so a rename that only updated that list left the card on screen
     * still bearing the old name.
     *
     * Re-fetching instead is what this replaces, and it did not work: YouTube's
     * `FEmusic_liked_playlists` is eventually consistent, and a fetch fired the
     * moment an edit returns reliably answers with the state from *before* it.
     * So the edit was applied, the feed denied it, and the denial is what
     * reached the screen — the bug this exists to fix. The re-fetch still
     * happens, via [libraryStale], once the tab is next opened and the feed has
     * caught up.
     *
     * A shelf that isn't there yet is created by [edit] returning rows for it
     * (a first playlist has no shelf to add to), and one left empty is dropped —
     * see [LibraryScreen], which draws the create tile with or without a shelf.
     */
    private fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (_library.value as? UiState.Success)?.data ?: return
        val existing = page.shelves.firstOrNull { it.title == YtMusicRepository.PLAYLISTS_SHELF }
        val items = edit(existing?.items.orEmpty())
        if (items == existing?.items) return
        val shelves = when {
            existing == null && items.isEmpty() -> return
            // No shelf yet: this is the account's first playlist, so the feed
            // has never had one to send. Leads the page, as the feed orders it.
            existing == null ->
                listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, items)) + page.shelves
            // Emptied by deleting the last playlist. Dropped rather than left as
            // a heading over nothing; the create tile is drawn either way.
            items.isEmpty() -> page.shelves.filterNot { it === existing }
            else -> page.shelves.map { if (it === existing) existing.copy(items = items) else it }
        }
        _library.value = UiState.Success(page.copy(shelves = shelves))
    }

    /**
     * Restates a playlist's name everywhere it is currently drawn: its card in
     * the library, the picker's list, and its own open page — header and top
     * bar both, which read [DetailPage.title].
     */
    private fun setPlaylistTitle(playlist: UserPlaylist, title: String) {
        pendingPlaylistCreations.rename(listenerKey(), playlist.browseId, title)
        _playlists.value = _playlists.value.map {
            if (it.playlistId == playlist.playlistId) it.copy(title = title) else it
        }
        editPlaylistShelf { items ->
            items.map { if (it.browseId == playlist.browseId) it.copy(title = title) else it }
        }
        _detailStack.value = _detailStack.value.map {
            if (it.browseId == playlist.browseId) it.copy(title = title) else it
        }
    }

    // Keep writes from the picker, suggestions and creation in one serial lane.
    private val playlistMutationGate = Mutex()

    /** Checks every destination before any write, so Cancel changes none of a batch. */
    fun preparePlaylistAdd(
        playlists: List<UserPlaylist>,
        song: Song,
        onResult: (Result<PlaylistAddPlan>) -> Unit,
    ) {
        val identity = listenerKey()
        viewModelScope.launch {
            val result = try {
                Result.success(planPlaylistAdd(playlists, song, identity) { playlist, track ->
                    if (playlist.playlistId.startsWith("local:playlist:")) {
                        val local = LocalPlaylistStore.getPlaylist(playlist.playlistId)
                            ?: error("Playlist no longer exists")
                        local.songs.any { it.videoId == track.videoId }
                    } else {
                        check(requireSignIn() && identity == listenerKey()) { "Account changed" }
                        YtMusicRepository.playlistContainsSong(playlist.browseId, track.videoId).getOrThrow()
                    }
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            onResult(result)
        }
    }

    /** A duplicate is written only after the caller explicitly accepts it. */
    fun commitPlaylistAdd(
        plan: PlaylistAddPlan,
        allowDuplicates: Boolean = false,
        onResult: (PlaylistAddResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = playlistMutationGate.withLock {
                executePlaylistAdd(plan, allowDuplicates) { playlist, song ->
                    if (playlist.playlistId.startsWith("local:playlist:")) {
                        val local = LocalPlaylistStore.getPlaylist(playlist.playlistId)
                            ?: return@executePlaylistAdd false
                        val saved = LocalPlaylistStore.addSong(local.id, song)
                        if (saved) appendToOpenPlaylist(local.browseId, song, null)
                        saved
                    } else {
                        if (!requireSignIn() || plan.accountKey != listenerKey()) return@executePlaylistAdd false
                        YtMusicRepository.addToPlaylist(playlist.playlistId, listOf(song.videoId)).fold(
                            onSuccess = { added ->
                                com.music.bitchord.data.library.LibraryPlaylistOrderStore.recordActivity(playlist.browseId)
                                libraryStale = true
                                appendToOpenPlaylist(playlist.browseId, song, added[song.videoId])
                                _detailStack.value = _detailStack.value.map { page ->
                                    if (page.browseId == playlist.browseId) page.copy(
                                        suggestedSongs = page.suggestedSongs.filterNot { it.videoId == song.videoId },
                                    ) else page
                                }
                                true
                            },
                            onFailure = { false },
                        )
                    }
                }
            }
            onResult(result)
        }
    }

    /** Seeds creation in one request and reports completion before the form closes. */
    fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        song: Song? = null,
        onResult: (Result<UserPlaylist>) -> Unit,
    ) {
        val name = title.trim().ifBlank { text(R.string.new_playlist) }
        val identity = listenerKey()
        viewModelScope.launch {
            val result = playlistMutationGate.withLock {
                val deviceOnly = song?.videoId?.let { it.startsWith("content:") || it.startsWith("file:") } == true
                if (!authStore.isSignedIn || deviceOnly) {
                    val local = LocalPlaylistStore.savePlaylist(name, listOfNotNull(song?.copy(setVideoId = null)))
                    Result.success(UserPlaylist(
                        playlistId = local.browseId,
                        title = local.title,
                        subtitle = getApplication<Application>().getString(R.string.local_playlist_subtitle, local.songs.size),
                        thumbnailUrl = song?.thumbnailUrl,
                    ))
                } else if (identity != listenerKey()) {
                    Result.failure(IllegalStateException("Account changed"))
                } else {
                    YtMusicRepository.createPlaylist(name, privacy, listOfNotNull(song?.videoId)).mapCatching { playlistId ->
                        check(identity == listenerKey()) { "Account changed" }
                        setPlaylistOwned("VL$playlistId", true)
                        libraryStale = true
                        val created = UserPlaylist(
                            playlistId = playlistId,
                            title = name,
                            subtitle = if (song != null) "1 song" else "",
                            thumbnailUrl = song?.thumbnailUrl,
                        )
                        com.music.bitchord.data.library.LibraryPlaylistOrderStore.recordCreated(created.browseId)
                        pendingPlaylistCreations.remember(identity, created)
                        _playlists.value = listOf(created) + _playlists.value.filterNot { it.playlistId == created.playlistId }
                        editPlaylistShelf { items ->
                            listOf(ShelfItem(
                                title = created.title,
                                subtitle = created.subtitle,
                                thumbnailUrl = created.thumbnailUrl,
                                videoId = null,
                                browseId = created.browseId,
                            )) + items.filterNot { it.browseId == created.browseId }
                        }
                        created
                    }
                }
            }
            onResult(result)
        }
    }

    fun createPlaylistWithVideoIds(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String>,
        songs: List<Song> = emptyList(),
        /** [savedLocally] is true when the playlist went to this device, not YouTube Music. */
        onResult: ((browseId: String?, title: String, savedLocally: Boolean, remoteAddedCount: Int?) -> Unit)? = null,
    ) {
        val name = title.trim().ifBlank { text(R.string.new_playlist) }
        val identity = listenerKey()
        viewModelScope.launch {
            if (authStore.isSignedIn) {
                com.music.bitchord.data.spotify.createPlaylistInBatches(
                    videoIds = videoIds,
                    create = { initial ->
                        if (identity == listenerKey()) YtMusicRepository.createPlaylist(name, privacy, initial)
                        else Result.failure(IllegalStateException("Account changed"))
                    },
                    append = { id, chunk ->
                        if (identity == listenerKey()) YtMusicRepository.addToPlaylist(id, chunk).map { Unit }
                        else Result.failure(IllegalStateException("Account changed"))
                    },
                ).fold(
                    onSuccess = { result ->
                        if (identity != listenerKey()) return@launch
                        val playlistId = result.playlistId
                        setPlaylistOwned("VL$playlistId", true)
                        libraryStale = true
                        val created = UserPlaylist(
                            playlistId = playlistId,
                            title = name,
                            subtitle = "${result.addedCount} songs",
                            thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                        )
                        com.music.bitchord.data.library.LibraryPlaylistOrderStore.recordCreated(created.browseId)
                        pendingPlaylistCreations.remember(identity, created)
                        _playlists.value = listOf(created) +
                            _playlists.value.filterNot { it.playlistId == created.playlistId }
                        editPlaylistShelf { items ->
                            listOf(
                                ShelfItem(
                                    title = created.title,
                                    subtitle = created.subtitle,
                                    thumbnailUrl = created.thumbnailUrl,
                                    videoId = null,
                                    browseId = created.browseId,
                                ),
                            ) + items.filterNot { it.browseId == created.browseId }
                        }
                        if (result.complete) {
                            onResult?.invoke(created.browseId, created.title, false, null)
                        } else {
                            // Keep every resolved track available even if a later server edit failed.
                            val local = com.music.bitchord.data.spotify.LocalPlaylistStore.savePlaylist(name, songs)
                            onResult?.invoke(local.browseId, local.title, true, result.addedCount)
                        }
                    },
                    onFailure = {
                        if (identity != listenerKey()) return@launch
                        val local = com.music.bitchord.data.spotify.LocalPlaylistStore.savePlaylist(name, songs)
                        onResult?.invoke(local.browseId, local.title, true, null)
                    },
                )
            } else {
                val local = com.music.bitchord.data.spotify.LocalPlaylistStore.savePlaylist(name, songs)
                onResult?.invoke(local.browseId, local.title, true, null)
            }
        }
    }

    /**
     * Drops [song] from the playlist page it is being read on, and takes the
     * row out from under the reader rather than waiting for a re-fetch.
     */
    fun removeFromPlaylist(browseId: String, song: Song) {
        val setVideoId = song.setVideoId ?: return
        if (!requireSignIn()) return
        val playlistId = browseId.removePrefix("VL")
        viewModelScope.launch {
            YtMusicRepository.removeFromPlaylist(
                playlistId,
                listOf(setVideoId to song.videoId),
            ).fold(
                onSuccess = {
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        val songs = (page.songs as? UiState.Success)?.data
                        if (page.browseId != browseId || songs == null) {
                            page
                        } else {
                            page.copy(
                                songs = UiState.Success(
                                    songs.filterNot { it.setVideoId == setVideoId },
                                ),
                            )
                        }
                    }
                },
                onFailure = {},
            )
        }
    }

    /**
     * Puts [song] at the end of the playlist page at [browseId], if that page
     * is open — where YouTube itself puts it, so the order survives the next
     * fetch.
     *
     * An empty playlist counts as open: it renders as an empty-state message, and the
     * first track added to one has to replace that message rather than be
     * dropped for want of a list to join. Only that message, though — any other
     * error is a page that failed to load, whose real contents are unknown, and
     * answering it with a one-track listing would be a playlist invented out of
     * a network failure. A page still loading is left alone too: the fetch in
     * flight is newer than this and will land with the addition already in it.
     */
    private fun appendToOpenPlaylist(browseId: String, song: Song, setVideoId: String?) {
        val added = song.copy(setVideoId = setVideoId)
        _detailStack.value = _detailStack.value.map { page ->
            if (page.browseId != browseId) return@map page
            val songs = when (val state = page.songs) {
                is UiState.Success -> state.data
                is UiState.Error -> if (state.message == text(R.string.no_tracks_here) ||
                    state.message == text(R.string.spotify_import_empty_playlist)
                ) emptyList() else return@map page
                UiState.Loading -> return@map page
            }
            // Only the same server entry can be redundant after an in-flight fetch.
            // A confirmed duplicate has a different setVideoId and remains a real row.
            if (setVideoId != null && songs.any { it.setVideoId == setVideoId }) return@map page
            page.copy(
                songs = UiState.Success(
                    songs + added.copy(
                        thumbnailUrl = added.thumbnailUrl ?: page.thumbnailUrl,
                    ),
                ),
            )
        }
    }

    /**
     * Renames a playlist, and says so everywhere it is named — see
     * [setPlaylistTitle]. Renaming is nearly always done from the playlist's
     * own page or its card, so there is always something on screen still
     * showing the old name.
     */
    fun renamePlaylist(playlist: UserPlaylist, title: String) {
        if (!requireSignIn()) return
        val name = title.trim()
        if (name.isBlank() || name == playlist.title) return
        viewModelScope.launch {
            YtMusicRepository.renamePlaylist(playlist.playlistId, name).fold(
                onSuccess = {
                    setPlaylistTitle(playlist, name)
                    libraryStale = true
                },
                onFailure = {},
            )
        }
    }

    /** Editing is account guarded; local creations never require sign-in. */
    fun editPlaylist(browseId: String, title: String, onResult: (Result<Unit>) -> Unit) {
        val name = title.trim()
        if (name.isBlank()) return onResult(Result.failure(IllegalArgumentException("Empty name")))
        val identity = listenerKey()
        viewModelScope.launch {
            val result = playlistMutationGate.withLock {
                val local = LocalPlaylistStore.getPlaylist(browseId)
                if (local != null) {
                    LocalPlaylistStore.renamePlaylist(local.id, name)
                    _detailStack.value = _detailStack.value.map { if (it.browseId == browseId) it.copy(title = name) else it }
                    Result.success(Unit)
                } else if (!requireSignIn() || identity != listenerKey() || _playlistOwned.value[browseId] != true) {
                    Result.failure(IllegalStateException("Playlist is not editable"))
                } else {
                    val existing = _playlists.value.firstOrNull { it.browseId == browseId }
                    val currentTitle = existing?.title ?: _detailStack.value.firstOrNull { it.browseId == browseId }?.title
                    val playlist = existing ?: UserPlaylist(browseId.removePrefix("VL"), name, "", null)
                    val rename = if (currentTitle == name) Result.success(Unit) else
                        YtMusicRepository.renamePlaylist(playlist.playlistId, name)
                    rename.mapCatching {
                        check(identity == listenerKey()) { "Account changed" }
                        setPlaylistTitle(playlist, name)
                        homeStale = true; libraryStale = true
                        val home = _home.value as? UiState.Success
                        if (home != null) _home.value = UiState.Success(home.data.map { shelf ->
                            shelf.copy(items = shelf.items.map { if (it.browseId == browseId) it.copy(title = name) else it })
                        })
                        Unit
                    }
                }
            }
            onResult(result)
        }
    }

    /**
     * The playlist's entries as they stand on YouTube, for the reorder sheet —
     * see [YtMusicRepository.playlistEntries] for why not the open page's list.
     */
    fun loadPlaylistEntries(playlist: UserPlaylist, onResult: (Result<List<Song>>) -> Unit) {
        if (!requireSignIn()) return onResult(Result.failure(IllegalStateException("signed out")))
        viewModelScope.launch {
            onResult(YtMusicRepository.playlistEntries(playlist.browseId))
        }
    }

    /**
     * Saves a new running order for [playlist], sent as the fewest moves that
     * get from [original] to [reordered], and puts the open page — if this
     * playlist's is — into that order straight away rather than after a
     * re-fetch the feed may answer with the old order.
     */
    fun reorderPlaylist(
        playlist: UserPlaylist,
        original: List<Song>,
        reordered: List<Song>,
        onResult: (Boolean) -> Unit = {},
    ) {
        if (!requireSignIn()) return onResult(false)
        viewModelScope.launch {
            YtMusicRepository.reorderPlaylist(
                playlist.playlistId,
                current = original.mapNotNull { it.setVideoId },
                target = reordered.mapNotNull { it.setVideoId },
            ).fold(
                onSuccess = {
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        if (page.browseId != playlist.browseId || page.songs !is UiState.Success) {
                            page
                        } else {
                            page.copy(songs = UiState.Success(reordered.withArtwork(page.thumbnailUrl)))
                        }
                    }
                    onResult(true)
                },
                onFailure = { onResult(false) },
            )
        }
    }

    fun deletePlaylist(playlist: UserPlaylist) {
        if (!requireSignIn()) return
        viewModelScope.launch {
            YtMusicRepository.deletePlaylist(playlist.playlistId).fold(
                onSuccess = {
                    pendingPlaylistCreations.remove(listenerKey(), playlist.browseId)
                    _playlists.value = _playlists.value
                        .filterNot { it.playlistId == playlist.playlistId }
                    // The card in the library tab, which is the surface the
                    // deletion was almost certainly ordered from — and which the
                    // re-fetch that used to stand in for this left in place; see
                    // [editPlaylistShelf].
                    editPlaylistShelf { items ->
                        items.filterNot { it.browseId == playlist.browseId }
                    }
                    // Its page may be the one open; a deleted playlist has
                    // nothing left to show.
                    _detailStack.value = _detailStack.value
                        .filterNot { it.browseId == playlist.browseId }
                    cancelMissingDetailJobs()
                    libraryStale = true
                },
                onFailure = {},
            )
        }
    }

    /**
     * Whether [browseId] is a playlist this account can be asked to edit.
     *
     * Only ever yes for a playlist [playlistOwned] has confirmed the account
     * made. "In this account's library" is not the same thing and cannot stand
     * in for it: `FEmusic_liked_playlists` lists a playlist saved from someone
     * else in exactly the shape it lists one this account created, so a lookup
     * in [playlists] alone called a stranger's playlist editable and the menus
     * offered Rename and Delete on it — neither of which YouTube would have
     * honoured.
     *
     * Strict rather than permissive-until-proven, so that every surface gives
     * the same answer for the same playlist. The permissive version was right on
     * a playlist's own page — where the page load supplies the answer — and
     * wrong on a card until that page had been opened once, which is a menu that
     * changes its mind about what a playlist is depending on where it is held.
     */
    fun editablePlaylist(browseId: String?): UserPlaylist? {
        if (browseId == null || _playlistOwned.value[browseId] != true) return null
        return _playlists.value.firstOrNull { it.browseId == browseId }
    }

    /**
     * Which playlists in this account's library the account actually made, by
     * browse id — see
     * [com.music.bitchord.data.innertube.InnertubeParser.parsePlaylistOwned].
     * An id absent from the map is one nothing has asked about yet, which is not
     * the same as a no.
     *
     * Observable, because the answer routinely arrives after whatever wanted it
     * is already on screen: a card's menu opens with nothing fetched, and the
     * rows that depend on this appear as [resolvePlaylistOwnership] answers.
     */
    private val _playlistOwned = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val playlistOwned: StateFlow<Map<String, Boolean>> = _playlistOwned.asStateFlow()

    /**
     * Finds out who made the playlist at [browseId], if it isn't already known.
     *
     * Only the playlist's own page states this, so a surface that has no page —
     * a card in the library, a search result — has to ask for one. Which is why
     * this is on demand rather than swept up front: the alternative is a request
     * per playlist every time the library loads, for a question most of them
     * will never be asked.
     *
     * Silent about anything that isn't a playlist in this account's library.
     * Nothing else can be renamed or deleted whatever the answer, so asking
     * would be a request spent to rule out what was never on offer.
     */
    fun resolvePlaylistOwnership(browseId: String?) {
        if (!_signedIn.value || browseId == null) return
        if (browseId in _playlistOwned.value || browseId in ownershipInFlight) return
        if (_playlists.value.none { it.browseId == browseId }) return
        val identity = listenerKey()
        val requestScope = Innertube.responseCacheScope
        val request = Any()
        ownershipInFlight[browseId] = request
        viewModelScope.launch {
            try {
                YtMusicRepository.playlistOwned(browseId).onSuccess { owned ->
                    if (owned != null && identity == listenerKey() && requestScope == Innertube.responseCacheScope) {
                        setPlaylistOwned(browseId, owned)
                    }
                }
            } finally {
                // An older account's completion cannot release a new account's
                // lookup of the same browse id after the identity changed.
                if (ownershipInFlight[browseId] === request) ownershipInFlight.remove(browseId)
            }
        }
    }

    /** Guards against a second lookup while the first is still out. */
    private val ownershipInFlight = mutableMapOf<String, Any>()

    private fun setPlaylistOwned(browseId: String, owned: Boolean) {
        _playlistOwned.value = _playlistOwned.value + (browseId to owned)
    }


    /**
     * Guards every account write. All of them are signed-in-only, and the UI
     * hides them for guests — this is the backstop for a session that expired
     * between the menu opening and the tap.
     */
    private fun requireSignIn(): Boolean = _signedIn.value

    /**
     * Whether the library needs re-fetching. Set by every write above and
     * acted on when the tab is next opened, for the same reason [homeStale]
     * exists: rearranging a page under whoever is reading it is worse than
     * showing it a moment out of date.
     */
    private var libraryStale = false

    /** Call when the library tab becomes visible. */
    fun onLibraryShown() {
        loadPlaylists()
        if (!libraryStale) return
        libraryStale = false
        if (_library.value is UiState.Success) refresh(Feed.LIBRARY)
    }

    init {
        startSearchPipeline()
        startSuggestPipeline()
        startTypeaheadMediaPipeline()
        loadHome()
        loadExplore()
        if (_signedIn.value) {
            loadLibrary()
            loadAccount()
            loadPlaylists()
        }
        viewModelScope.launch {
            // drop(1): the current value is just the count so far, not a play.
            PlaybackTracker.registeredPlays.drop(1).collect { homeStale = true }
        }
        viewModelScope.launch {
            AppSettings.filterNonMusicAudio.drop(1).collect {
                if (_detailStack.value.any { page -> page.browseId == "local:all" }) {
                    reloadLocalDetail("local:all")
                }
            }
        }
        viewModelScope.launch {
            // Emptied from Settings while the folder sits open underneath.
            AudioCache.contentsChanged.drop(1).collect {
                if (_detailStack.value.any { page -> page.browseId == CACHE_FOLDER_BROWSE_ID }) {
                    reloadLocalDetail(CACHE_FOLDER_BROWSE_ID)
                }
            }
        }
        viewModelScope.launch {
            // A leftover APK only means "Install Now" for the session that
            // downloaded it — see AppUpdateChecker.clearCache.
            AppUpdateChecker.clearCache(getApplication())
            AppUpdateChecker.check()
        }
    }

    /**
     * Whether a play has been registered since the home feed was last fetched.
     *
     * The feed leads with listening history, so it's out of date the moment a
     * track starts — but re-fetching there would rearrange the page under
     * whoever is reading it, and the tab is usually in the background anyway.
     * It's re-fetched when the tab is next opened instead.
     */
    private var homeStale = false

    /** Call when the home tab becomes visible. */
    fun onHomeShown() {
        if (!homeStale) return
        homeStale = false
        // A first load already in flight will pick the new play up by itself.
        if (_home.value is UiState.Success) refresh(Feed.HOME)
    }

    private fun loadAccount() {
        val identity = listenerKey()
        viewModelScope.launch {
            val account = YtMusicRepository.account().getOrNull()
            if (identity != listenerKey()) return@launch
            _account.value = account
            // A channel picked in the in-app browser arrives as ids and nothing
            // else — the page's `ytcfg` never says what the channel is called.
            // The account menu, asked *as* that channel, answers with its name,
            // which is what the settings row needs to show.
            if (account != null && _selectedChannelKey.value != null) {
                _selectedChannelName.value = account.name
                val accountId = _activeAccountId.value
                val profileId = _activeProfileId.value
                val session = accountId?.let { id -> authStore.sessions.firstOrNull { it.accountId == id } }
                val updated = session?.let { saved -> saved.copy(
                    name = account.name, email = account.email,
                    profiles = saved.profiles.map { profile ->
                        if (profile.profileId == profileId) profile.copy(
                            name = account.name, handle = account.email, avatar = account.thumbnailUrl,
                        ) else profile
                    },
                ) }
                if (updated != null) {
                    authStore.upsertSession(updated, activate = false)
                    _googleAccounts.value = authStore.sessions
                }
            }
        }
    }

    /**
     * A feed that can be pulled down to refresh. Tracked per feed rather than
     * as one flag: a pull on Library while Home is still refreshing in the
     * background shouldn't leave the wrong tab showing a loader.
     */
    enum class Feed { HOME, EXPLORE, LIBRARY }

    private val _refreshing = MutableStateFlow(emptySet<Feed>())
    val refreshing: StateFlow<Set<Feed>> = _refreshing.asStateFlow()

    /**
     * Re-fetches [feed] in place. Unlike the `load*` entry points this leaves
     * the current content on screen rather than dropping back to the loading
     * state — a refresh that swapped the page for a spinner would be a worse
     * experience than the stale content it replaces.
     */
    fun refresh(feed: Feed) {
        if (feed in _refreshing.value) return
        if (feed == Feed.LIBRARY && !_signedIn.value) return
        YtMusicRepository.clearBrowseCache()
        val identity = listenerKey()
        _refreshing.value = _refreshing.value + feed
        viewModelScope.launch {
            when (feed) {
                Feed.HOME -> refreshHome(identity)
                Feed.EXPLORE -> fetchExplore()
                Feed.LIBRARY -> fetchLibrary(identity, forceLiked = true)
            }
            _refreshing.value = _refreshing.value - feed
        }
    }

    fun loadExplore() {
        _explore.value = UiState.Loading
        viewModelScope.launch { fetchExplore() }
    }

    private suspend fun fetchExplore() {
        val state = YtMusicRepository.moodAndGenres().fold(
            onSuccess = { sections ->
                if (sections.isEmpty()) {
                    UiState.Error(text(R.string.nothing_to_explore))
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
        _explore.value = state
        (state as? UiState.Success)?.data?.let(::loadMoodGenreArtwork)
    }

    /**
     * Category buttons do not carry thumbnails themselves. Resolve a small
     * number at a time from the shelves they open, so the grid gains real art
     * without saturating the browse client or delaying the category list.
     */
    private fun loadMoodGenreArtwork(sections: List<MoodGenreSection>) {
        viewModelScope.launch {
            val limiter = Semaphore(4)
            coroutineScope {
                sections.flatMap(MoodGenreSection::items).forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            YtMusicRepository.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (_explore.value as? UiState.Success)?.data ?: return@launch
                        _explore.value = UiState.Success(current.map { section ->
                            section.copy(items = section.items.map { currentItem ->
                                if (currentItem.browseId == item.browseId && currentItem.params == item.params) {
                                    currentItem.copy(thumbnailUrl = artwork)
                                } else {
                                    currentItem
                                }
                            })
                        })
                    }
                }
            }
        }
    }

    fun openMoodGenre(item: MoodGenre) {
        _selectedMoodGenre.value = item
        _moodGenreShelves.value = UiState.Loading
        viewModelScope.launch {
            _moodGenreShelves.value = YtMusicRepository.moodGenreShelves(item.browseId, item.params).fold(
                onSuccess = { shelves ->
                    if (shelves.isEmpty()) UiState.Error(text(R.string.nothing_to_explore))
                    else UiState.Success(shelves)
                },
                onFailure = { UiState.Error(it.friendly()) },
            )
        }
    }

    fun closeMoodGenre(): Boolean {
        if (_selectedMoodGenre.value == null) return false
        _selectedMoodGenre.value = null
        return true
    }

    /** Tapping a tab should leave any pushed page behind. */
    fun clearDetail() {
        detailJobs.values.forEach { it.cancel() }
        detailJobs.clear()
        if (_detailStack.value.isNotEmpty()) _detailStack.value = emptyList()
    }

    fun loadHome() {
        val identity = listenerKey()
        val generation = homeLoadGeneration.incrementAndGet()
        homeRecommendationsJob?.cancel()
        homeRecommendationSeed = null
        _homeQuickRecommendations.value = emptyList()
        _home.value = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        _homeLoadingMore.value = false
        _homeRecentlyPlayedLoading.value = _signedIn.value
        // The core feed plus one browse per supplement. Recently played is left
        // out: it has its own skeleton at the head of the page rather than the
        // one at the tail.
        _homePendingShelves.value = 1 + YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.size
        viewModelScope.launch {
            coroutineScope {
                launch {
                    try {
                        YtMusicRepository.home()
                            .onSuccess { feed ->
                                if (!isCurrentHomeLoad(identity, generation)) return@onSuccess
                                homeContinuation = feed.continuation
                                publishHomeShelves(feed.shelves)
                            }
                            .onFailure { failure ->
                                if (isCurrentHomeLoad(identity, generation) && _home.value !is UiState.Success) {
                                    _home.value = UiState.Error(failure.friendly())
                                }
                            }
                    } finally {
                        homeShelfRequestSettled(identity, generation)
                    }
                }
                if (_signedIn.value) {
                    launch {
                        YtMusicRepository.homeRecentlyPlayed()
                            .onSuccess { shelf ->
                                if (isCurrentHomeLoad(identity, generation)) {
                                    shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
                                    _homeRecentlyPlayedLoading.value = false
                                    enrichHomeQuickPicks(identity, generation)
                                }
                            }
                            .onFailure {
                                if (isCurrentHomeLoad(identity, generation)) _homeRecentlyPlayedLoading.value = false
                            }
                    }
                }
                YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                    launch {
                        try {
                            YtMusicRepository.homeSupplement(browseId).onSuccess { shelves ->
                                if (isCurrentHomeLoad(identity, generation)) publishHomeShelves(shelves)
                            }
                        } finally {
                            homeShelfRequestSettled(identity, generation)
                        }
                    }
                }
            }
            if (isCurrentHomeLoad(identity, generation)) enrichHomeQuickPicks(identity, generation)
        }
    }

    private fun enrichHomeQuickPicks(identity: String?, generation: Long) {
        val shelves = (_home.value as? UiState.Success)?.data.orEmpty()
                val recent = com.music.bitchord.data.model.homeRecentTracks(shelves)
        val seed = (recent.firstOrNull() ?: com.music.bitchord.data.model.homeDiscoveryTracks(shelves).firstOrNull())?.videoId ?: return
        if (homeRecommendationSeed == seed) return
        homeRecommendationSeed = seed
        homeRecommendationsJob?.cancel()
        homeRecommendationsJob = viewModelScope.launch {
            val result = withTimeoutOrNull(4_000L) { YtMusicRepository.homeRecommendations(seed).getOrNull() }.orEmpty()
            if (!isCurrentHomeLoad(identity, generation)) return@launch
            val heard = recent.mapTo(HashSet()) { it.videoId }
            _homeQuickRecommendations.value = result.filter { it.videoId !in heard }.map {
                ShelfItem(it.title, it.artist, it.thumbnailUrl, it.videoId, null)
            }
        }
    }

    /**
     * One of the parallel first-page requests has finished. Ignored once a newer
     * load has taken over, which has already reset the count for its own fan-out.
     */
    private fun homeShelfRequestSettled(identity: String?, generation: Long) {
        if (!isCurrentHomeLoad(identity, generation)) return
        _homePendingShelves.value = (_homePendingShelves.value - 1).coerceAtLeast(0)
        if (!_homeRecentlyPlayedLoading.value) enrichHomeQuickPicks(identity, generation)
    }

    private fun isCurrentHomeLoad(identity: String?, generation: Long) =
        identity == listenerKey() && generation == homeLoadGeneration.get()

    private fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (_home.value as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            // YouTube's core home can also contain a stale "Recently played"
            // shelf. The dedicated history endpoint wins and replaces it.
            val replacing = shelves.map { it.title.lowercase(Locale.ROOT) }.toSet()
            homeSeenTitles.addAll(replacing)
            _home.value = UiState.Success(shelves + existing.filterNot {
                it.title.lowercase(Locale.ROOT) in replacing
            })
            return
        }
        val added = shelves.withoutRepeatsOf(existing)
            .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
        if (added.isNotEmpty()) _home.value = UiState.Success(existing + added)
    }

    /** Refreshes the core feed without blanking the current Play page first. */
    private suspend fun refreshHome(identity: String?) {
        if (identity != listenerKey()) return
        val generation = homeLoadGeneration.incrementAndGet()
        homeRecommendationsJob?.cancel()
        homeRecommendationSeed = null
        val previous = (_home.value as? UiState.Success)?.data.orEmpty()
        _homePendingShelves.value = 0
        _homeLoadingMore.value = false
        // If refresh interrupts first load, keep its reserved Recents slot
        // until this generation settles too.
        val refreshed = refreshedHomeFeed(
            previous = previous,
            loadFeed = { YtMusicRepository.home() },
            loadRecents = if (_signedIn.value) ({ YtMusicRepository.homeRecentlyPlayed() }) else null,
        )
        if (!isCurrentHomeLoad(identity, generation)) return
        refreshed.onSuccess { feed ->
            homeContinuation = feed.continuation
            homeSeenTitles.clear()
            homeSeenTitles.addAll(feed.shelves.map { it.title.lowercase(Locale.ROOT) })
            if (feed.shelves.isNotEmpty()) _home.value = UiState.Success(feed.shelves)
        }
        _homeRecentlyPlayedLoading.value = false
        if (refreshed.isSuccess) enrichHomeQuickPicks(identity, generation)
    }

    /**
     * Called as the Home list nears its end. A no-op while a page is already
     * in flight, once the feed is exhausted, or before the first page has
     * loaded — [homeContinuation] covers all three by construction.
     */
    fun loadMoreHome() {
        if (Feed.HOME in _refreshing.value) return
        val token = homeContinuation ?: return
        if (_homeLoadingMore.value) return
        val identity = listenerKey()
        val generation = homeLoadGeneration.get()
        _homeLoadingMore.value = true
        viewModelScope.launch {
            YtMusicRepository.moreHome(token).onSuccess { feed ->
                if (isCurrentHomeLoad(identity, generation)) {
                    val existing = (_home.value as? UiState.Success)?.data ?: emptyList()
                    val added = feed.shelves.withoutRepeatsOf(existing)
                        .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                    // A page with nothing new signals the feed has looped back on
                    // itself rather than run dry with a token still attached —
                    // treat it the same as exhausted so scrolling can't spin here.
                    homeContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                    if (added.isNotEmpty()) _home.value = UiState.Success(existing + added)
                }
            }
            if (isCurrentHomeLoad(identity, generation)) _homeLoadingMore.value = false
        }
    }

    fun loadLibrary() {
        if (!_signedIn.value) return
        val identity = listenerKey()
        if (_library.value !is UiState.Success) _library.value = UiState.Loading
        viewModelScope.launch { fetchLibrary(identity) }
    }

    private suspend fun fetchLibrary(identity: String?, forceLiked: Boolean = false) {
        if (identity != listenerKey()) return
        val request = latestLibraryRequest.begin()
        restoreLikedSnapshot(identity)
        if (identity != listenerKey() || !latestLibraryRequest.isCurrent(request)) return
        val response = YtMusicRepository.library()
        // Check before merging: an obsolete response must not acknowledge
        // pending creates, start a liked-song sync, or overwrite newer state.
        if (identity != listenerKey() || !latestLibraryRequest.isCurrent(request)) return
        val next = response.fold(
            onSuccess = { page ->
                val merged = pendingPlaylistCreations.mergeLibrary(identity, page)
                com.music.bitchord.playback.DaylightMixRepository.prime(
                    page.likedSongs + page.librarySongs,
                    page.shelves.flatMap { it.items }.mapNotNull { it.browseId?.takeIf { id -> id.startsWith("VL") && id != "VLLM" } },
                )
                if (page.likedLoaded) syncLikedMusic(identity, page.likedSongs, page.likedContinuation, forceLiked)
                val retained = merged.copy(likedSongs = likedSnapshot?.songs ?: merged.likedSongs, likedContinuation = null)
                if (retained.isEmpty) UiState.Error(text(R.string.library_empty)) else UiState.Success(retained)
            },
            onFailure = { (_library.value as? UiState.Success) ?: UiState.Error(it.friendly()) },
        )
        if (identity == listenerKey() && latestLibraryRequest.isCurrent(request)) _library.value = next
    }

    private suspend fun restoreLikedSnapshot(identity: String?) {
        if (likedSnapshot != null || identity == null) return
        val saved = CollectionMetadataStore.read(likedMetadataScope(identity))
            .firstOrNull { it.browseId == YtMusicRepository.LIKED_MUSIC } ?: return
        if (identity != listenerKey() || likedSnapshot != null) return
        publishLikedSnapshot(saved)
        if (_library.value is UiState.Loading) _library.value = UiState.Success(LibraryPage(saved.songs, emptyList(), emptyList()))
    }

    private fun publishLikedSnapshot(snapshot: CollectionSnapshot) {
        likedSnapshot = snapshot
        LikeState.seedLiked(snapshot.songs.mapTo(HashSet()) { it.videoId })
        val library = (_library.value as? UiState.Success)?.data
        if (library != null) _library.value = UiState.Success(library.copy(likedSongs = snapshot.songs))
        _detailStack.value = _detailStack.value.map { page ->
            if (page.browseId == YtMusicRepository.LIKED_MUSIC) page.copy(
                songs = UiState.Success(snapshot.songs), thumbnailUrl = snapshot.thumbnailUrl ?: page.thumbnailUrl,
                creator = snapshot.creator ?: page.creator,
            ) else page
        }
    }

    /** Reuse the existing sequential liked sync, retaining metadata instead of discarding it. */
    private fun syncLikedMusic(identity: String?, first: List<Song>, token: String?, force: Boolean = false) {
        val saved = likedSnapshot
        if (!force && saved?.isFresh() == true && first.map { it.videoId } == saved.songs.take(first.size).map { it.videoId } &&
            (token != null || first.size == saved.songs.size)) return
        likedSyncJob?.cancel()
        val revision = likedRevision
        val requestScope = Innertube.responseCacheScope
        likedSyncJob = viewModelScope.launch {
            fun current() = isActive && identity == listenerKey() && revision == likedRevision && requestScope == Innertube.responseCacheScope
            val header = YtMusicRepository.cachedBrowseSongs(YtMusicRepository.LIKED_MUSIC)
            val base = CollectionSnapshot(YtMusicRepository.LIKED_MUSIC, text(R.string.auto_liked),
                thumbnailUrl = header?.header?.thumbnailUrl ?: saved?.thumbnailUrl,
                creator = header?.creator ?: saved?.creator, songs = first,
                complete = token == null, updatedAt = System.currentTimeMillis())
            if (saved == null && current()) {
                publishLikedSnapshot(base)
                CollectionMetadataStore.save(likedMetadataScope(identity), base)
            }
            val songs = first.toMutableList()
            val known = first.mapTo(HashSet()) { it.setVideoId ?: it.videoId }
            val seen = HashSet<String>()
            var next = token
            while (next != null && seen.add(next) && current()) {
                val page = YtMusicRepository.moreSongs(next).getOrNull() ?: return@launch
                if (!current()) return@launch
                LikeState.seedLiked(page.songs.mapTo(HashSet()) { it.videoId })
                com.music.bitchord.playback.DaylightMixRepository.prime(page.songs)
                songs += page.songs.filter { known.add(it.setVideoId ?: it.videoId) }
                next = page.continuation
            }
            if (next != null || !current()) return@launch // Failed/looping partial refresh never replaces the complete saved list.
            val snapshot = base.copy(songs = songs.filter { LikeState.overrides.value[it.videoId]?.let { status -> status != LikeStatus.LIKE } != true }, complete = true)
            publishLikedSnapshot(snapshot)
            CollectionMetadataStore.save(likedMetadataScope(identity), snapshot)
        }
    }

    /**
     * The account's listening history.
     *
     * Loaded on each visit rather than cached: it is a page whose whole subject
     * is what happened most recently, and one that opened showing the state it
     * was in last time would be answering a different question. Guests get the
     * signed-out message straight away, since there is no account to have a
     * history on.
     */
    fun loadHistory() {
        if (!_signedIn.value) {
            _history.value = UiState.Error(text(R.string.history_sign_in_required))
            return
        }
        val identity = listenerKey()
        _history.value = UiState.Loading
        viewModelScope.launch {
            val next = YtMusicRepository.history().fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) UiState.Error(text(R.string.history_empty))
                    else UiState.Success(songs)
                },
                onFailure = { UiState.Error(it.friendly()) },
            )
            if (identity == listenerKey()) _history.value = next
        }
    }

    /** Recent searches, kept on device as rich entity records. */
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = SearchHistory.recent

    /**
     * Records a full entity payload in the history — used when the user taps
     * a suggestion, result row, or typeahead card so the recents list shows
     * real artwork and metadata instead of raw text.
     */
    fun recordEntity(entity: SearchHistoryEntity) = SearchHistory.record(entity)

    /**
     * Records a track entity from a search hit.
     * Checks _typeaheadResults first (live media while typing), then _results
     * (committed search). Falls back to recording the raw query if no song data
     * is available.
     */
    fun recordSearch() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        // Prefer typeahead results — they are live and always available while typing.
        val fromTypeahead = _typeaheadResults.value.firstOrNull {
            it is SearchResult.TopTrack || it is SearchResult.Track
        }
        if (fromTypeahead is SearchResult.Track) {
            recordEntity(SearchHistoryEntity(
                id = fromTypeahead.song.videoId,
                title = fromTypeahead.song.title,
                subtitle = listOfNotNull(fromTypeahead.song.artist).joinToString(" · "),
                artworkUrl = fromTypeahead.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
            return
        }
        if (fromTypeahead is SearchResult.TopTrack) {
            recordEntity(SearchHistoryEntity(
                id = fromTypeahead.song.videoId,
                title = fromTypeahead.song.title,
                subtitle = listOfNotNull(fromTypeahead.song.artist).joinToString(" · "),
                artworkUrl = fromTypeahead.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
            return
        }
        // Fall back to committed search results.
        val topResult = (_results.value as? UiState.Success)?.data?.firstOrNull {
            it is SearchResult.TopTrack || it is SearchResult.Track
        }
        if (topResult is SearchResult.Track) {
            recordEntity(SearchHistoryEntity(
                id = topResult.song.videoId,
                title = topResult.song.title,
                subtitle = listOfNotNull(topResult.song.artist).joinToString(" · "),
                artworkUrl = topResult.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
        } else if (topResult is SearchResult.TopTrack) {
            recordEntity(SearchHistoryEntity(
                id = topResult.song.videoId,
                title = topResult.song.title,
                subtitle = listOfNotNull(topResult.song.artist).joinToString(" · "),
                artworkUrl = topResult.song.thumbnailUrl,
                entityType = EntityType.TRACK,
            ))
        } else {
            // Fallback: record as a raw-text entity for backwards compatibility
            recordEntity(SearchHistoryEntity(
                id = "q:$q",
                title = q,
                subtitle = "",
                artworkUrl = null,
                entityType = EntityType.TRACK,
            ))
        }
    }

    /**
     * Runs a term the user picked out of a list rather than typed — a recent
     * search, or one of [suggestions] — and floats it to the top of the
     * history. Picking is as deliberate as submitting, so it searches on the
     * spot.
     *
     * Recording happens downstream via [recordSearch] when the user actually
     * interacts with a result (taps a song, album, or artist card), which
     * ensures the saved entity carries real artwork and metadata instead of
     * a blank placeholder.
     */
    fun searchFor(term: String) {
        _query.value = term
        searchSubmitted = true
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        runSearch()
    }

    fun removeSearch(id: String) = SearchHistory.remove(id)

    fun clearSearchHistory() = SearchHistory.clear()

    fun onQueryChange(newValue: String) {
        if (_query.value == newValue) return
        _query.value = newValue
        searchSubmitted = false
        invalidateCommittedSearch()
        if (newValue.isBlank()) {
            suggestRequests.tryEmit(null)
            _suggestions.value = emptyList()
            _typeaheadResults.value = emptyList()
            _results.value = null
            _searchScrollReset.value += 1
            return
        }
        if (_searchSource.value == SearchSource.LIBRARY) return
        // The typed query is usable immediately. Backspacing also restores
        // cached completions and media without waiting out the debounce again.
        publishSuggestions(newValue, YtMusicRepository.cachedSearchSuggestions(newValue).orEmpty())
        _typeaheadResults.value = YtMusicRepository.cachedSearchTypeahead(newValue)?.rows.orEmpty().take(TYPEAHEAD_MAX_RESULTS)
        suggestRequests.tryEmit(SuggestRequest(newValue, SearchRequestKey.current(newValue)))
    }

    /**
     * Commits the current query text: clears suggestions/typeahead, runs the
     * full search, and records the term in history for future recall.
     */
    fun submitSearch() {
        val q = _query.value.trim()
        if (q.isEmpty() || _searchSource.value == SearchSource.LIBRARY) return
        searchSubmitted = true
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        runSearch()
    }

    /**
     * Points the Search tab at YouTube Music or at the Local Music folder.
     *
     * The query carries over: whatever is in the field is answered again by
     * the source just picked, so flipping is a way to compare the two rather
     * than a reset.
     */
    fun setSearchSource(source: SearchSource) {
        if (_searchSource.value == source) return
        _searchSource.value = source
        invalidateCommittedSearch()
        suggestRequests.tryEmit(null)
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        _searchScrollReset.value += 1
        when (source) {
            SearchSource.LIBRARY -> loadLibrarySongs()
            SearchSource.YOUTUBE -> submitSearch()
        }
    }

    /**
     * Reads the Local Music folder for the Library source. Re-read on every
     * switch to it, so files added since — or a permission granted since —
     * are in the next answer; the last list stays up meanwhile.
     */
    fun loadLibrarySongs() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _librarySongs.value = if (!LocalMediaRepository.hasStoragePermission(context)) {
                UiState.Error(text(R.string.storage_required_read))
            } else {
                runCatching { LocalMediaRepository.getLocalMusic(context) }.fold(
                    onSuccess = { songs ->
                        if (songs.isEmpty()) UiState.Error(text(R.string.no_local_audio_found))
                        else UiState.Success(songs)
                    },
                    onFailure = { UiState.Error(text(R.string.no_local_audio_found)) },
                )
            }
        }
    }

    fun onFilterChange(value: SearchFilter) {
        if (_filter.value == value) return
        _filter.value = value
        searchSubmitted = true
        runSearch()
    }

    /** The request id and scoped key make superseded answers harmless. */
    private data class SearchRequest(val query: String, val key: SearchRequestKey, val requestId: Long)

    private fun invalidateCommittedSearch() {
        newestRequestId.incrementAndGet()
        activeSearchKey = null
        searchSession = null
        searchPaginationJob?.cancel()
        searchPaginationJob = null
        _searchLoadingMore.value = false
        searchRequests.tryEmit(null)
    }

    private fun clearSearchState() {
        invalidateCommittedSearch()
        suggestRequests.tryEmit(null)
        searchSubmitted = false
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        _results.value = null
        searchCache.clear()
        YtMusicRepository.clearSearchCache()
    }

    private fun runSearch() {
        val query = _query.value.trim()
        if (query.isEmpty() || _searchSource.value != SearchSource.YOUTUBE) {
            invalidateCommittedSearch()
            _results.value = null
            return
        }
        searchSubmitted = true
        suggestRequests.tryEmit(null)
        _suggestions.value = emptyList()
        _typeaheadResults.value = emptyList()
        val key = SearchRequestKey.current(query, _filter.value)
        // IME Search and the submit icon can arrive together. Keep the active
        // request (or already displayed page) rather than restarting its socket.
        if (activeSearchKey == key ||
            (searchSession?.key == key && _results.value is UiState.Success && searchCache.peek(key) != null)
        ) return
        invalidateCommittedSearch()
        val id = newestRequestId.get()
        activeSearchKey = key
        _searchScrollReset.value += 1
        searchRequests.tryEmit(SearchRequest(query, key, id))
    }

    /** Submit immediately; typing alone is debounced by the preview pipelines. */
    private fun startSearchPipeline() = viewModelScope.launch {
        searchRequests.collectLatest { request ->
            request ?: return@collectLatest
            if (request.requestId != newestRequestId.get()) return@collectLatest
            // The selected account's server scope can finish settling after
            // submission. Retry against its current identity rather than
            // leaving this still-visible search waiting on a discarded page.
            if (!request.key.isCurrent()) {
                runSearch()
                return@collectLatest
            }
            val key = request.key
            try {
                val exact = searchCache.peek(key)
                val preview = if (key.filter == SearchFilter.ALL) {
                    YtMusicRepository.cachedSearchTypeahead(key.query)?.rows?.takeIf { it.isNotEmpty() }
                } else null
                _results.value = (exact?.rows ?: preview)?.let { UiState.Success(it) } ?: UiState.Loading
                if (exact != null) {
                    searchSession = SearchSession(key, request.requestId, exact.continuation)
                    return@collectLatest
                }
                // A preview is anonymous, so confirmed searches still obtain
                // the signed-in result and continuation for the selected filter.
                val result = YtMusicRepository.searchPage(request.query, key.filter)
                if (request.requestId != newestRequestId.get()) return@collectLatest
                if (!key.isCurrent()) {
                    runSearch()
                    return@collectLatest
                }
                _results.value = result.fold(
                    onSuccess = { page -> published(page, key, request.requestId) },
                    onFailure = { failure -> preview?.let { UiState.Success(it) } ?: UiState.Error(failure.friendly()) },
                )
            } finally {
                if (request.requestId == newestRequestId.get()) activeSearchKey = null
            }
        }
    }

    /**
     * Continues the visible search only when the list reaches its end. This is
     * deliberately separate from the first-page request: waiting for every
     * continuation was the reason a search sat on a spinner for seconds.
     */
    fun loadMoreSearchResults() {
        val session = searchSession ?: return
        val token = session.continuation ?: return
        if (_searchLoadingMore.value || !session.key.isCurrent()) return
        _searchLoadingMore.value = true
        searchPaginationJob = viewModelScope.launch {
            try {
                val next = YtMusicRepository.searchContinuation(token, session.key.filter)
                val stillCurrent = searchSession == session && session.requestId == newestRequestId.get() && session.key.isCurrent()
                if (stillCurrent) next.onSuccess { page ->
                    val current = (_results.value as? UiState.Success)?.data.orEmpty()
                    val merged = (current + page.rows).distinctBy(::searchResultKey)
                    searchCache.put(session.key, SearchCacheEntry(merged, page.continuation))
                    searchSession = session.copy(continuation = page.continuation)
                    _results.value = UiState.Success(merged)
                }
            } finally {
                if (session.requestId == newestRequestId.get()) _searchLoadingMore.value = false
            }
        }
    }

    private fun stillWantsSuggestions(request: SuggestRequest): Boolean =
        _query.value == request.input && !searchSubmitted &&
            _searchSource.value == SearchSource.YOUTUBE && request.key.isCurrent()

    private fun publishSuggestions(input: String, fetched: List<String>) {
        _suggestions.value = listOf(input) + fetched.filterNot { it.equals(input, ignoreCase = true) }
    }

    /** Text completions are cheap and return sooner than the full media preview. */
    @OptIn(FlowPreview::class)
    private fun startSuggestPipeline() = viewModelScope.launch {
        suggestRequests
            .debounce { if (it == null) 0L else SUGGEST_DEBOUNCE_MS }
            .collectLatest { request ->
                request ?: return@collectLatest
                if (!stillWantsSuggestions(request)) return@collectLatest
                val fetched = YtMusicRepository.searchSuggestions(request.input).getOrNull() ?: return@collectLatest
                if (stillWantsSuggestions(request)) publishSuggestions(request.input, fetched)
            }
    }

    /** One media request after a short pause, with instant reuse on backspacing. */
    @OptIn(FlowPreview::class)
    private fun startTypeaheadMediaPipeline() = viewModelScope.launch {
        suggestRequests
            .debounce { if (it == null) 0L else TYPEAHEAD_MEDIA_DEBOUNCE_MS }
            .collectLatest { request ->
                request ?: return@collectLatest
                if (request.input.trim().length < 2 || !stillWantsSuggestions(request)) return@collectLatest
                val page = YtMusicRepository.searchTypeahead(request.input).getOrNull()
                // Submission, source changes and account switches can happen
                // while a response is arriving. None should reopen the preview.
                if (stillWantsSuggestions(request)) {
                    _typeaheadResults.value = page?.rows.orEmpty().take(TYPEAHEAD_MAX_RESULTS)
                }
            }
    }

    /** Caches and publishes the first page without waiting for continuation. */
    private fun published(
        page: YtMusicRepository.SearchPage,
        key: SearchRequestKey,
        requestId: Long,
    ): UiState<List<SearchResult>> {
        val rows = page.rows
        if (rows.isEmpty()) return UiState.Error(text(R.string.no_results))
        searchCache.put(key, SearchCacheEntry(rows, page.continuation))
        searchSession = SearchSession(key, requestId, page.continuation)
        prefetchTopResult(rows)
        return UiState.Success(rows)
    }

    private fun searchResultKey(row: SearchResult): String = when (row) {
        is SearchResult.TopTrack -> "v:${row.song.videoId}"
        is SearchResult.Track -> "v:${row.song.videoId}"
        is SearchResult.Browse -> "b:${row.item.browseId}"
    }

    /**
     * The enabled non-YouTube sources, asked at the same time and returned
     * split at YouTube's own place in the order.
     *
     * The split is what makes the Sources screen's ordering visible where it
     * matters most. A library server ranked above YouTube puts its own copies
     * at the top of the results — which is the whole point of ranking it there —
     * and one ranked below appears under them instead.
     *
     * Only the Songs filter fans out: albums, artists and playlists are
     * browse-shaped, and [MusicSource] deliberately answers for tracks only.
     *
     * Asked of the playback list rather than every enabled source, so a result
     * offered here is one this connection's ceiling would actually let play —
     * a row that can only be tapped into a YouTube stream is a lie about where
     * the track is coming from.
     */
    private suspend fun sourceResults(
        query: String,
        filter: SearchFilter,
    ): Pair<List<SearchResult>, List<SearchResult>> = coroutineScope {
        if (filter != SearchFilter.SONGS) return@coroutineScope emptyList<SearchResult>() to emptyList()
        val active = SourceRegistry.activeForPlayback()
        val youtubeRank = active.indexOfFirst { it.kind == SourceKind.YOUTUBE }
            .let { if (it < 0) active.size else it }

        val answers = active
            .filter { it.kind != SourceKind.YOUTUBE }
            .map { source ->
                source to async {
                    // Per-source, so one slow or unreachable server delays the
                    // results by at most this much rather than for as long as
                    // its socket takes to give up.
                    runCatching {
                        withTimeout(SOURCE_SEARCH_TIMEOUT_MS) { source.search(query, SOURCE_SEARCH_LIMIT) }
                    }.getOrDefault(emptyList())
                }
            }

        val above = mutableListOf<SearchResult>()
        val below = mutableListOf<SearchResult>()
        answers.forEach { (source, job) ->
            val rows = job.await().map { SearchResult.Track(it) }
            val rank = active.indexOfFirst { it.configId == source.configId }
            if (rank in 0 until youtubeRank) above += rows else below += rows
        }
        above to below
    }

    /**
     * Warms the stream URL for the top song result the instant results land,
     * not when it's tapped. [AudioCache] gives a head start to whatever's
     * already queued; a fresh search has nothing queued yet, and the top
     * result is overwhelmingly what gets tapped — see [play][MainActivity.play].
     * The exact search result is warmed because video tracks now play their
     * own audio by default; catalogue matching is only requested manually.
     */
    private fun prefetchTopResult(rows: List<SearchResult>) {
        val song = rows.firstNotNullOfOrNull {
            when (it) {
                is SearchResult.TopTrack -> it.song
                is SearchResult.Track -> it.song
                is SearchResult.Browse -> null
            }
        } ?: return
        viewModelScope.launch {
            runCatching {
                val audio = song
                // A source-backed row resolves through its own source already
                // and never takes the YouTube path — warming either half of
                // this for one would be work nothing asks for.
                if (SourceRegistry.parseTrackKey(audio.videoId) != null) return@runCatching
                // JioSaavn first, on the same reasoning as the queue's
                // read-ahead: it is the copy that will actually be played if it
                // has the track, so warming YouTube's URL instead warms the one
                // that loses. Pinned through [StreamChoice] so playback opens
                // this very stream rather than racing for it again — see
                // [SourceResolver.prefetchSubstitute], which requires it.
                val warmed = SourceResolver.prefetchSubstitute(
                    TrackMatcher.Target(
                        title = audio.title,
                        artist = audio.artist,
                        durationSec = TrackMatcher.secondsOf(audio.durationText),
                        album = audio.albumName,
                        isExplicit = audio.isExplicit,
                        isVideo = audio.isVideo,
                    ),
                )
                if (warmed != null) {
                    StreamChoice.remember(audio.videoId, warmed, substituted = true)
                    return@runCatching
                }
                // Disabled, or hasn't got it: the tap path falls back to
                // YouTube, so that is what is worth having ready.
                StreamResolver.resolve(audio.videoId)
            }
        }
    }

    companion object {
        /** How many Spotify tracks are looked up on YouTube Music at once. */
        private const val SPOTIFY_MATCH_PARALLELISM = 6

        /**
         * How long a keystroke waits before the typeahead is asked about it.
         *
         * Not the search's timer — searches aren't on a timer any more. This
         * one only stops a fast typist spending a round trip per letter, so it
         * wants to be as short as it can be while still collapsing a burst:
         * long enough that "cold" isn't four lookups, short enough that the
         * list is up by the time the thumb has left the key.
         */
        const val SUGGEST_DEBOUNCE_MS = 120L

        /**
         * Debounce for the parallel media-search pipeline. Slightly longer than
         * text suggestions so it doesn't fire on every single keystroke — a
         * full search is heavier than a suggestion request, and the UI only
         * needs a few results to fill the dropdown.
         */
        const val TYPEAHEAD_MEDIA_DEBOUNCE_MS = 300L

        /**
         * Maximum number of live media results shown in the typeahead dropdown.
         * Enough to give variety without making the list unscrollable.
         */
        const val TYPEAHEAD_MAX_RESULTS = 15

        /**
         * How long any one source gets to answer a search.
         *
         * Short on purpose: these run alongside the YouTube search, and their
         * only job is to be *there* when it lands. A home server reached over
         * a VPN that takes eight seconds has effectively not answered, and
         * holding the whole result list for it would make search feel worse
         * for the sake of results the user can still get by searching again.
         */
        const val SOURCE_SEARCH_TIMEOUT_MS = 4000L

        /** Enough to be worth scrolling, short enough not to bury YouTube's own rows. */
        const val SOURCE_SEARCH_LIMIT = 12

        /**
         * What a page with an empty listing says.
         *
         * Named because it is a state one can be got *out* of, not just a
         * message: an own playlist with nothing in it lands here, and adding the
         * first track to it has to be able to tell "this page is empty" apart
         * from "this page failed to load" — see [appendToOpenPlaylist].
         */

        /**
         * What a downloaded playlist's page says once the files under it are
         * gone.
         *
         * A record here outlives the folder it names — the user is expected to
         * manage Downloads with a file manager — so this is a state its page has
         * to be able to reach, not an error. Named because three places say it:
         * the page, its refresh, and the long-press menu that queues it without
         * opening it.
         */

        fun browseTypeOf(browseId: String, fallback: BrowseType = BrowseType.OTHER): BrowseType = when {
            browseId.startsWith(SPOTIFY_PAGE_PREFIX) -> BrowseType.PLAYLIST
            browseId.startsWith(Downloads.PLAYLIST_PREFIX) -> BrowseType.PLAYLIST
            browseId.startsWith("local:playlist:") -> BrowseType.PLAYLIST
            browseId.startsWith("UC") -> BrowseType.ARTIST
            browseId.startsWith("MPREb") || browseId.startsWith("VLOLAK") || browseId.startsWith("OLAK") -> BrowseType.ALBUM
            browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
            else -> fallback
        }
    }


    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        // A fast double tap used to push two identical loading pages and launch
        // two identical browse requests. Besides wasting the connection, both
        // completions then raced to update every matching browse id in the
        // stack. The page is pushed synchronously, so this closes that window
        // without suppressing a deliberate revisit after the first page loads.
        if (_detailStack.value.lastOrNull()?.let {
                it.browseId == browseId && it.songs is UiState.Loading
            } == true
        ) return
        if (browseId.startsWith(SPOTIFY_PAGE_PREFIX)) {
            openSpotifyPage(browseId, title, subtitle, thumbnailUrl)
            return
        }
        if (browseId == YtMusicRepository.LIKED_MUSIC) {
            openLikedPage(title, subtitle, thumbnailUrl)
            return
        }
        val resolved = browseTypeOf(browseId, type)
        val instanceId = detailInstanceIds.incrementAndGet()
        _detailStack.value += DetailPage(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            songs = UiState.Loading,
            type = resolved,
            instanceId = instanceId,
        )
        val identity = listenerKey()
        val requestScope = Innertube.responseCacheScope
        detailJobs[instanceId] = viewModelScope.launch {
            fun current() = isActive && identity == listenerKey() && requestScope == Innertube.responseCacheScope &&
                _detailStack.value.any { it.instanceId == instanceId }
            var sections = emptyList<HomeShelf>()
            // Callers that open an artist from a track — the player, the
            // long-press menu — only have that track's cover art and its full
            // credit ("A, B & C") to hand, so the page swaps in the artist's
            // own picture and name once they arrive.
            var artwork: String? = null
            var name: String? = null
            /**
             * The credit line, when the page had to supply its own.
             *
             * Only a link tapped outside the app arrives with neither — see
             * [com.music.bitchord.playback.MusicLink]. Every other caller was
             * looking at a card that already said this.
             */
            var credit: String? = null
            /** Set when the track list carries on past its first response. */
            var more: String? = null
            /** Tracks YouTube offers to round the playlist out — see [DetailPage.suggestedSongs]. */
            var suggested: List<Song> = emptyList()
            /** Whether this release is already saved — see [DetailPage.library]. */
            var library: LibraryState? = null
            /** YouTube's own "About" blurb — see [DetailPage.description]. */
            var description: String? = null
            /** Artist header stats — see [DetailPage.subscriberCountText]. */
            var subscriberCountText: String? = null
            var monthlyListenerCount: String? = null
            /** Whether this artist is subscribed to — see [DetailPage.subscription]. */
            var subscription: SubscriptionState? = null
            var creator: PlaylistCreator? = null
            val localPlaylist = com.music.bitchord.data.spotify.LocalPlaylistStore.getPlaylist(browseId)
            // A release downloaded whole and opened by its YouTube id — the
            // Playlists shelf, a search hit — used to wait on the network for
            // tracks already on the device, and showed an error with no
            // connection at all. Its downloaded copy goes up first; the
            // online listing replaces it if and when that arrives.
            val downloadedCopy = if (localPlaylist == null && !browseId.startsWith("local:")) {
                DownloadedListingFallback(
                    scope = this,
                    read = { downloadedCopyOf(browseId) },
                ) { onDevice ->
                    if (current() && onDevice.isNotEmpty()) {
                        _detailStack.value = _detailStack.value.map {
                            if (it.instanceId == instanceId && it.songs is UiState.Loading) {
                                it.copy(songs = UiState.Success(onDevice))
                            } else it
                        }
                    }
                }
            } else null
            val state = when {
                localPlaylist != null -> {
                    name = localPlaylist.title
                    creator = PlaylistCreator(text(R.string.creator_you), provider = CreatorProvider.LOCAL)
                    credit = getApplication<Application>().getString(R.string.local_playlist_subtitle, localPlaylist.songs.size)
                    artwork = localPlaylist.songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
                    if (localPlaylist.songs.isEmpty()) UiState.Error(text(R.string.spotify_import_empty_playlist))
                    else UiState.Success(localPlaylist.songs)
                }
                Downloads.recordIdOf(browseId) != null -> {
                    val songs = downloadedPlaylist(browseId)
                    if (songs.isEmpty()) UiState.Error(text(R.string.downloaded_playlist_empty))
                    else UiState.Success(songs)
                }
                browseId == "local:downloads" -> {
                    val context = getApplication<Application>()
                    val songs = Downloads.getDownloadedSongs(context)
                    if (songs.isEmpty()) UiState.Error("No downloaded tracks")
                    else UiState.Success(songs)
                }
                browseId == CACHE_FOLDER_BROWSE_ID -> cachedSongsState()
                browseId == "local:all" -> {
                    val context = getApplication<Application>()
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        UiState.Error(text(R.string.storage_required_read))
                    } else {
                        val songs = LocalMediaRepository.getLocalMusic(context)
                        if (songs.isEmpty()) UiState.Error(text(R.string.no_local_audio_found))
                        else UiState.Success(songs)
                    }
                }
                resolved == BrowseType.ARTIST -> {
                    YtMusicRepository.artistPage(browseId).fold(
                        onSuccess = { page ->
                            if (!current()) return@launch
                            sections = page.sections
                            artwork = page.thumbnailUrl
                            name = page.name
                            description = page.description
                            subscriberCountText = page.subscriberCountText
                            monthlyListenerCount = page.monthlyListenerCount
                            subscription = page.subscription
                            if (page.songs.isEmpty()) {
                                UiState.Error(text(R.string.no_tracks_here))
                            } else {
                                UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
                else -> {
                    YtMusicRepository.browseSongs(browseId).fold(
                        onSuccess = { page ->
                            if (!current()) return@launch
                            // Free here — the page that returned these rows is
                            // the one thing that states who made the playlist,
                            // so its own menu never has to go and ask. Recorded
                            // even when the listing came back empty.
                            page.owned?.let { setPlaylistOwned(browseId, it) }
                            creator = page.creator.takeIf { resolved == BrowseType.PLAYLIST }
                            sections = page.sections
                            // Only for the caller that had nothing: a card's own
                            // title is what the user just tapped, and must not
                            // be swapped for the header's wording underneath them.
                            page.header?.let { header ->
                                if (title.isBlank()) name = header.title
                                // Album cards often only carry the artist, while
                                // the page header also carries the release year.
                                // Prefer that richer line so the year appears
                                // directly below the artist on the album page.
                                if (resolved == BrowseType.ALBUM && header.subtitle.isNotBlank()) {
                                    credit = header.subtitle
                                } else if (subtitle.isBlank()) {
                                    credit = header.subtitle
                                }
                                if (thumbnailUrl == null) artwork = header.thumbnailUrl
                            }
                            description = page.description
                            if (page.songs.isEmpty()) {
                                UiState.Error(text(R.string.no_tracks_here))
                            } else {
                                more = page.continuation
                                suggested = page.suggested.withArtwork(thumbnailUrl ?: artwork)
                                library = page.library
                                UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
            }
            if (!current()) return@launch
            // File verification and the browse request start together. A fast
            // online listing no longer waits for hundreds of content-URI
            // checks; an offline/failing request still keeps its saved copy.
            val hasOnlineSongs = state is UiState.Success && state.data.isNotEmpty()
            val onDevice = downloadedCopy?.finish(state).orEmpty()
            if (!current()) return@launch
            // Update this stack entry — another visit may share its browse id.
            // A page showing its downloaded copy takes only a real listing: a
            // failed or empty fetch leaves the downloaded tracks up.
            _detailStack.value = _detailStack.value.map {
                if (it.instanceId == instanceId &&
                    (it.songs is UiState.Loading || (onDevice.isNotEmpty() && hasOnlineSongs))
                ) {
                    it.copy(
                        songs = state,
                        sections = sections,
                        thumbnailUrl = artwork ?: it.thumbnailUrl,
                        title = name ?: it.title,
                        subtitle = credit ?: it.subtitle,
                        suggestedSongs = suggested,
                        library = library,
                        description = description,
                        subscriberCountText = subscriberCountText,
                        monthlyListenerCount = monthlyListenerCount,
                        subscription = subscription,
                        creator = creator,
                    )
                } else {
                    it
                }
            }
            // Only once the first page is on screen: [fillIn] appends to it,
            // and has nothing to append to before this.
            more?.let { fillIn(instanceId, it, thumbnailUrl ?: artwork) }
        }
    }

    /** A persistent full list is painted before any refresh, and stays usable on refresh failure. */
    private fun openLikedPage(title: String, subtitle: String, thumbnailUrl: String?) {
        val identity = listenerKey()
        val saved = likedSnapshot ?: CollectionMetadataStore.peek(likedMetadataScope(identity), YtMusicRepository.LIKED_MUSIC)
        val warm = YtMusicRepository.cachedBrowseSongs(YtMusicRepository.LIKED_MUSIC)
        val rows = saved?.songs ?: warm?.songs
        val instanceId = detailInstanceIds.incrementAndGet()
        _detailStack.value += DetailPage(
            browseId = YtMusicRepository.LIKED_MUSIC, title = title, subtitle = subtitle,
            thumbnailUrl = thumbnailUrl ?: saved?.thumbnailUrl ?: warm?.header?.thumbnailUrl,
            songs = rows?.let { UiState.Success(it) } ?: UiState.Loading,
            type = BrowseType.PLAYLIST, instanceId = instanceId, creator = saved?.creator ?: warm?.creator,
        )
        detailJobs[instanceId] = viewModelScope.launch {
            restoreLikedSnapshot(identity)
            if (identity != listenerKey()) return@launch
            if (likedSnapshot?.isFresh() == true) return@launch
            if (likedSyncJob?.isActive == true) {
                likedSyncJob?.join()
                return@launch
            }
            YtMusicRepository.browseSongs(YtMusicRepository.LIKED_MUSIC).fold(
                onSuccess = { page ->
                    if (identity == listenerKey()) syncLikedMusic(identity, page.songs, page.continuation)
                },
                onFailure = { error ->
                    if (identity != listenerKey()) return@fold
                    _detailStack.value = _detailStack.value.map {
                        if (it.instanceId == instanceId && it.songs is UiState.Loading) it.copy(songs = UiState.Error(error.friendly())) else it
                    }
                },
            )
        }
    }

    /**
     * A Spotify playlist opened as an ordinary playlist page.
     *
     * Spotify's rows go up as soon as they are read, each marked as still
     * waiting on its YouTube Music version (see [isMatchPending]); the matches
     * then arrive in order and swap the rows in place, so the page is usable
     * before the last song has been found.
     */
    private fun openSpotifyPage(browseId: String, title: String, subtitle: String, thumbnailUrl: String?) {
        val cacheScope = spotifyMetadataScope(AppSettings.spotifySpdcToken.value)
        val cached = CollectionMetadataStore.peek(cacheScope, browseId)
        val instanceId = detailInstanceIds.incrementAndGet()
        _detailStack.value += DetailPage(
            browseId = browseId, title = cached?.title ?: title,
            subtitle = cached?.subtitle ?: subtitle, thumbnailUrl = cached?.thumbnailUrl ?: thumbnailUrl,
            songs = cached?.songs?.let { UiState.Success(it) } ?: UiState.Loading,
            type = BrowseType.PLAYLIST, instanceId = instanceId,
            creator = cached?.creator ?: subtitle.trim().takeIf {
                it.isNotBlank() && it != text(R.string.spotify) && browseId != SPOTIFY_PAGE_PREFIX + SpotifyLibrary.LIKED_ID
            }?.let { PlaylistCreator(it, provider = CreatorProvider.SPOTIFY) },
        )
        val identity = listenerKey()
        val requestScope = Innertube.responseCacheScope
        detailJobs[instanceId] = viewModelScope.launch {
            val playlistId = browseId.removePrefix(SPOTIFY_PAGE_PREFIX)
            fun open() = isActive && identity == listenerKey() && requestScope == Innertube.responseCacheScope &&
                cacheScope == spotifyMetadataScope(AppSettings.spotifySpdcToken.value) &&
                _detailStack.value.any { it.instanceId == instanceId }
            val saved = cached ?: CollectionMetadataStore.read(cacheScope).firstOrNull { it.browseId == browseId }
            if (!open()) return@launch
            if (saved != null) _detailStack.update { stack -> stack.map {
                if (it.instanceId == instanceId) it.copy(songs = UiState.Success(saved.songs), title = saved.title,
                    subtitle = saved.subtitle, thumbnailUrl = saved.thumbnailUrl ?: it.thumbnailUrl, creator = saved.creator ?: it.creator) else it
            } }
            if (saved?.isFresh() == true) return@launch
            val started = System.currentTimeMillis()
            val latest = java.util.concurrent.atomic.AtomicReference(saved ?: CollectionSnapshot(
                browseId, title, subtitle, thumbnailUrl, songs = emptyList(), complete = false, updatedAt = started))
            var changed = false
            fun setSongs(songs: List<Song>, trackIds: List<String>, complete: Boolean = false) {
                if (!open()) return
                latest.updateAndGet { it.copy(songs = songs, trackIds = trackIds, complete = complete, updatedAt = started) }
                changed = true
                _detailStack.update { stack -> stack.map { if (it.instanceId == instanceId) it.copy(songs = UiState.Success(songs)) else it } }
            }
            fun failure(error: Throwable) {
                if (!open() || saved != null) return
                _detailStack.update { stack -> stack.map {
                    if (it.instanceId == instanceId) it.copy(songs = UiState.Error(error.message ?: text(R.string.failed))) else it
                } }
            }
            // Recording ids, rather than title or row index, preserve matches across reorders and duplicate entries.
            val matches = saved?.trackIds.orEmpty().zip(saved?.songs.orEmpty()).filterNot { it.second.isUnresolvedSpotify }.toMap()
            try {
                val metadataJob = launch {
                    val metadata = try { SpotifyLibrary.metadata(playlistId) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { return@launch }
                    if (!open()) return@launch
                    latest.updateAndGet { it.copy(title = metadata.title ?: it.title, thumbnailUrl = metadata.coverUrl ?: it.thumbnailUrl, creator = metadata.creator ?: it.creator) }
                    _detailStack.update { stack -> stack.map {
                        if (it.instanceId == instanceId) it.copy(title = metadata.title ?: it.title, thumbnailUrl = metadata.coverUrl ?: it.thumbnailUrl, creator = metadata.creator ?: it.creator) else it
                    } }
                }
                val tracks = try {
                    SpotifyLibrary.tracks(playlistId) { soFar ->
                        if (saved == null) setSongs(soFar.map { matches[it.id] ?: it.asPendingSong() }, soFar.map { it.id })
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { failure(error); return@launch }
                if (!open()) return@launch
                setSongs(tracks.map { matches[it.id] ?: it.asPendingSong() }, tracks.map { it.id })
                // Save one initial listing before playback can mark its origin as listened to.
                if (saved?.complete != true) CollectionMetadataStore.save(cacheScope, latest.get())
                val gate = Semaphore(SPOTIFY_MATCH_PARALLELISM)
                coroutineScope {
                    tracks.forEachIndexed { index, track ->
                        if (track.id !in matches) launch {
                            gate.withPermit {
                                if (!open()) return@withPermit
                                val match = try { SpotifyImporter.matchTrack(track) }
                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { null }
                                if (!open()) return@withPermit
                                val found = match?.copy(thumbnailUrl = match.thumbnailUrl ?: track.imageUrl)
                                    ?: track.asPendingSong().copy(videoId = SPOTIFY_MISSING_PREFIX + track.id)
                                latest.updateAndGet { snapshot -> snapshot.copy(songs = snapshot.songs.toMutableList().also { it[index] = found }) }
                                _detailStack.update { stack -> stack.map { page ->
                                    if (page.instanceId == instanceId) page.copy(songs = UiState.Success(latest.get().songs)) else page
                                } }
                            }
                        }
                    }
                }
                metadataJob.join()
                if (open()) latest.updateAndGet { it.copy(complete = true) }
            } finally {
                // Closing a partly matched page retains the matches already found. A partial/failed
                // refresh cannot discard the last complete snapshot.
                if (changed && (saved == null || latest.get().complete)) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        CollectionMetadataStore.save(cacheScope, latest.get())
                    }
                }
            }
        }
    }

    private fun SpotifyTrack.asPendingSong() = Song(
        videoId = SPOTIFY_PENDING_PREFIX + id,
        title = title,
        artist = artist,
        thumbnailUrl = imageUrl,
        durationText = durationMs.takeIf { it > 0 }?.let { ms ->
            "%d:%02d".format(ms / 60000, ms / 1000 % 60)
        },
        albumName = album,
    )

    fun reloadLocalDetail(browseId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val localPlaylist = com.music.bitchord.data.spotify.LocalPlaylistStore.getPlaylist(browseId)
            val state: UiState<List<Song>> = when {
                localPlaylist != null -> {
                    if (localPlaylist.songs.isEmpty()) UiState.Error(text(R.string.spotify_import_empty_playlist))
                    else UiState.Success(localPlaylist.songs)
                }
                Downloads.recordIdOf(browseId) != null -> {
                    val songs = downloadedPlaylist(browseId)
                    if (songs.isEmpty()) UiState.Error(text(R.string.downloaded_playlist_empty))
                    else UiState.Success(songs)
                }
                browseId == "local:downloads" -> {
                    val songs = Downloads.getDownloadedSongs(context)
                    if (songs.isEmpty()) UiState.Error("No downloaded tracks")
                    else UiState.Success(songs)
                }
                browseId == CACHE_FOLDER_BROWSE_ID -> cachedSongsState()
                browseId == "local:all" -> {
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        UiState.Error(text(R.string.storage_required_read))
                    } else {
                        val songs = LocalMediaRepository.getLocalMusic(context)
                        if (songs.isEmpty()) UiState.Error(text(R.string.no_local_audio_found))
                        else UiState.Success(songs)
                    }
                }
                else -> return@launch
            }
            _detailStack.value = _detailStack.value.map {
                if (it.browseId == browseId) {
                    it.copy(songs = state)
                } else it
            }
        }
    }

    /**
     * The tracks of the downloaded playlist [browseId] names that are still on
     * disk, in the order the playlist had.
     *
     * The download record already names those files, so opening this page must
     * not scan every unrelated download first. [Downloads.getCollectionSongs]
     * verifies only this collection and still collapses aliases that point to
     * the same saved file.
     *
     * Empty is the honest answer for a record whose files have all been deleted
     * from under it, and callers turn that into an empty-state message rather than
     * into a blank page.
     */
    private suspend fun downloadedPlaylist(browseId: String): List<Song> {
        val id = Downloads.recordIdOf(browseId) ?: return emptyList()
        return Downloads.getCollectionSongs(getApplication(), id)
    }

    /**
     * The downloaded copy of the release YouTube calls [browseId], if there
     * is one. A playlist is filed under whichever id it was downloaded from,
     * which may or may not carry the `VL` its page id does.
     */
    private suspend fun downloadedCopyOf(browseId: String): List<Song> {
        val bare = browseId.removePrefix("VL")
        val id = Downloads.collections.value.keys.firstOrNull { it.removePrefix("VL") == bare } ?: return emptyList()
        return Downloads.getCollectionSongs(getApplication(), id)
    }

    /**
     * The Cached songs folder: the YouTube and JioSaavn tracks the song cache
     * is holding, most recently played first. See [AudioCache.cachedSongs].
     */
    private suspend fun cachedSongsState(): UiState<List<Song>> {
        val songs = AudioCache.cachedSongs().map { it.song }
        return if (songs.isEmpty()) UiState.Error(text(R.string.cached_songs_empty)) else UiState.Success(songs)
    }

    /**
     * Follows a detail page's continuations in the background, appending each
     * page to what is already being read.
     *
     * A playlist of a few hundred tracks is several round trips, and taking
     * them before showing anything meant a spinner for all of them. Growing
     * the list underneath the reader is also what makes it safe to keep
     * following continuations however deep the playlist runs — nobody is
     * waiting on the last one — so a playlist past YouTube's ~1000-track,
     * ten-page shelf still loads to the end instead of stopping there.
     *
     * Its owning detail job is cancelled when the page leaves the stack. A
     * repeated token is the other exit, for a feed that loops back on itself.
     */
    private suspend fun fillIn(instanceId: Long, token: String, artworkFallback: String?) {
        // Give the first response a frame to draw before filling the rest.
        // A fixed 150 ms pause used to delay even cached continuations.
        delay(16)
        var next: String? = token
        val scope = Innertube.responseCacheScope
        val seenTokens = HashSet<String>()
        while (next != null) {
            // Check before spending a round trip, and track tokens rather
            // than treating one empty page as the end of a valid listing.
            if (scope != Innertube.responseCacheScope ||
                _detailStack.value.none { it.instanceId == instanceId } || !seenTokens.add(next)
            ) return
            val fetched = YtMusicRepository.moreSongs(next).getOrNull() ?: return
            val stack = _detailStack.value
            val index = stack.indexOfFirst { it.instanceId == instanceId }
            if (index < 0 || scope != Innertube.responseCacheScope) return
            val current = stack[index]
            val existing = (current.songs as? UiState.Success)?.data ?: return
            val knownEntries = existing.mapTo(HashSet()) { it.setVideoId ?: it.videoId }
            val added = fetched.songs
                .filter { knownEntries.add(it.setVideoId ?: it.videoId) }
                .withArtwork(artworkFallback)
            // Suggestions can arrive on a later page than the real
            // tracks, once the playlist's own continuation runs dry —
            // see parsePlaylistShelf — so they're tracked separately
            // rather than folded into [known].
            val known = (existing + added).mapTo(HashSet()) { it.videoId }
            val knownSuggested = current.suggestedSongs.mapTo(HashSet()) { it.videoId }
            val addedSuggested = fetched.suggested
                .filter { it.videoId !in known && knownSuggested.add(it.videoId) }
                .withArtwork(artworkFallback)
            if (added.isNotEmpty() || addedSuggested.isNotEmpty()) {
                _detailStack.value = stack.toMutableList().also {
                    it[index] = current.copy(
                        songs = UiState.Success(existing + added),
                        suggestedSongs = current.suggestedSongs + addedSuggested,
                    )
                }
            }
            next = fetched.continuation
        }
    }

    /**
     * An album's track listing doesn't repeat the cover on every row — the
     * page carries it once — so rows arrive with no artwork and stay blank
     * through to the queue and the notification. Fall back to the page's.
     */
    private fun List<Song>.withArtwork(fallback: String?): List<Song> {
        if (fallback == null) return this
        return map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = fallback) else it }
    }

    /**
     * Home and Explore cards don't say what they point at, and an artist
     * fetched as an album only yields the five songs on its landing page.
     * YouTube's browse ids are prefixed by kind, so use that.
     *
     * Public because the long-press menus ask the same question of a card
     * before offering to queue what is behind it — an artist is not a running
     * order, so it gets no queue actions.
     */
    fun browseTypeOf(browseId: String, fallback: BrowseType = BrowseType.OTHER): BrowseType =
        Companion.browseTypeOf(browseId, fallback)

    /**
     * Every track behind an album or playlist, handed to [onResult] once it is
     * all in.
     *
     * What a long-press on a card is acting on. A card has nothing but a browse
     * id — its page was never opened, so there is no track list anywhere to
     * read — and "add this album to the queue" means the whole album, so this
     * follows continuations to the end rather than taking the first page.
     *
     * Runs in [viewModelScope], not the caller's: the sheet the tap came from
     * closes immediately, and a three-hundred-track playlist must not be
     * abandoned halfway because of it.
     */
    fun collectSongs(
        browseId: String,
        artworkFallback: String? = null,
        onResult: (Result<List<Song>>) -> Unit,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = when {
                browseId.startsWith(SPOTIFY_PAGE_PREFIX) -> runCatching {
                    CollectionMetadataStore.read(spotifyMetadataScope(AppSettings.spotifySpdcToken.value))
                        .firstOrNull { it.browseId == browseId }?.songs.orEmpty()
                        .filterNot { it.isUnresolvedSpotify }.ifEmpty { error(text(R.string.spotify_empty_tracks)) }
                }
                browseId == YtMusicRepository.LIKED_MUSIC && likedSnapshot?.complete == true -> Result.success(likedSnapshot!!.songs)
                Downloads.recordIdOf(browseId) != null -> runCatching {
                    downloadedPlaylist(browseId).ifEmpty {
                        error(text(R.string.downloaded_playlist_empty))
                    }
                }
                browseId == "local:downloads" -> runCatching {
                    Downloads.getDownloadedSongs(context)
                        .ifEmpty { error("No downloaded tracks") }
                }
                browseId == CACHE_FOLDER_BROWSE_ID -> runCatching {
                    AudioCache.cachedSongs().map { it.song }
                        .ifEmpty { error(text(R.string.cached_songs_empty)) }
                }
                browseId == "local:all" -> runCatching {
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        error(text(R.string.storage_required_read))
                    }
                    LocalMediaRepository.getLocalMusic(context)
                        .ifEmpty { error(text(R.string.no_local_audio_found)) }
                }
                else -> YtMusicRepository.allSongs(browseId)
            }
            onResult(result.map { it.withArtwork(artworkFallback) })
        }
    }

    /** Opens the account credited by a playlist, keeping normal detail Back/scroll behavior. */
    fun openCreatorProfile(source: DetailPage) {
        if (source.type != BrowseType.PLAYLIST) return
        val creator = source.creator ?: return
        if (_detailStack.value.lastOrNull()?.creatorProfile?.let {
                it.creator == creator && it.currentPlaylist.browseId == source.browseId
            } == true
        ) return
        val instanceId = detailInstanceIds.incrementAndGet()
        val profile = CreatorProfilePage(
            creator = creator,
            currentPlaylist = BrowseItem(
                source.browseId, source.title, creator.name, source.thumbnailUrl, BrowseType.PLAYLIST,
            ),
            playlists = UiState.Loading,
        )
        _detailStack.value += DetailPage(
            browseId = "creator:${creator.provider}:${creator.browseId ?: source.browseId}",
            title = creator.name,
            subtitle = "",
            thumbnailUrl = creator.thumbnailUrl,
            songs = UiState.Success(emptyList()),
            instanceId = instanceId,
            creatorProfile = profile,
        )
        loadCreatorProfile(instanceId)
    }

    fun retryCreatorProfile(instanceId: Long) = loadCreatorProfile(instanceId)

    private fun loadCreatorProfile(instanceId: Long) {
        val profile = _detailStack.value.firstOrNull { it.instanceId == instanceId }?.creatorProfile ?: return
        detailJobs.remove(instanceId)?.cancel()
        _detailStack.value = _detailStack.value.map { page ->
            if (page.instanceId == instanceId) page.copy(creatorProfile = profile.copy(playlists = UiState.Loading))
            else page
        }
        val identity = listenerKey()
        val requestScope = Innertube.responseCacheScope
        detailJobs[instanceId] = viewModelScope.launch {
            fun current() = isActive && identity == listenerKey() && requestScope == Innertube.responseCacheScope &&
                _detailStack.value.any { it.instanceId == instanceId }
            var loadedCreator = profile.creator
            var description: String? = null
            val creatorBrowseId = profile.creator.browseId
            val collections: UiState<List<BrowseItem>> = when {
                profile.creator.provider == CreatorProvider.LOCAL -> UiState.Success(
                    LocalPlaylistStore.playlists.value.map { playlist ->
                        BrowseItem(
                            browseId = playlist.browseId,
                            title = playlist.title,
                            subtitle = text(R.string.creator_on_device),
                            thumbnailUrl = playlist.songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl,
                            type = BrowseType.PLAYLIST,
                        )
                    },
                )
                profile.creator.provider == CreatorProvider.YOUTUBE_MUSIC && creatorBrowseId != null ->
                    YtMusicRepository.creatorProfile(creatorBrowseId).fold(
                        onSuccess = { data ->
                            loadedCreator = profile.creator.copy(
                                name = data.name ?: profile.creator.name,
                                thumbnailUrl = data.thumbnailUrl ?: profile.creator.thumbnailUrl,
                            )
                            description = data.description
                            UiState.Success(data.playlists)
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                // Spotify can identify the playlist author without exposing a
                // browsable public-playlists API in this integration. Keep its
                // actual profile and originating collection available.
                else -> UiState.Success(emptyList())
            }
            if (!current()) return@launch
            _detailStack.value = _detailStack.value.map { page ->
                if (page.instanceId == instanceId) page.copy(
                    title = loadedCreator.name,
                    thumbnailUrl = loadedCreator.thumbnailUrl,
                    creatorProfile = profile.copy(
                        creator = loadedCreator,
                        playlists = collections,
                        description = description,
                    ),
                ) else page
            }
        }
    }

    /** Pops one page; returns false when there was nothing to pop. */
    fun closeDetail(): Boolean {
        val stack = _detailStack.value
        if (stack.isEmpty()) return false
        _detailStack.value = stack.dropLast(1)
        detailJobs.remove(stack.last().instanceId)?.cancel()
        return true
    }

    private fun cancelMissingDetailJobs() {
        val remaining = _detailStack.value.mapTo(HashSet()) { it.instanceId }
        detailJobs.keys.filterNot { it in remaining }.forEach { instanceId ->
            detailJobs.remove(instanceId)?.cancel()
        }
    }

    /**
     * Loads the channel list, unless it is already in hand.
     *
     * @param force refetch even if it is — after a switch, since the list
     *   itself reports which channel is active.
     */
    fun loadChannels(force: Boolean = false) {
        if (!_signedIn.value) return
        if (_channelsLoading.value) return
        if (!force && _channels.value.isNotEmpty()) return
        viewModelScope.launch {
            _channelsLoading.value = true
            YtMusicRepository.accountChannels()
                .onSuccess {
                    _channels.value = it
                    persistDetectedProfiles(it)
                }
            _channelsLoading.value = false
        }
    }

    /**
     * Acts as [channel] from here on.
     *
     * Everything already on screen belongs to the channel being left — its
     * library, its hearts, its playlists — so the switch clears them and
     * refetches rather than letting the new identity's pages arrive one at a
     * time on top of the old one's.
     */
    fun selectChannel(channel: AccountChannel) {
        val accountId = _activeAccountId.value ?: return
        val profile = YouTubeProfile(
            profileId = profileId(channel.pageId, channel.dataSyncId, channel.name),
            name = channel.name, handle = channel.subtitle, avatar = channel.thumbnailUrl,
            pageId = channel.pageId, dataSyncId = channel.dataSyncId,
            isBrandAccount = channel.pageId != null,
        )
        selectProfile(accountId, profile.profileId, profile)
    }

    /**
     * A session lifted out of the in-app browser — a fresh sign-in, or the same
     * login now pointed at a different channel.
     *
     * One path for both because they differ in exactly one thing: whether the
     * cookie is new. What follows — adopt the identity the captured page
     * reported, remember it, and refetch everything that belongs to a listener
     * — is the same work either way.
     */
    fun onWebSession(
        session: CapturedSession,
        mode: WebSessionMode,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (!session.loggedIn) {
            onComplete(false)
            return
        }
        val oldAccountId = _activeAccountId.value
        val oldProfileId = _activeProfileId.value
        viewModelScope.launch {
            // Validate the exact cookie/profile pair before writing any of it.
            // The old implementation persisted first and unconditionally set
            // signedIn=true; a half-finished channel chooser therefore became
            // a durable broken "Personal" account.
            Innertube.cookie = session.cookie
            Innertube.adoptSessionScope(
                pageId = session.pageId,
                dataSyncId = session.dataSyncId,
                authUser = session.authUser,
                visitorData = session.visitorData,
                clientVersion = session.clientVersion,
                loggedIn = true,
            )
            Innertube.selectChannel(session.pageId, session.dataSyncId, session.authUser)

            val account = withTimeoutOrNull(20_000L) {
                var result = YtMusicRepository.account()
                if (result.isFailure) {
                    delay(750L)
                    result = YtMusicRepository.account()
                }
                result.getOrNull()
            }
            if (account == null) {
                restoreActiveSession(oldAccountId, oldProfileId)
                onComplete(false)
                return@launch
            }

            // A channel switch is another identity under the same Google
            // login, never a new Google account. For a fresh sign-in, matching
            // a non-empty email upgrades the existing entry instead of adding
            // a duplicate after cookies rotate.
            val accountId = when (mode) {
                WebSessionMode.SWITCH_CHANNEL -> oldAccountId
                WebSessionMode.SIGN_IN -> authStore.sessions.firstOrNull {
                    account.email.isNotBlank() && it.email.equals(account.email, ignoreCase = true)
                }?.accountId ?: sessionId(session.cookie, null)
            }
            if (accountId == null) {
                restoreActiveSession(oldAccountId, oldProfileId)
                onComplete(false)
                return@launch
            }
            val previous = authStore.sessions.firstOrNull { it.accountId == accountId }
            val selectedProfileId = profileId(session.pageId, session.dataSyncId, account.name)
            val profile = YouTubeProfile(
                profileId = selectedProfileId,
                name = account.name,
                handle = account.email,
                avatar = account.thumbnailUrl,
                pageId = session.pageId,
                dataSyncId = session.dataSyncId,
                authUser = session.authUser,
                isBrandAccount = session.pageId != null,
            )
            val hasRealIdentity = profile.pageId != null || profile.dataSyncId != null
            val profiles = previous?.profiles.orEmpty().filterNot { known ->
                known.profileId == profile.profileId ||
                    (hasRealIdentity && known.profileId.startsWith("profile:"))
            } + profile
            val stored = GoogleAccountSession(
                accountId = accountId,
                cookie = session.cookie,
                name = account.name,
                email = account.email,
                profiles = profiles,
                activeProfileId = profile.profileId,
            )

            val wasSignedIn = _signedIn.value
            if (wasSignedIn) cacheCurrentListener()
            authStore.upsertSession(stored)
            authStore.cookie = session.cookie // legacy compatibility only
            _googleAccounts.value = authStore.sessions
            _activeAccountId.value = accountId
            _activeProfileId.value = profile.profileId
            _selectedChannelKey.value = profile.profileId
            _selectedChannelName.value = account.name
            _account.value = account
            _channels.value = emptyList()
            _signedIn.value = true

            // Every resolver verdict and personalised page belongs to the
            // identity that was active before validation succeeded.
            StreamResolver.onSessionChanged()
            if (wasSignedIn) clearListenerState() else {
                LikeState.selectScope(listenerKey())
                clearSearchState()
            }
            reloadForAccount()
            loadChannels(force = true)
            onComplete(true)
        }
    }

    /** Put request signing back exactly as it was after a rejected candidate. */
    private fun restoreActiveSession(accountId: String?, selectedProfileId: String?) {
        val account = authStore.sessions.firstOrNull { it.accountId == accountId }
        val profile = account?.profiles?.firstOrNull { it.profileId == selectedProfileId }
        Innertube.cookie = account?.cookie
        Innertube.selectChannel(profile?.pageId, profile?.dataSyncId, profile?.authUser)
    }

    /** Selects an identity without ever allowing a response to replace it. */
    fun selectProfile(accountId: String, selectedProfileId: String, supplied: YouTubeProfile? = null) {
        val source = _googleAccounts.value.firstOrNull { it.accountId == accountId } ?: return
        val profile = supplied ?: source.profiles.firstOrNull { it.profileId == selectedProfileId } ?: return
        if (accountId == _activeAccountId.value && profile.profileId == _activeProfileId.value) return
        cacheCurrentListener()
        val updated = source.copy(
            profiles = (source.profiles.filterNot { it.profileId == profile.profileId } + profile),
            activeProfileId = profile.profileId,
        )
        authStore.upsertSession(updated)
        authStore.select(accountId, profile.profileId)
        _googleAccounts.value = authStore.sessions
        _activeAccountId.value = accountId
        _activeProfileId.value = profile.profileId
        authStore.cookie = updated.cookie
        Innertube.cookie = updated.cookie
        Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
        _selectedChannelKey.value = profile.profileId
        _selectedChannelName.value = profile.name
        StreamResolver.onSessionChanged()
        clearListenerState(restoreCached = true)
        reloadForAccount()
    }

    /** Returns false at an edge, allowing the avatar to play its elastic cue. */
    fun stepProfile(forward: Boolean): Boolean {
        val target = adjacentProfile(_googleAccounts.value, _activeAccountId.value, _activeProfileId.value, forward)
            ?: return false
        selectProfile(target.first, target.second)
        return true
    }

    fun removeAccount(accountId: String) {
        forgetAccountMetadata(accountId)
        val fallback = authStore.removeAccount(accountId)
        _googleAccounts.value = authStore.sessions
        if (fallback == null) { signOut(); return }
        selectProfile(fallback.accountId, fallback.activeProfileId ?: fallback.profiles.firstOrNull()?.profileId ?: return)
    }

    private fun forgetAccountMetadata(accountId: String?) {
        val account = authStore.sessions.firstOrNull { it.accountId == accountId } ?: return
        val scopes = account.profiles.map { likedMetadataScope("${account.accountId}:${it.profileId}") }
        listenerCache.keys.removeAll { it.startsWith("${account.accountId}:") }
        if (accountId == _activeAccountId.value) {
            likedRevision++
            likedSyncJob?.cancel()
            likedSnapshot = null
        }
        viewModelScope.launch { scopes.forEach { CollectionMetadataStore.removeScope(it) } }
    }

    private fun persistDetectedProfiles(channels: List<AccountChannel>) {
        val accountId = _activeAccountId.value ?: return
        val current = authStore.sessions.firstOrNull { it.accountId == accountId } ?: return
        val detected = channels.map { channel -> YouTubeProfile(
            profileId(channel.pageId, channel.dataSyncId, channel.name), channel.name, channel.subtitle,
            channel.thumbnailUrl, channel.pageId, channel.dataSyncId,
            isBrandAccount = channel.pageId != null,
        ) }
        // A profile captured before its channel existed carries a provisional,
        // name-hash id (see profileId()) rather than the pageId/dataSyncId Google
        // reports here once the channel is live. Once Google actually reports a
        // channel, that placeholder is stale — it would have been listed here
        // too if it still lacked real ids — and it's dropped rather than kept
        // alongside the identity it was standing in for. An empty response
        // (channel not created yet, or a transient miss) must never drop it.
        val stalePlaceholder = if (detected.isEmpty()) emptySet() else current.profiles
            .filter { it.profileId.startsWith("profile:") }
            .map { it.profileId }
            .toSet()
        // Keep the captured active identity if Google's endpoint temporarily
        // omits it; an empty/partial response must never erase a selection.
        val profiles = (current.profiles.filter { known ->
            known.profileId !in stalePlaceholder && detected.none { it.profileId == known.profileId }
        } + detected)
        val selected = when {
            current.activeProfileId != null && current.activeProfileId !in stalePlaceholder -> current.activeProfileId
            current.activeProfileId in stalePlaceholder -> detected.singleOrNull()?.profileId
            else -> null
        } ?: profiles.firstOrNull()?.profileId
        authStore.upsertSession(current.copy(profiles = profiles, activeProfileId = selected), activate = false)
        _googleAccounts.value = authStore.sessions
    }

    /**
     * Drops everything on screen that belongs to the identity being left.
     *
     * The hearts, the playlists and the library are all answers to "who is
     * asking", so keeping them across a switch shows the new channel the old
     * one's music until each page happens to be refetched.
     */
    private fun cacheCurrentListener() {
        val key = listenerKey() ?: return
        listenerCache[key] = ListenerSnapshot(
            _account.value, _library.value, _history.value, _playlists.value, _playlistOwned.value,
        )
    }

    private fun listenerKey(): String? = _activeAccountId.value?.let { accountId ->
        _activeProfileId.value?.let { profileId -> "$accountId:$profileId" }
    }

    private fun clearListenerState(restoreCached: Boolean = false) {
        likedSnapshot = null
        likedRevision++
        clearSearchState()
        homeRecommendationsJob?.cancel()
        homeRecommendationSeed = null
        _homeQuickRecommendations.value = emptyList()
        com.music.bitchord.playback.DaylightMixRepository.clear()
        latestLibraryRequest.invalidate()
        pendingPlaylistCreations.clear()
        clearDetail()
        YtMusicRepository.clearBrowseCache()
        _account.value = null
        likedSyncJob?.cancel()
        LikeState.selectScope(listenerKey())
        _playlistsLoading.value = false
        _playlists.value = emptyList()
        _playlistOwned.value = emptyMap()
        ownershipInFlight.clear()
        _songMenu.value = null
        _library.value = UiState.Loading
        _history.value = UiState.Loading
        if (restoreCached) listenerCache[listenerKey()]?.let { cached ->
            _account.value = cached.account
            _library.value = cached.library
            _history.value = cached.history
            _playlists.value = cached.playlists
            _playlistOwned.value = cached.owned
        }
    }

    /**
     * Everything that is "the signed-in listener's", refetched.
     *
     * The scope comes first, and inside one coroutine rather than beside them:
     * which channel the session acts as decides what "the library" and "the
     * history" even refer to, so loading them first and scoping second shows
     * the listener the wrong account's music and then silently disagrees with
     * itself.
     */
    private fun reloadForAccount() {
        viewModelScope.launch {
            Innertube.ensureSessionScope()
            loadHome()
            loadLibrary()
            loadAccount()
            loadPlaylists()
        }
    }

    fun signOut() {
        // In a multi-account install, sign out removes only the current Google
        // session and restores another one. A legacy single-account install
        // retains the familiar full sign-out behaviour.
        val current = _activeAccountId.value
        if (current != null && authStore.sessions.size > 1) {
            removeAccount(current)
            return
        }
        forgetAccountMetadata(current)
        likedSnapshot = null
        likedRevision++
        authStore.signOut()
        clearSearchState()
        latestLibraryRequest.invalidate()
        pendingPlaylistCreations.clear()
        clearDetail()
        YtMusicRepository.clearBrowseCache()
        Innertube.cookie = null
        // The mirror image: verdicts reached with a session in hand say nothing
        // about what an anonymous walk will be told, and the clients stood down
        // for refusing the session deserve a fresh hearing without it.
        StreamResolver.onSessionChanged()
        _signedIn.value = false
        _account.value = null
        Innertube.selectChannel(null, null)
        _channels.value = emptyList()
        _selectedChannelKey.value = null
        _selectedChannelName.value = null
        _googleAccounts.value = emptyList()
        _activeAccountId.value = null
        _activeProfileId.value = null
        _library.value = UiState.Loading
        // Ratings and playlists belong to the account that just left; keeping
        // them would show the next signed-in user someone else's hearts.
        likedSyncJob?.cancel()
        LikeState.selectScope(listenerKey())
        _playlists.value = emptyList()
        _playlistOwned.value = emptyMap()
        ownershipInFlight.clear()
        _songMenu.value = null
        loadHome()
    }

    private fun Throwable.friendly(): String = when {
        message?.contains("resolve host", true) == true ||
            message?.contains("Unable to resolve", true) == true -> text(R.string.no_internet_connection)
        message?.contains("401") == true || message?.contains("403") == true ->
            text(R.string.youtube_request_rejected)
        else -> message ?: text(R.string.something_went_wrong)
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)
}
