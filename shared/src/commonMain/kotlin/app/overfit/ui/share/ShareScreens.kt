package app.overfit.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.overfit.data.TemplateRepository
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.domain.Numbers
import app.overfit.domain.formatWeight
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.ShareStrings
import app.overfit.share.ImportInbox
import app.overfit.share.ImportPreview
import app.overfit.share.PlatformShare
import app.overfit.share.PreviewExercise
import app.overfit.share.QrCode
import app.overfit.share.QrScanner
import app.overfit.share.ShareFailure
import app.overfit.share.ShareFormat
import app.overfit.share.ShareMapper
import app.overfit.share.ShareParse
import app.overfit.share.TemplateImporter
import app.overfit.share.AiPrompt
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything share/import needs; built once in the app. */
class ShareDependencies(
    val platform: PlatformShare,
    val importer: TemplateImporter,
    val templates: TemplateRepository,
    val inbox: ImportInbox,
    /** Lives as long as the app: work that must finish even when its screen leaves the composition. */
    val scope: kotlinx.coroutines.CoroutineScope,
)

fun ShareFailure.message(t: ShareStrings): String = when (this) {
    ShareFailure.EMPTY -> t.importEmpty
    ShareFailure.NOT_OVERFIT -> t.importNotOverfit
    ShareFailure.TOO_NEW -> t.importTooNew
    ShareFailure.UNSUPPORTED_KIND -> t.importUnsupportedKind
    ShareFailure.INVALID -> t.importInvalid
    ShareFailure.TOO_LARGE -> t.importTooLarge
}

/** Copies the AI prompt (in the app language) for ChatGPT, Claude or Gemini. */
fun copyAiPrompt(platform: PlatformShare, t: ShareStrings) = platform.copyText(t.aiTemplatePrompt(AiPrompt.EXAMPLE))

// ---------------------------------------------------------------------------------------------
// Import

class ImportViewModel(private val importer: TemplateImporter) : ViewModel() {
    data class State(
        val preview: ImportPreview? = null,
        val error: ShareFailure? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var input: ShareParse.Ok? = null

    /** Reads text from link, file, scan or clipboard and shows the preview. */
    fun handle(text: String?) {
        when (val parsed = ShareFormat.parseInput(text.orEmpty())) {
            is ShareParse.Fail -> _state.value = State(error = parsed.reason)
            is ShareParse.Ok -> {
                input = parsed
                viewModelScope.launch {
                    val preview = importer.preview(parsed.training)
                    _state.value = if (preview.exercises.isEmpty()) State(error = ShareFailure.INVALID) else State(preview = preview)
                }
            }
        }
    }

    fun reset() { _state.value = State() }

    fun accept(onDone: (Long) -> Unit) {
        val preview = _state.value.preview ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val id = importer.accept(preview)
            _state.value = State()
            onDone(id)
        }
    }
}

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    share: ShareDependencies,
    unit: WeightUnit,
    language: Language,
    onBack: () -> Unit,
    onImported: (Long) -> Unit,
) {
    val t = LocalStrings.current
    val s = t.share
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by share.inbox.pending.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var aiCopied by remember { mutableStateOf(false) }

    LaunchedEffect(pending) { share.inbox.consume()?.let { scanning = false; viewModel.handle(it) } }

    val preview = state.preview
    when {
        preview != null -> PreviewScreen(preview, unit, language, state.busy, onBack = viewModel::reset) {
            viewModel.accept(onImported)
        }
        scanning -> Screen(header = { BackHeader(t.back) { scanning = false } }) {
            Title(s.scanQr)
            Spacer(Modifier.height(Space.s))
            Text(s.scanHint, tone = Tone.Muted)
            Spacer(Modifier.height(Space.l))
            Box(Modifier.fillMaxWidth().weight(1f).background(Theme.colors.hairline)) {
                QrScanner(
                    Modifier.fillMaxSize(),
                    onScanned = { scanning = false; viewModel.handle(it) },
                    onPermissionDenied = { scanning = false; denied = true },
                )
            }
            Spacer(Modifier.height(Space.m))
        }
        else -> Screen(
            scroll = true,
            header = { BackHeader(t.back, onBack) },
            footer = {
                state.error?.let { Text(it.message(s), style = Theme.type.caption, tone = Tone.Default) }
                if (denied) Text(s.scanCameraDenied, style = Theme.type.caption, tone = Tone.Muted)
                PrimaryButton(s.importFromClipboard, onClick = { denied = false; viewModel.handle(share.platform.pasteText()) })
            },
        ) {
            Title(s.importTitle)
            Spacer(Modifier.height(Space.m))
            Text(s.importIntro, tone = Tone.Muted)
            Spacer(Modifier.height(Space.l))
            Hairline()
            if (share.platform.canScanInApp) {
                ListRow(s.scanQr, onClick = { denied = false; scanning = true })
            } else {
                Column(Modifier.padding(vertical = Space.m)) { Text(s.scanIosHint, tone = Tone.Muted) }
                Hairline()
            }
            ListRow(
                if (aiCopied) s.aiTemplateCopied else s.aiTemplateCopy,
                onClick = { copyAiPrompt(share.platform, s); aiCopied = true },
            )
            Column(Modifier.padding(vertical = Space.m)) { Text(s.importFileHint, style = Theme.type.caption, tone = Tone.Muted) }
        }
    }
}

