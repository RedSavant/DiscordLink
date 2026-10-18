package fr.redsavant.discordlink.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.redsavant.discordlink.DiscordLinkPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class SettingsService {

    public enum Type {
        BOOLEAN,
        INTEGER,
        SNOWFLAKE,
        SNOWFLAKE_LIST,
        LOCALE,
        ENUM
    }

    public record Setting(String id, String path, Type type, int min, int max, int step, List<String> values) {

        static Setting toggle(String path) {
            return new Setting(path.replace('.', '-'), path, Type.BOOLEAN, 0, 0, 0, List.of());
        }

        static Setting number(String path, int min, int max, int step) {
            return new Setting(path.replace('.', '-'), path, Type.INTEGER, min, max, step, List.of());
        }

        static Setting of(String path, Type type) {
            return new Setting(path.replace('.', '-'), path, type, 0, 0, 0, List.of());
        }

        static Setting choice(String path, List<String> values) {
            return new Setting(path.replace('.', '-'), path, Type.ENUM, 0, 0, 0, values);
        }
    }

    public record Result(boolean success, String error) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String error) {
            return new Result(false, error);
        }
    }

    public static final List<String> LINK_MODES = List.of("COMMAND", "CHANNEL", "BOTH");

    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{15,21}");

    public static final List<Setting> SETTINGS = List.of(
            Setting.toggle("linking.required"),
            Setting.toggle("linking.channel.delete-messages"),
            Setting.toggle("roles.linked.enabled"),
            Setting.toggle("roles.linked.remove-on-unlink"),
            Setting.toggle("roles.online.enabled"),
            Setting.toggle("roles.online.remove-on-quit"),
            Setting.toggle("roles.resync-on-startup"),
            Setting.toggle("roles.sync-nickname"),
            Setting.toggle("logs.enabled"),
            Setting.toggle("logs.show-ip"),
            Setting.toggle("logs.events.join"),
            Setting.toggle("logs.events.quit"),
            Setting.toggle("logs.events.link"),
            Setting.toggle("logs.events.unlink"),
            Setting.toggle("logs.events.panel-actions"),
            Setting.toggle("logs.events.gui-actions"),
            Setting.toggle("language.auto-detect-client"),
            Setting.toggle("language.auto-detect-discord"),
            Setting.toggle("language.allow-player-choice"),
            Setting.toggle("discord.activity.enabled"),
            Setting.toggle("panel.confirm-actions"),
            Setting.toggle("gui.confirm-actions"),
            Setting.number("linking.code.length", 4, 16, 1),
            Setting.number("linking.code.expiration", 30, 3600, 30),
            Setting.number("discord.activity.update-interval", 15, 3600, 15),
            Setting.number("panel.players-per-page", 1, 25, 1),
            Setting.of("roles.linked.role-id", Type.SNOWFLAKE),
            Setting.of("roles.online.role-id", Type.SNOWFLAKE),
            Setting.of("logs.channel-id", Type.SNOWFLAKE),
            Setting.of("linking.channel.channel-id", Type.SNOWFLAKE),
            Setting.choice("linking.mode", LINK_MODES),
            Setting.of("panel.allowed-roles", Type.SNOWFLAKE_LIST),
            Setting.of("language.default", Type.LOCALE)
    );

    private final DiscordLinkPlugin plugin;

    public SettingsService(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<Setting> find(String idOrPath) {
        return SETTINGS.stream().filter(s -> s.id().equals(idOrPath) || s.path().equals(idOrPath)).findFirst();
    }

    public List<Setting> ofType(Type type) {
        return SETTINGS.stream().filter(s -> s.type() == type).toList();
    }

    public boolean bool(Setting setting) {
        return plugin.getPluginConfig().raw().getBoolean(setting.path());
    }

    public int integer(Setting setting) {
        return plugin.getPluginConfig().raw().getInt(setting.path());
    }

    public Result toggle(String id, String actor, String source) {
        Optional<Setting> setting = find(id);
        if (setting.isEmpty() || setting.get().type() != Type.BOOLEAN) {
            return Result.fail("unknown-setting");
        }
        return apply(setting.get(), !bool(setting.get()), actor, source);
    }

    public Result set(String id, JsonElement value, String actor, String source) {
        Optional<Setting> found = find(id);
        if (found.isEmpty()) {
            return Result.fail("unknown-setting");
        }
        Setting setting = found.get();
        try {
            Object parsed = switch (setting.type()) {
                case BOOLEAN -> value.getAsBoolean();
                case INTEGER -> {
                    int number = value.getAsInt();
                    if (number < setting.min() || number > setting.max()) {
                        throw new IllegalArgumentException("out-of-range");
                    }
                    yield number;
                }
                case SNOWFLAKE -> {
                    String snowflake = value.isJsonNull() ? "" : value.getAsString().trim();
                    if (!snowflake.isEmpty() && !SNOWFLAKE.matcher(snowflake).matches()) {
                        throw new IllegalArgumentException("invalid-id");
                    }
                    yield snowflake.isEmpty() ? "000000000000000000" : snowflake;
                }
                case SNOWFLAKE_LIST -> {
                    List<String> ids = new ArrayList<>();
                    for (JsonElement element : value.getAsJsonArray()) {
                        String snowflake = element.getAsString().trim();
                        if (!SNOWFLAKE.matcher(snowflake).matches()) {
                            throw new IllegalArgumentException("invalid-id");
                        }
                        ids.add(snowflake);
                    }
                    yield ids;
                }
                case ENUM -> {
                    String choice = value.getAsString().trim().toUpperCase(Locale.ROOT);
                    if (!setting.values().contains(choice)) {
                        throw new IllegalArgumentException("invalid-value");
                    }
                    yield choice;
                }
                case LOCALE -> {
                    String locale = plugin.getLangManager().normalize(value.getAsString());
                    if (locale == null) {
                        throw new IllegalArgumentException("unknown-locale");
                    }
                    yield locale;
                }
            };
            return apply(setting, parsed, actor, source);
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            String message = e.getMessage();
            return Result.fail(message != null && message.matches("[a-z-]+") ? message : "invalid-value");
        }
    }

    public Result apply(Setting setting, Object value, String actor, String source) {
        plugin.getPluginConfig().set(setting.path(), value);
        plugin.applySettings();
        String display = value instanceof List<?> list ? String.join(", ", list.stream().map(String::valueOf).toList()) : String.valueOf(value);
        plugin.getDlogger().info(plugin.getLangManager().console("setting-changed", "path", setting.path(), "value", display, "actor", actor));
        if (source.equals("gui")) {
            plugin.getBotBridge().log("gui", Map.of(
                    "actor", actor,
                    "action", "setting",
                    "target", setting.path(),
                    "value", display));
        }
        return Result.ok();
    }

    public JsonArray schema() {
        JsonArray array = new JsonArray();
        for (Setting setting : SETTINGS) {
            JsonObject json = new JsonObject();
            json.addProperty("id", setting.id());
            json.addProperty("path", setting.path());
            json.addProperty("type", setting.type().name());
            if (setting.type() == Type.INTEGER) {
                json.addProperty("min", setting.min());
                json.addProperty("max", setting.max());
            }
            if (setting.type() == Type.ENUM) {
                JsonArray values = new JsonArray();
                setting.values().forEach(values::add);
                json.add("values", values);
            }
            array.add(json);
        }
        return array;
    }
}
