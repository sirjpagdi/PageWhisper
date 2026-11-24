package com.pagewhisper.app.ui

import android.Manifest
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.BuildConfig
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.playback.Playback
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil

private const val HOLD_MS = 1100L

@Composable
fun AppRoot(vm: AppViewModel) {
    val c = LocalPw.current
    val view = LocalView.current
    val window = (LocalContext.current as ComponentActivity).window
    val tab by vm.tab.collectAsStateWithLifecycle()
    val fs by vm.fullscreen.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val settings by Repo.settings.collectAsStateWithLifecycle()
    val readerList = rememberLazyListState()

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }

    // Fullscreen hides the system bars; swiping from an edge shows them for a moment.
    DisposableEffect(fs) {
        val ic = WindowCompat.getInsetsController(window, view)
        if (fs) {
            ic.hide(WindowInsetsCompat.Type.systemBars())
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else ic.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    // Real screen brightness (the web version can only dim). Back to the system level outside fullscreen.
    LaunchedEffect(fs, settings.brightness) {
        val lp = window.attributes
        lp.screenBrightness = if (fs) settings.brightness / 100f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }
    // Keep the screen on when asked, or while the touch lock is on.
    val keepOn = settings.awake || (locked && settings.lockAwake)
    LaunchedEffect(keepOn) {
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    LaunchedEffect(c.light) {
        val ic = WindowCompat.getInsetsController(window, view)
        ic.isAppearanceLightStatusBars = c.light
        ic.isAppearanceLightNavigationBars = c.light
    }
    BackHandler(enabled = locked) { } // the back button does nothing while locked
    BackHandler(enabled = fs && !locked) { vm.exitFullscreen() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().then(if (fs) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))) {
            if (!fs) TopBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.READ -> ReaderScreen(vm, readerList)
                    Tab.OPEN -> OpenScreen(vm)
                    Tab.LIBRARY -> LibraryScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
            PlayerBar(vm)
            if (!fs) NavBar(tab) { vm.tab.value = it }
        }
        Sheets(vm)
        BrowserFetchHost(vm)
        if (locked) LockOverlay(onUnlock = { vm.unlock() })
        ToastHost(vm)
    }
}

@Composable
private fun TopBar() {
    val c = LocalPw.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString { append("Page"); withStyle(SpanStyle(color = c.accent2)) { append("Whisper") } },
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.weight(1f),
        )
        Text("v" + BuildConfig.VERSION_NAME, fontSize = 11.sp, color = c.muted)
    }
}

@Composable
private fun NavBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalPw.current
    NavigationBar(containerColor = c.panel, contentColor = c.text) {
        val items = listOf(Tab.READ to ("Read" to "book"), Tab.OPEN to ("Open" to "tab-open"), Tab.LIBRARY to ("Library" to "tab-library"), Tab.SETTINGS to ("Settings" to "tab-settings"))
        for ((t, p) in items) {
            NavigationBarItem(
                selected = tab == t, onClick = { onSelect(t) },
                icon = { PwIcon(p.second) }, label = { Text(p.first, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.accent2, selectedTextColor = c.accent2, unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                    indicatorColor = c.accent.copy(alpha = 0.18f),
                ),
            )
        }
    }
}

@Composable
private fun PlayerButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, text: String = "") {
    val c = LocalPw.current
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp))
            .background(if (primary || text.isNotEmpty()) c.accent else c.panel2)
            .alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (primary || text.isNotEmpty()) accentContentColor(c.accent) else c.text
        PwIcon(icon, tint = contentColor)
        if (text.isNotEmpty()) Text(text, Modifier.padding(start = 4.dp), fontSize = 12.sp, color = contentColor)
    }
}

@Composable
fun PlayerBar(vm: AppViewModel) {
    val c = LocalPw.current
    val ui by Playback.ui.collectAsStateWithLifecycle()
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    val has = chapter != null
    val sleepText = when (ui.sleep.mode) {
        "chapter" -> "End"
        "time" -> ui.sleep.remainingMs.let { r -> val s = ceil(r / 1000.0).toLong(); if (s >= 60) "${ceil(s / 60.0).toLong()}m" else "${s}s" }
        else -> ""
    }
    Column(Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
        LinearProgressIndicator(
            progress = { if (ui.total > 0) (ui.index + 1f) / ui.total else 0f },
            modifier = Modifier.fillMaxWidth().height3(), color = c.accent, trackColor = c.line,
        )
        Text(
            ui.status, Modifier.fillMaxWidth().padding(vertical = 6.dp), fontSize = 12.sp, maxLines = 1,
            color = if (ui.error) c.red else c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerButton("prev", "Previous section", { Playback.skip(-1) }, Modifier.weight(1f), has)
            PlayerButton(if (ui.playing && !ui.paused) "pause" else "play", "Play or pause", { Playback.toggle() }, Modifier.weight(1.6f), has, primary = true)
            PlayerButton("next", "Next section", { Playback.skip(1) }, Modifier.weight(1f), has)
            PlayerButton("stop", "Stop", { Playback.stop() }, Modifier.weight(1f), ui.playing)
            PlayerButton("moon", "Sleep timer", { vm.openSheet(Sheet.Sleep) }, Modifier.weight(1.35f), true, text = sleepText)
        }
    }
}

private fun Modifier.height3() = this.then(Modifier.heightIn(min = 3.dp, max = 3.dp))

/** Swallows every touch until the lock button is held for about a second. */
@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    val c = LocalPw.current
    var awake by remember { mutableStateOf(true) }
    var holding by remember { mutableStateOf(false) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(awake) { if (awake) { delay(3500); awake = false } }
    LaunchedEffect(holding) { if (holding) ring.animateTo(1f, tween(HOLD_MS.toInt())) else ring.snapTo(0f) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture { // eat everything
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume(); awake = true
                waitForUpOrCancellation()
            }
        },
    ) {
        if (awake) {
            Text(
                "Screen locked. Press and hold the lock button to unlock.",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 200.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(12.dp)).background(c.panel2).padding(horizontal = 14.dp, vertical = 9.dp),
                fontSize = 13.sp, color = c.text,
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 130.dp).size(68.dp).alpha(if (awake || holding) 1f else 0.4f)
                .clip(CircleShape).background(c.panel).border(1.dp, c.line, CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume(); awake = true; holding = true
                        val up = withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation() }
                        holding = false
                        if (up == null) onUnlock() // held for the whole time
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                val w = 4.dp.toPx()
                drawCircle(c.line, style = Stroke(w))
                drawArc(c.accent, -90f, 360f * ring.value, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            PwIcon("lock", tint = c.text, size = 28.dp)
        }
    }
}

@Composable
private fun ToastHost(vm: AppViewModel) {
    val c = LocalPw.current
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.toasts.collect { m -> msg = m; delay(4000); if (msg == m) msg = null } }
    msg?.let {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                it, Modifier.padding(bottom = 170.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(12.dp)).background(c.panel2)
                    .border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 13.sp, color = c.text,
            )
        }
    }
}
