package com.sulphate.chatcolor2.listeners;

import com.sulphate.chatcolor2.data.PlayerData;
import com.sulphate.chatcolor2.event.ChatColorEvent;
import com.sulphate.chatcolor2.commands.Setting;
import com.sulphate.chatcolor2.data.PlayerDataStore;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.GroupColoursManager;
import com.sulphate.chatcolor2.utils.Config;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Reloadable;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.regex.Pattern;

public class ChatListener implements Listener, Reloadable {

    private static final Pattern SYMBOLS_REGEX = Pattern.compile("^[!^\"£$%*()\\[\\]{}'#@~;:,./<>?\\\\|\\-_=+]+[^!^\"£$%&*()\\[\\]{}'#@~;:,./<>?\\\\|\\-_=+]+");

    private final ConfigsManager configsManager;
    private final GeneralUtils generalUtils;
    private final GroupColoursManager groupColoursManager;
    private final PlayerDataStore dataStore;

    private YamlConfiguration mainConfig;
    private final Set<Player> pausedPlayers;
    private final Set<String> loggedWarnings;
    private final Set<UUID> debuggingPlayers;

    public ChatListener(ConfigsManager configsManager, GeneralUtils generalUtils, GroupColoursManager groupColoursManager, PlayerDataStore dataStore) {
        this.configsManager = configsManager;
        this.generalUtils = generalUtils;
        this.groupColoursManager = groupColoursManager;
        this.dataStore = dataStore;

        pausedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        loggedWarnings = Collections.newSetFromMap(new ConcurrentHashMap<>());
        debuggingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

        reload();
    }

    public void reload() {
        mainConfig = configsManager.getConfig(Config.MAIN_CONFIG);
    }

    public boolean togglePause(Player player) {
        if (pausedPlayers.contains(player)) {
            pausedPlayers.remove(player);
            return false;
        }
        else {
            pausedPlayers.add(player);
            return true;
        }
    }

    /**
     * Toggles per-message chat debug logging for a player.
     *
     * @return {@code true} if logging is now enabled.
     */
    public boolean toggleDebug(Player player) {
        UUID uuid = player.getUniqueId();

        if (debuggingPlayers.remove(uuid)) {
            return false;
        }

        debuggingPlayers.add(uuid);
        return true;
    }

    public boolean isDebugging(UUID uuid) {
        return debuggingPlayers.contains(uuid);
    }

    public void logDebug(UUID uuid, String label, String value) {
        if (debuggingPlayers.contains(uuid)) {
            // The value is appended outside of the colouriser so its colour codes stay visible.
            Bukkit.getConsoleSender().sendMessage(
                    GeneralUtils.colourise("&b[ChatColor debug] &f" + label + ": &a")
                            + (value == null ? "null" : value.replace('\u00a7', '&'))
            );
        }
    }

    public void onEvent(AsyncPlayerChatEvent event) {
        handle(event.getPlayer(), new LegacyChatMessage(event));
    }

    /**
     * Resolves the colour a player's chat should use right now, taking the default colour, group colours and forced
     * group colours into account.
     *
     * @return the colour, or {@code null} if this player's chat should not be coloured at all.
     */
    public String resolveColour(Player player) {
        if (pausedPlayers.contains(player)) {
            return null;
        }

        UUID uuid = player.getUniqueId();
        String colour = dataStore.getColour(uuid);

        if (colour == null) {
            if (!mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
                return null;
            }

            colour = mainConfig.getString("default.color");
        }

        String groupColour = groupColoursManager.getGroupColourForPlayer(player);

        if (groupColour != null && mainConfig.getBoolean(Setting.FORCE_GROUP_COLORS.getConfigPath())) {
            colour = groupColour;
        }

        return colour == null || colour.isEmpty() ? null : colour;
    }

    /**
     * Applies the player's current colour to the given text. Used to re-apply the colour late in the chat pipeline,
     * after other chat plugins have had their say.
     *
     * @return the coloured text, or {@code null} if this player's chat should not be coloured.
     */
    public String colourFor(Player player, String text) {
        String colour = resolveColour(player);

        if (colour == null || text == null || text.isEmpty()) {
            return null;
        }

        return generalUtils.colouriseMessage(colour, text, false);
    }

