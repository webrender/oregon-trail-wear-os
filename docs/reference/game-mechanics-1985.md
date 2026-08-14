# Oregon Trail (1985) — mechanics reference

Source material gathered 2026-08-09 to ground the native Kotlin reimplementation.
Every number here is sourced. The two figures I could not confirm from research —
ammunition box size and per-profession starting money — were supplied by the user on
2026-08-09 and are noted as such at their respective tables.

## Provenance and licensing

| Source | What it gives us | License |
|---|---|---|
| [LiquidFox1776/oregon-trail-1978-basic](https://github.com/LiquidFox1776/oregon-trail-1978-basic) | Complete 735-line BASIC listing of the **1978** text version | MIT (Michael Hirsch, 2018) — safe to port |
| [ayebear/oregon-trail](https://github.com/ayebear/oregon-trail) | Trail graph with exact distances, river depth/width/ferry flags | **No license file — all rights reserved.** Do not copy code. Facts only. |
| [attilabuti/Oregon-Trail](https://github.com/attilabuti/Oregon-Trail) | Go recreation of the 1978 text version | Unspecified |
| GameFAQs walkthrough (ASchultz, 2000), vendored in ayebear repo as `original_game/strategy_and_path.txt` | Store price table, scoring, pace/ration rates | Fan documentation |
| [philipbouchard.com](https://www.philipbouchard.com/oregon-trail/) | Design intent from the 1985 lead designer; landmark order; the Columbia rafting game | Author's own site |
| [died-of-dysentery.com](https://www.died-of-dysentery.com/stories/rafting-columbia.html) | Second-hand account of the rafting game as played | Fan documentation |
| AppleWin `source/RGBMonitor.cpp` (in-repo submodule) | Authoritative hi-res palette RGB | GPL-3.0 — values are facts, not code |

**Key legal read:** game rules, numbers, and data tables are facts and not
copyrightable. The 1985 game's *prose* (landmark descriptions, event text) and its
*art* are MECC's. So: derive the tables, but write all display text ourselves and
draw all art ourselves. The `ayebear` repo's landmark strings are verbatim MECC text —
useful to confirm what a screen covered, not to copy.

Note the 1985 version shares no code with the 1971/1978 original — Bouchard describes
it as a complete reimagining that "abandoned all of the original algorithms and
variables." The MIT-licensed 1978 BASIC is therefore useful as a *sanity reference for
the genre*, not as the rules we're implementing.

## Trail graph

Distances in miles, to the *next* node. Three branch points; a typical run visits 16
landmarks over 15 segments.

```
Independence, MO
  │ 102
Kansas River Crossing ......... river · ferry available
  │ 83
Big Blue River Crossing ....... river · no ferry
  │ 119
Fort Kearney .................. fort · +25%
  │ 250
Chimney Rock
  │ 86
Fort Laramie .................. fort · +50%
  │ 190
Independence Rock
  │ 102
South Pass .................... ⑂ BRANCH 1
  ├── 57 ─→ Green River Crossing (river · ferry) ── 144 ──┐
  └── 125 ─→ Fort Bridger (fort · +75%) ─────────── 162 ──┤
                                                          ▼
                                                    Soda Springs
  │ 57
Fort Hall ..................... fort · +100%
  │ 182
Snake River Crossing .......... river
  │ 114
Fort Boise .................... fort · +125%
  │ 160
Blue Mountains (Grand Ronde) .. ⑂ BRANCH 2
  ├── 125 ────────────────────────────────────────────────┐
  └── 55 ─→ Fort Walla Walla (fort · +150%) ─── 120 ──────┤
                                                          ▼
                                                     The Dalles ⑂ BRANCH 3
                                    ├── Barlow Toll Road ── 200 ──→ Oregon City
                                    └── Raft the Columbia ── 50 + 20 ──→ Oregon City
```

**Validation:** Green River + Barlow route totals 1,971 miles, which matches Bouchard's
stated ~2,000-mile design target. This cross-check is the main reason I trust the
distance table.

⚠️ The `ayebear` landmark array lists South Pass *before* Fort Laramie, which
contradicts both real geography and Bouchard. The order above follows Bouchard; the
distances are `ayebear`'s. Independence Rock → South Pass = 102 is the value their
misplaced entry carried.

## Store prices

Matt's General Store in Independence is baseline; forts apply a linear markup.

| Place | Markup | Oxen (yoke of 2) | Parts / Clothes | Bullets (box of 20) | Food (per lb) |
|---|---|---|---|---|---|
| Matt's General Store | 0% | $20 | $10.00 | $2.00 | $0.20 |
| Fort Kearney | 25% | $25 | $12.50 | $2.50 | $0.25 |
| Fort Laramie | 50% | $30 | $15.00 | $3.00 | $0.30 |
| Fort Bridger | 75% | $35 | $17.50 | $3.50 | $0.35 |
| Fort Hall | 100% | $40 | $20.00 | $4.00 | $0.40 |
| Fort Boise | 125% | $45 | $22.50 | $4.50 | $0.45 |
| Fort Walla Walla | 150% | $50 | $25.00 | $5.00 | $0.50 |

**Ammunition is sold in boxes of 20 at $2 per box** (confirmed by the user, 2026-08-09).
That works out to 10 cents per bullet at Matt's, which agrees with the walkthrough's
hunting economics discussion; the "(100)" in its price-table header is an error in that
document. Note the scoring table awards points per *50 bullets*, i.e. per 2.5 boxes —
the two units are deliberately different, so keep ammunition in **individual bullets**
internally and convert only at the point of purchase.

## Pace and rations

| Pace | Rate |
|---|---|
| Steady | 50% |
| Strenuous | 75% |
| Grueling | 100% |

| Rations | Food per person per day |
|---|---|
| Filling | 3 lb |
| Meager | 2 lb |
| Bare bones | 1 lb |

Health is a four-level scale: **Good / Fair / Poor / Very Poor**. Weather, rations, and
pace apply cumulative effects that can recover as well as worsen.

## Professions

Confirmed by the user, 2026-08-09.

| Profession | Difficulty | Starting money | Score multiplier |
|---|---|---|---|
| Banker | Easy | $1,600 | ×1 |
| Carpenter | Medium | $800 | ×2 |
| Farmer | Hard | $400 | ×3 |

Money and multiplier are inversely paired, so expected score is roughly flat across
professions and the choice reads as pure difficulty. Worth preserving that relationship
if we ever retune: a farmer's $400 has to cover the same ~$200 of oxen and food that a
banker's $1,600 does, which is what makes the ×3 feel earned.

## Scoring

Points awarded on arrival in the Willamette Valley, then multiplied by profession.

| Item | Points |
|---|---|
| Each surviving party member, health Good | 500 |
| Each surviving party member, health Fair | 400 |
| Each surviving party member, health Poor | 300 |
| Each surviving party member, health Very Poor | 200 |
| Wagon | 50 |
| Each ox | 4 |
| Each set of clothing | 2 |
| Each spare part | 2 |
| Per 25 lb of food | 1 |
| Per 50 bullets | 1 |
| Per $5 cash | 1 |

Documented practical maximum ≈2,690 before the multiplier.

## Hazards

Documented ways to get stuck or killed:

- Wagon breaks down without the matching spare part (wheel / axle / tongue)
- Fording a river deeper than 3 feet
- Wagon capsizes during a crossing
- Hitting a rock or the bank while rafting the Columbia
- A party member is ill and you don't rest
- Food runs out
- Pushing oxen too hard on bad water / inadequate grass

Diseases named in the 1985 version: **typhoid, cholera, measles, dysentery, exhaustion,
fever** — plus injuries (broken limbs, snakebite).

## Hunting

- Real-time, skill-based, using terrain art matched to the current trail region
- Animal species vary by location
- **100 lb carry limit** back to the wagon regardless of how much was shot — the
  deliberate sustainability lesson
- Available only between landmarks

## Rafting the Columbia

The trail's other minigame, and the one the 1985 version *ends* on. Sourced from
Bouchard's own account ([Rafting Down the Columbia
River](https://www.philipbouchard.com/oregon-trail/rafting-columbia.html)) and a
player-side description of the shipped game
([died-of-dysentery.com](https://www.died-of-dysentery.com/stories/rafting-columbia.html)).

**Both endings are 1985 inventions.** Neither rafting the Columbia nor the Barlow Toll
Road appears in the 1971/1978 game; Bouchard added both as part of putting real
geography into the trail, which is what makes the branch at The Dalles in the
[trail graph](#trail-graph) faithful rather than embellishment.

What shipped is a stripped-down version of what was designed. Cut before release:
hiring local guides for the descent, a river current that pushed the raft sideways, and
portaging around waterfalls and rapids. Bouchard: the game was "entirely about avoiding
the rocks in the river, and then landing the raft at the correct exit point."

The shipped loop:

| Element | What it was |
|---|---|
| View | An abstract 45° overhead angle, deliberately "very similar to the river crossing module" |
| Motion | The current carries the raft downstream on its own; the player only steers |
| Controls | Arrow keys, left and right |
| Hazards | Rocks in the channel, and the two banks. Nothing else |
| Damage | Supplies lost, and party members could drown |
| Pacing | Three direction signs pass on the bank |
| Ending | After the third sign, land at the squiggly path up the bank |

**Two design faults Bouchard names himself**, both worth fixing rather than reproducing:

1. **No distinction between a glancing blow and a direct hit** — a scrape cost the same
   as a broadside. He states plainly he wishes this had been implemented.
2. **The losses were out of proportion** — "the losses from hitting a rock often seem
   excessive, and the losses from hitting the river bank seem even more out of whack."

Contemporary walkthroughs advised saving the game at The Dalles specifically so a bad
run could be retried, which is a fair measure of how punishing it was.

### What we implement

Unsourced beyond the shape above — the numbers are ours, and are tuning knobs. See
`core/Rafting.kt`, which owns the loss table, and `ui/screens/RaftScreen.kt`, which owns
the real-time loop. Departures from the original, all deliberate:

- **Impact severity is graded** — graze, solid hit, or bank — from how deep the overlap
  is, which is fault (1) above fixed in the one place it can be.
- **The crown steers**, not a d-pad. See [ADR 0004](../adr/0004-unified-input-scheme.md):
  a finger on a 1.2" screen covers the rocks it is trying to dodge.
- **A briefing card replaces the original's two screens of instructions**, for the same
  reason the rest of the game has no walls of text.
- **There is no failing to arrive.** A wrecked raft still washes up downstream, poorer,
  exactly as a capsized wagon still reaches the far bank in `RiverCrossing`. A party
  wiped out on the water ends the run as any other wipeout does.

## Apple II hi-res palette

From AppleWin `source/RGBMonitor.cpp` (the "Linards tweaked" values, which are what
AppleWin actually renders — not the naive full-saturation primaries).

| Color | Hex |
|---|---|
| Black | `#000000` |
| White | `#FFFFFF` |
| Green | `#38CB00` |
| Violet / Magenta | `#C734FF` |
| Orange | `#F25E00` |
| Blue | `#0DA1FF` |

Hi-res is 280×192, but adjacent pixel pairs form one color cell via NTSC artifacting,
so the effective color resolution is **140×192**. Colors are constrained per 7-pixel
byte group — which is exactly why 1985 Apple II art has its characteristic look, and
worth emulating deliberately rather than accidentally.

## Reference numbers from the 1978 BASIC (MIT-licensed)

Not the rules we're implementing, but useful for feel. From the listing:

- Daily mileage: `M = M + 200 + (A-220)/5 + 10*RND()` where `A` is spend on oxen
- Eating cost: `F = F - 8 - 5*E` where `E` is eating level 1–3
- Hunting mileage penalty: `M = M - 45`
- Illness roll: `IF 100*RND() < 10 + 35*(E-1)` → mild; `IF 100*RND() < 100 - 40/4^(E-1)` → bad
- River swamping: `F = F-30`, `C = C-20`, `M = M - 20 - 20*RND()`
- Starting funds: $900 total, $200 already spent on the wagon, $700 to allocate
