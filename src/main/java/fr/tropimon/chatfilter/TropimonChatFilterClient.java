package fr.tropimon.chatfilter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TropimonChatFilterClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("tropimon_chat_filter");

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (MinecraftClient.getInstance().currentScreen == null) {
                TeleportPopupRenderer.render(context);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register(
                        (current, context, mouseX, mouseY, delta) -> {
                            if (current instanceof ChatScreen) {
                                TeleportPopupRenderer.render(context);
                            }
                        }));
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() {
                        return Identifier.of("tropimon_chat_filter", "render_cache");
                    }
                    @Override public void reload(ResourceManager manager) {
                        RenderCacheEpoch.invalidate();
                    }
                });
    }
}
