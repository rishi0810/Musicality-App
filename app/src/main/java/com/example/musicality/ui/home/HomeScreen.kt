package com.example.musicality.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicality.util.UiState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val todoState by viewModel.todoState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center
    ) {
        when (val state = todoState) {
            is UiState.Idle -> {
                Text(
                    text = "Waiting to load data...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            is UiState.Loading -> {
                CircularProgressIndicator()
            }
            
            is UiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "API Response",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "ID: ${state.data.id}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "Title: ${state.data.title}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "Completed: ${if (state.data.isCompleted) "✓ Yes" else "✗ No"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "User ID: ${state.data.userId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // Display raw JSON-like format
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Raw JSON:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = """
                            {
                              "userId": ${state.data.userId},
                              "id": ${state.data.id},
                              "title": "${state.data.title}",
                              "completed": ${state.data.isCompleted}
                            }
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp)
                    )
                }
            }
            
            is UiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
