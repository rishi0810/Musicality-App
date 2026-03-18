package com.proj.Musicality.cache

import android.content.Context
import com.proj.Musicality.data.model.HomeFeed
import com.proj.Musicality.data.model.HomeSection
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object HomeDiskCache {

    private const val DIR = "home"
    private const val API_FILE = "api-feed.json"
    private const val PERSONALIZATION_FILE = "personalized-feed.json"
    private const val API_TTL_MS = 30L * 60L * 1000L
    private const val PERSONALIZATION_TTL_MS = 6L * 60L * 60L * 1000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun getApiFeed(context: Context): HomeFeed? {
        val payload = read<ApiFeedPayload>(context, API_FILE) ?: return null
        if (isExpired(payload.savedAtMillis, API_TTL_MS)) return null
        return payload.feed
    }

    fun putApiFeed(context: Context, feed: HomeFeed) {
        write(context, API_FILE, ApiFeedPayload(savedAtMillis = System.currentTimeMillis(), feed = feed))
    }

    fun getPersonalization(context: Context, snapshotKey: String): PersonalizedFeedPayload? {
        val payload = getLatestPersonalization(context) ?: return null
        return payload.takeIf { it.snapshotKey == snapshotKey }
    }

    fun getLatestPersonalization(context: Context): PersonalizedFeedPayload? {
        val payload = read<PersonalizedFeedPayload>(context, PERSONALIZATION_FILE) ?: return null
        if (isExpired(payload.savedAtMillis, PERSONALIZATION_TTL_MS)) return null
        return payload
    }

    fun putPersonalization(
        context: Context,
        snapshotKey: String,
        reservedSectionCount: Int,
        sections: List<HomeSection>
    ) {
        write(
            context,
            PERSONALIZATION_FILE,
            PersonalizedFeedPayload(
                savedAtMillis = System.currentTimeMillis(),
                snapshotKey = snapshotKey,
                reservedSectionCount = reservedSectionCount,
                sections = sections
            )
        )
    }

    fun invalidate(context: Context) {
        file(context, API_FILE).delete()
        file(context, PERSONALIZATION_FILE).delete()
    }

    private inline fun <reified T> read(context: Context, name: String): T? {
        val file = file(context, name)
        if (!file.exists()) return null
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }

    private fun write(context: Context, name: String, value: Any) {
        val file = file(context, name)
        file.parentFile?.mkdirs()
        val encoded = when (value) {
            is ApiFeedPayload -> json.encodeToString(ApiFeedPayload.serializer(), value)
            is PersonalizedFeedPayload -> json.encodeToString(PersonalizedFeedPayload.serializer(), value)
            else -> return
        }
        file.writeText(encoded)
    }

    private fun file(context: Context, name: String): File =
        File(context.cacheDir, "$DIR/$name")

    private fun isExpired(savedAtMillis: Long, ttlMillis: Long): Boolean =
        System.currentTimeMillis() - savedAtMillis > ttlMillis

    @Serializable
    data class ApiFeedPayload(
        val savedAtMillis: Long,
        val feed: HomeFeed
    )

    @Serializable
    data class PersonalizedFeedPayload(
        val savedAtMillis: Long,
        val snapshotKey: String,
        val reservedSectionCount: Int,
        val sections: List<HomeSection>
    )
}
