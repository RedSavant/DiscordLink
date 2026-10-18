package fr.redsavant.discordlink.config;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ConfigMigrator {

    public static final String LEGACY_LOCALE = "fr_FR";

    private static final Pattern LEGACY_PLACEHOLDER = Pattern.compile("%([a-zA-Z_-]+)%");

    private static final Map<String, String> MESSAGE_KEYS = Map.ofEntries(
            Map.entry("messages.prefix", "minecraft.prefix"),
            Map.entry("messages.link-success", "minecraft.link.linked"),
            Map.entry("messages.already-linked", "minecraft.link.already-linked"),
            Map.entry("messages.code-generated", "minecraft.link.code"),
            Map.entry("messages.unlink-success", "minecraft.unlink.success"),
            Map.entry("messages.not-linked", "minecraft.unlink.not-linked"),
            Map.entry("messages.unlink-other-success", "minecraft.forceunlink.success"),
            Map.entry("messages.player-not-found", "minecraft.errors.player-not-found"),
            Map.entry("messages.info-not-linked", "minecraft.linkinfo.not-linked"),
            Map.entry("messages.reload-success", "minecraft.reload.success"),
            Map.entry("messages.no-permission", "minecraft.errors.no-permission"),
            Map.entry("link-required.kick-message", "minecraft.kick.not-linked"),
            Map.entry("link-command.message", "minecraft.link.code")
    );

    private final DiscordLinkPlugin plugin;

    public ConfigMigrator(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, String> migrate() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            return Map.of();
        }
        YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
        if (old.contains("config-version")) {
            return Map.of();
        }

        plugin.getDlogger().info("Legacy v1 config.yml detected, migrating to v2...");
        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "config-v1.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getDlogger().error("Unable to backup config.yml: " + e.getMessage());
            return Map.of();
        }

        YamlConfiguration updated = loadResource("config.yml");
        updated.set("language.default", LEGACY_LOCALE);
        copy(old, updated, "discord.token", "discord.token");
        copy(old, updated, "discord.guild-id", "discord.guild-id");
        copy(old, updated, "discord.enabled", "discord.enabled");
        copy(old, updated, "roles.enabled", "roles.linked.enabled");
        copy(old, updated, "roles.enabled", "roles.online.enabled");
        copy(old, updated, "roles.linked-role", "roles.linked.role-id");
        copy(old, updated, "roles.online-role", "roles.online.role-id");
        copy(old, updated, "roles.remove-online-on-disconnect", "roles.online.remove-on-quit");
        copy(old, updated, "logging.enabled", "logs.enabled");
        copy(old, updated, "logging.channel-id", "logs.channel-id");
        copy(old, updated, "logging.log-join", "logs.events.join");
        copy(old, updated, "logging.log-quit", "logs.events.quit");
        copy(old, updated, "logging.log-link", "logs.events.link");
        copy(old, updated, "logging.log-unlink", "logs.events.unlink");
        copy(old, updated, "logging.embed-color.join", "logs.colors.join");
        copy(old, updated, "logging.embed-color.quit", "logs.colors.quit");
        copy(old, updated, "logging.embed-color.link", "logs.colors.link");
        copy(old, updated, "logging.embed-color.unlink", "logs.colors.unlink");
        copy(old, updated, "link-required.enabled", "linking.required");
        copy(old, updated, "link-command.code-expiration", "linking.code.expiration");
        copy(old, updated, "link-command.code-length", "linking.code.length");
        copy(old, updated, "database.filename", "database.file");
        copy(old, updated, "debug", "debug");

        List<String> bypass = old.getStringList("link-required.bypass");
        updated.set("linking.bypass.players", bypass.stream()
                .filter(name -> !name.equals("VotreNomDeJoueur") && !name.equals("AutreJoueur"))
                .toList());

        try {
            updated.save(file);
        } catch (IOException e) {
            plugin.getDlogger().error("Unable to save migrated config.yml: " + e.getMessage());
        }

        YamlConfiguration legacyDefaults = loadResource("legacy/v1-defaults.yml");
        Map<String, String> messages = new LinkedHashMap<>();
        MESSAGE_KEYS.forEach((oldKey, newKey) -> {
            String value = old.getString(oldKey);
            String original = legacyDefaults.getString(oldKey);
            if (value != null && !value.isBlank() && (original == null || !original.strip().equals(value.strip()))) {
                messages.put(newKey, toMiniMessage(value.stripTrailing()));
            }
        });
        plugin.getDlogger().info("Config migrated (backup saved as config-v1.yml).");
        return messages;
    }

    private YamlConfiguration loadResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    private void copy(YamlConfiguration from, YamlConfiguration to, String fromPath, String toPath) {
        if (from.contains(fromPath) && !from.isConfigurationSection(fromPath)) {
            to.set(toPath, from.get(fromPath));
        }
    }

    private String toMiniMessage(String legacy) {
        String withPlaceholders = LEGACY_PLACEHOLDER.matcher(legacy).replaceAll("{$1}");
        return MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacySection().deserialize(withPlaceholders));
    }
}
