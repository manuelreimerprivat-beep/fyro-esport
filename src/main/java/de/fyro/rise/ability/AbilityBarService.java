package de.fyro.rise.ability;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.game.GameService;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import de.fyro.rise.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class AbilityBarService implements Listener {
    private static final int[] HOTBAR_SLOTS = {6, 7, 8};
    private static final String[] DESIRED_KEYS = {"Y", "X", "B"};
    private static final Material[] ICONS = {
            Material.AMETHYST_SHARD, Material.ECHO_SHARD, Material.NETHER_STAR
    };

    private final FyroRisePlugin plugin;
    private final GameService game;
    private final NamespacedKey abilitySlotKey;

    public AbilityBarService(FyroRisePlugin plugin, GameService game) {
        this.plugin = plugin;
        this.game = game;
        this.abilitySlotKey = new NamespacedKey(plugin, "ability_slot");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer(), false), 45L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer(), false), 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeldItem(PlayerItemHeldEvent event) {
        ItemStack selected = event.getPlayer().getInventory().getItem(event.getNewSlot());
        int ability = abilitySlot(selected);
        if (ability == 0) return;
        event.setCancelled(true);
        game.castSkill(event.getPlayer(), ability);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isAbilityRune(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isAbilityRune(event.getMainHandItem()) || isAbilityRune(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (isAbilityRune(event.getCurrentItem()) || isAbilityRune(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player && event.getHotbarButton() >= 0
                && isAbilityRune(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isAbilityRune(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isAbilityRune);
    }

    public void refresh(Player player, boolean announce) {
        if (!player.isOnline()) return;
        Profile profile = game.profile(player);
        clearRunes(player.getInventory());
        if (!profile.characterReady()) return;
        if (isReserved(player.getInventory().getHeldItemSlot())) {
            player.getInventory().setHeldItemSlot(0);
        }
        for (int skill = 1; skill <= 3; skill++) {
            int inventorySlot = HOTBAR_SLOTS[skill - 1];
            moveDisplacedItem(player, inventorySlot);
            player.getInventory().setItem(inventorySlot, createRune(profile.riseClass(), skill));
        }
        if (announce) {
            Text.success(player, "Deine Fähigkeiten liegen auf Hotbar 7, 8 und 9.");
            Text.send(player, "Für Y/X/B: Optionen → Steuerung → Hotbarplätze 7/8/9 einmal auf Y/X/B legen.");
        }
    }

    public boolean isAbilityRune(ItemStack item) {
        return abilitySlot(item) > 0;
    }

    private int abilitySlot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer()
                .get(abilitySlotKey, PersistentDataType.INTEGER);
        return value == null || value < 1 || value > 3 ? 0 : value;
    }

    private ItemStack createRune(RiseClass riseClass, int skill) {
        ItemStack item = new ItemStack(ICONS[skill - 1]);
        ItemMeta meta = item.getItemMeta();
        String desiredKey = DESIRED_KEYS[skill - 1];
        meta.displayName(Component.text("[" + desiredKey + "] " + riseClass.skillName(skill),
                NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Fähigkeit " + skill + " • Hotbar " + (skill + 6), NamedTextColor.AQUA),
                Component.text("Zum Wirken den Hotbarplatz anwählen.", NamedTextColor.GRAY),
                Component.text("Abklingzeit: " + riseClass.cooldown(skill) + " Sekunden", NamedTextColor.DARK_GRAY),
                Component.text("Gebundene FYRO-Rune", NamedTextColor.DARK_PURPLE)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(abilitySlotKey, PersistentDataType.INTEGER, skill);
        item.setItemMeta(meta);
        return item;
    }

    private void clearRunes(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isAbilityRune(inventory.getItem(slot))) inventory.setItem(slot, null);
        }
    }

    private void moveDisplacedItem(Player player, int reservedSlot) {
        PlayerInventory inventory = player.getInventory();
        ItemStack displaced = inventory.getItem(reservedSlot);
        if (displaced == null || displaced.getType().isAir() || isAbilityRune(displaced)) return;
        inventory.setItem(reservedSlot, null);
        for (int slot = 0; slot < 36; slot++) {
            if (isReserved(slot)) continue;
            ItemStack present = inventory.getItem(slot);
            if (present == null || present.getType().isAir()) {
                inventory.setItem(slot, displaced);
                return;
            }
        }
        player.getWorld().dropItemNaturally(player.getLocation(), displaced);
        Text.error(player, "Dein Inventar war voll; ein Gegenstand aus der Fähigkeitenleiste wurde neben dir abgelegt.");
    }

    private boolean isReserved(int slot) {
        for (int reserved : HOTBAR_SLOTS) if (slot == reserved) return true;
        return false;
    }
}
