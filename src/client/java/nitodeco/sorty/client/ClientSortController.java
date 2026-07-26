package nitodeco.sorty.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import nitodeco.sorty.inventory.PlayerInventorySorter;
import nitodeco.sorty.network.SortInventoryPayload;

public final class ClientSortController {
	private ClientSortController() {
	}

	public static boolean trySort(InventoryScreen screen, Slot clickedSlot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
				|| clickedSlot == null
				|| clickedSlot.container != minecraft.player.getInventory()
				|| clickedSlot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_START
				|| clickedSlot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_END) {
			return false;
		}

		if (screen.getMenu().getCarried().isEmpty()
				&& ClientPlayNetworking.canSend(SortInventoryPayload.TYPE)) {
			ClientPlayNetworking.send(SortInventoryPayload.INSTANCE);
		}
		return true;
	}
}
