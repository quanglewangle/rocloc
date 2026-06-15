# Changelog

## v1.8 (versionCode 14)

### Bug fixes
- Fix blank map on Android 15 — opt out of forced edge-to-edge enforcement so system bars remain opaque and the map fragment is correctly sized
- Bump osmdroid to 6.1.18 for Android 15 (API 35) tile-download compatibility

### Improvements
- Selected LoS source site now shows a blue dot on top of the orange dot, making the active site clearly visible

---

## v1.7 (versionCode 13)

### Bug fixes
- Fix crash on orientation change in Here tab — background LoS executor now recreates correctly when the fragment view is rebuilt
- Map and Here tabs now refresh markers after pull-to-refresh — switching to the map after refreshing the Sites or Pins list now shows the updated site markers
- Fix deprecated back navigation on Android 13+

### Improvements
- All network calls now share a single HTTP connection pool, reducing background thread usage

---

## v1.6 (versionCode 12)

### Bug fixes
- Fix map tiles not loading (blank map) — osmdroid tile cache now uses app-internal storage instead of external storage, which was inaccessible on Android 10+ and required a permission on older devices
- Fix crash when navigating away during remote LoS calculation — callbacks now guard against a destroyed fragment view
- Fix silent failure when site list can't be loaded — Map tab shows a Snackbar and Here tab shows a status message

### Improvements
- Pull-to-refresh on Sites and Pins tabs — swipe down to force a fresh fetch from the server
- Repository is now a singleton — one background thread serves the whole app lifetime

---

## v1.5 (versionCode 11)

### Bug fixes
- Detail screen LoS check now uses your configured antenna height — previously hardcoded to 10 m regardless of Settings
- Search now shows the real error message — no longer always says "Not found" for network errors
- Tapping a pin on the map no longer leaves stale LoS lines
- Map: tapping a site from the Sites/Pins list now correctly centres and zooms the map
- Long press on map markers now shows site detail popup

### Improvements
- Terrain tiles can now be re-downloaded — button shows tile count/size and allows re-download for a new area
- Sites and Pins lists no longer reload from the network on every app resume
- Sites list is now sorted alphabetically
- All map/list views share a single getSites() network call per session
- Site markers on Here tab clean up correctly on fragment destroy

---

## v1.4 (versionCode 8)

- Redesigned launcher icon as broadcast signal (dot + arcs)

---

## v1.3 (versionCode 7)

- Custom radio tower launcher icon

---

## v1.2 (versionCode 6)

- Long press detail popup for sites and pins
- Fix Settings navigation bug

---

## v1.1 (versionCode 5)

- Auto-redraw Here LoS on movement
- Site filter in Settings

---

## v1.0

- Initial release
