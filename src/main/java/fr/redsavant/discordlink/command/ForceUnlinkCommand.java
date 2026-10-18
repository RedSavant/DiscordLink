package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.storage.LinkedAccount;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.Optional;

public class ForceUnlinkCommand extends BaseCommand {

    public ForceUnlinkCommand(DiscordLinkPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!requirePermission(sender, "discordlink.admin")) {
            return;
        }
        if (args.length == 0) {
            lang().send(sender, "minecraft.forceunlink.usage");
            return;
        }
        Optional<LinkedAccount> account = findAccount(args[0]);
        if (account.isEmpty()) {
            lang().send(sender, "minecraft.errors.player-not-found", "player", args[0]);
            return;
        }
        plugin.getLinkManager().unlink(account.get().uuid(), sender.getName());
        lang().send(sender, "minecraft.forceunlink.success", "player", account.get().username());
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? linkedNames(args.length == 0 ? "" : args[0]) : empty();
    }

    @Override
    public String permission() {
        return "discordlink.admin";
    }
}
