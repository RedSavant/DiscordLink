package fr.redsavant.discordlink.listener;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.lang.LangManager;
import fr.redsavant.discordlink.link.LinkManager;
import fr.redsavant.discordlink.storage.LinkedAccount;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionListener implements Listener {

    private final DiscordLinkPlugin plugin;
    private final Set<UUID> rejected = ConcurrentHashMap.newKeySet();

    public ConnectionListener(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        LangManager lang = plugin.getLangManager();

        if (plugin.getDataStore().playerLocale(uuid) == null && plugin.getPluginConfig().autoDetectClient()) {
            String detected = lang.fromClient(player.locale());
            if (detected != null) {
                plugin.getDataStore().setPlayerLocale(uuid, detected);
            }
        }

        plugin.getDataStore().updateUsername(uuid, player.getName());
        Optional<LinkedAccount> account = plugin.getDataStore().byUuid(uuid);

        if (account.isEmpty() && plugin.getPluginConfig().linkingRequired() && !plugin.getLinkManager().isBypassed(player)) {
            rejected.add(uuid);
            event.joinMessage(null);
            LinkManager.PendingCode code = plugin.getLinkManager().code(uuid, player.getName());
            String locale = lang.locale(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.kick(plugin.getLinkManager().codeMessage(locale, "minecraft.kick.not-linked", code, player.getName(), false),
                            PlayerKickEvent.Cause.PLUGIN);
                }
            });
            return;
        }

        account.ifPresent(value -> plugin.getBotBridge().syncRoles(value, null, true));
        plugin.getBotBridge().log("join", fields(player, account.orElse(null)));

        if (player.hasPermission("discordlink.admin")) {
            plugin.getUpdateChecker().notify(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (rejected.remove(player.getUniqueId())) {
            event.quitMessage(null);
            return;
        }
        Optional<LinkedAccount> account = plugin.getDataStore().byUuid(player.getUniqueId());
        account.ifPresent(value -> plugin.getBotBridge().syncRoles(value, null, false));
        plugin.getBotBridge().log("quit", fields(player, account.orElse(null)));
        plugin.getChatInput().cancel(player);
    }

    private Map<String, Object> fields(Player player, LinkedAccount account) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("player", player.getName());
        fields.put("uuid", player.getUniqueId().toString());
        InetSocketAddress address = player.getAddress();
        if (plugin.getPluginConfig().showIp() && address != null && address.getAddress() != null) {
            fields.put("ip", address.getAddress().getHostAddress());
        }
        if (account != null) {
            fields.put("discordId", account.discordId());
        }
        return fields;
    }
}
