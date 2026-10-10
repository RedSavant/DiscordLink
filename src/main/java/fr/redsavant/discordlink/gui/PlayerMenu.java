package fr.redsavant.discordlink.gui;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.storage.LinkedAccount;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class PlayerMenu implements InventoryProvider {

    private final GuiManager gui;
    private final UUID target;
    private final String filter;
    private final String query;
    private final int page;

    private PlayerMenu(GuiManager gui, UUID target, String filter, String query, int page) {
        this.gui = gui;
        this.target = target;
        this.filter = filter;
        this.query = query;
        this.page = page;
    }

    public static void open(DiscordLinkPlugin plugin, Player player, UUID target, String filter, String query, int page) {
        Optional<LinkedAccount> account = plugin.getDataStore().byUuid(target);
        if (account.isEmpty()) {
            PlayersMenu.open(plugin, player, filter, query, page);
            return;
        }
        GuiManager gui = plugin.getGuiManager();
        gui.build("discordlink-player", player, "player.title", 4, new PlayerMenu(gui, target, filter, query, page),
                "player", account.get().username()).open(player);
    }

    private void back(Player player) {
        PlayersMenu.open(gui.plugin(), player, filter, query, page);
    }

    private void reopen(Player player) {
        open(gui.plugin(), player, target, filter, query, page);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        DiscordLinkPlugin plugin = gui.plugin();
        Optional<LinkedAccount> found = plugin.getDataStore().byUuid(target);
        if (found.isEmpty()) {
            back(player);
            return;
        }
        LinkedAccount account = found.get();
        String locale = gui.locale(player);
        boolean online = Bukkit.getPlayer(target) != null;
        boolean bypass = plugin.getDataStore().bypass(target);

        contents.fill(gui.filler());
        contents.set(1, 1, ClickableItem.empty(gui.head(gui.item(player, "player.items.info",
                "player", account.username(),
                "uuid", account.uuid(),
                "discord", account.displayDiscordName(),
                "discord_id", account.discordId(),
                "date", gui.lang().date(locale, account.linkedAt()),
                "language", gui.lang().displayName(gui.lang().locale(target)),
                "bypass", gui.state(player, bypass),
                "status", gui.lang().raw(locale, online ? "minecraft.gui.states.online" : "minecraft.gui.states.offline")),
                account.uuid(), account.username())));

        contents.set(1, 3, ClickableItem.from(gui.item(player, "player.items.unlink"), data -> {
            if (!gui.check(player, "discordlink.gui.players.unlink")) {
                return;
            }
            Runnable action = () -> {
                plugin.getLinkManager().unlink(target, player.getName());
                gui.success(player);
                gui.lang().send(player, "minecraft.forceunlink.success", "player", account.username());
                gui.log(player, "unlink", account.username());
                back(player);
            };
            if (plugin.getPluginConfig().guiConfirm()) {
                gui.click(player);
                ConfirmMenu.open(plugin, player, "unlink", action, () -> reopen(player), "player", account.username());
            } else {
                action.run();
            }
        }));

        contents.set(1, 4, ClickableItem.from(gui.item(player, "player.items.resync"), data -> {
            if (!gui.check(player, "discordlink.gui.players.resync")) {
                return;
            }
            plugin.getLinkManager().resync(account);
            gui.success(player);
            gui.lang().send(player, "minecraft.gui.messages.resynced", "player", account.username());
            gui.log(player, "resync", account.username());
        }));

        contents.set(1, 5, ClickableItem.from(gui.item(player, "player.items.bypass", "state", gui.state(player, bypass)), data -> {
            if (!gui.check(player, "discordlink.gui.players.bypass")) {
                return;
            }
            plugin.getDataStore().setBypass(target, !bypass);
            gui.success(player);
            gui.lang().send(player, "minecraft.gui.messages.bypass", "player", account.username(), "state", gui.state(player, !bypass));
            gui.log(player, bypass ? "bypass-off" : "bypass-on", account.username());
            reopen(player);
        }));

        contents.set(1, 6, ClickableItem.from(gui.item(player, "player.items.language",
                "language", gui.lang().displayName(gui.lang().locale(target))), data -> {
            if (!gui.check(player, "discordlink.gui.players.lang")) {
                return;
            }
            gui.click(player);
            LanguageMenu.open(plugin, player, target, account.username(), () -> reopen(player));
        }));

        contents.set(3, 4, ClickableItem.from(gui.item(player, "common.back"), data -> {
            gui.click(player);
            back(player);
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}
