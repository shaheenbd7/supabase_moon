package com.moon.supabasetester.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moon.supabasetester.data.AppLog
import com.moon.supabasetester.ui.components.*

@Composable
fun LogsScreen() {
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${entries.size} entries", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(AppLog::clear) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Clear") }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.id }) { e ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp)) { StatusDot(e.level) }
                    Column {
                        Mono("${e.time}  [${e.tag}]")
                        Text(e.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
