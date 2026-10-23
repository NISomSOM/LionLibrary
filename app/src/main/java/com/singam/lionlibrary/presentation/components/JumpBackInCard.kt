package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.JumpBackInItem

@Composable
fun JumpBackInCard(
    item: JumpBackInItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
) {
    val separateCaption = LocalDensity.current.fontScale > 1.4f
    val moreActions = stringResource(R.string.more_actions)
    val background = MaterialTheme.colorScheme.background
    val status = item.resumeCardStatus()
    Column(
        modifier
            .width(247.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = moreActions,
            )
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(moreActions) {
                            onLongClick()
                            true
                        }
                    )
            }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            LocalArtwork(
                listOf(item.thumbnailPath, item.backdropPath, item.posterPath),
                null,
                Modifier.fillMaxSize().drawWithCache {
                    val brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0f to Color.Transparent,
                                    .6f to background.copy(alpha = .70f),
                                    1f to background.copy(alpha = .95f),
                                ),
                            startY = size.height * .45f,
                            endY = size.height,
                        )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush)
                    }
                },
            )
            Surface(
                Modifier.align(Alignment.TopEnd).padding(7.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = .72f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    resumeStatusText(status),
                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }
            if (!separateCaption)
                ResumeCaption(item, Modifier.align(Alignment.BottomStart).padding(10.dp))
            item.progress
                ?.takeIf { it.isFinite() && it > 0f && !item.isNextUp }
                ?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = .30f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
        }
        if (separateCaption) ResumeCaption(item, Modifier.padding(10.dp))
    }
}

@Composable
private fun resumeStatusText(status: ResumeCardStatus): String =
    when (status) {
        ResumeCardStatus.NextUp -> stringResource(R.string.ui_next_up)
        is ResumeCardStatus.Watched ->
            stringResource(R.string.resume_percent_watched, status.percent)
        is ResumeCardStatus.Remaining ->
            if (status.minutes >= 60L)
                stringResource(
                    R.string.resume_hours_minutes_left,
                    status.minutes / 60L,
                    status.minutes % 60L,
                )
            else
                pluralStringResource(
                    R.plurals.resume_minutes_left,
                    status.minutes.toInt(),
                    status.minutes,
                )
    }

@Composable
private fun ResumeCaption(item: JumpBackInItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (item.seasonNumber != null && item.episodeNumber != null)
            Text(
                stringResource(R.string.episode_number, item.seasonNumber, item.episodeNumber),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        Text(
            item.mediaTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.episodeTitle
            ?.takeIf { it.isNotBlank() }
            ?.let { title ->
                Text(
                    title,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}
