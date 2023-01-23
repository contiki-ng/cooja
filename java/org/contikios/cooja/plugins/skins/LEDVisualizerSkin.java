/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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
import java.awt.Graphics;
import java.awt.Point;
import java.util.Optional;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.util.AnyMoteEventTriggers;
import org.contikios.cooja.util.EventTriggers;

/**
 * Visualizer skin for LEDs.
 * <p>
 * Paints three LEDs left to each mote.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("LEDs")
public class LEDVisualizerSkin implements VisualizerSkin {
  private Simulation simulation;
  private Visualizer visualizer;
  private AnyMoteEventTriggers<EventTriggers.Update> ledTriggers;

  @Override
  public void setActive(Simulation simulation, Visualizer vis) {
    this.simulation = simulation;
    this.visualizer = vis;
    if (ledTriggers == null) {
      ledTriggers = new AnyMoteEventTriggers<>(simulation, mote -> {
        var ledInterface = mote.getInterfaces().getLED();
        return ledInterface != null ? Optional.of(ledInterface.getTriggers()) : Optional.empty();
      });
    }
    ledTriggers.addTrigger(this, (operation, mote) -> visualizer.repaint());
  }

  @Override
  public void setInactive() {
    if (ledTriggers != null) {
      ledTriggers.deleteTriggers(this);
    }
  }

  @Override
  public Color[] getColorOf(Mote mote) {
    return null;
  }

  @Override
  public void paintBeforeMotes(Graphics g) {
  }

  @Override
  public void paintAfterMotes(Graphics g) {
    /* Paint LEDs left of each mote */
    Mote[] allMotes = simulation.getMotes();
    for (Mote mote: allMotes) {
      LED leds = mote.getInterfaces().getLED();
      if (leds == null) {
        continue;
      }

      Position pos = mote.getInterfaces().getPosition();
      Point pixel = visualizer.transformPositionToPixel(pos);

      int x = pixel.x - 2*Visualizer.MOTE_RADIUS;
      
      int y = pixel.y - Visualizer.MOTE_RADIUS;
      g.setColor(Color.RED);
      if (leds.isRedOn()) {
        g.fillRect(x, y, 7, 4);
      } else {
        g.drawRect(x, y, 7, 4);
      }

      y += 6;
      g.setColor(Color.GREEN);
      if (leds.isGreenOn()) {
        g.fillRect(x, y, 7, 4);
      } else {
        g.drawRect(x, y, 7, 4);
      }

      y += 6;
      g.setColor(Color.BLUE);
      if (leds.isYellowOn()) {
        g.fillRect(x, y, 7, 4);
      } else {
        g.drawRect(x, y, 7, 4);
      }
    }
  }

  @Override
  public Visualizer getVisualizer() {
    return visualizer;
  }
}
