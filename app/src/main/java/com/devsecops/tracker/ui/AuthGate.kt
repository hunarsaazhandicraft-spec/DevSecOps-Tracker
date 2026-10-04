package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.BuildConfig
import com.devsecops.tracker.Supa
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// This app has exactly one user (you). There is no login screen —
// the app signs itself in with a fixed account, then the session persists
// on-device. Credentials come from local.properties (git-ignored) at build
// time. The account is never created from the app (no sign-up): RLS in
// Supabase still checks auth.uid() on every request.
private val APP_ACCOUNT_EMAIL: String = BuildConfig.APP_EMAIL
private val APP_ACCOUNT_PASSWORD: String = BuildConfig.APP_PASSWORD

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
            "Sign-in was rejected. The app account details may be wrong — check the build settings."
        else -> "Something went wrong while signing in. Please try again."
    }
}

@Composable
fun AuthGate() {
    val status by Supa.client.auth.sessionStatus.collectAsState()
    var bootstrapError by remember { mutableStateOf<String?>(null) }
    var timedOut by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun bootstrap() {
        bootstrapError = null
        if (APP_ACCOUNT_PASSWORD.isBlank()) {
            bootstrapError = "Set app.password in local.properties and rebuild"
            return
        }
        scope.launch {
            runCatching {
                Supa.client.auth.signInWith(Email) {
                    email = APP_ACCOUNT_EMAIL
                    password = APP_ACCOUNT_PASSWORD
                }
            }.onFailure {
                bootstrapError = friendlyAuthError(it)
            }
        }
    }

    LaunchedEffect(status) {
        if (status is SessionStatus.NotAuthenticated) bootstrap()
    }

    // Never spin forever: after ~8s without a session, show the error + Retry.
    LaunchedEffect(status, attempt) {
        if (status !is SessionStatus.Authenticated) {
            timedOut = false
            delay(SPINNER_TIMEOUT_MS)
            timedOut = true
        }
    }

    when (status) {
        is SessionStatus.Authenticated -> TrackerNavHost()
        else -> Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bootstrapError == null && !timedOut) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        bootstrapError ?: "Couldn't connect. Check your internet connection and try again.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        timedOut = false
                        attempt++
                        bootstrap()
                    }) { Text("Retry") }
                }
            }
        }
    }
}
