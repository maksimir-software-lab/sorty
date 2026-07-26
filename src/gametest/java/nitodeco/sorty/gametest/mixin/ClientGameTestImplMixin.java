package nitodeco.sorty.gametest.mixin;

import net.fabricmc.fabric.impl.client.gametest.util.ClientGameTestImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ClientGameTestImpl.class, remap = false)
public abstract class ClientGameTestImplMixin {
	private static final int DEFAULT_WORLD_LOAD_TIMEOUT_TICKS = 1200;
	private static final int CI_WORLD_LOAD_TIMEOUT_TICKS = 3600;

	@ModifyConstant(method = "waitForWorldLoad", constant = @Constant(intValue = DEFAULT_WORLD_LOAD_TIMEOUT_TICKS), require = 1)
	private static int sorty$extendWorldLoadTimeout(int timeoutTicks) {
		return CI_WORLD_LOAD_TIMEOUT_TICKS;
	}
}
