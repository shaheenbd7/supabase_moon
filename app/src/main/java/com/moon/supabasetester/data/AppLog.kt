package com.moon.supabasetester.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, SUCCESS, ERROR }

data class LogEntry(
    val id: Long,
    val time: String,
    val tag: String,
    val message: String,
    val level: LogLevel,
)

/** In-app console: every Supabase call from every screen is recorded here. */
object AppLog {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var counter = 0L
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val e = LogEntry(++counter, fmt.format(Date()), tag, message, level)
        _entries.update { (listOf(e) + it).take(300) }
    }

    fun clear() = _entries.update { emptyList() }
}

/** Runs [block], logs success/failure under [tag] and returns a [Result]. */
suspend fun <T> logged(tag: String, label: String, block: suspend () -> T): Result<T> {
    AppLog.add(tag, "$label …")
    return try {
        val v = block()
        AppLog.add(tag, "$label ✓ ${v.preview()}", LogLevel.SUCCESS)
        Result.success(v)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLog.add(tag, "$label ✗ ${e.message ?: e::class.simpleName}", LogLevel.ERROR)
        Result.failure(e)
    }
}

private fun Any?.preview(): String {
    val s = when (this) {
        null, Unit -> ""
        is Collection<*> -> "(${size} items)"
        else -> toString()
    }
    return if (s.length > 160) s.take(160) + "…" else s
}
