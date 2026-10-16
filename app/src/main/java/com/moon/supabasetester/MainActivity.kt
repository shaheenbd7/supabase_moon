package com.moon.supabasetester

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moon.supabasetester.data.AppLog
import com.moon.supabasetester.data.LogLevel
import com.moon.supabasetester.data.SupabaseProvider
import com.moon.supabasetester.ui.screens.*
import com.moon.supabasetester.ui.theme.SupabaseMoonTheme
import io.github.jan.supabase.auth.handleDeeplinks

object Routes {
    const val HOME = "home"
    const val AUTH = "auth"
    const val DATABASE = "database"
    const val STORAGE = "storage"
    const val REALTIME = "realtime"
    const val FUNCTIONS = "functions"
    const val LOGS = "logs"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Dashboard),
    Tab(Routes.AUTH, "Auth", Icons.Default.Lock),
    Tab(Routes.DATABASE, "Data", Icons.Default.Storage),
    Tab(Routes.STORAGE, "Files", Icons.Default.Folder),
    Tab(Routes.REALTIME, "Live", Icons.Default.Bolt),
)

private val titles = mapOf(Routes.FUNCTIONS to "Functions & RPC", Routes.LOGS to "Logs")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLink(intent)
        setContent { SupabaseMoonTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        if (intent?.data?.scheme != com.moon.supabasetester.data.DEEPLINK_SCHEME) return
        runCatching { SupabaseProvider.client.handleDeeplinks(intent) }
            .onSuccess { AppLog.add("Auth", "Deep link handled", LogLevel.SUCCESS) }
            .onFailure { AppLog.add("Auth", "Deep link failed: ${it.message}", LogLevel.ERROR) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: Routes.HOME
    val isTab = tabs.any { it.route == route }

    fun go(r: String) = nav.navigate(r) {
        launchSingleTop = true
        if (r in tabs.map { it.route }) {
            popUpTo(Routes.HOME) { saveState = true }
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[route] ?: tabs.firstOrNull { it.route == route }?.label?.let { "Supabase Moon" } ?: "Supabase Moon") },
                navigationIcon = {
                    if (!isTab) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (route != Routes.LOGS) IconButton({ go(Routes.LOGS) }) { Icon(Icons.Default.Terminal, "Logs") }
                    if (route != Routes.FUNCTIONS) IconButton({ go(Routes.FUNCTIONS) }) { Icon(Icons.Default.Code, "Functions") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(route == t.route, { go(t.route) }, { Icon(t.icon, t.label) }, label = { Text(t.label) })
                }
            }
        },
    ) { pad ->
        NavHost(nav, Routes.HOME, Modifier.padding(pad)) {
            composable(Routes.HOME) { HomeScreen(onOpen = ::go) }
            composable(Routes.AUTH) { AuthScreen() }
            composable(Routes.DATABASE) { DatabaseScreen() }
            composable(Routes.STORAGE) { StorageScreen() }
            composable(Routes.REALTIME) { RealtimeScreen() }
            composable(Routes.FUNCTIONS) { FunctionsScreen() }
            composable(Routes.LOGS) { LogsScreen() }
        }
    }
}
