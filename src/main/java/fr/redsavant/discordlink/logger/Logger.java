package fr.redsavant.discordlink.logger;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.Bukkit;

import static fr.redsavant.utils.Colorize.colorize;

public class Logger {
    private final LogLevel logLevel;

    public Logger(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public void log(String message, LogLevel level) {
        if (level != LogLevel.NEUTRAL && level.ordinal() < this.logLevel.ordinal()) {
            return;
        }
        String formattedMessage = switch (level) {
            case NEUTRAL -> "<color:#6B6B6B>[ᴅɪꜱᴄᴏʀᴅʟɪɴᴋ]</color> " + message;
            case INFO -> "<color:#6B6B6B>[ᴅɪꜱᴄᴏʀᴅʟɪɴᴋ]</color> <gradient:#00FF1C:#A4FAAD>{Info}</gradient> <reset>" + message;
            case WARNING -> "<color:#6B6B6B>[ᴅɪꜱᴄᴏʀᴅʟɪɴᴋ]</color> <gradient:#FFEE00:#F6F5E5>{Warning}</gradient> <reset>" + message;
            case ERROR -> "<color:#6B6B6B>[ᴅɪꜱᴄᴏʀᴅʟɪɴᴋ]</color> <gradient:#FF003E:#EAABBA>{Error}</gradient> <reset>" + message;
            case SEVERE -> "<color:#6B6B6B>[ᴅɪꜱᴄᴏʀᴅʟɪɴᴋ]</color> <color:#B3002C>{SEVERE}</color> <reset>" + message;
        };

        if (DiscordLinkPlugin.getInstance() != null) {
            DiscordLinkPlugin.getInstance().getServer().getConsoleSender().sendMessage(colorize(formattedMessage));
        } else {
            Bukkit.getConsoleSender().sendMessage(colorize(formattedMessage));
        }
    }
}