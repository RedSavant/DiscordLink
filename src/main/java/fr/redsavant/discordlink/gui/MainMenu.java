package fr.redsavant.discordlink.gui;

import com.google.gson.JsonObject;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.entity.Player;

public class MainMenu implements InventoryProvider {

    private final GuiManager gui;

    private MainMenu(GuiManager gui) {
        this.gui = gui;
    }

    public static void open(DiscordLinkPlugin plugin, Player player) {
        GuiManager gui = plugin.getGuiManager();
        gui.build("discordlink-main", player, "main.title", 5, new MainMenu(gui)).open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fill(gui.filler());
        renderStatus(player, contents);

        contents.set(3, 2, ClickableItem.from(gui.item(player, "main.items.players"), data -> {
            if (gui.check(player, "discordlink.gui.players")) {
                gui.click(player);
                PlayersMenu.open(gui.plugin(), player, "all", "", 0);
            }
        }));
        contents.set(3, 4, ClickableItem.from(gui.item(player, "main.items.settings"), data -> {
            if (gui.check(player, "discordlink.gui.settings")) {
                gui.click(player);
                SettingsMenu.open(gui.plugin(), player);
            }
        }));
        contents.set(3, 6, ClickableItem.from(gui.item(player, "main.items.reload"), data -> {
            if (gui.check(player, "discordlink.gui.reload")) {
                gui.plugin().reload();
                gui.success(player);
                gui.lang().send(player, "minecraft.reload.success");
                gui.log(player, "reload", "-");
                open(gui.plugin(), player);
            }
        }));
        contents.set(4, 4, ClickableItem.from(gui.item(player, "common.close"), data -> {
            gui.click(player);
            player.closeInventory();
        }));
    }

    private void renderStatus(Player player, InventoryContents contents) {
        DiscordLinkPlugin plugin = gui.plugin();
        JsonObject info = plugin.getBotBridge().getBotInfo();
        boolean ready = plugin.getBotBridge().isReady();
        String unknown = gui.lang().raw(gui.locale(player), "minecraft.gui.states.unknown");
        contents.set(1, 1, ClickableItem.empty(gui.item(player, ready ? "main.items.bot-online" : "main.items.bot-offline",
                "tag", string(info, "tag", unknown),
                "ping", string(info, "ping", "?"),
                "guild", string(info, "guildName", unknown))));
        contents.set(1, 3, ClickableItem.empty(gui.item(player, "main.items.linked",
                "count", plugin.getDataStore().linkedCount(),
                "online", plugin.getServer().getOnlinePlayers().size())));
        contents.set(1, 5, ClickableItem.from(gui.item(player, "main.items.codes", "count", plugin.getLinkManager().pendingCount()), data -> {
            if (gui.check(player, "discordlink.gui")) {
                int removed = plugin.getLinkManager().clearExpired();
                gui.success(player);
                gui.lang().send(player, "minecraft.gui.messages.codes-cleared", "count", removed);
                gui.log(player, "clear-codes", String.valueOf(removed));
                renderStatus(player, contents);
            }
        }));
        contents.set(1, 7, ClickableItem.from(gui.item(player, "main.items.language", "language", gui.lang().displayName(gui.locale(player))), data -> {
            gui.click(player);
            LanguageMenu.open(plugin, player, player.getUniqueId(), player.getName(), () -> open(plugin, player));
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        int ticks = contents.property("ticks", 0) + 1;
        contents.setProperty("ticks", ticks);
        if (ticks % 40 == 0) {
            renderStatus(player, contents);
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json != null && json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }
}
