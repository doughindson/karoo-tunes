package com.doughindson.karootunes.widget

/** The four widget layouts, from a single row of buttons up to a full page with album art. */
enum class PlayerLayout(val showsArtwork: Boolean) {
    SINGLE_FIELD(showsArtwork = false),
    SMALL(showsArtwork = false),
    MEDIUM(showsArtwork = false),
    FULL_PAGE(showsArtwork = true);

    companion object {
        /** Below this height the Small layout's text and button row don't both fit. */
        const val SMALL_MIN_HEIGHT_DP = 180f

        /**
         * Picks the layout for a data field. [gridSize] is the Karoo's (columns, rows) out of 60x60;
         * [heightDp] is the field's real height, which can be smaller than the grid suggests.
         */
        fun forField(gridSize: Pair<Int, Int>, heightDp: Float): PlayerLayout {
            val (columns, rows) = gridSize
            return when {
                columns <= 30 -> SINGLE_FIELD
                rows <= 30 -> if (heightDp >= SMALL_MIN_HEIGHT_DP) SMALL else SINGLE_FIELD
                rows < 60 -> MEDIUM
                else -> FULL_PAGE
            }
        }

        /** Field height (as a fraction of the screen) used to preview each layout in the app. */
        fun previewHeightFraction(layout: PlayerLayout): Float = when (layout) {
            FULL_PAGE -> 1f
            MEDIUM -> 0.45f
            SMALL -> 0.3f
            SINGLE_FIELD -> 0.15f
        }
    }
}
