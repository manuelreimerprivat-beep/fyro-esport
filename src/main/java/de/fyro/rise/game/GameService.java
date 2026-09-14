package de.fyro.rise.game;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.display.DisplayService;
import de.fyro.rise.gear.GearService;
import de.fyro.rise.model.Model.Faction;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import de.fyro.rise.progression.LevelCurve;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.targeting.TargetingService;
import de.fyro.rise.util.GameRules;
import de.fyro.rise.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

import java.util.*;

public final class GameService {
    private final FyroRisePlugin plugin;
    private final DataStore data;
    private final GearService gear;
    private final DisplayService display;
    private final TargetingService targeting;
    private final Map<UUID, Map<Integer, Long>> cooldowns = new HashMap<>();

    public GameService(FyroRisePlugin plugin, DataStore data, GearService gear, DisplayService display,
                       TargetingService targeting) {
        this.plugin = plugin;
        this.data = data;
        this.gear = gear;
        this.display = display;
        this.targeting = targeting;
    }

    public Profile profile(Player player) {
        return data.profile(player.getUniqueId());
    }

    public boolean ensureCharacter(Player player) {
        if (profile(player).characterReady()) return true;
        Text.error(player, "Schließe zuerst deine Auswahl im Buch des Aufstiegs ab.");
        return false;
    }

    public boolean completeCharacter(Player player, Faction faction, RiseClass riseClass) {
        Profile profile = profile(player);
        if (profile.characterReady()) {
            Text.error(player, "Dein Charakter wurde bereits erstellt.");
            return false;
        }
        if (faction == Faction.UNGEWAEHLT || riseClass == RiseClass.NOVIZE) return false;
        profile.faction(faction);
        profile.riseClass(riseClass);
        gear.giveStarterKit(player, riseClass);
        display.refresh(player);
        data.saveAll();
        return true;
    }

    public boolean chooseFaction(Player player, Faction faction) {
        Profile profile = profile(player);
        if (profile.faction() != Faction.UNGEWAEHLT) {
            Text.error(player, "Deine Fraktion ist bereits festgelegt.");
            return false;
        }
        profile.faction(faction);
        data.saveAll();
        Text.success(player, "Du gehörst jetzt zu: " + faction.displayName() + ".");
        return true;
    }

    public boolean chooseClass(Player player, RiseClass riseClass) {
        Profile profile = profile(player);
        if (profile.riseClass() != RiseClass.NOVIZE) {
            Text.error(player, "Deine Klasse ist bereits festgelegt.");
            return false;
        }
        profile.riseClass(riseClass);
        gear.giveStarterKit(player, riseClass);
        display.refresh(player);
        data.saveAll();
        Text.success(player, "Deine Klasse ist jetzt " + riseClass.displayName() + ".");
        return true;
    }

