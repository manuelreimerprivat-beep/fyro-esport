package de.fyro.rise.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameRulesTest {
    @Test
    void validatesGuildNames() {
        assertTrue(GameRules.validGuildName("FYRO Elite"));
        assertTrue(GameRules.validGuildName("Wächter_01"));
        assertFalse(GameRules.validGuildName("ab"));
        assertFalse(GameRules.validGuildName("ungültig!"));
        assertFalse(GameRules.validGuildName("dieser name ist wirklich viel zu lang"));
    }

    @Test
    void rejectsInvalidMoneyTransfers() {
        assertTrue(GameRules.validTransfer(25.50, 50));
        assertFalse(GameRules.validTransfer(0, 50));
        assertFalse(GameRules.validTransfer(-1, 50));
        assertFalse(GameRules.validTransfer(51, 50));
        assertFalse(GameRules.validTransfer(Double.NaN, 50));
        assertFalse(GameRules.validTransfer(Double.POSITIVE_INFINITY, 50));
    }

    @Test
    void roundsMoneySafely() {
        assertEquals(12.35, GameRules.roundedMoney(12.345), 0.001);
        assertEquals(0, GameRules.roundedMoney(-5), 0.001);
        assertEquals(0, GameRules.roundedMoney(Double.NaN), 0.001);
    }
}