    public void handle(Player player, ChatMessage chat) {
        String message = chat.getMessage();
        UUID uuid = player.getUniqueId();

        if (chat.isCancelled() || checkHasSymbolPrefix(message) || pausedPlayers.contains(player)) {
            return;
        }

        boolean defaultColourEnabled = mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath());

        // If they chat before the data store has had a chance to load/fail their data, use the default colour, or if
        // not enabled, do nothing at all.
        if (dataStore.getColour(uuid) == null) {
            if (defaultColourEnabled) {
                String defaultColor = mainConfig.getString("default.color");
                colourAndModify(player, message, defaultColor, chat);
            }

            return;
        }

        // Check default colour.
        if (defaultColourEnabled) {
            try {
                generalUtils.checkDefault(uuid);
            }
            catch (Exception ex) {
                logOnce("Failed to check the default colour for " + uuid + ": " + ex);
            }
        }

        // Check if the player should have their colour reset.
        if (mainConfig.getBoolean(Setting.REMOVE_INACCESSIBLE_COLORS.getConfigPath())) {
            try {
                String currentColour = dataStore.getColour(uuid);
                PlayerData data = dataStore.getPlayerData(uuid);

                if (data != null && currentColour != null && !hasDefaultOrGroupColour(player, currentColour)) {
                    removeInaccessibleColour(player, data.getColourName());
                }
            }
            catch (Exception ex) {
                logOnce("Failed to check inaccessible colours for " + uuid + ": " + ex);
            }
        }

        message = checkColourCodes(message, player);

        // Check if they have a group colour, and if it should be enforced.
        String groupColour = groupColoursManager.getGroupColourForPlayer(player);
        String colour = dataStore.getColour(uuid);

        if (groupColour != null) {
            // If it should be forced, set it so.
            if (mainConfig.getBoolean(Setting.FORCE_GROUP_COLORS.getConfigPath())) {
                colour = groupColour;
            }
        }

