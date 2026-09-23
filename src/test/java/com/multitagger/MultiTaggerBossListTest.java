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

import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MultiTaggerBossListTest
{
	/**
	 * The boss set is matched against a lowercased NPC name, so a capitalised entry would
	 * silently never match - the plugin would look correct and the boss would still be
	 * highlighted. Pin it, because nothing else would reveal it.
	 */
	@Test
	public void bossNamesAreAllLowercase()
	{
		for (String name : MultiTaggerPlugin.BOSS_NAMES)
		{
			assertEquals("boss name must be lowercase to ever match: " + name,
				name.toLowerCase(Locale.ROOT), name);
		}
	}

	@Test
	public void bossNamesAreTrimmedAndNonEmpty()
	{
		for (String name : MultiTaggerPlugin.BOSS_NAMES)
		{
			assertEquals("boss name must not have surrounding whitespace: '" + name + "'",
				name.trim(), name);
			assertTrue("boss name must not be empty", !name.isEmpty());
		}
	}

	/** Spot-check the encounters this exclusion exists for, including their minions. */
	@Test
	public void coversMultiCombatBossRooms()
	{
		// God Wars and the Ancient Prison are multi-combat, so the multi check alone
		// does not exclude them - which is the whole reason this set exists.
		assertBoss("General Graardor");
		assertBoss("Sergeant Strongstack");
		assertBoss("K'ril Tsutsaroth");
		assertBoss("Kree'arra");
		assertBoss("Commander Zilyana");
		assertBoss("Nex");
		assertBoss("Fumus");
		assertBoss("Blood reaver");
		assertBoss("Corporeal Beast");
	}

	/** Ordinary multi-combat targets must still be taggable. */
	@Test
	public void doesNotExcludeNormalSlayerTargets()
	{
		assertNotBoss("Dust devil");
		assertNotBoss("Nechryael");
		assertNotBoss("Abyssal demon");
		assertNotBoss("Bloodveld");
		assertNotBoss("Greater demon");
		assertNotBoss("Hydra");
	}

	private static void assertBoss(String name)
	{
		assertTrue("should be excluded as a boss: " + name,
			MultiTaggerPlugin.BOSS_NAMES.contains(name.toLowerCase(Locale.ROOT)));
	}

	private static void assertNotBoss(String name)
	{
		assertTrue("should NOT be excluded, it is a normal multi target: " + name,
			!MultiTaggerPlugin.BOSS_NAMES.contains(name.toLowerCase(Locale.ROOT)));
	}
}
