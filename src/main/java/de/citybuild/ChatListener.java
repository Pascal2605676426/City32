package de.citybuild;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Chat-Format: [Rang] [Clan] Name » Nachricht */
public class ChatListener implements Listener {

    private final CityBuildPlugin plugin;

    public ChatListener(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component name = plugin.formatName(player);
        Component separator = plugin.color(" &8» ");

        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.empty()
                        .append(name)
                        .append(separator)
                        .append(message.colorIfAbsent(NamedTextColor.GRAY)));
    }
}
