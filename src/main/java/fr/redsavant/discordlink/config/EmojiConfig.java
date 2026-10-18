package fr.redsavant.discordlink.config;

import com.google.gson.JsonObject;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiConfig {

    private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:[A-Za-z0-9_~]{2,32}:(\\d{17,21})>");

    private final DiscordLinkPlugin plugin;
    private YamlConfiguration config = new YamlConfiguration();

    public EmojiConfig(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "emojis.yml");
        if (!file.exists()) {
            plugin.saveResource("emojis.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("emojis.yml")) {
            if (in != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                config.options().copyDefaults(true);
                config.save(file);
            }
        } catch (IOException e) {
            plugin.getDlogger().error("Unable to update emojis.yml: " + e.getMessage());
        }
    }

    public String mode() {
        return config.getString("mode", "BASE").toUpperCase(Locale.ROOT).equals("CUSTOM") ? "CUSTOM" : "BASE";
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        ConfigurationSection base = config.getConfigurationSection("base");
        if (base == null) {
            return json;
        }
        boolean custom = mode().equals("CUSTOM");
        List<String> fallbacks = new ArrayList<>();
        for (String key : base.getKeys(false)) {
            String value = base.getString(key, "");
            if (custom) {
                String customValue = config.getString("custom." + key, "").trim();
                Matcher matcher = CUSTOM_EMOJI.matcher(customValue);
                if (matcher.matches() && !matcher.group(1).matches("0+")) {
                    value = customValue;
                } else {
                    fallbacks.add(key);
                }
            }
            json.addProperty(key, value);
        }
        if (custom && !fallbacks.isEmpty()) {
            plugin.getDlogger().debug("emojis.yml: " + fallbacks.size() + " custom emoji(s) not set, using base ones: " + String.join(", ", fallbacks));
        }
        return json;
    }
}
