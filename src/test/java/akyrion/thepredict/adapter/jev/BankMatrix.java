package akyrion.thepredict.adapter.jev;

import akyrion.thepredict.adapter.jev.BankScenario.BankProfile;
import akyrion.thepredict.adapter.jev.BankScenario.Family;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.TargetCue;
import akyrion.thepredict.domain.Trend;

import java.util.ArrayList;
import java.util.List;

/**
 * The declarative matrix of the labelled bank of M16.9: 48 {@code NOW} scenarios and 12 contingencies in twelve families,
 * each with proposed labels written from the situation alone (never from what fits now). The user reviews them with
 * {@code label-review.yml}; nothing here is final until a scenario is {@code REVIEWED}.
 */
final class BankMatrix {
    private static final String PRESS = "press_and_strike";
    private static final String SLAM = "close_in_and_slam";
    private static final String SHOOT = "keep_distance_and_shoot";
    private static final String KEEP = "keep_distance";
    private static final String RETREAT = "retreat_and_defend";
    private static final String FLANK = "flank_and_strike";
    private static final String APPROACH = "approach";
    private static final String HOLD = "hold";

    private BankMatrix() {
    }

    /** The sixty rows, family after family, in identifier order. */
    static List<BankScenario> rows() {
        var rows = new ArrayList<BankScenario>();
        rows.addAll(melee());
        rows.addAll(shooter());
        rows.addAll(shield());
        rows.addAll(lowHealth());
        rows.addAll(pillar());
        rows.addAll(grouped());
        rows.addAll(finisher());
        rows.addAll(drinking());
        rows.addAll(noLineOfSight());
        rows.addAll(fleeing());
        rows.addAll(contingencyMelee());
        rows.addAll(contingencyCritical());
        return List.copyOf(rows);
    }

    private static BankScenario.Builder row(Family family, int index, BankProfile profile) {
        return BankScenario.of(String.format("bank-%s-%02d", family.key(), index), family, profile);
    }

    private static List<BankScenario> melee() {
        var family = Family.MELEE;
        var guardian = BankProfile.GUARDIAN;
        return List.of(
                row(family, 1, guardian).target(HealthBand.HEALTHY).best(PRESS).acceptable(PRESS, SLAM, APPROACH)
                        .forbidden(RETREAT, HOLD).because("Healthy mob in a melee duel with a healthy target: striking up"
                                + " close wins the exchange; healing a healthy mob or standing still wastes it."),
                row(family, 2, guardian).at(DistanceBand.MELEE, Trend.CLOSING).best(PRESS)
                        .acceptable(PRESS, SLAM, APPROACH).forbidden(RETREAT, HOLD).because("The target keeps pushing"
                                + " in: meet it with a strike; running away or idling gives it free hits."),
                row(family, 3, guardian).at(DistanceBand.MELEE, Trend.BACKING_AWAY).best(PRESS)
                        .acceptable(PRESS, SLAM, APPROACH, SHOOT).forbidden(RETREAT, HOLD).because("The target steps"
                                + " back but is still in reach: keep the pressure or punish it from range, never let it go"
                                + " free."),
                row(family, 4, guardian).target(HealthBand.CRITICAL).best(PRESS).acceptable(PRESS, SLAM)
                        .forbidden(RETREAT, HOLD, KEEP).because("The target is nearly dead within reach: finish it now;"
                                + " backing off or healing gives it time to recover."),
                row(family, 5, guardian).at(DistanceBand.MELEE, Trend.CLOSING).target(HealthBand.CRITICAL).best(PRESS)
                        .acceptable(PRESS, SLAM).forbidden(RETREAT, HOLD, KEEP).because("A nearly dead target walks"
                                + " into reach: strike it down; stepping away wastes the kill."),
                row(family, 6, guardian).at(DistanceBand.MELEE, Trend.BACKING_AWAY).target(HealthBand.CRITICAL)
                        .best(PRESS).acceptable(PRESS, SLAM, SHOOT, APPROACH).forbidden(RETREAT, HOLD, KEEP)
                        .because("A nearly dead target tries to slip away: chase and strike or shoot it before it"
                                + " escapes."));
    }

