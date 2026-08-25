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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.maxx52.daric.settings.KeyboardBackgroundMode
import ru.maxx52.daric.settings.KeyboardColorTheme

@Composable
internal fun KeyboardScreen(
    state: KeyboardUiState,
    onKey: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onOpenGif: () -> Unit,
    onOpenPostcards: () -> Unit,
    onOpenEmoji: () -> Unit,
    onCloseGif: () -> Unit,
    onOpenGifSearch: () -> Unit,
    onCloseGifSearch: () -> Unit,
    onClearGifSearch: () -> Unit,
    onRunGifSearch: () -> Unit,
    onRetryGif: () -> Unit,
    onGifSelected: (KlipyGif) -> Unit,
    onClosePostcards: () -> Unit,
    onPostcardSelected: (Postcard) -> Unit,
    onCloseEmoji: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onBackspacePressStart: () -> Unit,
    onBackspacePressEnd: (released: Boolean) -> Unit
) {
    val palette = remember(state.colorTheme, state.darkTheme) {
        keyboardPalette(state.colorTheme, state.darkTheme)
    }
    val backgroundColors = if (state.backgroundMode == KeyboardBackgroundMode.GRADIENT) {
        listOf(palette.backgroundTop, palette.backgroundBottom)
    } else {
        listOf(palette.backgroundTop, palette.backgroundTop)
    }
    val backgroundBrush = Brush.verticalGradient(backgroundColors)

    CompositionLocalProvider(LocalKeyboardPalette provides palette) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            contentColor = palette.text
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundBrush)
                    .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.panel) {
                KeyboardPanel.KEYS -> {
                    MediaToolbar(
                        onOpenGif = onOpenGif,
                        onOpenPostcards = onOpenPostcards,
                        onOpenEmoji = onOpenEmoji
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
                KeyboardPanel.EMOJIS -> EmojiPanel(
                    onClose = onCloseEmoji,
                    onEmojiSelected = onEmojiSelected
                )
            }
        }
    }
}
}

