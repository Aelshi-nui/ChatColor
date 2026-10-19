# ChatColor
ChatColor lets players change the color and style of their chat.

Legacy colors, hex colors, rainbow colors and gradients are supported.

Works on Spigot, Paper and Folia. The plugin is built against the latest Paper API line (`26.1.x`) and still keeps support for older Minecraft versions back to `1.8.x` where the Bukkit API allows it.

Folia support is built in, with scheduler handling split for Spigot/Paper and Folia servers.

## Chat plugin compatibility

ChatColor applies colours through Paper's component-based chat event where available, which keeps hex, rainbow and
gradient colours from being stripped by other chat plugins.

[ZelChat](https://github.com/ZelChat/zelchat-api) builds the chat line itself and ignores the Bukkit/Paper chat
events, so it is supported through its own module API instead. When ZelChat is installed the integration is used
automatically - no configuration needed. Run `/chatcolor debug` to see which path is active.
