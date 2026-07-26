# Sorty

A Fabric mod for Minecraft Java Edition 26.2.

Middle-click a storage slot in the player inventory, a chest-style container,
or a shulker box to merge compatible partial stacks and sort that storage area
by item type. Ingots stay with ingots, slabs with slabs, tools with matching
tools, and logs with their stripped variants.

In the player inventory, only the 27-slot main inventory is sorted; the hotbar,
equipment, crafting slots, and offhand remain untouched. In container screens,
only the opened chest or shulker storage is sorted, not the player's inventory.
Sorting is ignored while carrying a cursor stack.

In singleplayer, Sorty applies the completed layout atomically through the
integrated server. On multiplayer servers, it performs paced vanilla inventory
clicks and does not need to be installed on the server. Keep the inventory
screen open until sorting finishes; pressing Escape waits until the cursor is
safe before closing it.

## Build

Build with JDK 25:

```powershell
.\gradlew.bat build
```
