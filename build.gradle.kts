import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    java
}

version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.compileJava {
    options.release.set(25)
}
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly(files("VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar"))
    compileOnly("com.google.code.gson:gson:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation(files("VersionsOfJars/MythicMobsPremium-5.13.1-SNAPSHOT.jar"))
    testImplementation("com.google.code.gson:gson:2.14.0")
    compileOnly("org.xerial:sqlite-jdbc:3.49.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
}

tasks.test {
    useJUnitPlatform()
    inputs.dir("Guias")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
    }
}

val projectVersion = version.toString()
val testClassDirectories = sourceSets.test.map { it.output.classesDirs }

val verifyPluginJar = tasks.register("verifyPluginJar") {
    dependsOn(tasks.jar, tasks.testClasses)
    val pluginJar = tasks.jar.flatMap { it.archiveFile }
    val testClasses = files(testClassDirectories)
    inputs.file(pluginJar)
    inputs.files(testClasses)
    val expectedVersion = projectVersion
    inputs.property("projectVersion", expectedVersion)
    doLast {
        val archive = pluginJar.get().asFile
        val testClassNames = testClasses.files.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }
                .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        }.toSet()
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val required = setOf(
                "plugin.yml",
                "AIConfig/config.yml",
                "storage/sqlite/V001__base.sql",
                "AIConfig/Profiles/README.txt",
                "AIConfig/Profiles/example.yml.example",
                "examples/README.txt",
                "examples/AI/ThePredictGuardian.yml",
                "examples/MythicMobs/Mobs/ThePredictGuardian.yml",
                "examples/MythicMobs/Skills/ThePredictGuardianSkills.yml",
                "migration/report-template.md",
                "difficulty/ladder-v5.json",
                "storage/sqlite/V002__m22_eval.sql",
                "storage/sqlite/V003__m26_patterns.sql",
                "akyrion/thepredict/ThePredictPlugin.class"
            )
            check(entries.containsAll(required)) { "Plugin JAR is missing required resources: ${required - entries}" }
            check(entries.none { it.startsWith("io/lumine/") || it.startsWith("org/bukkit/")
                    || it.startsWith("com/google/") || it.startsWith("org/sqlite/")
                    || it.startsWith("com/mysql/") || it.endsWith(".jar") }) {
                "Plugin JAR contains a bundled server, MythicMobs, or third-party dependency"
            }
            val retiredPackages = listOf("akyrion/thepredict/adapter/openrouter/", "akyrion/thepredict/decision/",
                "akyrion/thepredict/snapshot/")
            check(entries.none { entry -> retiredPackages.any { entry.startsWith(it) } }) {
                "Plugin JAR contains a package retired in M12: ${entries.filter { entry -> retiredPackages.any { entry.startsWith(it) } }}"
            }
            val textWithRetiredProvider = entries.filter { !it.endsWith("/") && !it.endsWith(".class") }
                .filter { it != "migration/report-template.md" }
                .filter { entry -> zip.getInputStream(zip.getEntry(entry)).bufferedReader().use { it.readText() }
                    .contains("openrouter", ignoreCase = true) }
            check(textWithRetiredProvider.isEmpty()) {
                "Plugin JAR resources still mention the retired provider outside the migration report: $textWithRetiredProvider"
            }
            val configuration = zip.getInputStream(zip.getEntry("AIConfig/config.yml")).bufferedReader().use { it.readText() }
            check(configuration.lineSequence().any { it.trim() == "apiKey: ''" }) {
                "Bundled config.yml must contain an empty API key"
            }
            check(configuration.lineSequence().any { it.trim() == "schemaVersion: 2" }) {
                "Bundled config.yml must use schema 2"
            }
            check(configuration.lineSequence().none { line ->
                line.isNotEmpty() && !line[0].isWhitespace() && line.trim().startsWith("provider:")
            }) {
                "Bundled config.yml must not contain a top-level schema 1 provider key"
            }
            val descriptor = zip.getInputStream(zip.getEntry("plugin.yml")).bufferedReader().use { it.readText() }
            val declaredVersion = descriptor.lineSequence().map { it.trim() }.filter { it.startsWith("version:") }
                .map { it.removePrefix("version:").trim().trim('\'', '"') }.toList()
            check(declaredVersion == listOf(expectedVersion)) {
                "plugin.yml version $declaredVersion must be the project version $expectedVersion"
            }
            val testOnly = entries.filter { it in testClassNames || Regex(
                "(^|/)(LiveJev[A-Za-z]*|LiveScenario[A-Za-z]*|MeasurementReport|[A-Za-z0-9]*Measurements|[A-Za-z]*Test|"
                        + "[A-Za-z]*Fixtures?)(\\$[A-Za-z0-9_$]*)?\\.class$").containsMatchIn(it) }
            check(testOnly.isEmpty()) { "Plugin JAR contains test, live or measurement classes: $testOnly" }
            val secretPatterns = listOf(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
                Regex("TYPESAFE_API_KEY\\s*=\\s*\\S"), Regex("(?i)apiKey:\\s*['\"]?[^'\"\\s#]"))
            val secrets = entries.filter { it.substringAfterLast('/').startsWith(".env") } + entries
                .filter { !it.endsWith("/") && !it.endsWith(".class") }
                .filter { entry -> zip.getInputStream(zip.getEntry(entry)).bufferedReader().use { it.readText() }
                    .let { text -> secretPatterns.any { pattern -> pattern.containsMatchIn(text) } } }
            check(secrets.isEmpty()) { "Plugin JAR contains a secret or an environment file: $secrets" }
            val pluginClass = zip.getInputStream(zip.getEntry("akyrion/thepredict/ThePredictPlugin.class"))
                .use { it.readNBytes(8) }
            val majorVersion = (pluginClass[6].toInt() and 0xff) shl 8 or (pluginClass[7].toInt() and 0xff)
            check(majorVersion == 69) { "Plugin JAR must target Java 25" }
            val ladderBytes = zip.getInputStream(zip.getEntry("difficulty/ladder-v5.json")).use { it.readBytes() }
            val ladderHex = MessageDigest.getInstance("SHA-256").digest(ladderBytes)
                .joinToString("") { byte -> "%02x".format(byte) }
            check(ladderHex == "c4665ef7e89d59e032ae3294681338a5dbc9b2b5ef2c3d2faef3dd73f1d7bdb0") {
                "difficulty/ladder-v5.json fingerprint $ladderHex"
            }
            val ladderConstant = zip.getInputStream(zip.getEntry(
                "akyrion/thepredict/domain/PressureLadder.class")).use { it.readBytes() }
            check(String(ladderConstant, Charsets.ISO_8859_1).contains(
                "c4665ef7e89d59e032ae3294681338a5dbc9b2b5ef2c3d2faef3dd73f1d7bdb0")) {
                "PressureLadder must embed the ladder fingerprint"
            }
            val packagedJev = entries.filter { it.startsWith("learn/packaged/") && !it.endsWith("/") }
                .filter { entry ->
                    val text = zip.getInputStream(zip.getEntry(entry)).bufferedReader().use { it.readText() }
                    text.contains("JEV_ARGMAX") || text.contains("\"jev")
                            || Regex("(?i)source\\s*[:=]\\s*['\"]?jev").containsMatchIn(text)
                }
            check(packagedJev.isEmpty()) { "Plugin JAR must not package a Jev source: $packagedJev" }
        }
    }
}

