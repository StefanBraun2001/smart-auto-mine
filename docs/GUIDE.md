# Smart Auto Mine - Detailed Guide

Client-side Fabric mod for MC 26.2. Auto-mines whatever's under your
crosshair, reusing the same auto-eat/hunger-safety/duration/durability
infrastructure as Smart Auto Attack, plus mining-specific features: tool
rotation and an experimental "place-mine" mode.

## Installation

1. Install Fabric Loader for MC 26.2.
2. Install **Fabric API**.
3. Install **Cloth Config API** (required - the config screen depends on it).
4. Optionally install **Mod Menu** for an in-game config entry point;
   without it, edit `config/smartautomine.json` directly.
5. Drop the mod jar from [Releases](../../../releases) into your `mods/` folder.

## Toggling it on/off - two separate keys

- **K** (default): toggle regular mining.
- **L** (default): toggle place-mine mode.

Only one mode runs at a time. Pressing a mode's own key while it's running
turns it off; pressing the *other* mode's key switches straight over.
Toggling sends a feedback message and resets all counters (including
health-guard state), so re-enabling always starts fresh.

## Core mining loop

Only mines when your crosshair is on a block - there's no "blind mining"
equivalent to Auto Attack's "require target detected". Under the hood the
mod holds the mine button down and lets the game do the breaking, exactly
what the manual F3+T "stuck button" glitch does, so it inherits vanilla's
exact hold-to-break behavior.

## Regular mining drive (default: Continuous)

Three ways to drive plain mining, because holding the button (which avoids
the constant attack-indicator flicker) has one quirk: the game skips all
held-button handling while a screen is open.

- **Continuous** (default): holds the mine button while playing (no
  flicker), switches to driving the game directly only while a screen is
  open, so it keeps mining through inventory/chat with no downside.
- **Vanilla input**: only ever holds the mine button. No flicker, but
  pauses while any screen is open.
- **Legacy**: always drives the game directly. Keeps mining through
  screens, but the attack-cooldown indicator flickers constantly, like
  Toro's Auto Mine.

## Stop conditions

- **Min durability** / **min durability %** (0 = disabled) - stops (or
  switches tools, if Use more tools is on) before durability runs out.
- **Hunger safety stop** (default: on) / threshold (default: 6) -
  independent safety net on the 0-20 hunger scale, regardless of Auto-eat.
- **Health safety stop** (default: on) / threshold (default: 6) - see
  below, since this one has two very different modes of reacting.
- **Max duration** (empty = unlimited) - same free-text format as Auto
  Attack (`90m`, `1.5h`, `1h30m`, or a bare number of seconds). Time spent
  auto-eating never counts toward it. **Pause timer while mining is
  paused** (default: off) also excludes time spent paused with a screen
  open.
- **Place-mine mode only**: stops when the offhand is empty (never falls
  back to normal mining). See **Finish last block when offhand empties**
  below for a short grace period instead of stopping immediately.

## Health safety: stop, or pause and recover

Health safety stop reacts once health drops below the threshold. What it
does depends on **Eat food to regenerate health**:

- **Off** (default): hard auto-disable, same as the hunger safety stop.
- **On**: instead, pauses everything (including all timers, and releases
  the held mine/place-mine input), force-feeds from your Auto-eat food
  source until hunger is full, then waits for health to climb 2 hearts
  above the threshold before resuming normally - re-topping-up hunger the
  whole time if it dips again. Clamped to your actual max health, so a
  high threshold (e.g. 18) can never target above what's reachable. Gives
  up and auto-stops if health hasn't recovered within 45 seconds. Needs an
  auto-eat food source configured - without one, it just waits out the
  timeout every time.

**Ignore hunger/health safety while regenerating** (default: off, one
toggle each): skips the respective safety check entirely while you have
the Regeneration effect (e.g. standing near a beacon).

**Paranoia switch** (default: off): overrides the regeneration-ignore
toggle specifically for the eat-to-recover path - even while regenerating,
the pause-and-eat cycle still triggers, so hunger never goes untended just
because health is being propped up by an effect that could end at any
time. Only has any effect with both Eat food to regenerate health and
Auto-eat on.

## Use more tools

Toggle (default: off) + **Tool rotation mode**:

- **KEYWORD** (default, keyword `pickaxe`): substring match against the
  item ID, case-insensitive - matches any modded pickaxe too.
- **SAME_TYPE**: any tool in the same vanilla tool-category tag
  (pickaxe/axe/shovel/hoe/sword/spear) as the one that just ran low,
  regardless of material.
- **EXACT_MATCH**: only the exact same item.

