package fr.redsavant.discordlink.link;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import com.google.gson.JsonObject;
import fr.redsavant.discordlink.config.PluginConfig;
import fr.redsavant.discordlink.lang.LangManager;
import fr.redsavant.discordlink.storage.LinkedAccount;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager {

    public enum Status {
        LINKED,
        INVALID_CODE,
        DISCORD_ALREADY_LINKED,
        PLAYER_ALREADY_LINKED
    }

    public record PendingCode(String code, UUID uuid, String name, long expiresAt) {
        public boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long remainingSeconds() {
            return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
        }
    }

    public record LinkResult(Status status, LinkedAccount account) {
    }

    private final DiscordLinkPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();
    private final Map<UUID, String> codeByPlayer = new ConcurrentHashMap<>();

    public LinkManager(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    private PluginConfig config() {
        return plugin.getPluginConfig();
    }

    public PendingCode code(UUID uuid, String name) {
        String existing = codeByPlayer.get(uuid);
        if (existing != null) {
            PendingCode pending = codes.get(existing);
            if (pending != null && !pending.expired()) {
                return pending;
            }
            codes.remove(existing);
        }
        String code;
        do {
            code = generate();
        } while (codes.containsKey(code));
        PendingCode pending = new PendingCode(code, uuid, name, System.currentTimeMillis() + config().codeExpiration() * 1000L);
        codes.put(code, pending);
        codeByPlayer.put(uuid, code);
        return pending;
    }

    private String generate() {
        String charset = config().codeCharset();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < config().codeLength(); i++) {
            builder.append(charset.charAt(random.nextInt(charset.length())));
        }
        return builder.toString();
    }

    public int pendingCount() {
        return (int) codes.values().stream().filter(code -> !code.expired()).count();
    }

    public int clearExpired() {
        int removed = 0;
        for (PendingCode pending : codes.values()) {
            if (pending.expired()) {
                codes.remove(pending.code());
                codeByPlayer.remove(pending.uuid(), pending.code());
                removed++;
            }
        }
        return removed;
    }

    public Component codeMessage(String locale, String key, PendingCode code, String player, boolean prefix) {
        LangManager lang = plugin.getLangManager();
        String instructions = lang.raw(locale, "minecraft.instructions." + config().linkMode().toLowerCase(Locale.ROOT));
        String template = (prefix ? lang.raw(locale, "minecraft.prefix") : "") + lang.raw(locale, key).replace("{instructions}", instructions);
        JsonObject info = plugin.getBotBridge().getBotInfo();
        String channel = info != null && info.has("linkChannelName") && !info.get("linkChannelName").isJsonNull()
                ? "#" + info.get("linkChannelName").getAsString()
                : lang.raw(locale, "minecraft.instructions.unknown-channel");
        return lang.parse(template,
                "code", code.code(),
                "player", player,
                "channel", channel,
                "minutes", Math.max(1, code.remainingSeconds() / 60),
                "seconds", code.remainingSeconds());
    }

    public LinkResult link(String rawCode, String discordId, String discordName) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        PendingCode pending = codes.get(code);
        if (pending == null || pending.expired()) {
            return new LinkResult(Status.INVALID_CODE, null);
        }
        Optional<LinkedAccount> discordAccount = plugin.getDataStore().byDiscord(discordId);
        if (discordAccount.isPresent()) {
            return new LinkResult(Status.DISCORD_ALREADY_LINKED, discordAccount.get());
        }
        Optional<LinkedAccount> playerAccount = plugin.getDataStore().byUuid(pending.uuid());
        if (playerAccount.isPresent()) {
            return new LinkResult(Status.PLAYER_ALREADY_LINKED, playerAccount.get());
        }
        codes.remove(code);
        codeByPlayer.remove(pending.uuid());

        LinkedAccount account = new LinkedAccount(pending.uuid(), pending.name(), discordId, discordName, System.currentTimeMillis());
        plugin.getDataStore().saveLink(account);

        Player player = Bukkit.getPlayer(account.uuid());
        plugin.getBotBridge().syncRoles(account, true, player != null);
        plugin.getBotBridge().log("link", Map.of(
                "player", account.username(),
                "uuid", account.uuid().toString(),
                "discordId", discordId));
        if (player != null) {
            plugin.getLangManager().send(player, "minecraft.link.linked", "discord", account.displayDiscordName());
        }
        plugin.getDlogger().info(plugin.getLangManager().console("linked", "player", account.username(), "discord", account.displayDiscordName()));
        return new LinkResult(Status.LINKED, account);
    }

    public Optional<LinkedAccount> unlink(UUID uuid, String actor) {
        Optional<LinkedAccount> removed = plugin.getDataStore().removeLink(uuid);
        removed.ifPresent(account -> {
            plugin.getBotBridge().syncRoles(account, false, false);
            plugin.getBotBridge().log("unlink", Map.of(
                    "player", account.username(),
                    "uuid", account.uuid().toString(),
                    "discordId", account.discordId(),
                    "actor", actor));
            plugin.getDlogger().info(plugin.getLangManager().console("unlinked", "player", account.username(), "actor", actor));
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (config().linkingRequired() && !isBypassed(player)) {
                    player.kick(plugin.getLangManager().component(plugin.getLangManager().locale(player), "minecraft.kick.unlinked"),
                            PlayerKickEvent.Cause.PLUGIN);
                } else {
                    plugin.getLangManager().send(player, "minecraft.unlink.notify", "actor", actor);
                }
            }
        });
        return removed;
    }

    public void resync(LinkedAccount account) {
        plugin.getBotBridge().syncRoles(account, true, Bukkit.getPlayer(account.uuid()) != null);
    }

    public List<LinkedAccount> search(String filter, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        return plugin.getDataStore().accounts().stream()
                .filter(account -> switch (filter == null ? "all" : filter) {
                    case "online" -> Bukkit.getPlayer(account.uuid()) != null;
                    case "offline" -> Bukkit.getPlayer(account.uuid()) == null;
                    default -> true;
                })
                .filter(account -> query.isEmpty()
                        || account.username().toLowerCase(Locale.ROOT).contains(query)
                        || account.uuid().toString().startsWith(query)
                        || account.discordId().equals(query)
                        || (account.discordName() != null && account.discordName().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(Comparator.comparing(account -> account.username().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public boolean isLinked(UUID uuid) {
        return plugin.getDataStore().byUuid(uuid).isPresent();
    }

    public boolean isBypassed(Player player) {
        String permission = config().bypassPermission();
        if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
            return true;
        }
        return isBypassed(player.getUniqueId(), player.getName());
    }

    public boolean isBypassed(UUID uuid, String name) {
        return plugin.getDataStore().bypass(uuid) || hasListBypass(uuid, name);
    }

    public boolean hasListBypass(UUID uuid, String name) {
        return config().bypassPlayers().stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(name) || entry.equalsIgnoreCase(uuid.toString()));
    }
}
