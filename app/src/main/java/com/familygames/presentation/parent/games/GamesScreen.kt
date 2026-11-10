package com.familygames.presentation.parent.games

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.familygames.R
import com.familygames.domain.model.AgeBand
import com.familygames.domain.voice.PromptPrefix
import com.familygames.presentation.games.GameRegistry
import com.familygames.presentation.parent.common.ParentHeader
import com.familygames.presentation.parent.common.ParentPrimaryButton
import com.familygames.presentation.parent.common.ParentSecondaryButton
import com.familygames.presentation.parent.common.SegmentedTabs
import com.familygames.presentation.theme.Palette
import com.familygames.presentation.theme.ParentTheme
import com.familygames.presentation.theme.ParentType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val CardShape = RoundedCornerShape(22.dp)

/** S17 · Gry. */
@Composable
fun GamesScreen(state: GamesUiState, onEvent: (GamesEvent) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PromptPrefix?>(null) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { if (granted) onEvent(GamesEvent.RecordPrefix(it)) }
        pending = null
    }
    val record: (PromptPrefix) -> Unit = { prefix ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onEvent(GamesEvent.RecordPrefix(prefix))
        } else {
            pending = prefix
            mic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ParentHeader(stringResource(R.string.games_title), stringResource(R.string.games_subtitle))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface, CardShape)
                .border(1.dp, Palette.ParentLine, CardShape)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.games_age_title), style = ParentType.H2.copy(fontSize = 22.sp))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.games_age_text), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 16.sp))
            }
            SegmentedTabs(
                options = listOf(stringResource(R.string.games_age_toddler), stringResource(R.string.games_age_preschool)),
                selected = if (state.ageBand == AgeBand.TODDLER) 0 else 1,
                onSelect = { onEvent(GamesEvent.SetAge(if (it == 0) AgeBand.TODDLER else AgeBand.PRESCHOOL)) },
            )
        }
        state.games.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { row -> GameRow(row, Modifier.weight(1f)) { onEvent(GamesEvent.Toggle(row.entry.id, it)) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        VoiceCard(state, record, onEvent)
    }
}

@Composable
private fun GameRow(row: GameRowUi, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    val entry = row.entry
    val format = remember { DecimalFormat("0.#", DecimalFormatSymbols(Locale.forLanguageTag("pl-PL"))) }
    Row(
        modifier
            .heightIn(min = 88.dp)
            .background(if (row.on) Palette.Surface else Palette.Surface.copy(alpha = 0.55f), CardShape)
            .border(1.dp, Palette.ParentLine, CardShape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val dim = if (row.on) 1f else 0.55f
        Box(Modifier.size(56.dp).alpha(dim).background(Palette.AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(entry.icon), null, tint = Palette.AccentInk, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f).alpha(dim)) {
            Text(stringResource(entry.title), style = ParentType.H2.copy(fontSize = 19.sp))
            Text(stringResource(entry.description), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 15.sp))
            if (row.needsSequence) {
                Text(stringResource(R.string.games_needs_sequence), style = ParentType.Body.copy(color = Palette.AccentInk, fontSize = 14.sp))
            }
        }
        Text(
            stringResource(R.string.games_age_badge, format.format(entry.age.fromYears), format.format(entry.age.toYears)),
            style = ParentType.Caption.copy(color = Palette.ParentInk2, fontSize = 14.sp),
            modifier = Modifier.background(Palette.Bg2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Switch(checked = row.on, onCheckedChange = onToggle)
    }
}

@Composable
private fun VoiceCard(state: GamesUiState, onRecord: (PromptPrefix) -> Unit, onEvent: (GamesEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, CardShape)
            .border(1.dp, Palette.ParentLine, CardShape)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_mic), null, tint = Palette.AccentInk, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.games_voice_title), style = ParentType.H2.copy(fontSize = 22.sp))
        }
        Text(stringResource(R.string.games_voice_text), style = ParentType.Body.copy(color = Palette.ParentInk2, fontSize = 16.sp))
        Text(
            stringResource(if (state.ttsAvailable) R.string.games_voice_tts_on else R.string.games_voice_tts_off),
            style = ParentType.Body.copy(color = Palette.AccentInk, fontSize = 16.sp),
        )
        state.prefixes.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(p.label), style = ParentType.Body.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
                if (p.recording) {
                    ParentPrimaryButton(stringResource(R.string.games_voice_stop), onClick = { onRecord(p.prefix) }, color = Palette.Danger)
                } else {
                    ParentSecondaryButton(stringResource(R.string.games_voice_record), onClick = { onRecord(p.prefix) }, icon = R.drawable.ic_mic)
                }
                if (p.recorded && !p.recording) {
                    ParentSecondaryButton(stringResource(R.string.games_voice_play), onClick = { onEvent(GamesEvent.PlayPrefix(p.prefix)) }, icon = R.drawable.ic_play)
                    ParentSecondaryButton(stringResource(R.string.games_voice_delete), onClick = { onEvent(GamesEvent.DeletePrefix(p.prefix)) }, textColor = Palette.Danger)
                }
            }
        }
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun GamesScreenPreview() {
    ParentTheme {
        GamesScreen(
            GamesUiState(
                games = GameRegistry.games.mapIndexed { i, g -> GameRowUi(g, on = i % 4 != 3, needsSequence = false) },
                prefixes = listOf(PrefixUi(PromptPrefix.WHERE_IS, R.string.prefix_where_is_label, recorded = true, recording = false)),
            ),
        ) {}
    }
}
