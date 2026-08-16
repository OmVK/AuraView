package com.arora.assistant.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.West
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlin.math.roundToInt

import com.arora.assistant.core.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

data class QuickAction(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector,
    val tint: Color,
    val category: String = "apps", // "vision", "apps", "power"
    val onClick: () -> Unit
)

@Composable
fun FloatingActionHub(
    onDismiss: () -> Unit,
    onCircleSearch: () -> Unit,
    actions: List<QuickAction>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val appPreferences = remember { AppPreferences(context) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("Apps & Tools", "Hardware & Power", "AI Vision")

    // Dynamic Free Dragging Coordinates
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dynamic Resizing State (Loaded from AppPreferences)
    var hubWidthDp by remember { mutableFloatStateOf(340f) }
    var showResizeSlider by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        hubWidthDp = appPreferences.hubWidthDp.first()
    }

    // Auto-compact determination: if width < 260dp, switch to icon-only minimal mode
    val isMinimalIconOnly = hubWidthDp < 260f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(hubWidthDp.dp)
                .heightIn(max = 540.dp)
                .shadow(28.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = SoftLavender.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SoftSurfaceElevated.copy(alpha = 0.85f),
                            SoftDarkBg.copy(alpha = 0.90f)
                        )
                    )
                )
                .border(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            SoftLavender.copy(alpha = 0.6f),
                            SoftCardBorder.copy(alpha = 0.7f),
                            SkyOpal.copy(alpha = 0.4f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Prevent closing when tapping inside the card
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Draggable Header Area (Touch & Drag Anywhere on Header to Move Window)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SoftLavender)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (!isMinimalIconOnly) {
                        Text(
                            text = "AuraView Hub",
                            color = TextPureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Drag Indicator Handle in the middle
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(SoftCardBorder.copy(alpha = 0.9f))
                        )
                    }

                    // Resize Toggle Button
                    IconButton(
                        onClick = { showResizeSlider = !showResizeSlider },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (isMinimalIconOnly) Icons.Default.ViewModule else Icons.Default.ViewAgenda,
                            "Resize Mode",
                            tint = SkyOpal,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Clean Flat Close Button (Zero circular shadow)
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }

                // Dynamic Width Resizing Slider
                AnimatedVisibility(visible = showResizeSlider) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftSurface.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Hub Width: ${hubWidthDp.toInt()}dp", color = TextOffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(if (isMinimalIconOnly) "Minimal (Icons Only)" else "Expanded (Labels)", color = if (isMinimalIconOnly) SageMint else SkyOpal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = hubWidthDp,
                            onValueChange = {
                                hubWidthDp = it
                                scope.launch { appPreferences.setHubWidthDp(it) }
                            },
                            valueRange = 210f..380f,
                            colors = SliderDefaults.colors(thumbColor = SoftLavender, activeTrackColor = SoftLavender)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hero Action: Circle to Search & Solve
                HeroCircleSearchCard(
                    isMinimal = isMinimalIconOnly,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onCircleSearch()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Segmented Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    containerColor = Color.Transparent,
                    contentColor = SoftLavender,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex]),
                            color = SoftLavender,
                            height = 2.dp
                        )
                    },
                    divider = {}
                ) {
                    categories.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedCategoryIndex == index,
                            onClick = { selectedCategoryIndex = index },
                            text = {
                                Text(
                                    text = if (isMinimalIconOnly) title.take(4) + "." else title,
                                    color = if (selectedCategoryIndex == index) TextPureWhite else TextMuted,
                                    fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filtered Actions for Selected Tab
                val filteredActions = when (selectedCategoryIndex) {
                    0 -> actions.filter { it.category == "apps" }
                    1 -> actions.filter { it.category == "power" }
                    else -> actions.filter { it.category == "vision" }
                }

                // Grid Column Count: 3 columns if minimal icon-only, 2 columns if expanded
                val columns = if (isMinimalIconOnly) 3 else 2
                val chunkedActions = filteredActions.chunked(columns)

                chunkedActions.forEach { rowActions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowActions.forEach { action ->
                            AdaptiveHubActionItem(
                                action = action,
                                isMinimal = isMinimalIconOnly,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    action.onClick()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Symmetrical Back & Home Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSurface.copy(alpha = 0.75f))
                        .border(1.dp, SoftCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                actions.firstOrNull { it.id == "back" }?.onClick()
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.West, "Back", tint = TextOffWhite, modifier = Modifier.size(15.dp))
                            if (!isMinimalIconOnly) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back", color = TextOffWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 16.dp)
                            .background(SoftCardBorder.copy(alpha = 0.8f))
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                actions.firstOrNull { it.id == "home" }?.onClick()
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, "Home", tint = TextOffWhite, modifier = Modifier.size(15.dp))
                            if (!isMinimalIconOnly) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Home", color = TextOffWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCircleSearchCard(
    isMinimal: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1.0f, label = "heroScale")

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = SoftLavender.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        SoftLavender.copy(alpha = 0.9f),
                        SkyOpal.copy(alpha = 0.9f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isMinimal) Arrangement.Center else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Adjust, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (!isMinimal) {
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Circle to Search & Solve",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Draw around math, text, or items",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AdaptiveHubActionItem(
    action: QuickAction,
    isMinimal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1.0f, label = "itemScale")

    if (isMinimal) {
        Box(
            modifier = modifier
                .scale(scale)
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface.copy(alpha = 0.7f))
                .border(1.dp, SoftCardBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                tint = action.tint,
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        Row(
            modifier = modifier
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(SoftSurface.copy(alpha = 0.7f))
                .border(1.dp, SoftCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(action.tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.title,
                    tint = action.tint,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = action.title,
                color = TextOffWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
