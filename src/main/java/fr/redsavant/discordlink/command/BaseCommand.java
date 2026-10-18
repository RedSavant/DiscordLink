package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.lang.LangManager;
import fr.redsavant.discordlink.storage.LinkedAccount;
import io.papermc.paper.command.brigadier.BasicCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public abstract class BaseCommand implements BasicCommand {

    protected final DiscordLinkPlugin plugin;

    protected BaseCommand(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    protected LangManager lang() {
        return plugin.getLangManager();
    }

    protected boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        lang().send(sender, "minecraft.errors.players-only");
        return false;
    }

    protected boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        lang().send(sender, "minecraft.errors.no-permission");
        return false;
    }

    protected Optional<LinkedAccount> findAccount(String input) {
        try {
            Optional<LinkedAccount> byUuid = plugin.getDataStore().byUuid(UUID.fromString(input));
            if (byUuid.isPresent()) {
                return byUuid;
            }
        } catch (IllegalArgumentException ignored) {
        }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            Optional<LinkedAccount> byOnline = plugin.getDataStore().byUuid(online.getUniqueId());
            if (byOnline.isPresent()) {
                return byOnline;
            }
        }
        return plugin.getDataStore().byName(input);
    }

    protected Collection<String> linkedNames(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return plugin.getDataStore().accounts().stream()
                .map(LinkedAccount::username)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(50)
                .toList();
    }

    protected Collection<String> filter(Stream<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    protected static List<String> empty() {
        return List.of();
    }
}
