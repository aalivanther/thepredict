# M19 local measurements

Java 25.0.4.1+1-LTS, Mac OS X aarch64.

Remeasured on 2026-10-06 after the r2 corrections (no `SkillChoice[]` or `int[]` on the due path, and recalibration rereads only a choice whose ready tick has passed or is at most 2 ticks ahead). Same harness as r1: `M19Measurements`. No time wheel was added. `safe`, cap 4 and horizon 20 stay off.

## Intention, native facts, ready casts and pruning (M19)

Wall time of this machine. `CombatActions` is a double: `ready` and `ticksUntilReady` are O(1) and do not take the synchronized cooldown registry of MythicMobs, and a cast does not run a skill. `NativeSkillReader` does parse lines with `MythicLineConfigImpl`. No socket is opened.
`IntentionVocabulary.parse` of 16 raw primitives (14 valid, one unknown, one repeated). Budget: p99 ≤ 20 µs. Measured p99 20.833 µs. No cumple.
| Row | Samples | p50 / p99 / max |
|---|---|---|
| IntentionVocabulary.parse | 2000 | 4166 / 20833 / 87750 ns (20.833 µs p99) |

`NativeSkillReader.read` of 20 mechanics (19 cosmetic, one damaging radius). Budget: p99 ≤ 80 µs. Measured p99 49.833 µs. Cumple.
| Row | Samples | p50 / p99 / max |
|---|---|---|
| NativeSkillReader | 2000 | 18125 / 49833 / 341750 ns |

`readyCasts` walks every active session. A session that is not due is only compared. A due walk checks at most 64 sessions (`decision.maxReadyChecksPerTick`) and each check runs `castIfReady` on an `INTENT_ONLY` profile whose double cast returns `CAST`. Budget: ≤ 20 ns per session that is not due; ≤ 30 µs p99 per tick with 20 mobs. The time wheel is not added: the not-due p99 of 200 sessions is the figure that would justify it, and only above 20 µs. The 20-session due row is no cumple.
| Sessions | Samples | Not due p50 / p99 / max per session | Not due p50 / p99 / max per tick | Due checks | Due p50 / p99 / max per tick |
|---|---|---|---|---|---|
| 20 | 200 | 104 / 302 / 822 ns | 2083 / 6042 / 16458 ns | 20 | 19792 / 110708 / 153417 ns |
| 100 | 200 | 18 / 95 / 248 ns | 1833 / 9584 / 24833 ns | 64 | 41333 / 139875 / 792250 ns |
| 200 | 200 | 16 / 25 / 178 ns | 3333 / 5167 / 35791 ns | 64 | 39625 / 102583 / 142917 ns |

Not-due p99 of 200 sessions: 5.167 µs per tick. No time wheel.
`castIfReady` of one due `INTENT_ONLY` session. Budget: p50 ≤ 1.5 µs and p99 ≤ 5 µs, without the MythicMobs registry. Measured p50 0.583 µs, p99 0.708 µs. Cumple.
| Row | Samples | p50 / p99 / max |
|---|---|---|
| castIfReady | 2000 | 583 / 708 / 3291 ns |

`ANY_READY` selection inside `castIfReady`: the intent choice is cooling and seven other choices are ordered, one of them needing a line of sight. Budget: p50 ≤ 2 µs and p99 ≤ 6 µs. Measured p50 2.292 µs, p99 13.917 µs. No cumple.
| Row | Samples | p50 / p99 / max |
|---|---|---|
| ANY_READY selection | 2000 | 2292 / 13917 / 53750 ns |

`readyRecalibration` reads `ticksUntilReady` only for a choice whose cached ready tick has passed or is at most 2 ticks ahead. Budget: p50 ≤ 0.5 µs per read and p99 ≤ 60 µs per tick for 20 sessions. Measured p50 per read 0.087 µs; p99 per tick 28.167 µs. Cumple.
| Row | p50 / p99 / max |
|---|---|
| readyRecalibration per read | 87 / 203 / 2555 ns |
| readyRecalibration 20 x 8 per tick | 10667 / 28167 / 35333 ns |

`OptionPruning.prune` of 8 tactics and 4 choices, `ANY_READY`, cap 8, one sustain veto and the approach merge. Budget: p50 ≤ 3 µs and p99 ≤ 10 µs. Measured p50 12.709 µs, p99 52.458 µs. No cumple.
| Row | Samples | p50 / p99 / max |
|---|---|---|
| OptionPruning.prune | 2000 | 12709 / 52458 / 107958 ns |

## Gate of the five rows

| Row | Measured again | Cap | Gate |
|---|---|---|---|
| `IntentionVocabulary.parse` p99 | 20.833 µs | ≤ 20 µs | no cumple |
| `readyCasts` 20 due sessions, p99 per tick | 110.708 µs | ≤ 30 µs | no cumple |
| `ANY_READY` selection p50 / p99 | 2.292 / 13.917 µs | ≤ 2 / 6 µs | no cumple |
| `readyRecalibration` 20×8, p99 per tick | 28.167 µs | ≤ 60 µs | cumple |
| `OptionPruning.prune` p50 / p99 | 12.709 / 52.458 µs | ≤ 3 / 10 µs | no cumple |

A cached ready tick more than 2 ticks ahead is not reread and is not pulled forward. The external-charge capture proved in `ReadyCastsTest` is the one inside that window.
