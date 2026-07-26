package nitodeco.sorty.inventory;

import java.util.Set;

final class ItemTypeClassifier {
	private static final int RESOURCES = 0;
	private static final int STORAGE_BLOCKS = 1;
	private static final int BUILDING_BLOCKS = 2;
	private static final int FUNCTIONAL_BLOCKS = 3;
	private static final int TOOLS = 4;
	private static final int WEAPONS = 5;
	private static final int ARMOR = 6;
	private static final int FOOD = 7;
	private static final int PLANTS = 8;
	private static final int MISCELLANEOUS = 9;

	private static final Set<String> STORAGE_MATERIALS = Set.of("amethyst", "coal", "copper", "diamond", "emerald",
			"gold", "iron", "lapis", "netherite", "quartz", "raw_copper", "raw_gold", "raw_iron", "redstone");

	private static final Set<String> FUNCTIONAL_BLOCK_NAMES = Set.of("anvil", "barrel", "beacon", "blast_furnace",
			"brewing_stand", "cartography_table", "chest", "chiseled_bookshelf", "composter", "crafting_table",
			"dispenser", "dropper", "enchanting_table", "ender_chest", "fletching_table", "furnace", "grindstone",
			"hopper", "jukebox", "lectern", "loom", "note_block", "shulker_box", "smithing_table", "smoker",
			"stonecutter");

	private ItemTypeClassifier() {
	}

	static SortKey classify(String itemPath, boolean blockItem, boolean foodItem) {

		if (itemPath.startsWith("raw_") && !itemPath.endsWith("_block")) {
			return key(RESOURCES, 0, "raw_material", itemPath.substring(4), 0);
		}

		if (itemPath.endsWith("_ingot")) {
			return suffixKey(RESOURCES, 1, "ingot", itemPath, "_ingot");
		}

		if (itemPath.endsWith("_nugget")) {
			return suffixKey(RESOURCES, 2, "nugget", itemPath, "_nugget");
		}

		if (isGem(itemPath)) {
			return key(RESOURCES, 3, "gem", gemMaterial(itemPath), 0);
		}

		if (itemPath.endsWith("_dust") || itemPath.endsWith("_powder") || itemPath.equals("redstone")
				|| itemPath.equals("gunpowder")) {
			return key(RESOURCES, 4, "dust", removeAnySuffix(itemPath, "_dust", "_powder"), 0);
		}

		if (itemPath.endsWith("_ore") || itemPath.equals("ancient_debris")) {
			boolean deepslate = itemPath.startsWith("deepslate_");
			String material = deepslate ? itemPath.substring("deepslate_".length()) : itemPath;

			return key(RESOURCES, 5, "ore", removeAnySuffix(material, "_ore"), deepslate ? 1 : 0);
		}

		if (itemPath.endsWith("_block")) {
			String material = itemPath.substring(0, itemPath.length() - "_block".length());

			if (STORAGE_MATERIALS.contains(material)) {
				return key(STORAGE_BLOCKS, 0, "storage_block", material, 0);
			}

		}

		SortKey buildingForm = classifyBuildingForm(itemPath);

		if (buildingForm != null) {
			return buildingForm;
		}

		if (isFunctionalBlock(itemPath)) {
			return key(FUNCTIONAL_BLOCKS, 0, "functional_block", itemPath, 0);
		}

		SortKey equipment = classifyEquipment(itemPath);

		if (equipment != null) {
			return equipment;
		}

		if (foodItem) {
			return key(FOOD, 0, "food", itemPath, 0);
		}

		if (isPlant(itemPath)) {
			return key(PLANTS, 0, "plant", itemPath, 0);
		}

		if (blockItem) {
			return key(BUILDING_BLOCKS, 2, "full_block", itemPath, 0);
		}

		SortKey miscellaneousType = classifyMiscellaneousType(itemPath);

		return miscellaneousType != null ? miscellaneousType : key(MISCELLANEOUS, 99, "miscellaneous", itemPath, 0);
	}

	private static SortKey classifyBuildingForm(String path) {

		if (isLog(path)) {
			boolean stripped = path.startsWith("stripped_");
			String unstripped = stripped ? path.substring("stripped_".length()) : path;
			boolean woodForm = unstripped.endsWith("_wood") || unstripped.endsWith("_hyphae");
			String material = removeAnySuffix(unstripped, "_log", "_wood", "_stem", "_hyphae");
			int formOrder = (woodForm ? 2 : 0) + (stripped ? 1 : 0);

			return key(BUILDING_BLOCKS, 0, "log", material, formOrder);
		}

		if (path.endsWith("_planks")) {
			return suffixKey(BUILDING_BLOCKS, 1, "planks", path, "_planks");
		}

		if (path.endsWith("_slab")) {
			return suffixKey(BUILDING_BLOCKS, 3, "slab", path, "_slab");
		}

		if (path.endsWith("_stairs")) {
			return suffixKey(BUILDING_BLOCKS, 4, "stairs", path, "_stairs");
		}

		if (path.endsWith("_wall")) {
			return suffixKey(BUILDING_BLOCKS, 5, "wall", path, "_wall");
		}

		if (path.endsWith("_fence")) {
			return suffixKey(BUILDING_BLOCKS, 6, "fence", path, "_fence");
		}

		if (path.endsWith("_fence_gate")) {
			return suffixKey(BUILDING_BLOCKS, 7, "fence_gate", path, "_fence_gate");
		}

		if (path.endsWith("_door") && !path.endsWith("_trapdoor")) {
			return suffixKey(BUILDING_BLOCKS, 8, "door", path, "_door");
		}

		if (path.endsWith("_trapdoor")) {
			return suffixKey(BUILDING_BLOCKS, 9, "trapdoor", path, "_trapdoor");
		}

		if (path.endsWith("_hanging_sign")) {
			return suffixKey(BUILDING_BLOCKS, 11, "hanging_sign", path, "_hanging_sign");
		}

		if (path.endsWith("_sign")) {
			return suffixKey(BUILDING_BLOCKS, 10, "sign", path, "_sign");
		}

		if (path.endsWith("_button")) {
			return suffixKey(BUILDING_BLOCKS, 12, "button", path, "_button");
		}

		if (path.endsWith("_pressure_plate")) {
			return suffixKey(BUILDING_BLOCKS, 13, "pressure_plate", path, "_pressure_plate");
		}

		return null;
	}

