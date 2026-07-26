package nitodeco.sorty.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;

public final class PlayerInventorySorter {
	public static final int MAIN_INVENTORY_START = 9;
	public static final int MAIN_INVENTORY_END = 36;

	private PlayerInventorySorter() {
	}

	public static boolean sortIfAllowed(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;

		if (!menu.getCarried().isEmpty() || !menu.stillValid(player)) {
			return false;
		}

		if (menu == player.inventoryMenu && menu instanceof InventoryMenu) {
			return sortPlayerMainInventory(player, menu);
		}

		if (menu instanceof ChestMenu chestMenu) {
			return sortContainer(chestMenu.getContainer(), menu);
		}

		if (menu instanceof ShulkerBoxMenu && !menu.slots.isEmpty()) {
			return sortContainer(menu.slots.getFirst().container, menu);
		}

		return false;
	}

	private static boolean sortPlayerMainInventory(ServerPlayer player, AbstractContainerMenu menu) {
		Inventory inventory = player.getInventory();
		List<ItemStack> source = copySlots(inventory, MAIN_INVENTORY_START, MAIN_INVENTORY_END);
		List<ItemStack> sorted = sortSafely(source);

		if (sorted == null) {
			return false;
		}

		for (int offset = 0; offset < sorted.size(); offset++) {
			inventory.setItem(MAIN_INVENTORY_START + offset, sorted.get(offset));
		}

		inventory.setChanged();
		menu.broadcastChanges();

		return true;
	}

	private static boolean sortContainer(Container container, AbstractContainerMenu menu) {
		int size = container.getContainerSize();

		if (size <= 0 || menu.slots.size() < size) {
			return false;
		}

		for (int slot = 0; slot < size; slot++) {

			if (menu.slots.get(slot).container != container) {
				return false;
			}

		}

		List<ItemStack> sorted = sortSafely(copySlots(container, 0, size));

		if (sorted == null) {
			return false;
		}

		for (int slot = 0; slot < size; slot++) {
			container.setItem(slot, sorted.get(slot));
		}

		container.setChanged();
		menu.broadcastChanges();

		return true;
	}

	private static List<ItemStack> copySlots(Container container, int start, int end) {
		List<ItemStack> source = new ArrayList<>(end - start);

		for (int slot = start; slot < end; slot++) {
			source.add(container.getItem(slot));
		}

		return source;
	}

	private static List<ItemStack> sortSafely(List<ItemStack> source) {

		try {
			return InventorySortAlgorithm.sortItemStacks(source);
		} catch (IllegalArgumentException invalidInventory) {
			return null;
		}

	}
}
