package fr.redsavant.discordlink;

import fr.redsavant.discordlink.logger.LogLevel;
import fr.redsavant.discordlink.logger.Logger;
import fr.redsavant.discordlink.runtime.BotManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class DiscordLinkPlugin extends JavaPlugin {

    @Getter
    private static DiscordLinkPlugin instance;

    @Getter
    private Logger dlogger;

    private BotManager botManager;

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

        File config = new File(getDataFolder(), "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        }

        String discordToken = getConfig().getString("discord.token");
        String guildId = getConfig().getString("discord.guild-id");
        boolean debug = getConfig().getBoolean("debug", false);

        if (discordToken == null || discordToken.isEmpty() || discordToken.equals("DISCORD_BOT_TOKEN")) {
            dlogger.log("Discord token is missing in config.yml !", LogLevel.ERROR);
            return;
        }

        botManager = new BotManager(getDataFolder(), discordToken, guildId, debug);
        botManager.start();
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (botManager != null) {
            botManager.destroy();
        }
    }

}
