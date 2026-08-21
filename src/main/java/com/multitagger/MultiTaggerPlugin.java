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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

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

	// Names of NPC types the local player has attacked while in a multi-combat area.
	// Insertion-ordered so the most-recently attacked type is the last entry.
	private final Set<String> attackedNames = new LinkedHashSet<>();

	// The NPC the local player most recently attacked, plus the tick it was recorded.
	// Kept as a sticky reference so a one-tick getInteracting() dropout (right after
	// clicking to attack, before combat actually starts) doesn't briefly re-highlight the
	// target. Time-bounded so a monster that later resets can be re-highlighted.
	private NPC lastAttacked;
	private int lastAttackedTick = -1;

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
		lastTaggedTick.remove(event.getNpc().getIndex());
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
		updateTaggedState();

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
	 * Per-tick bookkeeping: record the tick at which we last saw evidence an NPC is tagged
	 * (it has a health bar, or is interacting with us), which {@link #isTagged(NPC)} uses
	 * with a short grace period.
	 */
	private void updateTaggedState()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		int tick = client.getTickCount();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getHealthRatio() != -1 || npc.getInteracting() == local)
			{
				lastTaggedTick.put(npc.getIndex(), tick);
			}
		}
	}

	private boolean inMultiCombat()
	{
		Player local = client.getLocalPlayer();
		return local != null && client.getVarbitValue(VarbitID.MULTIWAY_INDICATOR) != 0;
	}

	/**
	 * Make an untagged (highlighted) NPC the left-click target when it is stacked under the
	 * cursor with a tagged one, so a stack can be tagged out without right-clicking.
	 *
	 * <p>Only like-for-like entries are reordered: the swap happens solely when the current
	 * left-click entry is an NPC entry whose option matches an untagged NPC's entry below
	 * it (both "Attack", for example). That way this can never steal a click from an item,
	 * an object, or a different action - it only breaks the tie the game resolves by
	 * proximity, which is exactly the case the highlight exists to flag.</p>
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		// The menu is not rebuilt while it is open, so reordering then would swap
		// repeatedly, moving entries under the player's cursor as they read them.
		if (!config.prioritizeUntagged() || client.isMenuOpen() || highlightedNpcs.isEmpty())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		if (entries.length < 2)
		{
			return;
		}

		// The last entry is the top of the menu, i.e. what a left click activates.
		final int top = entries.length - 1;
		MenuEntry leftClick = entries[top];
		NPC current = leftClick.getNpc();
		if (current == null || highlightedNpcs.contains(current))
		{
			// Not an NPC, or the left click already targets an untagged one.
			return;
		}

		for (int i = top - 1; i >= 0; i--)
		{
			MenuEntry candidate = entries[i];
			NPC npc = candidate.getNpc();
			if (npc == null
				|| candidate.isDeprioritized()
				|| !highlightedNpcs.contains(npc)
				|| !Objects.equals(candidate.getOption(), leftClick.getOption()))
			{
				continue;
			}

			entries[i] = leftClick;
			entries[top] = candidate;
			menu.setMenuEntries(entries);
			return;
		}
	}
}
