package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.CoverageCell;
import akyrion.thepredict.domain.distill.CoverageIndex;
import akyrion.thepredict.domain.distill.DistilledPolicy;
import akyrion.thepredict.domain.distill.OodCause;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LD-12 and LD-06: pairs stay inside their cell, and a shadow delta without usable values is no evidence. */
class AgreementBookTest {
    @Test
    void delegatedAndAuditedWindowsArePairedInsideTheirOwnContext() {
        var book = new AgreementBook();
        for (var window = 0; window < 9; window++) {
            book.prepareDelegated("outcome", "r1:ALL", "ctx-a");
            book.onWindow("Guardian", 0.8, 0L);
        }
        book.prepareAudit("outcome", "r1:ALL", "ctx-b");
        book.onWindow("Guardian", 0.2, 0L);
        book.prepareDelegated("outcome", "r1:ALL", "ctx-b");
        book.onWindow("Guardian", 0.5, 0L);
        book.prepareShadow("outcome", "r1:ALL", "ctx-c", 0.0, true);
        var rows = book.auditClose("Guardian", 0L);
        assertEquals(1, rows.size());
        assertEquals("ctx-b", rows.getFirst().context());
        assertEquals(0.3, rows.getFirst().sumDelta(), 1.0e-12);
        assertTrue(book.auditClose("Guardian", 0L).isEmpty());
    }

    @Test
    void aShadowDeltaWithoutUsableValuesOnlyCountsTheComparison() {
        var book = new AgreementBook();
        book.prepareShadow("outcome", "r1:ALL", "ctx", 0.0, false, false);
        var row = book.onWindow("Guardian", 0.1, 0L).getFirst();
        assertEquals(0, row.n());
        assertEquals(1, row.compared());
        assertEquals(0.0, row.sumDelta(), 0.0);
        book.prepareShadow("outcome", "r1:ALL", "ctx", -0.4, false);
        assertEquals(1, book.onWindow("Guardian", 0.1, 0L).getFirst().n());
    }

    @Test
    void sixtyEmptyShadowsNeverCoverACell() {
        var book = new AgreementBook();
        var rows = new ArrayList<akyrion.thepredict.domain.distill.AgreementStatDelta>();
        for (var capture = 0; capture < 60; capture++) {
            book.prepareShadow("outcome", "r1:ALL", "ctx", 0.0, true, false);
            rows.addAll(book.onWindow("Guardian", 0.0, 0L));
        }
        var runtime = new DistillRuntime();
        DistillPublish.refresh(runtime, rows, DistilledPolicy.table("Guardian", "ctx", List.of("press")), 0L, "f",
                "outcome", true);
        var view = runtime.view("ctx", List.of("press"), 0L, 0L, 50, 0.02, 60);
        assertFalse(view.covered());
        assertEquals(Optional.of(OodCause.OOD_CELL_UNSEEN), view.ood());
    }

    @Test
    void theArmsAreCappedPerEncounter() {
        var book = new AgreementBook();
        for (var window = 0; window < AgreementBook.MAX_WINDOWS + 10; window++) {
            book.prepareDelegated("outcome", "r1:ALL", "ctx");
            book.onWindow("Guardian", 1.0, 0L);
            book.prepareAudit("outcome", "r1:ALL", "ctx");
            book.onWindow("Guardian", 0.0, 0L);
        }
        assertEquals(AgreementBook.MAX_WINDOWS, book.auditClose("Guardian", 0L).size());
    }

    @Test
    void theLiveFingerprintAndTeacherDecideAgainstTheIndex() {
        var runtime = new DistillRuntime();
        var cell = new CoverageCell("ctx", 60, 0.0, 0.0, List.of("press"), false, 1.0);
        runtime.publish(CoverageIndex.of(List.of(cell), 0L, "finger-a", "outcome", true),
                DistilledPolicy.table("Guardian", "ctx", List.of("press")));
        assertTrue(runtime.view("ctx", List.of("press"), 0L, 0L, 50, 0.02, 60, "finger-a", "outcome").covered());
        assertEquals(Optional.of(OodCause.OOD_PROFILE_CHANGED),
                runtime.view("ctx", List.of("press"), 0L, 0L, 50, 0.02, 60, "finger-b", "outcome").ood());
        assertEquals(Optional.of(OodCause.OOD_MODEL_CHANGED),
                runtime.view("ctx", List.of("press"), 0L, 0L, 50, 0.02, 60, "finger-a", "kev-1").ood());
    }
}