When the current tool's durability guard would trigger, the mod scans your
hotbar (slots 1-9) for another match with enough durability left and
switches to it instead of stopping. Only stops once nothing usable remains
anywhere in the hotbar.

## Durability warning

An always-on watchdog, separate from everything else on this page - it
runs whether or not Auto Mine itself is toggled on, since its whole point
is catching you *manually* using a tool the mod would already refuse to
touch.

### Tool warning

Toggle (default: off) + **Warn for tools**: a list of keywords (e.g.
`pickaxe`, `axe`), matched the same way as **Use more tools**'s KEYWORD
mode - substring match against the item ID, case-insensitive. Checks
**both your main hand and offhand** independently. Whenever a held item
matches one of these keywords *and* its durability is already below the
**Min durability** / **Min durability %** threshold from the Safety tab
(the same values the auto-stop/rotation logic uses - there's no separate
threshold for this), the mod plays **Warning sound** (default
`minecraft:block.bell.use`, same free-text sound-event-ID format as
Auto-stop sound, and shared with Armor durability warning below):

- **Once**, the moment that item becomes held in that hand (switching to
  it, or its durability dropping below the threshold while already
  held).
- **Then repeatedly**, capped at twice a second, for as long as you keep
  holding down **either** attack/mine (left-click) **or** use
  (right-click) with it - so shearing a sheep, tilling dirt, or making
  farmland with a low-durability tool warns you too, not just breaking
  blocks.

### Armor warning

Separate toggle (default: off), **Armor durability warning**: checks all
four armor slots plus the elytra (which occupies the chest slot) for
durability - no keyword list, since any equipped armor piece counts
regardless of type. Uses the same Min durability/% threshold and Warning
sound as the tool warning above. Plays once the moment a piece drops
below the threshold or gets equipped already below it, then repeats
(capped at twice a second) for as long as it stays equipped and low -
there's no interaction key tied to wearing armor, so unlike the tool
warning this one isn't gated on attack/use being held.

### Mutual exclusion with Smart Auto Attack

Both toggles above are **independently** mutually exclusive with Smart
Auto Attack's equivalent features, if you have both mods installed -
enabling either one here while its counterpart in Smart Auto Attack is
already enabled shows an error on the toggle and blocks the config
screen's Save & Done, so the same low-durability item never triggers a
double warning from two mods at once. You can, however, have (for
example) the tool warning enabled here and the armor warning enabled in
Smart Auto Attack at the same time - only matching toggles conflict.

## Place-mine mode

Reproduces the manual "hold right-click, then left-click, then F3+T to
freeze both buttons held indefinitely" technique - e.g. holding raw ore in
your offhand and mining it with a Fortune III pickaxe for multiplied
drops, or placing coarse dirt from the offhand while a shovel in the main
hand tills it into a path before mining it. Requires an offhand item;
stops immediately if it's empty (see the grace-period option below).

It works by holding *both* mouse buttons down and letting the game do the
rest. Right-click tries the main-hand item first, then the offhand - the
same hand priority vanilla uses - so one held right-click both tills
existing dirt (main-hand shovel) and places a new block from the offhand
when there's nothing left to till. All timing comes straight from
vanilla - there are no delay/interval settings to tune.

For the intended setups (a raw-ore/Fortune farm, or a coarse-dirt + shovel
path farm), put a block behind the target that's slow to mine so the
crosshair always stays on a solid block. Refilling the offhand and any
block transforms are your own in-world redstone - the mod only drives the
click inputs.

**Finish last block when offhand empties** (default: off): normally
place-mine stops the instant the offhand runs out, which can leave the
block you just placed sitting there unmined. On: keeps running another
0.75 seconds so that last block still gets finished. Only applies to the
offhand running out - durability, hunger, health, and time limits always
stop immediately.

**Place-mine while a screen is open** (default: Vanilla):

- **Vanilla** (default): pauses while inventory/chat is open, resumes on
  close. Simple and reliable.
- **Advanced** (experimental): keeps placing/mining through an open
  screen by driving the game directly. May not place/till perfectly
  reliably in that state - normal play (no screen open) is unaffected.

## Wait after eat (default: on)

After finishing a bite, waits an extra 0.5 seconds before resuming mining,
as a safety buffer. Turn off to resume almost immediately instead.

## Play sound on auto-stop (default: on)

Plays a sound whenever the mod stops *itself* - never on a manual toggle.
**Auto-stop sound** is a full sound event ID, default
`minecraft:block.bell.use`; invalid/unknown IDs simply play nothing.

## Auto-eat