    private static List<BankScenario> shooter() {
        var family = Family.SHOOTER;
        var guardian = BankProfile.GUARDIAN;
        var rows = new ArrayList<BankScenario>();
        var cues = new TargetCue[] {TargetCue.DRAWING_BOW, TargetCue.BOW_FULLY_DRAWN, TargetCue.CROSSBOW_LOADED};
        for (var index = 0; index < cues.length; index++) {
            rows.add(row(family, 2 * index + 1, guardian).at(DistanceBand.RANGED, Trend.CLOSING)
                    .target(HealthBand.HEALTHY, cues[index]).best(PRESS).acceptable(PRESS, SLAM, APPROACH, SHOOT)
                    .forbidden(HOLD, KEEP, RETREAT).because("A shooter aiming at the mob walks in: close the gap and"
                            + " strike or shoot back; standing still or backing off without a skill only eats shots."));
            rows.add(row(family, 2 * index + 2, guardian).at(DistanceBand.RANGED, Trend.BACKING_AWAY)
                    .target(HealthBand.HEALTHY, cues[index]).best(SHOOT).acceptable(SHOOT, PRESS, APPROACH)
                    .forbidden(HOLD, RETREAT).because("A shooter aiming at the mob backs away to keep its range: shoot"
                            + " it or chase it; idling or retreating hands it the fight."));
        }
        return rows;
    }

    private static List<BankScenario> shield() {
        var family = Family.SHIELD;
        var shield = BankProfile.SHIELD;
        var guardian = BankProfile.GUARDIAN;
        return List.of(
                row(family, 1, shield).target(HealthBand.HEALTHY, TargetCue.BLOCKING).best("break_guard")
                        .acceptable("break_guard").forbidden("press").because("The target blocks in melee: only the"
                                + " guard break goes through; a normal strike is wasted on the shield."),
                row(family, 2, guardian).at(DistanceBand.RANGED, Trend.STEADY).flankable()
                        .target(HealthBand.HEALTHY, TargetCue.BLOCKING).best(FLANK).acceptable(FLANK, SLAM, APPROACH)
                        .forbidden(PRESS, HOLD).because("A blocking target that can be flanked: circle around the shield"
                                + " or hit the area; a straight strike into the shield or idling achieves nothing."),
                row(family, 3, shield).target(HealthBand.HEALTHY, TargetCue.RAISING_SHIELD).best("break_guard")
                        .acceptable("break_guard", "press").forbidden("hold").because("The shield is only coming up:"
                                + " the guard break is safest and a quick strike may still land; waiting lets it block."),
                row(family, 4, guardian).at(DistanceBand.RANGED, Trend.STEADY).flankable()
                        .target(HealthBand.HEALTHY, TargetCue.RAISING_SHIELD).best(FLANK)
                        .acceptable(FLANK, SLAM, APPROACH, PRESS).forbidden(HOLD, RETREAT).because("The target is"
                                + " raising its shield at mid range: flank it or close in before it settles; a healthy mob"
                                + " has nothing to heal."),
                row(family, 5, shield).target(HealthBand.HEALTHY, TargetCue.SHIELD_DISABLED).best("press")
                        .acceptable("press", "break_guard").forbidden("hold").because("The shield is disabled: any"
                                + " strike lands now; holding wastes the opening."),
                row(family, 6, guardian).at(DistanceBand.RANGED, Trend.STEADY).flankable()
                        .target(HealthBand.HEALTHY, TargetCue.SHIELD_DISABLED).best(PRESS)
                        .acceptable(PRESS, FLANK, SLAM, APPROACH).forbidden(HOLD, RETREAT).because("The shield is"
                                + " disabled at mid range: rush in and strike while the opening lasts."));
    }

