package nitodeco.sorty.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/** Pure inventory-stack transformation. Input stacks are never mutated. */
public final class InventorySortAlgorithm {
	private static final StackOperations<ItemStack> ITEM_STACK_OPERATIONS = new StackOperations<>() {
		@Override
		public boolean isEmpty(ItemStack stack) {
			return stack.isEmpty();
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
		public boolean canMerge(ItemStack first, ItemStack second) {
			return ItemStack.isSameItemSameComponents(first, second);
		}

		@Override
		public ItemTypeClassifier.SortKey sortKey(ItemStack stack) {
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

			return ItemTypeClassifier.classify(path, stack.getItem() instanceof BlockItem,
					stack.has(DataComponents.FOOD));
		}

		@Override
		public String tieBreakKey(ItemStack stack) {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		}

		@Override
		public ItemStack empty() {
			return ItemStack.EMPTY;
		}
	};

	private InventorySortAlgorithm() {
	}

	public static List<ItemStack> sortItemStacks(List<ItemStack> slots) {
		return prepareItemStackSort(slots).sortedLayout();
	}

	public static PreparedItemStackSort prepareItemStackSort(List<ItemStack> slots) {
		validateStacks(slots, ITEM_STACK_OPERATIONS);
		List<ItemStack> current = copyStacks(slots);
		List<BundleTransfer> bundleTransfers = fillExistingBundleTypes(current);
		List<ItemStack> sorted = sort(current, ITEM_STACK_OPERATIONS, InventorySortAlgorithm::isBundle);

		return new PreparedItemStackSort(current, sorted, bundleTransfers);
	}

	static <T> List<T> sort(List<T> slots, StackOperations<T> operations) {
		return sort(slots, operations, stack -> false);
	}

	private static <T> List<T> sort(List<T> slots, StackOperations<T> operations, Predicate<T> locked) {
		int slotCount = slots.size();
		List<T> nonEmpty = new ArrayList<>(slotCount);

		for (T stack : slots) {

			if (locked.test(stack)) {
				continue;
			}

			if (operations.isEmpty(stack)) {
				continue;
			}

			nonEmpty.add(operations.copyWithCount(stack, operations.count(stack)));
		}

		validateStacks(slots, operations);

		Comparator<T> order = Comparator.comparing(operations::sortKey).thenComparing(operations::tieBreakKey);
		nonEmpty.sort(order);
		List<T> merged = mergeCompatibleStacks(nonEmpty, operations);

		if (merged.size() > slotCount) {
			throw new IllegalArgumentException("Sorted inventory does not fit its source slots");
		}

		List<T> result = new ArrayList<>(slotCount);
		int sortedIndex = 0;

		for (T stack : slots) {

			if (locked.test(stack)) {
				result.add(stack);
			} else if (sortedIndex < merged.size()) {
				result.add(merged.get(sortedIndex++));
			} else {
				result.add(operations.empty());
			}

		}

		return result;
	}

	private static List<ItemStack> copyStacks(List<ItemStack> slots) {
		List<ItemStack> copies = new ArrayList<>(slots.size());

		for (ItemStack stack : slots) {
			copies.add(stack.copy());
		}

		return copies;
	}

	private static List<BundleTransfer> fillExistingBundleTypes(List<ItemStack> current) {
		List<BundleTransfer> transfers = new ArrayList<>();

		for (int bundleSlot = 0; bundleSlot < current.size(); bundleSlot++) {
			ItemStack bundle = current.get(bundleSlot);

			if (!isBundle(bundle)) {
				continue;
			}

			BundleContents originalContents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
			List<ItemStack> acceptedTypes = originalContents.itemCopyStream().toList();

			if (acceptedTypes.isEmpty()) {
				continue;
			}

			BundleContents.Mutable mutableContents = new BundleContents.Mutable(originalContents);

			for (int sourceSlot = 0; sourceSlot < current.size(); sourceSlot++) {
				ItemStack source = current.get(sourceSlot);

				if (sourceSlot == bundleSlot || source.isEmpty() || isBundle(source) || acceptedTypes.stream()
						.noneMatch(accepted -> ItemStack.isSameItemSameComponents(accepted, source))) {
					continue;
				}

				ItemStack remaining = source.copy();
				int moved = mutableContents.tryInsert(remaining);

				if (moved == 0) {
					continue;
				}

				bundle = bundle.copy();
				bundle.set(DataComponents.BUNDLE_CONTENTS, mutableContents.toImmutable());
				current.set(bundleSlot, bundle);
				current.set(sourceSlot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
				transfers.add(new BundleTransfer(sourceSlot, bundleSlot, copyStacks(current)));
			}

		}

		return List.copyOf(transfers);
	}

	private static boolean isBundle(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BundleItem;
	}

	private static <T> void validateStacks(List<T> slots, StackOperations<T> operations) {

		for (T stack : slots) {

			if (operations.isEmpty(stack)) {
				continue;
			}

			int count = operations.count(stack);

			if (count <= 0 || count > operations.maximumCount(stack)) {
				throw new IllegalArgumentException("Inventory contains an invalid stack size");
			}

		}

	}

	private static <T> List<T> mergeCompatibleStacks(List<T> sorted, StackOperations<T> operations) {
		List<T> result = new ArrayList<>(sorted.size());

		for (T source : sorted) {
			int remaining = operations.count(source);

			for (int targetIndex = 0; targetIndex < result.size(); targetIndex++) {
				T target = result.get(targetIndex);

				if (!operations.canMerge(source, target)) {
					continue;
				}

				int room = operations.maximumCount(target) - operations.count(target);
				int moved = Math.min(room, remaining);

				if (moved > 0) {
					result.set(targetIndex, operations.copyWithCount(target, operations.count(target) + moved));
					remaining -= moved;
				}

				if (remaining == 0) {
					break;
				}

			}

			if (remaining > 0) {
				result.add(operations.copyWithCount(source, remaining));
			}

		}

		return result;
	}

	interface StackOperations<T> {
		boolean isEmpty(T stack);
		boolean canMerge(T first, T second);

		int count(T stack);
		int maximumCount(T stack);

		T copyWithCount(T stack, int count);
		T empty();

		ItemTypeClassifier.SortKey sortKey(T stack);

		String tieBreakKey(T stack);
	}

	public record BundleTransfer(int sourceSlot, int bundleSlot, List<ItemStack> expectedLayout) {
		public BundleTransfer {
			expectedLayout = copyStacks(expectedLayout);
		}
	}

	public record PreparedItemStackSort(List<ItemStack> bundleFilledLayout, List<ItemStack> sortedLayout,
			List<BundleTransfer> bundleTransfers) {
		public PreparedItemStackSort {
			bundleFilledLayout = copyStacks(bundleFilledLayout);
			sortedLayout = copyStacks(sortedLayout);
			bundleTransfers = List.copyOf(bundleTransfers);
		}
	}
}
