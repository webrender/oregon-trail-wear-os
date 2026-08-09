# Input: rotary-first, and no free text anywhere

Rewritten 2026-08-09. The original version of this ADR described a gesture vocabulary
built around the emulator — swipes mapped to arrow keys, tap to Return, double-tap to
raise the watch's dictation sheet, triple-tap to swap disk sides. That scheme was
rejected by play testing and is superseded along with the emulator itself; see
[0005](0005-native-reimplementation.md). What follows replaces it.

**The crown is the primary control.** Rotating it moves the selection within a list;
tapping confirms; swiping right goes back, which is the Wear OS system gesture users
already expect. Nothing else is overloaded.

The single most important rule: **the app never asks the player to type.** Every
decision in the game is a selection from a bounded set, because we own the game loop and
can define it that way:

- Party names come from a curated list of period-appropriate names, not a text field.
- Hunting is aim-and-fire with the crown and a tap — not typing `BANG`.
- Menu choices, pace, rations, and river crossings are pickers.
- Store purchases are quantity steppers.

Rotary-first matters specifically on this device. On a 1.2" round screen a finger
covers a meaningful fraction of the display while it is touching it, so any interaction
that requires the player to *see* what they are selecting while selecting it is better
driven from the crown, where the hand stays off the glass. Tap is reserved for commit,
which is the one action where obscuring the screen briefly costs nothing.

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