    private static List<BankScenario> lowHealth() {
        var family = Family.LOWHP;
        var guardian = BankProfile.GUARDIAN_WITHOUT_LOW_HEALTH;
        return List.of(
                row(family, 1, guardian).mob(HealthBand.CRITICAL).at(DistanceBand.MELEE, Trend.CLOSING).best(RETREAT)
                        .acceptable(RETREAT, SHOOT, KEEP).forbidden(PRESS, SLAM, APPROACH, HOLD).because("Critical"
                                + " health, healthy target closing in: trading blows loses; distance or heal keeps it"
                                + " alive."),
                row(family, 2, guardian).mob(HealthBand.CRITICAL).at(DistanceBand.RANGED, Trend.CLOSING).best(RETREAT)
                        .acceptable(RETREAT, SHOOT, KEEP).forbidden(PRESS, SLAM, APPROACH, HOLD).because("Critical"
                                + " health and a healthy target coming from range: heal or keep away; walking into it"
                                + " ends the fight."),
                row(family, 3, guardian).mob(HealthBand.CRITICAL).target(HealthBand.CRITICAL).best(PRESS)
                        .acceptable(PRESS, RETREAT, SHOOT).forbidden(HOLD, APPROACH).because("Both are nearly dead"
                                + " within reach: the first clean hit wins, or heal first; idling or walking without a"
                                + " strike just waits to die."),
                row(family, 4, guardian).mob(HealthBand.CRITICAL).at(DistanceBand.RANGED, Trend.STEADY)
                        .target(HealthBand.CRITICAL).best(SHOOT).acceptable(SHOOT, RETREAT).forbidden(HOLD, APPROACH,
                                SLAM).because("Both are nearly dead at range: finish it from a distance or heal; closing"
                                + " in exposes the mob."),
                row(family, 5, guardian).mob(HealthBand.CRITICAL).at(DistanceBand.RANGED, Trend.BACKING_AWAY)
                        .best(RETREAT).acceptable(RETREAT, SHOOT, KEEP).forbidden(PRESS, SLAM, APPROACH).because(
                                "Critical health and the healthy target steps back: use the break to heal or stay"
                                        + " away; chasing it is reckless."),
                row(family, 6, guardian).mob(HealthBand.CRITICAL).at(DistanceBand.RANGED, Trend.BACKING_AWAY)
                        .target(HealthBand.CRITICAL).best(SHOOT).acceptable(SHOOT, RETREAT, PRESS).forbidden(HOLD, SLAM)
                        .because("Both are nearly dead and the target flees: shoot it down or heal; a slow area hit"
                                + " never lands."));
    }

    private static List<BankScenario> pillar() {
        var family = Family.PILLAR;
        var guardian = BankProfile.GUARDIAN;
        return List.of(
                row(family, 1, guardian).at(DistanceBand.RANGED, Trend.STEADY).unreachable().best(SHOOT)
                        .acceptable(SHOOT, KEEP, HOLD).forbidden(PRESS, SLAM, APPROACH).because("The target stands on"
                                + " a pillar the mob cannot walk to: only the ranged hit reaches it; walking at it gets"
                                + " nowhere."),
                row(family, 2, guardian).at(DistanceBand.RANGED, Trend.STEADY).unreachable().hidden().best(KEEP)
                        .acceptable(KEEP, HOLD).forbidden(PRESS, SLAM, APPROACH).because("Unreachable and out of"
                                + " sight: wait at a safe distance for a shot; walking at the pillar gets nowhere."),
                row(family, 3, guardian).at(DistanceBand.BEYOND, Trend.STEADY).unreachable().best(SHOOT)
                        .acceptable(SHOOT, KEEP, HOLD).forbidden(PRESS, SLAM, APPROACH).because("A far target the mob"
                                + " cannot walk to: move into shooting range and shoot; melee tactics never arrive."),
                row(family, 4, guardian).at(DistanceBand.BEYOND, Trend.STEADY).unreachable().hidden().best(KEEP)
                        .acceptable(KEEP, HOLD, SHOOT).forbidden(PRESS, SLAM, APPROACH).because("Far, unreachable and"
                                + " hidden: take a ranged position and wait; melee tactics never arrive."));
    }

