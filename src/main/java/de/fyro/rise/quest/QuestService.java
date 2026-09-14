package de.fyro.rise.quest;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.game.GameService;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.QuestDefinition;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public final class QuestService {
    private final FyroRisePlugin plugin;
    private final DataStore data;
    private final GameService game;
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();

    public QuestService(FyroRisePlugin plugin, DataStore data, GameService game) {
        this.plugin = plugin;
        this.data = data;
        this.game = game;
        reload();
    }

    public void reload() {
        quests.clear();
        File file = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String root = "quests." + id;
            QuestDefinition quest = new QuestDefinition(
                    id.toLowerCase(Locale.ROOT),
                    yaml.getString(root + ".name", id),
                    yaml.getString(root + ".description", ""),
                    yaml.getString(root + ".target", "ZOMBIE").toUpperCase(Locale.ROOT),
                    Math.max(1, yaml.getInt(root + ".amount", 1)),
                    Math.max(1, yaml.getInt(root + ".minimum-level", 1)),
                    Math.max(0, yaml.getInt(root + ".reward-xp", 0)),
                    Math.max(0, yaml.getDouble(root + ".reward-coins", 0))
            );
            quests.put(quest.id(), quest);
        }
        plugin.getLogger().info(quests.size() + " Quests geladen.");
    }

    public Collection<QuestDefinition> all() {
        return Collections.unmodifiableCollection(quests.values());
    }

    public void list(Player player) {
        Profile profile = data.profile(player.getUniqueId());
        Text.send(player, "Verfügbare Quests:");
        for (QuestDefinition quest : quests.values()) {
            String status;
            if (profile.completedQuests().contains(quest.id())) status = "ABGESCHLOSSEN";
            else if (profile.activeQuest().equals(quest.id())) status = profile.questProgress() + "/" + quest.amount();
            else if (profile.level() < quest.minimumLevel()) status = "ab Level " + quest.minimumLevel();
            else status = "bereit";
            player.sendMessage(" §8- §6" + quest.id() + " §7| " + quest.name() + " §8(" + status + ") §7– " + quest.description());
        }
    }

    public boolean accept(Player player, String id) {
        Profile profile = data.profile(player.getUniqueId());
        QuestDefinition quest = quests.get(id.toLowerCase(Locale.ROOT));
        if (quest == null) {
            Text.error(player, "Diese Quest existiert nicht.");
            return false;
        }
        if (!profile.activeQuest().isBlank()) {
            Text.error(player, "Schließe zuerst deine aktive Quest ab.");
            return false;
        }
        if (profile.completedQuests().contains(quest.id())) {
            Text.error(player, "Diese Quest hast du bereits abgeschlossen.");
            return false;
        }
        if (profile.level() < quest.minimumLevel()) {
            Text.error(player, "Dafür benötigst du Level " + quest.minimumLevel() + ".");
            return false;
        }
        profile.activeQuest(quest.id());
        profile.questProgress(0);
        data.saveAll();
        Text.success(player, "Quest angenommen: " + quest.name());
        return true;
    }

    public void progress(Player player, String target) {
        Profile profile = data.profile(player.getUniqueId());
        if (profile.activeQuest().isBlank()) return;
        QuestDefinition quest = quests.get(profile.activeQuest());
        if (quest == null || !quest.target().equalsIgnoreCase(target)) return;
        profile.questProgress(profile.questProgress() + 1);
        if (profile.questProgress() < quest.amount()) {
            Text.send(player, quest.name() + ": " + profile.questProgress() + "/" + quest.amount());
            return;
        }
        profile.completedQuests().add(quest.id());
        profile.activeQuest("");
        profile.questProgress(0);
        game.gainExperience(player, quest.rewardXp(), "Quest: " + quest.name());
        game.addCoins(player, quest.rewardCoins(), "Quest: " + quest.name());
        data.saveAll();
        Text.success(player, "Quest abgeschlossen: " + quest.name() + "!");
    }
}
