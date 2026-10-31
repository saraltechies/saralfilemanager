package com.saraltechies.saralfilemanager.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSortTest {

    private fun file(name: String, size: Long = 0, modified: Long = 0) =
        FileItem("/x/$name", name, isDirectory = false, size = size, lastModified = modified)

    private fun folder(name: String, children: Int = 0, modified: Long = 0) =
        FileItem("/x/$name", name, isDirectory = true, size = 0, lastModified = modified, childCount = children)

    private fun List<FileItem>.names() = map { it.name }

    @Test fun foldersComeFirstInEveryOrder() {
        val items = listOf(file("a.txt"), folder("z"), file("b.txt"), folder("m"))
        for (by in SortBy.entries) {
            for (descending in listOf(false, true)) {
                val sorted = items.sortedWith(FileSort.comparator(by, descending))
                assertTrue("$by $descending", sorted.take(2).all { it.isDirectory })
            }
        }
    }

    @Test fun namesSortNaturallyAndIgnoreCase() {
        val sorted = listOf(file("Photo 10.jpg"), file("photo 2.jpg"), file("Photo 1.jpg"), file("apple.txt"))
            .sortedWith(FileSort.comparator(SortBy.NAME, descending = false))
        assertEquals(listOf("apple.txt", "Photo 1.jpg", "photo 2.jpg", "Photo 10.jpg"), sorted.names())
    }

    @Test fun descendingReversesWithinFoldersAndFiles() {
        val sorted = listOf(file("a"), file("b"), folder("x"), folder("y"))
            .sortedWith(FileSort.comparator(SortBy.NAME, descending = true))
        assertEquals(listOf("y", "x", "b", "a"), sorted.names())
    }

    @Test fun sizeAndDateSortSmallestAndOldestFirst() {
        val items = listOf(file("big", size = 900, modified = 1), file("small", size = 10, modified = 3), file("mid", size = 50, modified = 2))
        assertEquals(listOf("small", "mid", "big"), items.sortedWith(FileSort.comparator(SortBy.SIZE, false)).names())
        assertEquals(listOf("big", "mid", "small"), items.sortedWith(FileSort.comparator(SortBy.DATE, false)).names())
    }

    @Test fun typeSortGroupsExtensionsThenNames() {
        val items = listOf(file("b.pdf"), file("a.zip"), file("a.pdf"), file("c.jpg"))
        assertEquals(listOf("c.jpg", "a.pdf", "b.pdf", "a.zip"), items.sortedWith(FileSort.comparator(SortBy.TYPE, false)).names())
    }

    @Test fun naturalOrderHandlesLeadingZerosAndPrefixes() {
        assertTrue(NaturalOrder.compare("file2", "file10") < 0)
        assertTrue(NaturalOrder.compare("file", "file1") < 0)
        assertTrue(NaturalOrder.compare("a", "B") < 0)
        assertEquals(0, NaturalOrder.compare("same", "same"))
        // Equal as numbers, but still a stable, non-zero order.
        assertTrue(NaturalOrder.compare("img007", "img7") != 0)
    }
}
