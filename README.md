# BluePAC – BlueMap + Open Parties and Claims Integration

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-5E7C16?logo=minecraft&logoColor=white)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1+-orange)
![Server Side](https://img.shields.io/badge/Side-Server-blue)

**BluePAC** bridges [BlueMap](https://bluemap.bluecolored.de/) and [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) to display color-coded claimed chunks directly on your BlueMap web map. See at a glance who owns what — no need to log in to the game.

---

## Features

- **Color-coded claim regions** — Each player's claims are rendered using their OpenPAC-configured color, making it easy to distinguish ownership at a glance.
- **Merged polygons** — Adjacent chunks from the same player are merged into clean shapes with no internal borders, keeping the map tidy even with hundreds of claims.
- **Live updates** — Markers update within moments of a player claiming or unclaiming chunks. No server restart or manual reload needed.
- **Sub-claim support** — Different sub-configurations are rendered with their own colors and labels.
- **Multi-dimension** — Works across Overworld, Nether, End, and any modded dimensions.
- **Configurable appearance** — Tweak fill opacity, border opacity, line width, and marker height to match your server's style.

---

## Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1+
- [**BlueMap**](https://modrinth.com/plugin/bluemap)
- [**Open Parties and Claims**](https://modrinth.com/mod/open-parties-and-claims)

---

## Configuration

On first launch the mod generates `config/bluepac-common.toml`:

| Option | Default | Description |
|--------|---------|-------------|
| `fillOpacity` | `0.3` | Fill transparency of claim regions (0.0–1.0) |
| `lineOpacity` | `0.8` | Border transparency of claim regions (0.0–1.0) |
| `lineWidth` | `2` | Border width in pixels |
| `markerYHeight` | `64` | Y level at which markers are drawn on the map |

---

## Installation

1. Install BlueMap and Open Parties and Claims on your server.
2. Drop the BluePAC jar into your server's `mods/` folder.
3. Start the server — claimed chunks will appear on the BlueMap web map automatically.

---

## Disclaimer

BluePAC is an independent, third-party mod and is **not affiliated with, endorsed by, or associated with** the BlueMap or Open Parties and Claims projects. BlueMap and Open Parties and Claims are the property of their respective authors. All trademarks and registered trademarks are the property of their respective owners.

---

Made by [@saunhardy](https://github.com/matejhozlar)