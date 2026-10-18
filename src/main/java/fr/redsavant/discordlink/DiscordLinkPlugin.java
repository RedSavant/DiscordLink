package fr.redsavant.discordlink;

import fr.redsavant.discordlink.bridge.BotBridge;
import fr.redsavant.discordlink.command.DiscordLinkCommand;
import fr.redsavant.discordlink.command.ForceUnlinkCommand;
import fr.redsavant.discordlink.command.LangCommand;
import fr.redsavant.discordlink.command.LinkCommand;
import fr.redsavant.discordlink.command.LinkInfoCommand;
import fr.redsavant.discordlink.command.UnlinkCommand;
import fr.redsavant.discordlink.config.ConfigMigrator;
import fr.redsavant.discordlink.config.EmojiConfig;
import fr.redsavant.discordlink.config.PluginConfig;
import fr.redsavant.discordlink.config.SettingsService;
import fr.redsavant.discordlink.gui.GuiManager;
import fr.redsavant.discordlink.lang.LangManager;
import fr.redsavant.discordlink.link.LinkManager;
import fr.redsavant.discordlink.listener.ChatInput;
import fr.redsavant.discordlink.listener.ConnectionListener;
import fr.redsavant.discordlink.logger.LogLevel;
import fr.redsavant.discordlink.logger.Logger;
import fr.redsavant.discordlink.runtime.BotManager;
import fr.redsavant.discordlink.storage.DataStore;
import fr.redsavant.discordlink.util.UpdateChecker;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DiscordLinkPlugin extends JavaPlugin {

    @Getter
    private static DiscordLinkPlugin instance;

    @Getter
    private Logger dlogger;
    @Getter
    private PluginConfig pluginConfig;
    @Getter
    private EmojiConfig emojiConfig;
    @Getter
    private LangManager langManager;
    @Getter
    private DataStore dataStore;
    @Getter
    private LinkManager linkManager;
    @Getter
    private SettingsService settingsService;
    @Getter
    private BotBridge botBridge;
    @Getter
    private BotManager botManager;
    @Getter
    private GuiManager guiManager;
    @Getter
    private ChatInput chatInput;
    @Getter
    private UpdateChecker updateChecker;
    @Getter
    private final long startedAt = System.currentTimeMillis();

    private BukkitTask cleanupTask;
    private BukkitTask statusTask;
    private String botFingerprint;
    private int botGeneration;

    @Override
    public void onEnable() {
        instance = this;

        dlogger = new Logger(LogLevel.INFO);

        String authorsFormatted = String.join(", ", getPluginMeta().getAuthors());
        dlogger.log("══════════════════════════════", LogLevel.NEUTRAL);
        dlogger.log("<color:#4401ff>DiscordLink</color> <gray>v</gray><green>" + getPluginMeta().getVersion().toLowerCase() + "</green>", LogLevel.NEUTRAL);
        dlogger.log("Author(s): " + authorsFormatted, LogLevel.NEUTRAL);
        dlogger.log("══════════════════════════════", LogLevel.NEUTRAL);

        Map<String, String> legacyMessages = new ConfigMigrator(this).migrate();
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        pluginConfig.load();
        dlogger.setLogLevel(pluginConfig.debug() ? LogLevel.DEBUG : LogLevel.INFO);

        emojiConfig = new EmojiConfig(this);
        emojiConfig.load();

        langManager = new LangManager(this);
        langManager.load();
        if (!legacyMessages.isEmpty()) {
            langManager.override(ConfigMigrator.LEGACY_LOCALE, legacyMessages);
            langManager.load();
        }

        dataStore = new DataStore(this);
        try {
            dataStore.open();
        } catch (SQLException e) {
            dlogger.log("Unable to open the database: " + e.getMessage(), LogLevel.SEVERE);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        linkManager = new LinkManager(this);
        settingsService = new SettingsService(this);
        botBridge = new BotBridge(this);
        guiManager = new GuiManager(this);
        chatInput = new ChatInput(this);

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(chatInput, this);

        registerCommand("link", new LinkCommand(this));
        registerCommand("unlink", new UnlinkCommand(this));
        registerCommand("linkinfo", new LinkInfoCommand(this));
        registerCommand("lang", new LangCommand(this));
        registerCommand("forceunlink", new ForceUnlinkCommand(this));
        registerCommand("discordlink", List.of("dl"), new DiscordLinkCommand(this));

        startBot();
        startTasks();

        updateChecker = new UpdateChecker(this);
        if (pluginConfig.checkUpdates()) {
            updateChecker.check();
        }
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (botManager != null) {
            botManager.destroy();
        }
        if (dataStore != null) {
            dataStore.close();
        }
    }

    private String fingerprint() {
        return pluginConfig.discordEnabled() + "|" + pluginConfig.token() + "|" + pluginConfig.guildId() + "|" + pluginConfig.usesLinkChannel();
    }

    private void startBot() {
        botFingerprint = fingerprint();
        if (!pluginConfig.discordEnabled()) {
            dlogger.info(langManager.console("bot-disabled"));
            return;
        }
        if (!pluginConfig.hasValidToken()) {
            dlogger.error(langManager.console("token-missing"));
            return;
        }
        if (botManager == null) {
            botManager = new BotManager(this, botBridge);
        }
        botManager.start();
    }

    private void startTasks() {
        stopTasks();
        long cleanupTicks = pluginConfig.cleanupInterval() * 20L;
        cleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int removed = linkManager.clearExpired();
            if (removed > 0) {
                dlogger.debug("Removed " + removed + " expired code(s).");
            }
        }, cleanupTicks, cleanupTicks);
        long statusTicks = pluginConfig.activityInterval() * 20L;
        statusTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (botBridge.isReady()) {
                botBridge.pushStatus();
            }
        }, statusTicks, statusTicks);
    }

    private void stopTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (statusTask != null) {
            statusTask.cancel();
        }
    }

    public void applySettings() {
        dlogger.setLogLevel(pluginConfig.debug() ? LogLevel.DEBUG : LogLevel.INFO);
        startTasks();
        refreshBot(40L);
    }

    public void reload() {
        pluginConfig.load();
        emojiConfig.load();
        langManager.load();
        dlogger.setLogLevel(pluginConfig.debug() ? LogLevel.DEBUG : LogLevel.INFO);
        startTasks();
        refreshBot(0L);
    }

    private void refreshBot(long delayTicks) {
        if (fingerprint().equals(botFingerprint)) {
            botBridge.pushConfig();
            if (botBridge.isReady()) {
                botBridge.pushStatus();
            }
            return;
        }
        botFingerprint = fingerprint();
        // Only the latest refresh may start a bot, otherwise quick successive changes leave two bots running
        int generation = ++botGeneration;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (generation != botGeneration) {
                return;
            }
            BotManager current = botManager;
            botManager = null;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                if (current != null) {
                    current.destroy();
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    if (generation == botGeneration && botManager == null) {
                        startBot();
                    }
                });
            });
        }, delayTicks);
    }
}
