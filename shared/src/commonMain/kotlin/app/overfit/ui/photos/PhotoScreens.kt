package app.overfit.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.data.DiaryPhoto
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private fun dateOf(epochMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date

private sealed interface GridEntry {
    data class Header(val month: Int, val year: Int) : GridEntry
    data class Item(val photo: DiaryPhoto) : GridEntry
}

private fun groupByMonth(photos: List<DiaryPhoto>): List<GridEntry> = buildList {
    var last: Pair<Int, Int>? = null
    photos.forEach {
        val d = dateOf(it.photo.takenAt)
        val key = d.monthNumber to d.year
        if (key != last) { add(GridEntry.Header(d.monthNumber, d.year)); last = key }
        add(GridEntry.Item(it))
    }
}

// ---------------------------------------------------------------------------------------------
// Diary tab

/** [photos] is null while loading; newest first. */
@Composable
fun PhotosScreen(
    photos: List<DiaryPhoto>?,
    onOpen: (photoId: String) -> Unit,
    onCompare: (olderId: String, newerId: String) -> Unit,
) {
    val t = LocalStrings.current
    val p = t.photos
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    val list = photos.orEmpty()
    val entries = remember(photos) { groupByMonth(list) }
    // Drop selections whose photo vanished.
    val valid = selected.filter { id -> list.any { it.photo.id == id } }

    Screen(
        footer = if (selecting) {
            {
                if (valid.size < 2) Text(p.selectTwo, style = Theme.type.caption, tone = Tone.Muted)
                PrimaryButton(p.compare, enabled = valid.size == 2, onClick = {
                    val chosen = list.filter { it.photo.id in valid }.sortedBy { it.photo.takenAt }
                    if (chosen.size == 2) onCompare(chosen[0].photo.id, chosen[1].photo.id)
                })
            }
        } else null,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Title(t.photosTitle)
            if (selecting) {
                TextButton(p.cancel, onClick = { selecting = false; selected = emptyList() })
            } else if (list.size >= 2) {
                TextButton(p.compare, onClick = { selecting = true })
            }
        }
        Spacer(Modifier.height(Space.m))
        if (photos != null && list.isEmpty()) {
            Text(t.photosEmpty, tone = Tone.Muted)
            Spacer(Modifier.height(Space.m))
            Text(p.emptyHint, style = Theme.type.caption, tone = Tone.Muted)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is GridEntry.Header -> item(span = { GridItemSpan(maxLineSpan) }, key = "h${entry.year}-${entry.month}") {
                            Text(
                                p.monthHeader(entry.month, entry.year),
                                Modifier.padding(top = Space.l, bottom = Space.s),
                                style = Theme.type.label,
                                tone = Tone.Muted,
                            )
                        }
                        is GridEntry.Item -> item(key = entry.photo.photo.id) {
                            val id = entry.photo.photo.id
                            Thumbnail(
                                entry.photo,
                                number = valid.indexOf(id).takeIf { it >= 0 }?.plus(1),
                                onClick = {
                                    if (!selecting) onOpen(id)
                                    else selected = when {
                                        id in valid -> valid - id
                                        valid.size < 2 -> valid + id
                                        else -> listOf(valid[1], id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(photo: DiaryPhoto, number: Int?, onClick: () -> Unit) {
    val p = LocalStrings.current.photos
    val t = LocalStrings.current
    val desc = p.photoDescription(t.date(dateOf(photo.photo.takenAt)))
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Theme.colors.hairline)
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
    ) {
        AsyncImage(photo.photo.path, desc, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (number != null) {
            Box(Modifier.fillMaxSize().border(2.dp, Theme.colors.foreground))
            Box(
                Modifier.align(Alignment.TopStart).padding(Space.xs).size(22.dp).background(Theme.colors.inverseBackground),
                contentAlignment = Alignment.Center,
            ) { Text(number.toString(), style = Theme.type.caption, tone = Tone.Inverse) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Viewer

@Composable
fun PhotoViewerScreen(
    photos: List<DiaryPhoto>?,
    photoId: String,
    onBack: () -> Unit,
    onWorkout: (sessionId: String) -> Unit,
    onDelete: (photoId: String) -> Unit,
) {
    val t = LocalStrings.current
    val p = t.photos
    val item = photos?.firstOrNull { it.photo.id == photoId }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(4000); confirm = false } }
    val white = Color.White
    val body = Theme.type.body.copy(color = white)

    Column(
        Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.padding(horizontal = Space.l, vertical = Space.m).height(40.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.size(40.dp).semantics { contentDescription = t.back }.clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { LineIcon(Icon.Back, color = white) }
        }
        if (item == null) {
            if (photos != null) BasicText(p.notFound, Modifier.padding(horizontal = Space.l), style = body)
            return@Column
        }
        val date = t.date(dateOf(item.photo.takenAt))
        AsyncImage(
            item.photo.path,
            p.photoDescription(date),
            Modifier.fillMaxWidth().weight(1f),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
            BasicText(date, style = Theme.type.label.copy(color = white))
            BasicText(p.viewerWorkout(item.workoutName), style = Theme.type.caption.copy(color = white.copy(alpha = 0.6f)))
            Spacer(Modifier.height(Space.s))
            ViewerAction(p.toWorkout, white) { onWorkout(item.photo.sessionId) }
            ViewerAction(if (confirm) p.deletePhotoConfirm else p.deletePhoto, if (confirm) white else white.copy(alpha = 0.6f)) {
                if (confirm) onDelete(item.photo.id) else confirm = true
            }
        }
    }
}

@Composable
private fun ViewerAction(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
    Box(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
        BasicText(label, style = Theme.type.body.copy(color = color))
    }
}

// ---------------------------------------------------------------------------------------------
// Compare

@Composable
fun PhotoCompareScreen(photos: List<DiaryPhoto>?, firstId: String, secondId: String, onBack: () -> Unit) {
    val t = LocalStrings.current
    val p = t.photos
    val a = photos?.firstOrNull { it.photo.id == firstId }
    val b = photos?.firstOrNull { it.photo.id == secondId }
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        if (a == null || b == null) {
            if (photos != null) Text(p.notFound, tone = Tone.Muted)
            return@Screen
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ComparePane(a, Modifier.weight(1f))
            ComparePane(b, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.l))
        Hairline()
        val days = dateOf(a.photo.takenAt).daysUntil(dateOf(b.photo.takenAt))
        Text(
            if (days == 0) p.sameDay else p.daysApart(kotlin.math.abs(days)),
            Modifier.padding(vertical = Space.m),
            tone = Tone.Muted,
        )
    }
}

@Composable
private fun ComparePane(item: DiaryPhoto, modifier: Modifier) {
    val t = LocalStrings.current
    val date = t.date(dateOf(item.photo.takenAt))
    Column(modifier) {
        AsyncImage(
            item.photo.path,
            t.photos.photoDescription(date),
            Modifier.fillMaxWidth().background(Theme.colors.hairline),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(Space.s))
        Text(date, style = Theme.type.caption, tone = Tone.Muted)
    }
}
