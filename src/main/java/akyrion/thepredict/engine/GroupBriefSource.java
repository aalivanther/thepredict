package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupBrief;

/**
 * Brief a capture may stamp onto the leader's request (M25). The group step is the source.
 */
public interface GroupBriefSource {
    /**
     * Brief of one encounter.
     *
     * @param encounter the encounter
     * @return the brief, or {@link GroupBrief#NONE}
     */
    GroupBrief brief(EncounterId encounter);
}
