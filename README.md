# Multi Tagger

A [RuneLite](https://runelite.net/) plugin that helps you tag every monster in a
stack while fighting in **multi-combat** areas.

When you attack an NPC in a multi-combat area, Multi Tagger highlights every other
**untagged** NPC of the same type, so you can quickly click each one to tag it
(e.g. for group Slayer, barraging, or Nightmare Zone style stacks). NPCs that are
already in combat (they show the `*` prefix), your current target, and dead NPCs
are skipped, so only the ones that still need tagging light up.

## Demo

![Multi Tagger demo](assets/demo.gif)

## Features

- **Highlights untagged same-type NPCs** while you are in a multi-combat area (hull,
  outline, tile, and/or name) with configurable colours.
- **Draws its own overlay**, so its highlight is never suppressed by another NPC
  highlighter (e.g. NPC Indicators) that happens to target the same monster.
- **Re-highlights monsters that reset.** When a monster drops aggro it loses its `*`
  and walks back to spawn; it becomes highlightable again straight away so you can
  re-tag it.
- **Handles big stacks.** The game only draws 6 health bars at once, so a tagged NPC
  in a large stack can briefly look untagged. A short, configurable grace period
  bridges that flicker without delaying genuine resets.
- **Clears promptly when you walk away.** A configurable max distance means monsters
  outside that range stop being highlighted within a tick.
- **Only-most-recent mode.** Optionally highlight only the single most recently
  attacked type instead of every type you have attacked.
- **Ignore list.** Comma-separated NPC names to never highlight, so tagging can be
  limited to specific monsters.

> Looking for monster HP in the right-click menu? Use the
> [Monster Menu HP](https://runelite.net/plugin-hub/show/menuhp) plugin.

## Configuration

| Setting | Description |
| --- | --- |
| Highlight colour / Fill colour | Colours for the highlight of untagged NPCs. |
| Highlight hull / outline / tile | Which highlight styles to draw. |
| Draw names | Draw the NPC name above untagged NPCs. |
| Only most-recent type | Highlight only the most recently attacked type. |
| Ignored NPCs | Comma-separated names to never highlight. |
| Max distance (tiles) | Only highlight NPCs within this many tiles (0 = no limit). |
| Tag grace (ticks) | How long an NPC stays treated as tagged after its health bar disappears. |
| Debug logging | Log multi-combat / per-NPC state for troubleshooting. |

## License

BSD 2-Clause. See [LICENSE](LICENSE).
