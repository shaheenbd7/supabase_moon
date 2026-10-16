package com.moon.supabasetester.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moon.supabasetester.data.*
import io.github.jan.supabase.realtime.*
import com.moon.supabasetester.ui.components.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RealtimeViewModel : ViewModel() {
    private val client = SupabaseProvider.client

    // --- Postgres changes --------------------------------------------------
    private var dbChannel: RealtimeChannel? = null
    private val _dbOn = MutableStateFlow(false)
    val dbOn = _dbOn.asStateFlow()
    private val _dbEvents = MutableStateFlow<List<String>>(emptyList())
    val dbEvents = _dbEvents.asStateFlow()

    fun toggleDb() = viewModelScope.launch {
        if (dbChannel == null) {
            logged("Realtime", "Subscribe to postgres_changes on notes") {
                val ch = client.channel("db-notes")
                ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "notes" }
                    .onEach { a ->
                        val line = when (a) {
                            is PostgresAction.Insert -> "INSERT ${a.record}"
                            is PostgresAction.Update -> "UPDATE ${a.record}"
                            is PostgresAction.Delete -> "DELETE ${a.oldRecord}"
                            is PostgresAction.Select -> "SELECT ${a.record}"
                        }
                        AppLog.add("Realtime", line, LogLevel.SUCCESS)
                        _dbEvents.update { (listOf(line) + it).take(50) }
                    }.launchIn(viewModelScope)
                ch.subscribe(blockUntilSubscribed = true)
                dbChannel = ch
            }.onSuccess { _dbOn.value = true }
        } else {
            logged("Realtime", "Unsubscribe postgres_changes") { dbChannel?.unsubscribe(); dbChannel?.let { client.realtime.removeChannel(it) } }
            dbChannel = null
            _dbOn.value = false
        }
    }

    // --- Broadcast + Presence ---------------------------------------------
    private var room: RealtimeChannel? = null
    private val _joined = MutableStateFlow(false)
    val joined = _joined.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _online = MutableStateFlow<Map<String, PresenceState>>(emptyMap())
    val online = _online.asStateFlow()
    private var me = ""

    fun join(name: String) = viewModelScope.launch {
        me = name
        logged("Realtime", "Join room as $name") {
            val ch = client.channel("room-lobby") {
                // deliver our own broadcasts back so a single device can verify the round trip
                broadcast { receiveOwnBroadcasts = true }
            }
            ch.broadcastFlow<ChatMessage>("chat").onEach { m ->
                _messages.update { (listOf(m) + it).take(100) }
            }.launchIn(viewModelScope)
            ch.presenceChangeFlow().onEach { p ->
                _online.update { cur ->
                    val next = cur.toMutableMap()
                    p.decodeJoinsAs<PresenceState>().forEach { next[it.user] = it }
                    p.decodeLeavesAs<PresenceState>().forEach { next.remove(it.user) }
                    next
                }
            }.launchIn(viewModelScope)
            ch.subscribe(blockUntilSubscribed = true)
            ch.track(PresenceState(user = name))
            room = ch
        }.onSuccess { _joined.value = true }
    }

    fun send(text: String) = viewModelScope.launch {
        val ch = room ?: return@launch
        logged("Realtime", "Broadcast '$text'") { ch.broadcast("chat", ChatMessage(me, text)) }
    }

    fun leave() = viewModelScope.launch {
        logged("Realtime", "Leave room") {
            room?.untrack()
            room?.unsubscribe()
            room?.let { client.realtime.removeChannel(it) }
        }
        room = null
        _joined.value = false
        _online.value = emptyMap()
    }

    override fun onCleared() {
        val db = dbChannel; val r = room
        // viewModelScope is already cancelled here; use the client's scope-free cleanup
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.NonCancellable) {
            runCatching { db?.unsubscribe(); r?.unsubscribe() }
        }
    }
}

@Composable
fun RealtimeScreen(vm: RealtimeViewModel = viewModel()) {
    val dbOn by vm.dbOn.collectAsStateWithLifecycle()
    val dbEvents by vm.dbEvents.collectAsStateWithLifecycle()
    val joined by vm.joined.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("user-${(100..999).random()}") }
    var text by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(Icons.Default.Bolt, "Realtime", "Postgres changes, Broadcast and Presence")

        SectionCard("Postgres changes", "Live INSERT/UPDATE/DELETE on notes. Add/edit notes on the Database tab (or another device) to see events.") {
            Button(vm::toggleDb::invoke) { Text(if (dbOn) "Unsubscribe" else "Subscribe") }
            if (dbEvents.isEmpty()) Text("No events yet.", style = MaterialTheme.typography.bodySmall)
            dbEvents.forEach { Mono(it) }
        }

        SectionCard("Room: Broadcast + Presence", "Open on two devices to chat and see who is online") {
            if (!joined) {
                Field(name, { name = it }, "Display name")
                Button({ vm.join(name.trim()) }, enabled = name.isNotBlank()) { Text("Join room") }
            } else {
                Text("Online (${online.size}): ${online.keys.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text("Message") }, singleLine = true)
                    FilledIconButton({ vm.send(text.trim()); text = "" }, enabled = text.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
                }
                messages.forEach { Mono("${it.sender}: ${it.text}") }
                OutlinedButton(vm::leave::invoke) { Text("Leave room") }
            }
        }
    }
}
