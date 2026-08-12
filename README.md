<p align="center">
  <img src="src/main/resources/assets/sorty/icon.png" alt="Sorty icon" width="128">
</p>

# Sorty - Simple inventory sorting

<p align="center">
  <a href="https://github.com/maksimir-software-lab/sorty/releases">
    <img src="https://img.shields.io/github/v/tag/maksimir-software-lab/sorty?label=Version" alt="Latest Sorty version">
  </a>
  <img src="https://img.shields.io/badge/Minecraft-26.2-62B47A" alt="MC Version: 26.2">
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/maksimir-software-lab/sorty?cacheSeconds=300" alt="License">
  </a>
</p>

Sorty is a lightweight Fabric mod for sorting inventories, chests, and shulker boxes. Middle-click a slot to sort that inventory. This also works while crafting tables, furnaces, and other screens are open. Compatible stacks are merged, then items are grouped by type: ingots with ingots, logs with stripped logs, planks with planks, and so on.

Bundles stay put. Sorty only adds loose items to a bundle if it already contains that item.

## Multiplayer

Sorty works client-side on any server. Install it on the server too for instant sorting; otherwise it uses vanilla clicks and may take slightly longer.

## Compatibility

Sorty is compatible with almost every mod, except with other inventory sorting mods.

## Development

The development toolchain is pinned in `mise.toml`. With mise-en-place:

```powershell
mise install
mise run install-hooks
mise exec -- .\gradlew.bat build
```

Alternatively, build directly with JDK 25:

```powershell
.\gradlew.bat build
```

### Minecraft integration test

Run the Fabric client GameTest to launch the production-remapped mod in the
configured Minecraft version, create a singleplayer world, and verify that the
client starts successfully:

```powershell
.\gradlew.bat runProductionClientGameTest
```

On Unix-like systems:

```sh
./gradlew runProductionClientGameTest
```
