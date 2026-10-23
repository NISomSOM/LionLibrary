package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import java.io.File

// Dynamic: a publication recomposes only artwork readers, not the whole tree.
val LocalArtworkRevision = compositionLocalOf { 0L }

/** Tries local alternatives after decoding errors; never requires a live image URL. */
@Composable
fun LocalArtwork(
    paths: List<String?>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit = {},
) {
    val revision = LocalArtworkRevision.current
    val candidates = paths.filterNotNull().distinct()
    // A publication elsewhere must not reset a successful painter whose request is unchanged.
    val signatures =
        remember(candidates, revision) {
            candidates.map { path ->
                val file = File(path)
                "$path:${file.lastModified()}:${file.length()}"
            }
        }
    var index by remember(signatures) { mutableIntStateOf(0) }
    val path = candidates.getOrNull(index)
    val context = LocalContext.current
    val request =
        remember(path, signatures, context) {
            path?.let {
                ImageRequest.Builder(context)
                    .data(File(it))
                    .memoryCacheKey(signatures[index])
                    .diskCacheKey(signatures[index])
                    .build()
            }
        }
    var loaded by remember(request) { mutableStateOf(false) }
    Box(modifier, contentAlignment = alignment) {
        // Keep both slots stable when the fallback disappears, including transparent logos.
        Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
            if (!loaded) fallback()
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true },
                onError = { index++ },
            )
        }
    }
}

@Composable
fun ArtworkTitle(
    title: String,
    logoPath: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    LocalArtwork(listOf(logoPath), title, modifier, ContentScale.Fit) {
        Text(
            title,
            style =
                if (compact) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 1 else 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Missing artwork")
@Composable
private fun MissingArtworkPreview() {
    LionLibraryTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            ArtworkTitle("Rascal Does Not Dream of a Knapsack Kid", null)
        }
    }
}
