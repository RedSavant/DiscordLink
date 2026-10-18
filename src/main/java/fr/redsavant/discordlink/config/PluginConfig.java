package fr.redsavant.discordlink.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PluginConfig {

    private static final Set<String> HIDDEN_PATHS = Set.of("discord.token");
    private static final Set<String> PLACEHOLDER_TOKENS = Set.of("YOUR_BOT_TOKEN", "DISCORD_BOT_TOKEN", "VOTRE_TOKEN_BOT_ICI");

    private final DiscordLinkPlugin plugin;

    public PluginConfig(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    public void load() {
        plugin.reloadConfig();
        raw().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public void save() {
        plugin.saveConfig();
    }

    public void set(String path, Object value) {
        raw().set(path, value);
        save();
    }

    public String defaultLocale() {
        return raw().getString("language.default", "en_US");
    }

    public boolean autoDetectClient() {
        return raw().getBoolean("language.auto-detect-client", true);
    }

    public boolean allowPlayerChoice() {
        return raw().getBoolean("language.allow-player-choice", true);
    }

    public boolean discordEnabled() {
        return raw().getBoolean("discord.enabled", true);
    }

    public String token() {
        return raw().getString("discord.token", "");
    }

    public boolean hasValidToken() {
        String token = token();
        return token != null && !token.isBlank() && !PLACEHOLDER_TOKENS.contains(token);
    }

    public String guildId() {
        return raw().getString("discord.guild-id", "");
    }

    public int activityInterval() {
        return Math.max(15, raw().getInt("discord.activity.update-interval", 60));
    }

    public boolean linkingRequired() {
        return raw().getBoolean("linking.required", true);
    }

    public String linkMode() {
        String mode = raw().getString("linking.mode", "COMMAND").toUpperCase(Locale.ROOT);
        return SettingsService.LINK_MODES.contains(mode) ? mode : "COMMAND";
    }

    public boolean usesLinkChannel() {
        return !linkMode().equals("COMMAND");
    }

    public int codeLength() {
        return Math.clamp(raw().getInt("linking.code.length", 6), 4, 16);
    }

    public int codeExpiration() {
        return Math.max(30, raw().getInt("linking.code.expiration", 300));
    }

    public String codeCharset() {
        String charset = raw().getString("linking.code.charset", "");
        return charset == null || charset.isEmpty() ? "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" : charset;
    }

    public int cleanupInterval() {
        return Math.max(10, raw().getInt("linking.cleanup-interval", 60));
    }

    public String bypassPermission() {
        return raw().getString("linking.bypass.permission", "discordlink.bypass");
    }

    public List<String> bypassPlayers() {
        return raw().getStringList("linking.bypass.players");
    }

    public boolean logEvent(String event) {
        return raw().getBoolean("logs.enabled", true) && raw().getBoolean("logs.events." + event, true);
    }

    public boolean showIp() {
        return raw().getBoolean("logs.show-ip", true);
    }

    public boolean guiEnabled() {
        return raw().getBoolean("gui.enabled", true);
    }

    public int playersRows() {
        return Math.clamp(raw().getInt("gui.players-rows", 6), 2, 6);
    }

    public boolean guiConfirm() {
        return raw().getBoolean("gui.confirm-actions", true);
    }

    public int searchTimeout() {
        return Math.max(5, raw().getInt("gui.search-timeout", 30));
    }

    public boolean soundsEnabled() {
        return raw().getBoolean("gui.sounds.enabled", true);
    }

    public String sound(String type) {
        return raw().getString("gui.sounds." + type, "");
    }

    public boolean fillerEnabled() {
        return raw().getBoolean("gui.filler.enabled", true);
    }

    public String fillerMaterial() {
        return raw().getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE");
    }

    public String databaseFile() {
        return raw().getString("database.file", "links.db");
    }

    public boolean debug() {
        return raw().getBoolean("debug", false);
    }

    public boolean checkUpdates() {
        return raw().getBoolean("check-updates", true);
    }

    public JsonObject toJson() {
        return sectionToJson(raw(), "");
    }

    private JsonObject sectionToJson(ConfigurationSection section, String prefix) {
        JsonObject json = new JsonObject();
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (HIDDEN_PATHS.contains(path)) {
                continue;
            }
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                json.add(key, sectionToJson(child, path));
            } else {
                json.add(key, toJsonElement(value));
            }
        }
        return json;
    }

    private JsonElement toJsonElement(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(item -> array.add(toJsonElement(item)));
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            map.forEach((k, v) -> object.add(String.valueOf(k), toJsonElement(v)));
            return object;
        }
        return new JsonPrimitive(String.valueOf(value));
    }
}
