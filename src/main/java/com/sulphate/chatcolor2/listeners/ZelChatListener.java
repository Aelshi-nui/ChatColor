package com.sulphate.chatcolor2.listeners;

import it.pino.zelchat.api.ZelChatAPI;
import it.pino.zelchat.api.message.state.MessageState;
import it.pino.zelchat.api.module.ChatModule;
import it.pino.zelchat.api.module.annotation.ChatModuleSettings;
import it.pino.zelchat.api.module.priority.ModulePriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.function.UnaryOperator;

/**
 * Integration with <a href="https://github.com/ZelChat/zelchat-api">ZelChat</a>.
 * <p>
 * ZelChat does not use the message from the Bukkit/Paper chat events - it builds the line that players see from the
 * raw message plus its own configured format. Anything ChatColor writes to {@code AsyncChatEvent#message()} is
 * therefore discarded, which is why hex, rainbow and gradient colours never showed up (plain colour codes survived
 * because ZelChat parses those itself).
 * <p>
 * The fix is to apply the colour through ZelChat's own module system instead. External modules run after ZelChat's
 * internal ones, so at {@link ModulePriority#HIGHEST} this module sees the finished component and can colour the
 * player's message inside it.
 * <p>
 * This class must only ever be loaded when the ZelChat API is present on the classpath.
 */
@ChatModuleSettings(pluginOwner = "ChatColor2", priority = ModulePriority.HIGHEST)
public class ZelChatListener implements ChatModule {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final ChatListener delegate;

    private ZelChatListener(ChatListener delegate) {
        this.delegate = delegate;
    }

    /**
     * Registers a ZelChat module which delegates its colouring logic to the given {@link ChatListener}.
     *
     * @return the registered module, so it can be unregistered on shutdown.
     */
    public static Object register(Plugin plugin, ChatListener delegate) {
        ZelChatListener module = new ZelChatListener(delegate);

        ZelChatAPI.get().getModuleManager().register(plugin, module);
        module.load();

        return module;
    }

    /**
     * Unregisters a module previously returned by {@link #register(Plugin, ChatListener)}.
     */
    public static void unregister(Plugin plugin, Object module) {
        if (module instanceof ChatModule) {
            ((ChatModule) module).unload();
            ZelChatAPI.get().getModuleManager().unregister(plugin, (ChatModule) module);
        }
    }

    @Override
    public void handleChatMessage(it.pino.zelchat.api.message.ChatMessage chatMessage) {
        MessageState state = chatMessage.getState();

        if (state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED) {
            return;
        }

        Player player = chatMessage.getBukkitPlayer();

        if (player == null) {
            return;
        }

        ZelChatMessage message = new ZelChatMessage(chatMessage);

        delegate.handle(player, message);
        message.apply(player);
    }

    // Adapts a ZelChat message to the plugin's own chat abstraction, so all of the normal colour logic (default
    // colours, group colours, permissions, pausing, colour override) is reused unchanged.
    private final class ZelChatMessage implements ChatMessage {

        private final it.pino.zelchat.api.message.ChatMessage chatMessage;
        private final String raw;

        private String message;
        private UnaryOperator<String> recolour;

        private ZelChatMessage(it.pino.zelchat.api.message.ChatMessage chatMessage) {
            this.chatMessage = chatMessage;
            // The message exactly as the player typed it, before ZelChat formatted anything.
            this.raw = chatMessage.getRawMessage();
            this.message = raw;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public boolean isCancelled() {
            MessageState state = chatMessage.getState();
            return state == MessageState.CANCELLED || state == MessageState.FILTERED_CANCELLED;
        }

        @Override
        public AsyncPlayerChatEvent getLegacyEvent() {
            return null;
        }

        @Override
        public void setRecolourFunction(UnaryOperator<String> recolour) {
            this.recolour = recolour;
        }

        private void apply(Player player) {
            if (message == null || message.equals(raw) || raw.isEmpty()) {
                return;
            }

            Component coloured = SERIALIZER.deserialize(message);
            Component current = chatMessage.getMessage();
            String plain = PlainTextComponentSerializer.plainText().serialize(current);

            // ZelChat may hand us either the message on its own, or the whole formatted line. Handle both, and leave
            // the message alone if the text no longer matches (it was filtered or rewritten by another module).
            if (plain.equals(raw)) {
                chatMessage.setMessage(coloured);
            }
            else if (plain.contains(raw)) {
                chatMessage.setMessage(current.replaceText(
                        builder -> builder.matchLiteral(raw).once().replacement(coloured)
                ));
            }
            else {
                delegate.logDebug(player.getUniqueId(), "ZelChat message did not contain the raw text, skipped", plain);
                return;
            }

            delegate.logDebug(player.getUniqueId(), "ZelChat message coloured", message);
        }

    }

}