        colourAndModify(player, message, colour, chat);
    }

    private boolean checkHasSymbolPrefix(String message) {
        boolean checkSymbols = mainConfig.getBoolean(Setting.IGNORE_SYMBOL_PREFIXES.getConfigPath());

        if (checkSymbols) {
            return SYMBOLS_REGEX.matcher(message).matches();
        }
        else {
            return false;
        }
    }

    private void colourAndModify(Player player, String message, String colour, ChatMessage chat) {
        if (GeneralUtils.isDifferentWhenColourised(message)) {
            boolean override = mainConfig.getBoolean(Setting.COLOR_OVERRIDE.getConfigPath());

            if (override) {
                while (GeneralUtils.isDifferentWhenColourised(message)) {
                    // Strip the colour from the message.
                    message = org.bukkit.ChatColor.stripColor(GeneralUtils.colourise(message));
                }

                chat.setMessage(message);
            }
            else {
                chat.setMessage(GeneralUtils.colourise(message));
            }
        }
        else {
            boolean eventSucceeded;

            try {
                eventSucceeded = fireEvent(player, message, colour, chat.getLegacyEvent());
            }
            catch (Exception ex) {
                // A failure to deliver the API event must not stop the player's colour from being applied.
                logOnce("Failed to fire ChatColorEvent, colouring anyway: " + ex);
                eventSucceeded = true;
            }

            if (eventSucceeded) {
                String finalColour = colour;
                chat.setRecolourFunction(text -> generalUtils.colouriseMessage(finalColour, text, false));

                String coloured = generalUtils.colouriseMessage(colour, message, false);
                chat.setMessage(coloured);

                logDebug(player.getUniqueId(), "colour", colour);
                logDebug(player.getUniqueId(), "message set to", coloured);
            }
        }
    }

    // Avoids spamming the console if something is persistently broken.
    private void logOnce(String message) {
        if (loggedWarnings.add(message)) {
            GeneralUtils.sendConsoleMessage("&b[ChatColor] &eWarning: " + message);
        }
    }

    private String checkColourCodes(String message, Player player) {
        // If their message contains &, check they have permissions for it, or strip the colour.
        if (!player.hasPermission("chatcolor.use-color-codes")) {
            // A player reported using '&&a' for example, would bypass this. So, loop until it's not different.
            while (GeneralUtils.isDifferentWhenColourised(message)) {
                // Strip the colour from the message.
                message = org.bukkit.ChatColor.stripColor(GeneralUtils.colourise(message));
            }
        }

        if (!player.hasPermission("chatcolor.use-hex-codes") && GeneralUtils.containsHexColour(message, true)) {
            while (GeneralUtils.isDifferentWhenColourised(message)) {
                message = org.bukkit.ChatColor.stripColor(GeneralUtils.colourise(message));
            }
        }

        return message;
    }

    private boolean hasDefaultOrGroupColour(Player player, String colour) {
        // Check if they are using the default colour (no permission needed).
        if (mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
            String defaultColour = mainConfig.getString("default.color");

            if (colour.equals(defaultColour)) {
                return true;
            }
        }

        // Check if they are using a group colour (no permission needed).
        String groupColour = groupColoursManager.getGroupColourForPlayer(player);

        if (groupColour != null) {
            if (colour.equals(groupColour)) {
                return true;
            }
        }

        return false;
    }

    private void removeInaccessibleColour(Player player, String colourName) {
        PlayerData data = dataStore.getPlayerData(player.getUniqueId());
        UUID uuid = player.getUniqueId();

        String permission = "";
        boolean shouldRemove = false;

        if (colourName.startsWith("%")) {
            permission = "chatcolor.custom." + colourName.substring(1);
        }
        else if (colourName.startsWith("#")) {
            if (player.hasPermission("chatcolor.use-hex-codes")) {
                shouldRemove = true;
            }
            else {
                permission = "chatcolor.color." + colourName.substring(1);
            }
        }
        else if (colourName.startsWith("u") || colourName.startsWith("g")) {
            permission = "chatcolor.special";
        }
        else {
            permission = "chatcolor.color." + colourName;
        }

        Set<Character> allowedMods = new HashSet<>();

        for (char mod : new HashSet<>(data.getModifiers())) {
            if (!player.hasPermission("chatcolor.modifier." + mod)) {
                data.removeModifier(mod);
            }
            else {
                allowedMods.add(mod);
            }
        }

        if (shouldRemove || !player.hasPermission(permission)) {
            if (mainConfig.getBoolean(Setting.DEFAULT_COLOR_ENABLED.getConfigPath())) {
                dataStore.setColour(uuid, mainConfig.getString("default.color"));
            }
            else {
                dataStore.setColour(uuid, "");
            }
        }

        for (char mod : allowedMods) {
            data.addModifier(mod);
        }
    }

    private boolean fireEvent(Player player, String message, String colour, AsyncPlayerChatEvent chatEvent) {
        // Bukkit will not dispatch an async event from a tick thread, so match the current context.
        boolean async = !Bukkit.isPrimaryThread();
        ChatColorEvent chatColorEvent = new ChatColorEvent(player, message, colour, chatEvent, async);
        Bukkit.getPluginManager().callEvent(chatColorEvent);

        return !chatColorEvent.isCancelled();
    }

    // Wraps Spigot's legacy chat event.
    private static final class LegacyChatMessage implements ChatMessage {

        private final AsyncPlayerChatEvent event;

        private LegacyChatMessage(AsyncPlayerChatEvent event) {
            this.event = event;
        }

        @Override
        public String getMessage() {
            return event.getMessage();
        }

        @Override
        public void setMessage(String message) {
            event.setMessage(message);
        }

        @Override
        public boolean isCancelled() {
            return event.isCancelled();
        }

        @Override
        public AsyncPlayerChatEvent getLegacyEvent() {
            return event;
        }

    }

}
