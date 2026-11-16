package com.singam.lionlibrary.presentation.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.cardEdge
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled

/** Window dim behind the preview; the only effect when blur is unavailable. */
private const val PREVIEW_DIM = .55f

/** Blur radius behind the preview on Android 12+, in pixels. */
private const val PREVIEW_BLUR_PX = 48

/**
 * Long-press preview for a poster: the poster lifts to the centre with a spring, the page behind is
 * blurred and dimmed (Android 12+ with cross-window blur enabled; dim only otherwise), and the
 * title's existing [actions] sit below it. Tapping the poster opens the title; tapping outside or
 * Back closes the preview. Destructive actions keep their own confirmation dialogs.
 */
@Composable
fun PosterPreview(
    media: MediaItem,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PreviewWindowEffects()
        val motion = rememberSystemMotionEnabled()
        val lift = remember { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (motion)
                lift.animateTo(
                    1f,
                    spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow),
                )
        }
        Box(
            Modifier.fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .safeDrawingPadding()
                .padding(LionLayout.SpaceXl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 360.dp).verticalScroll(rememberScrollState()).semantics {
                    paneTitle = media.title
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LionLayout.SpaceL),
            ) {
                Box(
                    Modifier.widthIn(max = 220.dp)
                        .fillMaxWidth(.62f)
                        .aspectRatio(2f / 3f)
                        .graphicsLayer {
                            val progress = lift.value
                            scaleX = .82f + .18f * progress
                            scaleY = .82f + .18f * progress
                            translationY = (1f - progress) * 48.dp.toPx()
                            alpha = progress.coerceIn(0f, 1f)
                            shadowElevation = 24.dp.toPx() * progress
                            shape = RoundedCornerShape(LionLayout.CardRadius)
                            clip = true
                        }
                        .clip(RoundedCornerShape(LionLayout.CardRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .cardEdge(LionLayout.CardRadius, LionTheme.colors)
                        .clickable(role = Role.Button, onClick = onOpen)
                ) {
                    LocalArtwork(
                        listOf(media.posterPath, media.backdropPath),
                        null,
                        Modifier.fillMaxSize(),
                        fallback = {
                            Icon(
                                Icons.Outlined.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                        },
                    )
                }
                Text(
                    media.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = LionTheme.colors.onMedia,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = lift.value.coerceIn(0f, 1f) },
                )
                Surface(
                    shape = RoundedCornerShape(LionLayout.RadiusL),
                    color = LionTheme.colors.sheet,
                    modifier =
                        Modifier.fillMaxWidth().graphicsLayer {
                            val progress = lift.value.coerceIn(0f, 1f)
                            alpha = progress
                            translationY = (1f - progress) * 24.dp.toPx()
                        },
                ) {
                    Column(Modifier.padding(vertical = LionLayout.SpaceS), content = actions)
                }
            }
        }
    }
}

/** Dim always; blur behind the window on Android 12+ when the device has cross-window blur on. */
@Composable
private fun PreviewWindowEffects() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    LaunchedEffect(window) {
        window.setDimAmount(PREVIEW_DIM)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                window.windowManager.isCrossWindowBlurEnabled
        ) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = PREVIEW_BLUR_PX }
        }
    }
}
