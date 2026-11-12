# Karoo Tunes

A Hammerhead Karoo 3 extension: a Spotify player data field plus a small app for login, settings and
browsing the library. It controls Spotify on the rider's phone through the Spotify Web API. The Karoo
has no internet of its own, so every request goes through the Karoo companion app on the phone.

Standalone mode (controlling a Spotify app sideloaded on the Karoo via the App Remote SDK) was removed.
The last version with it is tagged `standalone-mode-reference`. To bring it back, add a second
`PlaybackController` implementation and a setting that picks it.

## Build

- JDK 17+ (Android Studio's bundled JBR is fine), Android SDK with platform 37.
- `local.properties` needs `SPOTIFY_CLIENT_ID`, plus `gpr.user` / `gpr.key` (a GitHub token with
  `read:packages`) to download `io.hammerhead:karoo-ext` from GitHub Packages.
- `./gradlew lint testDebugUnitTest assembleDebug` is what CI runs (plus `assembleRelease`).
- `targetSdk` stays at 31 because the Karoo 3 runs Android 12.

## Layout (`app/src/main/kotlin/com/doughindson/karootunes`)

- `data/karoo/KarooGateway` is the single `KarooSystemService` connection: ride page/profile streams and in-ride alerts.
- `data/http` has `HttpTransport`. `KarooHttpTransport` sends requests through the companion app, or directly when not on a Karoo.
  It throws `NetworkException` only when no response arrives.
- `data/auth/SpotifyAuth` handles PKCE login, persisted pending state, and single-flight token refresh. Only `invalid_grant` logs out.
  Tokens live in their own DataStore (`TokenStore`). Backups are disabled.
- `data/spotify/SpotifyWebApi` is the Web API client. Every call returns `ApiResult` (Ok / NoContent / Failure(ApiError)).
  It does one 401 refresh-and-retry and capped 429 back-off. Library lists are cached on disk by `ResponseCache`
  until pull-to-refresh clears them. `OffsetPagingSource` pages any Spotify list.
- `data/settings/SettingsRepository` holds the settings as one shared `StateFlow`.
- `data/ThumbnailCache` is the image cache: memory LRU, then disk, then network, with single-flight downloads.
- `playback/`:
  - `PlayerState` (domain model) and `PlayerReducer` (pure transitions; unit-tested).
  - `PlaybackController` / `WebPlayback`.
  - `PlayerRepository` owns state and polls only while the field is visible.
  - `PlayerCommands` handles optimistic update, command, alert on failure, and refresh.
  - `WidgetUiState` is widget-only state: the options panel and pending commands.
- `widget/`:
  - `PlayerDataType` is the Karoo data field.
  - `WidgetModel` is exactly what a layout draws. It is compared before each render.
  - `WidgetRenderer` turns state into RemoteViews via Glance.
  - `WidgetLayouts` and `WidgetComponents` hold the layouts and their parts.
  - `PlayerActionCallback` is the one click handler; the button's `PlayerCommand` is passed as a parameter.
- `ui/` has a single `MainActivity` (it also receives the `karootunes://oauth2redirect` login redirect).
  It uses type-safe Navigation routes, Koin ViewModels, and the screens `home/`, `library/` and `tracks/`.
- `di/AppModule` holds the Koin modules. `AppModuleTest` verifies the graph.

## Conventions

- App UI: the screen is only 256 x 427 dp. Use the shared components in `ui/components` (`ScreenHeader`,
  `ListRow`, `PillButton`, `PlayButton`, `FilterChips`, `BackButton`) and the full colour schemes in
  `ui/theme/Theme.kt`, which follow Spotify's palette. Green is a fill (with black content), never text.
  `ui/Previews.kt` has light/dark previews at the Karoo's size.

- Network and HTTP errors are values (`ApiResult`), not exceptions; never show Toasts from the data layer.
- Widget colours: the text and icon `ColorProvider`s are in `WidgetColors` (day/night pairs). The shapes use
  `res/values(-night)/colors.xml`. Keep the two in sync.
- Spotify Web API (2026 Development Mode): use `/playlists/{id}/items` (items have `item`, older ones `track`),
  `/me/library` for saving and checking liked items, with full `spotify:` URIs.
