# Changelog

The first `## ` section below is published as the release notes in `manifest.json`.

## Unreleased

* Rebuilt on a current toolchain (Gradle 9, AGP 9, Kotlin 2.4)
* Fixed being logged out after a short loss of connection during a ride
* Fixed the Saved Songs list only ever loading its first page
* Removed standalone mode (controlling a Spotify app sideloaded on the Karoo); Karoo Tunes now controls Spotify on your phone

## 1.0

* Cached playlists, library items etc no longer expire after one week
* Reduced refresh interval to 25s in remote control mode if the player is only refreshed when it is on screen
