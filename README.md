# Smart Auto Mine

Client-side Fabric mod. Auto-mines whatever's under your crosshair, reusing
the same auto-eat/hunger-safety/duration/durability infrastructure as Smart
Auto Attack, plus mining-specific features: tool rotation and an
experimental "place-mine" mode.

Current build: **B0.5.5** (beta), **MC 26.2 only** (1.20.4 support
was dropped as of A0.4). Grab a built jar from the
[Releases](../../releases) page, or build from source with
`./gradlew build` inside `26.2/`. See [docs/GUIDE.md](docs/GUIDE.md) for
a full walkthrough of every setting.

**GitHub-only, not on Modrinth.** A subsequent, silent change to
Modrinth's rules disqualifies this mod from that platform, so GitHub
Releases is the only distribution channel going forward. The Modrinth
listing has been removed.

## Install

Needs Fabric Loader + **Fabric API**. Also install **Cloth Config API**
(required) and, optionally, **Mod Menu**.

## Features

- Two separate toggle keybinds: regular mining (default **K**) and
  place-mine mode (default **L**) - only one runs at a time.
- Works by holding the mouse buttons down and letting the game's own input
  handling mine/place - literally what the manual "hold buttons + F3+T"
  glitch does - so it inherits vanilla's exact timing and hand priority.
- Regular mining drive (config, three modes): **Continuous** (default) holds
  the button while playing and drives the game directly only while a screen
  is open, so it keeps mining through inventory/chat with no attack-indicator
  flicker; **Vanilla input** only holds the button (no flicker, but pauses
  while a screen is open); **Legacy** always drives directly (keeps mining
  through screens, but the attack indicator flickers, like Toro's Auto Mine).
- **Throttle**: alternates mining for a configured duration, then pausing
  for another, with an option to freeze the max-duration timer during the
  pause.
- Stop conditions: min durability (absolute/%), hunger safety stop, health
  safety stop, max duration.
- Health safety stop can either hard-disable (default) or, with **Eat food
  to regenerate health** on, pause everything (including all timers),
  force-feed until hunger is full, and wait for health to climb 2 hearts
  above the threshold before resuming - only giving up after 45 seconds.
  Both hunger and health safety can also be set to ignore themselves
  entirely while you have Regeneration (e.g. near a beacon); a **Paranoia
  switch** overrides that specifically for the eat-to-recover path, so
  hunger never goes untended even while regenerating.
- "Use more tools": rotates to another hotbar item when the current tool's
  durability guard trips - by keyword, by tool category (any material), or
  requiring an exact item match.
- **Durability warning**: two independent always-on watchdogs (work even
  while the mod itself is off). Tool warning plays a sound when a
  main-hand *or offhand* item matching a user-configured keyword (e.g.
  `pickaxe`, `axe`) drops below the same Min durability/% threshold above
  - once on equip, then looping (at most once every 2 seconds) while
  mining *or* right-click-using it (shearing, tilling, etc.). Armor
  warning checks all 4 armor slots + elytra for durability, no keyword
  needed. Both share a bundled default warning sound (a two-tone gong,
  distinct from the auto-stop sound below).
- Place-mine mode: right-click tries main-hand then offhand (so a main-hand
  shovel tills existing dirt and the offhand places a new block when there's
  nothing to till) alongside held left-click to mine - a faithful stand-in
  for the manual "hold both buttons + F3+T" cheese, e.g. a Fortune III ore
  farm or a coarse-dirt + shovel path farm. Optionally keeps running 0.75s
  after the offhand empties so the last placed block still gets mined. While
  a screen is open it pauses by default (**Vanilla**); an experimental
  **Advanced** mode keeps it going through the screen but may not place/till
  perfectly reliably then.
- Auto-eat from a configured hotbar slot or, with "Search any hotbar
  slot" on, the first eligible food found anywhere in the hotbar - with a
  choice of how much to eat per trigger: one bite, as much as won't waste
  nutrition past a full bar, or straight to full regardless of waste.
- **Presets**: named bundles of duration/durability/tool-rotation/auto-eat
  settings, managed from their own config tab (apply/save/delete by name,
  applied on the screen's Save & Done). Ships with `Pickaxe_TP`,
  `Pickaxe_MT_TP`, `Pickaxe_MT_AEHP`, and `Pickaxe_MT_TP_AEHP` - see
  [docs/GUIDE.md](docs/GUIDE.md#presets) for what each one sets.
- Auto-resumes after a reconnect handled by the separate
  [Smart Auto Reconnect](https://github.com/StefanBraun2001/smart-auto-reconnect)
  mod; optional "resume after manual reconnect" toggle for reconnects you
  initiate yourself.
- Auto-stop sound feedback, with a bundled default twin-bell ring (any
  other sound event ID works too).

Full feature/config documentation lives in the bundled README shipped
alongside the jars.

## Building from source

```
cd 26.2
./gradlew build
```

Built jar lands in `26.2/build/libs/`. Needs JDK 25.

## Credits

Feature set inspired by Toro Bolin's Auto Mine mod - this is an independent
reimplementation (no shared code), built from scratch with extra
tool-rotation/place-mine features layered on top.

## AI disclosure

This mod's code was generated by Claude (Sonnet 5, medium reasoning
effort), based on feature requests and iterative in-game testing feedback
from the repository owner.

## License

MIT - see [LICENSE](LICENSE).
