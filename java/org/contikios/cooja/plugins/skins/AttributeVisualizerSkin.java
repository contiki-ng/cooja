/*
 * Copyright (c) 2010, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.plugins.skins;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.util.function.BiConsumer;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.ui.ColorUtils;
import org.contikios.cooja.util.EventTriggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visualizer skin for mote attributes.
 *
 * @see MoteAttributes
 * @author Fredrik Osterlind
 */
@ClassDescription("Mote attributes")
public class AttributeVisualizerSkin implements VisualizerSkin {
  private static final Logger logger = LoggerFactory.getLogger(AttributeVisualizerSkin.class);

  private Simulation simulation;
  private Visualizer visualizer;

  private final BiConsumer<EventTriggers.AddRemoveUpdate, MoteAttributes.MoteAttributeUpdateData> attributesTrigger = (obs, obj) -> visualizer.repaint();
  private final BiConsumer<EventTriggers.AddRemove, Mote> newMotesListener = (event, mote) -> {
    var intf = mote.getInterfaces().getInterfaceOfType(MoteAttributes.class);
    if (intf != null) {
      if (event == EventTriggers.AddRemove.ADD) {
        intf.getAttributesTriggers().addTrigger(this, attributesTrigger);
      } else {
        intf.getAttributesTriggers().removeTrigger(this, attributesTrigger);
      }
    }
  };

  @Override
  public void setActive(Simulation simulation, Visualizer vis) {
    this.simulation = simulation;
    this.visualizer = vis;

    simulation.getMoteTriggers().addTrigger(this, newMotesListener);
    for (Mote m: simulation.getMotes()) {
      newMotesListener.accept(EventTriggers.AddRemove.ADD, m);
    }
  }

  @Override
  public void setInactive() {
    simulation.getMoteTriggers().removeTrigger(this, newMotesListener);
    for (Mote m: simulation.getMotes()) {
      newMotesListener.accept(EventTriggers.AddRemove.REMOVE, m);
    }
  }

  @Override
  public Color[] getColorOf(Mote mote) {
    String[] as = getAttributesStrings(mote);
    if (as == null) {
      return null;
    }

    Color color = null;
    for (String a: as) {
      if (a.startsWith("color=")) {
        String colorString = a.substring("color=".length());
        color = parseAttributeColor(colorString);
      }
    }
    if (color == null) {
      return null;
    }
    return new Color[] { color };
  }

  private static Color parseAttributeColor(String colorString) {
    var color = ColorUtils.decodeColor(colorString);
    if (color == null && colorString != null) {
      logger.warn("Unknown color attribute: " + colorString);
    }
    return color;
  }

  @Override
  public void paintBeforeMotes(Graphics g) {
  }

  private static String[] getAttributesStrings(Mote mote) {
    MoteAttributes intf = mote.getInterfaces().getInterfaceOfType(MoteAttributes.class);
    if (intf == null) {
      return null;
    }
    String text = intf.getText();
    if (text == null) {
      return null;
    }
    
    return text.split("\n");
  }
  
  @Override
  public void paintAfterMotes(Graphics g) {
    FontMetrics fm = g.getFontMetrics();
    g.setColor(Color.BLACK);

    /* Paint attributes below motes */
    Mote[] allMotes = simulation.getMotes();
    for (Mote mote: allMotes) {
      String[] as = getAttributesStrings(mote);
      if (as == null) {
        continue;
      }
      
      Position pos = mote.getInterfaces().getPosition();
      Point pixel = visualizer.transformPositionToPixel(pos);

      int y = pixel.y + 2*Visualizer.MOTE_RADIUS + 3;
      for (String a: as) {
        if (a.startsWith("color=")) {
          /* Ignore */
          continue;
        }

        Color color = null;
        if (a.contains(";")) {
          String[] args = a.split(";");
          color = parseAttributeColor(args[1]);
          a = args[0];
        }
        if (color != null) {
          g.setColor(color);
        }
        
        int msgWidth = fm.stringWidth(a);
        g.drawString(a, pixel.x - msgWidth/2, y);
        y += fm.getHeight();

        if (color != null) {
          g.setColor(Color.BLACK);
        }
      }
    }
  }

  @Override
  public Visualizer getVisualizer() {
    return visualizer;
  }
}
