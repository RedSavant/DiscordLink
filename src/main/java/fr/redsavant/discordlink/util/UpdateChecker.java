package fr.redsavant.discordlink.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateChecker {

    private static final String PROJECT_ID = "2RbnHQFK";
    private static final String PROJECT_URL = "https://modrinth.com/plugin/discord-link";

    private final DiscordLinkPlugin plugin;
    private volatile String latest;

    public UpdateChecker(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version"))
                        .header("User-Agent", "RedSavant/DiscordLink/" + plugin.getPluginMeta().getVersion())
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }
                JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                if (versions.isEmpty()) {
                    return;
                }
                String remote = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                if (isNewer(remote, plugin.getPluginMeta().getVersion())) {
                    latest = remote;
                    plugin.getDlogger().warn(plugin.getLangManager().console("update-available",
                            "current", plugin.getPluginMeta().getVersion(), "latest", remote, "url", PROJECT_URL));
                }
            } catch (Exception e) {
                plugin.getDlogger().debug("Update check failed: " + e.getMessage());
            }
        });
    }

    public void notify(Player player) {
        if (latest != null) {
            plugin.getLangManager().send(player, "minecraft.update-available",
                    "current", plugin.getPluginMeta().getVersion(), "latest", latest, "url", PROJECT_URL);
        }
    }

    private boolean isNewer(String remote, String current) {
        String[] a = remote.replaceAll("[^0-9.]", "").split("\\.");
        String[] b = current.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }
}
