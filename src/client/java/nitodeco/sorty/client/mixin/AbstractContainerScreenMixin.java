package nitodeco.sorty.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import nitodeco.sorty.client.ClientSortController;
import nitodeco.sorty.client.SortyKeyMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void sorty$handleSortClick(
		double mouseX,
		double mouseY,
		int button,
		CallbackInfoReturnable<Boolean> callback
	) {

		if (ClientSortController.isMultiplayerSortActive()) {
			callback.setReturnValue(true);

			return;
		}

		if (!SortyKeyMappings.sortInventory().matchesMouse(button)) {
			return;
		}

		if (ClientSortController.trySort((AbstractContainerScreen<?>) (Object) this)) {
			callback.setReturnValue(true);
		}

	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void sorty$lockInputDuringSort(
		int keyCode,
		int scanCode,
		int modifiers,
		CallbackInfoReturnable<Boolean> callback
	) {

		if (ClientSortController.isMultiplayerSortActive()) {
			Minecraft minecraft = Minecraft.getInstance();

			if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
				ClientSortController.cancelMultiplayerSortAndClose();
			}

			callback.setReturnValue(true);

			return;
		}

		if (SortyKeyMappings.sortInventory().matches(keyCode, scanCode)
				&& ClientSortController.trySort((AbstractContainerScreen<?>) (Object) this)) {
			callback.setReturnValue(true);
		}

	}
}
