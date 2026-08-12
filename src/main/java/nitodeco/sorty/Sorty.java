package nitodeco.sorty;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import nitodeco.sorty.inventory.PlayerInventorySorter;
import nitodeco.sorty.network.SortInventoryPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sorty implements ModInitializer {
	public static final String MOD_ID = "sorty";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playC2S().register(SortInventoryPayload.TYPE, SortInventoryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SortInventoryPayload.TYPE, (payload, context) -> context.server()
				.execute(() -> PlayerInventorySorter.sortIfAllowed(context.player(), payload.target())));
		LOGGER.info("Sorty initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
