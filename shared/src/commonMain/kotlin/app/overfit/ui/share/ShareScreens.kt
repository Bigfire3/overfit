package app.overfit.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.overfit.share.NoShortCodes
import app.overfit.share.QrCode
import app.overfit.share.QrPayload
import app.overfit.share.ShortCodeService
import app.overfit.share.qrTextFor
import app.overfit.platform.rememberFilePicker
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.size
import app.overfit.share.QrScanner
import app.overfit.share.ShareFailure
import app.overfit.share.ShareFormat
import app.overfit.share.ShareMapper
import app.overfit.share.SharedTraining
import app.overfit.share.ShareParse
import app.overfit.share.TemplateImporter
import app.overfit.share.AiPrompt
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
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
    /** Short codes over the backend (not deployed yet: [NoShortCodes], the QR then carries the full content). */
    val shortCodes: ShortCodeService = NoShortCodes,
    /** "About me" line (name, age, weight) in the app language for the AI prompt; null when nothing is known. */
    val aiProfile: () -> String? = { null },
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
fun copyAiPrompt(share: ShareDependencies, t: ShareStrings) = share.platform.copyText(aiTemplateText(t, share.aiProfile()))

/** The AI template prompt with the profile line on top. */
fun aiTemplateText(t: ShareStrings, profileHeader: String?): String =
    app.overfit.domain.withAiProfile(profileHeader, t.aiTemplatePrompt(AiPrompt.EXAMPLE))

// ---------------------------------------------------------------------------------------------
// Import

