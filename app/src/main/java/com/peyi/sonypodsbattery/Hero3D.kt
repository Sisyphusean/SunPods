package com.peyi.sonypodsbattery

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import kotlin.math.abs

/**
 * The Home hero as live 3D: the approved Blender earbuds and case, rendered by three.js from
 * app/src/main/assets/hero3d (offline, served through [WebViewAssetLoader], no network).
 *
 * @param color the earbuds' finish: "black", "silver" (Platinum Silver) or "pink" (Smoky Pink).
 *   [hero3dColorFor] maps the colour code the earbuds report. Changes crossfade.
 * @param model "buds" (the hero pose) or "case" (the charging case, earbuds inside).
 * @param lid case lid, 0 closed to 1 open; animates when it changes.
 * @param onTap a tap on the hero (the page tells taps from spins).
 *
 * Horizontal drags spin the earbuds (fling, then they ease home 2.5 s after the finger lifts,
 * with a haptic tick); vertical drags are left to the scrolling parent.
 */
@Composable
internal fun Hero3D(
    color: String,
    modifier: Modifier = Modifier,
    model: String = "buds",
    lid: Float = 0f,
    onTap: (() -> Unit)? = null,
) {
    val tap by rememberUpdatedState(onTap)
    val state = remember { Hero3DState() }
    state.color = color
    state.model = model
    state.lid = lid.coerceIn(0f, 1f)

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> state.view?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // push changes after every recomposition (each is a no-op unless a value changed)
    SideEffect { state.sync() }

    AndroidView(
        modifier = modifier,
        factory = { context -> createHeroWebView(context, state) { tap?.invoke() } },
        onRelease = { view ->
            state.view = null
            view.stopLoading()
            view.removeJavascriptInterface("SunPodsHost")
            view.destroy()
        },
    )
}

/** Sony's colour codes from the earbuds: 0x01 Black, 0x03 Platinum Silver, 0x06 Smoky Pink. */
internal fun hero3dColorFor(code: Int): String = when (code) {
    0x03 -> "silver"
    0x06 -> "pink"
    else -> "black"
}

private const val HERO3D_HOST = "appassets.androidplatform.net"
private const val HERO3D_PAGE = "https://$HERO3D_HOST/assets/hero3d/index.html"

/** What the page should show, and what it was last told, so only changes cross the bridge. */
private class Hero3DState {
    var view: WebView? = null
    var ready = false
    var color = "black"
    var model = "buds"
    var lid = 0f
    private var sentColor: String? = null
    private var sentModel: String? = null
    private var sentLid: Float? = null

    fun markLoaded(color: String, model: String, lid: Float) {
        sentColor = color; sentModel = model; sentLid = lid
    }

    fun sync() {
        val v = view ?: return
        if (!ready) return
        if (color != sentColor) {
            sentColor = color
            v.evaluateJavascript("window.sunpods&&window.sunpods.setColor(${js(color)})", null)
        }
        if (model != sentModel) {
            sentModel = model
            v.evaluateJavascript("window.sunpods&&window.sunpods.setModel(${js(model)})", null)
        }
        if (lid != sentLid) {
            sentLid = lid
            v.evaluateJavascript("window.sunpods&&window.sunpods.setLid($lid)", null)
        }
    }

    // only short, known words are sent, but quote them safely anyway
    private fun js(s: String) = "'" + s.filter { it.isLetterOrDigit() } + "'"
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createHeroWebView(context: Context, state: Hero3DState, onTap: () -> Unit): WebView {
    val assets = WebViewAssetLoader.Builder()
        .setDomain(HERO3D_HOST)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    val web = WebView(context)
    web.setBackgroundColor(Color.TRANSPARENT)
    web.overScrollMode = View.OVER_SCROLL_NEVER
    web.isVerticalScrollBarEnabled = false
    web.isHorizontalScrollBarEnabled = false
    web.isHapticFeedbackEnabled = true
    // decorative: the Compose parent carries the hero's semantics
    web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    with(web.settings) {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
    }
    web.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
            assets.shouldInterceptRequest(request.url)?.also { it.mimeType = mimeFor(request.url) ?: it.mimeType }
                ?: blocked()   // offline only: nothing outside the bundled assets loads

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
    }
    web.addJavascriptInterface(object {
        @JavascriptInterface
        fun ready() = web.post {
            state.ready = true
            state.sync()
        }

        @JavascriptInterface
        fun settled() = web.post { web.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE) }

        @JavascriptInterface
        fun tap() = web.post { onTap() }
    }, "SunPodsHost")

    // Once a drag is clearly sideways, keep the parents (page scroll, back swipe) out of it.
    // Vertical drags are never claimed, so the page still scrolls over the hero.
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var claimed = false
    web.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; claimed = false }
            MotionEvent.ACTION_MOVE -> if (!claimed) {
                val dx = abs(e.x - downX)
                val dy = abs(e.y - downY)
                if (dx > slop && dx > dy) {
                    claimed = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        false
    }

    state.view = web
    state.ready = false
    state.markLoaded(state.color, state.model, state.lid)
    web.loadUrl(
        Uri.parse(HERO3D_PAGE).buildUpon()
            .appendQueryParameter("color", state.color)
            .appendQueryParameter("model", state.model)
            .appendQueryParameter("lid", state.lid.toString())
            .build().toString()
    )
    return web
}

private fun mimeFor(url: Uri): String? = when (url.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
    "js", "mjs" -> "text/javascript"   // module scripts need a JavaScript type
    "wasm" -> "application/wasm"
    "glb" -> "model/gltf-binary"
    "html" -> "text/html"
    "png" -> "image/png"
    else -> null
}

private fun blocked() = WebResourceResponse(
    "text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))
)
