package akyrion.thepredict.domain;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The visible stance is the action when one is set and the base otherwise (M23.1). */
class CombatStanceTest {
    private static CombatStance sink = CombatStance.NEUTRAL;

    @Test
    void theActionHidesTheBaseAndNoneShowsIt() {
        for (var base : BaseStance.values()) {
            assertEquals(CombatStance.valueOf(base.name()), CombatStance.visible(base.ordinal(),
                    ActionStance.NONE.ordinal()));
            for (var action : ActionStance.values()) {
                if (action != ActionStance.NONE) {
                    assertEquals(CombatStance.valueOf(action.name()), CombatStance.visible(base.ordinal(),
                            action.ordinal()));
                }
            }
        }
    }

    @Test
    void readingTheVisibleStanceAllocatesNothing() {
        for (var step = 0; step < 200; step++) {
            sink = CombatStance.visible(step % 4, step % 4);
        }
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            return;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        var thread = Thread.currentThread().threadId();
        var before = bean.getThreadAllocatedBytes(thread);
        for (var step = 0; step < 200; step++) {
            sink = CombatStance.visible(step % 4, step % 4);
        }
        assertEquals(before, bean.getThreadAllocatedBytes(thread));
    }
}