class ImportViewModel(private val importer: TemplateImporter, private val shortCodes: ShortCodeService = NoShortCodes) : ViewModel() {
    data class State(
        /** Set once the content is read: the screen hands it to the workout editor and resets. */
        val preview: ImportPreview? = null,
        val error: ShareFailure? = null,
        /** Shown as a note instead of a failure reason (e.g. "this is a backup"). */
        val note: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var input: ShareParse.Ok? = null

    /** Reads text from link, file, scan or clipboard; the result opens in the workout editor. */
    fun handle(text: String?) {
        QrPayload.shortId(text.orEmpty())?.let { id ->
            viewModelScope.launch {
                val training = runCatching { shortCodes.fetch(id) }.getOrNull()
                if (training == null) _state.value = State(error = ShareFailure.NOT_OVERFIT) else openEditor(ShareParse.Ok(training))
            }
            return
        }
        when (val parsed = ShareFormat.parseInput(text.orEmpty())) {
            is ShareParse.Fail -> _state.value = State(error = parsed.reason)
            is ShareParse.Ok -> openEditor(parsed)
        }
    }

    private fun openEditor(parsed: ShareParse.Ok) {
        input = parsed
        viewModelScope.launch {
            val preview = importer.preview(parsed.training)
            _state.value = if (preview.exercises.isEmpty()) State(error = ShareFailure.INVALID) else State(preview = preview)
        }
    }

    /** Content of a picked file: a `.overfit` file (JSON) or, by mistake, a backup ZIP. */
    fun handleFile(bytes: ByteArray) {
        when {
            bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> _state.value = State(note = true)
            bytes.size > ShareFormat.MAX_JSON_BYTES -> _state.value = State(error = ShareFailure.TOO_LARGE)
            else -> handle(bytes.decodeToString())
        }
    }

    fun reset() { _state.value = State() }
}

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    share: ShareDependencies,
    onBack: () -> Unit,
    onImported: (ImportPreview) -> Unit,
) {
    val t = LocalStrings.current
    val s = t.share
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by share.inbox.pending.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var aiCopied by remember { mutableStateOf(false) }

    val pickFile = rememberFilePicker { bytes -> denied = false; viewModel.handleFile(bytes) }

    LaunchedEffect(pending) { share.inbox.consume()?.let { scanning = false; viewModel.handle(it) } }

    // The imported workout opens in the normal editor (nothing is saved before "Save").
    LaunchedEffect(state.preview) { state.preview?.let { viewModel.reset(); onImported(it) } }

    when {
        state.preview != null -> Screen {}
        scanning -> Screen(header = { BackHeader(t.back, { scanning = false }) }) {
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
                if (state.note) Text(s.importIsBackup, style = Theme.type.caption, tone = Tone.Default)
                if (denied) Text(s.scanCameraDenied, style = Theme.type.caption, tone = Tone.Muted)
                PrimaryButton(s.importFromClipboard, onClick = { denied = false; viewModel.handle(share.platform.pasteText()) })
            },
        ) {
            Title(s.importTitle)
            Spacer(Modifier.height(Space.m))
            Text(s.importIntro, tone = Tone.Muted)
            Spacer(Modifier.height(Space.l))
            Panel {
                if (share.platform.canScanInApp) {
                    ListRow(s.scanQr, onClick = { denied = false; scanning = true })
                } else {
                    PanelItem { Text(s.scanIosHint, tone = Tone.Muted) }
                }
                ListRow(s.importPickFile, onClick = { denied = false; pickFile() })
            }
            // The AI prompt with its full text (shared component with the Coach tab).
            app.overfit.ui.components.PromptView(
                title = s.aiTemplateCopy,
                hint = if (aiCopied) s.aiTemplateCopied else null,
                text = remember { aiTemplateText(s, share.aiProfile()) },
                onCopy = { share.platform.copyText(it); aiCopied = true },
            )
            Hint(s.importFileHint)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Share

/** Link/QR/file for a template or a finished workout ([source] builds the shared training). */
class ShareViewModel(
    private val source: suspend () -> SharedTraining?,
    private val shortCodes: ShortCodeService = NoShortCodes,
) : ViewModel() {
    constructor(templateId: Long, templates: TemplateRepository, shortCodes: ShortCodeService = NoShortCodes) :
        this({ templates.get(templateId)?.let { ShareMapper.fromTemplate(it) } }, shortCodes)

    data class State(
        val name: String = "",
        val link: String? = null,
        val json: String? = null,
        /** Compact app-internal QR content (`OF1:…`, or `OFS:<id>` with the backend), null if it does not fit. */
        val qrText: String? = null,
        val training: SharedTraining? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val training = source() ?: return@launch
            val qr = qrTextFor(training, shortCodes).takeIf { QrCode.fits(it) }
            _state.value = State(training.name, ShareFormat.toLink(training), ShareFormat.toJson(training), qr, training)
        }
    }
}

@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    platform: PlatformShare,
    onBack: () -> Unit,
    /** Optional explanation below the title (e.g. what a shared workout contains). */
    description: String? = null,
    /** Optional extra controls below the description (e.g. a toggle). */
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    unit: WeightUnit = WeightUnit.KG,
    language: Language = Language.DE,
    /** Shows what the shared content contains (counts, example values). */
    showPreview: Boolean = true,
) {
    val t = LocalStrings.current
    val s = t.share
    val state by viewModel.state.collectAsStateWithLifecycle()
    val link = state.link
    val qrText = state.qrText
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
        if (description != null) {
            Spacer(Modifier.height(Space.s))
            Text(description, style = Theme.type.caption, tone = Tone.Muted)
        }
        if (extra != null) {
            Spacer(Modifier.height(Space.m))
            extra()
        }
        state.training?.takeIf { showPreview }?.let { training ->
            Spacer(Modifier.height(Space.m))
            SharePreview(training, unit, language)
        }
        Spacer(Modifier.height(Space.l))
        if (link != null) {
            if (qrText != null) {
                QrView(qrText, Modifier.size(QR_DISPLAY_SIZE).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Space.m))
                Text(s.qrHint, style = Theme.type.caption, tone = Tone.Muted)
            } else {
                Text(s.qrTooLarge, tone = Tone.Muted)
            }
            Spacer(Modifier.height(Space.l))
            Panel {
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
}

/** Display size of the share QR code (before: 320 dp wide). The quiet zone is part of it. */
private val QR_DISPLAY_SIZE = 200.dp

/** "What gets shared": counts and, if values are included, one example set. */
@Composable
private fun SharePreview(training: SharedTraining, unit: WeightUnit, language: Language) {
    val s = LocalStrings.current.share
    val sets = training.exercises.sumOf { it.sets.size }
    val example = training.exercises.firstNotNullOfOrNull { e ->
        e.sets.firstOrNull { it.kg != null || it.reps != null || it.sec != null }
    }?.let { set ->
        buildList {
            set.kg?.let { add(formatWeight(it, unit, language)) }
            set.reps?.let { add(if (set.kg != null) "× $it" else "$it×") }
            set.sec?.let { add(Numbers.formatDuration(it)) }
        }.joinToString(" ")
    }
    Panel {
        PanelItem {
            Text(s.sharePreviewTitle, style = Theme.type.caption, tone = Tone.Muted)
            Text(s.sharePreviewCounts(training.exercises.size, sets), style = Theme.type.label)
            Text(if (example != null) s.sharePreviewValues(example) else s.sharePreviewNoValues, style = Theme.type.caption, tone = Tone.Muted)
        }
    }
}

/**
 * Generic QR code for any text (workouts, later Duo): black on white with quiet zone, so every scanner
 * reads it regardless of the theme. Round modules; the three finder patterns stay solid squares with rounded corners.
 */
@Composable
fun QrView(text: String, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val qr = remember(text) { runCatching { QrCode.encode(text) }.getOrNull() } ?: return
    val quiet = QR_QUIET_ZONE
    Canvas(modifier.aspectRatio(1f).background(Color.White, RoundedCornerShape(8.dp))) {
        val cell = size.width / (qr.size + 2 * quiet)
        fun inFinder(x: Int, y: Int) = (x < 8 && y < 8) || (x >= qr.size - 8 && y < 8) || (x < 8 && y >= qr.size - 8)
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (!qr[x, y]) continue
            val topLeft = Offset((x + quiet) * cell, (y + quiet) * cell)
            if (!rounded || inFinder(x, y)) {
                drawRect(Color.Black, topLeft, Size(cell + 0.5f, cell + 0.5f))
            } else {
                // Slightly overlapping rounded squares: round look, but neighbours still read as one block.
                val inset = cell * 0.04f
                drawRoundRect(Color.Black, topLeft + Offset(inset, inset), Size(cell - 2 * inset, cell - 2 * inset), CornerRadius(cell * 0.38f))
            }
        }
    }
}

/** Light margin around the symbol in modules. ISO asks for 4; scanners cope with less, we keep 3 plus the white card. */
const val QR_QUIET_ZONE = 3

// ---------------------------------------------------------------------------------------------
// Entry points in other screens

/** Rows under the template list: import and copy AI prompt. */
@Composable
fun TemplateImportRows(share: ShareDependencies, onImport: () -> Unit) {
    val s = LocalStrings.current.share
    var aiCopied by remember { mutableStateOf(false) }
    Panel { ListRow(s.importTitle, onClick = onImport) }
    app.overfit.ui.components.PromptView(
        title = s.aiTemplateCopy,
        hint = if (aiCopied) s.aiTemplateCopied else null,
        text = remember { aiTemplateText(s, share.aiProfile()) },
        onCopy = { share.platform.copyText(it); aiCopied = true },
    )
}
