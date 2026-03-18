# Changelog

## 1.0.1

- Downgraded NeoForge to 21.1.217 for broader compatibility
- Adjacent chunks from the same player are now merged into clean polygons with no internal borders
- Fixed BlueMap world ID matching (handles `world#minecraft:overworld` format)
- Claim changes now update the map live with debounced refreshes
- Collinear polygon points are removed for cleaner shapes
- Fixed crash when a sub-config has no custom color or name set
- Fixed out-of-memory crash when tracing complex claim shapes with diagonal connections
- Expired claims now show as "EXPIRED - PlayerName" on the map
- Player names are resolved from the server profile cache when OpenPAC doesn't have them
- BlueMap and OpenPAC are now marked as required dependencies
- Claims now always use the player's main color instead of sub-config colors

