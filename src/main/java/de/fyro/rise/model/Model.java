package de.fyro.rise.model;

import java.text.Normalizer;
import java.util.*;

public final class Model {
    private Model() {}

    private static String key(String input) {
        if (input == null) return "";
        String value = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return value.replace("ß", "ss").replace("_", "").replace("-", "");
    }

    public enum Faction {
        UNGEWAEHLT("Ungewählt"),
        AURORA("Orden der Morgenröte"),
        OBSIDIAN("Obsidianpakt");

        private final String displayName;
        Faction(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }

        public static Optional<Faction> parse(String input) {
            return switch (key(input)) {
                case "aurora", "morgenrote", "orden", "allianz" -> Optional.of(AURORA);
                case "obsidian", "pakt", "horde" -> Optional.of(OBSIDIAN);
                default -> Optional.empty();
            };
        }
    }

    public enum RiseClass {
        NOVIZE("Novize", List.of("Keine", "Keine", "Keine"), List.of(0, 0, 0)),
        KRIEGER("Krieger", List.of("Schildstoß", "Kriegsruf", "Wirbelsturm"), List.of(5, 18, 12)),
        MAGIER("Magier", List.of("Feuerlanze", "Arkanschritt", "Frostnova"), List.of(4, 10, 14)),
        WALDLAEUFER("Waldläufer", List.of("Präzisionsschuss", "Ausweichsprung", "Pfeilhagel"), List.of(4, 8, 15)),
        PRIESTER("Priester", List.of("Lichtheilung", "Heiliges Urteil", "Zuflucht"), List.of(6, 5, 18));

        private final String displayName;
        private final List<String> skills;
        private final List<Integer> cooldowns;

        RiseClass(String displayName, List<String> skills, List<Integer> cooldowns) {
            this.displayName = displayName;
            this.skills = skills;
            this.cooldowns = cooldowns;
        }

        public String displayName() { return displayName; }
        public String skillName(int slot) { return skills.get(slot - 1); }
        public int cooldown(int slot) { return cooldowns.get(slot - 1); }

        public static Optional<RiseClass> parse(String input) {
            return switch (key(input)) {
                case "krieger", "warrior" -> Optional.of(KRIEGER);
                case "magier", "mage" -> Optional.of(MAGIER);
                case "waldlaufer", "ranger" -> Optional.of(WALDLAEUFER);
                case "priester", "heiler", "priest" -> Optional.of(PRIESTER);
                default -> Optional.empty();
            };
        }
    }

    public static final class Profile {
        private final UUID uuid;
        private Faction faction = Faction.UNGEWAEHLT;
        private RiseClass riseClass = RiseClass.NOVIZE;
        private int level = 1;
        private int experience = 0;
        private double coins = 100.0;
        private boolean pvpEnabled;
        private String activeQuest = "";
        private int questProgress;
        private final Set<String> completedQuests = new HashSet<>();
        private String guild = "";

        public Profile(UUID uuid) { this.uuid = Objects.requireNonNull(uuid); }
        public UUID uuid() { return uuid; }
        public Faction faction() { return faction; }
        public void faction(Faction value) { faction = Objects.requireNonNull(value); }
        public RiseClass riseClass() { return riseClass; }
        public void riseClass(RiseClass value) { riseClass = Objects.requireNonNull(value); }
        public int level() { return level; }
        public void level(int value) { level = Math.max(1, Math.min(60, value)); }
        public int experience() { return experience; }
        public void experience(int value) { experience = Math.max(0, value); }
        public double coins() { return coins; }
        public void coins(double value) { coins = Math.max(0, Math.round(value * 100.0) / 100.0); }
        public boolean pvpEnabled() { return pvpEnabled; }
        public void pvpEnabled(boolean value) { pvpEnabled = value; }
        public String activeQuest() { return activeQuest; }
        public void activeQuest(String value) { activeQuest = value == null ? "" : value; }
        public int questProgress() { return questProgress; }
        public void questProgress(int value) { questProgress = Math.max(0, value); }
        public Set<String> completedQuests() { return completedQuests; }
        public String guild() { return guild; }
        public void guild(String value) { guild = value == null ? "" : value; }
        public boolean characterReady() {
            return faction != Faction.UNGEWAEHLT && riseClass != RiseClass.NOVIZE;
        }
    }

    public record QuestDefinition(
            String id,
            String name,
            String description,
            String target,
            int amount,
            int minimumLevel,
            int rewardXp,
            double rewardCoins
    ) {}

    public static final class Guild {
        private final String name;
        private UUID owner;
        private final Set<UUID> members = new HashSet<>();
        private double bank;

        public Guild(String name, UUID owner) {
            this.name = Objects.requireNonNull(name);
            this.owner = Objects.requireNonNull(owner);
            this.members.add(owner);
        }

        public String name() { return name; }
        public UUID owner() { return owner; }
        public void owner(UUID value) { owner = Objects.requireNonNull(value); }
        public Set<UUID> members() { return members; }
        public double bank() { return bank; }
        public void bank(double value) { bank = Math.max(0, Math.round(value * 100.0) / 100.0); }
    }
}
