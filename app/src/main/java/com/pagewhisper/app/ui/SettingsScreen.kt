package com.pagewhisper.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pagewhisper.app.BuildConfig
import com.pagewhisper.app.data.Repo
import com.pagewhisper.app.model.ReadingStyle
import com.pagewhisper.app.playback.Playback
import com.pagewhisper.app.tts.Voices
import java.time.LocalDate

private val AMBIENT = listOf(
    Triple("off", "Off", "amb-off"), Triple("rain", "Rain", "amb-rain"), Triple("heavy", "Heavy rain", "amb-heavy"),
    Triple("storm", "Thunderstorm", "amb-storm"), Triple("ocean", "Ocean waves", "amb-ocean"), Triple("insects", "Night insects", "amb-insects"),
    Triple("forest", "Night forest", "amb-forest"), Triple("wind", "Wind", "amb-wind"), Triple("fire", "Campfire", "amb-fire"),
    Triple("brown", "Brown noise", "amb-brown"),
)

@Composable
fun ThemeSwatches(selected: String, onPick: (String) -> Unit) {
    val c = LocalPw.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (id in ReadingStyle.THEME_IDS) {
            val t = pwColors(id)
            Box(
                Modifier.width(62.dp).height(52.dp).clip(RoundedCornerShape(14.dp)).background(t.bg)
                    .border(if (id == selected) 3.dp else 1.dp, if (id == selected) c.accent else c.line, RoundedCornerShape(14.dp)).clickable { onPick(id) },
                contentAlignment = Alignment.Center,
            ) { Text("Aa", fontWeight = FontWeight.Bold, color = t.text) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalPw.current
    val reading by Repo.reading.collectAsStateWithLifecycle()
    val s by Repo.settings.collectAsStateWithLifecycle()
    val pron by Repo.pron.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val backup by vm.backupStatus.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportBackup(uri) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.readBackup(uri) }
    var voiceMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        PwCard("Reading style") {
            ThemeSwatches(reading.theme) { id -> Repo.updateReading { it.copy(theme = id) } }
            PrimaryButton("Fonts, spacing, alignment & more", { vm.openSheet(Sheet.Reading) }, Modifier.fillMaxWidth())
            Hint("Opens the style panel with a live preview. You can also reach it from the Aa button in the Read tab.")
        }

        PwCard("Voice") {
            Label("Voice")
            Box {
                SoftButton(Voices.labelOf(s.voice), { voiceMenu = true }, Modifier.fillMaxWidth())
                DropdownMenu(voiceMenu, { voiceMenu = false }) {
                    for (v in Voices.all) DropdownMenuItem(text = { Text(v.label) }, onClick = { vm.update { st -> st.copy(voice = v.id) }; voiceMenu = false })
                }
            }
            SliderRow("Speech speed", s.rate.toFloat(), 0.7f..1.4f, 13, "%.2f×".format(s.rate)) { v -> vm.update { it.copy(rate = ((v * 20).toInt() / 20.0)) } }
            Label("Chunk size")
            Segmented(listOf("160" to "Short", "280" to "Balanced", "380" to "Long"), s.chunk.toString()) { v ->
                vm.update { it.copy(chunk = v.toInt()) }; vm.rebuildForChunkChange()
            }
            Hint("Shorter chunks start sooner; longer chunks sound smoother and keep the phone cooler.")
        }

        PwCard("Voice model") {
            when {
                model.ready -> Text("Installed. Speech is made on this phone and works offline.", fontSize = 13.sp, color = c.green)
                model.downloading -> {
                    LinearProgressIndicator(progress = { model.progress }, Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
                    Text("Downloading… ${(model.progress * 100).toInt()}% (keep the app open)", fontSize = 12.sp, color = c.muted)
                }
                else -> {
                    PrimaryButton("Download the voice model (about 350 MB)", { vm.downloadModel() }, Modifier.fillMaxWidth())
                    StatusText(model.error, true)
                    Hint("Needed once. Use Wi-Fi. It is stored on this phone, so reading works offline afterwards.")
                }
            }
        }

        PwCard("Ambient sound") {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                for ((key, label, icon) in AMBIENT) {
                    val on = s.amb == key
                    Column(
                        Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.accent else c.panel2)
                            .border(1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp))
                            .clickable { vm.update { it.copy(amb = key) }; if (key != "off") Playback.previewAmbient() else Playback.syncAmbient() },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val contentColor = if (on) accentContentColor(c.accent) else c.text
                        PwIcon(icon, tint = contentColor, size = 26.dp)
                        Text(label, fontSize = 11.sp, color = contentColor, maxLines = 1)
                    }
                }
            }
            SliderRow("Ambient volume", s.ambVol.toFloat(), 0f..1f, 19, "${(s.ambVol * 100).toInt()}%") { v -> vm.update { it.copy(ambVol = ((v * 20).toInt() / 20.0)) } }
            CheckRow("Only play while narrating", s.ambOnly) { v -> vm.update { it.copy(ambOnly = v) } }
            Hint("Sounds are made on your phone (nothing to download) and loop seamlessly. Tap one to hear a short preview.")
        }

        PwCard("Playback") {
            CheckRow("Auto-play the next chapter when this one finishes", s.auto) { v -> vm.update { it.copy(auto = v) } }
            CheckRow("Keep screen on while the app is open", s.awake) { v -> vm.update { it.copy(awake = v) } }
            CheckRow("Keep screen on while the screen is locked", s.lockAwake) { v -> vm.update { it.copy(lockAwake = v) } }
            Hint("Audio keeps playing with the screen off or in other apps, with lock-screen controls. If Android stops it anyway, set this app's battery usage to Unrestricted.")
        }

        PwCard("Pronunciation") {
            Hint("Teach the voice how to say names and made-up words, for example Longxuan as Long shwen. Rules can apply to every novel or to one novel. They change what is spoken, never the text on screen.")
            PrimaryButton("Edit pronunciation rules", { vm.openSheet(Sheet.Pron(false)) }, Modifier.fillMaxWidth())
            if (pron.totalRules > 0) Hint("${pron.totalRules} rule${if (pron.totalRules == 1) "" else "s"} saved.")
        }

        PwCard("Backup & restore") {
            Hint("Your library, covers, saved chapters, pronunciation rules and settings live on this phone. Back them up now and then. The file also works in the web version of PageWhisper.")
            PrimaryButton("Back up everything", { export.launch("pagewhisper-backup-${LocalDate.now()}.json") }, Modifier.fillMaxWidth())
            SoftButton("Restore from a backup file", { import.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.fillMaxWidth())
            StatusText(backup.text, backup.error)
        }

        PwCard("About") {
            Text("PageWhisper v${BuildConfig.VERSION_NAME}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
            Text("A web novel reader that speaks every chapter aloud, in natural voices that run on your phone.", fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Justify, color = c.text)
            Hint("Developed by Sir_JPagdi")

            Label("What it does")
            Hint("Paste a chapter, share a link to the app, open a .txt file or read a page in the in-app browser. PageWhisper cleans the text, reads it aloud, highlights the sentence being spoken and keeps your place in every novel in your Library. Chapter lists, auto-next, a sleep timer with fade-out, ambient sounds and pronunciation rules are all built in.")

            Label("Private by design")
            Hint("No account, no ads, no tracking and no server of ours. Your library, saved chapters, pronunciation rules and settings stay on this phone. The only things that leave it are the chapter links you choose to fetch and the one-time voice model download.")

            Label("Voice")
            Hint("28 Kokoro voices, speech made on-device by sherpa-onnx. After the one-time voice model download, reading works fully offline. Speech is generated a little ahead of the audio and the phone gets short rests, so it stays cool and saves battery. Longer chunks run cooler than shorter ones.")

            Label("Reading")
            Hint("Four themes, thirteen reading fonts, adjustable size, spacing, alignment and highlight style. Each chapter is announced by its number and title before the text begins.")

            Label("Credits")
            Hint("Voices: Kokoro (Apache-2.0). Engine: sherpa-onnx (Apache-2.0) with espeak-ng phonemes (GPL-3). Fonts: Literata, Merriweather, Lora, Atkinson Hyperlegible, Lexend, OpenDyslexic, EB Garamond, Crimson Pro, Source Serif 4 and Montserrat, all under the SIL Open Font License.")

            Label("Tips")
            Hint("For listening with the screen off, set this app's battery usage to Unrestricted. Back up from this screen now and then; the same file also works in the web version.")
        }
    }
}
