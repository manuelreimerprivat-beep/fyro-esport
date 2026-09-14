package de.fyro.rise.intro;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.selection.CharacterSelectionService;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.util.Text;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class IntroCinematicService implements Listener {
    private static final class ActiveScene {
        private final Player player;
        private final Location returnLocation;
        private final GameMode gameMode;
        private final boolean invulnerable;
        private final boolean allowFlight;
        private final boolean flying;
        private final ArmorStand camera;
        private BukkitTask task;
        private int tick;

        private ActiveScene(Player player, Location returnLocation, GameMode gameMode,
                            boolean invulnerable, boolean allowFlight, boolean flying,
                            ArmorStand camera) {
            this.player = player;
            this.returnLocation = returnLocation;
            this.gameMode = gameMode;
            this.invulnerable = invulnerable;
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.camera = camera;
        }
    }

    private final FyroRisePlugin plugin;
    private final DataStore data;
    private final CharacterSelectionService selection;
    private final Map<UUID, ActiveScene> activeScenes = new HashMap<>();

    public IntroCinematicService(FyroRisePlugin plugin, DataStore data,
                                 CharacterSelectionService selection) {
        this.plugin = plugin;
        this.data = data;
        this.selection = selection;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> startOrContinue(player), 35L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abort(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!activeScenes.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        Text.send(event.getPlayer(), "Die Reise beginnt – einen Augenblick noch.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (activeScenes.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    public void startOrContinue(Player player) {
        if (!player.isOnline()) return;
        Profile profile = data.profile(player.getUniqueId());
        boolean enabled = plugin.getConfig().getBoolean("intro.enabled", true);
        boolean everyJoin = plugin.getConfig().getBoolean("intro.play-on-every-join", false);
        if (!enabled || (profile.introSeen() && !everyJoin)) {
            selection.startIfRequired(player);
            return;
        }
        play(player);
    }

    private void play(Player player) {
        if (activeScenes.containsKey(player.getUniqueId())) return;
        player.closeInventory();

        Location origin = player.getLocation().clone();
        Location start = cameraLocation(origin, 0.0);
        ArmorStand camera = (ArmorStand) origin.getWorld().spawnEntity(start, EntityType.ARMOR_STAND);
        camera.setInvisible(true);
        camera.setMarker(true);
        camera.setGravity(false);
        camera.setInvulnerable(true);
        camera.setSilent(true);
        camera.setCollidable(false);

        ActiveScene scene = new ActiveScene(
                player,
                origin,
                player.getGameMode(),
                player.isInvulnerable(),
                player.getAllowFlight(),
                player.isFlying(),
                camera
        );
        activeScenes.put(player.getUniqueId(), scene);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) player.hidePlayer(plugin, other);
        }

        player.setInvulnerable(true);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(camera);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 28, 0, false, false, false));
        player.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.0f, 0.65f);

        int seconds = Math.max(6, Math.min(20,
                plugin.getConfig().getInt("intro.duration-seconds", 10)));
        int totalTicks = seconds * 20;

        scene.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(scene, totalTicks), 0L, 1L);
    }

    private void tick(ActiveScene scene, int totalTicks) {
        Player player = scene.player;
        if (!player.isOnline() || !scene.camera.isValid()) {
            abort(player);
            return;
        }

        double progress = Math.min(1.0, scene.tick / (double) totalTicks);
        Location cameraPosition = cameraLocation(scene.returnLocation, progress);
        scene.camera.teleport(cameraPosition);
        if (!scene.camera.equals(player.getSpectatorTarget())) {
            player.setSpectatorTarget(scene.camera);
        }

        if (scene.tick == 12) {
            player.sendTitle("§6§lHerzlich Willkommen", "", 8, 42, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.MASTER, 1.2f, 0.7f);
        }
        if (scene.tick == Math.round(totalTicks * 0.38f)) {
            player.sendTitle("§f§lin der magischen Welt", "", 8, 42, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.MASTER, 1.0f, 0.9f);
        }
        if (scene.tick == Math.round(totalTicks * 0.70f)) {
            player.sendTitle("§5§lvon §6§lFYRO", "", 8, 48, 12);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.MASTER, 1.1f, 0.65f);
        }

        if (scene.tick % 8 == 0) {
            Location center = scene.returnLocation.clone().add(0, 1.2, 0);
            center.getWorld().spawnParticle(Particle.ENCHANT, center, 14, 1.4, 1.0, 1.4, .22);
        }
        if (scene.tick >= totalTicks) finish(scene);
        scene.tick++;
    }

    private Location cameraLocation(Location origin, double progress) {
        double angle = Math.toRadians(215 + (progress * 285));
        double radius = 8.5 - (progress * 3.2);
        double height = 3.8 + Math.sin(progress * Math.PI) * 2.2;
        Location camera = origin.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius
        );
        Location target = origin.clone().add(0, 1.4, 0);
        camera.setDirection(target.toVector().subtract(camera.toVector()));
        return camera;
    }

    private void finish(ActiveScene scene) {
        if (activeScenes.remove(scene.player.getUniqueId()) == null) return;
        if (scene.task != null) scene.task.cancel();
        restore(scene);

        Player player = scene.player;
        Profile profile = data.profile(player.getUniqueId());
        profile.introSeen(true);
        data.saveAll();

        Location effect = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ENCHANT, effect, 75, 1.0, 1.2, 1.0, .24);
        player.getWorld().spawnParticle(Particle.END_ROD, effect, 24, .7, .9, .7, .04);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 1.2f, .8f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> selection.startIfRequired(player), 12L);
    }

    private void abort(Player player) {
        ActiveScene scene = activeScenes.remove(player.getUniqueId());
        if (scene == null) return;
        if (scene.task != null) scene.task.cancel();
        restore(scene);
    }

    private void restore(ActiveScene scene) {
        if (scene.camera.isValid()) scene.camera.remove();
        Player player = scene.player;
        if (!player.isOnline()) return;

        player.setSpectatorTarget(null);
        player.setGameMode(scene.gameMode);
        player.teleport(scene.returnLocation);
        player.setInvulnerable(scene.invulnerable);
        player.setAllowFlight(scene.allowFlight);
        if (scene.allowFlight) player.setFlying(scene.flying);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.clearTitle();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) player.showPlayer(plugin, other);
        }
    }

    public void shutdown() {
        for (ActiveScene scene : new ArrayList<>(activeScenes.values())) {
            if (scene.task != null) scene.task.cancel();
            restore(scene);
        }
        activeScenes.clear();
    }
}
