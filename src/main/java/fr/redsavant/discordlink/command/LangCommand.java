package fr.redsavant.discordlink.command;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.gui.LanguageMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.Collection;

public class LangCommand extends BaseCommand {

    public LangCommand(DiscordLinkPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!requirePlayer(source.getSender())) {
            return;
        }
        Player player = (Player) source.getSender();
        if (!plugin.getPluginConfig().allowPlayerChoice()) {
            lang().send(player, "minecraft.errors.lang-disabled");
            return;
        }
        if (args.length == 0) {
            if (plugin.getPluginConfig().guiEnabled()) {
                LanguageMenu.open(plugin, player, player.getUniqueId(), player.getName(), null);
                return;
            }
            lang().send(player, "minecraft.lang.current", "language", lang().displayName(lang().locale(player)));
            for (String locale : lang().locales()) {
                lang().sendRaw(player, "minecraft.lang.list-entry", "locale", locale, "language", lang().displayName(locale));
            }
            return;
        }
        String locale = lang().normalize(args[0]);
        if (locale == null) {
            lang().send(player, "minecraft.errors.unknown-locale", "locale", args[0]);
            return;
        }
        plugin.getDataStore().setPlayerLocale(player.getUniqueId(), locale);
        lang().send(player, "minecraft.lang.changed", "language", lang().displayName(locale));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? filter(lang().locales().stream(), args.length == 0 ? "" : args[0]) : empty();
    }

    @Override
    public String permission() {
        return "discordlink.lang";
    }
}
