package de.fyro.rise.game;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.gear.GearService;
import de.fyro.rise.model.Model.Faction;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import de.fyro.rise.progression.LevelCurve;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.util.GameRules;
import de.fyro.rise.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final Map<UUID, Map<Integer, Long>> cooldowns = new HashMap<>();

    public GameService(FyroRisePlugin plugin, DataStore data, GearService gear) {
        this.plugin = plugin;
        this.data = data;
        this.gear = gear;
    }

    public Profile profile(Player player) {
        return data.profile(player.getUniqueId());
    }

    public boolean ensureCharacter(Player player) {
        if (profile(player).characterReady()) return true;
        Text.error(player, "Wähle zuerst Fraktion und Klasse: /rise choose ...");
        return false;
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
        player.sendActionBar(Component.text("+" + amount + " EP • " + reason, NamedTextColor.AQUA));
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
            target.damage(6 + level * 0.22, player);
            target.setVelocity(player.getLocation().getDirection().normalize().multiply(1.1).setY(0.35));
        } else if (slot == 2) {
            player.setAbsorptionAmount(Math.min(20.0, player.getAbsorptionAmount() + 6 + level * 0.15));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, .6, .8, .6);
        } else {
            for (LivingEntity target : nearbyHostiles(player, 4)) target.damage(4 + level * 0.18, player);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 20, 1.5, .4, 1.5);
        }
        return true;
    }

    private boolean castMage(Player player, int slot, int level) {
        if (slot == 1) {
            LivingEntity target = nearestHostile(player, 12);
            if (target == null) return noTarget(player);
            target.setFireTicks(60);
            target.damage(7 + level * 0.28, player);
            player.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 25, .4, .6, .4, .04);
        } else if (slot == 2) {
            Vector direction = player.getLocation().getDirection().normalize();
            Location destination = player.getLocation().clone().add(direction.multiply(5));
            if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) {
                Text.error(player, "Dort ist kein sicherer Platz.");
                return false;
            }
            player.teleport(destination);
            player.getWorld().spawnParticle(Particle.PORTAL, destination.add(0, 1, 0), 35, .4, .7, .4);
        } else {
            for (LivingEntity target : nearbyHostiles(player, 5)) {
                target.damage(3 + level * 0.12, player);
                target.setVelocity(new Vector(0, 0, 0));
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 100));
            }
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 45, 2, .8, 2, .03);
        }
        return true;
    }

    private boolean castRanger(Player player, int slot, int level) {
        if (slot == 1) {
            LivingEntity target = nearestHostile(player, 18);
            if (target == null) return noTarget(player);
            target.damage(8 + level * 0.25, player);
            player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 24, .3, .6, .3);
        } else if (slot == 2) {
            Vector back = player.getLocation().getDirection().normalize().multiply(-1.0).setY(0.45);
            player.setVelocity(back);
        } else {
            List<LivingEntity> targets = nearbyHostiles(player, 9);
            if (targets.isEmpty()) return noTarget(player);
            targets.stream().limit(6).forEach(target -> {
                target.damage(5 + level * 0.18, player);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 12, .2, .5, .2);
            });
        }
        return true;
    }

    private boolean castPriest(Player player, int slot, int level) {
        if (slot == 1) {
            heal(player, 6 + level * 0.18);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 12, .6, .8, .6);
        } else if (slot == 2) {
            LivingEntity target = nearestHostile(player, 12);
            if (target == null) return noTarget(player);
            target.damage(6 + level * 0.22, player);
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 22, .3, .7, .3, .02);
        } else {
            Profile caster = profile(player);
            heal(player, 5 + level * 0.12);
            for (Entity entity : player.getNearbyEntities(6, 4, 6)) {
                if (entity instanceof Player ally && profile(ally).faction() == caster.faction()) {
                    heal(ally, 5 + level * 0.12);
                }
            }
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, .2, 0), 45, 2.5, .2, 2.5, .01);
        }
        return true;
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
        return nearbyHostiles(player, radius).stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private List<LivingEntity> nearbyHostiles(Player player, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living instanceof ArmorStand || living.isDead()) continue;
            if (living instanceof Player target && !canPvp(player, target)) continue;
            result.add(living);
        }
        return result;
    }
}
