package com.diniy.duit.feature.onboarding

import androidx.compose.ui.semantics.role
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.candyTone
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberReducedMotionEnabled
import com.diniy.duit.core.ui.theme.CandyDisplayFamily
import com.diniy.duit.core.ui.theme.SpatialTheme
import kotlinx.coroutines.launch

/**
 * Candy Bubble 2.0 Welcome Onboarding & Feature Tour screen.
 * 5 short interactive slides introducing duit.'s offline-first finance philosophy.
 */
@Composable
fun OnboardingScreen(
    onFinishTour: () -> Unit,
    onProceedToSetup: () -> Unit,
    modifier: Modifier = Modifier,
    isReplayTour: Boolean = false
) {
    val slides = OnboardingContent.slides
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val haptic = rememberDuitHaptic()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0 || isReplayTour) {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else if (isReplayTour) {
            onFinishTour()
        }
    }

    PrismaticBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top Navigation & Skip Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.floatingNavSurface)
                            .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(14.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Previous page"
                            ) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (isReplayTour) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.floatingNavSurface)
                            .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(14.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Close tour"
                            ) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onFinishTour()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close tour",
                            tint = colors.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(44.dp))
                }

                // Title badge if replaying from settings
                if (isReplayTour) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.aqua.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Feature Tour",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }
                }

                // Skip button
                if (!isLastPage) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = if (isReplayTour) "Close tour" else "Skip to setup"
                            ) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                if (isReplayTour) {
                                    onFinishTour()
                                } else {
                                    onProceedToSetup()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isReplayTour) "Close" else "Skip",
                            style = typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.secondaryText
                            )
                        )
                    }
                } else {
                    Spacer(Modifier.width(44.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Slides Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val slide = slides[page]
                OnboardingSlidePage(
                    slide = slide,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(16.dp))

            // Bottom Actions & Progress Dots
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Liquid Bubble Progress Dots
                OnboardingProgressIndicator(
                    totalPages = slides.size,
                    currentPage = pagerState.currentPage,
                    onDotClick = { targetPage ->
                        coroutineScope.launch {
                            haptic(HapticFeedbackType.TextHandleMove)
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                )

                // Primary Action Button (Next / Get Started / Done)
                val buttonText = when {
                    isLastPage && isReplayTour -> "Done"
                    isLastPage -> "Get Started"
                    else -> "Continue"
                }

                val primaryShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .drawBehind {
                            val o = 3.dp.toPx()
                            val outline = primaryShape.createOutline(size, layoutDirection, this)
                            translate(left = 0f, top = o) { drawOutline(outline, colors.sticker) }
                        }
                        .clip(primaryShape)
                        .background(colors.coral)
                        .border(BorderStroke(2.dp, colors.sticker), primaryShape)
                        .candyPressable(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                if (isLastPage) {
                                    if (isReplayTour) onFinishTour() else onProceedToSetup()
                                } else {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            onClickLabel = buttonText
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = buttonText
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = buttonText,
                            style = typography.buttonLabel.copy(
                                fontFamily = CandyDisplayFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.onCoral
                        )
                        Icon(
                            imageVector = if (isLastPage) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colors.onCoral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingSlidePage(
    slide: OnboardingSlide,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Cute Hero Icon Bubble
        Box(
            modifier = Modifier
                .size(88.dp)
                .rotate(-4f),
            contentAlignment = Alignment.Center
        ) {
            CandyIconBlob(
                icon = slide.heroIcon,
                tone = slide.heroTone,
                size = 84.dp,
            )
        }

        // Title and Subtitle Lockup
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = slide.title,
                style = typography.screenHeading.copy(
                    fontFamily = CandyDisplayFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.primaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = slide.subtitle,
                style = typography.supportingInfo.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.secondaryText,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(4.dp))

        // Feature Bullets Bubble Card
        CandyBubbleCard(
            tone = CandyTone.Neutral,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                slide.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.floatingNavSurface)
                                .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = colors.coral,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = typography.cardTitle.copy(fontSize = 15.sp),
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.description,
                                style = typography.supportingInfo.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgressIndicator(
    totalPages: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val reducedMotion = rememberReducedMotionEnabled()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.floatingNavSurface.copy(alpha = 0.8f))
            .border(BorderStroke(1.5.dp, colors.sticker), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val dotWidth by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = if (reducedMotion) spring() else spring(dampingRatio = 0.8f, stiffness = 400f),
                label = "onboarding_dot_width"
            )

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.coral else colors.secondaryText.copy(alpha = 0.4f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Slide ${index + 1} of $totalPages"
                    ) {
                        onDotClick(index)
                    }
                    .semantics {
                        contentDescription = "Slide ${index + 1} of $totalPages"
                    }
            )
        }
    }
}
