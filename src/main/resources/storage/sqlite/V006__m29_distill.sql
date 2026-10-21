CREATE TABLE m29_choice_stats (
  profile TEXT NOT NULL,
  regime TEXT NOT NULL,
  context TEXT NOT NULL,
  tactic TEXT NOT NULL,
  source TEXT NOT NULL,
  week INTEGER NOT NULL,
  model TEXT NOT NULL,
  offered INTEGER NOT NULL,
  chosen INTEGER NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY (profile, regime, context, tactic, source, week, model)
) STRICT, WITHOUT ROWID;

CREATE TABLE m29_agreement_stats (
  artifact TEXT NOT NULL,
  profile TEXT NOT NULL,
  regime TEXT NOT NULL,
  context TEXT NOT NULL,
  week INTEGER NOT NULL,
  n INTEGER NOT NULL,
  sum_delta REAL NOT NULL,
  sum_delta_sq REAL NOT NULL,
  compared INTEGER NOT NULL,
  agreed INTEGER NOT NULL,
  audited INTEGER NOT NULL,
  audit_agreed INTEGER NOT NULL,
  last_epoch_ms INTEGER NOT NULL,
  PRIMARY KEY (artifact, profile, regime, context, week)
) STRICT, WITHOUT ROWID;
