package com.hhst.youtubelite.library

import com.hhst.youtubelite.library.presentation.libraryTileColumns
import com.hhst.youtubelite.ui.design.WindowClass
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTileColumnsTest {
    @Test fun phonesUseTwoTilesAtNormalFontScale() = assertEquals(2, libraryTileColumns(WindowClass.Compact, 1f))

    @Test fun phonesUseOneTileAtLargeFontScaleSoLabelsDoNotBreak() {
        assertEquals(1, libraryTileColumns(WindowClass.Compact, 1.5f))
        assertEquals(1, libraryTileColumns(WindowClass.Compact, 2f))
    }

    @Test fun widerWindowsKeepThreeTiles() {
        assertEquals(3, libraryTileColumns(WindowClass.Medium, 2f))
        assertEquals(3, libraryTileColumns(WindowClass.Expanded, 1f))
    }
}
