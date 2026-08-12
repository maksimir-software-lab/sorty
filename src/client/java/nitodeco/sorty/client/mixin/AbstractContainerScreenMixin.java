package nitodeco.sorty.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import nitodeco.sorty.client.ClientSortController;
import nitodeco.sorty.client.SortyKeyMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Invoker("getHoveredSlot")
	protected abstract Slot sorty$getHoveredSlot(double mouseX, double mouseY);

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void sorty$handleSortClick(
		MouseButtonEvent event,
		boolean doubleClick,
		CallbackInfoReturnable<Boolean> callback
	) {

		if (ClientSortController.isMultiplayerSortActive()) {
			callback.setReturnValue(true);

			return;
		}

		if (!SortyKeyMappings.sortInventory().matchesMouse(event)) {
			return;
		}

		Slot clickedSlot = sorty$getHoveredSlot(event.x(), event.y());

		if (ClientSortController.trySort((AbstractContainerScreen<?>) (Object) this, clickedSlot)) {
			callback.setReturnValue(true);
		}

	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void sorty$lockInputDuringSort(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {

		if (ClientSortController.isMultiplayerSortActive()) {
			Minecraft minecraft = Minecraft.getInstance();

			if (event.key() == 256 || minecraft.options.keyInventory.matches(event)) {
				ClientSortController.cancelMultiplayerSortAndClose();
			}

			callback.setReturnValue(true);

			return;
		}

		if (SortyKeyMappings.sortInventory().matches(event)
				&& ClientSortController.trySort((AbstractContainerScreen<?>) (Object) this, hoveredSlot)) {
			callback.setReturnValue(true);
		}

	}
}