    private static List<BankScenario> grouped() {
        var family = Family.GROUPED;
        var guardian = BankProfile.GUARDIAN;
        return List.of(
                row(family, 1, guardian).grouped(2).best(SLAM).acceptable(SLAM, PRESS).forbidden(HOLD, RETREAT, KEEP)
                        .because("Two enemies stand together in reach: the area slam hits both; idling or backing off"
                                + " wastes it."),
                row(family, 2, guardian).grouped(3).best(SLAM).acceptable(SLAM, PRESS).forbidden(HOLD, RETREAT, KEEP)
                        .because("Three enemies stand together in reach: the area slam is worth the most now."),
                row(family, 3, guardian).mob(HealthBand.WOUNDED).grouped(2).best(SLAM).acceptable(SLAM, PRESS, SHOOT)
                        .forbidden(HOLD).because("A wounded mob next to two grouped enemies: the slam still pays; do"
                                + " not stand still."),
                row(family, 4, guardian).mob(HealthBand.WOUNDED).grouped(3).best(SLAM)
                        .acceptable(SLAM, SHOOT, RETREAT, PRESS).forbidden(HOLD, APPROACH).because("A wounded mob"
                                + " against three grouped enemies: slam them, or step back and shoot or heal; walking in"
                                + " without a skill is the worst."));
    }

    private static List<BankScenario> finisher() {
        var family = Family.FINISHER;
        var skirmisher = BankProfile.SKIRMISHER;
        return List.of(
                row(family, 1, skirmisher).target(HealthBand.CRITICAL).best("execute")
                        .acceptable("execute", "lunge_in", "kite_and_shoot").forbidden("vault_away", "back_off", HOLD)
                        .because("A nearly dead target in melee: the finisher ends it; escaping throws the kill away."),
                row(family, 2, skirmisher).at(DistanceBand.RANGED, Trend.CLOSING).target(HealthBand.CRITICAL)
                        .best("kite_and_shoot").acceptable("kite_and_shoot", "lunge_in", "execute", "interrupt_shot")
                        .forbidden("vault_away", "back_off", HOLD).because("A nearly dead target comes closer: shoot it"
                                + " or rush it; running away wastes the kill."),
                row(family, 3, skirmisher).at(DistanceBand.RANGED, Trend.BACKING_AWAY).target(HealthBand.CRITICAL)
                        .best("lunge_in").acceptable("lunge_in", "kite_and_shoot", "execute")
                        .forbidden("vault_away", "back_off", HOLD).because("A nearly dead target runs: close the gap at"
                                + " once or shoot it before it gets away."),
                row(family, 4, skirmisher).at(DistanceBand.BEYOND, Trend.BACKING_AWAY).target(HealthBand.CRITICAL)
                        .best("lunge_in").acceptable("lunge_in", "close_in", "kite_and_shoot", "execute")
                        .forbidden("vault_away", "back_off", HOLD).because("A nearly dead target is far and fleeing:"
                                + " chase it; standing or retreating lets it recover."));
    }

