package de.fyro.rise.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobLevelRulesTest {
    @Test
    void calculatesZoneLevelsFromDistanceAndDimensionFloor() {
        assertEquals(1, MobLevelRules.distanceLevel(0, 175, 1, 1, 60));
        assertEquals(3, MobLevelRules.distanceLevel(350, 175, 1, 1, 60));
        assertEquals(20, MobLevelRules.distanceLevel(0, 175, 20, 1, 60));
        assertEquals(60, MobLevelRules.distanceLevel(50_000, 175, 1, 1, 60));
    }

    @Test
    void stopsExperienceAtThreeLevelsAboveTheMob() {
        assertTrue(MobLevelRules.grantsExperience(10, 8));
        assertFalse(MobLevelRules.grantsExperience(10, 7));
        assertFalse(MobLevelRules.grantsExperience(60, 57));
    }

    @Test
    void scalesStatsWithoutWeakeningLevelOne() {
        assertEquals(1.0, MobLevelRules.statMultiplier(1, 3.0), 0.0001);
        assertEquals(1.27, MobLevelRules.statMultiplier(10, 3.0), 0.0001);
    }

    @Test
    void increasesRewardsWithTheMobLevel() {
        assertEquals(12, MobLevelRules.experienceReward(12, 1, 2));
        assertEquals(30, MobLevelRules.experienceReward(12, 10, 2));
        assertEquals(4.35, MobLevelRules.coinReward(3, 10, 0.15), 0.0001);
    }
}
