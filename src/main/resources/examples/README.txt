ThePredict Guardian example (schema 2)

Release candidate: the in-game behaviour described here is covered by automated
tests but has not been verified on a live server yet.

The files in this folder stay inactive here. Copy them to use the example.

1. Copy MythicMobs/Mobs/ThePredictGuardian.yml into plugins/MythicMobs/Mobs/.
2. Copy MythicMobs/Skills/ThePredictGuardianSkills.yml into plugins/MythicMobs/Skills/.
3. Copy AI/ThePredictGuardian.yml into plugins/MythicMobs/AIConfig/Profiles/.
4. Run /mm reload, then /thepredict status. The mob still has Predictor: false,
   so the profile is read but not active. On a fresh installation the Catalog
   section shows (the revision number varies):
   Profiles: SETUP_PENDING (revision N, 1 profiles read, 0 active, 0 errors, 0 warnings): no mob targeted by a profile has Predictor: true; set it in MythicMobs and reload MythicMobs
5. Spawn it with /mm m spawn ThePredictGuardian 1. With Predictor: false it is
   a plain MythicMobs zombie: it chases and hits you with its own attack.
6. Set Predictor: true in the copied mob file, run /mm reload and then
   /thepredict status. The Catalog section now shows:
   Profiles: LOADED (revision N, 1 profiles read, 1 active, 0 errors, 0 warnings)
7. Fight it in survival and run /thepredict inspect nearest (within 16 blocks).

What to observe with inspect:
- Goals: the goal set ThePredict generated from the profile, for example float,
  fleeConditional, moveWithinConditional, moveTowardsTargetConditional,
  goToSpawnLocation, meleeAttack, randomStroll, lookAtTarget and the goals
  thepredict:flank, thepredict:hold and thepredict:owner.
- Session: the level. Without a Jev key the mob fights at level LOCAL: the
  profile chooses by context and priority, at no cost. With a key and the
  service READY, answers of Jev put it at level JEV.
- Intent: the tactic, its movement, the ticks left and whether its skill was
  tried. The tactics are approach, press_and_strike, keep_distance,
  keep_distance_and_shoot, retreat_and_defend, flank_and_strike,
  close_in_and_slam and hold.
- Why: the last fallback reason and the last cast result explain a level
  LOCAL or BASE.
- Threat: the native threat table (Modules.ThreatTable) and the threat
  ThePredict added for assists, blocked hits and ranged harass.

To try one tactic without Jev, a player with thepredict.debug (or the console,
with the mob UUID) can run /thepredict debug intent nearest keep_distance and
/thepredict debug release nearest.

Each skill of the example declares its Intention line: what it is for, how it
reaches the target, its damage, when it works best and its risks; the profile
declares no Purpose. ThePredict reads the reach, the radius and the wind-up
from the skill itself (targetwithin, the r of the targeter and the delay),
describes every option to Jev with them and waits for the reach before casting. The ranged skill needs a line of sight and a
target within 16 blocks; the defense does not heal the mob at full health. The
slam (close_in_and_slam) hits every player within 3 blocks of the guardian and
throws them back; it targets @PlayersInRadius, so it never hurts other mobs:
the guardian is meant to fight players. Remove the example with
/mm reload after deleting the three copied files.

The author guide published with ThePredict 0.0.1, Guias/Guia-del-autor.md (in
Spanish), walks through this same example and explains every field it uses.
