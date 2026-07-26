package nitodeco.sorty.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class PlayerInventorySorter {
	public static final int MAIN_INVENTORY_START = 9;
	public static final int MAIN_INVENTORY_END = 36;

	private PlayerInventorySorter() {
	}

	public static boolean sortIfAllowed(ServerPlayer player) {
		if (player.containerMenu != player.inventoryMenu
				|| !(player.containerMenu instanceof InventoryMenu)
				|| !player.containerMenu.getCarried().isEmpty()) {
			return false;
		}

		Inventory inventory = player.getInventory();
		List<ItemStack> source = new ArrayList<>(MAIN_INVENTORY_END - MAIN_INVENTORY_START);
		for (int slot = MAIN_INVENTORY_START; slot < MAIN_INVENTORY_END; slot++) {
			source.add(inventory.getItem(slot));
		}

		final List<ItemStack> sorted;
		try {
			sorted = InventorySortAlgorithm.sortItemStacks(source);
		} catch (IllegalArgumentException invalidInventory) {
			return false;
		}

		for (int offset = 0; offset < sorted.size(); offset++) {
			inventory.setItem(MAIN_INVENTORY_START + offset, sorted.get(offset));
		}
		inventory.setChanged();
		player.containerMenu.broadcastChanges();
		return true;
	}
}
