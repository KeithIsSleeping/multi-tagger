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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(MultiTaggerConfig.GROUP)
public interface MultiTaggerConfig extends Config
{
	String GROUP = "multitagger";

	@Alpha
	@ConfigItem(
		position = 0,
		keyName = "highlightColor",
		name = "Highlight colour",
		description = "Colour used to highlight untagged NPCs of the type you are fighting."
	)
	default Color highlightColor()
	{
		return Color.YELLOW;
	}

	@Alpha
	@ConfigItem(
		position = 1,
		keyName = "fillColor",
		name = "Fill colour",
		description = "Colour used to fill the highlight of untagged NPCs."
	)
	default Color fillColor()
	{
		return new Color(255, 255, 0, 40);
	}

	@ConfigItem(
		position = 2,
		keyName = "highlightHull",
		name = "Highlight hull",
		description = "Outline the model hull of untagged NPCs."
	)
	default boolean highlightHull()
	{
		return true;
	}

	@ConfigItem(
		position = 3,
		keyName = "highlightOutline",
		name = "Highlight outline",
		description = "Outline the model of untagged NPCs."
	)
	default boolean highlightOutline()
	{
		return false;
	}

	@ConfigItem(
		position = 4,
		keyName = "highlightTile",
		name = "Highlight tile",
		description = "Highlight the tile untagged NPCs are standing on."
	)
	default boolean highlightTile()
	{
		return false;
	}

	@ConfigItem(
		position = 5,
		keyName = "drawNames",
		name = "Draw names",
		description = "Draw the name of untagged NPCs above them."
	)
	default boolean drawNames()
	{
		return false;
	}

	@ConfigItem(
		position = 6,
		keyName = "onlyMostRecent",
		name = "Only most-recent type",
		description = "Highlight only NPCs matching the most recently attacked type, instead of every type you have attacked."
	)
	default boolean onlyMostRecent()
	{
		return false;
	}

	@ConfigItem(
		position = 7,
		keyName = "ignoredNames",
		name = "Ignored NPCs",
		description = "Comma-separated NPC names to never highlight, even in multi-combat (case-insensitive). Use this to limit tagging to specific monsters."
	)
	default String ignoredNames()
	{
		return "";
	}

	@ConfigItem(
		position = 8,
		keyName = "maxDistance",
		name = "Max distance (tiles)",
		description = "Only highlight NPCs within this many tiles of you, so walking away from a monster that resets clears its highlight promptly. 0 = no limit."
	)
	default int maxDistance()
	{
		return 12;
	}

	@Range(min = 0, max = 20)
	@ConfigItem(
		position = 9,
		keyName = "tagGraceTicks",
		name = "Tag grace (ticks)",
		description = "How long an NPC stays treated as tagged after its health bar disappears. Bridges the brief dropouts caused by the game only drawing 6 health bars at once. Keep low so monsters that reset (drop aggro) are re-highlighted quickly."
	)
	default int tagGraceTicks()
	{
		return 3;
	}

	@ConfigItem(
		position = 10,
		keyName = "prioritizeUntagged",
		name = "Left-click untagged first",
		description = "When an untagged (highlighted) NPC and a tagged one are stacked under the cursor, make the untagged one the left-click target so you can tag it without right-clicking."
	)
	default boolean prioritizeUntagged()
	{
		return false;
	}
}
