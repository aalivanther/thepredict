ThePredict AI profiles (schema 2)

Release candidate: the in-game behaviour described here is covered by automated
tests but has not been verified on a live server yet.

1. Set the TYPESAFE_API_KEY environment variable (or jev.apiKey in
   plugins/MythicMobs/AIConfig/config.yml) and restart the server: the key and
   config.yml are read only at startup.
   Without a key Jev stays unconfigured, bound mobs fight with the local planner
   of their profile and MythicMobs runs normally.
2. Copy example.yml.example to a new file ending in .yml, or copy the complete
   example from plugins/ThePredict/examples/ (see plugins/ThePredict/examples/README.txt).
3. Replace the MythicID values with your MythicMobs mob and skill IDs. IDs are
   case-sensitive; /thepredict status suggests the right spelling.
4. Add an Intention line to each referenced MythicMobs skill (author guide,
   section 9), and a native Cooldown (or CD in the choice, in ticks). A skill
   without Intention, and without Purpose in its choice, is untyped: it fires
   only as the choice of its tactic. PredictValues are no longer read.
5. Set Predictor: true on the MythicMobs mob.
6. Run /mm reload, then /thepredict status and /thepredict inspect nearest.

A profile declares Tactics (Movement plus an optional Choice), optional Contexts
written with native MythicMobs conditions, and optional Movement, Health, Threat,
BaseAI and Timing sections. Values it does not set come from profileDefaults in
config.yml. The highest-priority active context limits the tactics; idle,
attacking_phase, tracking_enemy and low_health_phase are shortcut contexts that
need no Conditions. Objective and Description should be written in English.
ThePredict generates the movement goals of a bound mob; its AIGoalSelectors
are only the base AI when the profile has no BaseAI.

A profile with an error is skipped and the others still load; every problem names
the file and the path. Schema 1 files (with Behaviors or Operations) are not loaded:
ThePredict writes a migration proposal under plugins/ThePredict/migration/ and changes nothing.

The example file is ignored until you copy it to a .yml file.

Full guides, published with ThePredict 0.0.1 (in Spanish): Guias/Guia-del-autor.md
(every profile field, movement, the Intention line, the predictor* vocabulary and
the enriched threat) and Guias/Guia-del-administrador.md (AIConfig/config.yml, commands,
permissions, costs and diagnostics).
