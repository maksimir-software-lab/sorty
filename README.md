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

Sorty is a lightweight, client-side mod that adds fast inventory sorting. The default keybind is the middle mouse button. When sorting an inventory, items are sorted by amount and type. Incomplete stacks of the same item are merged to free more inventory space. Items are grouped by type: Ingots are sorted with ingots, logs with logs, planks with planks.

## Multiplayer

Sorty is entirely client-side, you don't need to install it on a server for it to work. Sorting is a bit slower on multiplayer due to rate limits, although in most cases it should feel near-instant.

## Compatibility

Sorty is compatible with almost every mod, except with other inventory sorting mods.

## Development

The development toolchain is pinned in `mise.toml`. With mise-en-place:

```powershell
mise install
mise exec -- .\gradlew.bat build
```

Alternatively, build directly with JDK 25:

```powershell
.\gradlew.bat build
```
