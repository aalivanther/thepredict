CREATE TABLE m27_player_memory (
  subject BLOB NOT NULL UNIQUE CHECK (length(subject) = 16),
  record_v INTEGER NOT NULL,
  record BLOB NOT NULL CHECK (length(record) <= 1024),
  fights INTEGER NOT NULL,
  rev INTEGER NOT NULL,
  first_epoch_ms INTEGER NOT NULL,
  updated_epoch_ms INTEGER NOT NULL
) STRICT;

CREATE INDEX m27_player_memory_updated ON m27_player_memory (updated_epoch_ms);
