/*
 * Copyright (c) 2026, KeithIsSleeping
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.multitagger;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Multi Tagger",
	description = "In multi-combat, highlights untagged NPCs of the type you are fighting so you can tag them too",
	tags = {"npc", "highlight", "multi", "combat", "slayer", "tag"}
)
public class MultiTaggerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private MultiTaggerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MultiTaggerOverlay overlay;

	@Inject
	private NPCManager npcManager;

	// Names of NPC types the local player has attacked while in a multi-combat area.
	// Insertion-ordered so the most-recently attacked type is the last entry.
	private final Set<String> attackedNames = new LinkedHashSet<>();

	// The NPC the local player most recently attacked, plus the tick it was recorded.
	// Kept as a sticky reference so a one-tick getInteracting() dropout (right after
	// clicking to attack, before combat actually starts) doesn't briefly re-highlight the
	// target. Time-bounded so a monster that later resets can be re-highlighted.
	private NPC lastAttacked;
	private int lastAttackedTick = -1;

	// Last-known HP percent per NPC (keyed by index), captured while it had a live
	// health bar. The game only maintains health bars for a handful of NPCs at once,
	// so for the rest we fall back to this remembered value instead of assuming full HP.
	// NOTE: display only - do NOT use this to decide "tagged" (it never expires while the
	// NPC is spawned, which would keep a reset monster permanently un-highlighted).
	private final Map<Integer, Integer> lastKnownHpPercent = new HashMap<>();

	// Tick at which we last saw EVIDENCE an NPC (by index) was tagged: it had a health
	// bar, was interacting with us, or we damaged it. Used with a short grace period so
	// brief health-bar dropouts (the game renders only 6 bars at once) don't un-tag a
	// stack member, while a monster that truly resets goes untagged almost immediately.
	private final Map<Integer, Integer> lastTaggedTick = new HashMap<>();

	// NPCs to highlight this tick. Recomputed each game tick and rendered by our own
	// overlay (not the shared NpcOverlayService, which would let another plugin's
	// highlighter suppress ours). Read on the client thread by the overlay.
	private final Set<NPC> highlightedNpcs = new HashSet<>();

	// Parsed ignore list (lowercased names) cached against the raw config string.
	private final Set<String> ignoredNames = new HashSet<>();
	private String ignoredRaw = null;

	@Provides
	MultiTaggerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MultiTaggerConfig.class);
	}

	/** NPCs Multi Tagger wants to highlight this tick (read by {@link MultiTaggerOverlay}). */
	Set<NPC> getHighlightedNpcs()
	{
		return highlightedNpcs;
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		attackedNames.clear();
		lastAttacked = null;
		lastAttackedTick = -1;
		lastKnownHpPercent.clear();
		lastTaggedTick.clear();
		highlightedNpcs.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			attackedNames.clear();
			lastAttacked = null;
			lastAttackedTick = -1;
			lastKnownHpPercent.clear();
			lastTaggedTick.clear();
			highlightedNpcs.clear();
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer() || !inMultiCombat())
		{
			return;
		}
		if (event.getTarget() instanceof NPC)
		{
			lastAttacked = (NPC) event.getTarget();
			lastAttackedTick = client.getTickCount();
		}
		if (rememberAttacked(event.getTarget()))
		{
			recomputeHighlights();
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == lastAttacked)
		{
			lastAttacked = null;
		}
		highlightedNpcs.remove(event.getNpc());
		// Index may be reused by a future NPC, so drop the remembered state.
		lastKnownHpPercent.remove(event.getNpc().getIndex());
		lastTaggedTick.remove(event.getNpc().getIndex());
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.showMenuHp())
		{
			return;
		}

		for (MenuEntry entry : event.getMenuEntries())
		{
			NPC npc = entry.getNpc();
			if (npc == null || entry.getType() == MenuAction.EXAMINE_NPC)
			{
				continue;
			}
			String hp = hpText(npc);
			if (hp != null)
			{
				entry.setTarget(entry.getTarget() + " " + hp);
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!inMultiCombat())
		{
			return;
		}
		Hitsplat hitsplat = event.getHitsplat();
		if (hitsplat != null && hitsplat.isMine())
		{
			// We damaged it, so it is definitely tagged right now.
			if (event.getActor() instanceof NPC)
			{
				lastTaggedTick.put(((NPC) event.getActor()).getIndex(), client.getTickCount());
			}
			if (rememberAttacked(event.getActor()))
			{
				recomputeHighlights();
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateHpCache();

		if (config.debugLogging())
		{
			logDebug();
		}

		if (!inMultiCombat())
		{
			// Leaving multi-combat drops all tags and clears the highlights.
			if (!attackedNames.isEmpty())
			{
				attackedNames.clear();
				lastAttacked = null;
			}
			highlightedNpcs.clear();
			return;
		}

		// Combat state (health bar), distance and tagged-status all change over time
		// without spawn events, so recompute which NPCs to highlight each tick.
		recomputeHighlights();
	}

	/**
	 * Rebuild the set of NPCs to highlight from the current world state. Cheap - it just
	 * scans the loaded NPCs once. The overlay reads this set each frame and pulls live
	 * geometry from each NPC, so movement is still smooth between ticks.
	 */
	private void recomputeHighlights()
	{
		highlightedNpcs.clear();
		if (!inMultiCombat() || attackedNames.isEmpty() || client.getLocalPlayer() == null)
		{
			return;
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (shouldHighlight(npc))
			{
				highlightedNpcs.add(npc);
			}
		}
	}

	private void logDebug()
	{
		int varbit = client.getVarbitValue(VarbitID.MULTIWAY_INDICATOR);
		log.info("[MultiTagger] multiVarbit={} inMulti={} attackedNames={}",
			varbit, inMultiCombat(), attackedNames);
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			String name = npc.getName();
			if (name != null && attackedNames.contains(name))
			{
				log.info("[MultiTagger]   {} idx={} interacting={} hp={} dead={} -> highlight={}",
					name, npc.getIndex(), npc.getInteracting() != null, npc.getHealthRatio(),
						npc.isDead(), shouldHighlight(npc));
			}
		}
	}

	/**
	 * Record the name of an NPC the local player attacked. Returns true if this added
	 * a new type (so a rebuild is worthwhile).
	 */
	private boolean rememberAttacked(Actor target)
	{
		if (!(target instanceof NPC))
		{
			return false;
		}
		String name = target.getName();
		if (name == null)
		{
			return false;
		}
		// Keep the most-recently attacked type as the last inserted entry.
		attackedNames.remove(name);
		attackedNames.add(name);
		return true;
	}

	private boolean shouldHighlight(NPC npc)
	{
		if (!inMultiCombat() || attackedNames.isEmpty())
		{
			return false;
		}

		String name = npc.getName();
		if (name == null)
		{
			return false;
		}

		if (config.onlyMostRecent())
		{
			if (!name.equals(mostRecentName()))
			{
				return false;
			}
		}
		else if (!attackedNames.contains(name))
		{
			return false;
		}

		// Never highlight an explicitly ignored NPC type, so tagging can be limited to
		// specific monsters even when several types are attacked in the stack.
		if (isIgnored(name))
		{
			return false;
		}

		// Skip the NPC we are currently attacking, and (briefly) the one we just clicked -
		// the sticky lastAttacked reference avoids a flicker in the tick before combat
		// registers. It is time-bounded so that if that same monster later resets, it
		// becomes highlightable again instead of being excluded forever.
		boolean justClicked = npc == lastAttacked
			&& lastAttackedTick >= 0
			&& (client.getTickCount() - lastAttackedTick) <= config.tagGraceTicks();
		if (justClicked
			|| npc == client.getLocalPlayer().getInteracting()
			|| npc.isDead()
			|| isTagged(npc))
		{
			return false;
		}

		// Skip NPCs beyond the configured distance, so walking away from a monster that
		// resets clears its highlight promptly (re-evaluated every tick as the player
		// moves). 0 = no limit.
		return !isTooFar(npc);
	}

	/**
	 * Whether an NPC is "tagged" - i.e. shows the {@code *} prefix in the menu, meaning it
	 * is in combat. Treated as tagged when ANY of these hold:
	 * <ol>
	 *   <li>it currently has a visible health bar ({@link NPC#getHealthRatio()} != -1); or</li>
	 *   <li>it is interacting with the local player - an aggressive NPC carries the
	 *       {@code *} the moment it targets us, before we have damaged it (no health bar
	 *       yet); or</li>
	 *   <li>we saw either of the above (or landed a hit on it) within the last
	 *       {@link MultiTaggerConfig#tagGraceTicks()} ticks. The game renders only <b>6
	 *       health bars at once</b>, so a tagged NPC in a big stack can briefly report
	 *       {@code getHealthRatio() == -1}; the grace period bridges that flicker.</li>
	 * </ol>
	 *
	 * <p>The grace period is deliberately SHORT and time-bounded rather than a permanent
	 * "has ever had a health bar" cache: when a monster drops aggro it resets, loses the
	 * {@code *} and walks back to its spawn, and must become highlightable again straight
	 * away so the player can re-tag it.</p>
	 */
	private boolean isTagged(NPC npc)
	{
		if (npc.getHealthRatio() != -1)
		{
			return true;
		}
		if (npc.getInteracting() == client.getLocalPlayer())
		{
			return true;
		}
		Integer last = lastTaggedTick.get(npc.getIndex());
		return last != null && (client.getTickCount() - last) <= config.tagGraceTicks();
	}

	/**
	 * True if the NPC is farther than the configured max distance from the local player.
	 * Used so that walking away from a resetting monster clears its highlight quickly.
	 */
	private boolean isTooFar(NPC npc)
	{
		int max = config.maxDistance();
		if (max <= 0)
		{
			return false;
		}
		Player local = client.getLocalPlayer();
		if (local == null || local.getWorldLocation() == null || npc.getWorldLocation() == null)
		{
			return false;
		}
		return npc.getWorldLocation().distanceTo(local.getWorldLocation()) > max;
	}

	private String mostRecentName()
	{
		String last = null;
		for (String n : attackedNames)
		{
			last = n;
		}
		return last;
	}

	/**
	 * Whether an NPC name is in the user's ignore list (case-insensitive). The list is
	 * parsed from the comma-separated config string and cached until that string changes,
	 * so this is cheap to call per NPC per tick.
	 */
	private boolean isIgnored(String name)
	{
		String raw = config.ignoredNames();
		if (!Objects.equals(raw, ignoredRaw))
		{
			ignoredRaw = raw;
			ignoredNames.clear();
			for (String s : Text.fromCSV(raw == null ? "" : raw))
			{
				ignoredNames.add(s.toLowerCase(Locale.ROOT));
			}
		}
		return !ignoredNames.isEmpty() && ignoredNames.contains(name.toLowerCase(Locale.ROOT));
	}

	/**
	 * Per-tick bookkeeping over the loaded NPCs:
	 * <ul>
	 *   <li>remember the HP of any NPC that currently has a health bar, so the menu can
	 *       still show a value for stack members whose bar the game later drops; and</li>
	 *   <li>record the tick at which we last saw evidence an NPC is tagged (health bar or
	 *       interacting with us), which {@link #isTagged(NPC)} uses with a short grace
	 *       period.</li>
	 * </ul>
	 */
	private void updateHpCache()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		int tick = client.getTickCount();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			int ratio = npc.getHealthRatio();
			int scale = npc.getHealthScale();
			if (ratio >= 0 && scale > 0)
			{
				lastKnownHpPercent.put(npc.getIndex(), (int) Math.round((double) ratio / scale * 100.0));
				lastTaggedTick.put(npc.getIndex(), tick);
			}
			else if (npc.getInteracting() == local)
			{
				lastTaggedTick.put(npc.getIndex(), tick);
			}
		}
	}

	/**
	 * Build the menu HP suffix for an NPC (e.g. "(45)", "(30%)" or "(45 / 30%)"). Uses
	 * the live health bar when present; otherwise the last-known value we remembered
	 * while it had a bar (prefixed with {@code ~}); otherwise assumes full HP (an NPC
	 * that has never had a health bar is not in combat). Optionally colours it.
	 */
	private String hpText(NPC npc)
	{
		int ratio = npc.getHealthRatio();
		int scale = npc.getHealthScale();

		double fraction;
		int percent;
		Integer hitpoints;
		boolean approximate = false;
		if (ratio >= 0 && scale > 0)
		{
			fraction = (double) ratio / scale;
			percent = (int) Math.round(fraction * 100.0);
			hitpoints = estimateHitpoints(npc, ratio, scale);
		}
		else
		{
			Integer cached = lastKnownHpPercent.get(npc.getIndex());
			Integer maxHealth = npcManager.getHealth(npc.getId());
			if (cached != null)
			{
				// The game dropped this NPC's health bar (stack too large); show the
				// last value we saw rather than pretending it is full.
				percent = cached;
				fraction = cached / 100.0;
				hitpoints = maxHealth == null ? null : (int) Math.round(fraction * maxHealth);
				approximate = true;
			}
			else
			{
				// Never had a health bar => not in combat => full HP.
				fraction = 1.0;
				percent = 100;
				hitpoints = maxHealth;
			}
		}

		MenuHpMode mode = config.menuHpMode();
		String body;
		if (mode == MenuHpMode.PERCENTAGE || (mode == MenuHpMode.HITPOINTS && hitpoints == null))
		{
			body = percent + "%";
		}
		else if (mode == MenuHpMode.HITPOINTS)
		{
			body = String.valueOf(hitpoints);
		}
		else // BOTH
		{
			body = (hitpoints != null ? hitpoints + " / " : "") + percent + "%";
		}

		String text = "(" + (approximate ? "~" : "") + body + ")";
		if (config.menuHpColor())
		{
			text = ColorUtil.wrapWithColorTag(text, hpColor(fraction));
		}
		return text;
	}

	/**
	 * Estimate an NPC's current hitpoints from its health-bar ratio and known max HP,
	 * using the server's health-bar formula (as in the Opponent Info plugin). Returns
	 * null when the max HP is unknown.
	 */
	private Integer estimateHitpoints(NPC npc, int ratio, int scale)
	{
		Integer maxHealth = npcManager.getHealth(npc.getId());
		if (maxHealth == null)
		{
			return null;
		}
		if (ratio == 0)
		{
			return 0;
		}

		int minHealth = 1;
		int maxHp;
		if (scale > 1)
		{
			if (ratio > 1)
			{
				minHealth = (maxHealth * (ratio - 1) + scale - 2) / (scale - 1);
			}
			maxHp = (maxHealth * ratio - 1) / (scale - 1);
			if (maxHp > maxHealth)
			{
				maxHp = maxHealth;
			}
		}
		else
		{
			maxHp = maxHealth;
		}
		return (minHealth + maxHp + 1) / 2;
	}

	private static Color hpColor(double fraction)
	{
		fraction = Math.max(0.0, Math.min(1.0, fraction));
		// Interpolate red (0%) -> yellow (50%) -> green (100%).
		int r = fraction < 0.5 ? 255 : (int) Math.round(255 * (1 - fraction) * 2);
		int g = fraction > 0.5 ? 255 : (int) Math.round(255 * fraction * 2);
		return new Color(r, g, 0);
	}

	private boolean inMultiCombat()
	{
		Player local = client.getLocalPlayer();
		return local != null && client.getVarbitValue(VarbitID.MULTIWAY_INDICATOR) != 0;
	}
}
