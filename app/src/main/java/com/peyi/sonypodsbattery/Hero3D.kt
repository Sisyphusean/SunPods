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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
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
 * @param onTap a tap on the hero (the page tells taps from spins).
 * @param device which earbuds/headphones: "xm5" (WF-1000XM5), "c710n" (WF-C710N), or "wh1000xm5" (WH-1000XM5); each has its own finishes.
 * @param cycleDevices when given (at least two device ids), the hero steps through them in the one
 *   WebView, every [CYCLE_MS] with a soft fade, each in [color]; the next is preloaded. The timer starts
 *   once the first has drawn. Not with reduced motion (animator duration scale 0): the first one stays.
 *
 * @param centered shift the picture so the shown model sits in the middle of the view (the device picker and Welcome); Home keeps the hero framing.
 * @param softSwap a change of [device] fades across (the device picker); not with reduced motion.
 * @param preload devices to warm up in the background (all of them, for the device picker), so no swap lands on an empty hero.
 * @param caseProgress continuous pack/unpack state (0 = hero pose, 1 = closed carry case).
 * @param animateEntrance when true with [caseProgress] == 0 and motion enabled, starts closed at caseProgress=1 and calls playUnpack().
 * @param onEntrancePlayed invoked once the entrance unpack is triggered (or skipped due to reduced motion).
 * @param onUnpacked invoked when playUnpack() settles at caseProgress == 0.
 * @param onPacked invoked when playPack() settles at caseProgress == 1.
 *
 * Horizontal drags spin the earbuds (fling, then they ease home 2.5 s after the finger lifts,
 * with a haptic tick); vertical drags are left to the scrolling parent.
 */
