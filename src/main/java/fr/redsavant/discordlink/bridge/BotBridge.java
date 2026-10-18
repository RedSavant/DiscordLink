package fr.redsavant.discordlink.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.config.SettingsService;
import fr.redsavant.discordlink.link.LinkManager;
import fr.redsavant.discordlink.storage.LinkedAccount;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class BotBridge {

    private final DiscordLinkPlugin plugin;
    private final Map<String, Function<JsonObject, JsonObject>> handlers = new HashMap<>();

    @Getter
    private volatile boolean ready = false;
    @Getter
    private volatile JsonObject botInfo = new JsonObject();

    public BotBridge(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
        handlers.put("link.verify", this::linkVerify);
        handlers.put("link.unlink", this::linkUnlink);
        handlers.put("link.info", this::linkInfo);
        handlers.put("players.list", this::playersList);
        handlers.put("players.get", this::playersGet);
        handlers.put("players.unlink", this::playersUnlink);
        handlers.put("players.resync", this::playersResync);
        handlers.put("players.bypass", this::playersBypass);
        handlers.put("players.lang", this::playersLang);
        handlers.put("discord.lang", this::discordLang);
        handlers.put("settings.set", this::settingsSet);
        handlers.put("settings.toggle", this::settingsToggle);
        handlers.put("server.status", data -> serverStatus());
        handlers.put("admin.reload", this::adminReload);
        handlers.put("admin.clear-codes", this::adminClearCodes);
        handlers.put("accounts.all", data -> accountsAll());
    }

    public CompletableFuture<JsonObject> handleRequest(String type, JsonObject data) {
        Function<JsonObject, JsonObject> handler = handlers.get(type);
        if (handler == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown request type: " + type));
        }
        return sync(() -> handler.apply(data));
    }

    public void handleEvent(String type, JsonObject data) {
        switch (type) {
            case "boot" -> sync(() -> {
                request("init", initPayload());
                return null;
            });
            case "ready" -> {
                ready = true;
                botInfo = data;
                plugin.getDlogger().info(plugin.getLangManager().console("bot-ready", "tag", string(data, "tag")));
                sync(() -> {
                    pushStatus();
                    return null;
                });
            }
            default -> plugin.getDlogger().debug("Unknown bot event: " + type);
        }
    }

    public void onDisconnected() {
        ready = false;
    }

    private <T> CompletableFuture<T> sync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(plugin, task);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } else {
            future.completeExceptionally(new IllegalStateException("Plugin disabled"));
        }
        return future;
    }

    private CompletableFuture<JsonObject> request(String type, JsonObject data) {
        if (plugin.getBotManager() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bot disabled"));
        }
        return plugin.getBotManager().request(type, data).exceptionally(error -> {
            plugin.getDlogger().debug("Bot request '" + type + "' failed: " + error.getMessage());
            return null;
        });
    }

    private JsonObject initPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("version", plugin.getPluginMeta().getVersion());
        payload.add("config", plugin.getPluginConfig().toJson());
        payload.add("languages", plugin.getLangManager().discordBundle());
        payload.add("emojis", plugin.getEmojiConfig().toJson());
        payload.addProperty("defaultLocale", plugin.getLangManager().defaultLocale());
        payload.add("schema", plugin.getSettingsService().schema());
        JsonObject locales = new JsonObject();
        plugin.getDataStore().discordLocales().forEach(locales::addProperty);
        payload.add("discordLocales", locales);
        return payload;
    }

    public void pushConfig() {
        request("config.update", initPayload());
    }

    public void syncRoles(LinkedAccount account, Boolean linked, Boolean online) {
        JsonObject data = new JsonObject();
        data.addProperty("discordId", account.discordId());
        data.addProperty("name", account.username());
        if (linked != null) {
            data.addProperty("linked", linked);
        }
        if (online != null) {
            data.addProperty("online", online);
        }
        request("roles.sync", data);
    }

    public void log(String event, Map<String, ?> values) {
        String configEvent = event.equals("gui") ? "gui-actions" : event;
        if (!plugin.getPluginConfig().logEvent(configEvent)) {
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty("event", event);
        JsonObject fields = new JsonObject();
        values.forEach((key, value) -> fields.addProperty(key, String.valueOf(value)));
        data.add("fields", fields);
        request("log", data);
    }

    public void pushStatus() {
        JsonObject data = new JsonObject();
        data.addProperty("online", Bukkit.getOnlinePlayers().size());
        data.addProperty("max", Bukkit.getMaxPlayers());
        data.addProperty("linked", plugin.getDataStore().linkedCount());
        data.addProperty("pending", plugin.getLinkManager().pendingCount());
        request("status", data).thenAccept(info -> {
            if (info != null) {
                botInfo = info;
            }
        });
    }

    private JsonObject linkVerify(JsonObject data) {
        LinkManager.LinkResult result = plugin.getLinkManager().link(string(data, "code"), string(data, "discordId"), string(data, "discordName"));
        JsonObject json = new JsonObject();
        json.addProperty("status", result.status().name().toLowerCase(Locale.ROOT));
        if (result.account() != null) {
            json.add("account", accountJson(result.account()));
        }
        return json;
    }

    private JsonObject linkUnlink(JsonObject data) {
        JsonObject json = new JsonObject();
        Optional<LinkedAccount> account = plugin.getDataStore().byDiscord(string(data, "discordId"));
        if (account.isEmpty()) {
            json.addProperty("unlinked", false);
            return json;
        }
        plugin.getLinkManager().unlink(account.get().uuid(), string(data, "actor"));
        json.addProperty("unlinked", true);
        json.add("account", accountJson(account.get()));
        return json;
    }

    private JsonObject linkInfo(JsonObject data) {
        JsonObject json = new JsonObject();
        Optional<LinkedAccount> account = plugin.getDataStore().byDiscord(string(data, "discordId"));
        json.addProperty("linked", account.isPresent());
        account.ifPresent(value -> json.add("account", accountJson(value)));
        return json;
    }

    private JsonObject playersList(JsonObject data) {
        int size = Math.clamp(integer(data, "size", 10), 1, 25);
        List<LinkedAccount> matches = plugin.getLinkManager().search(string(data, "filter"), string(data, "query"));
        int pages = Math.max(1, (matches.size() + size - 1) / size);
        int page = Math.clamp(integer(data, "page", 0), 0, pages - 1);
        JsonArray items = new JsonArray();
        matches.stream().skip((long) page * size).limit(size).map(this::accountJson).forEach(items::add);
        JsonObject json = new JsonObject();
        json.addProperty("total", matches.size());
        json.addProperty("page", page);
        json.addProperty("pages", pages);
        json.add("items", items);
        return json;
    }

    private JsonObject playersGet(JsonObject data) {
        JsonObject json = new JsonObject();
        Optional<LinkedAccount> account = account(data);
        json.addProperty("found", account.isPresent());
        account.ifPresent(value -> json.add("account", accountJson(value)));
        return json;
    }

    private JsonObject playersUnlink(JsonObject data) {
        JsonObject json = new JsonObject();
        Optional<LinkedAccount> account = account(data);
        account.ifPresent(value -> plugin.getLinkManager().unlink(value.uuid(), string(data, "actor")));
        json.addProperty("unlinked", account.isPresent());
        account.ifPresent(value -> json.add("account", accountJson(value)));
        return json;
    }

    private JsonObject playersResync(JsonObject data) {
        JsonObject json = new JsonObject();
        Optional<LinkedAccount> account = account(data);
        account.ifPresent(plugin.getLinkManager()::resync);
        json.addProperty("found", account.isPresent());
        return json;
    }

    private JsonObject playersBypass(JsonObject data) {
        JsonObject json = new JsonObject();
        UUID uuid = uuid(data);
        if (uuid == null) {
            json.addProperty("found", false);
            return json;
        }
        boolean value = data.has("value") ? data.get("value").getAsBoolean() : !plugin.getDataStore().bypass(uuid);
        plugin.getDataStore().setBypass(uuid, value);
        json.addProperty("found", true);
        json.addProperty("bypass", value);
        return json;
    }

    private JsonObject playersLang(JsonObject data) {
        JsonObject json = new JsonObject();
        UUID uuid = uuid(data);
        String locale = plugin.getLangManager().normalize(string(data, "locale"));
        if (uuid == null || locale == null) {
            json.addProperty("success", false);
            return json;
        }
        plugin.getDataStore().setPlayerLocale(uuid, locale);
        json.addProperty("success", true);
        json.addProperty("locale", locale);
        return json;
    }

    private JsonObject discordLang(JsonObject data) {
        JsonObject json = new JsonObject();
        String raw = string(data, "locale");
        String locale = raw.isEmpty() ? null : plugin.getLangManager().normalize(raw);
        if (!raw.isEmpty() && locale == null) {
            json.addProperty("success", false);
            return json;
        }
        plugin.getDataStore().setDiscordLocale(string(data, "discordId"), locale);
        json.addProperty("success", true);
        if (locale != null) {
            json.addProperty("locale", locale);
        }
        return json;
    }

    private JsonObject settingsSet(JsonObject data) {
        JsonElement value = data.has("value") ? data.get("value") : JsonNull.INSTANCE;
        return settingsResult(plugin.getSettingsService().set(string(data, "id"), value, string(data, "actor"), "panel"));
    }

    private JsonObject settingsToggle(JsonObject data) {
        return settingsResult(plugin.getSettingsService().toggle(string(data, "id"), string(data, "actor"), "panel"));
    }

    private JsonObject settingsResult(SettingsService.Result result) {
        JsonObject json = new JsonObject();
        json.addProperty("success", result.success());
        if (result.error() != null) {
            json.addProperty("error", result.error());
        }
        json.add("config", plugin.getPluginConfig().toJson());
        json.addProperty("defaultLocale", plugin.getLangManager().defaultLocale());
        return json;
    }

    private JsonObject serverStatus() {
        JsonObject json = new JsonObject();
        json.addProperty("online", Bukkit.getOnlinePlayers().size());
        json.addProperty("max", Bukkit.getMaxPlayers());
        json.addProperty("version", Bukkit.getMinecraftVersion());
        json.addProperty("software", Bukkit.getName());
        json.addProperty("tps", Math.min(20.0, Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0));
        json.addProperty("linked", plugin.getDataStore().linkedCount());
        json.addProperty("pending", plugin.getLinkManager().pendingCount());
        json.addProperty("startedAt", plugin.getStartedAt());
        json.addProperty("pluginVersion", plugin.getPluginMeta().getVersion());
        return json;
    }

    private JsonObject adminReload(JsonObject data) {
        plugin.reload();
        plugin.getDlogger().info(plugin.getLangManager().console("reloaded-by", "actor", string(data, "actor")));
        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        return json;
    }

    private JsonObject adminClearCodes(JsonObject data) {
        JsonObject json = new JsonObject();
        json.addProperty("removed", plugin.getLinkManager().clearExpired());
        return json;
    }

    private JsonObject accountsAll() {
        JsonArray array = new JsonArray();
        plugin.getDataStore().accounts().forEach(account -> array.add(accountJson(account)));
        JsonObject json = new JsonObject();
        json.add("accounts", array);
        return json;
    }

    private Optional<LinkedAccount> account(JsonObject data) {
        UUID uuid = uuid(data);
        return uuid == null ? Optional.empty() : plugin.getDataStore().byUuid(uuid);
    }

    private UUID uuid(JsonObject data) {
        try {
            return UUID.fromString(string(data, "uuid"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public JsonObject accountJson(LinkedAccount account) {
        Player player = Bukkit.getPlayer(account.uuid());
        String locale = plugin.getLangManager().locale(account.uuid());
        JsonObject json = new JsonObject();
        json.addProperty("uuid", account.uuid().toString());
        json.addProperty("name", account.username());
        json.addProperty("discordId", account.discordId());
        json.addProperty("discordName", account.displayDiscordName());
        json.addProperty("linkedAt", account.linkedAt());
        json.addProperty("online", player != null);
        json.addProperty("locale", locale);
        json.addProperty("bypass", plugin.getDataStore().bypass(account.uuid()));
        json.addProperty("listBypass", plugin.getLinkManager().hasListBypass(account.uuid(), account.username()));
        return json;
    }

    private static String string(JsonObject data, String key) {
        JsonElement element = data.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int integer(JsonObject data, String key, int fallback) {
        JsonElement element = data.get(key);
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }
}
