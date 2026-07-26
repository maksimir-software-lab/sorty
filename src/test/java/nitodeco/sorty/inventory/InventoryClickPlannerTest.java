package nitodeco.sorty.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryClickPlannerTest {
	private static final FakeStack EMPTY = new FakeStack("", "", 0, 64);
	private static final InventoryClickPlanner.StackOperations<FakeStack> OPERATIONS = new InventoryClickPlanner.StackOperations<>() {
		@Override
		public boolean isEmpty(FakeStack stack) {
			return stack.count() == 0;
		}

		@Override
		public boolean canMerge(FakeStack first, FakeStack second) {
			return first.item().equals(second.item()) && first.components().equals(second.components());
		}

		@Override
		public int count(FakeStack stack) {
			return stack.count();
		}

		@Override
		public int maximumCount(FakeStack stack) {
			return stack.maximumCount();
		}

		@Override
		public FakeStack copyWithCount(FakeStack stack, int count) {
			return new FakeStack(stack.item(), stack.components(), count, stack.maximumCount());
		}

		@Override
		public FakeStack empty() {
			return EMPTY;
		}
	};

	@Test
	void mergesAndReordersUsingActionsThatAlwaysEmptyTheCursor() {
		List<FakeStack> source = List.of(stack("stone", 40), EMPTY, stack("dirt", 3), stack("stone", 30));
		List<FakeStack> target = List.of(stack("dirt", 3), stack("stone", 64), stack("stone", 6), EMPTY);

		assertPlanExecutes(source, target);
	}

	@Test
	void sortsACompletelyFullInventoryBySwappingStacks() {
		List<FakeStack> source = List.of(stack("zinc", 64), stack("copper", 64), stack("iron", 64));
		List<FakeStack> target = List.of(stack("copper", 64), stack("iron", 64), stack("zinc", 64));

		assertPlanExecutes(source, target);
	}

	@Test
	void keepsComponentBearingStacksSeparate() {
		List<FakeStack> source = List.of(stack("tool", "damaged", 1), stack("tool", "pristine", 1), EMPTY);
		List<FakeStack> target = List.of(stack("tool", "pristine", 1), stack("tool", "damaged", 1), EMPTY);

		assertPlanExecutes(source, target);
	}

	private static void assertPlanExecutes(List<FakeStack> source, List<FakeStack> target) {
		List<FakeStack> actual = new ArrayList<>(source);
		FakeStack cursor = EMPTY;
		List<InventoryClickPlanner.Action<FakeStack>> actions = InventoryClickPlanner.plan(source, target, OPERATIONS);

		for (InventoryClickPlanner.Action<FakeStack> action : actions) {

			for (int slot : action.slots()) {
				FakeStack slotStack = actual.get(slot);

				if (OPERATIONS.isEmpty(cursor)) {
					cursor = slotStack;
					actual.set(slot, EMPTY);
				} else if (OPERATIONS.isEmpty(slotStack)) {
					actual.set(slot, cursor);
					cursor = EMPTY;
				} else if (OPERATIONS.canMerge(cursor, slotStack)) {
					int moved = Math.min(slotStack.maximumCount() - slotStack.count(), cursor.count());
					actual.set(slot, OPERATIONS.copyWithCount(slotStack, slotStack.count() + moved));
					cursor = cursor.count() == moved ? EMPTY : OPERATIONS.copyWithCount(cursor, cursor.count() - moved);
				} else {
					actual.set(slot, cursor);
					cursor = slotStack;
				}

			}

			assertTrue(OPERATIONS.isEmpty(cursor));
			assertEquals(action.expectedLayout(), actual);
		}

		assertEquals(target, actual);
	}

	private static FakeStack stack(String item, int count) {
		return stack(item, "", count);
	}

	private static FakeStack stack(String item, String components, int count) {
		return new FakeStack(item, components, count, 64);
	}

	private record FakeStack(String item, String components, int count, int maximumCount) {
	}
}
