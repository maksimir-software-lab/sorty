package nitodeco.sorty.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventorySortAlgorithmTest {
	private static final FakeStack EMPTY = new FakeStack("", "", 0, 64, false, false);
	private static final InventorySortAlgorithm.StackOperations<FakeStack> OPERATIONS =
			new InventorySortAlgorithm.StackOperations<>() {
				@Override
				public boolean isEmpty(FakeStack stack) {
					return stack.count() == 0;
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
					return new FakeStack(
							stack.itemId(),
							stack.components(),
							count,
							stack.maximumCount(),
							stack.blockItem(),
							stack.foodItem()
					);
				}

				@Override
				public boolean canMerge(FakeStack first, FakeStack second) {
					return first.itemId().equals(second.itemId())
							&& first.components().equals(second.components());
				}

				@Override
				public ItemTypeClassifier.SortKey sortKey(FakeStack stack) {
					String path = stack.itemId().substring(stack.itemId().indexOf(':') + 1);
					return ItemTypeClassifier.classify(path, stack.blockItem(), stack.foodItem());
				}

				@Override
				public String tieBreakKey(FakeStack stack) {
					return stack.itemId();
				}

				@Override
				public FakeStack empty() {
					return EMPTY;
				}
			};

	@Test
	void mergesCompatiblePartialStacksAndMovesEmptySlotsToEnd() {
		List<FakeStack> result = sort(slots(
				block("minecraft:stone", "", 40),
				EMPTY,
				block("minecraft:dirt", "", 3),
				block("minecraft:stone", "", 30)
		));

		assertEquals("minecraft:dirt", result.get(0).itemId());
		assertEquals(3, result.get(0).count());
		assertEquals("minecraft:stone", result.get(1).itemId());
		assertEquals(64, result.get(1).count());
		assertEquals(6, result.get(2).count());
		assertEquals(EMPTY, result.get(3));
	}

	@Test
	void componentBearingStacksRemainDistinctAndAdjacent() {
		List<FakeStack> result = sort(slots(
				block("minecraft:stone", "enchanted", 20),
				block("minecraft:stone", "ordinary", 20)
		));

		assertEquals("enchanted", result.get(0).components());
		assertEquals("ordinary", result.get(1).components());
		assertEquals(20, result.get(0).count());
		assertEquals(20, result.get(1).count());
	}

	@Test
	void fullInventoryPreservesEveryItem() {
		List<FakeStack> input = new ArrayList<>();
		for (int index = 0; index < 27; index++) {
			input.add(block(
					index % 2 == 0 ? "minecraft:stone" : "minecraft:dirt",
					Integer.toString(index),
					64
			));
		}

		List<FakeStack> result = sort(input);

		assertEquals(27, result.size());
		assertEquals(27 * 64, result.stream().mapToInt(FakeStack::count).sum());
		assertTrue(result.stream().noneMatch(OPERATIONS::isEmpty));
	}

	@Test
	void sortingIsIdempotentAndDoesNotReuseInputObjects() {
		List<FakeStack> input = slots(
				block("minecraft:stone", "", 12),
				block("minecraft:dirt", "", 7),
				block("minecraft:stone", "", 4)
		);

		List<FakeStack> once = sort(input);
		List<FakeStack> twice = sort(once);

		assertEquals(once, twice);
		assertEquals(12, input.get(0).count());
		assertEquals(4, input.get(2).count());
		assertNotSame(input.get(0), once.get(1));
	}

	@Test
	void registryIdentifierBreaksEqualTypeTies() {
		List<FakeStack> result = sort(slots(
				stack("example:z_item", "", 1),
				stack("example:a_item", "", 1)
		));

		assertEquals("example:a_item", result.get(0).itemId());
		assertEquals("example:z_item", result.get(1).itemId());
	}

	@Test
	void rejectsOverstackedInputWithoutTouchingIt() {
		FakeStack invalid = block("minecraft:stone", "", 65);

		assertThrows(IllegalArgumentException.class, () -> sort(slots(invalid)));
		assertEquals(65, invalid.count());
	}

	@Test
	void groupsByItemFormBeforeMaterial() {
		List<FakeStack> result = sort(slots(
				block("minecraft:spruce_planks", "", 4),
				stack("minecraft:iron_nugget", "", 2),
				stack("minecraft:iron_ingot", "", 3),
				block("minecraft:oak_planks", "", 5),
				stack("minecraft:gold_nugget", "", 2),
				stack("minecraft:copper_ingot", "", 3)
		));

		assertEquals(
				List.of(
						"minecraft:copper_ingot",
						"minecraft:iron_ingot",
						"minecraft:gold_nugget",
						"minecraft:iron_nugget",
						"minecraft:oak_planks",
						"minecraft:spruce_planks"
				),
				ids(result, 6)
		);
	}

	@Test
	void keepsLogsAndStrippedLogsInOneWoodVariantGroup() {
		List<FakeStack> result = sort(slots(
				block("minecraft:stripped_oak_wood", "", 1),
				block("minecraft:spruce_log", "", 1),
				block("minecraft:oak_wood", "", 1),
				block("minecraft:stripped_oak_log", "", 1),
				block("minecraft:oak_log", "", 1)
		));

		assertEquals(
				List.of(
						"minecraft:oak_log",
						"minecraft:stripped_oak_log",
						"minecraft:oak_wood",
						"minecraft:stripped_oak_wood",
						"minecraft:spruce_log"
				),
				ids(result, 5)
		);
	}

	@Test
	void keepsBuildingFormsTogether() {
		List<FakeStack> result = sort(slots(
				block("minecraft:stone_stairs", "", 1),
				block("minecraft:oak_slab", "", 1),
				block("minecraft:cobblestone_stairs", "", 1),
				block("minecraft:stone_slab", "", 1)
		));

		assertEquals(
				List.of(
						"minecraft:oak_slab",
						"minecraft:stone_slab",
						"minecraft:cobblestone_stairs",
						"minecraft:stone_stairs"
				),
				ids(result, 4)
		);
	}

	private static List<String> ids(List<FakeStack> stacks, int count) {
		return stacks.subList(0, count).stream().map(FakeStack::itemId).toList();
	}

	private static List<FakeStack> sort(List<FakeStack> slots) {
		return InventorySortAlgorithm.sort(slots, OPERATIONS);
	}

	private static List<FakeStack> slots(FakeStack... initial) {
		List<FakeStack> result = new ArrayList<>(List.of(initial));
		while (result.size() < 27) {
			result.add(EMPTY);
		}
		return result;
	}

	private static FakeStack stack(String itemId, String components, int count) {
		return new FakeStack(itemId, components, count, 64, false, false);
	}

	private static FakeStack block(String itemId, String components, int count) {
		return new FakeStack(itemId, components, count, 64, true, false);
	}

	private record FakeStack(
			String itemId,
			String components,
			int count,
			int maximumCount,
			boolean blockItem,
			boolean foodItem
	) {
	}
}