@Composable
private fun MediaToolbar(
    onOpenGif: () -> Unit,
    onOpenPostcards: () -> Unit,
    onOpenEmoji: () -> Unit
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
        Spacer(Modifier.width(6.dp))
        SmallAction(
            label = "😊",
            modifier = Modifier.width(48.dp),
            onClick = onOpenEmoji
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
    if (state.showNumberRow) {
        KeyRow(
            keys = numberRow,
            state = state,
            isNumberPanel = true,
            onKey = onKey,
            onBackspacePressStart = onBackspacePressStart,
            onBackspacePressEnd = onBackspacePressEnd
        )
    }
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
    val keyHeight = if (isNumberPanel) {
        (state.keyHeightDp - 8).coerceAtLeast(40).dp
    } else {
        state.keyHeightDp.dp
    }
    val height = keyHeight + 4.dp
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
                    keyHeight = keyHeight,
                    isNumberPanel = isNumberPanel,
                    onClick = { onKey(key) },
                    onAlternativeKey = onKey,
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
    onAlternativeKey: (String) -> Unit,
    onBackspacePressStart: () -> Unit,
    onBackspacePressEnd: (Boolean) -> Unit
) {
    val special = rawKey in setOf("⇧", "⌫", "?123", "АБВ", "ABC", "🌐", "↵")
    val fillColor = when {
        isNumberPanel -> NumberKeyBackground
        special -> SpecialKeyBackground
        else -> NumberKeyBackground
    }
    val strokeColor = if (isNumberPanel) NumberKeyStroke else KeyStroke
    var punctuationMenuVisible by remember { mutableStateOf(false) }
    val gestureModifier = when (rawKey) {
        "⌫" -> Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onBackspacePressStart()
                    val released = tryAwaitRelease()
                    onBackspacePressEnd(released)
                }
            )
        }
        "." -> Modifier.pointerInput(rawKey) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { punctuationMenuVisible = true }
            )
        }
        else -> Modifier.clickable(onClick = onClick)
    }
    val popupOffset = with(LocalDensity.current) { 50.dp.roundToPx() }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(keyHeight),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
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

        if (punctuationMenuVisible && rawKey == ".") {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -popupOffset),
                onDismissRequest = { punctuationMenuVisible = false },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuggestionBackground,
                    border = BorderStroke(1.dp, KeyStroke),
                    shadowElevation = 8.dp
                ) {
                    Row(Modifier.padding(4.dp)) {
                        punctuationAlternatives.forEach { symbol ->
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(2.dp)
                                    .clickable {
                                        punctuationMenuVisible = false
                                        onAlternativeKey(symbol)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = SpecialKeyBackground,
                                border = BorderStroke(1.dp, NumberKeyStroke)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = symbol,
                                        fontSize = 20.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EmojiPanel(
    onClose: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    var selectedCategory by rememberSaveable {
        mutableStateOf(EmojiCategory.SMILEYS)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAction("⌨", Modifier.width(48.dp), onClose)
        Text(
            text = selectedCategory.title,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(48.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiCategory.entries.forEach { category ->
            val selected = category == selectedCategory
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { selectedCategory = category },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    SpecialKeyBackground
                } else {
                    NumberKeyBackground
                },
                border = BorderStroke(1.dp, KeyStroke)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(category.icon, fontSize = 20.sp)
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height(EmojiGridHeight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(
            items = selectedCategory.emojis,
            key = { index, emoji -> selectedCategory.name + "-" + index + "-" + emoji }
        ) { _, emoji ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clickable { onEmojiSelected(emoji) },
                shape = RoundedCornerShape(9.dp),
                color = NumberKeyBackground,
                border = BorderStroke(1.dp, KeyStroke)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 25.sp, textAlign = TextAlign.Center)
                }
            }
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
                contentColor = if (selected) NumberKeyBackground else KeyTextColor
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
            color = NumberKeyBackground,
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
                    color = HintColor,
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
            color = NumberKeyBackground,
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

private val punctuationAlternatives = listOf(",", "!", "?", ":", ";", "…", "-", "—")

private fun keyWeight(key: String): Float = when (key) {
    "пробел", "space" -> 4f
    "⇧", "⌫", "?123", "АБВ", "ABC" -> 1.45f
    else -> 1f
}

private data class KeyboardPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val numberPanel: Color,
    val key: Color,
    val special: Color,
    val stroke: Color,
    val text: Color,
    val hint: Color
)

private val LocalKeyboardPalette = compositionLocalOf {
    keyboardPalette(KeyboardColorTheme.LAVENDER, darkTheme = false)
}

private fun keyboardPalette(
    theme: KeyboardColorTheme,
    darkTheme: Boolean
): KeyboardPalette = when (theme) {
    KeyboardColorTheme.LAVENDER -> if (darkTheme) {
        keyboardPaletteOf(
            0xFF18141F, 0xFF2A2335, 0xFF2F283A,
            0xFF3A3148, 0xFF342D40, 0xFF58456E,
            0xFF75638A, 0xFFF4ECFC, 0xFFC6B9D3
        )
    } else {
        keyboardPaletteOf(
            0xFFF4F0FA, 0xFFE4DFF0, 0xFFFAF7FF,
            0xFFDED5EC, 0xFFFCFBFF, 0xFFD4C5E8,
            0xFFAC9DBF, 0xFF2B2435, 0xFF6C6376
        )
    }

    KeyboardColorTheme.OCEAN -> if (darkTheme) {
        keyboardPaletteOf(
            0xFF0E1D25, 0xFF173441, 0xFF19333F,
            0xFF24505F, 0xFF1D3A47, 0xFF24566A,
            0xFF3B7187, 0xFFE4F5FC, 0xFFAACBD8
        )
    } else {
        keyboardPaletteOf(
            0xFFE7F4FA, 0xFFCDE8F3, 0xFFF1FAFD,
            0xFFC6E3EE, 0xFFF8FCFF, 0xFFB9DCEB,
            0xFF8CB9CC, 0xFF15313E, 0xFF587581
        )
    }

    KeyboardColorTheme.ROSE -> if (darkTheme) {
        keyboardPaletteOf(
            0xFF27151B, 0xFF42232D, 0xFF40242D,
            0xFF58303C, 0xFF4A2B34, 0xFF71394A,
            0xFF955469, 0xFFFFE9EF, 0xFFD4AAB6
        )
    } else {
        keyboardPaletteOf(
            0xFFFFF0F4, 0xFFF4D6DF, 0xFFFFF7F9,
            0xFFF0CED8, 0xFFFFF9FA, 0xFFEAB9C8,
            0xFFCC91A4, 0xFF43242D, 0xFF805B66
        )
    }

    KeyboardColorTheme.FOREST -> if (darkTheme) {
        keyboardPaletteOf(
            0xFF101F15, 0xFF1D3524, 0xFF223B29,
            0xFF2D5035, 0xFF27432E, 0xFF35623F,
            0xFF4F7E58, 0xFFE8F6E9, 0xFFACC9AF
        )
    } else {
        keyboardPaletteOf(
            0xFFEDF6EE, 0xFFD4E7D5, 0xFFF5FBF5,
            0xFFCCE1CE, 0xFFFAFEFA, 0xFFBCD8BE,
            0xFF91B494, 0xFF1F3824, 0xFF5D7761
        )
    }

    KeyboardColorTheme.SUNSET -> if (darkTheme) {
        keyboardPaletteOf(
            0xFF2A1810, 0xFF47281B, 0xFF472B1E,
            0xFF623823, 0xFF503124, 0xFF7B462A,
            0xFFA26643, 0xFFFFF0E5, 0xFFD6B39C
        )
    } else {
        keyboardPaletteOf(
            0xFFFFF2E6, 0xFFF9D7BD, 0xFFFFF8F2,
            0xFFF7CFAD, 0xFFFFFBF7, 0xFFF3BE91,
            0xFFD59768, 0xFF462A19, 0xFF80624E
        )
    }

    KeyboardColorTheme.GRAPHITE -> if (darkTheme) {
        keyboardPaletteOf(
            0xFF121417, 0xFF24282E, 0xFF292E34,
            0xFF383E46, 0xFF2E333A, 0xFF414852,
            0xFF5D6672, 0xFFF1F3F5, 0xFFB7BDC5
        )
    } else {
        keyboardPaletteOf(
            0xFFF0F1F3, 0xFFD9DCE1, 0xFFF6F7F8,
            0xFFD5D8DD, 0xFFFAFAFB, 0xFFC9CDD4,
            0xFFA5AAB3, 0xFF22252A, 0xFF686E77
        )
    }
}

private fun keyboardPaletteOf(
    backgroundTop: Long,
    backgroundBottom: Long,
    surface: Long,
    numberPanel: Long,
    key: Long,
    special: Long,
    stroke: Long,
    text: Long,
    hint: Long
) = KeyboardPalette(
    backgroundTop = Color(backgroundTop),
    backgroundBottom = Color(backgroundBottom),
    surface = Color(surface),
    numberPanel = Color(numberPanel),
    key = Color(key),
    special = Color(special),
    stroke = Color(stroke),
    text = Color(text),
    hint = Color(hint)
)

private val SuggestionBackground: Color
    @Composable get() = LocalKeyboardPalette.current.surface
private val NumberPanelBackground: Color
    @Composable get() = LocalKeyboardPalette.current.numberPanel
private val NumberKeyBackground: Color
    @Composable get() = LocalKeyboardPalette.current.key
private val SpecialKeyBackground: Color
    @Composable get() = LocalKeyboardPalette.current.special
private val NumberKeyStroke: Color
    @Composable get() = LocalKeyboardPalette.current.stroke
private val KeyStroke: Color
    @Composable get() = LocalKeyboardPalette.current.stroke
private val KeyTextColor: Color
    @Composable get() = LocalKeyboardPalette.current.text
private val HintColor: Color
    @Composable get() = LocalKeyboardPalette.current.hint
private val GifPlaceholder: Color
    @Composable get() = LocalKeyboardPalette.current.numberPanel
private val GifGridHeight = 286.dp
private val EmojiGridHeight = 246.dp
private val PostcardGridHeight = 260.dp
