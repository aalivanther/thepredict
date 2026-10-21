CREATE TABLE m26_pattern_stats (
  profile TEXT NOT NULL,
  pattern TEXT NOT NULL,
  target_kind TEXT NOT NULL,
  mode TEXT NOT NULL,
  encounters INTEGER NOT NULL,
  activations INTEGER NOT NULL,
  exploited_casts INTEGER NOT NULL,
  exploited_landed INTEGER NOT NULL,
  counter_windows INTEGER NOT NULL,
  counter_effect_mean REAL NOT NULL,
  counter_effect_m2 REAL NOT NULL,
  first_epoch_ms INTEGER NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  extra BLOB,
  PRIMARY KEY (profile, pattern, target_kind, mode)
) STRICT, WITHOUT ROWID;
