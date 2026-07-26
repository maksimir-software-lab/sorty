# Sorty - MVP Specification

## Summary

Sorty is a Fabric mod for Minecraft Java Edition 26.2 that sorts the player's
main inventory when the player middle-clicks an inventory item.

## MVP behavior

- Trigger sorting when the player middle-clicks a slot in the player inventory
  screen.
- Sort only the 27-slot main inventory, from left to right and row by row.
- Do not change the hotbar, armor slots, offhand slot, crafting grid, or item
  currently held by the cursor.
- Merge compatible partial stacks before sorting.
- Sort non-empty stacks alphabetically by item name.
- Place empty slots after all non-empty stacks.
- Consume the triggering middle-click so vanilla middle-click behavior does not
  also run.
- Do nothing when the cursor is holding an item or the clicked slot is outside
  the player's main inventory.

## Stack compatibility

Stacks may be merged only when Minecraft considers their item and components
compatible. Custom names, damage, enchantments, potion contents, container
contents, and other components must be preserved. No resulting stack may exceed
its maximum stack size.

## Architecture

1. Client-side input handling detects a middle-click over an eligible slot.
2. The client sends a sort request to the logical server.
3. The server validates the player's current menu and requested operation.
4. The server merges and sorts the eligible inventory slots.
5. Minecraft's normal menu synchronization sends the updated inventory to the
   client.

The sorting algorithm should be isolated from mouse handling and networking so
it can be tested independently. Client-only classes must remain under the client
source set. Any screen Mixin should only capture the gesture and delegate the
operation.

## Sorting order

For the MVP, use a deterministic, case-insensitive item-name key with the
registry identifier as a tie-breaker. Stacks of the same item that differ in
components must remain distinct but adjacent where practical.

## Safety requirements

- Inventory mutation occurs only on the logical server.
- A malformed or invalid client request must not alter inventory state.
- Sorting must neither create nor destroy items or components.
- Applying the sort twice must produce the same result as applying it once.
- Multiplayer use requires Sorty on both the client and server.

## Acceptance criteria

- Two compatible partial stacks are combined correctly.
- Different enchanted, damaged, named, potion, or container-bearing stacks are
  not incorrectly combined.
- A full inventory sorts without losing items.
- The hotbar and excluded equipment/crafting slots remain unchanged.
- A middle-click with a non-empty cursor performs no sort.
- A middle-click outside the main inventory performs no sort.
- Sorting works in single-player and on a dedicated Fabric server with Sorty
  installed.
- The project builds successfully with `.\gradlew.bat build` on JDK 25.

## Out of scope for MVP

- Sorting chests or other containers.
- Sorting the hotbar.
- Client-only compatibility with unmodified multiplayer servers.
- Configurable sort rules, keybindings, buttons, or per-container settings.
- Locale-specific ordering guarantees.
