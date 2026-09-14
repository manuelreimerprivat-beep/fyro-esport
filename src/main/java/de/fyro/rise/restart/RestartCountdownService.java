package de.fyro.rise.restart;

import de.fyro.rise.FyroRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class RestartCountdownService {
    private final FyroRisePlugin plugin;
    private BukkitTask countdownTask;
    private BukkitTask finishTask;

    public RestartCountdownService(FyroRisePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean start(CommandSender initiator) {
        if (isRunning()) {
            initiator.sendMessage(Component.text("Der FYRO-Neustart läuft bereits.", NamedTextColor.RED));
            return false;
        }

        int configured = plugin.getConfig().getInt("restart.countdown-seconds", 10);
        int seconds = Math.max(1, Math.min(60, configured));
        String message = updateMessage();
        initiator.sendMessage(Component.text("FYRO-Neustart gestartet: " + seconds + " Sekunden.",
                NamedTextColor.GOLD));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text("[FYRO] ", NamedTextColor.GOLD)
                    .append(Component.text(message, NamedTextColor.LIGHT_PURPLE)));
        }

        countdownTask = new BukkitRunnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                showFrame(remaining, message);
                if (remaining <= 0) {
                    cancel();
                    countdownTask = null;
                    finishTask = Bukkit.getScheduler().runTaskLater(plugin,
                            () -> finishRestart(message), 20L);
                    return;
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    public boolean isRunning() {
        return countdownTask != null || finishTask != null;
    }

    public void shutdown() {
        if (countdownTask != null) countdownTask.cancel();
        if (finishTask != null) finishTask.cancel();
        countdownTask = null;
        finishTask = null;
    }

    private void showFrame(int remaining, String message) {
        String numberColor = remaining <= 3 ? "§c" : remaining <= 6 ? "§e" : "§6";
        float pitch = (float) Math.min(1.8, .65 + ((10 - Math.min(10, remaining)) * .1));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(numberColor + "§l" + remaining, "§d" + message, 0, 25, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,
                    SoundCategory.MASTER, .9f, pitch);
        }
    }

    private void finishRestart(String message) {
        finishTask = null;
        Bukkit.savePlayers();
        Bukkit.getWorlds().forEach(world -> world.save());

        Component kickMessage = Component.text("FYRO: RISE\n\n", NamedTextColor.GOLD)
                .append(Component.text(message, NamedTextColor.LIGHT_PURPLE));
        for (Player player : Bukkit.getOnlinePlayers()) player.kick(kickMessage);

        String command = plugin.getConfig().getString("restart.server-command", "restart").trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank() || !Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
            Bukkit.shutdown();
        }
    }

    private String updateMessage() {
        return plugin.getConfig().getString("restart.message",
                "Wir updaten kurz mal diese Magische Welt! Sie ist gleich wieder für euch da!");
    }
}
