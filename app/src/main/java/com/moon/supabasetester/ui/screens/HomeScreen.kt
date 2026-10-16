package com.moon.supabasetester.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moon.supabasetester.BuildConfig
import com.moon.supabasetester.Routes
import com.moon.supabasetester.data.*
import com.moon.supabasetester.ui.components.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

data class Check(val name: String, val state: LogLevel? = null, val detail: String = "…")

class HomeViewModel : ViewModel() {
    private val c = SupabaseProvider.client
    private val _checks = MutableStateFlow(emptyList<Check>())
    val checks = _checks.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private val steps: List<Pair<String, suspend () -> String>> = listOf(
        "Auth service" to {
            withContext(Dispatchers.IO) {
                val conn = URL("${BuildConfig.SUPABASE_URL}/auth/v1/health").openConnection() as HttpURLConnection
                conn.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                "health HTTP ${conn.responseCode}".also { conn.disconnect() }
            }
        },
        "Auth session" to { c.auth.currentSessionOrNull()?.user?.let { "signed in: ${it.email ?: it.id}" } ?: "no session (sign in on Auth tab)" },
        "Database (notes)" to { "${c.from("notes").select { limit(1) }.data.length} bytes returned" },
        "RPC (add_numbers)" to { "2 + 3 = " + c.postgrest.rpc("add_numbers", buildJsonObject { put("a", 2); put("b", 3) }).data },
        "Storage ($BUCKET)" to { "${c.storage.from(BUCKET).list("uploads").size} files in uploads/" },
        "Realtime socket" to {
            withTimeout(10_000) { c.realtime.connect() }
            "status: ${c.realtime.status.value}"
        },
        "Edge Function (hello)" to { "HTTP " + c.functions.invoke("hello").status.value },
    )

    fun runAll() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            _checks.value = steps.map { Check(it.first) }
            steps.forEachIndexed { i, (name, step) ->
                val r = logged("Diagnostics", name) { step() }
                _checks.update { list ->
                    list.toMutableList().also {
                        it[i] = r.fold({ d -> Check(name, LogLevel.SUCCESS, d) }, { e -> Check(name, LogLevel.ERROR, (e.message ?: "error").trim().lines().first().take(140)) })
                    }
                }
            }
            _running.value = false
        }
    }
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val checks by vm.checks.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(Icons.Default.Dashboard, "Supabase Moon", BuildConfig.SUPABASE_URL.removePrefix("https://"))

        SectionCard("Connection check", "Runs one request against every Supabase service") {
            BusyButton("Run all checks", running, vm::runAll)
            checks.forEach {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 5.dp)) { StatusDot(it.state) }
                    Column {
                        Text(it.name, style = MaterialTheme.typography.bodyMedium)
                        Mono(it.detail)
                    }
                }
            }
            if (checks.any { it.state == LogLevel.ERROR }) {
                Text(
                    "Failures on Database / RPC / Storage usually mean supabase/setup.sql has not been run yet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("Features", style = MaterialTheme.typography.titleMedium)
        FeatureTile(Icons.Default.Lock, "Authentication", "Email, OTP, anonymous, OAuth, profile") { onOpen(Routes.AUTH) }
        FeatureTile(Icons.Default.Storage, "Database", "Insert, select, update, delete with RLS") { onOpen(Routes.DATABASE) }
        FeatureTile(Icons.Default.Folder, "Storage", "Upload, list, signed URLs, delete") { onOpen(Routes.STORAGE) }
        FeatureTile(Icons.Default.Bolt, "Realtime", "DB changes, broadcast, presence") { onOpen(Routes.REALTIME) }
        FeatureTile(Icons.Default.Code, "Functions & RPC", "Edge Functions and SQL functions") { onOpen(Routes.FUNCTIONS) }
        FeatureTile(Icons.Default.Terminal, "Logs", "Every request and response") { onOpen(Routes.LOGS) }
    }
}

@Composable
private fun FeatureTile(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
