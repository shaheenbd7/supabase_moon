package com.moon.supabasetester.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moon.supabasetester.data.SupabaseProvider
import com.moon.supabasetester.data.logged
import com.moon.supabasetester.ui.components.*
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class FunctionsViewModel : ViewModel() {
    private val client = SupabaseProvider.client
    private val _result = MutableStateFlow<Pair<String, Boolean>?>(null)
    val result = _result.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    private fun op(name: String, block: suspend () -> String) = viewModelScope.launch {
        _busy.value = name
        logged("Functions", name, block).fold(
            { _result.value = it to false },
            { _result.value = (it.message ?: "error") to true },
        )
        _busy.value = null
    }

    fun invokeEdge(fn: String, bodyJson: String) = op("EDGE $fn") {
        val body = if (bodyJson.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(bodyJson)
        val resp = client.functions.invoke(function = fn, body = body)
        "HTTP ${resp.status.value}\n${resp.bodyAsText()}"
    }

    fun rpc(fn: String, params: String) = op("RPC $fn") {
        val args = if (params.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(params).jsonObject
        client.postgrest.rpc(fn, args).data
    }
}

@Composable
fun FunctionsScreen(vm: FunctionsViewModel = viewModel()) {
    val result by vm.result.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var fn by remember { mutableStateOf("hello") }
    var body by remember { mutableStateOf("""{"name":"Moon"}""") }
    var rpcName by remember { mutableStateOf("add_numbers") }
    var rpcArgs by remember { mutableStateOf("""{"a":2,"b":3}""") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(Icons.Default.Code, "Functions & RPC", "Edge Functions and Postgres functions")

        SectionCard("Edge Function", "Deploy supabase/functions/hello with: supabase functions deploy hello") {
            Field(fn, { fn = it }, "Function name")
            Field(body, { body = it }, "JSON body", singleLine = false, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            BusyButton("Invoke", busy == "EDGE $fn", { vm.invokeEdge(fn.trim(), body) }, enabled = fn.isNotBlank())
        }

        SectionCard("Postgres function (RPC)", "add_numbers(a, b) and hello_world() are created by supabase/setup.sql") {
            Field(rpcName, { rpcName = it }, "Function name")
            Field(rpcArgs, { rpcArgs = it }, "JSON args", singleLine = false)
            BusyButton("Call RPC", busy == "RPC $rpcName", { vm.rpc(rpcName.trim(), rpcArgs) }, enabled = rpcName.isNotBlank())
        }

        ResultBox(result?.first, result?.second == true)
    }
}
