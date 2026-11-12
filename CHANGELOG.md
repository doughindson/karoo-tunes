# Changelog

The first `## ` section below is published as the release notes in `manifest.json`.

## Unreleased

* You no longer get logged out after a short loss of connection during a ride, and the player keeps showing the last track while offline
* Liked Songs, playlists and podcast lists now load past their first page
* Playlist track lists use Spotify's current API (only playlists you own or collaborate on can be listed; any can be played)
* New layouts designed to sit alongside the Karoo's own media controls: compact fields show like, shuffle and library; medium fields add album art and the current track; the full page adds play/pause and skip
* Podcasts: skip back 15 s / forward 30 s buttons on the full page while an episode plays
* Liking a song likes the one actually playing, even right after skipping with the Karoo's controls
* The library now includes the play queue
* Redesigned app screens in Spotify's style for day and night mode: readable text everywhere (library names were black on black), green play buttons and filter chips, larger touch targets, and a long-press menu to queue or like a song
* Play all your Liked Songs from the library
* Removed the progress bar, which kept the Karoo busy redrawing
* The player refreshes every 10 s while on screen (was 25 s), so skips made with the Karoo's controls show up sooner
* Removed the repeat button
* The player follows the Karoo's day and night themes
* Faster, smoother player: it only redraws when something visible changes
* Logging in survives the app being closed while the browser is open
* Removed standalone mode (controlling a Spotify app sideloaded on the Karoo)
* Rebuilt on a current toolchain (Gradle 9, AGP 9, Kotlin 2.4)

## 1.0

* Cached playlists, library items etc no longer expire after one week
* Reduced refresh interval to 25s in remote control mode if the player is only refreshed when it is on screen
