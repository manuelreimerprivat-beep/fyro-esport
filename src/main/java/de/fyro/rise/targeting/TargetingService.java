package de.fyro.rise.targeting;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.mob.MobLevelService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class TargetingService implements Listener {
    private final FyroRisePlugin plugin;
    private final MobLevelService mobLevels;
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final BukkitTask updateTask;

    public TargetingService(FyroRisePlugin plugin, MobLevelService mobLevels) {
        this.plugin = plugin;
        this.mobLevels = mobLevels;
        long period = Math.max(2L, plugin.getConfig().getLong("targeting.update-ticks", 5L));
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTargets,
                period, period);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getRightClicked() instanceof LivingEntity living) select(event.getPlayer(), living);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) select(attacker, living);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    public void select(Player player, LivingEntity entity) {
        if (!plugin.getConfig().getBoolean("targeting.enabled", true)
                || !(entity instanceof Enemy) || entity.isDead() || !entity.isValid()) return;
        mobLevels.assignIfRequired(entity);
        targets.put(player.getUniqueId(), entity.getUniqueId());
        update(player, entity);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .65f, 1.45f);
    }

    public LivingEntity target(Player player) {
        UUID targetId = targets.get(player.getUniqueId());
        if (targetId == null) return null;
        Entity raw = Bukkit.getEntity(targetId);
        if (!(raw instanceof LivingEntity living) || !validTarget(player, living)) {
            clear(player.getUniqueId());
            return null;
        }
        return living;
    }

    public void shutdown() {
        updateTask.cancel();
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        targets.clear();
    }

    private void updateTargets() {
        Iterator<Map.Entry<UUID, UUID>> iterator = targets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Entity raw = Bukkit.getEntity(entry.getValue());
            if (player == null || !(raw instanceof LivingEntity living) || !validTarget(player, living)) {
                BossBar bar = bars.remove(entry.getKey());
                if (bar != null) bar.removeAll();
                iterator.remove();
                continue;
            }
            update(player, living);
        }
    }

    private void update(Player player, LivingEntity target) {
        AttributeInstance healthAttribute = target.getAttribute(Attribute.MAX_HEALTH);
        double maximum = healthAttribute == null ? Math.max(1.0, target.getHealth())
                : Math.max(1.0, healthAttribute.getValue());
        double health = Math.max(0.0, Math.min(maximum, target.getHealth()));
        double progress = health / maximum;

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored ->
                Bukkit.createBossBar("FYRO-Ziel", BarColor.RED, BarStyle.SEGMENTED_10));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        bar.setProgress(progress);
        bar.setColor(progress > .60 ? BarColor.GREEN : progress > .30 ? BarColor.YELLOW : BarColor.RED);
        bar.setTitle(icon(target) + "  [Level " + mobLevels.levelOf(target) + "] "
                + mobLevels.displayName(target) + "  •  " + rounded(health) + " / "
                + rounded(maximum) + " ❤");
    }

    private boolean validTarget(Player player, LivingEntity target) {
        if (!player.isOnline() || target.isDead() || !target.isValid()
                || player.getWorld() != target.getWorld()) return false;
        double maximumDistance = Math.max(8.0,
                plugin.getConfig().getDouble("targeting.maximum-distance", 48.0));
        return player.getLocation().distanceSquared(target.getLocation())
                <= maximumDistance * maximumDistance;
    }

    private void clear(UUID playerId) {
        targets.remove(playerId);
        BossBar bar = bars.remove(playerId);
        if (bar != null) bar.removeAll();
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private String icon(LivingEntity target) {
        String type = target.getType().name();
        if (type.contains("SKELETON") || type.contains("WITHER") || type.contains("ZOMBIE")) return "☠";
        if (type.contains("CREEPER")) return "✹";
        if (type.contains("SPIDER")) return "✦";
        if (type.contains("ENDERMAN") || type.contains("ENDERMITE")) return "◈";
        if (type.contains("BLAZE") || type.contains("MAGMA")) return "☀";
        if (type.contains("WITCH") || type.contains("EVOKER")) return "✧";
        return "◆";
    }

    private String rounded(double value) {
        if (Math.abs(value - Math.rint(value)) < .05) return Long.toString(Math.round(value));
        return String.format(java.util.Locale.GERMAN, "%.1f", value);
    }
}
