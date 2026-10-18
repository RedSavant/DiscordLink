package fr.redsavant.discordlink.gui;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.entity.Player;

public class ConfirmMenu implements InventoryProvider {

    private final GuiManager gui;
    private final String action;
    private final Object[] placeholders;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private ConfirmMenu(GuiManager gui, String action, Object[] placeholders, Runnable onConfirm, Runnable onCancel) {
        this.gui = gui;
        this.action = action;
        this.placeholders = placeholders;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public static void open(DiscordLinkPlugin plugin, Player player, String action, Runnable onConfirm, Runnable onCancel, Object... placeholders) {
        GuiManager gui = plugin.getGuiManager();
        gui.build("discordlink-confirm", player, "confirm.title", 3, new ConfirmMenu(gui, action, placeholders, onConfirm, onCancel)).open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fill(gui.filler());
        contents.set(1, 2, ClickableItem.from(gui.item(player, "confirm.items.confirm"), data -> onConfirm.run()));
        contents.set(1, 4, ClickableItem.empty(gui.item(player, "confirm.actions." + action, placeholders)));
        contents.set(1, 6, ClickableItem.from(gui.item(player, "confirm.items.cancel"), data -> {
            gui.click(player);
            onCancel.run();
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}
