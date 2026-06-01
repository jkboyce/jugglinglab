//
// WalkthroughOverlay.kt
//
// Standalone UI overlay component for the mobile onboarding walkthrough.
// Renders spotlights, touch blocker areas, tooltips, and handles auto-advancements.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WalkthroughOverlay(
    walkthroughCoordinator: WalkthroughCoordinator,
    currentRoute: String,
    patternListTitle: String?,
    hasLoadedPatternList: Boolean,
    maxW: Dp,
    maxH: Dp,
    modifier: Modifier = Modifier
) {
    // Walkthrough auto-advancements observer effects
    LaunchedEffect(currentRoute, walkthroughCoordinator.walkthroughStep) {
        if (walkthroughCoordinator.walkthroughStep == 14 && currentRoute == "PatternList") {
            walkthroughCoordinator.walkthroughStep = 15
        }
    }

    LaunchedEffect(currentRoute, patternListTitle, walkthroughCoordinator.walkthroughStep) {
        if (walkthroughCoordinator.walkthroughStep == 16 && currentRoute == "PatternList" && patternListTitle == "How to Juggle") {
            walkthroughCoordinator.walkthroughStep = 17
        }
    }

    LaunchedEffect(currentRoute, hasLoadedPatternList, walkthroughCoordinator.walkthroughStep) {
        if (walkthroughCoordinator.walkthroughStep == 18 && currentRoute == "PatternList" && !hasLoadedPatternList) {
            walkthroughCoordinator.walkthroughStep = 19
        }
    }

    if (walkthroughCoordinator.walkthroughStep in 1..19) {
        val activeStepData = walkthroughCoordinator.activeStepData
        if (activeStepData != null) {
            val highlightKey = activeStepData.first
            val tooltipText = activeStepData.second
            val rawHighlightRect = walkthroughCoordinator.walkthroughPositions[highlightKey]
            val rBounds = walkthroughCoordinator.rootBounds
            val highlightRect = if (rawHighlightRect != null && rBounds != null) {
                val localLeft = rawHighlightRect.left - rBounds.left
                val localTop = rawHighlightRect.top - rBounds.top
                val localWidth = rawHighlightRect.width
                val localHeight = rawHighlightRect.height
                if (highlightKey == "anim_center" || highlightKey == "ladder_center" || highlightKey == "info_favorites" || highlightKey == "info_pattern_list") {
                    Rect(
                        left = localLeft,
                        top = localTop,
                        right = localLeft + localWidth,
                        bottom = localTop + localHeight
                    )
                } else {
                    val deltaW = localWidth * 0.25f
                    val deltaH = localHeight * 0.25f
                    Rect(
                        left = localLeft - deltaW,
                        top = localTop - deltaH,
                        right = localLeft + localWidth + deltaW,
                        bottom = localTop + localHeight + deltaH
                    )
                }
            } else {
                rawHighlightRect
            }
            val isInteractive = walkthroughCoordinator.walkthroughStep in listOf(6, 9, 14, 16, 18)
            val density = LocalDensity.current
            val densityVal = density.density

            Box(modifier = modifier.fillMaxSize()) {
                // Spotlight draw & border
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("DEPRECATION")
                    with(drawContext.canvas.nativeCanvas) {
                        val checkpoint = saveLayer(null, null)

                        drawRect(
                            color = Color.Black.copy(alpha = 0.45f),
                            size = size
                        )

                        if (highlightRect != null) {
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(highlightRect.left, highlightRect.top),
                                size = Size(highlightRect.width, highlightRect.height),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                blendMode = BlendMode.Clear
                            )
                        }

                        restoreToCount(checkpoint)
                    }

                    if (highlightRect != null) {
                        drawRoundRect(
                            color = Color(0xFFFFD700), // Premium gold
                            topLeft = Offset(highlightRect.left, highlightRect.top),
                            size = Size(highlightRect.width, highlightRect.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = 2.2.dp.toPx())
                        )
                    }
                }

                // Touch blocker overlays
                if (isInteractive && highlightRect != null) {
                    val topDp =
                        with(density) { highlightRect.top.toDp() }.coerceIn(0.dp, maxH)
                    val bottomDp =
                        with(density) { highlightRect.bottom.toDp() }.coerceIn(
                            0.dp,
                            maxH
                        )
                    val leftDp =
                        with(density) { highlightRect.left.toDp() }.coerceIn(0.dp, maxW)
                    val rightDp = with(density) { highlightRect.right.toDp() }.coerceIn(
                        0.dp,
                        maxW
                    )
                    val bottomPaddingDp = with(density) {
                        (maxH.value * densityVal - highlightRect.bottom).coerceAtLeast(
                            0f
                        ).toDp()
                    }.coerceIn(0.dp, maxH)

                    // Top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topDp)
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) { detectTapGestures {} }
                    )
                    // Bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = bottomDp)
                            .fillMaxHeight()
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) { detectTapGestures {} }
                    )
                    // Left
                    Box(
                        modifier = Modifier
                            .padding(
                                top = topDp,
                                bottom = bottomPaddingDp
                            )
                            .width(leftDp)
                            .fillMaxHeight()
                            .align(Alignment.TopStart)
                            .pointerInput(Unit) { detectTapGestures {} }
                    )
                    // Right
                    Box(
                        modifier = Modifier
                            .padding(
                                top = topDp,
                                bottom = bottomPaddingDp,
                                start = rightDp
                            )
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .align(Alignment.TopStart)
                            .pointerInput(Unit) { detectTapGestures {} }
                    )
                } else if (!isInteractive) {
                    val step = walkthroughCoordinator.walkthroughStep
                    val isTapToAdvanceStep = step in listOf(1, 5, 13)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(step, highlightRect) {
                                detectTapGestures { offset ->
                                    if (isTapToAdvanceStep && highlightRect != null && highlightRect.contains(offset)) {
                                        walkthroughCoordinator.handleOkClick()
                                    }
                                }
                            }
                    )
                }

                val onOkClick = { walkthroughCoordinator.handleOkClick() }
                val onSkipClick = { walkthroughCoordinator.handleSkipClick() }

                val alignMode = if (highlightRect == null) {
                    Alignment.Center
                } else if (highlightRect.top > (maxH.value * densityVal) / 2) {
                    Alignment.TopCenter
                } else {
                    Alignment.BottomCenter
                }

                // Tooltip Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.95f
                        )
                    ),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(0.92f)
                        .align(alignMode),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOUR: STEP ${walkthroughCoordinator.walkthroughStep} OF 19",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = onSkipClick,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Exit Tour")
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = tooltipText,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val showOkButton = !isInteractive && walkthroughCoordinator.walkthroughStep !in listOf(1, 5, 13)
                        if (showOkButton) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onOkClick,
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(if (walkthroughCoordinator.walkthroughStep == 19) "Finish" else "OK")
                            }
                        }
                    }
                }
            }
        }
    }
}
