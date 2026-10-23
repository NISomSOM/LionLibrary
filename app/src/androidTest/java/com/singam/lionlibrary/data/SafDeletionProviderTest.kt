package com.singam.lionlibrary.data

import android.provider.DocumentsContract as DC
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.DeletionFixtureProvider
import com.singam.lionlibrary.data.storage.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SafDeletionProviderTest {
    @Test
    fun loadingProviderCursorIsNotTreatedAsEmptyStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "deletion-documents/Movies").apply { mkdirs() }
        val file = File(root, "untouched.mp4").apply { writeText("keep") }
        val id = DeletionFixtureProvider.rootId(context, "Movies")
        val uri = DC.buildTreeDocumentUri("${context.packageName}.deletion-fixtures", id).toString()
        try {
            DeletionFixtureProvider.loading = true
            try {
                SafLibraryDocuments(context).inventory(uri)
                fail("Partial inventory accepted")
            } catch (_: java.io.IOException) {}
            assertTrue(file.exists())
        } finally {
            DeletionFixtureProvider.loading = false
            check(
                root.canonicalFile.parentFile ==
                    File(context.cacheDir, "deletion-documents").canonicalFile
            )
            root.deleteRecursively()
        }
    }

    @Test
    fun opaqueSafInventoryAndNativeDeleteStayInsideExactSyntheticDocument() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(context.cacheDir, "deletion-documents")
        val root = File(base, "Movies").apply { mkdirs() }
        val target = File(root, "Film 🦁.mp4").apply { writeText("fixture") }
        val sibling = File(root, "Film 🦁-other.mp4").apply { writeText("keep") }
        val folder = File(root, "Folder").apply { mkdirs() }
        File(folder, "episode.mkv").writeText("fixture")
        try {
            val rootId = DeletionFixtureProvider.rootId(context, "Movies")
            val uri =
                DC.buildTreeDocumentUri("${context.packageName}.deletion-fixtures", rootId)
                    .toString()
            val source = SafLibraryDocuments(context)
            val tree = source.inventory(uri)
            val film = tree.nodes.values.single { it.name == target.name }
            assertFalse(film.id.contains(target.name))
            assertTrue(source.delete(uri, film.id))
            assertFalse(target.exists())
            assertTrue(sibling.exists())
            assertTrue(folder.exists())
            assertEquals(DocumentPresence.MISSING, source.presence(uri, film.id))
            assertFalse(source.delete(uri, rootId))
            val dir = tree.nodes.values.single { it.name == folder.name }
            assertTrue(source.delete(uri, dir.id))
            assertFalse(folder.exists())
            assertTrue(sibling.exists())
            assertTrue(root.exists())
        } finally {
            // Only the explicitly created private-cache fixture root.
            check(root.canonicalFile.parentFile == base.canonicalFile)
            root.deleteRecursively()
        }
    }
}
