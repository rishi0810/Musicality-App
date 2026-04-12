package com.proj.Musicality.viewmodel

import android.util.Log
import android.content.Context
import com.proj.Musicality.api.VisitorManager
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.cache.HomeDiskCache
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.data.model.HomeFeed
import com.proj.Musicality.data.model.HomeSection
import com.proj.Musicality.data.parser.HomeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object HomePrefetchManager {
    private const val TAG = "HomePrefetchManager"
    private const val HOME_BROWSE_ID = "FEmusic_home"
    private const val HOME_API_FEED_CACHE_KEY = "home-feed-api"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiMutex = Mutex()
    private val personalizationMutex = Mutex()
    private val prefetchMutex = Mutex()
    private var pendingPrefetchContext: Context? = null
    private var pendingForceApi = false
    private var pendingForcePersonalization = false
    private var prefetchJob: Job? = null

    fun prefetch(
        context: Context,
        forceApi: Boolean = false,
        forcePersonalization: Boolean = false
    ) {
        val appContext = context.applicationContext
        scope.launch {
            prefetchMutex.withLock {
                pendingPrefetchContext = appContext
                pendingForceApi = pendingForceApi || forceApi
                pendingForcePersonalization = pendingForcePersonalization || forcePersonalization

                if (prefetchJob?.isActive == true) {
                    return@withLock
                }

                prefetchJob = scope.launch {
                    drainPrefetchQueue()
                }
            }
        }
    }

    suspend fun loadCached(context: Context): CachedHomeSnapshot {
        val appContext = context.applicationContext
        val generator = PersonalizedHomeFeedGenerator(
            ListeningHistoryRepository.getInstance(appContext)
        )
        val plan = generator.snapshotPlan()
        val cachedApiFeed = (AppCache.browse.get(HOME_API_FEED_CACHE_KEY) as? HomeFeed)
            ?: HomeDiskCache.getApiFeed(appContext)?.also { cached ->
                AppCache.browse.put(HOME_API_FEED_CACHE_KEY, cached)
            }
        val exactPersonalization = HomeDiskCache.getPersonalization(appContext, plan.snapshotKey)
        val latestPersonalization = exactPersonalization ?: HomeDiskCache.getLatestPersonalization(appContext)
        val reservedSlots = when {
            exactPersonalization != null -> exactPersonalization.reservedSectionCount
            plan.reservedSectionCount > 0 -> maxOf(
                plan.reservedSectionCount,
                latestPersonalization?.reservedSectionCount ?: 0
            )
            else -> latestPersonalization?.reservedSectionCount ?: 0
        }

        return CachedHomeSnapshot(
            apiFeed = cachedApiFeed,
            personalizedSections = exactPersonalization?.sections.orEmpty(),
            reservedPersonalizedSlots = reservedSlots,
            snapshotKey = plan.snapshotKey
        )
    }

    suspend fun streamApiFeed(
        context: Context,
        force: Boolean = false,
        onFeedUpdated: suspend (HomeFeed) -> Unit = {}
    ): HomeFeed? =
        apiMutex.withLock {
            val appContext = context.applicationContext
            val currentFeed = if (force) {
                null
            } else {
                loadCachedApiFeed(appContext)
            }

            var resolvedFeed: HomeFeed = currentFeed ?: run {
                val json = VisitorManager.executeBrowseRequestWithRecovery(HOME_BROWSE_ID)
                if (json.isBlank()) return null

                HomeParser.extractFeed(json).also { fetchedFeed ->
                    cacheApiFeed(appContext, fetchedFeed)
                }
            }

            onFeedUpdated(resolvedFeed)

            while (true) {
                val continuation = resolvedFeed.continuation ?: break
                val json = VisitorManager.executeBrowseContinuationRequestWithRecovery(continuation)
                if (json.isBlank()) break

                val next = HomeParser.extractContinuation(json)
                val mergedSections = if (next.sections.isEmpty()) {
                    resolvedFeed.sections
                } else {
                    resolvedFeed.sections + next.sections
                }
                val updatedFeed = resolvedFeed.copy(
                    sections = mergedSections,
                    continuation = next.continuation.takeUnless { it == continuation }
                )
                if (updatedFeed == resolvedFeed) break

                resolvedFeed = updatedFeed
                cacheApiFeed(appContext, resolvedFeed)
                onFeedUpdated(resolvedFeed)
            }

            resolvedFeed
        }

    suspend fun loadOrGeneratePersonalization(
        context: Context,
        force: Boolean = false
    ): PersonalizedSectionsResult = personalizationMutex.withLock {
        val appContext = context.applicationContext
        val generator = PersonalizedHomeFeedGenerator(
            ListeningHistoryRepository.getInstance(appContext)
        )
        val plan = generator.snapshotPlan()
        if (!force) {
            HomeDiskCache.getPersonalization(appContext, plan.snapshotKey)?.let { cached ->
                return PersonalizedSectionsResult(
                    snapshotKey = cached.snapshotKey,
                    reservedSectionCount = cached.reservedSectionCount,
                    sections = cached.sections
                )
            }
        }

        val result = generator.generateSections()
        HomeDiskCache.putPersonalization(
            context = appContext,
            snapshotKey = result.snapshotKey,
            reservedSectionCount = result.reservedSectionCount,
            sections = result.sections
        )
        result
    }

    suspend fun warmCaches(
        context: Context,
        forceApi: Boolean = false,
        forcePersonalization: Boolean = false
    ) = coroutineScope {
        val apiJob = async {
            streamApiFeed(context, force = forceApi)
        }
        val personalizationJob = async {
            loadOrGeneratePersonalization(context, force = forcePersonalization)
        }
        apiJob.await()
        personalizationJob.await()
    }

    private suspend fun drainPrefetchQueue() {
        while (true) {
            val request = prefetchMutex.withLock {
                val appContext = pendingPrefetchContext ?: run {
                    prefetchJob = null
                    return@withLock null
                }
                PrefetchRequest(
                    context = appContext,
                    forceApi = pendingForceApi,
                    forcePersonalization = pendingForcePersonalization
                ).also {
                    pendingPrefetchContext = null
                    pendingForceApi = false
                    pendingForcePersonalization = false
                }
            } ?: return

            runCatching {
                warmCaches(
                    context = request.context,
                    forceApi = request.forceApi,
                    forcePersonalization = request.forcePersonalization
                )
            }.onFailure { throwable ->
                Log.w(TAG, "prefetch: warmCaches failed", throwable)
            }
        }
    }

    private fun loadCachedApiFeed(context: Context): HomeFeed? {
        val memoryFeed = AppCache.browse.get(HOME_API_FEED_CACHE_KEY) as? HomeFeed
        if (memoryFeed != null) return memoryFeed

        return HomeDiskCache.getApiFeed(context)?.also { cached ->
            AppCache.browse.put(HOME_API_FEED_CACHE_KEY, cached)
        }
    }

    private fun cacheApiFeed(context: Context, feed: HomeFeed) {
        AppCache.browse.put(HOME_API_FEED_CACHE_KEY, feed)
        HomeDiskCache.putApiFeed(context, feed)
    }
}

internal data class CachedHomeSnapshot(
    val apiFeed: HomeFeed?,
    val personalizedSections: List<HomeSection>,
    val reservedPersonalizedSlots: Int,
    val snapshotKey: String
)

private data class PrefetchRequest(
    val context: Context,
    val forceApi: Boolean,
    val forcePersonalization: Boolean
)
