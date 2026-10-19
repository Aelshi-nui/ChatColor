package com.sulphate.chatcolor2.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

public class ChatColorEvent extends Event implements Cancellable {

    private final HandlerList handlerList;
    private final Player player;
    private final String message;
    private final String colour;
    private final AsyncPlayerChatEvent chatEvent;

    private boolean cancelled;

    public ChatColorEvent(Player player, String message, String colour, AsyncPlayerChatEvent chatEvent) {
        this(player, message, colour, chatEvent, true);
    }

    /**
     * @param async whether the event is being fired from an asynchronous context. Bukkit refuses to dispatch an
     *              asynchronous event from a server tick thread (and vice versa), so this must match reality.
     */
    public ChatColorEvent(Player player, String message, String colour, AsyncPlayerChatEvent chatEvent, boolean async) {
        super(async);

        handlerList = new HandlerList();
        cancelled = false;

        this.player = player;
        this.message = message;
        this.colour = colour;
        this.chatEvent = chatEvent;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Player getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public String getColour() {
        return colour;
    }

    /**
     * The legacy chat event which triggered this colour change.
     *
     * @return the legacy chat event, or {@code null} when the server is using Paper's modern component-based chat
     *         event (see the {@code use-modern-chat-event} setting).
     */
    public AsyncPlayerChatEvent getChatEvent() {
        return chatEvent;
    }

}
