package su.plo.voice.server.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import su.plo.voice.client.VoiceClient;
import su.plo.voice.common.packets.Packet;
import su.plo.voice.common.packets.tcp.*;
import su.plo.voice.server.PlayerManager;
import su.plo.voice.server.VoiceServer;
import su.plo.voice.server.config.ServerConfig;
import su.plo.voice.server.socket.SocketClientUDP;
import su.plo.voice.server.socket.SocketServerUDP;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public abstract class ServerNetworkHandler {
    public static Map<UUID, UUID> playerToken = new ConcurrentHashMap<>();

    private static ScheduledExecutorService executor;

    public static void reconnectClient(ServerPlayer player) {
        execute(() -> {
            disconnectClient(player.getUUID());
            UUID token = UUID.randomUUID();
            playerToken.put(player.getUUID(), token);

            try {
                sendTo(new ServerConnectPacket(token.toString(),
                                VoiceServer.getServerConfig().getProxyIp() != null && !VoiceServer.getServerConfig().getProxyIp().isEmpty()
                                        ? VoiceServer.getServerConfig().getProxyIp()
                                        : VoiceServer.getServerConfig().getIp(),
                                VoiceServer.getServerConfig().getProxyPort() != 0
                                        ? VoiceServer.getServerConfig().getProxyPort()
                                        : VoiceServer.getServerConfig().getPort(),
                                VoiceServer.getPlayerManager().hasPermission(player.getUUID(), "voice.priority")),
                        player);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void disconnectClient(UUID uuid) {
        SocketClientUDP clientUDP = SocketServerUDP.clients.get(uuid);

        try {
            if (clientUDP != null) {
                clientUDP.close();
                sendToClients(new ClientDisconnectedPacket(uuid), null);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void sendTo(Packet packet, ServerPlayer player) throws IOException {
        player.connection.send(new ClientboundCustomPayloadPacket(VoiceServer.PLASMO_VOICE,
                new FriendlyByteBuf(Unpooled.wrappedBuffer(PacketTCP.write(packet)))));
    }

    public static void sendToClients(Packet packet, UUID except) {
        execute(() -> {
            try {
                byte[] pkt = PacketTCP.write(packet);
                Enumeration<UUID> it = SocketServerUDP.clients.keys();
                while (it.hasMoreElements()) {
                    UUID uuid = it.nextElement();
                    if (!uuid.equals(except)) {
                        ServerPlayer player = PlayerManager.getByUUID(uuid);

                        player.connection.send(new ClientboundCustomPayloadPacket(
                                VoiceServer.PLASMO_VOICE,
                                new FriendlyByteBuf(Unpooled.wrappedBuffer(pkt))));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    protected static void execute(Runnable runnable) {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) return;

        try {
            executor.submit(runnable);
        } catch (RejectedExecutionException e) {
            VoiceClient.LOGGER.warn("Failed to execute: {}", e.getMessage());
        }
    }

    protected static Optional<ScheduledFuture<?>> schedule(Runnable runnable, long delay, TimeUnit unit) {
        if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
            try {
                return Optional.of(executor.schedule(runnable, delay, unit));
            } catch (RejectedExecutionException e) {
                VoiceClient.LOGGER.warn("Failed to execute: {}", e.getMessage());
            }
        }

        return Optional.empty();
    }

    public ServerNetworkHandler() {
    }

    public boolean isVanillaPlayer(ServerPlayer player) {
        return true;
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            VoiceServer.getMuted().forEach((uuid, muted) -> {
                if (muted.getTo() > 0 && muted.getTo() < System.currentTimeMillis()) {
                    VoiceServer.getMuted().remove(uuid);
                    VoiceServer.saveData(true);
                    ServerPlayer player = PlayerManager.getByUUID(uuid);
                    if (player != null) {
                        sendToClients(new ClientUnmutedPacket(uuid), null);
                        player.sendSystemMessage(Component.literal(
                                VoiceServer.getInstance().getMessagePrefix("player_unmuted")
                        ));
                    }
                }
            });
        }, 0L, 5L, TimeUnit.SECONDS);
    }

    public void close() {
        executor.shutdown();
    }

    public void handleRegisterChannels(List<ResourceLocation> channels, ServerPlayer player) {
        if (!playerToken.containsKey(player.getUUID()) && channels.contains(VoiceServer.PLASMO_VOICE)
                && !SocketServerUDP.clients.containsKey(player.getUUID())) {
            ServerNetworkHandler.reconnectClient(player);
        }
    }

    public void handleJoin(ServerPlayer player) {
        if (VoiceServer.getServer() == null) return;

        if (PlayerManager.isOp(player)
                && !SocketServerUDP.started) {
            player.sendSystemMessage(Component.literal(VoiceServer.getInstance().getPrefix() +
                    String.format("Voice chat is installed but doesn't work. Check if port %d UDP is open.",
                            VoiceServer.getServerConfig().getPort())));
        }
    }

    public void handleQuit(ServerPlayer player) {
        playerToken.remove(player.getUUID());
        execute(() -> disconnectClient(player.getUUID()));
    }

    public void handle(ClientConnectPacket packet, ServerPlayer player) throws IOException {
        ServerConfig config = VoiceServer.getServerConfig();

        String version = packet.getVersion();
        int ver = VoiceServer.calculateVersion(version);

        if (ver > VoiceServer.version) {
            player.sendSystemMessage(
                    Component.translatable("message.plasmo_voice.version_not_supported", VoiceServer.rawVersion)
            );
            return;
        } else if (ver < VoiceServer.minVersion) {
            player.sendSystemMessage(
                    Component.translatable("message.plasmo_voice.min_version", VoiceServer.rawMinVersion)
            );
            return;
        } else if (ver < VoiceServer.version) {
            MutableComponent link = Component.literal("https://www.curseforge.com/minecraft/mc-mods/plasmo-voice-client/files");
            link.setStyle(link.getStyle().withClickEvent(
                    new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.curseforge.com/minecraft/mc-mods/plasmo-voice-client/files")
            ));

            player.sendSystemMessage(
                    Component.translatable(
                            "message.plasmo_voice.new_version_available",
                            VoiceServer.rawVersion,
                            link
                    )
            );
        }

        sendTo(new ConfigPacket(config.getSampleRate(),
                        new ArrayList<>(config.getDistances()),
                        config.getDefaultDistance(),
                        config.getMaxPriorityDistance(),
                        config.isDisableVoiceActivation() ||
                                !VoiceServer.getPlayerManager().hasPermission(player.getUUID(), "voice.activation"),
                        config.getFadeDivisor(),
                        config.getPriorityFadeDivisor()),
                player);
    }
}
