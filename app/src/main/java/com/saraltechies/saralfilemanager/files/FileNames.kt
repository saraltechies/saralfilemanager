package com.saraltechies.saralfilemanager.files

object FileNames {

    enum class Problem { EMPTY, RESERVED, INVALID_CHARACTER, TOO_LONG }

    /** Why [name] can't be used for a new or renamed file, or null when it's fine. */
    fun problem(name: String): Problem? = when {
        name.isBlank() -> Problem.EMPTY
        name == "." || name == ".." -> Problem.RESERVED
        name.any { it == '/' || it == '\u0000' } -> Problem.INVALID_CHARACTER
        name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> Problem.TOO_LONG
        else -> null
    }

    /**
     * [name] if it's free, otherwise "name (1).ext", "name (2).ext"... until [taken] says no.
     * A folder's name is kept whole, dots included.
     */
    fun unique(name: String, isDirectory: Boolean, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = if (isDirectory) -1 else name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$extension"
            if (!taken(candidate)) return candidate
            n++
        }
    }

    private const val MAX_NAME_BYTES = 255
}
