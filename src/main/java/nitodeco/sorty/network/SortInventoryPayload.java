package nitodeco.sorty.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import nitodeco.sorty.Sorty;

public record SortInventoryPayload() implements CustomPacketPayload {
	public static final SortInventoryPayload INSTANCE = new SortInventoryPayload();
	public static final Type<SortInventoryPayload> TYPE =
			new Type<>(Sorty.id("sort_inventory"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SortInventoryPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<SortInventoryPayload> type() {
		return TYPE;
	}
}