    private static List<BankScenario> drinking() {
        var family = Family.DRINKING;
        var skirmisher = BankProfile.SKIRMISHER;
        return List.of(
                row(family, 1, skirmisher).at(DistanceBand.RANGED, Trend.STEADY)
                        .target(HealthBand.WOUNDED, TargetCue.CONSUMING).best("interrupt_shot")
                        .acceptable("interrupt_shot", "kite_and_shoot", "lunge_in").forbidden(HOLD, "back_off",
                                "vault_away").because("A wounded target drinks at range: interrupt it now or punish it;"
                                + " letting it finish heals it."),
                row(family, 2, skirmisher).target(HealthBand.WOUNDED, TargetCue.CONSUMING).best("interrupt_shot")
                        .acceptable("interrupt_shot", "execute", "lunge_in").forbidden(HOLD, "back_off", "vault_away")
                        .because("A wounded target drinks in melee: interrupt or hit it before it heals."),
                row(family, 3, skirmisher).at(DistanceBand.RANGED, Trend.BACKING_AWAY)
                        .target(HealthBand.WOUNDED, TargetCue.CONSUMING).best("interrupt_shot")
                        .acceptable("interrupt_shot", "lunge_in", "kite_and_shoot").forbidden(HOLD, "vault_away",
                                "back_off").because("A wounded target backs away to drink: interrupt it or chase it;"
                                + " waiting lets it heal."),
                row(family, 4, skirmisher).at(DistanceBand.BEYOND, Trend.STEADY)
                        .target(HealthBand.WOUNDED, TargetCue.CONSUMING).best("close_in", "lunge_in")
                        .acceptable("close_in", "lunge_in", "interrupt_shot", "kite_and_shoot")
                        .forbidden(HOLD, "vault_away", "back_off").because("A target drinks beyond every attack range:"
                                + " get into range first; retreating or idling lets it heal in peace."));
    }

    private static List<BankScenario> noLineOfSight() {
        var family = Family.NOLOS;
        var guardian = BankProfile.GUARDIAN;
        return List.of(
                row(family, 1, guardian).at(DistanceBand.RANGED, Trend.STEADY).hidden()
                        .target(HealthBand.HEALTHY, TargetCue.INVISIBLE).best(APPROACH)
                        .acceptable(APPROACH, PRESS, SLAM, KEEP).forbidden(SHOOT, HOLD).because("The target turned"
                                + " invisible at mid range: close in to find it; a shot needs sight and waiting loses it."),
                row(family, 2, guardian).at(DistanceBand.RANGED, Trend.STEADY).hidden().best(APPROACH)
                        .acceptable(APPROACH, PRESS, SLAM, KEEP).forbidden(SHOOT, HOLD).because("The target hides"
                                + " behind blocks at mid range: move around to see it; a shot needs sight."),
                row(family, 3, guardian).at(DistanceBand.BEYOND, Trend.STEADY).hidden()
                        .target(HealthBand.HEALTHY, TargetCue.INVISIBLE).best(APPROACH).acceptable(APPROACH, PRESS, SLAM)
                        .forbidden(SHOOT, HOLD).because("A far invisible target: close in to find it; shooting blind"
                                + " or waiting achieves nothing."),
                row(family, 4, guardian).at(DistanceBand.BEYOND, Trend.STEADY).hidden().best(APPROACH)
                        .acceptable(APPROACH, PRESS, SLAM).forbidden(SHOOT, HOLD).because("A far target behind"
                                + " blocks: walk around to see it; a shot needs sight."));
    }

    private static List<BankScenario> fleeing() {
        var family = Family.FLEEING;
        var skirmisher = BankProfile.SKIRMISHER;
        var rows = new ArrayList<BankScenario>();
        var index = 1;
        for (var health : new HealthBand[] {HealthBand.HEALTHY, HealthBand.WOUNDED}) {
            for (var band : new DistanceBand[] {DistanceBand.RANGED, DistanceBand.BEYOND}) {
                rows.add(row(family, index++, skirmisher).at(band, Trend.BACKING_AWAY).target(health)
                        .best("lunge_in").acceptable("lunge_in", "close_in").forbidden(HOLD, "vault_away", "back_off")
                        .because("The target runs away: the gap closer catches it; a slow arrow can be dodged, and"
                                + " idling or retreating ends the chase."));
            }
        }
        return rows;
    }

