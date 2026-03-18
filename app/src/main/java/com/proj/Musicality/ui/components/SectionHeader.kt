package com.proj.Musicality.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val PERSONALIZATION_PREFIXES = listOf(
    "Similar to ",
    "Because you listened to ",
    "Because You Like ",
    "More from ",
    "More like ",
    "Inspired by ",
)

private val FANS_OF_REGEX = Regex("^(Fans of) (.+?)( also listen to.*)$", RegexOption.IGNORE_CASE)

private fun splitSectionTitle(title: String): Pair<String, String>? {
    for (prefix in PERSONALIZATION_PREFIXES) {
        if (title.startsWith(prefix, ignoreCase = true)) {
            return prefix.trimEnd() to title.substring(prefix.length)
        }
    }
    FANS_OF_REGEX.matchEntire(title)?.let { match ->
        return "Fans also listen to" to match.groupValues[2]
    }
    return null
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val split = remember(title) { splitSectionTitle(title) }

    if (split != null) {
        Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = split.first,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = split.second,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
