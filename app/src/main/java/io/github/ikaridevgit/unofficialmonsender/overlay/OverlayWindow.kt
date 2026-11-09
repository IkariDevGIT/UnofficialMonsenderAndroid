package io.github.ikaridevgit.unofficialmonsender.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.ikaridevgit.unofficialmonsender.appContainer
import io.github.ikaridevgit.unofficialmonsender.detection.Delivery
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonsenderTheme

/**
 * Hosts [SendOverlay] in a TYPE_ACCESSIBILITY_OVERLAY window, which an enabled accessibility
 * service may draw over any app without the "display over other apps" permission.
 */
class OverlayWindow(private val service: AccessibilityService) {
    private class Attached(val root: FrameLayout, val owner: OverlayOwner)

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var attached: Attached? = null

    val isShowing: Boolean get() = attached != null

    fun show(
        request: OverlayRequest,
        options: OverlayOptions,
        onDelivery: (Delivery) -> Unit,
        onClosed: () -> Unit,
    ) {
        dismiss()
        val owner = OverlayOwner()
        val root = BackKeyLayout(service) { owner.onBackPressedDispatcher.onBackPressed() }
        val compose = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MonsenderTheme {
                    SendOverlay(
                        request = request,
                        options = options,
                        send = {
                            onDelivery(Delivery.Sending)
                            service.appContainer.monLoader.send(request.url).also { onDelivery(Delivery.of(it)) }
                        },
                        onClosed = {
                            onClosed()
                            if (attached?.root === root) dismiss()
                        },
                    )
                }
            }
        }
        root.addView(compose, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeOnBackPressedDispatcherOwner(owner)
        owner.start()
        try {
            windowManager.addView(root, layoutParams())
            attached = Attached(root, owner)
        } catch (_: RuntimeException) {
            owner.destroy()
        }
    }

    fun dismiss() {
        val current = attached ?: return
        attached = null
        runCatching { windowManager.removeViewImmediate(current.root) }
        current.owner.destroy()
    }

    @Suppress("DEPRECATION")
    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            (if (Build.VERSION.SDK_INT < 30) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0),
        PixelFormat.TRANSLUCENT,
    ).apply {
        title = "MonsenderOverlay"
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
    }

    private class BackKeyLayout(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
}

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
