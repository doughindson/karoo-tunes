package com.doughindson.karootunes.widget

/**
 * The widget's three layouts. Karoo Tunes works alongside the Karoo's own media controls, which
 * handle play/pause/skip instantly, so only the full page repeats those; the smaller layouts focus
 * on what only Spotify can do (like, shuffle, library).
 */
enum class PlayerLayout(val showsArtwork: Boolean) {
    /** Like / shuffle / library in one row. */
    COMPACT(showsArtwork = false),

    /** Album art with the three buttons beside it, track and artist underneath. */
    MEDIUM(showsArtwork = true),

    /** Medium plus play/pause and skip (or podcast seek) buttons. */
    FULL_PAGE(showsArtwork = true);

    companion object {
        /** Picks the layout from the field's size on the Karoo's 60 x 60 grid (columns, rows). */
        fun forField(gridSize: Pair<Int, Int>): PlayerLayout {
            val (columns, rows) = gridSize
            return when {
                columns <= 30 || rows <= 30 -> COMPACT
                rows < 60 -> MEDIUM
                else -> FULL_PAGE
            }
        }

        /**
         * Field height as a fraction of the screen, for previewing each layout in the app. Measured on
         * a Karoo 3: a full page field is 342 of 427 dp (the ride app's bars take the rest).
         */
        fun previewHeightFraction(layout: PlayerLayout): Float = when (layout) {
            FULL_PAGE -> 0.8f
            MEDIUM -> 0.5f
            COMPACT -> 0.2f
        }
    }
}
