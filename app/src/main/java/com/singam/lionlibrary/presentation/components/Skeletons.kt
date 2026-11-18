package com.singam.lionlibrary.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled

/** Shimmer phase 0..1 shared by every placeholder under one [ShimmerHost]. */
private val LocalShimmerPhase = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Provides one shimmer clock for all placeholders below it. The clock runs only while the screen is
 * resumed and system animations are on; otherwise placeholders are static blocks.
 */
@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val running = rememberSystemMotionEnabled() && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val phase =
        if (running) {
            val transition = rememberInfiniteTransition(label = "shimmer")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(LionMotion.Slow * 2, easing = LinearEasing),
                        RepeatMode.Restart,
                    ),
                label = "shimmerPhase",
            )
        } else remember { mutableFloatStateOf(0f) }
    CompositionLocalProvider(LocalShimmerPhase provides phase, content = content)
}

/**
 * A placeholder block: base surface colour with a soft highlight sweeping across (read in draw).
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LionLayout.CardRadius),
) {
    val phase = LocalShimmerPhase.current
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    Box(
        modifier.clip(shape).drawBehind {
            drawRect(base)
            val width = size.width
            val x = -width + phase.value * width * 3f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + width, size.height),
                )
            )
        }
    )
}

@Composable
private fun SkeletonLine(width: Dp, modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.width(width).height(14.dp), RoundedCornerShape(LionLayout.RadiusXs))
}

@Composable
private fun SkeletonPosterShelf(modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = LionLayout.SectionGap)) {
        SkeletonLine(140.dp, Modifier.padding(horizontal = LionLayout.Gutter))
        Spacer(Modifier.height(LionLayout.SpaceM))
        Row(
            Modifier.padding(horizontal = LionLayout.Gutter),
            horizontalArrangement = Arrangement.spacedBy(LionLayout.ItemGap),
        ) {
            repeat(4) {
                SkeletonBlock(Modifier.width(LionLayout.posterCardWidth).aspectRatio(2f / 3f))
            }
        }
    }
}

/** Home while the library loads: hero block and two poster shelves. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading_content)
    ShimmerHost {
        Column(
            modifier.fillMaxSize().testTag("home-skeleton").semantics {
                contentDescription = description
            }
        ) {
            SkeletonBlock(
                Modifier.fillMaxWidth().aspectRatio(.9f),
                RoundedCornerShape(0.dp),
            )
            Spacer(Modifier.height(LionLayout.SectionGap))
            SkeletonPosterShelf()
            SkeletonPosterShelf()
        }
    }
}

/** Library grid while the first results for a query load. */
@Composable
fun LibrarySkeleton(minimumCardWidth: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading_content)
    ShimmerHost {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minimumCardWidth),
            modifier =
                modifier.testTag("library-skeleton").semantics {
                    contentDescription = description
                },
            userScrollEnabled = false,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(LionLayout.ItemGap),
            verticalArrangement = Arrangement.spacedBy(LionLayout.ItemGap),
        ) {
            items(12) {
                Column {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    Spacer(Modifier.height(LionLayout.CaptionGap))
                    SkeletonLine(72.dp)
                }
            }
        }
    }
}

/** Details while its row loads: hero, title lines and a few episode rows. */
@Composable
fun DetailsSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading_content)
    ShimmerHost {
        Column(
            modifier.fillMaxSize().testTag("details-skeleton").semantics {
                contentDescription = description
            }
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(.8f), RoundedCornerShape(0.dp))
            Column(
                Modifier.padding(LionLayout.DetailGutter),
                verticalArrangement = Arrangement.spacedBy(LionLayout.SpaceM),
            ) {
                SkeletonLine(220.dp)
                SkeletonLine(160.dp)
                SkeletonBlock(
                    Modifier.fillMaxWidth().height(48.dp),
                    RoundedCornerShape(LionLayout.RadiusL),
                )
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(LionLayout.SpaceM)) {
                        SkeletonBlock(Modifier.width(96.dp).aspectRatio(16f / 9f))
                        Column(verticalArrangement = Arrangement.spacedBy(LionLayout.SpaceS)) {
                            SkeletonLine(150.dp)
                            SkeletonLine(90.dp)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Home skeleton", heightDp = 720)
@Composable
private fun HomeSkeletonPreview() {
    LionLibraryTheme { HomeSkeleton() }
}

@Preview(name = "Library skeleton", heightDp = 640)
@Composable
private fun LibrarySkeletonPreview() {
    LionLibraryTheme { LibrarySkeleton(104.dp) }
}

@Preview(name = "Details skeleton, large font", heightDp = 720, fontScale = 2f)
@Composable
private fun DetailsSkeletonPreview() {
    LionLibraryTheme { DetailsSkeleton() }
}
