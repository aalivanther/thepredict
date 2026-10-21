package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.ParticipantRules;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Which living entities the mobs of one profile may perceive, target and fight (M17.2), compiled when the profile binds
 * and kept in its {@code BoundProfile}. {@link #admits} decides in this order, the first rule that decides wins:
 * <ol>
 * <li>an invalid or dead entity is not admitted;</li>
 * <li>the mob itself and the members of its encounter (its allies) are not;</li>
 * <li>a player is admitted when its game mode is in the list of the profile, and nothing else applies to players, so
 * the defaults never change;</li>
 * <li>an armor stand is not;</li>
 * <li>an entity matching a {@code deny} selector (global or of the profile) is not;</li>
 * <li>an entity of the same side as the mob ({@link SideReader}) is not;</li>
 * <li>an entity matching an {@code allow} selector (global, and of the profile unless {@code profilesMayAllow} is
 * false) is admitted; any other is not.</li>
 * </ol>
 * Without any allowed selector a non-player is refused before any read, which is the result of rule 7: the default
 * policy costs what the eligibility check of 0.0.1 cost and reads nothing of a non-player (rules A5, C12). Immutable;
 * server thread only.
 */
public final class ParticipantPolicy {
    /**
     * The policy of 0.0.1, derived from {@link ParticipantRules#DEFAULT} and {@link ParticipantDefaults#STANDARD}
     * (rule C13): players in {@code SURVIVAL} or {@code ADVENTURE}, no other entity.
     */
    public static final ParticipantPolicy DEFAULT = new ParticipantPolicy(gameModes(ParticipantRules.DEFAULT),
            List.of(), List.of(), ParticipantRules.DEFAULT.perceiveNonTargets(),
            ParticipantDefaults.STANDARD.maxCandidatesPerMember());

    private final Set<GameMode> playerGameModes;
    private final List<ParticipantSelectors> allow;
    private final List<ParticipantSelectors> deny;
    private final boolean perceiveNonTargets;
    private final int maxCandidatesPerMember;

    private ParticipantPolicy(Set<GameMode> playerGameModes, List<ParticipantSelectors> allow,
                              List<ParticipantSelectors> deny, boolean perceiveNonTargets, int maxCandidatesPerMember) {
        this.playerGameModes = playerGameModes.isEmpty() ? EnumSet.noneOf(GameMode.class)
                : EnumSet.copyOf(playerGameModes);
        this.allow = allow.stream().filter(selectors -> !selectors.isEmpty()).toList();
        this.deny = deny.stream().filter(selectors -> !selectors.isEmpty()).toList();
        this.perceiveNonTargets = perceiveNonTargets;
        this.maxCandidatesPerMember = maxCandidatesPerMember;
    }

    /**
     * Compiles the policy of one profile.
     *
     * @param defaults global settings, whose rules add to those of the profile
     * @param global global selectors, compiled once per binding with their paths under {@code participants}
     * @param profile rules of the profile, already resolved against the global ones by {@code config}
     * @param registry entity types, tags and teams of the server
     * @param warnings receives the path under {@code Participants} and the reason of each selector of the profile that
     *                 resolves to nothing
     * @return the policy
     */
    public static ParticipantPolicy compile(ParticipantDefaults defaults, GlobalSelectors global,
                                            ParticipantRules profile, SelectorRegistry registry,
                                            BiConsumer<String, String> warnings) {
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(global, "global");
        var modes = gameModes(profile);
        var allow = new ArrayList<ParticipantSelectors>(List.of(global.allow()));
        if (defaults.profilesMayAllow()) {
            allow.add(ParticipantSelectors.compile(profile.entityAllow(), paths("Participants.Entities.Allow",
                    profile.entityAllow().size()), registry, warnings));
        }
        var deny = List.of(global.deny(), ParticipantSelectors.compile(profile.entityDeny(),
                paths("Participants.Entities.Deny", profile.entityDeny().size()), registry, warnings));
        return new ParticipantPolicy(modes, allow, deny, profile.perceiveNonTargets(),
                defaults.maxCandidatesPerMember());
    }

    private static Set<GameMode> gameModes(ParticipantRules rules) {
        var modes = EnumSet.noneOf(GameMode.class);
        rules.playerGameModes().forEach(mode -> modes.add(GameMode.valueOf(mode)));
        return modes;
    }

    /**
     * Paths of the selectors of one list, for the warnings.
     *
     * @param list path of the list
     * @param size number of selectors
     * @return {@code list[0]} to {@code list[size - 1]}
     */
    public static List<String> paths(String list, int size) {
        var paths = new ArrayList<String>(size);
        for (var index = 0; index < size; index++) {
            paths.add(list + "[" + index + "]");
        }
        return paths;
    }

    /**
     * Decides whether an entity may be perceived, targeted and fought by a mob, with the rules of the class.
     *
     * @param candidate the entity
     * @param mob the mob that would perceive it
     * @param members UUIDs of the members of the mob's encounter; empty when there is none
     * @param lookup native identities, read only for a non-player past the cheap rules
     * @return {@code true} if the entity is a possible participant
     */
    public boolean admits(LivingEntity candidate, LivingEntity mob, Set<UUID> members, ParticipantLookup lookup) {
        var player = candidate instanceof Player;
        if (!player && allow.isEmpty() || !candidate.isValid() || candidate.isDead()) {
            return false;
        }
        var id = candidate.getUniqueId();
        if (id.equals(mob.getUniqueId()) || members.contains(id)) {
            return false;
        }
        if (candidate instanceof Player human) {
            return playerGameModes.contains(human.getGameMode());
        }
        if (candidate instanceof ArmorStand || denied(candidate, lookup)
                || SideReader.sameSide(candidate, mob, lookup)) {
            return false;
        }
        for (var selectors : allow) {
            if (selectors.matches(candidate, lookup)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cheap test for the predicate of {@code World#getNearbyLivingEntities}: players pass (their game mode is read
     * later), and a non-player passes unless an armor stand, a cheap {@code deny} selector or the lack of any possible
     * {@code allow} rules it out. {@link #admits} still decides.
     *
     * @param candidate the entity
     * @return {@code false} only for an entity that {@link #admits} would refuse
     */
    public boolean admitsCheap(LivingEntity candidate) {
        if (candidate instanceof Player) {
            return true;
        }
        if (allow.isEmpty() || candidate instanceof ArmorStand) {
            return false;
        }
        for (var selectors : deny) {
            if (selectors.matchesCheap(candidate)) {
                return false;
            }
        }
        for (var selectors : allow) {
            if (selectors.needsLookup() || selectors.matchesCheap(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether any non-player entity can be admitted.
     *
     * @return {@code true} with at least one resolved {@code allow} selector
     */
    public boolean admitsEntities() {
        return !allow.isEmpty();
    }

    /**
     * Tells whether the mobs perceive entities other than their targets: only when entities can be admitted and
     * {@code perceiveNonTargets} is on; otherwise the capture uses {@code getNearbyPlayers} as in 0.0.1.
     *
     * @return {@code true} when a capture queries the nearby living entities
     */
    public boolean perceivesEntities() {
        return perceiveNonTargets && admitsEntities();
    }

    /**
     * Returns the cap of non-target entities kept per member and capture.
     *
     * @return 1 to 16
     */
    public int maxCandidatesPerMember() {
        return maxCandidatesPerMember;
    }

    /**
     * The kind of a participant.
     *
     * @param entity the participant
     * @return {@code PLAYER} for a player, {@code ENTITY} for any other living entity
     */
    public static ParticipantKind kind(LivingEntity entity) {
        return entity instanceof Player ? ParticipantKind.PLAYER : ParticipantKind.ENTITY;
    }

    private boolean denied(LivingEntity candidate, ParticipantLookup lookup) {
        for (var selectors : deny) {
            if (selectors.matches(candidate, lookup)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The global selectors of {@code participants.entities}, compiled once per binding.
     *
     * @param allow compiled {@code participants.entities.allow}
     * @param deny compiled {@code participants.entities.deny}
     */
    public record GlobalSelectors(ParticipantSelectors allow, ParticipantSelectors deny) {
        /** No global selector. */
        public static final GlobalSelectors NONE = new GlobalSelectors(ParticipantSelectors.NONE,
                ParticipantSelectors.NONE);

        /**
         * Validates the components.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public GlobalSelectors {
            Objects.requireNonNull(allow, "allow");
            Objects.requireNonNull(deny, "deny");
        }

        /**
         * Compiles the global selectors.
         *
         * @param defaults global settings
         * @param registry entity types, tags and teams of the server
         * @param warnings receives the path under {@code participants} and the reason of each unresolved selector
         * @return the compiled selectors
         */
        public static GlobalSelectors compile(ParticipantDefaults defaults, SelectorRegistry registry,
                                              BiConsumer<String, String> warnings) {
            var rules = defaults.global();
            return new GlobalSelectors(ParticipantSelectors.compile(rules.entityAllow(),
                    paths("participants.entities.allow", rules.entityAllow().size()), registry, warnings),
                    ParticipantSelectors.compile(rules.entityDeny(), paths("participants.entities.deny",
                            rules.entityDeny().size()), registry, warnings));
        }
    }
}
