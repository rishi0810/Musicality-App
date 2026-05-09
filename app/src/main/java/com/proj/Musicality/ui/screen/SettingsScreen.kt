package com.proj.Musicality.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proj.Musicality.cache.AppCache
import com.proj.Musicality.cache.AudioFileCache
import com.proj.Musicality.cache.ExploreDiskCache
import com.proj.Musicality.cache.HomeDiskCache
import com.proj.Musicality.config.AppConfig
import com.proj.Musicality.config.CornerRadiusPreset
import com.proj.Musicality.config.ThemeMode
import com.proj.Musicality.data.local.ListeningHistoryRepository
import com.proj.Musicality.ui.components.HapticIconButton
import com.proj.Musicality.update.AppUpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    collapsedMiniPlayerHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by AppConfig.themeMode.collectAsStateWithLifecycle()
    val crossfadeEnabled by AppConfig.crossfadeEnabled.collectAsStateWithLifecycle()
    val cornerRadius by AppConfig.cornerRadius.collectAsStateWithLifecycle()
    val wordSyncLyrics by AppConfig.wordSyncLyrics.collectAsStateWithLifecycle()
    val listeningHistoryPaused by AppConfig.listeningHistoryPaused.collectAsStateWithLifecycle()
    val searchHistoryPaused by AppConfig.searchHistoryPaused.collectAsStateWithLifecycle()
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "unknown"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = collapsedMiniPlayerHeight + 8.dp)
    ) {
        item(key = "settings-header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
            ) {
                HapticIconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Appearance ──
        item(key = "section-appearance") {
            SectionLabel("Appearance", Icons.Rounded.Palette)
        }
        item(key = "theme-mode") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { AppConfig.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size
                            ),
                            icon = {
                                SegmentedButtonDefaults.Icon(themeMode == mode) {
                                    Icon(
                                        imageVector = when (mode) {
                                            ThemeMode.SYSTEM -> Icons.Rounded.Contrast
                                            ThemeMode.DARK -> Icons.Rounded.DarkMode
                                            ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                }
                            }
                        ) {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.LIGHT -> "Light"
                                }
                            )
                        }
                    }
                }
            }
        }
        item(key = "corner-radius") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Corner radius",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CornerRadiusPreset.entries.forEachIndexed { index, preset ->
                        SegmentedButton(
                            selected = cornerRadius == preset,
                            onClick = { AppConfig.setCornerRadius(preset) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CornerRadiusPreset.entries.size
                            ),
                            icon = {
                                SegmentedButtonDefaults.Icon(cornerRadius == preset) {
                                    Icon(
                                        imageVector = Icons.Rounded.RoundedCorner,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                }
                            }
                        ) {
                            Text(preset.label)
                        }
                    }
                }
            }
        }
        item(key = "divider-appearance") { SettingsDivider() }

        // ── Playback ──
        item(key = "section-playback") {
            SectionLabel("Playback", Icons.Rounded.MusicNote)
        }
        item(key = "crossfade") {
            SettingsSwitch(
                title = "Crossfade",
                subtitle = "Blend tracks with DSP-shaped transitions",
                checked = crossfadeEnabled,
                onCheckedChange = { AppConfig.setCrossfadeEnabled(it) }
            )
        }
        item(key = "word-sync-lyrics") {
            SettingsSwitch(
                title = "Word-by-word lyrics",
                subtitle = "Highlight each word in sync with the music",
                checked = wordSyncLyrics,
                onCheckedChange = { AppConfig.setWordSyncLyrics(it) }
            )
        }
        item(key = "divider-playback") { SettingsDivider() }

        // ── Privacy ──
        item(key = "section-privacy") {
            SectionLabel("Privacy", Icons.Rounded.Shield)
        }
        item(key = "pause-listening-history") {
            SettingsSwitch(
                title = "Pause listening history",
                subtitle = "Stops recording plays and disables personalization",
                checked = listeningHistoryPaused,
                onCheckedChange = { AppConfig.setListeningHistoryPaused(it) }
            )
        }
        item(key = "pause-search-history") {
            SettingsSwitch(
                title = "Pause search history",
                subtitle = "New searches won't be saved (max 5 kept)",
                checked = searchHistoryPaused,
                onCheckedChange = { AppConfig.setSearchHistoryPaused(it) }
            )
        }
        item(key = "clear-personalization") {
            var cleared by remember { mutableStateOf(false) }
            SettingsAction(
                title = "Clear personalization data",
                subtitle = if (cleared) "Cleared" else "Listening history, play counts, and recommendations",
                icon = Icons.Rounded.DeleteSweep,
                onClick = {
                    scope.launch {
                        ListeningHistoryRepository.getInstance(context.applicationContext).clearAll()
                        HomeDiskCache.invalidate(context)
                        AppCache.browse.clear()
                        cleared = true
                        Toast.makeText(context, "Personalization data cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        item(key = "divider-privacy") { SettingsDivider() }

        // ── Storage ──
        item(key = "section-storage") {
            SectionLabel("Storage", Icons.Rounded.Storage)
        }
        item(key = "clear-audio-cache") {
            var cleared by remember { mutableStateOf(false) }
            SettingsAction(
                title = "Clear audio cache",
                subtitle = if (cleared) "Cleared" else "Cached audio files for gapless playback",
                icon = Icons.Rounded.DeleteSweep,
                onClick = {
                    AudioFileCache.clearAll()
                    cleared = true
                    Toast.makeText(context, "Audio cache cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }
        item(key = "clear-browse-cache") {
            var cleared by remember { mutableStateOf(false) }
            SettingsAction(
                title = "Clear browse cache",
                subtitle = if (cleared) "Cleared" else "Home feed, explore data, and in-memory caches",
                icon = Icons.Rounded.DeleteSweep,
                onClick = {
                    AppCache.browse.clear()
                    AppCache.search.clear()
                    AppCache.paletteColors.clear()
                    HomeDiskCache.invalidate(context)
                    ExploreDiskCache.invalidate(context)
                    cleared = true
                    Toast.makeText(context, "Browse cache cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }
        item(key = "clear-search-history") {
            var cleared by remember { mutableStateOf(false) }
            SettingsAction(
                title = "Clear search history",
                subtitle = if (cleared) "Cleared" else "Recent search terms",
                icon = Icons.Rounded.History,
                onClick = {
                    context.getSharedPreferences("search_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().remove("search_history").apply()
                    cleared = true
                    Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }
        item(key = "divider-storage") { SettingsDivider() }

        // ── About ──
        item(key = "section-about") {
            SectionLabel("About", Icons.Rounded.Info)
        }
        item(key = "version") {
            SettingsAction(
                title = "Version",
                subtitle = versionName,
                icon = Icons.Rounded.Info,
                onClick = {}
            )
        }
        item(key = "check-update") {
            SettingsAction(
                title = "Check for updates",
                subtitle = "Download the latest version",
                icon = Icons.Rounded.SystemUpdate,
                onClick = {
                    scope.launch {
                        val update = AppUpdateManager.checkForUpdate(context.applicationContext, notify = false)
                        if (update != null) {
                            AppUpdateManager.downloadUpdate(context.applicationContext, update)
                            Toast.makeText(context, "Downloading ${update.latestVersionName}…", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "You're on the latest version", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}
