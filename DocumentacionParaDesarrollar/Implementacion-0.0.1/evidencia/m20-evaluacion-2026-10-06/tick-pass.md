
## M20 tick layer

ReactionPolicy.select mean 0.8376454999999999 µs over 2000 calls (sink -2000).
REACTIVE capacity: about 1 encounter per world (300/200 ≈ 1.5) and up to about 0.95 USD per mob per hour at the default caps. STANDARD capacity: about 5 encounters per world.
Measured pass bound stays maxMicros plus 8 visits without a cast plus 1 cast. These rows have no server sample.
Sessions mix 50% HOT, 30% WARM, 20% COLD. Times are one pass, in µs, on this machine. No server sample. Step bound: maxMicros 600 plus at most 8 visits without a cast plus 1 cast, which is when the clock is read.

| Sessions | Layer | mean | p50 | p95 | p99 | max | bound |
|---|---|---|---|---|---|---|---|
| 20 | sin micro (REACTIONS) | 19.084 | 20.125 | 45.792 | 47.916 | 47.916 | cumple |
| 20 | con micro (FULL) | 17.372 | 16.958 | 20.583 | 21.125 | 21.125 | cumple |
| 100 | sin micro (REACTIONS) | 18.246 | 18.208 | 18.917 | 19.500 | 19.500 | cumple |
| 100 | con micro (FULL) | 27.791 | 26.792 | 28.833 | 56.584 | 56.584 | cumple |
| 200 | sin micro (REACTIONS) | 32.904 | 32.708 | 33.917 | 34.750 | 34.750 | cumple |
| 200 | con micro (FULL) | 61.439 | 50.791 | 94.792 | 272.042 | 272.042 | cumple |

Component rows, µs per call unless noted. ReactionPolicy p50 gate 100 ns. MicroPolicy p50 gate 50 ns. castIfReady gates 1.5 µs mean and 5 µs p99.

| Piece | mean | p50 | p95 | p99 | max | gate |
|---|---|---|---|---|---|---|
| ReactionPolicy | 0.0588 | 0.0333 | 0.0883 | 0.7467 | 0.7467 | cumple p50 <= 100 ns |
| MicroPolicy | 0.1056 | 0.0583 | 0.1883 | 0.2267 | 0.2267 | no cumple p50 <= 50 ns |
| reorder CandidatePolicy.select | 1.0040 | 0.8025 | 1.9325 | 2.0142 | 2.0142 | cumple measured |
| tickSignals lower bound (doubles, no server) | 0.0291 | 0.0250 | 0.0400 | 0.2158 | 0.2158 | cumple p99 <= 1.5 µs |
| castIfReady | 0.5944 | 0.5592 | 1.0200 | 1.3358 | 1.3358 | cumple p99 <= 5 µs |

