package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.BuildConfig
import com.devsecops.tracker.Supa
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// This app has exactly one user (you). You type the password once on this
// device; supabase-kt keeps the session on-device and refreshes it. The
// password is NOT compiled into the app (it used to come from local.properties
// via BuildConfig, which put it inside the APK). Only the email is prefilled.
// The account is never created from the app (no sign-up): RLS in Supabase
// still checks auth.uid() on every request.
private const val SPINNER_TIMEOUT_MS = 8_000L

private fun friendlyAuthError(t: Throwable): String {
    var cur: Throwable? = t
    val text = StringBuilder()
    while (cur != null) {
        text.append(cur.javaClass.simpleName).append(' ').append(cur.message ?: "").append(' ')
        cur = cur.cause
    }
    val m = text.toString().lowercase()
    return when {
        m.contains("unknownhost") || m.contains("unable to resolve") || m.contains("timeout") ||
            m.contains("timed out") || m.contains("connect") || m.contains("network") ||
            m.contains("ioexception") || m.contains("socket") ->
            "Couldn't reach the server. Check your internet connection and try again."
        m.contains("invalid") || m.contains("credentials") || m.contains("400") || m.contains("401") ||
            m.contains("unauthorized") ->
            "Sign-in was rejected. Check the email and password."
        else -> "Something went wrong while signing in. Please try again."
    }
}

@Composable
private fun LoginForm() {
    var email by remember { mutableStateOf(BuildConfig.APP_EMAIL) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || email.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                Supa.client.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
            }.onFailure { error = friendlyAuthError(it) }
            password = ""   // never keep it around once used
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("DevSecOps Tracker", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sign in once on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Button(
                onClick = { submit() },
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Signing in..." else "Sign in") }
        }
    }
}

@Composable
fun AuthGate() {
    val status by Supa.client.auth.sessionStatus.collectAsState()
    var timedOut by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Initializing / refresh failure: never spin forever, show Retry after ~8s.
    LaunchedEffect(status, attempt) {
        if (status !is SessionStatus.Authenticated && status !is SessionStatus.NotAuthenticated) {
            timedOut = false
            delay(SPINNER_TIMEOUT_MS)
            timedOut = true
        }
    }

    when (status) {
        is SessionStatus.Authenticated -> TrackerNavHost()
        is SessionStatus.NotAuthenticated -> LoginForm()
        else -> Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!timedOut) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        "Couldn't connect. Check your internet connection and try again.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        timedOut = false
                        attempt++
                        scope.launch { runCatching { Supa.client.auth.refreshCurrentSession() } }
                    }) { Text("Retry") }
                }
            }
        }
    }
}
