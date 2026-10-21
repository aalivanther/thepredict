# ThePredict

Tactical, predictive combat AI for MythicMobs Premium bosses on Paper 26.2.
Bosses read the player's build, timing and patterns, and choose tactics through Jev (TypeSafe) or a local planner.
Demo: https://akyrion.tech/thepredict/ · Docs: https://akyrion.tech/thepredict/docs/

## Run locally

1. Install Java 25 and put `paper-26.2-129.jar` and `MythicMobsPremium-5.13.1-SNAPSHOT.jar` in `VersionsOfJars/`.
2. `./gradlew build` compiles and runs the tests.
3. Copy `build/libs/ThePredict-0.0.1.jar` into the `plugins/` folder of a Paper 26.2 server with MythicMobs Premium.
4. Optional: set `TYPESAFE_API_KEY` (see `.env.example`) to use Jev; without it, the local planner plays.
5. Author and admin guides: `Guias/`.
