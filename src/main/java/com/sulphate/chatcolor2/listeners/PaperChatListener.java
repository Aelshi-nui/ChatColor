package com.sulphate.chatcolor2.listeners;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Re-applies a player's colour at render time, using Paper's {@link AsyncChatEvent}.
 * <p>
 * Chat plugins legitimately sanitise the chat message by permission - EssentialsX, for example, runs
 * {@code FormatUtil.formatMessage(user, "essentials.chat", message)} at {@link EventPriority#LOWEST} and strips any
 * formatting the player is not allowed to use. Because ChatColor puts its colour into the message text, that colour
 * looks like something the player typed, so it gets stripped along with everything else.
 * <p>
 * This listener works around that without fighting other plugins: it hooks in at {@link EventPriority#MONITOR}, after
 * everyone else, and wraps whatever renderer is in place. If the message about to be rendered has ended up with no
 * colour on it at all, the player's colour is applied to it there and then - too late for anything to strip it. A
 * message which still carries colour is left completely alone, so plugins which handle colour themselves keep working
 * and nothing is ever coloured twice.
 * <p>
 * This class must only ever be loaded when {@link AsyncChatEvent} is present on the classpath.
 */
public class PaperChatListener implements Listener {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final ChatListener delegate;

    private PaperChatListener(ChatListener delegate) {
        this.delegate = delegate;
    }

    /**
     * Registers the render-time safety net.
     */
    public static void register(Plugin plugin, ChatListener delegate) {
        PaperChatListener listener = new PaperChatListener(delegate);

        EventExecutor executor = (registered, event) -> {
            if (registered instanceof PaperChatListener && event instanceof AsyncChatEvent) {
                ((PaperChatListener) registered).onChat((AsyncChatEvent) event);
            }
        };

        plugin.getServer().getPluginManager().registerEvent(
                AsyncChatEvent.class, listener, EventPriority.MONITOR, executor, plugin
        );
    }

    /**
     * Lists the plugins listening to the modern chat event, for the debug command.
     */
    public static String describeListeners() {
        StringBuilder builder = new StringBuilder();

        for (org.bukkit.plugin.RegisteredListener listener : AsyncChatEvent.getHandlerList().getRegisteredListeners()) {
            builder.append(listener.getPlugin().getName()).append('(').append(listener.getPriority()).append(") ");
        }

        return builder.length() == 0 ? "none" : builder.toString().trim();
    }

    private void onChat(AsyncChatEvent event) {
        Player source = event.getPlayer();

        if (event.isCancelled() || delegate.resolveColour(source) == null) {
            return;
        }

        ChatRenderer previous = event.renderer();

        event.renderer((player, displayName, message, viewer) -> {
            Component finalMessage = message;

            try {
                String legacy = SERIALIZER.serialize(message);

                if (!hasColour(legacy)) {
                    String coloured = delegate.colourFor(player, legacy);

                    if (coloured != null) {
                        finalMessage = SERIALIZER.deserialize(coloured);
                        delegate.logDebug(player.getUniqueId(), "re-coloured at render time", coloured);
                    }
                }
                else {
                    delegate.logDebug(player.getUniqueId(), "message already carried colour, left alone", legacy);
                }
            }
            catch (Exception ex) {
                finalMessage = message;
            }

            return previous.render(player, displayName, finalMessage, viewer);
        });
    }

    // True if the legacy string carries any colour (a legacy colour code, or the start of a hex sequence). Formatting
    // codes such as bold or italic are deliberately not treated as colour.
    private static boolean hasColour(String legacy) {
        for (int i = 0; i < legacy.length() - 1; i++) {
            if (legacy.charAt(i) != LegacyComponentSerializer.SECTION_CHAR) {
                continue;
            }

            char code = Character.toLowerCase(legacy.charAt(i + 1));

            if (code == 'x' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                return true;
            }
        }

        return false;
    }

}
