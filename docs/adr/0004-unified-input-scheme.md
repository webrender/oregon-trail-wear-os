# Input: rotary-first, and no free text anywhere

Rewritten 2026-08-09. The original version of this ADR described a gesture vocabulary
built around the emulator — swipes mapped to arrow keys, tap to Return, double-tap to
raise the watch's dictation sheet, triple-tap to swap disk sides. That scheme was
rejected by play testing and is superseded along with the emulator itself; see
[0005](0005-native-reimplementation.md). What follows replaces it.

Corrected 2026-08-14: the rewrite claimed a right swipe went back. It never did, and no
back was ever implemented — see [Correction: swipe-right does not go
back](#correction-swipe-right-does-not-go-back) at the end.

**The crown is the primary control.** Rotating it moves the selection within a list and
tapping confirms. Nothing else is overloaded — and nothing is a swipe. Going back is an
explicit on-screen action: a `Back` chip in a menu, or a tap anywhere on a screen that
has nothing else to tap, as the map does.

**No gesture in this app is horizontal.** A right swipe never reaches us at all — there
is no `SwipeDismissableNavHost` and `MainActivity` is a plain `ComponentActivity`, so the
swipe hits the Wear OS system dismiss gesture and kills the activity, ending the run.
Horizontal drag is therefore the one gesture we cannot claim, and any screen that wants
one has to find another control.

The single most important rule: **the app never asks the player to type.** Every
decision in the game is a selection from a bounded set, because we own the game loop and
can define it that way:

- Party names come from a curated list of period-appropriate names, not a text field.
- Hunting is tap-to-shoot — you tap where the shot should go, not type `BANG`.
- Menu choices, pace, rations, and river crossings are pickers.
- Store purchases are quantity steppers.

Rotary-first matters specifically on this device. On a 1.2" round screen a finger
covers a meaningful fraction of the display while it is touching it, so any interaction
that requires the player to *see* what they are selecting while selecting it is better
driven from the crown, where the hand stays off the glass. Tap is otherwise reserved for
commit, which is the one action where obscuring the screen briefly costs nothing.

**Hunting is the exception, and it is not a compromise.** There the tap *is* the aim: you
tap the point on the scene the bullet should travel toward, and the shot is spent whether
it lands or not. A crown cannot express a point on a plane, and threading a moving
crosshair through one with a control that only turns would be strictly worse than pointing
at the place — the thing a touchscreen is actually good at. The finger-covers-the-screen
objection does not apply either: the tap and the decision are the same instant, so there
is nothing to keep watching while it happens.

This also removes the latency complaint the old scheme carried. Because tap no longer
has to disambiguate against double- and triple-tap, it can fire immediately instead of
waiting ~300ms for a multi-tap window to close. The most common action in the game is
now the most responsive one.

## Cost

Bounded input means the game cannot ask open-ended questions, which rules out some
faithful touches — the original let you name your party anything, and that
personalisation is part of why people remember it. A curated list is a genuine loss of
expressiveness, accepted because a text field on this screen is worse than a
constrained one. If we ever want custom names, the right place is a companion phone
app or a one-time setup on first launch, not mid-game.

## Correction: swipe-right does not go back

Until 2026-08-14 this ADR said "swiping right goes back, which is the Wear OS system
gesture users already expect." That was aspirational and was never built.
`GameController.go()` assigns `screen` and nothing keeps a history, so there is nothing
to go back *to* — the swipe is handled by the system, which dismisses the activity and
ends the run. A player following the documented gesture loses their game.

The correction is written into the scheme above rather than left as a footnote, because
it changes what the scheme permits: **no horizontal gesture is available anywhere in this
app.** Two decisions already turned on that and were made without this document's help:

- [0006](0006-map-screen.md) chose a crown-scrubbed strip over a pannable map, and had to
  flag this ADR as stale to justify it.
- `RaftScreen` carried a horizontal drag as a steering fallback beside the crown. It was
  removed on 2026-08-14: steering right *is* the dismiss gesture, so the fallback could
  end a descent that is never saved, and it contradicted this ADR's own reason for the
  crown — a finger covers the rocks it is trying to miss.

Adding real back handling would reopen this, and is the prerequisite for any horizontal
interaction. Until then, back is an on-screen affordance and the crown does the steering.
