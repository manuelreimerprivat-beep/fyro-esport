package de.fyro.rise.command;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.dungeon.DungeonService;
import de.fyro.rise.game.GameService;
import de.fyro.rise.gear.GearService;
import de.fyro.rise.model.Model.Faction;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import de.fyro.rise.progression.LevelCurve;
import de.fyro.rise.quest.QuestService;
import de.fyro.rise.social.SocialService;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class RiseCommand implements CommandExecutor, TabCompleter {
    private final FyroRisePlugin plugin;
    private final DataStore data;
    private final GameService game;
    private final GearService gear;
    private final QuestService quests;
    private final SocialService social;
    private final DungeonService dungeons;

    public RiseCommand(FyroRisePlugin plugin, DataStore data, GameService game, GearService gear,
                       QuestService quests, SocialService social, DungeonService dungeons) {
        this.plugin = plugin;
        this.data = data;
        this.game = game;
        this.gear = gear;
        this.quests = quests;
        this.social = social;
        this.dungeons = dungeons;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadPlugin();
                Text.success(sender, "Konfiguration neu geladen.");
            } else {
                Text.error(sender, "Dieser Befehl benötigt einen Spieler.");
            }
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(player);
            return true;
        }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "choose", "waehlen", "wählen" -> choose(player, args);
                case "profile", "profil" -> profile(player);
                case "skills", "faehigkeiten", "fähigkeiten" -> skills(player);
                case "skill", "faehigkeit", "fähigkeit" -> skill(player, args);
                case "quest" -> quest(player, args);
                case "party", "gruppe" -> party(player, args);
                case "guild", "gilde" -> guild(player, args);
                case "balance", "geld" -> Text.send(player, "Guthaben: " + game.profile(player).coins() + " ✦");
                case "pay", "zahlen" -> pay(player, args);
                case "pvp" -> pvp(player, args);
                case "dungeon" -> dungeon(player, args);
                case "admin" -> admin(player, args);
                default -> Text.error(player, "Unbekannter Unterbefehl. Nutze /rise help.");
            }
        } catch (NumberFormatException exception) {
            Text.error(player, "Bitte gib eine gültige Zahl ein.");
        }
        return true;
    }

    private void choose(Player player, String[] args) {
        if (args.length < 3) {
            Text.send(player, "/rise choose faction <aurora|obsidian>");
            Text.send(player, "/rise choose class <krieger|magier|waldlaeufer|priester>");
            return;
        }
        if (args[1].equalsIgnoreCase("faction") || args[1].equalsIgnoreCase("fraktion")) {
            Faction.parse(args[2]).ifPresentOrElse(
                    faction -> game.chooseFaction(player, faction),
                    () -> Text.error(player, "Fraktionen: aurora oder obsidian.")
            );
        } else if (args[1].equalsIgnoreCase("class") || args[1].equalsIgnoreCase("klasse")) {
            RiseClass.parse(args[2]).ifPresentOrElse(
                    riseClass -> game.chooseClass(player, riseClass),
                    () -> Text.error(player, "Klassen: krieger, magier, waldlaeufer, priester.")
            );
        } else {
            Text.error(player, "Nutze faction/fraktion oder class/klasse.");
        }
    }

    private void profile(Player player) {
        Profile profile = game.profile(player);
        int needed = LevelCurve.xpForNext(profile.level());
        Text.send(player, "===== Dein Charakter =====");
        player.sendMessage("§6Fraktion: §7" + profile.faction().displayName());
        player.sendMessage("§6Klasse: §7" + profile.riseClass().displayName());
        player.sendMessage("§6Level: §7" + profile.level() + (needed == 0 ? " (Maximum)" : " §8[" + profile.experience() + "/" + needed + " EP]"));
        player.sendMessage("§6Guthaben: §7" + profile.coins() + " ✦");
        player.sendMessage("§6Gilde: §7" + (profile.guild().isBlank() ? "Keine" : profile.guild()));
        player.sendMessage("§6PvP: §7" + (profile.pvpEnabled() ? "AN" : "AUS"));
        if (!profile.activeQuest().isBlank()) {
            player.sendMessage("§6Aktive Quest: §7" + profile.activeQuest() + " §8(" + profile.questProgress() + ")");
        }
    }

    private void skills(Player player) {
        RiseClass riseClass = game.profile(player).riseClass();
        if (riseClass == RiseClass.NOVIZE) {
            Text.error(player, "Wähle zuerst eine Klasse.");
            return;
        }
        Text.send(player, "Fähigkeiten von " + riseClass.displayName() + ":");
        for (int slot = 1; slot <= 3; slot++) {
            player.sendMessage("§8- §6/rise skill " + slot + " §7" + riseClass.skillName(slot)
                    + " §8(" + riseClass.cooldown(slot) + " Sek.)");
        }
    }

    private void skill(Player player, String[] args) {
        if (args.length < 2) {
            skills(player);
            return;
        }
        game.castSkill(player, Integer.parseInt(args[1]));
    }

    private void quest(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            quests.list(player);
        } else if (args[1].equalsIgnoreCase("accept") && args.length >= 3) {
            quests.accept(player, args[2]);
        } else {
            Text.send(player, "/rise quest list | /rise quest accept <id>");
        }
    }

    private void party(Player player, String[] args) {
        if (args.length < 2) {
            Text.send(player, "/rise party <create|invite|accept|leave|info>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create", "erstellen" -> social.createParty(player);
            case "invite", "einladen" -> {
                Player target = requireOnline(player, args, 2);
                if (target != null) social.inviteParty(player, target);
            }
            case "accept", "annehmen" -> social.acceptParty(player);
            case "leave", "verlassen" -> social.leaveParty(player);
            case "info" -> social.partyInfo(player);
            default -> Text.error(player, "Gruppenbefehle: create, invite, accept, leave, info.");
        }
    }

    private void guild(Player player, String[] args) {
        if (args.length < 2) {
            Text.send(player, "/rise guild <create|invite|accept|leave|info|deposit>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create", "erstellen" -> {
                if (args.length < 3) Text.error(player, "Nutze /rise guild create <Name>.");
                else social.createGuild(player, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "invite", "einladen" -> {
                Player target = requireOnline(player, args, 2);
                if (target != null) social.inviteGuild(player, target);
            }
            case "accept", "annehmen" -> {
                if (args.length < 3) Text.error(player, "Gib den Gildennamen an.");
                else social.acceptGuild(player, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "leave", "verlassen" -> social.leaveGuild(player);
            case "info" -> social.guildInfo(player);
            case "deposit", "einzahlen" -> {
                if (args.length < 3) Text.error(player, "Gib einen Betrag an.");
                else social.deposit(player, Double.parseDouble(args[2]));
            }
            default -> Text.error(player, "Gildenbefehle: create, invite, accept, leave, info, deposit.");
        }
    }

    private void pay(Player player, String[] args) {
        Player target = requireOnline(player, args, 1);
        if (target == null || args.length < 3) {
            Text.error(player, "Nutze /rise pay <Spieler> <Betrag>.");
            return;
        }
        double amount = Double.parseDouble(args[2]);
        if (!game.transfer(player, target, amount)) {
            Text.error(player, "Überweisung fehlgeschlagen. Prüfe Betrag und Guthaben.");
            return;
        }
        Text.success(player, amount + " ✦ an " + target.getName() + " gesendet.");
        Text.success(target, player.getName() + " hat dir " + amount + " ✦ gesendet.");
    }

    private void pvp(Player player, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            Text.send(player, "/rise pvp <on|off>");
            return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        game.profile(player).pvpEnabled(enabled);
        data.saveAll();
        Text.success(player, "PvP ist jetzt " + (enabled ? "AN" : "AUS") + ".");
    }

    private void dungeon(Player player, String[] args) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("crypt") || args[1].equalsIgnoreCase("krypta"))) {
            dungeons.startCrypt(player);
        } else {
            Text.send(player, "/rise dungeon crypt");
        }
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("fyrorise.admin")) {
            Text.error(player, "Dafür fehlt dir die Berechtigung.");
            return;
        }
        if (args.length < 2) {
            Text.send(player, "/rise admin <reload|setlevel|givegear|spawnboss>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                Text.success(player, "Konfiguration und Quests neu geladen.");
            }
            case "setlevel" -> {
                Player target = requireOnline(player, args, 2);
                if (target == null || args.length < 4) return;
                Profile profile = game.profile(target);
                profile.level(Integer.parseInt(args[3]));
                profile.experience(0);
                data.saveAll();
                game.syncExperience(target);
                Text.success(player, target.getName() + " ist jetzt Level " + profile.level() + ".");
            }
            case "givegear" -> {
                Player target = requireOnline(player, args, 2);
                if (target == null || args.length < 4) return;
                Profile profile = game.profile(target);
                if (profile.riseClass() == RiseClass.NOVIZE) {
                    Text.error(player, "Der Spieler hat noch keine Klasse.");
                    return;
                }
                gear.giveTier(target, profile.riseClass(), Integer.parseInt(args[3]));
                Text.success(player, "Ausrüstung vergeben.");
            }
            case "spawnboss" -> {
                dungeons.spawnBoss(player.getLocation().add(4, 0, 4), player);
                Text.success(player, "Testboss beschworen.");
            }
            default -> Text.error(player, "Adminbefehle: reload, setlevel, givegear, spawnboss.");
        }
    }

    private Player requireOnline(Player sender, String[] args, int index) {
        if (args.length <= index) {
            Text.error(sender, "Spielername fehlt.");
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[index]);
        if (target == null) Text.error(sender, "Dieser Spieler ist nicht online.");
        return target;
    }

    private void help(Player player) {
        Text.send(player, "===== FOR YOUR RISE ONLY =====");
        player.sendMessage("§6/rise profile §7– Charakterübersicht");
        player.sendMessage("§6/rise choose ... §7– Fraktion und Klasse wählen");
        player.sendMessage("§6/rise skills §7– Fähigkeiten anzeigen");
        player.sendMessage("§6/rise quest list §7– Quests");
        player.sendMessage("§6/rise party ... §7– Gruppe");
        player.sendMessage("§6/rise guild ... §7– Gilde");
        player.sendMessage("§6/rise balance | pay §7– Wirtschaft");
        player.sendMessage("§6/rise dungeon crypt §7– Grabkrypta");
        player.sendMessage("§6/rise pvp on|off §7– Fraktions-PvP");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> values = switch (args.length) {
            case 1 -> List.of("help", "profile", "choose", "skills", "skill", "quest", "party", "guild", "balance", "pay", "pvp", "dungeon", "admin");
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "choose" -> List.of("faction", "class");
                case "quest" -> List.of("list", "accept");
                case "party" -> List.of("create", "invite", "accept", "leave", "info");
                case "guild" -> List.of("create", "invite", "accept", "leave", "info", "deposit");
                case "pvp" -> List.of("on", "off");
                case "dungeon" -> List.of("crypt");
                case "skill" -> List.of("1", "2", "3");
                case "admin" -> List.of("reload", "setlevel", "givegear", "spawnboss");
                default -> List.of();
            };
            case 3 -> args[0].equalsIgnoreCase("choose")
                    ? (args[1].equalsIgnoreCase("faction") ? List.of("aurora", "obsidian")
                    : List.of("krieger", "magier", "waldlaeufer", "priester"))
                    : List.of();
            default -> List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
