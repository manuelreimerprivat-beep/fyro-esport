package de.fyro.rise.selection;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.ability.AbilityBarService;
import de.fyro.rise.game.GameService;
import de.fyro.rise.model.Model.Faction;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import de.fyro.rise.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class CharacterSelectionService implements Listener {
    private enum Step { FACTION, CLASS, CONFIRM }

    private static final class SelectionState {
        private Faction faction = Faction.UNGEWAEHLT;
        private RiseClass riseClass = RiseClass.NOVIZE;
        private Step step = Step.FACTION;
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Step step;
        private Inventory inventory;

        private MenuHolder(Step step) {
            this.step = step;
        }

        @Override
        public Inventory getInventory() {
            return Objects.requireNonNull(inventory);
        }
    }

    private final FyroRisePlugin plugin;
    private final GameService game;
    private final AbilityBarService abilities;
    private final Map<UUID, SelectionState> sessions = new HashMap<>();

    public CharacterSelectionService(FyroRisePlugin plugin, GameService game, AbilityBarService abilities) {
        this.plugin = plugin;
        this.game = game;
        this.abilities = abilities;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public void startIfRequired(Player player) {
        if (!player.isOnline() || game.profile(player).characterReady()) return;
        SelectionState state = sessions.computeIfAbsent(player.getUniqueId(), ignored -> initialState(player));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, .8f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0),
                35, .8, 1.0, .8, .15);
        openCurrent(player, state);
    }

    private SelectionState initialState(Player player) {
        Profile profile = game.profile(player);
        SelectionState state = new SelectionState();
        state.faction = profile.faction();
        state.riseClass = profile.riseClass();
        if (state.faction == Faction.UNGEWAEHLT) state.step = Step.FACTION;
        else if (state.riseClass == RiseClass.NOVIZE) state.step = Step.CLASS;
        else state.step = Step.CONFIRM;
        return state;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        SelectionState state = sessions.get(player.getUniqueId());
        if (state == null) return;

        switch (holder.step) {
            case FACTION -> handleFaction(player, state, event.getRawSlot());
            case CLASS -> handleClass(player, state, event.getRawSlot());
            case CONFIRM -> handleConfirm(player, state, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        Player player = (Player) event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || game.profile(player).characterReady()) return;
            SelectionState state = sessions.get(player.getUniqueId());
            if (state == null) return;
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)) {
                Text.send(player, "Schließe deine Charakterwahl im Buch des Aufstiegs ab.");
                openCurrent(player, state);
            }
        }, 10L);
    }

    private void handleFaction(Player player, SelectionState state, int slot) {
        if (slot == 11) state.faction = Faction.AURORA;
        else if (slot == 15) state.faction = Faction.OBSIDIAN;
        else return;
        state.step = Step.CLASS;
        pageTurn(player);
        openClass(player, state);
    }

    private void handleClass(Player player, SelectionState state, int slot) {
        state.riseClass = switch (slot) {
            case 10 -> RiseClass.KRIEGER;
            case 12 -> RiseClass.MAGIER;
            case 14 -> RiseClass.WALDLAEUFER;
            case 16 -> RiseClass.PRIESTER;
            default -> state.riseClass;
        };
        if (slot == 40) {
            state.step = Step.FACTION;
            pageTurn(player);
            openFaction(player);
            return;
        }
        if (slot != 10 && slot != 12 && slot != 14 && slot != 16) return;
        state.step = Step.CONFIRM;
        pageTurn(player);
        openConfirm(player, state);
    }

    private void handleConfirm(Player player, SelectionState state, int slot) {
        if (slot == 18) {
            state.step = Step.CLASS;
            pageTurn(player);
            openClass(player, state);
            return;
        }
        if (slot != 22) return;
        if (state.faction == Faction.UNGEWAEHLT || state.riseClass == RiseClass.NOVIZE) {
            Text.error(player, "Orden und Klasse müssen ausgewählt sein.");
            state.step = Step.FACTION;
            openFaction(player);
            return;
        }
        if (!game.completeCharacter(player, state.faction, state.riseClass)) return;
        abilities.refresh(player, true);
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        Location effect = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ENCHANT, effect, 90, 1.0, 1.2, 1.0, .25);
        player.getWorld().spawnParticle(Particle.END_ROD, effect, 35, .8, 1.0, .8, .05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.15f);
        Text.success(player, "Dein Aufstieg beginnt: " + state.faction.displayName()
                + " • " + state.riseClass.displayName() + ".");
    }

    private void openCurrent(Player player, SelectionState state) {
        switch (state.step) {
            case FACTION -> openFaction(player);
            case CLASS -> openClass(player, state);
            case CONFIRM -> openConfirm(player, state);
        }
    }

    private void openFaction(Player player) {
        MenuHolder holder = new MenuHolder(Step.FACTION);
        Inventory menu = Bukkit.createInventory(holder, 27,
                Component.text("✦ Buch des Aufstiegs: Orden ✦", NamedTextColor.DARK_PURPLE));
        holder.inventory = menu;
        decorate(menu);
        menu.setItem(4, item(Material.ENCHANTED_BOOK, "Das Buch des Aufstiegs",
                "Wähle den Orden, dem du dienen möchtest.",
                "Die Wahl wird erst auf der letzten Seite gespeichert."));
        menu.setItem(11, item(Material.SUNFLOWER, "Orden der Morgenröte",
                "Licht, Ehre und Zusammenhalt.",
                "Klicke, um diesen Orden vorzumerken."));
        menu.setItem(15, item(Material.CRYING_OBSIDIAN, "Obsidianpakt",
                "Stärke, Freiheit und unbändiger Wille.",
                "Klicke, um diesen Orden vorzumerken."));
        player.openInventory(menu);
    }

    private void openClass(Player player, SelectionState state) {
        MenuHolder holder = new MenuHolder(Step.CLASS);
        Inventory menu = Bukkit.createInventory(holder, 45,
                Component.text("✦ Buch des Aufstiegs: Klasse ✦", NamedTextColor.DARK_PURPLE));
        holder.inventory = menu;
        decorate(menu);
        menu.setItem(4, item(Material.ENCHANTED_BOOK, "Dein Weg",
                "Gewählter Orden: " + state.faction.displayName(),
                "Wähle jetzt deine Klasse."));
        menu.setItem(10, item(Material.IRON_SWORD, "Krieger",
                "Robuster Nahkämpfer.",
                "Schildstoß • Kriegsruf • Wirbelsturm"));
        menu.setItem(12, item(Material.BLAZE_ROD, "Magier",
                "Meister arkaner und elementarer Macht.",
                "Feuerlanze • Arkanschritt • Frostnova"));
        menu.setItem(14, item(Material.BOW, "Waldläufer",
                "Schneller Kämpfer auf Entfernung.",
                "Präzisionsschuss • Ausweichsprung • Pfeilhagel"));
        menu.setItem(16, item(Material.GOLDEN_APPLE, "Priester",
                "Heiler und Kämpfer des Lichts.",
                "Lichtheilung • Heiliges Urteil • Zuflucht"));
        menu.setItem(40, item(Material.ARROW, "Eine Seite zurück",
                "Orden erneut auswählen."));
        player.openInventory(menu);
    }

    private void openConfirm(Player player, SelectionState state) {
        MenuHolder holder = new MenuHolder(Step.CONFIRM);
        Inventory menu = Bukkit.createInventory(holder, 27,
                Component.text("✦ Buch des Aufstiegs: Schwur ✦", NamedTextColor.DARK_PURPLE));
        holder.inventory = menu;
        decorate(menu);
        menu.setItem(4, item(Material.ENCHANTED_BOOK, "Dein Schicksal",
                "Prüfe deine Auswahl.",
                "Nach dem Abschluss beginnt dein Abenteuer."));
        menu.setItem(10, item(state.faction == Faction.AURORA ? Material.SUNFLOWER : Material.CRYING_OBSIDIAN,
                state.faction.displayName(), "Dein gewählter Orden"));
        Material classIcon = switch (state.riseClass) {
            case KRIEGER -> Material.IRON_SWORD;
            case MAGIER -> Material.BLAZE_ROD;
            case WALDLAEUFER -> Material.BOW;
            case PRIESTER -> Material.GOLDEN_APPLE;
            default -> Material.BOOK;
        };
        menu.setItem(12, item(classIcon, state.riseClass.displayName(), "Deine gewählte Klasse"));
        menu.setItem(18, item(Material.ARROW, "Eine Seite zurück", "Klasse erneut auswählen."));
        menu.setItem(22, item(Material.LIME_CONCRETE, "AUSWAHL ABSCHLIESSEN",
                "Orden: " + state.faction.displayName(),
                "Klasse: " + state.riseClass.displayName(),
                "Klicke, um deinen Schwur zu bestätigen."));
        player.openInventory(menu);
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = item(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack item(Material material, String name, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        List<Component> lore = Arrays.stream(loreLines)
                .<Component>map(line -> Component.text(line, NamedTextColor.GRAY))
                .toList();
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private void pageTurn(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }
}
