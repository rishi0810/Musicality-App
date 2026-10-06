package com.proj.Musicality.ui.components

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.proj.Musicality.config.LocalCornerRadius
import com.proj.Musicality.config.scaled
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.core.net.toUri
import com.proj.Musicality.data.local.CustomAlbum
import com.proj.Musicality.data.local.LibraryRepository
import com.proj.Musicality.data.model.MediaItem
import com.proj.Musicality.ui.theme.AppShapes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CustomAlbumArtwork(path: String?, modifier: Modifier = Modifier) {
    val radiusPreset = LocalCornerRadius.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp.scaled(radiusPreset)))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (path != null) {
            AsyncImage(
                model = if (path.startsWith("content:")) path.toUri() else File(path),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAlbumFormSheet(
    repository: LibraryRepository,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    album: CustomAlbum? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(album?.id) { mutableStateOf(album?.name.orEmpty()) }
    var pickedUri by rememberSaveable(album?.id) { mutableStateOf<String?>(null) }
    var removeArtwork by rememberSaveable(album?.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedUri = uri.toString()
            removeArtwork = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !saving }),
        shape = AppShapes.bottomSheet()
    ) {
        val radiusPreset = LocalCornerRadius.current
        val artwork = pickedUri ?: album?.artworkPath.takeUnless { removeArtwork }
        val chooseArtwork = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(if (album == null) "New album" else "Edit album", style = MaterialTheme.typography.headlineMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Album name", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = { Text("e.g. Workout") },
                        singleLine = true,
                        enabled = !saving,
                        isError = name.trim().length > 100,
                        shape = RoundedCornerShape(16.dp.scaled(radiusPreset)),
                        supportingText = if (name.trim().length > 80) {
                            { Text("${name.trim().length}/100") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Artwork", style = MaterialTheme.typography.titleSmall)
                        if (artwork != null) TextButton(
                            onClick = { pickedUri = null; removeArtwork = true }, enabled = !saving
                        ) { Text("Remove") }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(168.dp)
                            .hapticClickable(enabled = !saving, onClickLabel = "Choose album artwork", onClick = chooseArtwork),
                        shape = RoundedCornerShape(20.dp.scaled(radiusPreset)),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (artwork == null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                    Text("Choose artwork", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                CustomAlbumArtwork(artwork, Modifier.fillMaxSize())
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                ) {
                                    Text("Change artwork", style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss, enabled = !saving,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                        error = null
                        scope.launch(Dispatchers.Main) {
                            try {
                                val uri = pickedUri?.toUri()
                                val id = if (album == null) repository.createCustomAlbum(name, uri).id else {
                                    repository.editCustomAlbum(album.id, name, uri, removeArtwork)
                                    album.id
                                }
                                Toast.makeText(context, if (album == null) "Album created" else "Album updated", Toast.LENGTH_SHORT).show()
                                onSaved(id)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                Log.e("CustomAlbums", "Could not save album", failure)
                                error = "Could not save the album. Please try again."
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && name.trim().isNotEmpty() && name.trim().length <= 100,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) { Text(if (saving) "Saving…" else if (album == null) "Create album" else "Save changes") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCustomAlbumSheet(item: MediaItem, repository: LibraryRepository, onDismiss: () -> Unit) {
    AddToCustomAlbumSheet(listOf(item), repository, onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCustomAlbumSheet(
    selectedItems: List<MediaItem>,
    repository: LibraryRepository,
    onDismiss: () -> Unit,
    onAdded: () -> Unit = {}
) {
    val items = remember(selectedItems) { selectedItems.distinctBy { it.videoId } }
    val selectionKey = items.map { it.videoId }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val membership by remember(selectionKey, repository) {
        kotlinx.coroutines.flow.combine(items.map { repository.observeCustomAlbumMembership(it.videoId) }) { memberships ->
            memberships.map { it.toSet() }.reduceOrNull { all, next -> all intersect next }.orEmpty()
        }
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    var selectedId by rememberSaveable(selectionKey) { mutableStateOf<String?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (showCreate) {
        CustomAlbumFormSheet(repository, onDismiss = { showCreate = false }, onSaved = {
            selectedId = it
            showCreate = false
        })
        return
    }
    val windowSize = LocalWindowInfo.current.containerSize
    val maxSheetHeight = with(LocalDensity.current) { windowSize.height.toDp() * 0.85f }
    val selectedAlbum = snapshot.customAlbums.firstOrNull { it.id == selectedId && it.id !in membership }
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !saving }),
        shape = AppShapes.bottomSheet()
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add to album", style = MaterialTheme.typography.headlineSmall)
            Text(if (items.size == 1) items.first().title else "${items.size} songs selected", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (snapshot.customAlbums.isEmpty()) Text("Create an album to save this song.")
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(snapshot.customAlbums, key = { it.id }) { album ->
                    val alreadyAdded = album.id in membership
                    val enabled = !saving && !alreadyAdded
                    Row(
                        Modifier.fillMaxWidth().hapticClickable(enabled = enabled) {
                            selectedId = album.id
                            error = null
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CustomAlbumArtwork(album.artworkPath, Modifier.size(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(album.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (alreadyAdded) "Already added" else "${album.itemCount} songs",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        RadioButton(selected = album.id == selectedAlbum?.id, onClick = {
                            selectedId = album.id
                            error = null
                        }, enabled = enabled)
                    }
                }
            }
            TextButton(onClick = { showCreate = true }, enabled = !saving) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create album")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val album = selectedAlbum ?: return@Button
                    saving = true
                    error = null
                    scope.launch(Dispatchers.Main) {
                        try {
                            val inserted = repository.addToCustomAlbum(album.id, items)
                            Toast.makeText(context, if (inserted > 0) "Added to ${album.name}" else "Already added", Toast.LENGTH_SHORT).show()
                            onAdded()
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            Log.e("CustomAlbums", "Could not add song", failure)
                            error = "Could not add the song. Please try again."
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && selectedAlbum != null && items.isNotEmpty() && items.all { it.videoId.isNotBlank() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saving) "Adding…" else "Add") }
        }
    }
}
