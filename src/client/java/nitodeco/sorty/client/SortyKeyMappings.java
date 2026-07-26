package nitodeco.sorty.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SortyKeyMappings {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category
			.register(Identifier.fromNamespaceAndPath("sorty", "sorty"));

	private static KeyMapping sortInventory;

	private SortyKeyMappings() {
	}

	public static void register() {

		if (sortInventory != null) {
			throw new IllegalStateException("Sorty key mappings are already registered");
		}

		sortInventory = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sorty.sort_inventory",
				InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY));
	}

	public static KeyMapping sortInventory() {

		if (sortInventory == null) {
			throw new IllegalStateException("Sorty key mappings have not been registered");
		}

		return sortInventory;
	}
}