**Auto-eat enabled** (default: on) is a master switch, separate from the
food source below - lets you disable eating without losing your
configuration (also what presets toggle, since they don't touch hotbar
slots).

**Search any hotbar slot** (default: off): off eats only from the
configured **Auto-eat hotbar slot** (1-9, 0 = disabled); on searches the
whole hotbar for the first eligible food instead, ignoring that slot.

**Auto-eat hunger threshold** (0-20, default 20): starts eating once
hunger drops below this.

**Auto-eat amount** decides how much gets eaten once triggered:

- **EAT_ONCE** (default): exactly one bite per dip below the threshold,
  then waits for hunger to rise back above it before eating again - even
  if that one bite wasn't enough to clear the threshold itself.
- **DONT_OVEREAT**: keeps eating bite after bite past the threshold, but
  stops the moment the next bite's nutrition would push hunger past the
  20-point cap, so nothing gets wasted.
- **FILL_HUNGER**: keeps eating bite after bite until hunger is fully at
  20, regardless of how much of a bite's nutrition would go to waste.

**Auto-eat food safety** guards against eating something regrettable while
AFK:

- **LIGHT** (default): blocks enchanted golden apple, pufferfish.
- **FOOD_INSPECTOR**: Light + rotten flesh, spider eye, raw chicken,
  poisonous potato.
- **RAT**: no extra blocks beyond the hardcoded ones below.

Cake and chorus fruit are **always** blocked regardless of preset (cake
can't be eaten via right-click; chorus fruit randomly teleports you).

## Reconnecting after a disconnect

**Resume after manual reconnect** (default: off) controls what happens if
you reconnect yourself (not through the separate
[Smart Auto Reconnect](https://github.com/StefanBraun2001/smart-auto-reconnect)
mod) after a disconnect while this mod was running. Off: turns itself off
rather than silently resuming unnoticed. On: resumes after a brief settle
buffer.

If Smart Auto Reconnect handles the reconnect for you after an
*involuntary* disconnect, this mod always resumes automatically once the
world finishes loading, regardless of the setting above.

## Presets

Named bundles of "technique" settings - never your hotbar slot, keybind,
or feedback style, since those depend on your own setup. Covers: max
duration, min durability (absolute/%), use more tools + tool rotation
mode/keyword, auto-eat enabled, auto-eat hunger threshold, hunger safety
stop + threshold.

Managed from the **Presets** tab, not commands - it's the one tab that
reads live (a text line lists every currently saved preset by name) but
only *acts* when you press the config screen's own **Save & Done**, since
Cloth Config has no clickable-button entries to act on immediately:

- **Apply preset**: type an exact saved name, then Save & Done. Overwrites
  whatever you changed elsewhere on the same screen, since it applies
  last, right before the screen actually saves.
- **Save current settings as**: type a name, then Save & Done - saves the
  full current settings (including anything else you changed on the same
  screen) under that name, creating it or overwriting an existing preset
  of the same name.
- **Delete preset**: type an exact saved name, then Save & Done.

All three are independent and optional - leave any of them blank to skip
that action. If you fill in more than one at once, apply runs first, then
save, then delete, so e.g. typing the same name into both Apply and Save
just re-saves that preset's own values back under itself (a no-op).

Ships with four built-in presets, all pickaxe-keyword tool rotation:

- **Pickaxe_TP**: tool protection only (durability floor 10 / 5%). No
  tool rotation, no auto-eat, no hunger safety stop.
- **Pickaxe_MT_TP**: same as above, plus "Use more tools" tool rotation.
- **Pickaxe_MT_AEHP**: "Use more tools" tool rotation plus auto-eat with
  hunger-safety protection (eat below 7, hard-stop below 3) - no
  durability floor set.
- **Pickaxe_MT_TP_AEHP**: everything at once - tool protection, tool
  rotation, and auto-eat with hunger-safety protection. The fullest
  safety net of the four.

## Troubleshooting

- **It keeps pausing to eat but never resumes.** Check an Auto-eat food
  source is actually configured (slot set, or Search any hotbar slot on)
  and that Eat food to regenerate health's resume target (threshold + 2
  hearts) is actually reachable given your max health.
- **The health/hunger safety stop never fires even at low health.** Check
  whether you have Regeneration and the matching "Ignore ... while
  regenerating" toggle on - that's an intentional bypass. Turn on the
  Paranoia switch if you still want food management to happen anyway.
- **Place-mine stopped immediately.** The offhand was already empty when
  you started - check "Finish last block when offhand empties" only helps
  once it *runs out*, not if it started empty.

## License

MIT - see [LICENSE](../LICENSE).
