package com.sulphate.chatcolor2.listeners;

import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * A small abstraction over the different chat events supported by the plugin, so the colouring logic can be shared
 * between Spigot's {@link AsyncPlayerChatEvent} and Paper's component-based chat event.
 * <p>
 * Messages are always exposed as legacy strings (using the section symbol), which is what the rest of the plugin
 * works with.
 */
public interface ChatMessage {

    String getMessage();

    void setMessage(String message);

    boolean isCancelled();

    /**
     * The underlying legacy chat event, if there is one. This is only present when running on a server which fires
     * {@link AsyncPlayerChatEvent}, and is exposed purely so that the plugin's own API event can keep passing it on.
     *
     * @return the underlying legacy event, or {@code null} if the modern (component) chat event is in use.
     */
    AsyncPlayerChatEvent getLegacyEvent();

    /**
     * Provides a function which can re-apply the player's colour to arbitrary text.
     * <p>
     * Implementations may use this as a safety net if something later in the chat pipeline strips or replaces the
     * coloured message. The default implementation ignores it.
     *
     * @param recolour a function taking plain text and returning it with the player's colour applied.
     */
    default void setRecolourFunction(java.util.function.UnaryOperator<String> recolour) {
        // Optional.
    }

}
