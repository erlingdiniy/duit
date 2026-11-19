package com.diniy.duit.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diniy.duit.core.ui.components.CandyBubbleCard
import com.diniy.duit.core.ui.components.CandyIconBlob
import com.diniy.duit.core.ui.components.CandyTone
import com.diniy.duit.core.ui.components.PrismaticBackground
import com.diniy.duit.core.ui.components.candyPressable
import com.diniy.duit.core.ui.components.rememberDuitHaptic
import com.diniy.duit.core.ui.components.rememberSpatialScreenPadding
import com.diniy.duit.core.ui.theme.CandyDisplayFamily
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.feature.onboarding.FeatureGuideContent
import com.diniy.duit.feature.onboarding.FeatureGuideSection

/**
 * Getting Started & Feature Guide screen.
 * Permanent documentation of all 12 duit. financial capabilities with step-by-step instructions.
 */
@Composable
fun FeatureGuideScreen(
    onBack: () -> Unit,
    onReplayTour: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography
    val haptic = rememberDuitHaptic()
    val screenPadding = rememberSpatialScreenPadding(hasFloatingBottomBar = false)
    val sections = FeatureGuideContent.sections

    var expandedSectionId by rememberSaveable { mutableStateOf<Int?>(1) }

    BackHandler(onBack = onBack)

    PrismaticBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.primaryText
                        )
                    }
                    Column {
                        Text(
                            text = "Feature Guide",
                            style = typography.screenHeading.copy(fontFamily = CandyDisplayFamily),
                            color = colors.primaryText,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Learn how to use duit.",
                            style = typography.caption,
                            color = colors.secondaryText
                        )
                    }
                }
            }

            // Replay Welcome Tour Action Card
            item(key = "replay_tour_card") {
                CandyBubbleCard(
                    tone = CandyTone.Coral,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .candyPressable(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onReplayTour()
                                },
                                onClickLabel = "Replay Welcome Tour"
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.floatingNavSurface)
                                .border(BorderStroke(2.dp, colors.sticker), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = colors.coral,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replay Welcome Tour",
                                style = typography.cardTitle.copy(
                                    fontFamily = CandyDisplayFamily,
                                    fontSize = 17.sp
                                ),
                                color = colors.primaryText
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Review the 5-screen interactive intro without touching your data.",
                                style = typography.supportingInfo.copy(fontSize = 13.sp),
                                color = colors.secondaryText
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.secondaryText
                        )
                    }
                }
            }

            // Sections Header
            item(key = "sections_header") {
                Text(
                    text = "App Features (${sections.size})",
                    style = typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // 12 Feature Sections
            items(sections, key = { it.id }) { section ->
                val isExpanded = expandedSectionId == section.id

                FeatureGuideSectionCard(
                    section = section,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        expandedSectionId = if (isExpanded) null else section.id
                    }
                )
            }
        }
    }
}

@Composable
private fun FeatureGuideSectionCard(
    section: FeatureGuideSection,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpatialTheme.colors
    val typography = SpatialTheme.typography

    CandyBubbleCard(
        tone = section.tone,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (isExpanded) "Collapse ${section.title}" else "Expand ${section.title}",
                    onClick = onToggleExpand
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CandyIconBlob(
                    icon = section.icon,
                    tone = section.tone,
                    size = 42.dp,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${section.id}. ${section.title}",
                        style = typography.cardTitle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.primaryText
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.primaryText
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // What it does
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.floatingNavSurface.copy(alpha = 0.65f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "WHAT IT DOES",
                            style = typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = section.whatItDoes,
                            style = typography.supportingInfo.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = colors.primaryText
                        )
                    }

                    // Why it is useful
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.floatingNavSurface.copy(alpha = 0.65f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "WHY IT IS USEFUL",
                            style = typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.secondaryText
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = section.whyItIsUseful,
                            style = typography.supportingInfo.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = colors.primaryText
                        )
                    }

                    // How to use it (2-4 steps)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.floatingNavSurface.copy(alpha = 0.65f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "HOW TO USE IT",
                            style = typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.secondaryText
                        )
                        section.steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.coral),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = typography.caption.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.onCoral
                                    )
                                }
                                Text(
                                    text = step,
                                    style = typography.supportingInfo.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
