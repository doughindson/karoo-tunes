# Changelog

The first `## ` section below is published as the release notes in `manifest.json`.

## Unreleased

* You no longer get logged out after a short loss of connection during a ride, and the player keeps showing the last track while offline
* Liked Songs, playlists and podcast lists now load past their first page
* Playlist track lists use Spotify's current API (only playlists you own or collaborate on can be listed; any can be played)
* Podcasts: skip back 15 s / forward 30 s buttons while an episode plays
* Repeat is back, in the options next to like and shuffle; the library now includes the play queue
* The player follows the Karoo's day and night themes
* Faster, smoother player: it only redraws when something visible changes
* Logging in survives the app being closed while the browser is open
* Removed standalone mode (controlling a Spotify app sideloaded on the Karoo)
* Rebuilt on a current toolchain (Gradle 9, AGP 9, Kotlin 2.4)

## 1.0

* Cached playlists, library items etc no longer expire after one week
* Reduced refresh interval to 25s in remote control mode if the player is only refreshed when it is on screen
