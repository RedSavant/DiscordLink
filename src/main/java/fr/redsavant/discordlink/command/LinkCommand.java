package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.link.LinkManager;
import fr.redsavant.discordlink.storage.LinkedAccount;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LinkCommand extends BaseCommand {

    public LinkCommand(DiscordLinkPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!requirePlayer(source.getSender())) {
            return;
        }
        Player player = (Player) source.getSender();
        Optional<LinkedAccount> account = plugin.getDataStore().byUuid(player.getUniqueId());
        if (account.isPresent()) {
            lang().send(player, "minecraft.link.already-linked", "discord", account.get().displayDiscordName());
            return;
        }
        LinkManager.PendingCode code = plugin.getLinkManager().code(player.getUniqueId(), player.getName());
        player.sendMessage(plugin.getLinkManager().codeMessage(lang().locale(player), "minecraft.link.code", code, player.getName(), true));
    }

    @Override
    public String permission() {
        return "discordlink.link";
    }
}
