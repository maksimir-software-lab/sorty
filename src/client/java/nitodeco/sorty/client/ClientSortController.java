package nitodeco.sorty.client;

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

	public static boolean trySort(AbstractContainerScreen<?> screen, Slot clickedSlot) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || clickedSlot == null || !isSortableTarget(screen, clickedSlot, minecraft)) {
			return false;
		}

		if (screen.getMenu().getCarried().isEmpty() && ClientPlayNetworking.canSend(SortInventoryPayload.TYPE)) {
			ClientPlayNetworking.send(SortInventoryPayload.INSTANCE);
		}

		return true;
	}

	private static boolean isSortableTarget(AbstractContainerScreen<?> screen, Slot clickedSlot, Minecraft minecraft) {

		if (screen instanceof InventoryScreen) {
			return clickedSlot.container == minecraft.player.getInventory()
					&& clickedSlot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START
					&& clickedSlot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END;
		}

		if (!(screen.getMenu() instanceof ChestMenu || screen.getMenu() instanceof ShulkerBoxMenu)) {
			return false;
		}

		return clickedSlot.container != minecraft.player.getInventory();
	}
}
