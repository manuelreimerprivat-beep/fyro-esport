package de.fyro.rise;

import de.fyro.rise.command.RiseCommand;
import de.fyro.rise.display.DisplayService;
import de.fyro.rise.dungeon.DungeonService;
import de.fyro.rise.game.GameService;
import de.fyro.rise.gear.GearService;
import de.fyro.rise.listener.GameListener;
import de.fyro.rise.intro.IntroCinematicService;
import de.fyro.rise.quest.QuestService;
import de.fyro.rise.selection.CharacterSelectionService;
import de.fyro.rise.social.SocialService;
import de.fyro.rise.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public final class FyroRisePlugin extends JavaPlugin {
    private DataStore data;
    private GearService gear;
    private DisplayService display;
    private GameService game;
    private QuestService quests;
    private SocialService social;
    private DungeonService dungeons;
    private CharacterSelectionService selection;
    private IntroCinematicService intro;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);

        data = new DataStore(this);
        gear = new GearService(this);
        display = new DisplayService(this, data);
        game = new GameService(this, data, gear, display);
        quests = new QuestService(this, data, game);
        social = new SocialService(this, data, game, display);
        dungeons = new DungeonService(this, game, gear, social, quests);
        selection = new CharacterSelectionService(this, game);
        intro = new IntroCinematicService(this, data, selection);

        RiseCommand commandHandler = new RiseCommand(this, data, game, gear, quests, social, dungeons);
        PluginCommand riseCommand = Objects.requireNonNull(getCommand("rise"), "Befehl rise fehlt in plugin.yml");
        riseCommand.setExecutor(commandHandler);
        riseCommand.setTabCompleter(commandHandler);

        Bukkit.getPluginManager().registerEvents(
                new GameListener(this, data, game, gear, quests, dungeons, display), this);
        Bukkit.getPluginManager().registerEvents(selection, this);
        Bukkit.getPluginManager().registerEvents(intro, this);

        long minutes = Math.max(1, getConfig().getLong("server.autosave-minutes", 5));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            data.saveAll();
            social.saveGuilds();
        }, minutes * 1200L, minutes * 1200L);

        getLogger().info("FYRO-RISE v" + getPluginMeta().getVersion() + " aktiviert – FOR YOUR RISE ONLY");
    }

    @Override
    public void onDisable() {
        if (intro != null) intro.shutdown();
        if (dungeons != null) dungeons.shutdown();
        if (data != null) data.saveAll();
        if (social != null) social.saveGuilds();
        if (display != null) display.shutdown();
        getLogger().info("FYRO-RISE sicher deaktiviert.");
    }

    public void reloadPlugin() {
        reloadConfig();
        quests.reload();
    }
}
