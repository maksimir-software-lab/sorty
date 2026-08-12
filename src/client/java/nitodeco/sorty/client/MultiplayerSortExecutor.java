package nitodeco.sorty.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import nitodeco.sorty.inventory.InventoryClickPlanner;
import nitodeco.sorty.inventory.InventorySortAlgorithm;

public final class MultiplayerSortExecutor {
	private static final int ACTIONS_PER_TICK = 6;
	private static final int BATCH_SETTLE_TICKS = 1;
	private static final int FINAL_SETTLE_TICKS = 3;
	private static final InventoryClickPlanner.StackOperations<ItemStack> STACK_OPERATIONS = new InventoryClickPlanner.StackOperations<>() {
		@Override
		public boolean isEmpty(ItemStack stack) {
			return stack.isEmpty();
		}

		@Override
		public boolean canMerge(ItemStack first, ItemStack second) {
			return ItemStack.isSameItemSameComponents(first, second);
		}

		@Override
		public int count(ItemStack stack) {
			return stack.getCount();
		}

		@Override
		public int maximumCount(ItemStack stack) {
			return stack.getMaxStackSize();
		}

		@Override
		public ItemStack copyWithCount(ItemStack stack, int count) {
			return stack.copyWithCount(count);
		}

		@Override
		public ItemStack empty() {
			return ItemStack.EMPTY;
		}
	};

	private static Session activeSession;

	private MultiplayerSortExecutor() {
	}

	public static boolean start(AbstractContainerScreen<?> screen, List<Slot> sortableSlots) {
		Minecraft minecraft = Minecraft.getInstance();

		if (activeSession != null || minecraft.player == null || minecraft.gameMode == null
				|| !screen.getMenu().getCarried().isEmpty()) {
			return false;
		}

		List<Slot> orderedSlots = sortableSlots.stream().sorted(Comparator.comparingInt(Slot::getContainerSlot))
				.toList();
		List<ItemStack> source = orderedSlots.stream().map(slot -> slot.getItem().copy()).toList();
		List<InventoryClickPlanner.Action<ItemStack>> actions;

		try {
			actions = planBundleSafeClicks(source);
		} catch (IllegalArgumentException invalidInventory) {
			return false;
		}

		if (actions.isEmpty()) {
			return true;
		}

		AbstractContainerMenu menu = screen.getMenu();
		List<Integer> menuSlotIds = new ArrayList<>(orderedSlots.size());

		for (Slot slot : orderedSlots) {
			int menuSlot = menu.slots.indexOf(slot);

			if (menuSlot < 0) {
				return false;
			}

			menuSlotIds.add(menuSlot);
		}

		activeSession = new Session(screen, menu, List.copyOf(menuSlotIds), actions);
		minecraft.player.sendOverlayMessage(Component.literal("Sorting..."));

		return true;
	}

	private static List<InventoryClickPlanner.Action<ItemStack>> planBundleSafeClicks(List<ItemStack> source) {
		InventorySortAlgorithm.PreparedItemStackSort prepared = InventorySortAlgorithm.prepareItemStackSort(source);
		List<InventoryClickPlanner.Action<ItemStack>> actions = new ArrayList<>();

		for (InventorySortAlgorithm.BundleTransfer transfer : prepared.bundleTransfers()) {
			actions.add(new InventoryClickPlanner.Action<>(
					List.of(transfer.sourceSlot(), transfer.bundleSlot(), transfer.sourceSlot()),
					transfer.expectedLayout()));
		}

		List<Integer> movableSlots = new ArrayList<>();

		for (int slot = 0; slot < prepared.bundleFilledLayout().size(); slot++) {

			if (!(prepared.bundleFilledLayout().get(slot).getItem() instanceof BundleItem)) {
				movableSlots.add(slot);
			}

		}

		List<ItemStack> movableSource = movableSlots.stream().map(prepared.bundleFilledLayout()::get).toList();
		List<ItemStack> movableTarget = movableSlots.stream().map(prepared.sortedLayout()::get).toList();
		List<ItemStack> fullLayout = new ArrayList<>(prepared.bundleFilledLayout());

		for (InventoryClickPlanner.Action<ItemStack> action : InventoryClickPlanner.plan(movableSource, movableTarget,
				STACK_OPERATIONS)) {
			List<Integer> mappedClicks = action.slots().stream().map(movableSlots::get).toList();

			for (int movableIndex = 0; movableIndex < movableSlots.size(); movableIndex++) {
				fullLayout.set(movableSlots.get(movableIndex), action.expectedLayout().get(movableIndex));
			}

			actions.add(new InventoryClickPlanner.Action<>(mappedClicks, fullLayout));
		}

		return List.copyOf(actions);
	}