@Composable
internal fun Hero3D(
    color: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    device: String = "xm5",
    cycleDevices: List<String>? = null,
    softSwap: Boolean = false,
    centered: Boolean = false,
    preload: List<String> = emptyList(),
    caseProgress: Float = 0f,
    animateEntrance: Boolean = false,
    onEntrancePlayed: (() -> Unit)? = null,
    onUnpacked: (() -> Unit)? = null,
    onPacked: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cycle = cycleDevices?.takeIf { it.size > 1 }
    val reduceMotion = rememberReduceMotion()
    val cycling = cycle != null && !reduceMotion
    var step by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val shownDevice = if (cycle != null) cycle[step % cycle.size] else device
    val nextDevice = if (cycling) listOf(cycle!![(step + 1) % cycle.size]) else preload
    val targetProgress = caseProgress.coerceIn(0f, 1f)
    val wantEntrance = animateEntrance && targetProgress == 0f && !reduceMotion
    LaunchedEffect(animateEntrance, reduceMotion) {
        if (animateEntrance && reduceMotion) onEntrancePlayed?.invoke()
    }
    // One hero view for the whole screen, kept alive while you move between tabs and pages, so
    // coming back to Home doesn't reload the 3D scene (which showed as the earbuds vanishing).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val state = remember(lifecycle) { HeroCache.stateFor(lifecycle) }
    // While one page slides out and the next slides in, both hold a Hero3D. Only the newest owns the one
    // view: the other leaves it alone, so the two never fight over its size, device or position.
    val token = remember { Any().also { HeroCache.driver = it } }
    val isOwner = HeroCache.driver === token
    androidx.compose.runtime.DisposableEffect(token) { onDispose { if (HeroCache.driver === token) HeroCache.driver = null } }
    var prevWantEntrance by remember { mutableStateOf(false) }

    // Push state mutations and bridge updates strictly in SideEffect (never during composition).
    SideEffect {
        if (isOwner) {
            HeroCache.onTap = onTap
            HeroCache.onEntrancePlayed = onEntrancePlayed
            HeroCache.onUnpacked = onUnpacked
            HeroCache.onPacked = onPacked
            val hostChanged = state.onHost(token)
            if (hostChanged && state.ready && !reduceMotion && state.view != null) {
                state.snapped = false
            }
            if (wantEntrance && !prevWantEntrance) {
                state.armEntrance()
            } else if (!wantEntrance && state.pendingUnpack) {
                state.cancelEntrance()
            }
            prevWantEntrance = wantEntrance
            state.device = shownDevice
            state.soft = cycling || (softSwap && !reduceMotion)
            state.centered = centered
            state.preload = nextDevice
            state.color = color
            state.caseProgress = targetProgress
            state.reduceMotion = reduceMotion
            state.sync(forceSnap = hostChanged)
        }
    }

    // Until the 3D page has drawn, show the picture it left last time (same size, same pose), then
    // fade across. Opening the app shows the earbuds at once instead of them popping in.
    // Suppress the standalone-buds placeholder while starting closed for the unpack entrance.
    val suppressPlaceholder = wantEntrance || state.pendingUnpack || targetProgress > 0.5f
    val snapKey = if (centered) "centered" else "buds"
    val placeholder = if (state.drawn || suppressPlaceholder) null else remember(shownDevice, color, snapKey) { HeroSnapshots.load(context, shownDevice, color, snapKey) }
    if (cycling && isOwner) LaunchedEffect(state.drawn, state.snapped) {
        if (!state.drawn || !state.snapped) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(CYCLE_MS); step++ }
    }
    val cover by androidx.compose.animation.core.animateFloatAsState(
        if (state.drawn) 0f else 1f, androidx.compose.animation.core.tween(240), label = "heroCover"
    )
    var revealed by remember { mutableStateOf(reduceMotion || (placeholder != null && state.snapped)) }
    LaunchedEffect(state.drawn, state.snapped, isOwner, placeholder, reduceMotion) {
        if (reduceMotion) {
            revealed = true
        } else if (placeholder != null && state.drawn) {
            revealed = true
        } else if (state.drawn && state.snapped && isOwner) {
            withFrameNanos { }
            revealed = true
        } else {
            revealed = false
        }
    }
    val webAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (revealed) 1f else 0f,
        if (reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(260),
        label = "heroWebAlpha"
    )
    // Creating the WebView takes a few hundred ms on the main thread, so a cold start puts the
    // first frame (with the picture above) on screen first and creates the 3D view right after.
    var mountWeb by remember { mutableStateOf(state.view != null) }
    if (!mountWeb) LaunchedEffect(Unit) { withFrameNanos { }; mountWeb = true }
    androidx.compose.foundation.layout.Box(modifier) {
        if (mountWeb && isOwner) AndroidView(
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = webAlpha },
            factory = { ctx ->
                state.hosts++
                state.view?.also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                    ?: createHeroWebView(ctx, state) { HeroCache.onTap?.invoke() }
            },
            // Not destroyed here: the next Home screen picks the same view up again.
            // A page change can build the next screen's hero before this one is released; only the last host lets go.
            onRelease = { view -> if (--state.hosts <= 0) (view.parent as? android.view.ViewGroup)?.removeView(view) },
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
    private fun prefix(device: String, color: String, model: String) = if (device == "xm5") "${color}_${model}_" else "${device}_${color}_${model}_"

    private fun file(context: Context, device: String, color: String, model: String) =
        java.io.File(java.io.File(context.filesDir, "hero3d"), "${prefix(device, color, model)}${BuildConfig.VERSION_CODE}.png")

    fun load(context: Context, device: String, color: String, model: String): androidx.compose.ui.graphics.ImageBitmap? {
        val f = file(context, device, color, model)
        if (f.exists()) return runCatching { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        // First launch ever: a pre-rendered hero frame in the same pose (black WF-1000XM5s, and every
        // WF-C710N colour), so the earbuds show while the 3D page compiles.
        val first = when {
            model != "buds" -> null
            device == "xm5" && color == "black" -> "turntable/f000.webp"
            device != "xm5" -> "turntable/${device}_$color.webp"   // every other model's first-launch picture
            else -> null
        } ?: return null
        return runCatching {
            context.assets.open(first).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }

    fun save(context: Context, device: String, color: String, model: String, dataUrl: String) {
        val b64 = dataUrl.substringAfter("base64,", "").ifEmpty { return }
        val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }.getOrNull() ?: return
        val f = file(context, device, color, model)
        f.parentFile?.listFiles()?.filter { it.name.startsWith(prefix(device, color, model)) && it != f }?.forEach { it.delete() }
        f.parentFile?.mkdirs()
        runCatching { f.writeBytes(bytes) }
    }

    fun has(context: Context, device: String, color: String, model: String) = file(context, device, color, model).exists()
}

/** Holds the hero's view and state for one screen (activity) lifetime. */
private object HeroCache {
    private var owner: Lifecycle? = null
    private var state: Hero3DState? = null
    var onTap: (() -> Unit)? = null
    var onEntrancePlayed: (() -> Unit)? = null
    var onUnpacked: (() -> Unit)? = null
    var onPacked: (() -> Unit)? = null
    /** The Hero3D that currently drives the view (the newest one composed). */
    var driver by androidx.compose.runtime.mutableStateOf<Any?>(null)

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
        onEntrancePlayed = null
        onUnpacked = null
        onPacked = null
    }
}


