package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.storage.LinkedAccount;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

public class LinkInfoCommand extends BaseCommand {

    public LinkInfoCommand(DiscordLinkPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            if (!requirePlayer(sender)) {
                return;
            }
            Player player = (Player) sender;
            Optional<LinkedAccount> account = plugin.getDataStore().byUuid(player.getUniqueId());
            if (account.isEmpty()) {
                lang().send(sender, "minecraft.linkinfo.not-linked");
                return;
            }
            show(sender, account.get());
            return;
        }
        if (!requirePermission(sender, "discordlink.info.others")) {
            return;
        }
        Optional<LinkedAccount> account = findAccount(args[0]);
        if (account.isEmpty()) {
            lang().send(sender, "minecraft.linkinfo.other-not-linked", "player", args[0]);
            return;
        }
        show(sender, account.get());
    }

    private void show(CommandSender sender, LinkedAccount account) {
        String locale = lang().locale(sender);
        lang().sendRaw(sender, "minecraft.linkinfo.details",
                "player", account.username(),
                "uuid", account.uuid(),
                "discord", account.displayDiscordName(),
                "discord_id", account.discordId(),
                "date", lang().date(locale, account.linkedAt()),
                "language", lang().displayName(lang().locale(account.uuid())));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1 && source.getSender().hasPermission("discordlink.info.others")) {
            return linkedNames(args.length == 0 ? "" : args[0]);
        }
        return empty();
    }

    @Override
    public String permission() {
        return "discordlink.info";
    }
}
