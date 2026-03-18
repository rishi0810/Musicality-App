package com.proj.Musicality.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proj.Musicality.api.IpLocationRepository
import com.proj.Musicality.api.RequestExecutor
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.ExploreDiskCache
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.parser.ChartsParser
import com.proj.Musicality.data.parser.MoodCategoryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val data: ExploreData) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * All data needed to render the Explore screen.
     *
     * [chartsGlobalSections] — sections from the Global (ZZ) charts response.
     *   Typically: [0] "Video charts", [last] "Top artists".
     *
     * [chartsLocalSections] — sections from the local country's charts.
     *   Empty when [countryCode] is "ZZ" (would duplicate global).
     *
     * [feelGoodFirstSection] — first shelf from the Feel Good mood category.
     *
     * [partySections] — first two shelves from the Party mood category.
     */
    data class ExploreData(
        val countryCode: String,
        val countryName: String,
        val chartsGlobalSections: List<HomeSection>,
        val chartsLocalSections: List<HomeSection>,
        val feelGoodFirstSection: HomeSection?,
        val partySections: List<HomeSection>
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized && _state.value !is UiState.Error) return
        initialized = true
        viewModelScope.launch { loadData(forceRefresh = false) }
    }

    suspend fun refresh() {
        loadData(forceRefresh = true)
    }

    // ── Data loading ───────────────────────────────────────────────────────────

    private suspend fun loadData(forceRefresh: Boolean) {
        val context = getApplication<Application>()
        val countryCode = IpLocationRepository.getCountryCode(context)
        val isGlobal = countryCode == "ZZ"

        val keyGlobal    = "charts-ZZ"
        val keyLocal     = "charts-$countryCode"
        val keyFeelGood  = "mood-feel-good"
        val keyParty     = "mood-party"

        if (forceRefresh) {
            withContext(Dispatchers.IO) { ExploreDiskCache.invalidate(context) }
        }

        // Try disk cache first (skip on forced refresh)
        if (!forceRefresh) {
            withContext(Dispatchers.IO) {
                val globalJson    = ExploreDiskCache.get(context, keyGlobal)
                val localJson     = if (!isGlobal) ExploreDiskCache.get(context, keyLocal) else ""
                val feelGoodJson  = ExploreDiskCache.get(context, keyFeelGood)
                val partyJson     = ExploreDiskCache.get(context, keyParty)

                val allCached = globalJson != null &&
                    (isGlobal || localJson != null) &&
                    feelGoodJson != null &&
                    partyJson != null

                if (allCached) {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            parseAll(countryCode, globalJson!!, localJson, feelGoodJson!!, partyJson!!)
                        }
                    }.onSuccess { data ->
                        _state.value = UiState.Loaded(data)
                        return@withContext
                    }
                    // Cache corrupted → fall through to network fetch
                }
            }
            // Return early if cache load succeeded
            if (_state.value is UiState.Loaded) return
        }

        // Network fetch
        _state.value = UiState.Loading

        runCatching {
            withContext(Dispatchers.IO) {
                val visitorId = VisitorManager.ensureBrowseVisitorId()

                // Fire all 4 requests concurrently
                coroutineScope {
                    val globalDeferred = async {
                        RequestExecutor.executeChartsRequest("ZZ", visitorId)
                    }
                    val localDeferred = if (!isGlobal) async {
                        RequestExecutor.executeChartsRequest(countryCode, visitorId)
                    } else null
                    val feelGoodDeferred = async {
                        RequestExecutor.executeMoodCategoryRequest(
                            MoodCategoryParser.Mood.FEEL_GOOD.params, visitorId
                        )
                    }
                    val partyDeferred = async {
                        RequestExecutor.executeMoodCategoryRequest(
                            MoodCategoryParser.Mood.PARTY.params, visitorId
                        )
                    }

                    val globalJson   = globalDeferred.await()
                    val localJson    = localDeferred?.await()
                    val feelGoodJson = feelGoodDeferred.await()
                    val partyJson    = partyDeferred.await()

                    // Cache valid responses
                    if (globalJson.isNotBlank())   ExploreDiskCache.put(context, keyGlobal, globalJson)
                    if (!localJson.isNullOrBlank()) ExploreDiskCache.put(context, keyLocal, localJson)
                    if (feelGoodJson.isNotBlank())  ExploreDiskCache.put(context, keyFeelGood, feelGoodJson)
                    if (partyJson.isNotBlank())     ExploreDiskCache.put(context, keyParty, partyJson)

                    withContext(Dispatchers.Default) {
                        parseAll(countryCode, globalJson, localJson, feelGoodJson, partyJson)
                    }
                }
            }
        }.onSuccess { data ->
            _state.value = UiState.Loaded(data)
        }.onFailure { err ->
            _state.value = UiState.Error(err.message ?: "Failed to load Explore")
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    private fun parseAll(
        countryCode: String,
        globalJson: String,
        localJson: String?,
        feelGoodJson: String,
        partyJson: String
    ): ExploreData {
        val globalFeed   = ChartsParser.parse(globalJson)
        val localFeed    = localJson?.takeIf { it.isNotBlank() }?.let { ChartsParser.parse(it) }
        val feelGoodFeed = MoodCategoryParser.parse(feelGoodJson)
        val partyFeed    = MoodCategoryParser.parse(partyJson)

        return ExploreData(
            countryCode           = countryCode,
            countryName           = localFeed?.selectedCountryName?.takeIf { it.isNotBlank() }
                ?: countryCode,
            chartsGlobalSections  = globalFeed.sections,
            chartsLocalSections   = localFeed?.sections ?: emptyList(),
            feelGoodFirstSection  = feelGoodFeed.sections.firstOrNull(),
            partySections         = partyFeed.sections.take(2)
        )
    }
}