	public static boolean isActive() {
		return activeSession != null;
	}

	public static void requestCancelAndClose() {

		if (activeSession != null) {
			activeSession.closeWhenSafe = true;
		}

	}

	public static void tick(Minecraft minecraft) {

		if (activeSession == null) {
			return;
		}

		Session session = activeSession;

		if (minecraft.player == null || minecraft.gameMode == null || minecraft.screen != session.screen
				|| minecraft.player.containerMenu != session.menu) {
			activeSession = null;

			return;
		}

		session.elapsedTicks++;

		if (session.elapsedTicks % 20 == 0) {
			minecraft.player.sendOverlayMessage(Component.literal("Sorting..."));
		}

		if (session.settleTicks-- > 0) {
			return;
		}

		if (session.lastCompletedAction >= 0 && (!session.menu.getCarried().isEmpty()
				|| !matchesExpectedLayout(session, session.lastCompletedAction))) {
			activeSession = null;
			minecraft.player.sendOverlayMessage(Component.literal("Sorting stopped: inventory changed"));

			return;
		}

		if (session.closeWhenSafe || session.nextAction == session.actions.size()) {
			boolean shouldClose = session.closeWhenSafe;
			activeSession = null;

			if (shouldClose) {
				session.screen.onClose();
			} else {
				minecraft.player.sendOverlayMessage(Component.literal("Sorted"));
			}

			return;
		}

		int actionsThisTick = 0;

		while (session.nextAction < session.actions.size() && actionsThisTick++ < ACTIONS_PER_TICK) {
			InventoryClickPlanner.Action<ItemStack> action = session.actions.get(session.nextAction);

			for (int sortableSlot : action.slots()) {
				minecraft.gameMode.handleContainerInput(session.menu.containerId, session.menuSlotIds.get(sortableSlot),
						0, ContainerInput.PICKUP, minecraft.player);
				session.menu.incrementStateId();
			}

			session.lastCompletedAction = session.nextAction++;
		}

		session.settleTicks = session.nextAction == session.actions.size() ? FINAL_SETTLE_TICKS : BATCH_SETTLE_TICKS;
	}

	private static boolean matchesExpectedLayout(Session session, int actionIndex) {
		List<ItemStack> expected = session.actions.get(actionIndex).expectedLayout();

		for (int slot = 0; slot < expected.size(); slot++) {
			ItemStack actualStack = session.menu.getSlot(session.menuSlotIds.get(slot)).getItem();
			ItemStack expectedStack = expected.get(slot);

			if (actualStack.getCount() != expectedStack.getCount()
					|| !ItemStack.isSameItemSameComponents(actualStack, expectedStack)) {
				return false;
			}

		}

		return true;
	}

	private static final class Session {
		private final AbstractContainerScreen<?> screen;
		private final AbstractContainerMenu menu;
		private final List<Integer> menuSlotIds;
		private final List<InventoryClickPlanner.Action<ItemStack>> actions;
		private int nextAction;
		private int lastCompletedAction = -1;
		private int settleTicks;
		private int elapsedTicks;
		private boolean closeWhenSafe;

		private Session(
			AbstractContainerScreen<?> screen,
			AbstractContainerMenu menu,
			List<Integer> menuSlotIds,
			List<InventoryClickPlanner.Action<ItemStack>> actions
		) {
			this.screen = screen;
			this.menu = menu;
			this.menuSlotIds = menuSlotIds;
			this.actions = actions;
		}
	}
}
