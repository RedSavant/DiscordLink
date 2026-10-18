package fr.redsavant.discordlink.gui;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.storage.LinkedAccount;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PlayersMenu implements InventoryProvider {

    private static final List<String> FILTERS = List.of("all", "online", "offline");

    private final GuiManager gui;
    private final String filter;
    private final String query;
    private final int rows;

    private PlayersMenu(GuiManager gui, String filter, String query, int rows) {
        this.gui = gui;
        this.filter = filter;
        this.query = query;
        this.rows = rows;
    }

    public static void open(DiscordLinkPlugin plugin, Player player, String filter, String query, int page) {
        GuiManager gui = plugin.getGuiManager();
        int rows = plugin.getPluginConfig().playersRows();
        int total = plugin.getLinkManager().search(filter, query).size();
        int perPage = (rows - 1) * 9;
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        int safePage = Math.clamp(page, 0, pages - 1);
        gui.build("discordlink-players", player, "players.title", rows, new PlayersMenu(gui, filter, query, rows),
                        "page", safePage + 1, "pages", pages, "count", total)
                .open(player, safePage);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        DiscordLinkPlugin plugin = gui.plugin();
        String locale = gui.locale(player);
        List<LinkedAccount> accounts = plugin.getLinkManager().search(filter, query);
        int nav = rows - 1;
        contents.fillRow(nav, gui.filler());

        Pagination pagination = contents.pagination();
        ClickableItem[] items = accounts.stream().map(account -> {
            boolean online = Bukkit.getPlayer(account.uuid()) != null;
            ItemStack item = gui.head(gui.item(player, "players.items.player",
                    "player", account.username(),
                    "discord", account.displayDiscordName(),
                    "date", gui.lang().date(locale, account.linkedAt()),
                    "language", gui.lang().displayName(gui.lang().locale(account.uuid())),
                    "status", gui.lang().rich(locale, online ? "minecraft.gui.states.online" : "minecraft.gui.states.offline")),
                    account.uuid(), account.username());
            return ClickableItem.from(item, data -> {
                gui.click(player);
                PlayerMenu.open(plugin, player, account.uuid(), filter, query, pagination.getPage());
            });
        }).toArray(ClickableItem[]::new);

        pagination.setItems(items);
        pagination.setItemsPerPage(nav * 9);
        pagination.addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0));

        if (items.length == 0) {
            contents.set(nav / 2, 4, ClickableItem.empty(gui.item(player, "players.items.empty")));
        }

        if (!pagination.isFirst()) {
            contents.set(nav, 0, ClickableItem.from(gui.item(player, "common.previous"), data -> {
                gui.click(player);
                open(plugin, player, filter, query, pagination.getPage() - 1);
            }));
        }
        if (!pagination.isLast()) {
            contents.set(nav, 8, ClickableItem.from(gui.item(player, "common.next"), data -> {
                gui.click(player);
                open(plugin, player, filter, query, pagination.getPage() + 1);
            }));
        }

        String next = FILTERS.get((FILTERS.indexOf(filter) + 1) % FILTERS.size());
        contents.set(nav, 2, ClickableItem.from(gui.item(player, "players.items.filter",
                "filter", gui.lang().rich(locale, "minecraft.gui.players.filters." + filter),
                "next", gui.lang().rich(locale, "minecraft.gui.players.filters." + next)), data -> {
            gui.click(player);
            open(plugin, player, next, query, 0);
        }));

        contents.set(nav, 3, ClickableItem.from(gui.item(player, "players.items.search",
                "query", query.isEmpty() ? gui.lang().rich(locale, "minecraft.gui.states.none") : query), data -> {
            gui.click(player);
            player.closeInventory();
            plugin.getChatInput().await(player,
                    input -> open(plugin, player, filter, input, 0),
                    () -> open(plugin, player, filter, query, 0));
        }));

        if (!query.isEmpty()) {
            contents.set(nav, 5, ClickableItem.from(gui.item(player, "players.items.clear-search"), data -> {
                gui.click(player);
                open(plugin, player, filter, "", 0);
            }));
        }

        contents.set(nav, 4, ClickableItem.from(gui.item(player, "common.back"), data -> {
            gui.click(player);
            MainMenu.open(plugin, player);
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}
