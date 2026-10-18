package fr.redsavant.discordlink.listener;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInput implements Listener {

    private record Pending(Consumer<String> callback, Runnable onCancel, BukkitTask timeout) {
    }

    private final DiscordLinkPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public void await(Player player, Consumer<String> callback, Runnable onCancel) {
        cancel(player);
        int seconds = plugin.getPluginConfig().searchTimeout();
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending removed = pending.remove(player.getUniqueId());
            if (removed != null && player.isOnline()) {
                plugin.getLangManager().send(player, "minecraft.chat.timeout");
                removed.onCancel().run();
            }
        }, seconds * 20L);
        pending.put(player.getUniqueId(), new Pending(callback, onCancel, timeout));
        plugin.getLangManager().send(player, "minecraft.chat.prompt", "seconds", seconds,
                "cancel", plugin.getLangManager().raw(plugin.getLangManager().locale(player), "minecraft.chat.cancel-word"));
    }

    public void cancel(Player player) {
        Pending removed = pending.remove(player.getUniqueId());
        if (removed != null) {
            removed.timeout().cancel();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending entry = pending.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        event.setCancelled(true);
        entry.timeout().cancel();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String cancelWord = plugin.getLangManager().raw(plugin.getLangManager().locale(player), "minecraft.chat.cancel-word");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase(cancelWord) || message.equalsIgnoreCase("cancel")) {
                plugin.getLangManager().send(player, "minecraft.chat.cancelled");
                entry.onCancel().run();
            } else {
                entry.callback().accept(message);
            }
        });
    }
}
