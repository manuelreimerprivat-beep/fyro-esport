package de.fyro.rise.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    @Test
    void parsesGermanClassNamesAndAliases() {
        assertEquals(Model.RiseClass.KRIEGER, Model.RiseClass.parse("Krieger").orElseThrow());
        assertEquals(Model.RiseClass.WALDLAEUFER, Model.RiseClass.parse("Waldläufer").orElseThrow());
        assertEquals(Model.RiseClass.WALDLAEUFER, Model.RiseClass.parse("waldlaeufer").orElseThrow());
        assertEquals(Model.RiseClass.PRIESTER, Model.RiseClass.parse("Heiler").orElseThrow());
        assertTrue(Model.RiseClass.parse("unbekannt").isEmpty());
    }

    @Test
    void parsesOnlyTheTwoPlayableFactions() {
        assertEquals(Model.Faction.AURORA, Model.Faction.parse("Aurora").orElseThrow());
        assertEquals(Model.Faction.OBSIDIAN, Model.Faction.parse("Obsidianpakt").orElseThrow());
        assertTrue(Model.Faction.parse("neutral").isEmpty());
    }
}
