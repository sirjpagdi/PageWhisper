package com.pagewhisper.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PwCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPw.current
    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(c.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text)
        content()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Justify, color = LocalPw.current.muted)
}

@Composable
fun Label(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = LocalPw.current.muted)
}

@Composable
fun StatusText(text: String, error: Boolean = false, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    val c = LocalPw.current
    Text(text, modifier, fontSize = 12.sp, color = if (error) c.red else c.muted)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: String? = null) {
    val c = LocalPw.current
    Button(
        onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = accentContentColor(c.accent)),
    ) {
        if (icon != null) { PwIcon(icon, size = 20.dp); Box(Modifier.size(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, icon: String? = null) {
    val c = LocalPw.current
    OutlinedButton(
        onClick, modifier.heightIn(min = 44.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) c.accent else c.line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) c.accent else c.panel2,
            contentColor = if (selected) accentContentColor(c.accent) else c.text,
            disabledContainerColor = c.panel2.copy(alpha = 0.4f), disabledContentColor = c.muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) { PwIcon(icon, size = 18.dp); Box(Modifier.size(6.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
}

/** A row of options of which one is chosen (alignment, theme, order...). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((id, label) in options) SoftButton(label, { onSelect(id) }, Modifier.weight(1f), selected = id == selected)
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPw.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = c.accent))
        Text(text, Modifier.padding(start = 4.dp), fontSize = 14.sp, color = c.text)
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, display: String, onChange: (Float) -> Unit) {
    val c = LocalPw.current
    Column {
        Label(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(value, onChange, Modifier.weight(1f), valueRange = range, steps = steps)
            Text(display, Modifier.padding(start = 10.dp), fontSize = 12.sp, color = c.muted)
        }
    }
}

@Composable
fun SheetHeader(title: String, doneText: String = "Done", onDone: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        extra?.invoke()
        if (extra != null) Box(Modifier.size(8.dp))
        PrimaryButton(doneText, onDone)
    }
}
