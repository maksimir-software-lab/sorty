package nitodeco.sorty.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import nitodeco.sorty.client.ClientSortController;
import nitodeco.sorty.client.MultiplayerSortExecutor;
import nitodeco.sorty.client.SortyKeyMappings;
import nitodeco.sorty.inventory.PlayerInventorySorter;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("UnstableApiUsage")
public final class SortyClientGameTest implements FabricClientGameTest {
	private static final int HOTBAR_SENTINEL_SLOT = 0;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(2);
			client.options.simulationDistance().set(5);
			client.options.mipmapLevels().set(0);
			client.options.enableVsync().set(false);
			client.options.entityShadows().set(false);
			assertKnownLocaleIds(client);
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			context.takeScreenshot("sorty-startup-smoke");
			testDefaultKeySort(context, singleplayer);
			testRemappedKeySort(context, singleplayer);
			testBundleFillPolicy(context, singleplayer);
			testPlayerSortFromCraftingScreen(context, singleplayer);
			testChestTargetSelection(context, singleplayer);
		}

	}

	private static void testBundleFillPolicy(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		List<ItemStack> inventory = emptyMainInventory();
		inventory.set(3, stack(Items.REDSTONE, 7));
		inventory.set(5, bundleWith(Items.GUNPOWDER, 2));
		inventory.set(9, stack(Items.GUNPOWDER, 10));
		seedMainInventory(context, singleplayer, inventory);
		openInventory(context);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
		context.waitFor(client -> {
			ItemStack bundle = client.player.getInventory().getItem(PlayerInventorySorter.MAIN_INVENTORY_START + 5);

			return bundleCount(bundle, Items.GUNPOWDER) == 12
					&& bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).size() == 1;
		});
		assertBundlePolicy(context, singleplayer);
		context.takeScreenshot("sorty-server-bundle-policy");
		context.setScreen(() -> null);

		seedMainInventory(context, singleplayer, inventory);
		openInventory(context);
		triggerClientFallbackPlayerSort(context);
		context.waitFor(client -> {
			ItemStack bundle = client.player.getInventory().getItem(PlayerInventorySorter.MAIN_INVENTORY_START + 5);

			return bundleCount(bundle, Items.GUNPOWDER) == 12 && !MultiplayerSortExecutor.isActive();
		});
		assertBundlePolicy(context, singleplayer);
		context.takeScreenshot("sorty-client-fallback-bundle-policy");
		context.setScreen(() -> null);
	}

	private static void testPlayerSortFromCraftingScreen(
		ClientGameTestContext context,
		TestSingleplayerContext singleplayer
	) {
		seedInventory(context, singleplayer);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new CraftingMenu(id, inventory),
					Component.literal("Crafting")));
		});
		context.waitForScreen(CraftingScreen.class);
		triggerSortOnPlayerSlot(context);
		waitForSortedInventory(context, "crafting screen player sort");
		assertServerInventory(singleplayer, expectedInventory(), "crafting screen player sort");
		context.takeScreenshot("sorty-crafting-player-sort");
		context.setScreen(() -> null);
	}

	private static void testChestTargetSelection(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		AtomicReference<SimpleContainer> chestReference = new AtomicReference<>();
		seedInventory(context, singleplayer);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			SimpleContainer chest = new SimpleContainer(27);
			chest.setItem(0, stack(Items.STONE, 30));
			chest.setItem(8, stack(Items.DIRT, 4));
			chest.setItem(12, stack(Items.STONE, 40));
			chestReference.set(chest);
			player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> ChestMenu.threeRows(id, inventory, chest),
					Component.literal("Chest")));
		});
		context.waitForScreen(ContainerScreen.class);
		triggerSortOnContainerSlot(context);
		context.waitFor(client -> chestMatches(client.player.containerMenu.getSlot(0).container));
		assertChest(chestReference.get(), "server chest target");
		assertClientInventory(context, chaoticInventory(), "player inventory after chest target");
		assertServerInventory(singleplayer, chaoticInventory(), "player inventory after chest target");

		seedInventory(context, singleplayer);
		triggerSortOnPlayerSlot(context);
		waitForSortedInventory(context, "chest screen player target");
		assertServerInventory(singleplayer, expectedInventory(), "chest screen player target");
		assertChest(chestReference.get(), "chest after player target");
		context.takeScreenshot("sorty-chest-target-selection");
		context.setScreen(() -> null);
	}

	private static void testDefaultKeySort(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		seedInventory(context, singleplayer);
		openInventory(context);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
		waitForSortedInventory(context, "default key sort");
		assertServerInventory(singleplayer, expectedInventory(), "default key sort");
		assertHotbarSentinel(context, singleplayer);
		context.takeScreenshot("sorty-default-key-sort");
		context.setScreen(() -> null);
	}

	private static void testRemappedKeySort(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		seedInventory(context, singleplayer);
		InputConstants.Key originalKey = context
				.computeOnClient(client -> InputConstants.getKey(SortyKeyMappings.sortInventory().saveString()));
		InputConstants.Key remappedKey = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R);

		try {
			context.runOnClient(client -> {
				SortyKeyMappings.sortInventory().setKey(remappedKey);
				KeyMapping.resetMapping();
			});
			openInventory(context);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
			context.waitTicks(10);
			assertClientInventory(context, chaoticInventory(), "old key after remapping");
			assertServerInventory(singleplayer, chaoticInventory(), "old key after remapping");
			context.getInput().pressKey(remappedKey);
			waitForSortedInventory(context, "remapped key sort");
			assertServerInventory(singleplayer, expectedInventory(), "remapped key sort");
			assertHotbarSentinel(context, singleplayer);
			context.takeScreenshot("sorty-remapped-key-sort");
		} finally {
			context.runOnClient(client -> {
				SortyKeyMappings.sortInventory().setKey(originalKey);
				KeyMapping.resetMapping();
			});
			context.setScreen(() -> null);
		}

	}

	private static void seedInventory(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		seedMainInventory(context, singleplayer, chaoticInventory());
	}

	private static void seedMainInventory(
		ClientGameTestContext context,
		TestSingleplayerContext singleplayer,
		List<ItemStack> inventory
	) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getInventory().clearContent();
			player.getInventory().setItem(HOTBAR_SENTINEL_SLOT, hotbarSentinel());

			for (int offset = 0; offset < inventory.size(); offset++) {
				player.getInventory().setItem(PlayerInventorySorter.MAIN_INVENTORY_START + offset,
						inventory.get(offset).copy());
			}

			player.getInventory().setChanged();
			player.inventoryMenu.broadcastChanges();
		});
		context.waitFor(client -> inventoryMatches(client.player.getInventory(), inventory));
		assertHotbarSentinel(context, singleplayer);
	}

	private static void triggerSortOnPlayerSlot(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen();
			Slot playerSlot = screen.getMenu().slots.stream()
					.filter(slot -> slot.container == client.player.getInventory())
					.filter(slot -> slot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START)
					.filter(slot -> slot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END).findFirst()
					.orElseThrow();

			if (!ClientSortController.trySort(screen, playerSlot)) {
				throw new AssertionError("Player inventory sort was not accepted");
			}

		});
	}

	private static void triggerSortOnContainerSlot(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen();
			Slot containerSlot = screen.getMenu().slots.getFirst();

			if (!ClientSortController.trySort(screen, containerSlot)) {
				throw new AssertionError("Open container sort was not accepted");
			}

		});
	}

	private static void triggerClientFallbackPlayerSort(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen();
			List<Slot> playerSlots = screen.getMenu().slots.stream()
					.filter(slot -> slot.container == client.player.getInventory())
					.filter(slot -> slot.getContainerSlot() >= PlayerInventorySorter.MAIN_INVENTORY_START)
					.filter(slot -> slot.getContainerSlot() < PlayerInventorySorter.MAIN_INVENTORY_END).toList();

			if (!MultiplayerSortExecutor.start(screen, playerSlots)) {
				throw new AssertionError("Client fallback sort was not accepted");
			}

		});
	}

	private static void openInventory(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTick();
	}

	private static void waitForSortedInventory(ClientGameTestContext context, String scenario) {
		List<ItemStack> expected = expectedInventory();

		try {
			context.waitFor(client -> inventoryMatches(client.player.getInventory(), expected));
		} catch (AssertionError timeout) {
			assertClientInventory(context, expected, scenario);

			throw timeout;
		}

	}

	private static void assertClientInventory(
		ClientGameTestContext context,
		List<ItemStack> expected,
		String scenario
	) {
		context.runOnClient(client -> assertInventory(client.player.getInventory(), expected, "client " + scenario));
	}

	private static void assertServerInventory(
		TestSingleplayerContext singleplayer,
		List<ItemStack> expected,
		String scenario
	) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			assertInventory(player.getInventory(), expected, "server " + scenario);
		});
	}

	private static void assertHotbarSentinel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> assertStack(hotbarSentinel(),
				client.player.getInventory().getItem(HOTBAR_SENTINEL_SLOT), "client hotbar sentinel"));
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			assertStack(hotbarSentinel(), player.getInventory().getItem(HOTBAR_SENTINEL_SLOT),
					"server hotbar sentinel");
		});
	}

	private static boolean inventoryMatches(Container inventory, List<ItemStack> expected) {

		for (int offset = 0; offset < expected.size(); offset++) {

			if (!ItemStack.matches(expected.get(offset),
					inventory.getItem(PlayerInventorySorter.MAIN_INVENTORY_START + offset))) {
				return false;
			}

		}

		return true;
	}

	private static void assertInventory(Container inventory, List<ItemStack> expected, String scenario) {

		for (int offset = 0; offset < expected.size(); offset++) {
			int slot = PlayerInventorySorter.MAIN_INVENTORY_START + offset;

			if (!ItemStack.matches(expected.get(offset), inventory.getItem(slot))) {
				throw new AssertionError("%s slot %d: expected %s, got %s; full inventory: %s".formatted(scenario, slot,
						expected.get(offset), inventory.getItem(slot), inventoryContents(inventory)));
			}

		}

	}

	private static List<ItemStack> inventoryContents(Container inventory) {
		return java.util.stream.IntStream
				.range(PlayerInventorySorter.MAIN_INVENTORY_START, PlayerInventorySorter.MAIN_INVENTORY_END)
				.mapToObj(inventory::getItem).toList();
	}

	private static void assertStack(ItemStack expected, ItemStack actual, String description) {

		if (!ItemStack.matches(expected, actual)) {
			throw new AssertionError("%s: expected %s, got %s".formatted(description, expected, actual));
		}

	}

	private static List<ItemStack> chaoticInventory() {
		return List.of(stack(Items.BONE, 5), stack(Items.IRON_INGOT, 40), stack(Items.OAK_PLANKS, 8),
				stack(Items.APPLE, 3), stack(Items.IRON_BLOCK, 2), stack(Items.DIAMOND_SWORD), ItemStack.EMPTY,
				stack(Items.IRON_INGOT, 30), stack(Items.OAK_LOG, 3), stack(Items.CHEST), stack(Items.DIAMOND_PICKAXE),
				stack(Items.OAK_SAPLING, 2), stack(Items.IRON_HELMET), stack(Items.WATER_BUCKET), stack(Items.STICK, 4),
				stack(Items.RAW_COPPER, 4), stack(Items.GOLD_NUGGET, 6), stack(Items.DIAMOND, 2),
				stack(Items.REDSTONE, 7), stack(Items.IRON_ORE, 5), stack(Items.STRIPPED_OAK_LOG, 2),
				stack(Items.OAK_WOOD), stack(Items.STRIPPED_OAK_WOOD), stack(Items.STONE_SLAB, 6),
				stack(Items.STONE, 40), stack(Items.STONE, 10), namedStack(Items.STONE, 5, "Named stone"));
	}

	private static List<ItemStack> expectedInventory() {
		return List.of(stack(Items.RAW_COPPER, 4), stack(Items.IRON_INGOT, 64), stack(Items.IRON_INGOT, 6),
				stack(Items.GOLD_NUGGET, 6), stack(Items.DIAMOND, 2), stack(Items.REDSTONE, 7),
				stack(Items.IRON_ORE, 5), stack(Items.IRON_BLOCK, 2), stack(Items.OAK_LOG, 3),
				stack(Items.STRIPPED_OAK_LOG, 2), stack(Items.OAK_WOOD), stack(Items.STRIPPED_OAK_WOOD),
				stack(Items.OAK_PLANKS, 8), stack(Items.STONE, 50), namedStack(Items.STONE, 5, "Named stone"),
				stack(Items.STONE_SLAB, 6), stack(Items.CHEST), stack(Items.DIAMOND_PICKAXE),
				stack(Items.DIAMOND_SWORD), stack(Items.IRON_HELMET), stack(Items.APPLE, 3),
				stack(Items.OAK_SAPLING, 2), stack(Items.WATER_BUCKET), stack(Items.BONE, 5), stack(Items.STICK, 4),
				ItemStack.EMPTY, ItemStack.EMPTY);
	}

	private static List<ItemStack> emptyMainInventory() {
		List<ItemStack> inventory = new ArrayList<>(
				PlayerInventorySorter.MAIN_INVENTORY_END - PlayerInventorySorter.MAIN_INVENTORY_START);

		while (inventory.size() < PlayerInventorySorter.MAIN_INVENTORY_END
				- PlayerInventorySorter.MAIN_INVENTORY_START) {
			inventory.add(ItemStack.EMPTY);
		}

		return inventory;
	}

	private static ItemStack bundleWith(Item item, int count) {
		ItemStack bundle = stack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(item, count))));

		return bundle;
	}

	private static int bundleCount(ItemStack bundle, Item item) {
		return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopyStream()
				.filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static void assertBundlePolicy(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> assertBundlePolicy(client.player.getInventory(), "client bundle policy"));
		singleplayer.getServer()
				.runOnServer(server -> assertBundlePolicy(server.getPlayerList().getPlayers().getFirst().getInventory(),
						"server bundle policy"));
	}

	private static void assertBundlePolicy(Container inventory, String scenario) {
		ItemStack bundle = inventory.getItem(PlayerInventorySorter.MAIN_INVENTORY_START + 5);
		BundleContents contents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

		if (bundleCount(bundle, Items.GUNPOWDER) != 12 || contents.size() != 1) {
			throw new AssertionError(scenario + ": expected only 12 gunpowder in anchored bundle, got " + contents);
		}

		int looseGunpowder = inventoryContents(inventory).stream().filter(stack -> stack.is(Items.GUNPOWDER))
				.mapToInt(ItemStack::getCount).sum();
		int looseRedstone = inventoryContents(inventory).stream().filter(stack -> stack.is(Items.REDSTONE))
				.mapToInt(ItemStack::getCount).sum();

		if (looseGunpowder != 0 || looseRedstone != 7) {
			throw new AssertionError(scenario + ": expected no loose gunpowder and 7 loose redstone, got "
					+ inventoryContents(inventory));
		}

	}

	private static boolean chestMatches(Container chest) {
		return chest.getItem(0).is(Items.DIRT) && chest.getItem(0).getCount() == 4 && chest.getItem(1).is(Items.STONE)
				&& chest.getItem(1).getCount() == 64 && chest.getItem(2).is(Items.STONE)
				&& chest.getItem(2).getCount() == 6;
	}

	private static void assertChest(Container chest, String scenario) {

		if (!chestMatches(chest)) {
			throw new AssertionError(scenario + ": unexpected contents " + chest);
		}

	}

	private static ItemStack stack(Item item) {
		return stack(item, 1);
	}

	private static ItemStack hotbarSentinel() {
		return stack(Items.TOTEM_OF_UNDYING);
	}

	private static ItemStack stack(Item item, int count) {
		return new ItemStack(item, count);
	}

	private static ItemStack namedStack(Item item, int count, String name) {
		ItemStack stack = stack(item, count);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));

		return stack;
	}

	private static void assertKnownLocaleIds(Minecraft client) {
		var languages = client.getLanguageManager().getLanguages();
		var languageResources = client.getResourceManager().listResources("lang",
				id -> id.getNamespace().equals("sorty") && id.getPath().endsWith(".json"));

		for (Identifier resourceId : languageResources.keySet()) {
			String path = resourceId.getPath();
			String localeId = path.substring("lang/".length(), path.length() - ".json".length());

			if (!languages.containsKey(localeId)) {
				throw new AssertionError("Unknown Sorty locale ID: " + localeId);
			}

		}

	}
}
