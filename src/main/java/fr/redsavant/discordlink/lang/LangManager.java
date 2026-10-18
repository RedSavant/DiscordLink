package fr.redsavant.discordlink.lang;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class LangManager {

    public static final List<String> BUILTIN = List.of("fr_FR", "en_US");
    public static final String FALLBACK = "en_US";

    private final DiscordLinkPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, YamlConfiguration> languages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public LangManager(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "languages");
    }

    public void load() {
        File folder = folder();
        folder.mkdirs();
        for (String locale : BUILTIN) {
            if (!new File(folder, locale + ".yml").exists()) {
                plugin.saveResource("languages/" + locale + ".yml", false);
            }
        }
        languages.clear();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String locale = file.getName().substring(0, file.getName().length() - 4);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                YamlConfiguration bundled = bundled(locale);
                YamlConfiguration defaults = bundled != null ? bundled : bundled(FALLBACK);
                if (defaults != null) {
                    config.setDefaults(defaults);
                    config.options().copyDefaults(true);
                }
                languages.put(locale, config);
            }
        }
        if (!languages.containsKey(defaultLocale())) {
            plugin.getDlogger().warn("Default language '" + plugin.getPluginConfig().defaultLocale() + "' not found, using " + FALLBACK + ".");
        }
        plugin.getDlogger().info("Loaded " + languages.size() + " language(s): " + String.join(", ", languages.keySet()));
    }

    private YamlConfiguration bundled(String locale) {
        try (InputStream in = plugin.getResource("languages/" + locale + ".yml")) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    public Set<String> locales() {
        return Collections.unmodifiableSet(languages.keySet());
    }

    public String defaultLocale() {
        String configured = plugin.getPluginConfig().defaultLocale();
        String normalized = normalize(configured);
        return normalized != null ? normalized : FALLBACK;
    }

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String candidate = input.trim().replace('-', '_');
        for (String locale : languages.keySet()) {
            if (locale.equalsIgnoreCase(candidate)) {
                return locale;
            }
        }
        for (String locale : languages.keySet()) {
            List<String> aliases = languages.get(locale).getStringList("meta.minecraft-locales");
            if (aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(candidate))) {
                return locale;
            }
        }
        String language = candidate.split("_")[0];
        for (String locale : languages.keySet()) {
            if (locale.split("_")[0].equalsIgnoreCase(language)) {
                return locale;
            }
        }
        return null;
    }

    public String fromClient(Locale locale) {
        return locale == null ? null : normalize(locale.toString());
    }

    public String displayName(String locale) {
        YamlConfiguration lang = languages.get(locale);
        return lang == null ? locale : lang.getString("meta.name", locale);
    }

    public String locale(UUID uuid) {
        String stored = normalize(plugin.getDataStore().playerLocale(uuid));
        return stored != null ? stored : defaultLocale();
    }

    public String locale(CommandSender sender) {
        return sender instanceof Player player ? locale(player.getUniqueId()) : defaultLocale();
    }

    public YamlConfiguration language(String locale) {
        YamlConfiguration lang = languages.get(locale);
        if (lang == null) {
            lang = languages.get(defaultLocale());
        }
        return lang;
    }

    public ConfigurationSection section(String locale, String path) {
        YamlConfiguration lang = language(locale);
        ConfigurationSection section = lang == null ? null : lang.getConfigurationSection(path);
        if (section == null && languages.get(defaultLocale()) != null) {
            section = languages.get(defaultLocale()).getConfigurationSection(path);
        }
        return section;
    }

    public String raw(String locale, String key) {
        String value = lookup(languages.get(locale), key);
        if (value == null) {
            value = lookup(languages.get(defaultLocale()), key);
        }
        if (value == null) {
            value = lookup(languages.get(FALLBACK), key);
        }
        return value == null ? key : value;
    }

    private String lookup(YamlConfiguration lang, String key) {
        if (lang == null || !lang.contains(key)) {
            return null;
        }
        if (lang.isList(key)) {
            return String.join("\n", lang.getStringList(key));
        }
        return lang.getString(key);
    }

    public List<String> rawList(String locale, String key) {
        YamlConfiguration lang = language(locale);
        if (lang != null && lang.isList(key)) {
            return lang.getStringList(key);
        }
        YamlConfiguration fallback = languages.get(defaultLocale());
        if (fallback != null && fallback.isList(key)) {
            return fallback.getStringList(key);
        }
        return List.of();
    }

    public String format(String text, boolean escape, Object... placeholders) {
        String result = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            Object placeholder = placeholders[i + 1];
            String value = String.valueOf(placeholder);
            boolean trusted = placeholder instanceof Rich;
            result = result.replace("{" + placeholders[i] + "}", escape && !trusted ? miniMessage.escapeTags(value) : value);
        }
        return result;
    }

    public Rich rich(String locale, String key) {
        return new Rich(raw(locale, key));
    }

    /**
     * Placeholder value coming from a language file: its MiniMessage tags are kept instead of escaped.
     */
    public record Rich(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    public String plain(String locale, String key, Object... placeholders) {
        return format(raw(locale, key), false, placeholders);
    }

    public Component component(String locale, String key, Object... placeholders) {
        return parse(raw(locale, key), placeholders);
    }

    public Component parse(String text, Object... placeholders) {
        return miniMessage.deserialize(format(text, true, placeholders));
    }

    public Component item(String text, Object... placeholders) {
        return parse(text, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public List<Component> lore(List<String> lines, Object... placeholders) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            for (String split : format(line, true, placeholders).split("\n", -1)) {
                lore.add(miniMessage.deserialize(split).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        return lore;
    }

    public String date(String locale, long timestamp) {
        Locale javaLocale = Locale.forLanguageTag(locale.replace('_', '-'));
        try {
            return DateTimeFormatter.ofPattern(raw(locale, "minecraft.date-format"), javaLocale)
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
        }
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        String locale = locale(sender);
        sender.sendMessage(parse(raw(locale, "minecraft.prefix") + raw(locale, key), placeholders));
    }

    public void sendRaw(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(component(locale(sender), key, placeholders));
    }

    public String console(String key, Object... placeholders) {
        return format(raw(defaultLocale(), "console." + key), false, placeholders);
    }

    public void override(String locale, Map<String, String> values) {
        File file = new File(folder(), locale + ".yml");
        if (!file.exists() || values.isEmpty()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        values.forEach(config::set);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getDlogger().error("Unable to save " + file.getName() + ": " + e.getMessage());
        }
    }

    public JsonObject discordBundle() {
        JsonObject bundle = new JsonObject();
        for (Map.Entry<String, YamlConfiguration> entry : languages.entrySet()) {
            JsonObject lang = new JsonObject();
            lang.add("meta", toJson(entry.getValue(), "meta"));
            lang.add("discord", toJson(entry.getValue(), "discord"));
            bundle.add(entry.getKey(), lang);
        }
        return bundle;
    }

    private JsonObject toJson(YamlConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null && config.getDefaults() != null) {
            section = config.getDefaults().getConfigurationSection(path);
        }
        return section == null ? new JsonObject() : sectionToJson(section, config, path);
    }

    private JsonObject sectionToJson(ConfigurationSection section, YamlConfiguration root, String path) {
        JsonObject json = new JsonObject();
        for (String key : section.getKeys(false)) {
            String child = path + "." + key;
            if (root.isConfigurationSection(child)) {
                json.add(key, sectionToJson(root.getConfigurationSection(child), root, child));
            } else if (root.isList(child)) {
                JsonArray array = new JsonArray();
                root.getStringList(child).forEach(array::add);
                json.add(key, array);
            } else {
                json.addProperty(key, root.getString(child));
            }
        }
        return json;
    }
}
