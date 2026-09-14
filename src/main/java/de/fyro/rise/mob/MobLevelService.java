package de.fyro.rise.mob;

import de.fyro.rise.FyroRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class MobLevelService implements Listener {
    private static final Map<String, String> GERMAN_NAMES = Map.ofEntries(
            Map.entry("BLAZE", "Lohe"),
            Map.entry("BOGGED", "Sumpfskelett"),
            Map.entry("BREEZE", "Böe"),
            Map.entry("CAVE_SPIDER", "Höhlenspinne"),
            Map.entry("CREAKING", "Knarrender"),
            Map.entry("CREEPER", "Creeper"),
            Map.entry("DROWNED", "Ertrunkener"),
            Map.entry("ELDER_GUARDIAN", "Großer Wächter"),
            Map.entry("ENDERMAN", "Enderman"),
            Map.entry("ENDERMITE", "Endermite"),
            Map.entry("ENDER_DRAGON", "Enderdrache"),
            Map.entry("EVOKER", "Magier"),
            Map.entry("GHAST", "Ghast"),
            Map.entry("GIANT", "Riese"),
            Map.entry("GUARDIAN", "Wächter"),
            Map.entry("HOGLIN", "Hoglin"),
            Map.entry("HUSK", "Wüstenzombie"),
            Map.entry("ILLUSIONER", "Illusionist"),
            Map.entry("MAGMA_CUBE", "Magmawürfel"),
            Map.entry("PHANTOM", "Phantom"),
            Map.entry("PIGLIN_BRUTE", "Piglin-Barbar"),
            Map.entry("PILLAGER", "Plünderer"),
            Map.entry("RAVAGER", "Verwüster"),
            Map.entry("SHULKER", "Shulker"),
            Map.entry("SILVERFISH", "Silberfischchen"),
            Map.entry("SKELETON", "Skelett"),
            Map.entry("SLIME", "Schleim"),
            Map.entry("SPIDER", "Spinne"),
            Map.entry("STRAY", "Eiswanderer"),
            Map.entry("VEX", "Plagegeist"),
            Map.entry("VINDICATOR", "Diener"),
            Map.entry("WARDEN", "Wärter"),
            Map.entry("WITCH", "Hexe"),
            Map.entry("WITHER", "Wither"),
            Map.entry("WITHER_SKELETON", "Witherskelett"),
            Map.entry("ZOGLIN", "Zoglin"),
            Map.entry("ZOMBIE", "Zombie"),
            Map.entry("ZOMBIE_VILLAGER", "Zombiedorfbewohner"),
            Map.entry("ZOMBIFIED_PIGLIN", "Zombifizierter Piglin")
    );

    private final FyroRisePlugin plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey nameKey;

    public MobLevelService(FyroRisePlugin plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "mob_level");
        this.nameKey = new NamespacedKey(plugin, "mob_display_name");
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getWorlds().forEach(world ->
                world.getLivingEntities().forEach(this::assignIfRequired)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> assignIfRequired(entity));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity raw : event.getEntities()) {
            if (raw instanceof LivingEntity living) assignIfRequired(living);
        }
    }

    public void assignIfRequired(LivingEntity entity) {
        if (!enabled() || !(entity instanceof Enemy) || entity.isDead() || !entity.isValid()) return;
        Integer stored = entity.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        if (stored != null) {
            if (!entity.getPersistentDataContainer().has(nameKey, PersistentDataType.STRING)) {
                entity.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING,
                        germanName(entity.getType()));
            }
            entity.setCustomNameVisible(true);
            if (entity.customName() == null) setName(entity, stored, germanName(entity.getType()));
            return;
        }
        assignLevel(entity, levelAt(entity.getLocation()), germanName(entity.getType()));
    }

    public void assignLevel(LivingEntity entity, int requestedLevel, String displayName) {
        if (!(entity instanceof Enemy) || entity.isDead()) return;
        Integer existing = entity.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        int level = clamp(requestedLevel);
        entity.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        entity.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, displayName);
        setName(entity, level, displayName);
        if (existing == null) scaleAttributes(entity, level);
    }

    public int levelOf(LivingEntity entity) {
        Integer stored = entity.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return stored == null ? 1 : clamp(stored);
    }

    public String displayName(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(nameKey,
                PersistentDataType.STRING, germanName(entity.getType()));
    }

    public int levelAt(Location location) {
        World world = location.getWorld();
        if (world == null) return minimumLevel();
        Location spawn = world.getSpawnLocation();
        double deltaX = location.getX() - spawn.getX();
        double deltaZ = location.getZ() - spawn.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        int dimensionFloor = switch (world.getEnvironment()) {
            case NETHER -> plugin.getConfig().getInt("mob-levels.nether-minimum-level", 20);
            case THE_END -> plugin.getConfig().getInt("mob-levels.end-minimum-level", 40);
            default -> minimumLevel();
        };
        int floor = Math.max(minimumLevel(), dimensionFloor);
        int base = MobLevelRules.distanceLevel(distance,
                plugin.getConfig().getInt("mob-levels.blocks-per-level", 175),
                floor, minimumLevel(), maximumLevel());
        int variance = Math.max(0, plugin.getConfig().getInt("mob-levels.random-variance", 2));
        int offset = variance == 0 ? 0 : ThreadLocalRandom.current().nextInt(-variance, variance + 1);
        return MobLevelRules.clampLevel(base + offset, floor, maximumLevel());
    }

    public boolean grantsExperience(int playerLevel, LivingEntity entity) {
        return MobLevelRules.grantsExperience(playerLevel, levelOf(entity));
    }

    public int experienceReward(int baseExperience, LivingEntity entity) {
        return MobLevelRules.experienceReward(baseExperience, levelOf(entity),
                plugin.getConfig().getInt("mob-levels.rewards.xp-per-level", 2));
    }

    public double coinReward(double baseCoins, LivingEntity entity) {
        return MobLevelRules.coinReward(baseCoins, levelOf(entity),
                plugin.getConfig().getDouble("mob-levels.rewards.coins-per-level", 0.15));
    }

    public int clamp(int level) {
        return MobLevelRules.clampLevel(level, minimumLevel(), maximumLevel());
    }

    private void scaleAttributes(LivingEntity entity, int level) {
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double multiplier = MobLevelRules.statMultiplier(level,
                    plugin.getConfig().getDouble("mob-levels.health-percent-per-level", 3.0));
            health.setBaseValue(Math.min(2048.0, Math.max(1.0, health.getBaseValue() * multiplier)));
            entity.setHealth(health.getValue());
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            double multiplier = MobLevelRules.statMultiplier(level,
                    plugin.getConfig().getDouble("mob-levels.damage-percent-per-level", 2.0));
            damage.setBaseValue(Math.min(100.0, Math.max(0.0, damage.getBaseValue() * multiplier)));
        }
    }

    private void setName(LivingEntity entity, int level, String displayName) {
        NamedTextColor levelColor = levelColor(level);
        entity.customName(Component.text("[Level " + level + "] ", levelColor, TextDecoration.BOLD)
                .append(Component.text(displayName, NamedTextColor.WHITE)));
        entity.setCustomNameVisible(true);
    }

    private NamedTextColor levelColor(int level) {
        if (level <= 10) return NamedTextColor.GREEN;
        if (level <= 20) return NamedTextColor.YELLOW;
        if (level <= 35) return NamedTextColor.GOLD;
        if (level <= 50) return NamedTextColor.RED;
        return NamedTextColor.DARK_PURPLE;
    }

    private String germanName(EntityType type) {
        String direct = GERMAN_NAMES.get(type.name());
        if (direct != null) return direct;
        String[] words = type.name().toLowerCase(Locale.GERMAN).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mob-levels.enabled", true);
    }

    private int minimumLevel() {
        return Math.max(1, plugin.getConfig().getInt("mob-levels.minimum-level", 1));
    }

    private int maximumLevel() {
        return Math.max(minimumLevel(), Math.min(60,
                plugin.getConfig().getInt("mob-levels.maximum-level", 60)));
    }
}
