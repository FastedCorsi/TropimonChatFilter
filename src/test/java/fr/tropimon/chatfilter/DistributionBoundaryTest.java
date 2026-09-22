package fr.tropimon.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributionBoundaryTest {
    @Test void playerUpdaterAndInstallerStayOutOfTheMod() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("fr.tropimon.chatfilter.TropimonSelfUpdater"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("fr.tropimon.chatfilter.TropimonUpdateInstaller"));
    }
}
