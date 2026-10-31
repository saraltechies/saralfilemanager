package com.saraltechies.saralfilemanager.files

enum class SortBy { NAME, DATE, SIZE, TYPE }

object FileSort {
    /** Folders first, then [by]; ties are broken by name so the order never jumps around. */
    fun comparator(by: SortBy, descending: Boolean): Comparator<FileItem> {
        val byName = compareBy(NaturalOrder) { item: FileItem -> item.name }
        val primary: Comparator<FileItem> = when (by) {
            SortBy.NAME -> byName
            SortBy.DATE -> compareBy<FileItem> { it.lastModified }.then(byName)
            // Folders have no size of their own; their item count stands in for it.
            SortBy.SIZE -> compareBy<FileItem> { if (it.isDirectory) it.childCount.toLong() else it.size }.then(byName)
            SortBy.TYPE -> compareBy<FileItem> { it.extension }.then(byName)
        }
        val ordered = if (descending) primary.reversed() else primary
        return compareBy<FileItem> { !it.isDirectory }.then(ordered)
    }
}

/** Case-insensitive, with runs of digits compared as numbers: "Photo 2" comes before "Photo 10". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
                val startA = i
                while (i < a.length && a[i].isAsciiDigit()) i++
                val startB = j
                while (j < b.length && b[j].isAsciiDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val byDigits = numA.compareTo(numB)
                if (byDigits != 0) return byDigits
            } else {
                val byChar = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        val byRest = (a.length - i).compareTo(b.length - j)
        return if (byRest != 0) byRest else a.compareTo(b)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
