package nitodeco.sorty.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans left-click operations that transform one inventory layout into another.
 * Every action starts and ends with an empty cursor.
 */
public final class InventoryClickPlanner {
	private InventoryClickPlanner() {
	}

	public static <T> List<Action<T>> plan(List<T> source, List<T> target, StackOperations<T> operations) {

		if (source.size() != target.size()) {
			throw new IllegalArgumentException("Source and target slot counts differ");
		}

		List<T> current = new ArrayList<>(source);
		List<Action<T>> actions = new ArrayList<>();
		mergeCompatibleStacks(current, actions, operations);
		arrangeTarget(current, target, actions, operations);

		if (!layoutsEqual(current, target, operations)) {
			throw new IllegalArgumentException("Target layout cannot be produced with pickup clicks");
		}

		return List.copyOf(actions);
	}

	private static <T> void mergeCompatibleStacks(
		List<T> current,
		List<Action<T>> actions,
		StackOperations<T> operations
	) {

		for (int targetSlot = 0; targetSlot < current.size(); targetSlot++) {
			T target = current.get(targetSlot);

			if (operations.isEmpty(target)) {
				continue;
			}

			for (int sourceSlot = targetSlot + 1; sourceSlot < current.size(); sourceSlot++) {
				T source = current.get(sourceSlot);

				if (operations.isEmpty(source) || !operations.canMerge(target, source)) {
					continue;
				}

				int room = operations.maximumCount(target) - operations.count(target);

				if (room <= 0) {
					break;
				}

				int moved = Math.min(room, operations.count(source));
				int remaining = operations.count(source) - moved;
				List<Integer> clicks = new ArrayList<>(3);
				clicks.add(sourceSlot);
				clicks.add(targetSlot);

				if (remaining > 0) {
					clicks.add(sourceSlot);
				}

				target = operations.copyWithCount(target, operations.count(target) + moved);
				current.set(targetSlot, target);
				current.set(sourceSlot,
						remaining == 0 ? operations.empty() : operations.copyWithCount(source, remaining));
				actions.add(new Action<>(clicks, List.copyOf(current)));
			}

		}

	}

	private static <T> void arrangeTarget(
		List<T> current,
		List<T> target,
		List<Action<T>> actions,
		StackOperations<T> operations
	) {

		for (int targetSlot = 0; targetSlot < target.size(); targetSlot++) {

			if (stacksEqual(current.get(targetSlot), target.get(targetSlot), operations)) {
				continue;
			}

			int sourceSlot = findMatchingStack(current, target.get(targetSlot), targetSlot + 1, operations);

			if (sourceSlot < 0) {
				throw new IllegalArgumentException("Target layout contains an unavailable stack");
			}

			boolean targetWasEmpty = operations.isEmpty(current.get(targetSlot));
			T displaced = current.get(targetSlot);
			current.set(targetSlot, current.get(sourceSlot));
			current.set(sourceSlot, displaced);
			List<Integer> clicks = targetWasEmpty
					? List.of(sourceSlot, targetSlot)
					: List.of(sourceSlot, targetSlot, sourceSlot);
			actions.add(new Action<>(clicks, List.copyOf(current)));
		}

	}

	private static <T> int findMatchingStack(List<T> stacks, T wanted, int start, StackOperations<T> operations) {

		for (int slot = start; slot < stacks.size(); slot++) {

			if (stacksEqual(stacks.get(slot), wanted, operations)) {
				return slot;
			}

		}

		return -1;
	}

	private static <T> boolean layoutsEqual(List<T> first, List<T> second, StackOperations<T> operations) {

		for (int slot = 0; slot < first.size(); slot++) {

			if (!stacksEqual(first.get(slot), second.get(slot), operations)) {
				return false;
			}

		}

		return true;
	}

	private static <T> boolean stacksEqual(T first, T second, StackOperations<T> operations) {

		if (operations.isEmpty(first) || operations.isEmpty(second)) {
			return operations.isEmpty(first) && operations.isEmpty(second);
		}

		return operations.count(first) == operations.count(second) && operations.canMerge(first, second);
	}

	public record Action<T>(List<Integer> slots, List<T> expectedLayout) {
		public Action {
			slots = List.copyOf(slots);
			expectedLayout = List.copyOf(expectedLayout);
		}
	}

	public interface StackOperations<T> {
		boolean isEmpty(T stack);
		boolean canMerge(T first, T second);

		int count(T stack);
		int maximumCount(T stack);

		T copyWithCount(T stack, int count);
		T empty();
	}
}
