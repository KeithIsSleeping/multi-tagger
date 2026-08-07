# Multi Tagger

A [RuneLite](https://runelite.net/) plugin that helps you tag every monster in a
stack while fighting in **multi-combat** areas.

When you attack an NPC in a multi-combat area, Multi Tagger highlights every other
**untagged** NPC of the same type, so you can quickly click each one to tag it
(e.g. for group Slayer, barraging, or Nightmare Zone style stacks). NPCs that are
already in combat (they show the `*` prefix), your current target, and dead NPCs
are skipped, so only the ones that still need tagging light up.

## Demo

<!--
  How media renders on the runelite.net plugin page (it shows this README, pinned to
  the commit in the plugin-hub manifest):
    - PNG/JPG committed to the repo -> shown INLINE on runelite.net and on GitHub.
        ![Multi Tagger demo](assets/demo.png)
    - GIF committed to the repo     -> INLINE on GitHub, but shown as a LINK on
                                       runelite.net (they linkify gifs).
        ![Multi Tagger demo](assets/demo.gif)
    - Video (mp4/webm)              -> INLINE on GitHub if uploaded via the README
                                       editor / an issue (user-attachments URL), but
                                       NOT rendered on runelite.net.
  So use a PNG for the guaranteed inline demo, and optionally add a gif/video too.
  Whatever is repo-hosted must exist at the commit referenced in the plugin-hub manifest.
  Then remove this comment and the line below.
-->

_Demo coming soon._

## Features

- **Highlights untagged same-type NPCs** while you are in a multi-combat area, using
  a shared highlighter (hull, outline, tile, and/or name) with configurable colours.
- **Clears promptly when you walk away.** A configurable max distance means monsters
  that reset as you move out of their aggro range stop being highlighted within a tick,
  instead of staying yellow.
- **Only-most-recent mode.** Optionally highlight only the single most recently
  attacked type instead of every type you have attacked.
- **HP in the right-click menu.** Optionally appends each NPC's current HP to its menu
  entry so you can tell stacked monsters apart, shown as estimated hitpoints, a
  percentage, or both, and optionally colour-coded from green (full) to red (low).
  Because the game only keeps health bars for a handful of NPCs at once, the plugin
  remembers the last-seen HP of monsters in a large stack (shown with a `~`) rather
  than assuming they are at full health.

## Configuration

| Setting | Description |
| --- | --- |
| Highlight colour / Fill colour | Colours for the highlight of untagged NPCs. |
| Highlight hull / outline / tile | Which highlight styles to draw. |
| Draw names | Draw the NPC name above untagged NPCs. |
| Only most-recent type | Highlight only the most recently attacked type. |
| Max distance (tiles) | Only highlight NPCs within this many tiles (0 = no limit). |
| Show HP in menu | Append each NPC's HP to its right-click menu entry. |
| Menu HP format | Hitpoints, percentage, or both. |
| Colour menu HP | Colour the menu HP from green (full) to red (low). |
| Debug logging | Log multi-combat / per-NPC state for troubleshooting. |

## License

BSD 2-Clause. See [LICENSE](LICENSE).
