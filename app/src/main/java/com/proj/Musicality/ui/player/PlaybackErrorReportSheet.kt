package com.proj.Musicality.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proj.Musicality.ui.theme.AppShapes
import com.proj.Musicality.viewmodel.PlaybackErrorReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackErrorReportSheet(
    report: PlaybackErrorReport,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit,
) {
    var userNotes by remember(report.videoId, report.reason) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.bottomSheet(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Playback error",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${report.title} • ${report.artistName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Playback Error. The failed song is not reachable. Add details below the fixed error information if you want to report it via email",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card(),
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = report.fixedMessage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = userNotes,
                onValueChange = { userNotes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your notes") },
                minLines = 4,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
                Button(onClick = { onReport(userNotes) }) {
                    Text("Report by email")
                }
            }
        }
    }
}
