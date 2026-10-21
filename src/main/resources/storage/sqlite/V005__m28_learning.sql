CREATE TABLE m28_value_stats (
  profile TEXT NOT NULL,
  tactic TEXT NOT NULL,
  context TEXT NOT NULL,
  source TEXT NOT NULL,
  spec TEXT NOT NULL,
  week INTEGER NOT NULL,
  windows INTEGER NOT NULL,
  reward_mean REAL NOT NULL,
  reward_m2 REAL NOT NULL,
  effect_sum REAL NOT NULL,
  credit_sum REAL NOT NULL,
  avoided_sum REAL NOT NULL,
  jev_seen INTEGER NOT NULL,
  jev_agree INTEGER NOT NULL,
  first_epoch_ms INTEGER NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  extra BLOB,
  PRIMARY KEY (profile, tactic, context, source, spec, week)
) STRICT, WITHOUT ROWID;

CREATE TABLE m28_artifacts (
  sha256 TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  format_v INTEGER NOT NULL,
  created_epoch_ms INTEGER NOT NULL,
  source_batch INTEGER NOT NULL,
  entries INTEGER NOT NULL,
  bytes INTEGER NOT NULL,
  state TEXT NOT NULL,
  detail BLOB
) STRICT, WITHOUT ROWID;