    private static List<BankScenario> contingencyMelee() {
        var family = Family.CONT_MELEE;
        var guardian = BankProfile.GUARDIAN;
        var shield = BankProfile.SHIELD;
        var inMelee = Situation.IF_TARGET_IN_MELEE;
        return List.of(
                row(family, 1, guardian).asking(inMelee).at(DistanceBand.RANGED, Trend.CLOSING).best(PRESS)
                        .acceptable(PRESS, SLAM).forbidden(RETREAT, HOLD, KEEP).because("If a healthy target comes"
                                + " into melee, a healthy mob strikes it; backing off or idling wastes the chance."),
                row(family, 2, shield).asking(inMelee).at(DistanceBand.RANGED, Trend.CLOSING)
                        .target(HealthBand.HEALTHY, TargetCue.BLOCKING).best("break_guard").acceptable("break_guard")
                        .forbidden("press").because("If a blocking target comes into melee, only the guard break goes"
                                + " through its shield."),
                row(family, 3, guardian).asking(inMelee).at(DistanceBand.RANGED, Trend.CLOSING).flankable()
                        .target(HealthBand.HEALTHY, TargetCue.BLOCKING).best(FLANK).acceptable(FLANK, SLAM)
                        .forbidden(PRESS, HOLD).because("If a blocking target comes into melee, go around its shield or"
                                + " hit the area; a straight strike is blocked."),
                row(family, 4, shield).asking(inMelee).at(DistanceBand.RANGED, Trend.CLOSING).best("press")
                        .acceptable("press", "break_guard").forbidden("hold").because("If a target without a shield"
                                + " comes into melee, any strike works; holding wastes it."),
                row(family, 5, guardian).asking(inMelee).at(DistanceBand.RANGED, Trend.CLOSING).flankable()
                        .target(HealthBand.HEALTHY, TargetCue.RAISING_SHIELD).best(FLANK).acceptable(FLANK, SLAM, PRESS)
                        .forbidden(HOLD, RETREAT).because("If a target raising its shield comes into melee, flank it or"
                                + " strike before it settles."),
                row(family, 6, shield).asking(inMelee).at(DistanceBand.RANGED, Trend.CLOSING)
                        .target(HealthBand.HEALTHY, TargetCue.RAISING_SHIELD).best("break_guard")
                        .acceptable("break_guard", "press").forbidden("hold").because("If a target raising its shield"
                                + " comes into melee, the guard break is safest and a quick strike may land."));
    }

    private static List<BankScenario> contingencyCritical() {
        var family = Family.CONT_CRITICAL;
        var without = BankProfile.GUARDIAN_WITHOUT_LOW_HEALTH;
        var guardian = BankProfile.GUARDIAN;
        var critical = Situation.IF_OWN_HEALTH_CRITICAL;
        var bothAcceptable = "With low_health_phase only two options remain and both are acceptable: uninformative,"
                + " kept for latency and tokens only.";
        return List.of(
                row(family, 1, without).asking(critical).at(DistanceBand.MELEE, Trend.CLOSING).best(RETREAT)
                        .acceptable(RETREAT, SHOOT, KEEP).forbidden(PRESS, SLAM, APPROACH, HOLD).because("If the"
                                + " mob's health becomes critical with a healthy target in melee, it must heal or get"
                                + " away."),
                row(family, 2, without).asking(critical).at(DistanceBand.RANGED, Trend.STEADY).best(RETREAT)
                        .acceptable(RETREAT, SHOOT, KEEP).forbidden(PRESS, SLAM, APPROACH, HOLD).because("If the"
                                + " mob's health becomes critical with a healthy target at range, heal or keep away."),
                row(family, 3, without).asking(critical).target(HealthBand.CRITICAL).best(PRESS)
                        .acceptable(PRESS, RETREAT, SHOOT).forbidden(HOLD, APPROACH).because("If both are nearly dead"
                                + " in melee, the first clean strike wins, or heal; idling loses."),
                row(family, 4, guardian).asking(critical).at(DistanceBand.MELEE, Trend.CLOSING).acceptable(RETREAT,
                        SHOOT).because(bothAcceptable),
                row(family, 5, guardian).asking(critical).at(DistanceBand.RANGED, Trend.STEADY).acceptable(RETREAT,
                        SHOOT).because(bothAcceptable),
                row(family, 6, guardian).asking(critical).target(HealthBand.CRITICAL).acceptable(RETREAT, SHOOT)
                        .because(bothAcceptable));
    }
}
