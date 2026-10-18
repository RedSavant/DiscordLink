package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.gui.MainMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

public class DiscordLinkCommand extends BaseCommand {

    public DiscordLinkCommand(DiscordLinkPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!requirePermission(sender, "discordlink.admin")) {
                    return;
                }
                plugin.reload();
                lang().send(sender, "minecraft.reload.success");
            }
            case "gui" -> {
                if (!requirePlayer(sender) || !requirePermission(sender, "discordlink.gui")) {
                    return;
                }
                if (!plugin.getPluginConfig().guiEnabled()) {
                    lang().send(sender, "minecraft.errors.gui-disabled");
                    return;
                }
                MainMenu.open(plugin, (Player) sender);
            }
            case "version" -> lang().send(sender, "minecraft.version", "version", plugin.getPluginMeta().getVersion());
            default -> lang().sendRaw(sender, "minecraft.help");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) {
            return empty();
        }
        CommandSender sender = source.getSender();
        Stream<String> options = Stream.of("help", "version", "reload", "gui")
                .filter(option -> switch (option) {
                    case "reload" -> sender.hasPermission("discordlink.admin");
                    case "gui" -> sender.hasPermission("discordlink.gui");
                    default -> true;
                });
        return filter(options, args.length == 0 ? "" : args[0]);
    }
}
