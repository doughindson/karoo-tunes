package com.doughindson.karootunes.widget

/**
 * The widget's three layouts. Karoo Tunes works alongside the Karoo's own media controls, which
 * handle play/pause/skip instantly, so only the full page repeats those; the smaller layouts focus
 * on what only Spotify can do (like, shuffle, repeat, library).
 */
enum class PlayerLayout(val showsArtwork: Boolean) {
    /** Like / shuffle / repeat / library in one row. */
    COMPACT(showsArtwork = false),

    /** Album art with the four buttons beside it, track and artist underneath. */
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

        /** Field height (as a fraction of the screen) used to preview each layout in the app. */
        fun previewHeightFraction(layout: PlayerLayout): Float = when (layout) {
            FULL_PAGE -> 1f
            MEDIUM -> 0.5f
            COMPACT -> 0.15f
        }
    }
}
