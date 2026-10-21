package akyrion.thepredict.adapter.server.goals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallDecisionTest {
    @Test
    void aMissingMarkerMeansTheGoalsWereReplacedAndMustBeInstalled() {
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(new InstallDecision.Marker.Missing(), 3, 0));
    }

    @Test
    void aMarkerOfAnotherPluginInstanceIsReplaced() {
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(new InstallDecision.Marker.Foreign(), 3, 0));
    }

    @Test
    void theSameRevisionAndGenerationChangesNothing() {
        assertEquals(InstallDecision.NO_OP, InstallDecision.decide(new InstallDecision.Marker.Owned(3, 2, 12), 3, 2));
        assertEquals(InstallDecision.NO_OP, InstallDecision.decide(new InstallDecision.Marker.Owned(0, 0, 0), 0, 0));
    }

    @Test
    void aDifferentRevisionReinstallsInEitherDirection() {
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(new InstallDecision.Marker.Owned(3, 2, 12), 4, 2));
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(new InstallDecision.Marker.Owned(4, 2, 12), 3, 2));
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(
                new InstallDecision.Marker.Owned(-1, 0, 0), 0, 0));
    }

    @Test
    void aDifferentGenerationReinstallsBecauseThePaperGoalsCheckTheOldOne() {
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(new InstallDecision.Marker.Owned(3, 2, 12), 3, 3));
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(new InstallDecision.Marker.Owned(3, 3, 12), 3, 2));
    }

    @Test
    void aMissingReadingIsAProgrammingError() {
        assertThrows(NullPointerException.class, () -> InstallDecision.decide(null, 0, 0));
    }

    @Test
    void aRefusalOfTheSameRevisionAndGenerationIsKeptAndAnyChangeRetries() {
        var refused = new InstallDecision.Marker.Refused(3, 2, "install incomplete: 1 MythicMobs goals missing");
        assertEquals(InstallDecision.NO_OP, InstallDecision.decide(refused, 3, 2));
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(refused, 4, 2));
        assertEquals(InstallDecision.INSTALL, InstallDecision.decide(refused, 3, 3));
    }
}