/** The Welcome page cycle: time on each model, and the fade out and in of a swap. */
private const val CYCLE_MS = 4000L
private const val SOFT_FADE_MS = 320

private const val HERO3D_HOST = "appassets.androidplatform.net"
private const val HERO3D_PAGE = "https://$HERO3D_HOST/assets/hero3d/index.html"

/** What the page should show, and what it was last told, so only changes cross the bridge. */
private class Hero3DState {
    var view: WebView? = null
    /** How many on-screen hosts currently hold the view. */
    var hosts = 0
    var ready = false
    /** The live view has drawn its first frame; the placeholder can go. */
    var drawn by androidx.compose.runtime.mutableStateOf(false)
    /** The live view is synced and painted for the current screen host (gates fade-in across screen changes). */
    var snapped by androidx.compose.runtime.mutableStateOf(false)
    var awaitingSnap = false
    var device = "xm5"
    var color = "black"
    var caseProgress = 0f
    var pendingUnpack = false
    private var unpackScheduled = false
    private var unpackRunnable: Runnable? = null
    private var lastHost: Any? = null
    var reduceMotion = false
    /** Slow fade for device swaps (the Welcome cycle), and the device to warm up next. */
    var soft = false
    var centered = false
    /** The WebView's own layout size in dp, pushed to the page so its canvas always matches it. */
    var wDp = 0f
    var hDp = 0f
    private var sentW = 0f
    private var sentH = 0f
    private var sentCentered = false
    var preload: List<String> = emptyList()
    private val sentPreloads = mutableSetOf<String>()
    private var sentDevice: String? = null
    private var sentColor: String? = null
    private var sentCaseProgress: Float? = null

    fun onHost(hostToken: Any): Boolean {
        if (lastHost === hostToken) return false
        lastHost = hostToken
        return true
    }

    fun onSnapped() {
        awaitingSnap = false
        snapped = true
        scheduleEntranceIfReady()
    }

    fun armEntrance() {
        unpackRunnable?.let { view?.removeCallbacks(it) }
        unpackRunnable = null
        pendingUnpack = true
        unpackScheduled = false
        sentCaseProgress = null
    }

    fun cancelEntrance() {
        unpackRunnable?.let { view?.removeCallbacks(it) }
        unpackRunnable = null
        pendingUnpack = false
        unpackScheduled = false
    }

    private fun scheduleEntranceIfReady() {
        val v = view ?: return
        if (!pendingUnpack || unpackScheduled || !ready || !drawn || !snapped) return
        unpackScheduled = true
        val r = Runnable {
            unpackRunnable = null
            unpackScheduled = false
            if (hosts > 0 && pendingUnpack && ready && snapped && view === v && caseProgress == 0f) {
                pendingUnpack = false
                sentCaseProgress = 0f
                v.evaluateJavascript("window.sunpods&&window.sunpods.playUnpack&&window.sunpods.playUnpack()", null)
                HeroCache.onEntrancePlayed?.invoke()
            }
        }
        unpackRunnable = r
        // Fade-in (~240ms) plus a brief visual settle before the case opens and unpacks.
        v.postDelayed(r, 480L)
    }

    fun markLoaded(device: String, color: String, initialCaseProgress: Float) {
        sentDevice = device; sentColor = color
        sentCaseProgress = initialCaseProgress
        sentCentered = centered; sentW = 0f; sentH = 0f
    }

