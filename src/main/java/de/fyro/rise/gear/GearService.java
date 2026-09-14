package de.fyro.rise.gear;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import de.fyro.rise.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;

import java.util.List;

public final class GearService {
    private final NamespacedKey requiredLevelKey;
    private final NamespacedKey powerKey;
    private final NamespacedKey classKey;
    private final NamespacedKey gearKey;

    public GearService(FyroRisePlugin plugin) {
        requiredLevelKey = new NamespacedKey(plugin, "required_level");
        powerKey = new NamespacedKey(plugin, "power");
        classKey = new NamespacedKey(plugin, "class");
        gearKey = new NamespacedKey(plugin, "gear");
    }

    public void giveStarterKit(Player player, RiseClass riseClass) {
        player.getInventory().addItem(createWeapon(riseClass, 1));
        player.getInventory().addItem(createArmor(riseClass, 1));
        Text.success(player, "Deine Klassenausrüstung wurde deinem Inventar hinzugefügt.");
    }

    public void giveTier(Player player, RiseClass riseClass, int tier) {
        int safeTier = Math.max(1, Math.min(5, tier));
        player.getInventory().addItem(createWeapon(riseClass, safeTier));
        player.getInventory().addItem(createArmor(riseClass, safeTier));
    }

    public ItemStack createWeapon(RiseClass riseClass, int tier) {
        int safeTier = Math.max(1, Math.min(5, tier));
        Material material = switch (riseClass) {
            case KRIEGER -> safeTier >= 4 ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
            case MAGIER -> Material.BLAZE_ROD;
            case WALDLAEUFER -> safeTier >= 3 ? Material.CROSSBOW : Material.BOW;
            case PRIESTER -> Material.GOLDEN_HOE;
            default -> Material.WOODEN_SWORD;
        };
        String title = switch (riseClass) {
            case KRIEGER -> "Klinge der Morgenwacht";
            case MAGIER -> "Arkanfokus";
            case WALDLAEUFER -> "Bogen des Windpfads";
            case PRIESTER -> "Stab des ersten Lichts";
            default -> "Übungswaffe";
        };
        int required = 1 + ((safeTier - 1) * 12);
        int power = 2 + (safeTier * 2);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title + " [Rang " + safeTier + "]", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Klasse: " + riseClass.displayName(), NamedTextColor.GRAY),
                Component.text("Benötigtes Level: " + required, NamedTextColor.GRAY),
                Component.text("Kampfkraft: +" + power, NamedTextColor.GREEN),
                Component.text("FYRO: RISE Ausrüstung", NamedTextColor.DARK_PURPLE)
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(requiredLevelKey, PersistentDataType.INTEGER, required);
        meta.getPersistentDataContainer().set(powerKey, PersistentDataType.INTEGER, power);
        meta.getPersistentDataContainer().set(classKey, PersistentDataType.STRING, riseClass.name());
        meta.getPersistentDataContainer().set(gearKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createArmor(RiseClass riseClass, int tier) {
        int safeTier = Math.max(1, Math.min(5, tier));
        Material material = switch (riseClass) {
            case KRIEGER -> safeTier >= 4 ? Material.DIAMOND_CHESTPLATE : Material.IRON_CHESTPLATE;
            case MAGIER -> Material.LEATHER_CHESTPLATE;
            case WALDLAEUFER -> Material.CHAINMAIL_CHESTPLATE;
            case PRIESTER -> Material.GOLDEN_CHESTPLATE;
            default -> Material.LEATHER_CHESTPLATE;
        };
        int required = 1 + ((safeTier - 1) * 12);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Gewand des " + riseClass.displayName() + "s [Rang " + safeTier + "]", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Benötigtes Level: " + required, NamedTextColor.GRAY),
                Component.text("Lebenskraft: +" + (safeTier * 2), NamedTextColor.GREEN),
                Component.text("FYRO: RISE Ausrüstung", NamedTextColor.DARK_PURPLE)
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(requiredLevelKey, PersistentDataType.INTEGER, required);
        meta.getPersistentDataContainer().set(classKey, PersistentDataType.STRING, riseClass.name());
        meta.getPersistentDataContainer().set(gearKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public int weaponPower(ItemStack item, Profile profile) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(gearKey, PersistentDataType.BYTE)) return 0;
        int required = meta.getPersistentDataContainer().getOrDefault(requiredLevelKey, PersistentDataType.INTEGER, 1);
        String requiredClass = meta.getPersistentDataContainer().getOrDefault(classKey, PersistentDataType.STRING, "");
        if (profile.level() < required || !profile.riseClass().name().equals(requiredClass)) return -1;
        return meta.getPersistentDataContainer().getOrDefault(powerKey, PersistentDataType.INTEGER, 0);
    }

    public boolean isRiseGear(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(gearKey, PersistentDataType.BYTE);
    }

    public int requiredLevel(ItemStack item) {
        if (!isRiseGear(item)) return 1;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(requiredLevelKey, PersistentDataType.INTEGER, 1);
    }
}
