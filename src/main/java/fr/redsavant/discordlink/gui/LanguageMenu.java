package fr.redsavant.discordlink.gui;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LanguageMenu implements InventoryProvider {

    private final GuiManager gui;
    private final UUID target;
    private final String targetName;
    private final Runnable back;

    private LanguageMenu(GuiManager gui, UUID target, String targetName, Runnable back) {
        this.gui = gui;
        this.target = target;
        this.targetName = targetName;
        this.back = back;
    }

    public static void open(DiscordLinkPlugin plugin, Player player, UUID target, String targetName, Runnable back) {
        GuiManager gui = plugin.getGuiManager();
        int count = plugin.getLangManager().locales().size();
        int rows = Math.clamp(2 + (count + 6) / 7, 3, 6);
        gui.build("discordlink-language", player, "language.title", rows, new LanguageMenu(gui, target, targetName, back),
                "player", targetName).open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        DiscordLinkPlugin plugin = gui.plugin();
        int rows = contents.inventory().getRows();
        contents.fill(gui.filler());
        String current = gui.lang().locale(target);
        List<String> locales = new ArrayList<>(gui.lang().locales());
        int maxSlots = (rows - 2) * 7;
        for (int i = 0; i < Math.min(locales.size(), maxSlots); i++) {
            String locale = locales.get(i);
            YamlConfiguration language = gui.lang().language(locale);
            boolean selected = locale.equalsIgnoreCase(current);
            Material material = gui.material(language == null ? null : language.getString("meta.icon"), Material.PAPER);
            ItemStack item = gui.create(material,
                    gui.lang().raw(gui.locale(player), "minecraft.gui.language.items.entry.name"),
                    gui.lang().rawList(gui.locale(player), selected ? "minecraft.gui.language.items.entry.lore-selected" : "minecraft.gui.language.items.entry.lore"),
                    "language", gui.lang().displayName(locale), "locale", locale);
            gui.glow(item, selected);
            contents.set(1 + i / 7, 1 + i % 7, ClickableItem.from(item, data -> select(player, locale)));
        }
        contents.set(rows - 1, 4, back != null
                ? ClickableItem.from(gui.item(player, "common.back"), data -> {
                    gui.click(player);
                    back.run();
                })
                : ClickableItem.from(gui.item(player, "common.close"), data -> {
                    gui.click(player);
                    player.closeInventory();
                }));
    }

    private void select(Player player, String locale) {
        DiscordLinkPlugin plugin = gui.plugin();
        plugin.getDataStore().setPlayerLocale(target, locale);
        gui.success(player);
        String language = gui.lang().displayName(locale);
        if (target.equals(player.getUniqueId())) {
            gui.lang().send(player, "minecraft.lang.changed", "language", language);
        } else {
            gui.lang().send(player, "minecraft.gui.messages.language-set", "player", targetName, "language", language);
            Player other = Bukkit.getPlayer(target);
            if (other != null) {
                gui.lang().send(other, "minecraft.lang.changed", "language", language);
            }
            gui.log(player, "language", targetName + " → " + locale);
        }
        if (back != null) {
            back.run();
        } else {
            open(plugin, player, target, targetName, null);
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}
