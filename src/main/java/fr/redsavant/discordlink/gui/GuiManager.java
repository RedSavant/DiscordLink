package fr.redsavant.discordlink.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryProvider;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.lang.LangManager;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class GuiManager {

    private final DiscordLinkPlugin plugin;
    @Getter
    private final InventoryManager inventoryManager;

    public GuiManager(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
        this.inventoryManager = new InventoryManager(plugin);
        this.inventoryManager.init();
    }

    public DiscordLinkPlugin plugin() {
        return plugin;
    }

    public LangManager lang() {
        return plugin.getLangManager();
    }

    public String locale(Player player) {
        return lang().locale(player.getUniqueId());
    }

    public SmartInventory build(String id, Player viewer, String titleKey, int rows, InventoryProvider provider, Object... placeholders) {
        String title = LegacyComponentSerializer.legacySection().serialize(
                lang().parse(lang().raw(locale(viewer), "minecraft.gui." + titleKey), placeholders));
        return SmartInventory.builder()
                .manager(inventoryManager)
                .id(id)
                .title(title)
                .size(rows, 9)
                .provider(provider)
                .build();
    }

    public ItemStack item(Player viewer, String path, Object... placeholders) {
        ConfigurationSection section = lang().section(locale(viewer), "minecraft.gui." + path);
        if (section == null) {
            return create(Material.BARRIER, path, List.of(), placeholders);
        }
        ItemStack item = create(material(section.getString("material"), Material.PAPER),
                section.getString("name", ""), section.getStringList("lore"), placeholders);
        if (section.getBoolean("glow", false)) {
            glow(item, true);
        }
        return item;
    }

    public ItemStack create(Material material, String name, List<String> lore, Object... placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(lang().item(name, placeholders));
            if (!lore.isEmpty()) {
                meta.lore(lang().lore(lore, placeholders));
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack head(ItemStack item, UUID uuid, String name) {
        if (item.getType() != Material.PLAYER_HEAD || !(item.getItemMeta() instanceof SkullMeta meta)) {
            return item;
        }
        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack glow(ItemStack item, boolean glow) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack appendLore(ItemStack item, List<String> lines, Object... placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || lines.isEmpty()) {
            return item;
        }
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(lang().lore(lines, placeholders));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material == null || !material.isItem() ? fallback : material;
    }

    public ClickableItem filler() {
        if (!plugin.getPluginConfig().fillerEnabled()) {
            return ClickableItem.empty(new ItemStack(Material.AIR));
        }
        Material material = material(plugin.getPluginConfig().fillerMaterial(), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return ClickableItem.empty(item);
    }

    public LangManager.Rich state(Player viewer, boolean enabled) {
        return lang().rich(locale(viewer), enabled ? "minecraft.gui.states.enabled" : "minecraft.gui.states.disabled");
    }

    public boolean check(Player player, String permission) {
        if (player.hasPermission(permission) || player.hasPermission("discordlink.gui.*")) {
            return true;
        }
        error(player);
        lang().send(player, "minecraft.errors.no-permission");
        return false;
    }

    public void click(Player player) {
        play(player, "click");
    }

    public void success(Player player) {
        play(player, "success");
    }

    public void error(Player player) {
        play(player, "error");
    }

    private void play(Player player, String type) {
        if (!plugin.getPluginConfig().soundsEnabled()) {
            return;
        }
        String name = plugin.getPluginConfig().sound(type);
        if (name == null || name.isBlank()) {
            return;
        }
        Key key = soundKey(name);
        if (key != null) {
            player.playSound(Sound.sound(key, Sound.Source.MASTER, 1f, 1f));
        }
    }

    private Key soundKey(String name) {
        try {
            if (name.contains(".") || name.contains(":")) {
                return Key.key(name.toLowerCase(Locale.ROOT));
            }
            Object field = org.bukkit.Sound.class.getField(name.toUpperCase(Locale.ROOT)).get(null);
            if (field instanceof org.bukkit.Sound sound) {
                return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).getKeyOrThrow(sound);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    public void log(Player actor, String action, String target) {
        plugin.getBotBridge().log("gui", Map.of(
                "actor", actor.getName(),
                "action", action,
                "target", target));
    }
}
