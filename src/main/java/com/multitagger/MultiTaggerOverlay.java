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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

/**
 * Renders Multi Tagger's own highlights, deliberately NOT via the shared
 * {@link net.runelite.client.game.npcoverlay.NpcOverlayService}. That service only lets
 * ONE registered highlighter draw per NPC (first registered wins), so if another plugin
 * (e.g. the built-in NPC Indicators) also highlights the same NPC, our tag would be
 * silently dropped and the user sees nothing. Drawing our own overlay guarantees the
 * tag always renders, layered on top of any other highlighter, so the two never hide
 * each other.
 */
class MultiTaggerOverlay extends Overlay
{
	private final MultiTaggerPlugin plugin;
	private final MultiTaggerConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private MultiTaggerOverlay(MultiTaggerPlugin plugin, MultiTaggerConfig config,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.plugin = plugin;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Color border = config.highlightColor();
		final Color fill = config.fillColor();
		final float width = 2f;

		for (NPC npc : plugin.getHighlightedNpcs())
		{
			NPCComposition comp = npc.getTransformedComposition();
			if (comp == null || !comp.isInteractible())
			{
				continue;
			}

			if (config.highlightHull())
			{
				renderPoly(graphics, border, width, fill, npc.getConvexHull());
			}

			if (config.highlightTile())
			{
				renderPoly(graphics, border, width, fill, npc.getCanvasTilePoly());
			}

			if (config.highlightOutline())
			{
				modelOutlineRenderer.drawOutline(npc, (int) width, border, 0);
			}

			if (config.drawNames() && npc.getName() != null)
			{
				String name = Text.removeTags(npc.getName());
				Point loc = npc.getCanvasTextLocation(graphics, name, npc.getLogicalHeight() + 40);
				if (loc != null)
				{
					OverlayUtil.renderTextLocation(graphics, loc, name, border);
				}
			}
		}

		return null;
	}

	private void renderPoly(Graphics2D graphics, Color border, float width, Color fill, Shape poly)
	{
		if (poly != null)
		{
			graphics.setColor(border);
			graphics.setStroke(new BasicStroke(width));
			graphics.draw(poly);
			graphics.setColor(fill);
			graphics.fill(poly);
		}
	}
}
