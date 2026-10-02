package com.mobplayer.tv.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobplayer.tv.ui.focus.LocalBrowseFocus
import com.mobplayer.tv.ui.theme.TvColors

private val YouTubeRed = Color(0xFFFF0000)

/** YouTube search field with recent-search chips and load status, shown on the Search tab. */
@Composable
fun TvYouTubeSearchBar(
    query: String,
    recentSearches: List<String>,
    isLoading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClearRecents: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    val fieldInteraction = remember { MutableInteractionSource() }

    // TV pattern: the field stays read-only (no keyboard) while D-pad focus passes over it, and
    // only becomes editable on OK/Enter or a tap. Compose 1.6 text fields open the IME on any
    // focus gain, which would trap remote D-pad presses inside the on-screen keyboard.
    var isEditing by remember { mutableStateOf(false) }
    // True while we bounce focus to start the input session; that blur must not end editing.
    val isRefocusing = remember { booleanArrayOf(false) }

    // Land on the field whenever the Search tab opens (also back from the player), so the D-pad
    // has a predictable start point. It's read-only until OK, so no keyboard pops up.
    // Skip when returning from the player: the played result card takes focus back instead.
    val browseFocus = LocalBrowseFocus.current
    LaunchedEffect(Unit) { if (!browseFocus.isRestorePending) runCatching { fieldFocus.requestFocus() } }

    // The input session only starts on a focus gain, so re-focus once the field becomes editable.
    LaunchedEffect(isEditing) {
        if (isEditing) runCatching {
            isRefocusing[0] = true
            try {
                focusManager.clearFocus()
                fieldFocus.requestFocus()
            } finally {
                isRefocusing[0] = false
            }
            keyboard?.show()
        }
    }
    LaunchedEffect(fieldInteraction) {
        fieldInteraction.interactions.collect { if (it is PressInteraction.Release) isEditing = true }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search YouTube", color = TvColors.TextTertiary, fontSize = 18.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TvColors.TextSecondary) },
            trailingIcon = {
                when {
                    isLoading -> CircularProgressIndicator(
                        color = YouTubeRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TvColors.TextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSubmit(query)
                keyboard?.hide()
                isEditing = false
            }),
            readOnly = !isEditing,
            interactionSource = fieldInteraction,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedContainerColor = TvColors.SurfaceElevated,
                unfocusedContainerColor = TvColors.SurfaceDark,
                cursorColor = YouTubeRed
            ),
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onFocusChanged { if (!it.hasFocus && isEditing && !isRefocusing[0]) isEditing = false }
                .onPreviewKeyEvent { event ->
                    if (isEditing) return@onPreviewKeyEvent false
                    val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    // Compose 1.6 text fields eat arrows (cursor moves) even when read-only,
                    // trapping D-pad focus; hand them to focus navigation instead.
                    val direction = when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        else -> null
                    }
                    when {
                        isOk -> {
                            if (event.type == KeyEventType.KeyUp) isEditing = true
                            true // consume both down and up so the field doesn't act on them
                        }
                        direction != null -> {
                            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                            true
                        }
                        else -> false
                    }
                }
        )

        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent searches", color = TvColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(recentSearches, key = { "recent_$it" }) { recent ->
                    TvNavTab(title = recent, isSelected = false, onClick = { onSubmit(recent) })
                }
                item(key = "clear_recents") {
                    TvNavTab(title = "Clear", isSelected = false, onClick = onClearRecents)
                }
            }
        }

        if (error != null && !isLoading) {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = error, color = TvColors.TextSecondary, fontSize = 14.sp)
                TvNavTab(title = "Retry", isSelected = false, onClick = onRetry)
            }
        }
    }
}
