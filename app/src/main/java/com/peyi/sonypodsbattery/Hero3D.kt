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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
    // One hero view for the whole screen, kept alive while you move between tabs and pages, so
    // coming back to Home doesn't reload the 3D scene (which showed as the earbuds vanishing).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state = remember(lifecycle) { HeroCache.stateFor(lifecycle) }
    SideEffect { HeroCache.onTap = onTap }
    state.color = color
    state.model = model
    state.lid = lid.coerceIn(0f, 1f)

    // push changes after every recomposition (each is a no-op unless a value changed)
    SideEffect { state.sync() }

    // Until the 3D page has drawn, show the picture it left last time (same size, same pose), then
    // fade across. Opening the app shows the earbuds at once instead of them popping in.
    val context = androidx.compose.ui.platform.LocalContext.current
    val placeholder = if (state.drawn) null else remember(color, model) { HeroSnapshots.load(context, color, model) }
    val cover by androidx.compose.animation.core.animateFloatAsState(
        if (state.drawn) 0f else 1f, androidx.compose.animation.core.tween(240), label = "heroCover"
    )
    androidx.compose.foundation.layout.Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                state.view?.also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                    ?: createHeroWebView(ctx, state) { HeroCache.onTap?.invoke() }
            },
            // Not destroyed here: the next Home screen picks the same view up again.
            onRelease = { view -> (view.parent as? android.view.ViewGroup)?.removeView(view) },
        )
        if (placeholder != null && cover > 0f) androidx.compose.foundation.Image(
            placeholder, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = cover }
        )
    }
}

/** The hero's last frame per colour and model, kept in the app's files. */
private object HeroSnapshots {
    private fun file(context: Context, color: String, model: String) =
        java.io.File(java.io.File(context.filesDir, "hero3d"), "${color}_${model}_${BuildConfig.VERSION_CODE}.png")

    fun load(context: Context, color: String, model: String): androidx.compose.ui.graphics.ImageBitmap? {
        val f = file(context, color, model)
        if (f.exists()) return runCatching { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        // First launch ever: the pre-rendered hero frame is the same pose for black earbuds.
        if (color == "black" && model == "buds") return runCatching {
            context.assets.open("turntable/f000.webp").use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
        return null
    }

    fun save(context: Context, color: String, model: String, dataUrl: String) {
        val b64 = dataUrl.substringAfter("base64,", "").ifEmpty { return }
        val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }.getOrNull() ?: return
        val f = file(context, color, model)
        f.parentFile?.listFiles()?.filter { it.name.startsWith("${color}_${model}_") && it != f }?.forEach { it.delete() }
        f.parentFile?.mkdirs()
        runCatching { f.writeBytes(bytes) }
    }

    fun has(context: Context, color: String, model: String) = file(context, color, model).exists()
}

/** Holds the hero's view and state for one screen (activity) lifetime. */
private object HeroCache {
    private var owner: Lifecycle? = null
    private var state: Hero3DState? = null
    var onTap: (() -> Unit)? = null

    fun stateFor(lifecycle: Lifecycle): Hero3DState {
        if (owner !== lifecycle) {
            state?.view?.destroy()
            owner = lifecycle
            state = Hero3DState()
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> state?.view?.onResume()
                    Lifecycle.Event.ON_PAUSE -> state?.view?.onPause()
                    Lifecycle.Event.ON_DESTROY -> release(lifecycle)
                    else -> Unit
                }
            })
        }
        return state!!
    }

    fun release(lifecycle: Lifecycle) {
        if (owner !== lifecycle) return
        state?.view?.let { v -> (v.parent as? android.view.ViewGroup)?.removeView(v); v.stopLoading(); v.removeJavascriptInterface("SunPodsHost"); v.destroy() }
        state = null
        owner = null
        onTap = null
    }
}


private const val HERO3D_HOST = "appassets.androidplatform.net"
private const val HERO3D_PAGE = "https://$HERO3D_HOST/assets/hero3d/index.html"

/** What the page should show, and what it was last told, so only changes cross the bridge. */
private class Hero3DState {
    var view: WebView? = null
    var ready = false
    /** The live view has drawn its first frame; the placeholder can go. */
    var drawn by androidx.compose.runtime.mutableStateOf(false)
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
            // One frame for the GPU to put it on screen, then drop the placeholder.
            web.postDelayed({ state.drawn = true }, 120)
            // Keep this exact picture for next launch (once per colour, model and app version).
            val c = state.color; val m = state.model
            if (!HeroSnapshots.has(context, c, m)) web.postDelayed({
                if (state.color == c && state.model == m) web.evaluateJavascript("window.sunpods&&window.sunpods.snapshot()") { r ->
                    val url = r?.trim('"')?.takeIf { it.startsWith("data:image/png") } ?: return@evaluateJavascript
                    Thread { HeroSnapshots.save(context.applicationContext, c, m, url) }.start()
                }
            }, 900)
        }

        @JavascriptInterface
        fun settled() = web.post { web.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK) }

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
