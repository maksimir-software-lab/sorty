package nitodeco.sorty.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import nitodeco.sorty.Sorty;

public record SortInventoryPayload(SortTarget target) implements CustomPacketPayload {
	public static final Type<SortInventoryPayload> TYPE = new Type<>(Sorty.id("sort_inventory_v2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SortInventoryPayload> CODEC = StreamCodec.of(
			(buffer, payload) -> buffer.writeEnum(payload.target),
			buffer -> new SortInventoryPayload(buffer.readEnum(SortTarget.class)));

	@Override
	public Type<SortInventoryPayload> type() {
		return TYPE;
	}
}
