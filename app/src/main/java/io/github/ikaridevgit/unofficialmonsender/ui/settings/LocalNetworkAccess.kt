package io.github.ikaridevgit.unofficialmonsender.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Android 17 blocks apps from the local network until they're allowed in, which is where
 * MonLoader usually lives. Older versions let every app through.
 */
@Stable
class LocalNetworkAccess internal constructor(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set
    internal var launch: (() -> Unit) -> Unit = {}

    /** Asks for access if it's missing, then runs [then] whatever the answer. */
    fun request(then: () -> Unit = {}) = if (granted) then() else launch(then)
}

@Composable
fun rememberLocalNetworkAccess(): LocalNetworkAccess {
    val context = LocalContext.current
    val access = remember { LocalNetworkAccess(context.hasLocalNetworkAccess()) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askedAt by remember { mutableLongStateOf(0L) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        access.granted = allowed
        if (!allowed && SystemClock.elapsedRealtime() - askedAt < NO_PROMPT_MS) context.openAppInfo()
        pending?.invoke()
        pending = null
    }
    access.launch = { then ->
        pending = then
        askedAt = SystemClock.elapsedRealtime()
        launcher.launch(PERMISSION)
    }

    LifecycleResumeEffect(Unit) {
        access.granted = context.hasLocalNetworkAccess()
        onPauseOrDispose { }
    }
    return access
}

private fun Context.hasLocalNetworkAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
        ContextCompat.checkSelfPermission(this, PERMISSION) == PackageManager.PERMISSION_GRANTED

private const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** An answer this fast means Android didn't show the prompt, so the user has to allow it in App info. */
private const val NO_PROMPT_MS = 400L
