package com.pagewhisper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.ui.AppRoot
import com.pagewhisper.app.ui.AppViewModel
import com.pagewhisper.app.ui.PageWhisperTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val reading by Repo.reading.collectAsStateWithLifecycle()
            PageWhisperTheme(reading.theme, reading.accent, reading.accentTone) { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Share to PageWhisper" from the browser: a chapter link, or selected chapter text. */
    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.handleShare(it) }
    }
}
