package de.fyro.rise.progression;

import de.fyro.rise.model.Model.Profile;
import java.util.function.IntConsumer;

public final class LevelCurve {
    public static final int MAX_LEVEL = 60;

    private LevelCurve() {}

    public static int xpForNext(int level) {
        if (level >= MAX_LEVEL) return 0;
        int safeLevel = Math.max(1, level);
        int step = safeLevel - 1;
        return 100 + (55 * step) + (8 * step * step);
    }

    public static int totalXpToReach(int level) {
        int capped = Math.max(1, Math.min(MAX_LEVEL, level));
        int result = 0;
        for (int current = 1; current < capped; current++) result += xpForNext(current);
        return result;
    }

    public static int grant(Profile profile, int amount, IntConsumer onLevelUp) {
        if (amount <= 0 || profile.level() >= MAX_LEVEL) return 0;
        profile.experience(profile.experience() + amount);
        int gained = 0;
        while (profile.level() < MAX_LEVEL && profile.experience() >= xpForNext(profile.level())) {
            profile.experience(profile.experience() - xpForNext(profile.level()));
            profile.level(profile.level() + 1);
            gained++;
            onLevelUp.accept(profile.level());
        }
        if (profile.level() >= MAX_LEVEL) profile.experience(0);
        return gained;
    }
}
