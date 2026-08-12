package nitodeco.sorty.client;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import nitodeco.sorty.inventory.PlayerInventorySorter;
import nitodeco.sorty.network.SortInventoryPayload;
import nitodeco.sorty.network.SortTarget;

public final class ClientSortController {
	private ClientSortController() {
	}

	public static boolean isMultiplayerSortActive() {
		return MultiplayerSortExecutor.isActive();
	}

	public static void cancelMultiplayerSortAndClose() {
		MultiplayerSortExecutor.requestCancelAndClose();
	}

	public static boolean trySort(AbstractContainerScreen<?> screen, Slot triggerSlot) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return false;
		}

		if (!screen.getMenu().getCarried().isEmpty()) {
			return true;
		}

		SortTarget target = findTarget(screen, triggerSlot, minecraft);

		if (target == null) {
			return false;
		}

		List<Slot> slots = sortableSlots(screen, minecraft, target);

		if (slots.isEmpty()) {
			return false;
		}

		if (ClientPlayNetworking.canSend(SortInventoryPayload.TYPE)) {
			ClientPlayNetworking.send(new SortInventoryPayload(target));
		} else {
			MultiplayerSortExecutor.start(screen, slots);
		}

		return true;
	}

	private static List<Slot> sortableSlots(AbstractContainerScreen<?> screen, Minecraft minecraft, SortTarget target) {

		if (target == SortTarget.PLAYER_INVENTORY) {
			return screen.getMenu().slots.stream().filter(slot -> slot.container == minecraft.player.getInventory())
					.filter(slot -> slot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START)
					.filter(slot -> slot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END).toList();
		}

		if (!(screen.getMenu() instanceof ChestMenu) && !(screen.getMenu() instanceof ShulkerBoxMenu)) {
			return List.of();
		}

		Slot firstStorageSlot = screen.getMenu().slots.getFirst();

		return screen.getMenu().slots.stream().filter(slot -> slot.container == firstStorageSlot.container).toList();
	}

	private static SortTarget findTarget(AbstractContainerScreen<?> screen, Slot triggerSlot, Minecraft minecraft) {

		if (triggerSlot == null) {
			boolean hasPlayerMainInventory = screen.getMenu().slots.stream()
					.anyMatch(slot -> slot.container == minecraft.player.getInventory()
							&& slot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START
							&& slot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END);

			return hasPlayerMainInventory && !(screen.getMenu() instanceof ChestMenu)
					&& !(screen.getMenu() instanceof ShulkerBoxMenu) ? SortTarget.PLAYER_INVENTORY : null;
		}

		if (triggerSlot.container == minecraft.player.getInventory()
				&& triggerSlot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START
				&& triggerSlot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END) {
			return SortTarget.PLAYER_INVENTORY;
		}

		if ((screen.getMenu() instanceof ChestMenu || screen.getMenu() instanceof ShulkerBoxMenu)
				&& !screen.getMenu().slots.isEmpty()
				&& triggerSlot.container == screen.getMenu().slots.getFirst().container) {
			return SortTarget.OPEN_CONTAINER;
		}

		return null;
	}
}