    public void gainExperience(Player player, int amount, String reason) {
        Profile profile = profile(player);
        if (amount <= 0) return;
        LevelCurve.grant(profile, amount, newLevel -> {
            player.sendMessage(Component.text("LEVEL AUFSTIEG! Du bist jetzt Level " + newLevel + ".", NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        });
        display.syncExperience(player);
        player.sendActionBar(Component.text("+" + amount + " EP • " + reason, NamedTextColor.AQUA));
    }

    public void syncExperience(Player player) {
        display.syncExperience(player);
    }

    public void addCoins(Player player, double amount, String reason) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        Profile profile = profile(player);
        profile.coins(GameRules.roundedMoney(profile.coins() + amount));
        player.sendActionBar(Component.text("+" + GameRules.roundedMoney(amount) + " ✦ • " + reason, NamedTextColor.GOLD));
    }

    public boolean transfer(Player sender, Player receiver, double amount) {
        if (sender.equals(receiver)) return false;
        Profile from = profile(sender);
        if (!GameRules.validTransfer(amount, from.coins())) return false;
        Profile to = profile(receiver);
        from.coins(from.coins() - amount);
        to.coins(to.coins() + amount);
        data.saveAll();
        return true;
    }

    public boolean canPvp(Player attacker, Player victim) {
        if (!plugin.getConfig().getBoolean("pvp.enabled", true)) return false;
        Profile a = profile(attacker);
        Profile v = profile(victim);
        if (!a.characterReady() || !v.characterReady()) return false;
        if (!plugin.getConfig().getBoolean("pvp.same-faction-damage", false)
                && a.faction() != Faction.UNGEWAEHLT && a.faction() == v.faction()) return false;
        return !plugin.getConfig().getBoolean("pvp.require-both-opted-in", true)
                || (a.pvpEnabled() && v.pvpEnabled());
    }

    public double attackDamage(Player attacker, double original) {
        Profile profile = profile(attacker);
        int power = gear.weaponPower(attacker.getInventory().getItemInMainHand(), profile);
        if (power < 0) {
            Text.error(attacker, "Du erfüllst die Anforderungen dieser FYRO-Ausrüstung nicht.");
            return 0;
        }
        double classMultiplier = switch (profile.riseClass()) {
            case KRIEGER -> 1.12;
            case WALDLAEUFER -> 1.08;
            case MAGIER, PRIESTER, NOVIZE -> 1.0;
        };
        return Math.min(40.0, (original + (power * 0.35)) * classMultiplier);
    }

    public boolean castSkill(Player player, int slot) {
        if (!ensureCharacter(player) || slot < 1 || slot > 3) return false;
        Profile profile = profile(player);
        long remaining = remainingCooldown(player, slot);
        if (remaining > 0) {
            Text.error(player, "Fähigkeit noch " + remaining + " Sekunden auf Abklingzeit.");
            return false;
        }
        boolean success = switch (profile.riseClass()) {
            case KRIEGER -> castWarrior(player, slot, profile.level());
            case MAGIER -> castMage(player, slot, profile.level());
            case WALDLAEUFER -> castRanger(player, slot, profile.level());
            case PRIESTER -> castPriest(player, slot, profile.level());
            default -> false;
        };
        if (success) {
            cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .put(slot, System.currentTimeMillis() + (profile.riseClass().cooldown(slot) * 1000L));
            Text.success(player, profile.riseClass().skillName(slot) + " eingesetzt.");
        }
        return success;
    }

    private boolean castWarrior(Player player, int slot, int level) {
        if (slot == 1) {
            LivingEntity target = nearestHostile(player, 5);
            if (target == null) return noTarget(player);
            particleBeam(player.getEyeLocation(), target.getLocation().add(0, target.getHeight() * .55, 0),
                    Particle.SWEEP_ATTACK, Particle.CRIT, 16);
            expandingRings(target.getLocation().add(0, .15, 0), Particle.CRIT, Particle.SWEEP_ATTACK,
                    2.2, 3, 2L);
            player.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, .75f);
            target.damage(6 + level * 0.22, player);
            target.setVelocity(player.getLocation().getDirection().normalize().multiply(1.1).setY(0.35));
        } else if (slot == 2) {
            player.setAbsorptionAmount(Math.min(20.0, player.getAbsorptionAmount() + 6 + level * 0.15));
            expandingRings(player.getLocation().add(0, .15, 0), Particle.SWEEP_ATTACK, Particle.CRIT,
                    5.0, 5, 2L);
            spiralBurst(player.getLocation().add(0, .2, 0), Particle.HAPPY_VILLAGER, Particle.CRIT);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, .8f, 1.25f);
        } else {
            for (LivingEntity target : nearbyHostiles(player, 4)) target.damage(4 + level * 0.18, player);
            spiralBurst(player.getLocation().add(0, .15, 0), Particle.SWEEP_ATTACK, Particle.CRIT);
            expandingRings(player.getLocation().add(0, .1, 0), Particle.SWEEP_ATTACK, Particle.CRIT,
                    4.5, 4, 1L);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, .65f);
        }
        return true;
    }

    private boolean castMage(Player player, int slot, int level) {
        if (slot == 1) {
            LivingEntity target = nearestHostile(player, 12);
            if (target == null) return noTarget(player);
            Location impact = target.getLocation().add(0, target.getHeight() * .55, 0);
            particleBeam(player.getEyeLocation(), impact, Particle.FLAME, Particle.END_ROD, 28);
            spiralBurst(impact, Particle.FLAME, Particle.END_ROD);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.1f, 1.25f);
            player.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, .8f, 1.45f);
            target.setFireTicks(60);
            target.damage(7 + level * 0.28, player);
        } else if (slot == 2) {
            Location origin = player.getLocation().clone();
            Vector direction = player.getLocation().getDirection().normalize();
            Location destination = player.getLocation().clone().add(direction.multiply(5));
            if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) {
                Text.error(player, "Dort ist kein sicherer Platz.");
                return false;
            }
            player.teleport(destination);
            spiralBurst(origin.add(0, .2, 0), Particle.PORTAL, Particle.END_ROD);
            spiralBurst(destination.clone().add(0, .2, 0), Particle.PORTAL, Particle.END_ROD);
            player.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.3f);
        } else {
            for (LivingEntity target : nearbyHostiles(player, 5)) {
                target.damage(3 + level * 0.12, player);
                target.setVelocity(new Vector(0, 0, 0));
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 100));
            }
            expandingRings(player.getLocation().add(0, .15, 0), Particle.SNOWFLAKE, Particle.END_ROD,
                    5.5, 6, 2L);
            spiralBurst(player.getLocation().add(0, .2, 0), Particle.SNOWFLAKE, Particle.END_ROD);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, .65f);
        }
        return true;
    }

    private boolean castRanger(Player player, int slot, int level) {
        if (slot == 1) {
            LivingEntity target = nearestHostile(player, 18);
            if (target == null) return noTarget(player);
            Location impact = target.getLocation().add(0, target.getHeight() * .6, 0);
            particleBeam(player.getEyeLocation(), impact, Particle.CRIT, Particle.END_ROD, 34);
            expandingRings(impact, Particle.CRIT, Particle.SWEEP_ATTACK, 1.8, 3, 1L);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.2f, 1.55f);
            target.damage(8 + level * 0.25, player);
        } else if (slot == 2) {
            Vector back = player.getLocation().getDirection().normalize().multiply(-1.0).setY(0.45);
            player.setVelocity(back);
            spiralBurst(player.getLocation().add(0, .2, 0), Particle.CRIT, Particle.PORTAL);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, .9f, .75f);
        } else {
            List<LivingEntity> targets = nearbyHostiles(player, 9);
            if (targets.isEmpty()) return noTarget(player);
            targets.stream().limit(6).forEach(target -> {
                target.damage(5 + level * 0.18, player);
                fallingColumn(target.getLocation().add(0, target.getHeight() + 4, 0),
                        Particle.CRIT, Particle.END_ROD);
            });
            expandingRings(player.getLocation().add(0, .1, 0), Particle.CRIT, Particle.END_ROD,
                    5.0, 4, 2L);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.4f);
        }
        return true;
    }

    private boolean castPriest(Player player, int slot, int level) {
        if (slot == 1) {
            heal(player, 6 + level * 0.18);
            spiralBurst(player.getLocation().add(0, .2, 0), Particle.HEART, Particle.END_ROD);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .8f, 1.55f);
        } else if (slot == 2) {
            LivingEntity target = nearestHostile(player, 12);
            if (target == null) return noTarget(player);
            Location impact = target.getLocation().add(0, target.getHeight() * .55, 0);
            particleBeam(player.getEyeLocation(), impact, Particle.END_ROD, Particle.ENCHANT, 26);
            expandingRings(impact, Particle.END_ROD, Particle.ENCHANT, 2.4, 4, 1L);
            player.getWorld().playSound(impact, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
            target.damage(6 + level * 0.22, player);
        } else {
            Profile caster = profile(player);
            heal(player, 5 + level * 0.12);
            for (Entity entity : player.getNearbyEntities(6, 4, 6)) {
                if (entity instanceof Player ally && profile(ally).faction() == caster.faction()) {
                    heal(ally, 5 + level * 0.12);
                }
            }
            expandingRings(player.getLocation().add(0, .1, 0), Particle.END_ROD, Particle.HEART,
                    6.0, 6, 2L);
            spiralBurst(player.getLocation().add(0, .2, 0), Particle.ENCHANT, Particle.END_ROD);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.3f, .8f);
        }
        return true;
    }

    private void expandingRings(Location center, Particle primary, Particle secondary,
                                double maximumRadius, int steps, long intervalTicks) {
        Location fixed = center.clone();
        for (int step = 1; step <= steps; step++) {
            int frame = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double radius = maximumRadius * frame / steps;
                particleRing(fixed, primary, radius, 22);
                particleRing(fixed.clone().add(0, .25, 0), secondary, Math.max(.35, radius * .65), 14);
            }, Math.max(0, step - 1) * intervalTicks);
        }
    }

    private void particleRing(Location center, Particle particle, double radius, int points) {
        for (int point = 0; point < points; point++) {
            double angle = (Math.PI * 2 * point) / points;
            Location at = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0);
        }
    }

    private void particleBeam(Location start, Location end, Particle primary, Particle secondary, int points) {
        if (start.getWorld() == null || start.getWorld() != end.getWorld()) return;
        Vector path = end.toVector().subtract(start.toVector());
        for (int point = 0; point <= points; point++) {
            Location at = start.clone().add(path.clone().multiply(point / (double) points));
            start.getWorld().spawnParticle(point % 4 == 0 ? secondary : primary, at, 1, 0, 0, 0, 0);
        }
    }

    private void spiralBurst(Location center, Particle primary, Particle secondary) {
        Location fixed = center.clone();
        for (int frame = 0; frame < 6; frame++) {
            int step = frame;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int arm = 0; arm < 4; arm++) {
                    double angle = step * .85 + arm * (Math.PI / 2);
                    double radius = .35 + step * .18;
                    Location at = fixed.clone().add(Math.cos(angle) * radius, .2 + step * .28,
                            Math.sin(angle) * radius);
                    fixed.getWorld().spawnParticle(arm % 2 == 0 ? primary : secondary,
                            at, 2, .04, .04, .04, .01);
                }
            }, step * 2L);
        }
    }

    private void fallingColumn(Location top, Particle primary, Particle secondary) {
        Location fixed = top.clone();
        for (int frame = 0; frame < 6; frame++) {
            int step = frame;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location at = fixed.clone().add(0, -step * .8, 0);
                fixed.getWorld().spawnParticle(primary, at, 5, .18, .12, .18, .02);
                fixed.getWorld().spawnParticle(secondary, at, 2, .08, .08, .08, .01);
            }, step);
        }
    }

    private void heal(Player player, double amount) {
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
    }

    private boolean noTarget(Player player) {
        Text.error(player, "Kein gültiges Ziel in Reichweite.");
        return false;
    }

    private long remainingCooldown(Player player, int slot) {
        long until = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(slot, 0L);
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    private LivingEntity nearestHostile(Player player, double radius) {
        LivingEntity selected = targeting.target(player);
        if (selected != null) {
            return selected.getLocation().distanceSquared(player.getLocation()) <= radius * radius
                    ? selected : null;
        }
        return nearbyHostiles(player, radius).stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private List<LivingEntity> nearbyHostiles(Player player, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living instanceof ArmorStand || living.isDead()) continue;
            if (living instanceof Player target) {
                if (canPvp(player, target)) result.add(living);
            } else if (living instanceof Enemy) {
                result.add(living);
            }
        }
        return result;
    }
}