tasks.check {
    dependsOn(verifyPluginJar)
}

val testRuntime = sourceSets.test.map { it.runtimeClasspath }
val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }

tasks.register<JavaExec>("m21Measure") {
    group = "verification"
    description = "M21 volume and baseline. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.storage.M21Measurements")
    javaLauncher.set(java25)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(layout.buildDirectory.dir("reports/m21").get().asFile.absolutePath)
}

tasks.register<JavaExec>("recordsReport") {
    group = "verification"
    description = "Reads a records directory. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.storage.RecordsReport")
    javaLauncher.set(java25)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("recordsToScenarios") {
    group = "verification"
    description = "Builds scenarios from records. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.storage.RecordsToScenarios")
    javaLauncher.set(java25)
}

tasks.register<JavaExec>("recordsBodyRepeat") {
    group = "verification"
    description = "Body repeat from body_sha256. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.storage.RecordsBodyRepeat")
    javaLauncher.set(java25)
}

fun JavaExec.evalTool(kind: String) {
    group = "verification"
    description = "M22 offline evaluation ($kind). Not part of build. LIVE is refused without -PliveJev=true."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.eval.EvalTools")
    javaLauncher.set(java25)
    args(kind, layout.buildDirectory.dir("reports/m22").get().asFile.absolutePath)
}

tasks.register<JavaExec>("evalBaseline") { evalTool("baseline") }
tasks.register<JavaExec>("evalRun") { evalTool("run") }
tasks.register<JavaExec>("evalGate") { evalTool("gate") }
tasks.register<JavaExec>("evalLadder") { evalTool("ladder") }
tasks.register<JavaExec>("evalTuneClassic") { evalTool("tune") }
tasks.register<JavaExec>("evalRating") { evalTool("rating") }
tasks.register<JavaExec>("evalReport") { evalTool("report") }

tasks.register<JavaExec>("evalMemory") {
    group = "verification"
    description = "M27 campaign evaluation. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.eval.MemoryEval")
    javaLauncher.set(java25)
    args(layout.buildDirectory.dir("reports/m27").get().asFile.absolutePath)
}

