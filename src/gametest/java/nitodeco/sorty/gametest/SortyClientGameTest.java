package nitodeco.sorty.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

@SuppressWarnings("UnstableApiUsage")
public final class SortyClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(SortyClientGameTest::assertKnownLocaleIds);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			context.takeScreenshot("sorty-startup-smoke");
		}

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
