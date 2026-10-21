CREATE TABLE schema_migrations (
  version INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  applied_epoch_ms INTEGER NOT NULL,
  plugin_version TEXT NOT NULL
) STRICT;

CREATE TABLE runs (
  run TEXT PRIMARY KEY,
  started_epoch_ms INTEGER NOT NULL,
  stopped_epoch_ms INTEGER,
  clean_shutdown INTEGER NOT NULL DEFAULT 0,
  plugin_version TEXT NOT NULL,
  model TEXT NOT NULL,
  config_sha256 TEXT NOT NULL,
  sqlite_version TEXT NOT NULL,
  last_applied_batch INTEGER NOT NULL DEFAULT 0
) STRICT;

CREATE TABLE outcome_stats (
  profile TEXT NOT NULL,
  tactic TEXT NOT NULL,
  movement TEXT NOT NULL,
  level TEXT NOT NULL,
  context TEXT NOT NULL,
  windows INTEGER NOT NULL,
  effect_mean REAL NOT NULL,
  effect_m2 REAL NOT NULL,
  dealt_sum REAL NOT NULL,
  taken_sum REAL NOT NULL,
  reached INTEGER NOT NULL,
  mob_died INTEGER NOT NULL,
  target_died INTEGER NOT NULL,
  casts INTEGER NOT NULL,
  hits INTEGER NOT NULL,
  first_epoch_ms INTEGER NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  extra BLOB,
  PRIMARY KEY (profile, tactic, movement, level, context)
) STRICT, WITHOUT ROWID;

CREATE TABLE decision_daily (
  day TEXT NOT NULL,
  model TEXT NOT NULL,
  profile TEXT NOT NULL,
  question_detail TEXT NOT NULL,
  requests INTEGER NOT NULL,
  answered INTEGER NOT NULL,
  failed INTEGER NOT NULL,
  input_tokens INTEGER NOT NULL,
  answered_cache INTEGER NOT NULL,
  input_tokens_saved INTEGER NOT NULL,
  adopted INTEGER NOT NULL,
  discarded INTEGER NOT NULL,
  breakdown BLOB,
  PRIMARY KEY (day, model, profile, question_detail)
) STRICT, WITHOUT ROWID;

CREATE TABLE answer_cache_daily (
  day TEXT NOT NULL,
  model TEXT NOT NULL,
  mode TEXT NOT NULL,
  lookups INTEGER NOT NULL,
  hits INTEGER NOT NULL,
  compared INTEGER NOT NULL,
  agree_best INTEGER NOT NULL,
  confidence_delta_p95 REAL,
  PRIMARY KEY (day, model, mode)
) STRICT, WITHOUT ROWID;