	private static SortKey classifyEquipment(String path) {

		if (path.endsWith("_pickaxe")) {
			return suffixKey(TOOLS, 0, "pickaxe", path, "_pickaxe");
		}

		if (path.endsWith("_axe")) {
			return suffixKey(TOOLS, 1, "axe", path, "_axe");
		}

		if (path.endsWith("_shovel")) {
			return suffixKey(TOOLS, 2, "shovel", path, "_shovel");
		}

		if (path.endsWith("_hoe")) {
			return suffixKey(TOOLS, 3, "hoe", path, "_hoe");
		}

		if (path.equals("shears") || path.equals("brush") || path.equals("fishing_rod")
				|| path.equals("flint_and_steel")) {
			return key(TOOLS, 4, "utility_tool", path, 0);
		}

		if (path.endsWith("_sword")) {
			return suffixKey(WEAPONS, 0, "sword", path, "_sword");
		}

		if (path.equals("bow") || path.equals("crossbow") || path.equals("trident") || path.equals("mace")
				|| path.endsWith("_spear")) {
			return key(WEAPONS, 1, "ranged_or_special_weapon", path, 0);
		}

		if (path.endsWith("_helmet")) {
			return suffixKey(ARMOR, 0, "helmet", path, "_helmet");
		}

		if (path.endsWith("_chestplate")) {
			return suffixKey(ARMOR, 1, "chestplate", path, "_chestplate");
		}

		if (path.endsWith("_leggings")) {
			return suffixKey(ARMOR, 2, "leggings", path, "_leggings");
		}

		if (path.endsWith("_boots")) {
			return suffixKey(ARMOR, 3, "boots", path, "_boots");
		}

		return null;
	}

	private static SortKey classifyMiscellaneousType(String path) {
		String[] suffixes = {
			"_bucket",
			"_boat",
			"_minecart",
			"_spawn_egg",
			"_potion",
			"_book",
			"_smithing_template",
			"_pottery_sherd",
			"_music_disc",
			"_banner",
			"_bed",
			"_candle",
			"_dye"
		};

		for (int index = 0; index < suffixes.length; index++) {
			String suffix = suffixes[index];

			if (path.endsWith(suffix)) {
				return suffixKey(MISCELLANEOUS, index, suffix.substring(1), path, suffix);
			}

		}

		return null;
	}

	private static boolean isLog(String path) {
		return path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae");
	}

	private static boolean isFunctionalBlock(String path) {
		return FUNCTIONAL_BLOCK_NAMES.contains(path) || path.endsWith("_shulker_box") || path.endsWith("_chest");
	}

	private static boolean isGem(String path) {
		return path.equals("diamond") || path.equals("emerald") || path.equals("quartz") || path.endsWith("_crystal")
				|| path.endsWith("_crystals") || path.endsWith("_shard");
	}

	private static String gemMaterial(String path) {
		return removeAnySuffix(path, "_crystal", "_crystals", "_shard");
	}

	private static boolean isPlant(String path) {
		return path.endsWith("_sapling") || path.endsWith("_seeds") || path.endsWith("_flower")
				|| path.endsWith("_leaves") || path.endsWith("_fungus");
	}

	private static SortKey suffixKey(int category, int typeOrder, String type, String path, String suffix) {
		return key(category, typeOrder, type, path.substring(0, path.length() - suffix.length()), 0);
	}

	private static SortKey key(int category, int typeOrder, String type, String variant, int formOrder) {
		return new SortKey(category, typeOrder, type, variant, formOrder);
	}

	private static String removeAnySuffix(String value, String... suffixes) {

		for (String suffix : suffixes) {

			if (value.endsWith(suffix)) {
				return value.substring(0, value.length() - suffix.length());
			}

		}

		return value;
	}

	record SortKey(int category, int typeOrder, String type, String variant,
			int formOrder) implements Comparable<SortKey> {
		@Override
		public int compareTo(SortKey other) {
			int result = Integer.compare(category, other.category);

			if (result == 0) {
				result = Integer.compare(typeOrder, other.typeOrder);
			}

			if (result == 0) {
				result = type.compareTo(other.type);
			}

			if (result == 0) {
				result = variant.compareTo(other.variant);
			}

			if (result == 0) {
				result = Integer.compare(formOrder, other.formOrder);
			}

			return result;
		}
	}
}
