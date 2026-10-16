package com.moon.supabasetester.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.moon.supabasetester.data.BUCKET
import com.moon.supabasetester.data.SupabaseProvider
import com.moon.supabasetester.data.logged
import com.moon.supabasetester.ui.components.*
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

data class StoredFile(val path: String, val name: String, val size: Long?, val publicUrl: String) {
    val isImage get() = name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp")
}

class StorageViewModel : ViewModel() {
    private val bucket get() = SupabaseProvider.client.storage.from(BUCKET)
    private val folder = "uploads"

    private val _files = MutableStateFlow<List<StoredFile>>(emptyList())
    val files = _files.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _info = MutableStateFlow<Pair<String, Boolean>?>(null)
    val info = _info.asStateFlow()

    private fun op(block: suspend () -> Unit) = viewModelScope.launch { _busy.value = true; block(); _busy.value = false }

    fun refresh() = op {
        logged("Storage", "LIST $BUCKET/$folder") {
            bucket.list(folder).filter { it.name != ".emptyFolderPlaceholder" }.map {
                val path = "$folder/${it.name}"
                StoredFile(path, it.name, (it.metadata?.get("size") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull(), bucket.publicUrl(path))
            }
        }.fold({ _files.value = it; _info.value = null }, { _info.value = (it.message ?: "error") to true })
    }

    fun upload(resolver: ContentResolver, uri: Uri) = op {
        logged("Storage", "UPLOAD") {
            val (name, bytes) = withContext(Dispatchers.IO) {
                val n = resolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                } ?: "file-${System.currentTimeMillis()}"
                n to (resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read file"))
            }
            bucket.upload("$folder/$name", bytes) { upsert = true }
            "$name (${bytes.size} bytes)"
        }.fold({ _info.value = "Uploaded $it" to false }, { _info.value = (it.message ?: "error") to true })
        refresh()
    }

    fun signedUrl(f: StoredFile) = op {
        logged("Storage", "SIGNED URL ${f.path}") { bucket.createSignedUrl(f.path, 10.minutes) }
            .fold({ _info.value = "Signed URL (10 min):\n$it" to false }, { _info.value = (it.message ?: "error") to true })
    }

    fun download(f: StoredFile) = op {
        logged("Storage", "DOWNLOAD ${f.path}") { bucket.downloadPublic(f.path).size }
            .fold({ _info.value = "Downloaded $it bytes via public URL" to false }, { _info.value = (it.message ?: "error") to true })
    }

    fun delete(f: StoredFile) = op {
        logged("Storage", "DELETE ${f.path}") { bucket.delete(f.path) }
            .onFailure { _info.value = (it.message ?: "error") to true }
        refresh()
    }
}

@Composable
fun StorageScreen(vm: StorageViewModel = viewModel()) {
    val files by vm.files.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.upload(resolver, uri)
    }
    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader(Icons.Default.Folder, "Storage", "Bucket '$BUCKET': upload, list, URLs, delete") }
        item {
            SectionCard("Actions", "Upload requires a signed-in user (storage RLS)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BusyButton("Upload file", busy, { picker.launch("*/*") })
                    BusyButton("Refresh", busy, vm::refresh, tonal = true)
                }
                ResultBox(info?.first, info?.second == true)
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Text("${files.size} files", style = MaterialTheme.typography.labelLarge) }
        items(files, key = { it.path }) { f ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (f.isImage) AsyncImage(f.publicUrl, f.name, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.InsertDriveFile, null, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Mono(f.size?.let { "$it bytes" } ?: f.path)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip({ vm.signedUrl(f) }, { Text("Signed URL") }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) })
                        AssistChip({ vm.download(f) }, { Text("Download") }, leadingIcon = { Icon(Icons.Default.Download, null, Modifier.size(18.dp)) })
                        AssistChip({ vm.delete(f) }, { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) })
                    }
                }
            }
        }
    }
}
