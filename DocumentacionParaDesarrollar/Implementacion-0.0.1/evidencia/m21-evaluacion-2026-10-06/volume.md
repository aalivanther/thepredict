# M21 volume

| row | p50 µs | p99 µs |
|---|---:|---:|
| tap.recordOffer | 1.334 | 8.833 |
| tap.verdicts | 0.375 | 1.959 |
| aggregate.flush 256 | 49.583 | 81.708 |
| aggregate.flush 1024 | 160.750 | 190.375 |
| aggregate.flush 4096 | 217.167 | 603.875 |
| aggregate.batchApplied 1024 | 52.375 | 130.667 |
| answerCache.lookup 1000 | 1.500 | 3.834 |
| answerCache.lookup 3000 | 1.208 | 4.625 |
| answerCache.lookup 8000 | 0.333 | 1.167 |
