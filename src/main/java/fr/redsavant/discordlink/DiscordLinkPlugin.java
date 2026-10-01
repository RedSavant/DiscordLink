package fr.redsavant.discordlink;

import fr.redsavant.discordlink.logger.LogLevel;
import fr.redsavant.discordlink.logger.Logger;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DiscordLinkPlugin extends JavaPlugin {

    @Getter
    private static DiscordLinkPlugin instance;

    @Getter
    private Logger dlogger;

    private List<String> authors = this.getPluginMeta().getAuthors();

    @Override
    public void onEnable() {
        instance = this;
        super.onEnable();

        // Instanciations
        dlogger = new Logger(LogLevel.INFO);

        String authorsformatted = String.join(", ", authors);


        // Starting
        dlogger.log("══════════════════════════════", LogLevel.NEUTRAL);
        dlogger.log("<color:#4401ff>DiscordLink</color> <gray>v</gray><green>" + this.getPluginMeta().getVersion().toLowerCase() + "</green>", LogLevel.NEUTRAL);
        dlogger.log("Author(s): " + authorsformatted, LogLevel.NEUTRAL);
        dlogger.log("══════════════════════════════", LogLevel.NEUTRAL);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

}
