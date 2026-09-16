package fr.tropimon.chatfilter;

import fr.tropimon.chatfilter.mixin.ChatHudAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Difficulty;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Offline integration check. No server connection and no packet or installed-config writes. */
public final class ChatFilterSmoke implements ClientModInitializer {
    private int tick;
    private int stage = -1;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("chatfilter.smoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (client.getOverlay() != null || ++tick < 60) return;
                if (stage == -1) {
                    stage = 0;
                    client.options.getViewDistance().setValue(2);
                    client.createIntegratedServerLoader().createAndStart("chatfilter-smoke-" + System.currentTimeMillis(),
                            new LevelInfo("Chat Filter smoke test", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), DataConfiguration.SAFE_MODE),
                            new GeneratorOptions(1L, false, false),
                            registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                            client.currentScreen);
                    return;
                }
                if (stage == 0) {
                    if (client.player == null) return;
                    check(net.minecraft.network.handler.DecoderHandler.class != null, "decoder mixin loaded");
                    check(net.minecraft.client.network.ClientPlayNetworkHandler.class != null, "network mixin loaded");
                    client.setScreen(new ChatScreen(""));
                    exercise(client);
                    stage = 1;
                    tick = 0;
                } else if (stage == 1) {
                    // The real ChatScreen/HUD have rendered repeatedly with the new layout cache.
                    client.setScreen(null);
                    System.out.println("CHAT_FILTER_SMOKE_OK: offline world, mixins, classification, visibility, clickable text, chat screen rendered");
                    client.scheduleStop();
                    stage = 2;
                }
            } catch (Throwable error) {
                error.printStackTrace();
                System.out.println("CHAT_FILTER_SMOKE_FAILED");
                System.exit(1);
            }
        });
    }

    private static void exercise(MinecraftClient client) {
        exerciseNetworkDecoder(client);
        // Zstandard raw-block fixture: an empty town list. Exercises the relocated decoder
        // from the distributable JAR, without relying on any other mod's compressor.
        var compressed = io.netty.buffer.Unpooled.wrappedBuffer(java.util.HexFormat.of()
                .parseHex("0e0000000128b52ffd200109000000"));
        try {
            var decoded = ServerProfileWire.decode("tropimon:update_towns", compressed);
            check(decoded instanceof ServerProfileState.Towns towns && towns.towns().isEmpty(), "private Zstandard decoder");
        } finally { compressed.release(); }
        var hud = client.inGameHud.getChatHud();
        var accessor = (ChatHudAccessor) hud;
        hud.clear(false);
        ChatFilterController.select(ChatChannel.ALL);
        Text system = Text.literal("Téléportation réussie");
        Text global = Text.literal("ꈎ Alex: global").styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/profile Alex"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("profil"))));
        Text town = Text.literal("[ꑤ Alex 석 Polaris] ville sur plusieurs lignes quand le texte dépasse la largeur disponible dans le chat");
        Text incoming = Text.literal("[ꑤ Alex 석 Moi] privé");
        Text outgoing = Text.literal("[Moi 석 ꑤ Alex] réponse");
        hud.addMessage(system);
        hud.addMessage(global);
        hud.addMessage(town);
        hud.addMessage(incoming);
        hud.addMessage(outgoing);
        int before = accessor.tropimonChatFilter$messages().size();
        hud.addMessage(Text.literal("ꌇ Ton chat a bien été mis sur : TOWN"));
        check(accessor.tropimonChatFilter$messages().size() == before, "channel confirmations hidden only");
        check(visible(accessor, "global") && visible(accessor, "Téléportation"), "global and server messages retained");
        check(!visible(accessor, "privé") && !visible(accessor, "réponse"), "private messages excluded from ALL");
        ChatFilterController.select(ChatChannel.TOWN);
        check(visible(accessor, "ville sur"), "town visible");
        check(!visible(accessor, "privé") && !visible(accessor, "réponse"), "private messages excluded from TOWN");
        int unread = PrivateChatManager.tabs().getFirst().unread();
        check(unread == 1, "outgoing reply did not increment unread");
        PrivateChatManager.preview("Alex");
        check(PrivateChatManager.tabs().getFirst().unread() == unread, "preview did not mark read");
        ChatFilterController.select(ChatChannel.PRIVATE);
        check(visible(accessor, "privé") && visible(accessor, "réponse"), "both sides in the same private conversation");
        check(!visible(accessor, "ville sur"), "town excluded from private");
        check(PrivateChatManager.tabs().size() == 1, "no duplicate reply tab");
        if (!GlobalFilterSettings.showTimestamps()) GlobalFilterSettings.toggleTimestamps();
        if (!GlobalFilterSettings.showPlayerHeads()) GlobalFilterSettings.togglePlayerHeads();
        Text decorated = ChatMessageDecorator.decorate(global);
        check(ChatMessageDecorator.decorate(global) == decorated, "unchanged decoration reused");
        Text original = ChatMessageDecorator.original(decorated);
        check(original == global && original.getStyle().getClickEvent() != null
                && original.getStyle().getHoverEvent() != null, "click and hover preserved");
        check(MessageAnalysisCache.get(global) == MessageAnalysisCache.get(decorated), "decoration shares stable analysis");
        check(decorated.getSiblings().getLast().getStyle().equals(global.getStyle()), "decorated child preserves click and hover");
        Text longMessage = Text.empty().append(global.copy()).append(" long message".repeat(25));
        var lines = ChatMessageDecorator.wrapLines(ChatMessageDecorator.decorate(longMessage), 100, client.textRenderer);
        check(lines.size() > 1 && "Alex".equals(ChatMessageDecorator.headSpeaker(lines.getFirst())), "head attached to first wrapped line");
        check(lines.stream().skip(1).noneMatch(line -> ChatMessageDecorator.headSpeaker(line) != null), "no head on continuation lines");
        GlobalFilterSettings.togglePlayerHeads();
        GlobalFilterSettings.toggleTimestamps();
        check(ChatMessageDecorator.decorate(decorated) == global, "disabling presentation restores original");
        GlobalFilterSettings.togglePlayerHeads();
        GlobalFilterSettings.toggleTimestamps();
        check(ChatMessageDecorator.original(ChatMessageDecorator.decorate(decorated)) == global, "option changes do not stack prefixes");
        ChatFilterController.select(ChatChannel.TOWN);
        int visibleLines = accessor.tropimonChatFilter$visibleMessages().size();
        hud.addMessage(Text.literal("[ꑤ Bob 석 Moi] hidden private"));
        check(accessor.tropimonChatFilter$visibleMessages().size() == visibleLines, "hidden PM does not animate the town chat");
        hud.addMessage(Text.literal("[ꌃ Alex 석 Staff] staff message"));
        check(!visible(accessor, "staff message"), "staff excluded from town");
        ChatFilterController.select(ChatChannel.STAFF);
        check(visible(accessor, "staff message") && !visible(accessor, "ville sur"), "staff channel isolated");
        ChatFilterController.select(ChatChannel.ALL);
        check(!visible(accessor, "staff message"), "staff excluded from ALL");
        String local = client.player.getGameProfile().getName();
        hud.addMessage(Text.literal("[ꑤ Alex 석 Moi] " + GroupMessageProtocol.invitation("abcdef12", java.util.List.of("Alex", local))));
        check(GroupChatManager.active() && GroupChatManager.memberCount() == 2, "group invitation accepted");
        hud.addMessage(Text.literal("[ꑤ Alex 석 Moi] " + GroupMessageProtocol.message("abcdef12", "group message")));
        check(!visible(accessor, "group message"), "group excluded from ALL");
        ChatFilterController.select(ChatChannel.GROUP);
        check(visible(accessor, "group message") && !visible(accessor, "privé"), "group isolated from private");
        client.setScreen(null);
        hud.addMessage(Text.literal("[ꑤ Bob 석 Moi] preview private"));
        check(ChatFilterController.selected() == ChatChannel.PRIVATE, "closed chat previews private channel");
        int bobUnread = PrivateChatManager.tabs().stream().filter(tab -> tab.name().equals("Bob")).findFirst().orElseThrow().unread();
        hud.addMessage(Text.literal("[ꑤ Alex 석 Polaris] preview town"));
        check(ChatFilterController.selected() == ChatChannel.TOWN && TownChatNotifications.unread() > 0, "closed chat previews town and keeps unread");
        check(PrivateChatManager.tabs().stream().filter(tab -> tab.name().equals("Bob")).findFirst().orElseThrow().unread() == bobUnread,
                "town preview preserves private unread");
        hud.addMessage(Text.literal("[ꌃ Alex 석 Staff] preview staff"));
        check(ChatFilterController.selected() == ChatChannel.STAFF && StaffChatNotifications.unread() > 0, "closed chat previews staff");
        ChatFilterController.select(ChatChannel.ALL);
        client.setScreen(new ChatScreen(""));
    }

    private static void exerciseNetworkDecoder(MinecraftClient client) {
        int[] packetId = {-1};
        net.minecraft.network.state.PlayStateFactories.S2C.forEachPacketType((type, id) -> {
            if (type.equals(net.minecraft.network.packet.CommonPackets.CUSTOM_PAYLOAD_S2C)) packetId[0] = id;
        });
        check(packetId[0] >= 0, "vanilla payload packet registered");
        var state = net.minecraft.network.state.PlayStateFactories.S2C.bind(
                net.minecraft.network.RegistryByteBuf.makeFactory(client.world.getRegistryManager()));
        var frame = new net.minecraft.network.PacketByteBuf(io.netty.buffer.PooledByteBufAllocator.DEFAULT.directBuffer());
        frame.writeInt(42).readerIndex(4);
        frame.writeVarInt(packetId[0]);
        var channelId = net.minecraft.util.Identifier.of("tropimon", "update_town_packet");
        frame.writeIdentifier(channelId);
        frame.writeUuid(java.util.UUID.fromString("abcdef01-1234-1234-1234-123456789abc"));
        frame.writeString("Polaris");
        frame.writeBoolean(false); // no region
        frame.writeBoolean(false); // recruiting
        frame.writeVarInt(0); // chunks
        frame.writeVarInt(0); // citizens
        var observation = ServerProfileWire.inspectFrame(frame);
        check(observation != null && observation.error() == null, "profile observed from direct frame");
        var channel = new io.netty.channel.embedded.EmbeddedChannel(new net.minecraft.network.handler.DecoderHandler<>(state));
        try {
            check(channel.writeInbound(frame), "real network decoder returned a packet");
            Object decoded = channel.readInbound();
            check(decoded instanceof net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket payload
                    && payload.payload().getId().id().equals(channelId), "observer did not replace payload");
            check(((ServerProfileState.Towns) observation.update()).towns().getFirst().name().equals("Polaris"),
                    "town observation survives pooled frame release");
        } finally { channel.finishAndReleaseAll(); }
    }

    private static boolean visible(ChatHudAccessor accessor, String fragment) {
        for (ChatHudLine line : accessor.tropimonChatFilter$messages()) {
            if (line.content().getString().contains(fragment) && ChatFilterController.shouldShow(line)) return true;
        }
        return false;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
