package com.saraltechies.saralfilemanager.files

import org.junit.Assert.assertEquals
import org.junit.Test

class SizesAndCategoriesTest {

    @Test fun formatsSizes() {
        assertEquals("0 B", Sizes.format(0))
        assertEquals("1023 B", Sizes.format(1023))
        assertEquals("1.0 KB", Sizes.format(1024))
        assertEquals("1.5 MB", Sizes.format(1536L * 1024))
        assertEquals("120 GB", Sizes.format(120L * 1024 * 1024 * 1024))
    }

    @Test fun categoriesComeFromTheExtension() {
        assertEquals(FileCategory.IMAGE, FileCategory.of("IMG_001.JPG"))
        assertEquals(FileCategory.VIDEO, FileCategory.of("clip.mp4"))
        assertEquals(FileCategory.AUDIO, FileCategory.of("song.mp3"))
        assertEquals(FileCategory.DOCUMENT, FileCategory.of("notes.pdf"))
        assertEquals(FileCategory.ARCHIVE, FileCategory.of("backup.zip"))
        assertEquals(FileCategory.APK, FileCategory.of("app.apk"))
        assertEquals(FileCategory.OTHER, FileCategory.of("README"))
        assertEquals(FileCategory.OTHER, FileCategory.of("data.bin"))
    }

    @Test fun everyExtensionBelongsToOneCategory() {
        val all = FileCategory.entries.flatMap { it.extensions }
        assertEquals(all.size, all.toSet().size)
    }
}
