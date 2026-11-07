package fr.clixmods.livinghorizon.platform;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

//? if forge && >=1.20.2 {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
*///?} elif forge {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
*///?} elif neoforge {
/*import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
*///?} elif >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
*///?}

/**
 * The mod's one channel, server to client: the shared positions ({@code PositionsMessage}),
 * as bytes. Only a client that has the mod is sent anything; a server without the mod sends
 * nothing, and a client without it never hears of the channel.
 *
 * <p>Each loader has its own networking, and it changed with the game: Fabric's payloads came
 * in 1.20.5 (before, a named channel and a buffer), NeoForge's are the same from 1.20.6, and
 * Forge has had its channel builder since 1.20.2 (before, its network registry). The rest of the
 * mod only sees {@link #send}, {@link #canSend} and {@link #receiver}.
 *
 * <p>Loaded on a dedicated server too: nothing here may reach a class of the client's. What
 * the client does with a message is handed in by its own entry point, through {@link #receiver};
 * on Fabric and Quilt the client listens through {@code ClientNetwork}, which a server never loads.
 */
public final class Network {
    static final Identifier ID = Identifier.fromNamespaceAndPath("livinghorizon", "positions");

    /** Where a message goes once received; the client sets it. May run off the game's thread. */
    private static volatile Consumer<byte[]> receiver = data -> {
    };

    private Network() {
    }

    /** What the client does with a message: it hops to the game's thread itself. */
    public static void receiver(Consumer<byte[]> handler) {
        receiver = handler;
    }

    /** A message arrived. */
    static void receive(byte[] data) {
        receiver.accept(data);
    }

    //? if forge && >=1.20.2 {
    /*private static EventNetworkChannel channel;

    /^* On both sides, while the mod is built. ^/
    public static void register() {
        // The version first: optional() freezes the versions each side accepts to the one set so far.
        channel = ChannelBuilder.named(ID).networkProtocolVersion(1).optional().eventNetworkChannel();
        channel.addListener((CustomPayloadEvent event) -> {
            FriendlyByteBuf buf = event.getPayload();
            if (buf != null && event.getSource().isClientSide()) receiver.accept(buf.readByteArray());
            event.getSource().setPacketHandled(true);
        });
    }

    public static boolean canSend(ServerPlayer player) {
        return channel != null && channel.isRemotePresent(player.connection.getConnection());
    }

    public static void send(ServerPlayer player, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByteArray(data);
        channel.send(buf, PacketDistributor.PLAYER.with(player));
    }
    *///?} elif forge {
    /*private static EventNetworkChannel channel;

    /^* On both sides, while the mod is built. Any version is accepted: a side without the mod too. ^/
    public static void register() {
        channel = NetworkRegistry.ChannelBuilder.named(ID).networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(version -> true).serverAcceptedVersions(version -> true).eventNetworkChannel();
        channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            FriendlyByteBuf buf = event.getPayload();
            if (buf != null) receiver.accept(buf.readByteArray());
            event.getSource().get().setPacketHandled(true);
        });
    }

    public static boolean canSend(ServerPlayer player) {
        return channel != null && channel.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByteArray(data);
        player.connection.send(new ClientboundCustomPayloadPacket(ID, buf));
    }
    *///?} elif neoforge {
    /*/^* The message, as NeoForge carries it. ^/
    private record Payload(byte[] data) implements CustomPacketPayload {
        static final Type<Payload> TYPE = new Type<>(ID);
        static final StreamCodec<io.netty.buffer.ByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, Payload::data, Payload::new);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    /^* On both sides, from the mod's constructor: the payload is registered with the mod bus. ^/
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
                .playToClient(Payload.TYPE, Payload.CODEC, (payload, context) -> receiver.accept(payload.data())));
    }

    public static boolean canSend(ServerPlayer player) {
        return player.connection.hasChannel(Payload.TYPE);
    }

    public static void send(ServerPlayer player, byte[] data) {
        PacketDistributor.sendToPlayer(player, new Payload(data));
    }
    *///?} elif >=1.20.5 {
    /** The message, as the Fabric API carries it. */
    record Payload(byte[] data) implements CustomPacketPayload {
        static final Type<Payload> TYPE = new Type<>(ID);
        static final StreamCodec<io.netty.buffer.ByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, Payload::data, Payload::new);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    /** On both sides, from the common entry point: the payload's type is known to both. */
    public static void register() {
        // The Fabric API for 26.1 names the directions after the game's packets.
        //? if >=26.1 {
        /*PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
        *///?} else
        PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
    }

    public static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, Payload.TYPE);
    }

    public static void send(ServerPlayer player, byte[] data) {
        ServerPlayNetworking.send(player, new Payload(data));
    }
    //?} else {
    /*/^* On both sides, from the common entry point: nothing to declare before 1.20.5. ^/
    public static void register() {
    }

    public static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ID);
    }

    public static void send(ServerPlayer player, byte[] data) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByteArray(data);
        ServerPlayNetworking.send(player, ID, buf);
    }
    *///?}
}
