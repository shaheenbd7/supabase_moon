package com.moon.supabasetester.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moon.supabasetester.data.SupabaseProvider
import com.moon.supabasetester.data.logged
import com.moon.supabasetester.ui.components.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.OtpType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthViewModel : ViewModel() {
    private val auth = SupabaseProvider.client.auth
    val status: StateFlow<SessionStatus> = auth.sessionStatus

    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val message = _message.asStateFlow()

    private fun run(name: String, ok: String? = null, block: suspend () -> Any?) {
        viewModelScope.launch {
            _busy.value = name
            val r = logged("Auth", name) { block() }
            _message.value = r.fold(
                { (ok ?: "$name succeeded") to false },
                { "$name failed: ${it.message}" to true },
            )
            _busy.value = null
        }
    }

    fun signUp(email: String, pw: String) = run("Sign up", "Account created. If email confirmation is on, check your inbox.") {
        auth.signUpWith(Email) { this.email = email; this.password = pw }
    }
    fun signIn(email: String, pw: String) = run("Sign in") {
        auth.signInWith(Email) { this.email = email; this.password = pw }
    }
    fun anonymous() = run("Anonymous sign-in") { auth.signInAnonymously() }
    fun magicLink(email: String) = run("Send magic link / OTP", "Email sent. Tap the link or enter the code below.") {
        auth.signInWith(OTP) { this.email = email }
    }
    fun verifyOtp(email: String, code: String) = run("Verify email code") {
        auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
    }
    fun resetPassword(email: String) = run("Password reset email", "Reset email sent.") { auth.resetPasswordForEmail(email) }
    fun github() = run("GitHub OAuth", "Browser opened for GitHub login.") { auth.signInWith(Github) }
    fun updatePassword(pw: String) = run("Update password") { auth.updateUser { password = pw } }
    fun updateMetadata(name: String) = run("Update user metadata") {
        auth.updateUser { data = buildJsonObject { put("display_name", name) } }
    }
    fun refresh() = run("Refresh session") { auth.refreshCurrentSession() }
    fun fetchUser() = run("Fetch user from server") { auth.retrieveUserForCurrentSession(updateSession = true) }
    fun signOut() = run("Sign out") { auth.signOut() }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(Icons.Default.Lock, "Authentication", "Email, magic link, anonymous, OAuth, session")

        SectionCard("Session") {
            when (val s = status) {
                is SessionStatus.Authenticated -> {
                    val u = s.session.user
                    Mono("status   : AUTHENTICATED")
                    Mono("user id  : ${u?.id}")
                    Mono("email    : ${u?.email?.ifBlank { null } ?: "(anonymous)"}")
                    Mono("provider : ${u?.appMetadata?.get("provider")}")
                    Mono("metadata : ${u?.userMetadata}")
                    Mono("expires  : ${s.session.expiresAt}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BusyButton("Refresh", busy == "Refresh session", vm::refresh, tonal = true)
                        BusyButton("Get user", busy == "Fetch user from server", vm::fetchUser, tonal = true)
                        BusyButton("Sign out", busy == "Sign out", vm::signOut)
                    }
                }
                is SessionStatus.Initializing -> Text("Loading session…")
                is SessionStatus.RefreshFailure -> Text("Session refresh failed (offline?).")
                is SessionStatus.NotAuthenticated -> Text("Not signed in.")
            }
            ResultBox(message?.first, message?.second == true)
        }

        SectionCard("Credentials", "Used by every method below") {
            Field(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Field(password, { password = it }, "Password", visual = PasswordVisualTransformation())
        }

        SectionCard("Email + password") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyButton("Sign in", busy == "Sign in", { vm.signIn(email.trim(), password) }, enabled = email.isNotBlank() && password.isNotBlank())
                BusyButton("Sign up", busy == "Sign up", { vm.signUp(email.trim(), password) }, enabled = email.isNotBlank() && password.length >= 6, tonal = true)
            }
            BusyButton("Forgot password – send reset email", busy == "Password reset email", { vm.resetPassword(email.trim()) }, enabled = email.isNotBlank(), tonal = true)
        }

        SectionCard("Passwordless", "Magic link / one-time code") {
            BusyButton("Send magic link", busy == "Send magic link / OTP", { vm.magicLink(email.trim()) }, enabled = email.isNotBlank())
            Field(code, { code = it }, "6-digit code from email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            BusyButton("Verify code", busy == "Verify email code", { vm.verifyOtp(email.trim(), code.trim()) }, enabled = email.isNotBlank() && code.isNotBlank(), tonal = true)
        }

        SectionCard("Other sign-in methods") {
            BusyButton("Continue anonymously", busy == "Anonymous sign-in", vm::anonymous, tonal = true)
            BusyButton("Continue with GitHub (OAuth)", busy == "GitHub OAuth", vm::github, tonal = true)
            Text(
                "Anonymous sign-ins and the GitHub provider must be enabled in the Supabase dashboard (Authentication → Sign In / Providers). Add supabasemoon://auth to Redirect URLs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (status is SessionStatus.Authenticated) {
            SectionCard("Update user", "Requires a signed-in session") {
                Field(newPw, { newPw = it }, "New password", visual = PasswordVisualTransformation())
                BusyButton("Change password", busy == "Update password", { vm.updatePassword(newPw) }, enabled = newPw.length >= 6, tonal = true)
                Field(displayName, { displayName = it }, "Display name (user metadata)")
                BusyButton("Save metadata", busy == "Update user metadata", { vm.updateMetadata(displayName) }, enabled = displayName.isNotBlank(), tonal = true)
            }
        }
    }
}
