### Currently Solved RNG:
- Seed-deterministic portal light time via cumulative probability
- Vanilla per-tick probability math (not arbitrary scoring)
- Exact vanilla lava walk pathing checks
- Vanilla flammable block registry checks
- Deterministic fire placement from lava (seed-derived timing)
- Deterministic fire spread (lava → fire → fire → portal)
- Deterministic block burn-away timing
- Canonical position keys (same setup mirrored/rotated produces identical fire timing to reduce incosistancies with same setup but different sides of lava pools)
- Persistence across save/load
- Fire suppression in portal subchunks (prevents vanilla RNG interference)
- Biome humidity not factored in
- Difficulty for fire spread

### Credits
@ClearColdWater - w/ testing and feedback
