package de.fyro.rise.dungeon;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.game.GameService;
import de.fyro.rise.gear.GearService;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.mob.MobLevelService;
import de.fyro.rise.quest.QuestService;
import de.fyro.rise.social.SocialService;
import de.fyro.rise.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class DungeonService {
    private final FyroRisePlugin plugin;
    private final GameService game;
    private final GearService gear;
    private final SocialService social;
    private final QuestService quests;
    private final MobLevelService mobLevels;
    private final NamespacedKey bossKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public DungeonService(FyroRisePlugin plugin, GameService game, GearService gear,
                          SocialService social, QuestService quests, MobLevelService mobLevels) {
        this.plugin = plugin;
        this.game = game;
        this.gear = gear;
        this.social = social;
        this.quests = quests;
        this.mobLevels = mobLevels;
        this.bossKey = new NamespacedKey(plugin, "rise_boss");
        this.ownerKey = new NamespacedKey(plugin, "dungeon_owner");
        long period = Math.max(5, plugin.getConfig().getLong("performance.boss-update-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateBars, period, period);
    }

    public void startCrypt(Player leader) {
        Profile profile = game.profile(leader);
        int minimum = plugin.getConfig().getInt("dungeons.grave-crypt.minimum-level", 5);
        if (!game.ensureCharacter(leader) || profile.level() < minimum) {
            Text.error(leader, "Die Grabkrypta benötigt Level " + minimum + ".");
            return;
        }
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(leader.getUniqueId(), 0L);
        if (until > now) {
            Text.error(leader, "Dungeon-Abklingzeit: noch " + ((until - now + 59_999) / 60_000) + " Minuten.");
            return;
        }
        Set<Player> party = social.onlineParty(leader);
        for (Player member : party) {
            if (member.getWorld() != leader.getWorld() || member.getLocation().distanceSquared(leader.getLocation()) > 225) {
                Text.error(leader, "Alle Gruppenmitglieder müssen im Umkreis von 15 Blöcken stehen.");
                return;
            }
        }
        int minutes = plugin.getConfig().getInt("dungeons.grave-crypt.cooldown-minutes", 10);
        cooldowns.put(leader.getUniqueId(), now + minutes * 60_000L);
        Location origin = safeSpawn(leader.getLocation().clone().add(8, 0, 8));
        int dungeonLevel = mobLevels.clamp(profile.level());
        int minions = Math.min(6, plugin.getConfig().getInt("performance.maximum-dungeon-mobs-per-instance", 8));
        for (int index = 0; index < minions; index++) {
            Location at = origin.clone().add((index % 3) - 1, 0, (index / 3) + 2);
            LivingEntity mob = (LivingEntity) origin.getWorld().spawnEntity(at, index % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON);
            mobLevels.assignLevel(mob, dungeonLevel + (index % 3) - 1, "Kryptenwache");
        }
        spawnBoss(origin, leader);
        party.forEach(member -> Text.success(member, "Die Grabkrypta wurde geöffnet. Besiegt Grabfürst Morvath!"));
    }

    public LivingEntity spawnBoss(Location location, Player owner) {
        Location at = safeSpawn(location);
        WitherSkeleton boss = (WitherSkeleton) at.getWorld().spawnEntity(at, EntityType.WITHER_SKELETON);
        double maxHealth = plugin.getConfig().getDouble("dungeons.grave-crypt.boss-health", 240.0);
        Objects.requireNonNull(boss.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(maxHealth);
        boss.setHealth(maxHealth);
        boss.setRemoveWhenFarAway(false);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        boss.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        int bossLevel = mobLevels.clamp(game.profile(owner).level() + 3);
        mobLevels.assignLevel(boss, bossLevel, "Grabfürst Morvath");
        BossBar bar = Bukkit.createBossBar("[Level " + bossLevel + "] Grabfürst Morvath",
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        social.onlineParty(owner).forEach(bar::addPlayer);
        bars.put(boss.getUniqueId(), bar);
        boss.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 1f, .8f);
        return boss;
    }

    public boolean isBoss(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    public void handleDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isBoss(entity)) return;
        BossBar bar = bars.remove(entity.getUniqueId());
        if (bar != null) bar.removeAll();
        String ownerValue = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        Player owner = null;
        if (ownerValue != null) {
            try { owner = Bukkit.getPlayer(UUID.fromString(ownerValue)); } catch (IllegalArgumentException ignored) {}
        }
        if (owner == null) owner = entity.getKiller();
        if (owner == null) return;
        int xp = plugin.getConfig().getInt("dungeons.grave-crypt.reward-xp", 500);
        double coins = plugin.getConfig().getDouble("dungeons.grave-crypt.reward-coins", 275);
        for (Player member : social.onlineParty(owner)) {
            if (member.getWorld() == entity.getWorld() && member.getLocation().distanceSquared(entity.getLocation()) <= 2500) {
                game.gainExperience(member, xp, "Grabfürst Morvath");
                game.addCoins(member, coins, "Dungeonboss");
                quests.progress(member, "FYRO_BOSS");
            }
        }
        int tier = Math.max(1, Math.min(5, 1 + game.profile(owner).level() / 12));
        entity.getWorld().dropItemNaturally(entity.getLocation(), gear.createWeapon(game.profile(owner).riseClass(), tier));
        entity.getWorld().playSound(entity.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, .7f);
    }

    private void updateBars() {
        Iterator<Map.Entry<UUID, BossBar>> iterator = bars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BossBar> entry = iterator.next();
            Entity raw = Bukkit.getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity boss) || boss.isDead() || !boss.isValid()) {
                entry.getValue().removeAll();
                iterator.remove();
                continue;
            }
            double max = Objects.requireNonNull(boss.getAttribute(Attribute.MAX_HEALTH)).getValue();
            entry.getValue().setProgress(Math.max(0, Math.min(1, boss.getHealth() / max)));
        }
    }

    private Location safeSpawn(Location location) {
        Location result = location.clone();
        for (int i = 0; i < 8 && !result.getBlock().isPassable(); i++) result.add(0, 1, 0);
        return result;
    }

    public void shutdown() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
    }
}
