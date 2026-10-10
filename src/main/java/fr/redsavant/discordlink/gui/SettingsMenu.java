package fr.redsavant.discordlink.gui;

import com.google.gson.JsonPrimitive;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.config.SettingsService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SettingsMenu implements InventoryProvider {

    private final GuiManager gui;

    private SettingsMenu(GuiManager gui) {
        this.gui = gui;
    }

    public static void open(DiscordLinkPlugin plugin, Player player) {
        GuiManager gui = plugin.getGuiManager();
        gui.build("discordlink-settings", player, "settings.title", 6, new SettingsMenu(gui)).open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        DiscordLinkPlugin plugin = gui.plugin();
        SettingsService settings = plugin.getSettingsService();
        contents.fill(gui.filler());

        List<SettingsService.Setting> toggles = settings.ofType(SettingsService.Type.BOOLEAN);
        for (int i = 0; i < Math.min(toggles.size(), 24); i++) {
            SettingsService.Setting setting = toggles.get(i);
            boolean enabled = settings.bool(setting);
            ItemStack item = entry(player, setting, "minecraft.gui.settings.toggle-lore", "state", gui.state(player, enabled));
            gui.glow(item, enabled);
            int row = i < 21 ? 1 + i / 7 : 4;
            int column = i < 21 ? 1 + i % 7 : 5 + (i - 21);
            contents.set(row, column, ClickableItem.from(item, data -> {
                settings.toggle(setting.id(), player.getName(), "gui");
                gui.success(player);
                open(plugin, player);
            }));
        }

        number(player, contents, 4, 1, settings.find("linking.code.length").orElseThrow());
        number(player, contents, 4, 2, settings.find("linking.code.expiration").orElseThrow());

        SettingsService.Setting localeSetting = settings.find("language.default").orElseThrow();
        String current = plugin.getLangManager().defaultLocale();
        contents.set(4, 3, ClickableItem.from(entry(player, localeSetting, "minecraft.gui.settings.locale-lore",
                "value", plugin.getLangManager().displayName(current)), data -> {
            List<String> locales = new ArrayList<>(plugin.getLangManager().locales());
            String next = locales.get((locales.indexOf(current) + 1) % locales.size());
            settings.set(localeSetting.id(), new JsonPrimitive(next), player.getName(), "gui");
            gui.success(player);
            open(plugin, player);
        }));

        SettingsService.Setting modeSetting = settings.find("linking.mode").orElseThrow();
        String mode = plugin.getPluginConfig().linkMode();
        String nextMode = modeSetting.values().get((modeSetting.values().indexOf(mode) + 1) % modeSetting.values().size());
        String locale = gui.locale(player);
        contents.set(4, 4, ClickableItem.from(entry(player, modeSetting, "minecraft.gui.settings.mode-lore",
                "value", gui.lang().raw(locale, "minecraft.gui.settings.modes." + mode),
                "next", gui.lang().raw(locale, "minecraft.gui.settings.modes." + nextMode)), data -> {
            settings.set(modeSetting.id(), new JsonPrimitive(nextMode), player.getName(), "gui");
            gui.success(player);
            open(plugin, player);
        }));

        contents.set(5, 4, ClickableItem.from(gui.item(player, "common.back"), data -> {
            gui.click(player);
            MainMenu.open(plugin, player);
        }));
    }

    private void number(Player player, InventoryContents contents, int row, int column, SettingsService.Setting setting) {
        DiscordLinkPlugin plugin = gui.plugin();
        SettingsService settings = plugin.getSettingsService();
        int value = settings.integer(setting);
        contents.set(row, column, ClickableItem.from(entry(player, setting, "minecraft.gui.settings.number-lore",
                "value", value, "step", setting.step(), "min", setting.min(), "max", setting.max()), data -> {
            ClickType click = data.getEvent() instanceof InventoryClickEvent clickEvent ? clickEvent.getClick() : ClickType.LEFT;
            int delta = click.isRightClick() ? -setting.step() : setting.step();
            if (click.isShiftClick()) {
                delta *= 10;
            }
            int updated = Math.clamp((long) value + delta, setting.min(), setting.max());
            if (updated == value) {
                gui.error(player);
                return;
            }
            settings.set(setting.id(), new JsonPrimitive(updated), player.getName(), "gui");
            gui.click(player);
            open(plugin, player);
        }));
    }

    private ItemStack entry(Player player, SettingsService.Setting setting, String loreKey, Object... placeholders) {
        String locale = gui.locale(player);
        ConfigurationSection section = gui.lang().section(locale, "minecraft.gui.settings.entries." + setting.id());
        Material material = gui.material(section == null ? null : section.getString("material"), Material.COMPARATOR);
        String name = section == null ? setting.path() : section.getString("name", setting.path());
        List<String> lore = new ArrayList<>(section == null ? List.of() : section.getStringList("description"));
        lore.addAll(gui.lang().rawList(locale, loreKey));
        return gui.create(material, name, lore, placeholders);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
    }
}
