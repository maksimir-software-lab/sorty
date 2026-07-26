package nitodeco.sorty.client;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import nitodeco.sorty.inventory.PlayerInventorySorter;
import nitodeco.sorty.network.SortInventoryPayload;

public final class ClientSortController {
	private ClientSortController() {
	}

	public static boolean isMultiplayerSortActive() {
		return MultiplayerSortExecutor.isActive();
	}

	public static void cancelMultiplayerSortAndClose() {
		MultiplayerSortExecutor.requestCancelAndClose();
	}

	public static boolean trySort(AbstractContainerScreen<?> screen) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || !isSortableScreen(screen)) {
			return false;
		}

		if (!screen.getMenu().getCarried().isEmpty()) {
			return true;
		}

		if (minecraft.hasSingleplayerServer() && ClientPlayNetworking.canSend(SortInventoryPayload.TYPE)) {
			ClientPlayNetworking.send(SortInventoryPayload.INSTANCE);
		} else if (!minecraft.hasSingleplayerServer()) {
			MultiplayerSortExecutor.start(screen, sortableSlots(screen, minecraft));
		}

		return true;
	}

	private static List<Slot> sortableSlots(AbstractContainerScreen<?> screen, Minecraft minecraft) {

		if (screen instanceof InventoryScreen) {
			return screen.getMenu().slots.stream().filter(slot -> slot.container == minecraft.player.getInventory())
					.filter(slot -> slot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START)
					.filter(slot -> slot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END).toList();
		}

		Slot firstStorageSlot = screen.getMenu().slots.getFirst();

		return screen.getMenu().slots.stream().filter(slot -> slot.container == firstStorageSlot.container).toList();
	}

	private static boolean isSortableScreen(AbstractContainerScreen<?> screen) {
		return screen instanceof InventoryScreen || screen.getMenu() instanceof ChestMenu
				|| screen.getMenu() instanceof ShulkerBoxMenu;
	}
}
