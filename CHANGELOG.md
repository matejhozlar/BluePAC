# Changelog

## 1.0.1

Initial release for Minecraft 1.20.1, available for both **Forge** and **Fabric** (the Forge build also runs on NeoForge 1.20.1).

- Displays Open Parties and Claims claimed chunks as color-coded regions on your BlueMap web map
- Each player's claims use their OpenPAC-configured color, with sub-claim names and labels
- Adjacent chunks from the same player are merged into clean polygons with no internal borders
- Live updates: claiming or unclaiming refreshes the map within moments (debounced), no restart needed
- Works across the Overworld, Nether, End, and any modded dimensions
- Player names are resolved from the server profile cache for offline players
- Configurable marker appearance via `config/bluepac-server.toml` — fill opacity, line opacity, line width, and marker Y height
- BlueMap and Open Parties and Claims are required dependencies
