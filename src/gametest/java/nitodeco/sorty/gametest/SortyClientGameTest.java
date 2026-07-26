package nitodeco.sorty.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
			singleplayer.getClientWorld().waitForChunksRender();
			context.takeScreenshot("sorty-startup-smoke");
			testDefaultKeySort(context, singleplayer);
			testRemappedKeySort(context, singleplayer);
		}

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
		List<ItemStack> inventory = chaoticInventory();
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

		for (ResourceLocation resourceId : languageResources.keySet()) {
			String path = resourceId.getPath();
			String localeId = path.substring("lang/".length(), path.length() - ".json".length());

			if (!languages.containsKey(localeId)) {
				throw new AssertionError("Unknown Sorty locale ID: " + localeId);
			}

		}

	}
}
