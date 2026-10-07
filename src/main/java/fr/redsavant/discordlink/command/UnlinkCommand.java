package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

public class UnlinkCommand extends BaseCommand {

    public UnlinkCommand(DiscordLinkPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!requirePlayer(source.getSender())) {
            return;
        }
        Player player = (Player) source.getSender();
        if (!plugin.getLinkManager().isLinked(player.getUniqueId())) {
            lang().send(player, "minecraft.unlink.not-linked");
            return;
        }
        lang().send(player, "minecraft.unlink.success");
        plugin.getLinkManager().unlink(player.getUniqueId(), player.getName());
    }

    @Override
    public String permission() {
        return "discordlink.unlink";
    }
}