@Composable
private fun PreviewScreen(
    preview: ImportPreview,
    unit: WeightUnit,
    language: Language,
    busy: Boolean,
    onBack: () -> Unit,
    onAccept: () -> Unit,
) {
    val t = LocalStrings.current
    val s = t.share
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = { PrimaryButton(s.importAccept, onAccept, loading = busy) },
    ) {
        Text(s.previewTitle, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Title(preview.name)
        Spacer(Modifier.height(Space.s))
        Text(s.previewCounts(preview.exercises.size, preview.exercises.count { it.isNew }), tone = Tone.Muted)
        preview.note?.let { Spacer(Modifier.height(Space.m)); Text(it, style = Theme.type.caption, tone = Tone.Muted) }
        Spacer(Modifier.height(Space.l))
        Hairline()
        preview.exercises.forEach { PreviewRow(it, unit, language) }
        if (preview.newExerciseNames.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            Text(s.previewNewNote(preview.newExerciseNames), style = Theme.type.caption, tone = Tone.Muted)
        }
    }
}

@Composable
private fun PreviewRow(line: PreviewExercise, unit: WeightUnit, language: Language) {
    val s = LocalStrings.current.share
    val first = line.sets.firstOrNull()
    val detail = buildList {
        add(s.previewSets(line.sets.size))
        if (first != null) {
            val value = buildList {
                first.weightKg?.let { add(formatWeight(it, unit, language)) }
                first.reps?.let { add("$it×") }
                first.durationSec?.let { add(Numbers.formatDuration(it)) }
            }.joinToString(" ")
            if (value.isNotBlank()) add(value)
        }
    }.joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.displayName, style = Theme.type.label)
                Text(detail, style = Theme.type.caption, tone = Tone.Muted)
            }
            if (line.isNew) Text(s.previewNewTag.uppercase(), style = Theme.type.caption, tone = Tone.Default)
        }
        Hairline()
    }
}

// ---------------------------------------------------------------------------------------------
// Share

class ShareViewModel(private val templateId: Long, private val templates: TemplateRepository) : ViewModel() {
    data class State(val name: String = "", val link: String? = null, val json: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val template = templates.get(templateId) ?: return@launch
            val training = ShareMapper.fromTemplate(template)
            _state.value = State(template.name, ShareFormat.toLink(training), ShareFormat.toJson(training))
        }
    }
}

@Composable
fun ShareScreen(viewModel: ShareViewModel, platform: PlatformShare, onBack: () -> Unit) {
    val t = LocalStrings.current
    val s = t.share
    val state by viewModel.state.collectAsStateWithLifecycle()
    val link = state.link
    val qrFits = link != null && link.length <= ShareFormat.MAX_QR_LINK_LENGTH && QrCode.fits(link)
    var copied by remember { mutableStateOf(false) }
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            PrimaryButton(s.shareLink, onClick = { link?.let { platform.shareText(s.shareMessage(state.name, it)) } }, enabled = link != null)
        },
    ) {
        Text(s.shareTemplate, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Title(state.name)
        Spacer(Modifier.height(Space.l))
        if (link != null) {
            if (qrFits) {
                QrView(link, Modifier.widthIn(max = 320.dp).fillMaxWidth().align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Space.m))
                Text(s.qrHint, style = Theme.type.caption, tone = Tone.Muted)
            } else {
                Text(s.qrTooLarge, tone = Tone.Muted)
            }
            Spacer(Modifier.height(Space.l))
            Hairline()
            ListRow(if (copied) s.linkCopied else s.copyLink, onClick = { platform.copyText(link); copied = true })
            state.json?.let { json ->
                ListRow(s.shareFile, onClick = {
                    val file = state.name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().ifEmpty { "overfit" }
                    platform.shareFile("$file.${ShareFormat.FILE_EXTENSION}", json)
                })
            }
        }
    }
}

/** QR code: always black on white with quiet zone, so every scanner reads it regardless of the theme. */
@Composable
fun QrView(text: String, modifier: Modifier = Modifier) {
    val qr = remember(text) { runCatching { QrCode.encode(text) }.getOrNull() } ?: return
    val quiet = 3
    Canvas(modifier.aspectRatio(1f).background(Color.White)) {
        val cell = size.width / (qr.size + 2 * quiet)
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr[x, y]) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Duo teaser

@Composable
fun DuoScreen(onBack: () -> Unit) {
    val t = LocalStrings.current
    Screen(header = { BackHeader(t.back, onBack) }) {
        Title(t.share.duoTitle)
        Spacer(Modifier.height(Space.l))
        Text(t.share.duoBody)
        Spacer(Modifier.height(Space.m))
        Text(t.share.duoNote, style = Theme.type.caption, tone = Tone.Muted)
    }
}

// ---------------------------------------------------------------------------------------------
// Entry points in other screens

/** "Add sample templates" (idempotent) with the result shown as the row's second line. */
@Composable
fun SamplesRow(share: ShareDependencies) {
    val s = LocalStrings.current.share
    var message by remember { mutableStateOf<String?>(null) }
    ListRow(s.addSamples, onClick = {
        // App scope, not the composable's: the first saved template makes the list non-empty, the row leaves
        // the composition, and a composition scope would cancel the import half-way (only Push would arrive).
        share.scope.launch {
            val n = share.importer.addSamples()
            message = if (n == 0) s.samplesAlready else s.samplesAdded(n)
        }
    }) {
        message?.let { Text(it, style = Theme.type.caption, tone = Tone.Muted) }
    }
}

/** Rows under the template list: import, copy AI prompt, sample templates. */
@Composable
fun TemplateImportRows(share: ShareDependencies, onImport: () -> Unit, showSamples: Boolean = true) {
    val s = LocalStrings.current.share
    var aiCopied by remember { mutableStateOf(false) }
    ListRow(s.importTitle, onClick = onImport)
    ListRow(if (aiCopied) s.aiTemplateCopied else s.aiTemplateCopy, onClick = { copyAiPrompt(share.platform, s); aiCopied = true })
    if (showSamples) SamplesRow(share)
}
