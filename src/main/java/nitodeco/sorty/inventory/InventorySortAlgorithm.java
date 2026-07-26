package nitodeco.sorty.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

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
		return sort(slots, ITEM_STACK_OPERATIONS);
	}

	static <T> List<T> sort(List<T> slots, StackOperations<T> operations) {
		int slotCount = slots.size();
		List<T> nonEmpty = new ArrayList<>(slotCount);

		for (T stack : slots) {

			if (operations.isEmpty(stack)) {
				continue;
			}

			int count = operations.count(stack);

			if (count <= 0 || count > operations.maximumCount(stack)) {
				throw new IllegalArgumentException("Inventory contains an invalid stack size");
			}

			nonEmpty.add(operations.copyWithCount(stack, count));
		}

		Comparator<T> order = Comparator.comparing(operations::sortKey).thenComparing(operations::tieBreakKey);
		nonEmpty.sort(order);
		List<T> merged = mergeCompatibleStacks(nonEmpty, operations);

		if (merged.size() > slotCount) {
			throw new IllegalArgumentException("Sorted inventory does not fit its source slots");
		}

		List<T> result = new ArrayList<>(slotCount);
		result.addAll(merged);

		while (result.size() < slotCount) {
			result.add(operations.empty());
		}

		return result;
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

		int count(T stack);

		int maximumCount(T stack);

		T copyWithCount(T stack, int count);

		boolean canMerge(T first, T second);

		ItemTypeClassifier.SortKey sortKey(T stack);

		String tieBreakKey(T stack);

		T empty();
	}
}
