package com.moon.supabasetester.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moon.supabasetester.data.Note
import com.moon.supabasetester.data.SupabaseProvider
import com.moon.supabasetester.data.logged
import com.moon.supabasetester.ui.components.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NoteFilter { ALL, PENDING, DONE }

class DatabaseViewModel : ViewModel() {
    private val db = SupabaseProvider.client
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes = _notes.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private fun launchOp(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        block()
        _busy.value = false
    }

    fun load(query: String = "", filter: NoteFilter = NoteFilter.ALL, limit: Long = 50) = launchOp {
        logged("Database", "SELECT notes q='$query' filter=$filter limit=$limit") {
            db.from("notes").select {
                filter {
                    if (query.isNotBlank()) ilike("title", "%$query%")
                    when (filter) {
                        NoteFilter.DONE -> eq("is_done", true)
                        NoteFilter.PENDING -> eq("is_done", false)
                        NoteFilter.ALL -> {}
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }.decodeList<Note>()
        }.fold({ _notes.value = it; _error.value = null }, { _error.value = it.message })
    }

    fun add(title: String, content: String, after: () -> Unit) = launchOp {
        logged("Database", "INSERT note '$title'") {
            db.from("notes").insert(Note(title = title, content = content)) { select() }.decodeSingle<Note>()
        }.onSuccess { _error.value = null; after() }.onFailure { _error.value = it.message }
    }

    fun toggle(n: Note, after: () -> Unit) = launchOp {
        logged("Database", "UPDATE note ${n.id} is_done=${!n.isDone}") {
            db.from("notes").update({ set("is_done", !n.isDone) }) { filter { eq("id", n.id!!) } }
        }.onSuccess { after() }.onFailure { _error.value = it.message }
    }

    fun rename(n: Note, title: String, after: () -> Unit) = launchOp {
        logged("Database", "UPDATE note ${n.id} title") {
            db.from("notes").update({ set("title", title) }) { filter { eq("id", n.id!!) } }
        }.onSuccess { after() }.onFailure { _error.value = it.message }
    }

    fun delete(n: Note, after: () -> Unit) = launchOp {
        logged("Database", "DELETE note ${n.id}") {
            db.from("notes").delete { filter { eq("id", n.id!!) } }
        }.onSuccess { after() }.onFailure { _error.value = it.message }
    }
}

@Composable
fun DatabaseScreen(vm: DatabaseViewModel = viewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NoteFilter.ALL) }
    var editing by remember { mutableStateOf<Note?>(null) }

    fun refresh() = vm.load(query, filter)
    LaunchedEffect(filter) { vm.load(query, filter) }

    editing?.let { n ->
        var text by remember(n) { mutableStateOf(n.title) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Rename note") },
            text = { Field(text, { text = it }, "Title") },
            confirmButton = {
                TextButton(onClick = { vm.rename(n, text, ::refresh); editing = null }, enabled = text.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(Icons.Default.Storage, "Database", "PostgREST CRUD on public.notes with RLS") }
        item {
            SectionCard("Create (INSERT)", "Needs a signed-in user: user_id defaults to auth.uid()") {
                Field(title, { title = it }, "Title")
                Field(content, { content = it }, "Content", singleLine = false)
                BusyButton("Add note", busy, { vm.add(title.trim(), content.trim()) { title = ""; content = ""; refresh() } }, enabled = title.isNotBlank())
            }
        }
        item {
            SectionCard("Read (SELECT)", "ilike search, eq filter, order, limit") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        query, { query = it }, Modifier.weight(1f),
                        label = { Text("Search title") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                    )
                    FilledTonalIconButton(onClick = ::refresh) { Icon(Icons.Default.Refresh, "Reload") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoteFilter.entries.forEach {
                        FilterChip(filter == it, { filter = it }, label = { Text(it.name.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
                ResultBox(error, isError = true)
                if (error != null) Text(
                    "If the table is missing, run supabase/setup.sql in the SQL editor.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { Text("${notes.size} notes", style = MaterialTheme.typography.labelLarge) }
        items(notes, key = { it.id ?: it.hashCode().toLong() }) { n ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(n.isDone, { vm.toggle(n, ::refresh) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            n.title, style = MaterialTheme.typography.titleSmall,
                            textDecoration = if (n.isDone) TextDecoration.LineThrough else null,
                        )
                        if (n.content.isNotBlank()) Text(n.content, style = MaterialTheme.typography.bodySmall)
                        Mono("#${n.id}  ${n.createdAt?.take(19)?.replace('T', ' ') ?: ""}")
                    }
                    IconButton({ editing = n }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton({ vm.delete(n, ::refresh) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
}
