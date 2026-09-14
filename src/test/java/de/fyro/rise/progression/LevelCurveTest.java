package de.fyro.rise.progression;

import de.fyro.rise.model.Model.Profile;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LevelCurveTest {
    @Test
    void curveStartsPredictablyAndIncreases() {
        assertEquals(100, LevelCurve.xpForNext(1));
        assertEquals(163, LevelCurve.xpForNext(2));
        for (int level = 1; level < 59; level++) {
            assertTrue(LevelCurve.xpForNext(level + 1) > LevelCurve.xpForNext(level));
        }
        assertEquals(0, LevelCurve.xpForNext(60));
    }

    @Test
    void calculatesVisibleExperienceProgress() {
        assertEquals(0.0f, LevelCurve.progress(1, 0), 0.0001f);
        assertEquals(0.5f, LevelCurve.progress(1, 50), 0.0001f);
        assertEquals(0.9999f, LevelCurve.progress(1, 999), 0.0001f);
        assertEquals(1.0f, LevelCurve.progress(60, 0), 0.0001f);
    }

    @Test
    void grantsMultipleLevelsAndHonorsCap() {
        Profile profile = new Profile(UUID.randomUUID());
        List<Integer> reached = new ArrayList<>();
        assertEquals(2, LevelCurve.grant(profile, 263, reached::add));
        assertEquals(3, profile.level());
        assertEquals(0, profile.experience());
        assertEquals(List.of(2, 3), reached);

        LevelCurve.grant(profile, Integer.MAX_VALUE, ignored -> {});
        assertEquals(60, profile.level());
        assertEquals(0, profile.experience());
    }

    @Test
    void totalRequirementIsMonotonic() {
        int previous = 0;
        for (int level = 1; level <= 60; level++) {
            int total = LevelCurve.totalXpToReach(level);
            assertTrue(total >= previous);
            previous = total;
        }
    }
}
