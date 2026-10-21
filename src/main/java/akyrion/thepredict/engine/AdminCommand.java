package akyrion.thepredict.engine;

import java.util.List;
import java.util.Objects;

/**
 * A command the server thread hands to storage (M21.9). Only the variants of M21. Sealed so later modules can add one.
 */
public sealed interface AdminCommand
        permits AdminCommand.Export, AdminCommand.Prune, AdminCommand.Verify, AdminCommand.Backup,
        AdminCommand.Flush, AdminCommand.CacheClear, AdminCommand.Tag, AdminCommand.EvalReport,
        AdminCommand.VerdictImport, AdminCommand.MemoryStats, AdminCommand.MemoryShow, AdminCommand.MemoryReset,
        AdminCommand.MemoryPurge, AdminCommand.MemoryExport, AdminCommand.MemoryImport,
        AdminCommand.LearnBuild, AdminCommand.LearnExport, AdminCommand.LearnImport, AdminCommand.LearnRollback,
        AdminCommand.LearnDisable, AdminCommand.DistillModeSet, AdminCommand.DistillDisable, AdminCommand.DistillBuild,
        AdminCommand.DistillExport, AdminCommand.DistillImport, AdminCommand.DistillRollback,
        AdminCommand.AutoconfigImport, AdminCommand.AutoconfigApply, AdminCommand.AutoconfigShow,
        AdminCommand.AutoconfigRevert {
    /**
     * Exports outcomes or events.
     *
     * @param kind {@code outcomes} or {@code events}
     * @param argument profile, or {@code from to types}
     */
    record Export(String kind, String argument) implements AdminCommand {
        /**
         * Validates the export.
         *
         * @throws NullPointerException if a component is {@code null}
         */
        public Export {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(argument, "argument");
        }
    }

    /**
     * Deletes record days older than a number of days.
     *
     * @param days days to keep
     */
    record Prune(int days) implements AdminCommand {
    }

    /** Runs {@code integrity_check}. */
    record Verify() implements AdminCommand {
    }

    /** Copies the database. */
    record Backup() implements AdminCommand {
    }

    /** Asks the cache to build a batch on the next tick. */
    record Flush() implements AdminCommand {
    }

    /** Drops the in-memory answer cache. */
    record CacheClear() implements AdminCommand {
    }

    /**
     * Sets or clears the record tag.
     *
     * @param tag the tag, or empty to clear
     */
    record Tag(String tag) implements AdminCommand {
        /**
         * Validates the tag.
         *
         * @throws NullPointerException if the tag is {@code null}
         */
        public Tag {
            Objects.requireNonNull(tag, "tag");
        }
    }

    /**
     * Parses the words that follow {@code records}.
     *
     * @param words the words
     * @return the command
     * @throws IllegalArgumentException if the words are not a command
     */
    static AdminCommand parse(List<String> words) {
        if (words.isEmpty()) {
            throw new IllegalArgumentException("usage");
        }
        return switch (words.getFirst()) {
            case "export" -> export(words);
            case "prune" -> new Prune(words.size() < 2 ? 14 : Integer.parseInt(words.get(1)));
            case "verify" -> new Verify();
            case "backup" -> new Backup();
            case "flush" -> new Flush();
            case "cache" -> {
                if (words.size() == 2 && words.get(1).equals("clear")) {
                    yield new CacheClear();
                }
                throw new IllegalArgumentException("usage");
            }
            case "tag" -> tag(words);
            default -> throw new IllegalArgumentException("usage");
        };
    }

    private static AdminCommand export(List<String> words) {
        if (words.size() < 2) {
            throw new IllegalArgumentException("usage");
        }
        if (words.get(1).equals("outcomes")) {
            return new Export("outcomes", words.size() > 2 ? words.get(2) : "");
        }
        if (words.get(1).equals("events") && words.size() >= 4) {
            return new Export("events", String.join(" ", words.subList(2, words.size())));
        }
        throw new IllegalArgumentException("usage");
    }

    private static AdminCommand tag(List<String> words) {
        if (words.size() == 2 && words.get(1).equals("clear")) {
            return new Tag("");
        }
        if (words.size() == 2 && words.get(1).matches("[a-z0-9-]{1,40}")) {
            return new Tag(words.get(1));
        }
        throw new IllegalArgumentException("tag");
    }

    /**
     * Asks for an evaluation report. The writer answers in chunks.
     *
     * @param tag tag filter, empty for all
     * @param since day {@code yyyy-MM-dd}, empty for all
     * @param format {@code md}, {@code jsonl} or {@code html}
     */
    record EvalReport(String tag, String since, String format) implements AdminCommand {
        /**
         * Validates the report.
         */
        public EvalReport {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(since, "since");
            Objects.requireNonNull(format, "format");
        }
    }

    /**
     * Imports one verdict file.
     *
     * @param path path inside the plugin folder, with no {@code ..}
     */
    record VerdictImport(String path) implements AdminCommand {
        /**
         * Rejects a path that climbs out of the folder.
         */
        public VerdictImport {
            Objects.requireNonNull(path, "path");
            if (path.contains("..")) {
                throw new IllegalArgumentException("path");
            }
        }
    }

    /** Counts rows of player memory. */
    record MemoryStats() implements AdminCommand {
    }

    /**
     * Shows one subject's record to an administrator.
     *
     * @param subject UUID text
     */
    record MemoryShow(String subject) implements AdminCommand {
        /**
         * Validates the subject.
         */
        public MemoryShow {
            Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * Deletes one subject's row, cache entry and pending digests.
     *
     * @param subject UUID text
     */
    record MemoryReset(String subject) implements AdminCommand {
        /**
         * Validates the subject.
         */
        public MemoryReset {
            Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * Deletes rows older than a number of days, in chunks.
     *
     * @param days days to keep
     */
    record MemoryPurge(int days) implements AdminCommand {
    }

    /**
     * Exports records as JSONL. {@code all} exports every row. A UUID exports one player.
     *
     * @param selector {@code all} or a UUID
     */
    record MemoryExport(String selector) implements AdminCommand {
        /**
         * Exports every row.
         */
        public MemoryExport() {
            this("all");
        }

        /**
         * Requires a selector.
         */
        public MemoryExport {
            Objects.requireNonNull(selector, "selector");
        }
    }

    /**
     * Imports a JSONL file from {@code records/imports}.
     *
     * @param path file name, with no {@code ..}
     * @param replace whether an older row may overwrite a newer one
     */
    record MemoryImport(String path, boolean replace) implements AdminCommand {
        /**
         * Rejects a path that climbs out of the folder.
         */
        public MemoryImport {
            Objects.requireNonNull(path, "path");
            if (path.contains("..")) {
                throw new IllegalArgumentException("path");
            }
        }
    }

    /**
     * Builds a value artifact from {@code m28_value_stats}.
     *
     * @param profileFilter profile id, empty for every profile
     */
    record LearnBuild(String profileFilter) implements AdminCommand {
        /**
         * Rejects a filter that climbs out of the table.
         */
        public LearnBuild {
            Objects.requireNonNull(profileFilter, "profileFilter");
            if (profileFilter.contains("..") || profileFilter.indexOf('/') >= 0 || profileFilter.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("profile");
            }
        }
    }

    /**
     * Copies one artifact into {@code learn/export}.
     *
     * @param sha256 fingerprint
     * @param file file name, with no path
     */
    record LearnExport(String sha256, String file) implements AdminCommand {
        /**
         * Rejects a path.
         */
        public LearnExport {
            Objects.requireNonNull(sha256, "sha256");
            LearnPath.requireName(file);
        }
    }

    /**
     * Copies an exported file back into {@code learn/artifacts}.
     *
     * @param path file name, with no {@code ..}
     */
    record LearnImport(String path) implements AdminCommand {
        /**
         * Rejects a path that climbs out of the folder.
         */
        public LearnImport {
            LearnPath.requireName(path);
        }
    }

    /**
     * Points {@code active.json} at the previous artifact.
     */
    record LearnRollback() implements AdminCommand {
    }

    /**
     * Clears the active value, calibration, or both.
     *
     * @param kind {@code value}, {@code calibration} or {@code all}
     */
    record LearnDisable(String kind) implements AdminCommand {
        /**
         * Requires one of the three kinds.
         */
        public LearnDisable {
            Objects.requireNonNull(kind, "kind");
            if (!kind.equals("value") && !kind.equals("calibration") && !kind.equals("all")) {
                throw new IllegalArgumentException("kind");
            }
        }
    }

    /**
     * Asks {@code distill mode}. The applied mode is the minimum of this token, the global and the verdict.
     *
     * @param mode {@code off}, {@code shadow}, {@code assist} or {@code autonomous}
     */
    record DistillModeSet(String mode) implements AdminCommand {
        /**
         * Requires a known mode token.
         */
        public DistillModeSet {
            Objects.requireNonNull(mode, "mode");
            if (!mode.equals("off") && !mode.equals("shadow") && !mode.equals("assist") && !mode.equals("autonomous")) {
                throw new IllegalArgumentException("mode");
            }
        }
    }

    /**
     * Asks {@code distill disable}. The next capture sees off.
     *
     * @param scope {@code all}
     */
    record DistillDisable(String scope) implements AdminCommand {
        /**
         * Requires {@code all}.
         */
        public DistillDisable {
            Objects.requireNonNull(scope, "scope");
            if (!scope.equals("all")) {
                throw new IllegalArgumentException("scope");
            }
        }
    }

    /**
     * Notes a distill build. Training is not executed.
     *
     * @param profileFilter profile id, empty for every profile
     */
    record DistillBuild(String profileFilter) implements AdminCommand {
        /**
         * Rejects a filter that climbs out of the folder.
         */
        public DistillBuild {
            Objects.requireNonNull(profileFilter, "profileFilter");
            if (profileFilter.contains("..") || profileFilter.indexOf('/') >= 0 || profileFilter.indexOf('\\') >= 0) {
                throw new IllegalArgumentException("profile");
            }
        }
    }

    /**
     * Copies one distill file into {@code learn/distill/export}.
     *
     * @param file file name, with no path
     */
    record DistillExport(String file) implements AdminCommand {
        /**
         * Rejects a path.
         */
        public DistillExport {
            LearnPath.requireName(file);
        }
    }

    /**
     * Copies one exported distill file back into {@code learn/distill/artifacts}.
     *
     * @param path file name, with no {@code ..}
     */
    record DistillImport(String path) implements AdminCommand {
        /**
         * Rejects a path that climbs out of the folder.
         */
        public DistillImport {
            LearnPath.requireName(path);
        }
    }

    /**
     * Points the distill active file at the previous artifact. It does not train.
     */
    record DistillRollback() implements AdminCommand {
    }

    /**
     * Copies a proposal that already lives under {@code plugins/ThePredict/autoconfig/} into {@code inbox}.
     *
     * @param path absolute or plugin path
     */
    record AutoconfigImport(String path) implements AdminCommand {
        /**
         * Rejects a path outside the autoconfig directory.
         */
        public AutoconfigImport {
            akyrion.thepredict.domain.distill.OverlayPath.require(path);
        }
    }

    /**
     * Writes the active overlay under {@code plugins/ThePredict/autoconfig/active/}.
     *
     * @param path proposal path
     */
    record AutoconfigApply(String path) implements AdminCommand {
        /**
         * Rejects a path outside the autoconfig directory.
         */
        public AutoconfigApply {
            akyrion.thepredict.domain.distill.OverlayPath.require(path);
        }
    }

    /**
     * Reads the active overlay. It does not apply it.
     *
     * @param path proposal path whose file name selects the active copy
     */
    record AutoconfigShow(String path) implements AdminCommand {
        /**
         * Rejects a path outside the autoconfig directory.
         */
        public AutoconfigShow {
            akyrion.thepredict.domain.distill.OverlayPath.require(path);
        }
    }

    /**
     * Removes the active overlay. The author profile is not opened.
     *
     * @param path proposal path whose file name selects the active copy
     */
    record AutoconfigRevert(String path) implements AdminCommand {
        /**
         * Rejects a path outside the autoconfig directory.
         */
        public AutoconfigRevert {
            akyrion.thepredict.domain.distill.OverlayPath.require(path);
        }
    }
}