tasks.register<JavaExec>("recordsMemoryReport") {
    group = "verification"
    description = "M27 memory record report. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.eval.RecordsMemoryReport")
    javaLauncher.set(java25)
    args("report", layout.buildDirectory.dir("reports/m27").get().asFile.absolutePath)
}

tasks.register<JavaExec>("learnBaseline") {
    group = "verification"
    description = "M28 X1 and X2 on the frozen bank. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.learn.LearnBaselineMain")
    javaLauncher.set(java25)
}

tasks.register<JavaExec>("learnReport") {
    group = "verification"
    description = "M28 read-only cell count. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.learn.LearnReportMain")
    javaLauncher.set(java25)
}

tasks.register<JavaExec>("m29Measure") {
    group = "verification"
    description = "M29 local distill timings. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.M29Measurements")
    javaLauncher.set(java25)
}

tasks.register<JavaExec>("distillTrain") {
    group = "verification"
    description = "Builds a distilled artifact from outcome, rollout or kev labels. Never jev. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.distill.DistillTrainMain")
    javaLauncher.set(java25)
    val dagger = providers.gradleProperty("dagger").orElse("off")
    systemProperty("thepredict.dagger", dagger)
    doFirst {
        val mode = dagger.get()
        if (mode != "off" && mode != "kev" && mode != "rollout") {
            throw GradleException("distillTrain dagger is off, kev or rollout")
        }
    }
}

tasks.register<JavaExec>("evalTape") {
    group = "verification"
    description = "Champion tape. Refuses -PliveJev=true. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.distill.EvalTapeMain")
    javaLauncher.set(java25)
    val paidTape = providers.gradleProperty("liveJev").map { it == "true" }.orElse(false)
    systemProperty("thepredict.liveJev", paidTape.get().toString())
    doFirst {
        if (paidTape.get()) {
            throw GradleException("evalTape refuses -PliveJev=true")
        }
    }
}

tasks.register<JavaExec>("autoconfigSearch") {
    group = "verification"
    description = "Autoconfig search notes. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.distill.AutoconfigSearchMain")
    javaLauncher.set(java25)
}

tasks.register<JavaExec>("distillBaseline") {
    group = "verification"
    description = "Points at the Y1 test. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.distill.DistillTrainMain")
    javaLauncher.set(java25)
    systemProperty("thepredict.dagger", "off")
}

tasks.register<JavaExec>("distillReport") {
    group = "verification"
    description = "M29 measurement report. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.M29Measurements")
    javaLauncher.set(java25)
}

tasks.register<JavaExec>("m13Measure") {
    group = "verification"
    description = "M13 local measurements (load, work budget, A/B harnesses, Jev path on loopback). Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.M13Measurements")
    javaLauncher.set(java25)
    maxHeapSize = "2g"
    args(layout.buildDirectory.file("reports/m13/measurements.md").get().asFile.absolutePath)
}

val liveJev = providers.gradleProperty("liveJev").map { it == "true" }.orElse(false)
val liveProperties = listOf("calls", "spacingSeconds", "repetitions", "maxCalls", "maxUsd", "scenarioDir", "arms",
    "scenarios", "model", "baseUrl").associateWith { providers.gradleProperty(it) }

fun JavaExec.liveTool(tool: String, report: String, folder: String = "m13") {
    group = "verification"
    description = "PAID: calls the real TypeSafe account. Needs -PliveJev=true and TYPESAFE_API_KEY in the environment."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.jev.$tool")
    javaLauncher.set(java25)
    args(layout.buildDirectory.file("reports/$folder/$report").get().asFile.absolutePath)
    systemProperty("thepredict.liveJev", liveJev.get().toString())
    liveProperties.forEach { (name, value) -> value.orNull?.let { systemProperty("thepredict.live.$name", it) } }
    val enabled = liveJev
    doFirst {
        if (!enabled.get()) {
            throw GradleException("$name calls the paid TypeSafe account and runs only with -PliveJev=true")
        }
    }
}

tasks.register<JavaExec>("liveJevCheck") {
    liveTool("LiveJevCheck", "live-check.md")
}

tasks.register<JavaExec>("liveScenarioBench") {
    liveTool("LiveScenarioBench", "live-scenario-bench.md", "m16")
}

tasks.register<JavaExec>("labelReviewSheet") {
    group = "verification"
    description = "M16.9: writes build/reports/m16/label-review.md for the label review. No network, no cost."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.jev.LabelReviewSheet")
    javaLauncher.set(java25)
    args(layout.buildDirectory.file("reports/m16/label-review.md").get().asFile.absolutePath)
}