    fun sync(forceSnap: Boolean = false) {
        val v = view ?: return
        if (!ready) return
        if (wDp > 0f && hDp > 0f && (wDp != sentW || hDp != sentH)) {
            sentW = wDp; sentH = hDp
            v.evaluateJavascript("window.sunpods&&window.sunpods.setSize&&window.sunpods.setSize($wDp, $hDp)", null)
        }
        val screenSwap = forceSnap || centered != sentCentered
        if (screenSwap) {
            val targetSnap = if (pendingUnpack) 1f else caseProgress
            sentCentered = centered
            sentDevice = device
            sentColor = color
            sentCaseProgress = targetSnap
            snapped = false
            awaitingSnap = true
            for (p in preload) if (sentPreloads.add(p)) {
                v.evaluateJavascript("window.sunpods&&window.sunpods.preload&&window.sunpods.preload(${js(p)})", null)
            }
            v.evaluateJavascript(
                "window.sunpods&&window.sunpods.snapTo&&window.sunpods.snapTo(${js(device)}, ${js(color)}, $targetSnap, $centered)",
                null
            )
            return
        }
        // Within the same screen: crossfade device swaps and animate pack/unpack transitions smoothly.
        if (device != sentDevice) {
            sentDevice = device
            sentColor = null
            v.evaluateJavascript("window.sunpods&&window.sunpods.setDevice&&window.sunpods.setDevice(${js(device)}${if (soft) ", $SOFT_FADE_MS" else ""})", null)
        }
        for (p in preload) if (sentPreloads.add(p)) {
            v.evaluateJavascript("window.sunpods&&window.sunpods.preload&&window.sunpods.preload(${js(p)})", null)
        }
        if (color != sentColor) {
            sentColor = color
            v.evaluateJavascript("window.sunpods&&window.sunpods.setColor(${js(color)})", null)
        }
        if (pendingUnpack) {
            if (sentCaseProgress != 1f) {
                sentCaseProgress = 1f
                v.evaluateJavascript("window.sunpods&&window.sunpods.setCaseProgress&&window.sunpods.setCaseProgress(1, true)", null)
            }
            scheduleEntranceIfReady()
        } else if (caseProgress != sentCaseProgress) {
            val prev = sentCaseProgress
            sentCaseProgress = caseProgress
            when {
                !reduceMotion && prev != null && prev > 0.5f && caseProgress == 0f ->
                    v.evaluateJavascript("window.sunpods&&window.sunpods.playUnpack&&window.sunpods.playUnpack()", null)
                !reduceMotion && prev != null && prev < 0.5f && caseProgress == 1f ->
                    v.evaluateJavascript("window.sunpods&&window.sunpods.playPack&&window.sunpods.playPack()", null)
                else ->
                    v.evaluateJavascript("window.sunpods&&window.sunpods.setCaseProgress&&window.sunpods.setCaseProgress($caseProgress, true)", null)
            }
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
    StartupLog.mark("hero webview create")
    val web = WebView(context)
    StartupLog.mark("hero webview created")
    web.setBackgroundColor(Color.TRANSPARENT)
    web.overScrollMode = View.OVER_SCROLL_NEVER
    web.isVerticalScrollBarEnabled = false
    web.isHorizontalScrollBarEnabled = false
    web.isHapticFeedbackEnabled = true
    web.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
        val d = v.resources.displayMetrics.density
        state.wDp = (r - l) / d; state.hDp = (b - t) / d
        state.sync()
    }
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
    fun scheduleSnapshot(key: String, delayMs: Long) {
        val d = state.device; val c = state.color
        val poseReady = state.centered || (!state.pendingUnpack && state.caseProgress == 0f)
        if (!poseReady || HeroSnapshots.has(context, d, c, key)) return
        web.postDelayed({
            val stillReadyPose = state.centered || (!state.pendingUnpack && state.caseProgress == 0f)
            if (stillReadyPose && state.device == d && state.color == c) {
                web.evaluateJavascript("window.sunpods&&window.sunpods.snapshot()") { r ->
                    val url = r?.trim('"')?.takeIf { it.startsWith("data:image/png") } ?: return@evaluateJavascript
                    Thread { HeroSnapshots.save(context.applicationContext, d, c, key, url) }.start()
                }
            }
        }, delayMs)
    }
    web.addJavascriptInterface(object {
        @JavascriptInterface
        fun ready() = web.post {
            StartupLog.mark("hero 3d ready")
            state.ready = true
            state.sync()
            // One frame for the GPU to put it on screen, then drop the placeholder and start delayed entrance if pending.
            web.postDelayed({ state.drawn = true; if (!state.awaitingSnap) state.onSnapped() }, 120)
            // Keep this exact picture for next launch (once per colour, pose and app version),
            // only when settled in the hero pose (never mid-unpack or closed case).
            scheduleSnapshot(if (state.centered) "centered" else "buds", 900)
        }

        @JavascriptInterface
        fun snapped() = web.post { state.onSnapped() }

        @JavascriptInterface
        fun settled() = web.post { web.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK) }

        @JavascriptInterface
        fun tap() = web.post { onTap() }

        @JavascriptInterface
        fun unpacked() = web.post {
            HeroCache.onUnpacked?.invoke()
            if (!state.centered) scheduleSnapshot("buds", 150)
        }

        @JavascriptInterface
        fun packed() = web.post { HeroCache.onPacked?.invoke() }
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
    val initialCaseProgress = if (state.pendingUnpack) 1f else state.caseProgress
    state.markLoaded(state.device, state.color, initialCaseProgress)
    web.loadUrl(
        Uri.parse(HERO3D_PAGE).buildUpon()
            .appendQueryParameter("device", state.device)
            .appendQueryParameter("color", state.color)
            .appendQueryParameter("centered", if (state.centered) "1" else "0")
            .appendQueryParameter("caseProgress", initialCaseProgress.toString())
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
