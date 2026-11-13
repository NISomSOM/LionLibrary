package com.singam.lionlibrary.data

import android.net.Uri
import android.provider.DocumentsContract as DC
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.DeletionFixtureProvider
import com.singam.lionlibrary.data.scanner.FolderEntry
import com.singam.lionlibrary.data.scanner.FolderUnavailableException
import com.singam.lionlibrary.data.scanner.SafFolderLister
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * File addresses are the library's stored identities, so the projected lister must hand out exactly
 * what DocumentFile handed out before the rewrite, in the same order, and must fail loudly where
 * DocumentFile silently returned nothing.
 */
class SafFolderListerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = File(context.cacheDir, "deletion-documents")
    private val lister = SafFolderLister(context)

    private fun treeUri(name: String): Uri =
        DC.buildTreeDocumentUri(
            "${context.packageName}.deletion-fixtures",
            DeletionFixtureProvider.rootId(context, name),
        )

    private fun withMovies(test: (File) -> Unit) {
        val root = File(base, "Movies").apply { mkdirs() }
        try {
            test(root)
        } finally {
            // Only the explicitly created private-cache fixture root.
            check(root.canonicalFile.parentFile == base.canonicalFile)
            root.deleteRecursively()
        }
    }

    private fun describe(uri: Uri, name: String?, directory: Boolean, file: Boolean) =
        "$uri | $name | dir=$directory | file=$file"

    private fun walkDocumentFile(dir: DocumentFile, out: MutableList<String>) {
        for (child in dir.listFiles()) {
            out += describe(child.uri, child.name, child.isDirectory, child.isFile)
            if (child.isDirectory) walkDocumentFile(child, out)
        }
    }

    private fun walkLister(dir: FolderEntry<Uri>, out: MutableList<String>) {
        for (child in lister.children(dir)) {
            out += describe(child.uri, child.name, child.isDirectory, child.isFile)
            if (child.isDirectory) walkLister(child, out)
        }
    }

    @Test
    fun addressesNamesTypesAndOrderMatchDocumentFile() = withMovies { root ->
        File(root, "Film 🦁 (2001).mkv").writeText("fixture")
        File(root, "Film 🦁 (2001).srt").writeText("fixture")
        File(root, "[Group] 千と千尋 (2001) [1080p]").mkdirs()
        File(root, "[Group] 千と千尋 (2001) [1080p]/a b%20c#d.mkv").writeText("fixture")
        File(root, "Collection/Inner/Deep (1999)").mkdirs()
        File(root, "Collection/Inner/Deep (1999)/Deep (1999).mp4").writeText("fixture")
        File(root, "Empty").mkdirs()

        val uri = treeUri("Movies")
        val legacyRoot = DocumentFile.fromTreeUri(context, uri)!!
        val newRoot = lister.root(uri)
        assertEquals(legacyRoot.uri, newRoot.uri)

        val legacy = mutableListOf<String>().also { walkDocumentFile(legacyRoot, it) }
        val rewritten = mutableListOf<String>().also { walkLister(newRoot, it) }
        assertEquals(9, legacy.size)
        assertEquals(legacy, rewritten)
        assertEquals(
            "${uri.authority}:${DC.getTreeDocumentId(uri)}",
            newRoot.documentKey,
        )
    }

    @Test
    fun partialListingThrowsWhereDocumentFileAcceptsIt() = withMovies { root ->
        File(root, "untouched.mp4").writeText("keep")
        val newRoot = lister.root(treeUri("Movies"))
        try {
            DeletionFixtureProvider.loading = true
            try {
                lister.children(newRoot)
                fail("Partial listing accepted")
            } catch (_: FolderUnavailableException) {}
        } finally {
            DeletionFixtureProvider.loading = false
        }
        assertEquals(1, lister.children(newRoot).size)
    }

    @Test
    fun missingFolderThrowsWhereDocumentFileReturnsEmpty() {
        val uri = treeUri("Gone-${System.nanoTime()}")
        assertEquals(0, DocumentFile.fromTreeUri(context, uri)!!.listFiles().size)
        try {
            lister.children(lister.root(uri))
            fail("Missing folder listed as empty")
        } catch (_: FolderUnavailableException) {}
    }

    @Test
    fun emptyFolderIsAnEmptyListingNotAFailure() = withMovies { root ->
        assertTrue(root.isDirectory)
        assertTrue(lister.children(lister.root(treeUri("Movies"))).isEmpty())
    }
}
