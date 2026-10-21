CREATE TABLE m22_eval_runs (
  run_id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  candidate_sha256 TEXT NOT NULL,
  baseline_sha256 TEXT,
  sources TEXT NOT NULL,
  base_seed INTEGER NOT NULL,
  pairs INTEGER NOT NULL,
  paid_calls INTEGER NOT NULL,
  input_tokens INTEGER NOT NULL,
  usd REAL NOT NULL,
  started_epoch_ms INTEGER NOT NULL,
  finished_epoch_ms INTEGER,
  extra BLOB
) STRICT;

CREATE TABLE m22_eval_metrics (
  run_id TEXT NOT NULL,
  arm TEXT NOT NULL,
  opponent TEXT NOT NULL,
  metric TEXT NOT NULL,
  n INTEGER NOT NULL,
  value REAL,
  ci_low REAL,
  ci_high REAL,
  PRIMARY KEY (run_id, arm, opponent, metric)
) STRICT, WITHOUT ROWID;

CREATE TABLE m22_difficulty_stats (
  profile TEXT NOT NULL,
  context TEXT NOT NULL,
  rung INTEGER NOT NULL,
  fights INTEGER NOT NULL,
  log_ttk_mean REAL NOT NULL,
  log_ttk_m2 REAL NOT NULL,
  censored INTEGER NOT NULL,
  excluded INTEGER NOT NULL,
  start_rung_estimate REAL NOT NULL,
  start_rung_epoch_ms INTEGER NOT NULL,
  first_epoch_ms INTEGER NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY (profile, context, rung)
) STRICT, WITHOUT ROWID;

CREATE TABLE m22_calibration_bins (
  model TEXT NOT NULL,
  question_detail TEXT NOT NULL,
  target TEXT NOT NULL,
  bin INTEGER NOT NULL,
  n INTEGER NOT NULL,
  confidence_sum REAL NOT NULL,
  success_sum REAL NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY (model, question_detail, target, bin)
) STRICT, WITHOUT ROWID;

CREATE TABLE m22_gate_verdicts (
  candidate_sha256 TEXT NOT NULL,
  gate TEXT NOT NULL,
  verdict TEXT NOT NULL,
  run_id TEXT NOT NULL,
  decided_epoch_ms INTEGER NOT NULL,
  detail BLOB,
  PRIMARY KEY (candidate_sha256, gate)
) STRICT, WITHOUT ROWID;