val scenarioBankTest = tasks.register<Test>("scenarioBankTest") {
    group = "verification"
    description = "M16.9: checks the labelled bank after a label review."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("akyrion.thepredict.adapter.jev.ScenarioBankTest") }
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("applyLabelReview") {
    group = "verification"
    description = "M16.9: applies src/test/resources/scenarios/bank/label-review.yml to the bank. No network, no cost."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.jev.LabelReviewApply")
    javaLauncher.set(java25)
    workingDir = projectDir
    finalizedBy(scenarioBankTest)
}

tasks.register<JavaExec>("m17Goldens") {
    group = "verification"
    description = "M17.0: writes the golden references under src/test/resources/golden/m17. No network. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.golden.GoldenRecorder")
    javaLauncher.set(java25)
    workingDir = projectDir
    maxHeapSize = "2g"
    args(layout.projectDirectory.dir("src/test/resources/golden/m17").asFile.absolutePath)
}

tasks.register<JavaExec>("reanalyseBank") {
    group = "verification"
    description = "M16.10: recomputes a re-bench report from its answers.jsonl with the current labels. No network, no cost."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.jev.BankReanalysis")
    javaLauncher.set(java25)
    workingDir = projectDir
    val answers = providers.gradleProperty("answersFile")
        .orElse("DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/m16-rebanco-2026-09-30/P2/answers.jsonl")
    args(answers.get(), layout.buildDirectory.file("reports/m16/recomputed.md").get().asFile.absolutePath)
}

tasks.register<JavaExec>("pruningBench") {
    group = "verification"
    description = "M19.9 E1: bank pruning report. No network, no cost. Not part of build."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.jev.BankPruningReport")
    javaLauncher.set(java25)
    workingDir = projectDir
    args(layout.projectDirectory.file(
        "DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/m19-evaluacion-2026-10-06/e1-pruning.md")
        .asFile.absolutePath)
}

tasks.register<JavaExec>("m20Evidence") {
    group = "verification"
    description = "M20 E1 and E2 duel evidence. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.engine.duel.M20Evidence")
    javaLauncher.set(java25)
    workingDir = projectDir
    maxHeapSize = "2g"
    args(layout.buildDirectory.file("reports/m20/duel-tick.md").get().asFile.absolutePath)
}

tasks.register<JavaExec>("m20Tick") {
    group = "verification"
    description = "M20 E3 tick-layer table. Not part of build. No network."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.M20Measurements")
    javaLauncher.set(java25)
    workingDir = projectDir
    maxHeapSize = "2g"
    args(layout.buildDirectory.file("reports/m20/tick-pass.md").get().asFile.absolutePath)
}

tasks.register<JavaExec>("duelMeasure") {
    group = "verification"
    description = "M18.7 writes build/reports/m18/duel-local.md. -PduelEval=m19 writes the M19.9 E2 matrix. No network, no cost."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.engine.duel.DuelMeasure")
    javaLauncher.set(java25)
    workingDir = projectDir
    maxHeapSize = "2g"
    val evaluation = providers.gradleProperty("duelEval").orElse("m18").get()
    systemProperty("thepredict.duel.evaluation", evaluation)
    providers.gradleProperty("rounds").orNull?.let { systemProperty("thepredict.duel.rounds", it) }
    val report = if (evaluation == "m19") {
        layout.projectDirectory.file(
            "DocumentacionParaDesarrollar/Implementacion-0.0.1/evidencia/m19-evaluacion-2026-10-06/e2-duel-matrix.md")
            .asFile.absolutePath
    } else {
        layout.buildDirectory.file("reports/m18/duel-local.md").get().asFile.absolutePath
    }
    args(report)
}

tasks.register<JavaExec>("effectivenessBench") {
    group = "verification"
    description = "Banco de efectividad v2 (auditoría 2026-10-14, §7): build/reports/efectividad/banco.md y sus .tsv. " +
        "Fuera de check. Sin red, sin coste. -PfirstSeed=dev juega las celdas con las semillas de desarrollo."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.engine.duel.EffectivenessBench")
    javaLauncher.set(java25)
    workingDir = projectDir
    maxHeapSize = "3g"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    listOf("seeds", "jevSeeds", "threads", "search", "freeze", "firstSeed").forEach { name ->
        providers.gradleProperty(name).orNull?.let { systemProperty("thepredict.bench.$name", it) }
    }
    args(layout.buildDirectory.dir("reports/efectividad").get().asFile.absolutePath)
}

tasks.register<JavaExec>("duelJoin") {
    group = "verification"
    description = "M18.4: joins the lines of a duel decisions.jsonl by (encounter, sequence). No network, no cost."
    classpath = files(testRuntime)
    mainClass.set("akyrion.thepredict.adapter.server.duel.DuelJoin")
    javaLauncher.set(java25)
    workingDir = projectDir
    val decisions = providers.gradleProperty("decisions").orElse("decisions.jsonl")
    args(decisions.get(), layout.buildDirectory.file("reports/m18/decisions-joined.json").get().asFile.absolutePath)
}
