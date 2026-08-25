package ru.maxx52.daric.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun KeyboardScreen(
    state: KeyboardUiState,
    onKey: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onOpenGif: () -> Unit,
    onOpenPostcards: () -> Unit,
    onCloseGif: () -> Unit,
    onOpenGifSearch: () -> Unit,
    onCloseGifSearch: () -> Unit,
    onClearGifSearch: () -> Unit,
    onRunGifSearch: () -> Unit,
    onRetryGif: () -> Unit,
    onGifSelected: (KlipyGif) -> Unit,
    onClosePostcards: () -> Unit,
    onPostcardSelected: (Postcard) -> Unit,
    onBackspacePressStart: () -> Unit,
    onBackspacePressEnd: (released: Boolean) -> Unit
) {
    Surface(
        color = KeyboardBackground,
        contentColor = KeyTextColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.panel) {
                KeyboardPanel.KEYS -> {
                    MediaToolbar(
                        onOpenGif = onOpenGif,
                        onOpenPostcards = onOpenPostcards
                    )
                    if (state.suggestionsVisible) {
                        SuggestionBar(state.suggestions, onSuggestion)
                    }
                    KeysPanel(
                        state = state,
                        onKey = onKey,
                        onBackspacePressStart = onBackspacePressStart,
                        onBackspacePressEnd = onBackspacePressEnd
                    )
                }
                KeyboardPanel.GIFS -> GifPanel(
                    state = state,
                    onClose = onCloseGif,
                    onOpenSearch = onOpenGifSearch,
                    onSearch = onRunGifSearch,
                    onRetry = onRetryGif,
                    onGifSelected = onGifSelected
                )
                KeyboardPanel.GIF_SEARCH -> {
                    GifSearchHeader(
                        query = state.gifQuery,
                        onBack = onCloseGifSearch,
                        onClear = onClearGifSearch
                    )
                    KeysPanel(
                        state = state,
                        onKey = onKey,
                        onBackspacePressStart = onBackspacePressStart,
                        onBackspacePressEnd = onBackspacePressEnd
                    )
                }
                KeyboardPanel.POSTCARDS -> PostcardPanel(
                    onClose = onClosePostcards,
                    onPostcardSelected = onPostcardSelected
                )
            }
        }
    }
}

@Composable
private fun MediaToolbar(
    onOpenGif: () -> Unit,
    onOpenPostcards: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAction(
            label = "GIF",
            modifier = Modifier.width(68.dp),
            onClick = onOpenGif
        )
        Spacer(Modifier.width(6.dp))
        SmallAction(
            label = "Открытки",
            modifier = Modifier.width(104.dp),
            onClick = onOpenPostcards
        )
    }
}

