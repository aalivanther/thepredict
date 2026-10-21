package akyrion.thepredict.adapter.server.duel;

import java.util.Optional;

/**
 * The files of one duel recording (M18.4), each with its name and, for a CSV, its header line. Every CSV row carries
 * {@code tick}, {@code round}, {@code arm} and the UUID of the mobs involved; {@code decisions.jsonl} holds typed lines.
 */
public enum DuelFile {
    /** One typed line per event of a decision: {@code planned}, {@code body}, {@code result}, {@code acceptance}, {@code local}. */
    DECISIONS("decisions.jsonl", ""),
    /** One row per followed duelist every 5 ticks. */
    SAMPLES("samples.csv", "tick,round,arm,uuid,side,kind,health_ratio,absorption,x,y,z,yaw,distance_to_rival,"
            + "gap_to_rival,target_uuid,level,tactic,movement,confidence,raised_shield,blocking,active_item"),
    /** One row per cast attempt. */
    CASTS("casts.csv", "tick,round,arm,uuid,side,kind,source,skill,tactic,choice,outcome"),
    /** One row per damage taken or dealt by a followed duelist. */
    DAMAGE("damage.csv", "tick,round,arm,attacker_uuid,attacker_side,victim_uuid,victim_side,cause,final_damage,"
            + "victim_health_fraction,victim_blocking,attributed_skill,is_hit_of_cast"),
    /** One row per intent or level event. */
    INTENTS("intents.csv", "tick,round,arm,uuid,event,from_level,to_level,tactic,movement,confidence,question_key,reason"),
    /** One row per guard or shield use. */
    DEFENSE("defense.csv", "tick,round,arm,uuid,kind"),
    /** One row per retreat. */
    RETREATS("retreats.csv", "tick,round,arm,uuid,source,distance_before,distance_after"),
    /** One row per enriched threat change. */
    THREAT("threat.csv", "tick,round,arm,mob_uuid,participant_uuid,amount,cause"),
    /** One row per closed outcome window. */
    OUTCOMES("outcomes.csv", "tick,round,arm,uuid,level,tactic,movement,dealt,taken,reached,casts,hits,mob_died,"
            + "target_died,effect"),
    /** One row per round. */
    ROUNDS("rounds.csv", "round,arm,spawn_swapped,red_uuid,red_kind,blue_uuid,blue_kind,start_tick,end_tick,winner,"
            + "end_reason,red_dealt,blue_dealt,red_hits,blue_hits,requests,input_tokens"),
    /** The totals of the recording, written once when it stops. */
    SUMMARY("summary.md", "");

    private final String fileName;
    private final String header;

    DuelFile(String fileName, String header) {
        this.fileName = fileName;
        this.header = header;
    }

    /**
     * Returns the name of the file inside the folder of the recording.
     *
     * @return the file name
     */
    public String fileName() {
        return fileName;
    }

    /**
     * Returns the header line of a CSV file.
     *
     * @return the header, empty for {@code decisions.jsonl} and {@code summary.md}
     */
    public Optional<String> header() {
        return header.isEmpty() ? Optional.empty() : Optional.of(header);
    }
}
