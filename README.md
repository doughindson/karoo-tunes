# Karoo Tunes

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/doughindson/karoo-tunes/android.yml)](https://github.com/doughindson/karoo-tunes/actions/workflows/android.yml)
[![GitHub Downloads (specific asset, all releases)](https://img.shields.io/github/downloads/doughindson/karoo-tunes/app-release.apk)](https://github.com/doughindson/karoo-tunes/releases)
[![GitHub License](https://img.shields.io/github/license/doughindson/karoo-tunes)](https://github.com/doughindson/karoo-tunes/blob/master/LICENSE)

This extension for Karoo devices adds a graphical data field with Spotify music controls that can be added to data pages.

Compatible with Karoo 3 devices.

![Player](player.png)
![Playlists](playlists.png)
![Podcast](podcast.png)
![Login](login.png)

## Features

- A player data field in four sizes (single field, small, medium, full page) with play/pause, skip, like, shuffle and repeat
- Podcasts: skip back 15 s / forward 30 s instead of previous/next while an episode plays
- Album art, and a library of playlists, Liked Songs, podcasts, saved episodes and the play queue
- Follows the Karoo's day and night themes

## Installation

If you are using a Karoo 3, you can use [Hammerhead's sideloading procedure](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading) to install the app:

1. Using the browser on your phone, long-press [this download link](https://github.com/doughindson/karoo-tunes/releases/latest/download/app-release.apk) and share it with the Hammerhead Companion app.
2. Your Karoo should show an info screen about the app now. Press "Install".
3. Open Karoo Tunes from the main menu, acknowledge the note and log in to Spotify.
4. Add the Karoo Tunes player data field to one of your ride pages. A full page works best.

Karoo Tunes controls Spotify on your phone (or any other Spotify Connect device, such as a computer when training indoors) through the Spotify Web API. It needs an internet connection, which the companion app provides while your phone has mobile data. The player refreshes every 25 seconds while it is on screen, and right after the current track ends. You can make it refresh whenever it is part of the active ride profile instead, which makes it quicker to show the current track when you switch to its page but uses more battery.

Spotify's Development Mode rules apply: the app owner needs Spotify Premium, and track lists are only available for playlists you own or collaborate on (any playlist can still be played as a whole).

## Development

Requirements: JDK 17+ (Android Studio's bundled JDK works) and, in `local.properties`:

```
SPOTIFY_CLIENT_ID=<your Spotify app's client ID>
gpr.user=<GitHub username>
gpr.key=<GitHub token with read:packages, for karoo-ext>
```

Build and test with `./gradlew lint testDebugUnitTest assembleDebug`. See [CLAUDE.md](CLAUDE.md) for how the code is organised.

## Credits

- Forked from [timklge/karoo-spintunes](https://github.com/timklge/karoo-spintunes)
- Icons are [Material Symbols](https://fonts.google.com/icons) (Apache 2.0)
- Interfaces with the [Spotify Web API](https://developer.spotify.com/documentation/web-api/)
- Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) (Apache2-licensed)