@Composable
private fun SuggestionBar(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        shape = RoundedCornerShape(10.dp),
        color = SuggestionBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            suggestions.take(3).forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(
                            if (suggestion.isNotBlank()) {
                                Modifier.clickable { onSuggestion(suggestion) }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = suggestion,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun KeysPanel(
    state: KeyboardUiState,
    onKey: (String) -> Unit,
    onBackspacePressStart: () -> Unit,
    onBackspacePressEnd: (Boolean) -> Unit
) {
    KeyRow(
        keys = numberRow,
        state = state,
        isNumberPanel = true,
        onKey = onKey,
        onBackspacePressStart = onBackspacePressStart,
        onBackspacePressEnd = onBackspacePressEnd
    )
    state.rows.forEach { row ->
        KeyRow(
            keys = row,
            state = state,
            onKey = onKey,
            onBackspacePressStart = onBackspacePressStart,
            onBackspacePressEnd = onBackspacePressEnd
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<String>,
    state: KeyboardUiState,
    isNumberPanel: Boolean = false,
    onKey: (String) -> Unit,
    onBackspacePressStart: () -> Unit,
    onBackspacePressEnd: (Boolean) -> Unit
) {
    val height = if (isNumberPanel) 48.dp else 56.dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isNumberPanel) 10.dp else 0.dp),
        color = if (isNumberPanel) NumberPanelBackground else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(if (isNumberPanel) 2.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            keys.forEach { key ->
                KeyboardKey(
                    label = state.displayText(key),
                    rawKey = key,
                    weight = keyWeight(key),
                    keyHeight = if (isNumberPanel) 44.dp else 52.dp,
                    isNumberPanel = isNumberPanel,
                    onClick = { onKey(key) },
                    onBackspacePressStart = onBackspacePressStart,
                    onBackspacePressEnd = onBackspacePressEnd
                )
            }
        }
    }
}

@Composable
private fun RowScope.KeyboardKey(
    label: String,
    rawKey: String,
    weight: Float,
    keyHeight: Dp,
    isNumberPanel: Boolean,
    onClick: () -> Unit,
    onBackspacePressStart: () -> Unit,
    onBackspacePressEnd: (Boolean) -> Unit
) {
    val special = rawKey in setOf("⇧", "⌫", "?123", "АБВ", "ABC", "🌐", "↵")
    val fillColor = when {
        isNumberPanel -> NumberKeyBackground
        special -> SpecialKeyBackground
        else -> Color.White
    }
    val strokeColor = if (isNumberPanel) NumberKeyStroke else KeyStroke
    val gestureModifier = if (rawKey == "⌫") {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onBackspacePressStart()
                    val released = tryAwaitRelease()
                    onBackspacePressEnd(released)
                }
            )
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Surface(
        modifier = Modifier
            .weight(weight)
            .height(keyHeight)
            .padding(2.dp)
            .then(gestureModifier),
        shape = RoundedCornerShape(9.dp),
        color = fillColor,
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = when {
                    rawKey == "пробел" || rawKey == "space" -> 13.sp
                    isNumberPanel -> 16.sp
                    else -> 18.sp
                },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun PostcardPanel(
    onClose: () -> Unit,
    onPostcardSelected: (Postcard) -> Unit
) {
    var selectedCategory by rememberSaveable {
        mutableStateOf(PostcardCategory.ALL)
    }
    val visiblePostcards = remember(selectedCategory) {
        if (selectedCategory == PostcardCategory.ALL) {
            postcardCatalog
        } else {
            postcardCatalog.filter { it.category == selectedCategory }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAction("⌨", Modifier.width(48.dp), onClose)
        Text(
            text = "Открытки",
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(48.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 3.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PostcardCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .padding(end = 6.dp)
                    .clickable { selectedCategory = category },
                shape = RoundedCornerShape(17.dp),
                color = if (selected) KeyTextColor else SuggestionBackground,
                border = BorderStroke(1.dp, if (selected) KeyTextColor else KeyStroke),
                contentColor = if (selected) Color.White else KeyTextColor
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.label, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(PostcardGridHeight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(3.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(visiblePostcards, key = { it.id }) { postcard ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clickable { onPostcardSelected(postcard) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, KeyStroke)
            ) {
                PostcardPreview(postcard, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PostcardPreview(
    postcard: Postcard,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(postcard.startColor),
                        Color(postcard.endColor)
                    )
                )
            )
            .padding(10.dp)
    ) {
        Text(
            text = postcard.decoration,
            modifier = Modifier.align(Alignment.TopEnd),
            fontSize = 28.sp
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = postcard.title,
                color = Color(postcard.textColor),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = postcard.message,
                modifier = Modifier.padding(top = 5.dp),
                color = Color(postcard.textColor).copy(alpha = 0.9f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun GifPanel(
    state: KeyboardUiState,
    onClose: () -> Unit,
    onOpenSearch: () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onGifSelected: (KlipyGif) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAction("⌨", Modifier.width(48.dp), onClose)
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 5.dp)
                .clickable(onClick = onOpenSearch),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, KeyStroke)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = state.gifQuery.ifBlank { "Поиск GIF" },
                    color = if (state.gifQuery.isBlank()) HintColor else KeyTextColor,
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }
        }
        SmallAction(
            label = "⌕",
            modifier = Modifier.width(48.dp),
            onClick = if (state.gifQuery.isBlank()) onOpenSearch else onSearch
        )
    }

    Text(
        text = "Powered by KLIPY",
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 5.dp, top = 2.dp, bottom = 3.dp),
        color = HintColor,
        fontSize = 11.sp,
        textAlign = TextAlign.End
    )

    when {
        state.gifLoading -> GifStatus { CircularProgressIndicator() }
        state.gifError != null -> GifStatus {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.gifError,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                SmallAction("Повторить", Modifier.width(112.dp), onRetry)
            }
        }
        state.gifItems.isEmpty() -> GifStatus { Text("GIF не найдены") }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(GifGridHeight),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.gifItems, key = { it.slug }) { gif ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onGifSelected(gif) },
                    shape = RoundedCornerShape(10.dp),
                    color = GifPlaceholder,
                    border = BorderStroke(1.dp, KeyStroke)
                ) {
                    AnimatedGif(
                        url = gif.previewUrl,
                        description = gif.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun GifSearchHeader(
    query: String,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(47.dp)
            .padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAction("←", Modifier.width(48.dp), onBack)
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 5.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, KeyStroke)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = query.ifBlank { "Введите запрос" },
                    color = if (query.isBlank()) HintColor else KeyTextColor,
                    fontSize = 17.sp,
                    maxLines = 1
                )
            }
        }
        SmallAction("×", Modifier.width(48.dp), onClear)
    }
}

@Composable
private fun GifStatus(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GifGridHeight),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SmallAction(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = SpecialKeyBackground,
        border = BorderStroke(1.dp, NumberKeyStroke)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 14.sp, maxLines = 1)
        }
    }
}

private fun keyWeight(key: String): Float = when (key) {
    "пробел", "space" -> 4f
    "⇧", "⌫", "?123", "АБВ", "ABC" -> 1.45f
    else -> 1f
}

private val KeyboardBackground = Color(0xFFECE9F4)
private val SuggestionBackground = Color(0xFFF5F1FA)
private val NumberPanelBackground = Color(0xFFD8D1E8)
private val NumberKeyBackground = Color(0xFFF8F6FC)
private val SpecialKeyBackground = Color(0xFFD8D1E8)
private val NumberKeyStroke = Color(0xFFB9AFCB)
private val KeyStroke = Color(0xFFC8C1D2)
private val KeyTextColor = Color(0xFF241F2E)
private val HintColor = Color(0xFF776B84)
private val GifPlaceholder = Color(0xFFE1DBEB)
private val GifGridHeight = 286.dp
private val PostcardGridHeight = 260.dp
