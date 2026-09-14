package de.fyro.rise.listener;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.dungeon.DungeonService;
import de.fyro.rise.display.DisplayService;
import de.fyro.rise.game.GameService;
import de.fyro.rise.gear.GearService;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.quest.QuestService;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.projectiles.ProjectileSource;

public final class GameListener implements Listener {
    private final FyroRisePlugin plugin;
    private final DataStore data;
    private final GameService game;
    private final GearService gear;
    private final QuestService quests;
    private final DungeonService dungeons;
    private final DisplayService display;

    public GameListener(FyroRisePlugin plugin, DataStore data, GameService game, GearService gear,
                        QuestService quests, DungeonService dungeons, DisplayService display) {
        this.plugin = plugin;
        this.data = data;
        this.game = game;
        this.gear = gear;
        this.quests = quests;
        this.dungeons = dungeons;
        this.display = display;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Profile profile = game.profile(player);
        display.refresh(player);
        if (plugin.getConfig().getBoolean("server.welcome-message", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Text.success(player, "Willkommen bei FYRO: RISE – FOR YOUR RISE ONLY");
                if (!profile.characterReady()) {
                    Text.send(player, "Das Buch des Aufstiegs öffnet sich gleich für deine Charakterwahl.");
                } else {
                    Text.send(player, "Level " + profile.level() + " • " + profile.riseClass().displayName()
                            + " • " + profile.faction().displayName());
                }
            }, 30L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        display.forget(event.getPlayer());
        data.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) ->
                display.renderChat(source.getUniqueId(), sourceDisplayName, message));
    }

    @EventHandler
    public void onVanillaExperience(PlayerExpChangeEvent event) {
        event.setAmount(0);
        Bukkit.getScheduler().runTask(plugin, () -> display.syncExperience(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> display.refresh(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player armored) {
            int defense = gear.armorDefense(armored.getInventory().getChestplate(), game.profile(armored));
            if (defense > 0) event.setDamage(Math.max(0.5, event.getDamage() - (defense * 0.3)));
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null) return;
        if (event.getEntity() instanceof Player victim && !game.canPvp(attacker, victim)) {
            event.setCancelled(true);
            Text.error(attacker, "PvP ist hier nicht erlaubt. Gegnerische Spieler müssen PvP aktiviert haben.");
            return;
        }
        double changed = game.attackDamage(attacker, event.getDamage());
        if (changed <= 0) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile) changed *= 1.05;
        event.setDamage(changed);
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (dungeons.isBoss(entity)) {
            dungeons.handleDeath(event);
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof Enemy)) return;
        int baseXp = plugin.getConfig().getInt("progression.base-kill-xp", 12);
        double baseCoins = plugin.getConfig().getDouble("progression.base-kill-coins", 3);
        int multiplier = switch (entity.getType()) {
            case ENDERMAN, BLAZE, WITHER_SKELETON -> 3;
            case CREEPER, PILLAGER, WITCH -> 2;
            default -> 1;
        };
        game.gainExperience(killer, baseXp * multiplier, entity.getType().name());
        game.addCoins(killer, baseCoins * multiplier, "Beute");
        quests.progress(killer, entity.getType().name());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        Profile profile = game.profile(victim);
        double percentage = plugin.getConfig().getDouble("progression.death-coin-loss-percent", 5);
        double loss = Math.floor(profile.coins() * Math.max(0, percentage) / 100.0);
        profile.coins(profile.coins() - loss);
        if (loss > 0) Text.error(victim, "Du hast beim Tod " + loss + " ✦ verloren.");
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            game.gainExperience(killer, 60, "PvP-Sieg");
            game.addCoins(killer, 25, "PvP-Sieg");
        }
        data.saveAll();
    }

    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        var item = player.getInventory().getItem(event.getNewSlot());
        if (gear.isRiseGear(item) && gear.weaponPower(item, game.profile(player)) < 0) {
            Text.error(player, "Diese Ausrüstung benötigt Level " + gear.requiredLevel(item) + " und die passende Klasse.");
        }
    }
}
