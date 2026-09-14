package de.fyro.rise.mob;

public final class MobLevelRules {
    private MobLevelRules() {}

    public static int clampLevel(int level, int minimum, int maximum) {
        int safeMinimum = Math.max(1, minimum);
        int safeMaximum = Math.max(safeMinimum, maximum);
        return Math.max(safeMinimum, Math.min(safeMaximum, level));
    }

    public static int distanceLevel(double distance, int blocksPerLevel, int dimensionFloor,
                                    int minimum, int maximum) {
        int safeBlocks = Math.max(1, blocksPerLevel);
        int calculated = 1 + (int) Math.floor(Math.max(0.0, distance) / safeBlocks);
        return clampLevel(Math.max(calculated, dimensionFloor), minimum, maximum);
    }

    public static double statMultiplier(int level, double percentPerLevel) {
        int safeLevel = Math.max(1, level);
        double safePercent = Math.max(0.0, percentPerLevel);
        return 1.0 + ((safeLevel - 1) * safePercent / 100.0);
    }

    public static boolean grantsExperience(int playerLevel, int mobLevel) {
        return Math.max(1, playerLevel) - Math.max(1, mobLevel) < 3;
    }

    public static int experienceReward(int baseExperience, int mobLevel, int experiencePerLevel) {
        return Math.max(0, baseExperience)
                + (Math.max(1, mobLevel) - 1) * Math.max(0, experiencePerLevel);
    }

    public static double coinReward(double baseCoins, int mobLevel, double coinsPerLevel) {
        double result = Math.max(0.0, baseCoins)
                + (Math.max(1, mobLevel) - 1) * Math.max(0.0, coinsPerLevel);
        return Math.round(result * 100.0) / 100.0;
    }
}
