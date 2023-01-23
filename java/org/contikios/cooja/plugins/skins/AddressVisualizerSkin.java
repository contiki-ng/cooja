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
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;
import java.util.Optional;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.plugins.Visualizer.MoteMenuAction;
import org.contikios.cooja.util.AnyMoteEventTriggers;
import org.contikios.cooja.util.EventTriggers;

/**
 * Visualizer skin for mote addresses.
 * <p>
 * Paints the address below each mote.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("IP addresses")
public class AddressVisualizerSkin implements VisualizerSkin {
  private Simulation simulation;
  private Visualizer visualizer;

  private AnyMoteEventTriggers<EventTriggers.Update> newMotesListener;

  @Override
  public void setActive(Simulation simulation, Visualizer vis) {
    this.simulation = simulation;
    this.visualizer = vis;
    Objects.requireNonNullElseGet(newMotesListener, () ->
            newMotesListener = new AnyMoteEventTriggers<>(simulation, mote -> {
              var ipAddr = mote.getInterfaces().getIPAddress();
              return ipAddr == null ? Optional.empty() : Optional.of(ipAddr.getTriggers());
            })).addTrigger(this, (event, mote) -> visualizer.repaint());
    visualizer.registerMoteMenuAction(CopyAddressAction.class);
  }

  @Override
  public void setInactive() {
    newMotesListener.deleteTriggers(this);
    visualizer.unregisterMoteMenuAction(CopyAddressAction.class);
  }

  @Override
  public Color[] getColorOf(Mote mote) {
    return null;
  }

  @Override
  public void paintBeforeMotes(Graphics g) {
  }

  private static String getMoteString(Mote mote) {
    IPAddress ipAddr = mote.getInterfaces().getIPAddress();
    if ((ipAddr != null) && (ipAddr.hasIP())) {
      if (ipAddr.getLocalIP() == null) {
        return "";
      }
      return ipAddr.getLocalIP().toString();
    }
    return null;
  }
  
  @Override
  public void paintAfterMotes(Graphics g) {
    FontMetrics fm = g.getFontMetrics();
    g.setColor(Color.BLACK);

    /* Paint last output below motes */
    Mote[] allMotes = simulation.getMotes();
    for (Mote mote: allMotes) {
      String msg = getMoteString(mote);
      if (msg == null) {
        continue;
      }
      
      Position pos = mote.getInterfaces().getPosition();
      Point pixel = visualizer.transformPositionToPixel(pos);

      int msgWidth = fm.stringWidth(msg);
      g.drawString(msg, pixel.x - msgWidth/2, pixel.y + 2*Visualizer.MOTE_RADIUS + 3);
    }
  }

  public static class CopyAddressAction implements MoteMenuAction {
    @Override
    public boolean isEnabled(Visualizer visualizer, Mote mote) {
      return true;
    }

    @Override
    public String getDescription(Visualizer visualizer, Mote mote) {
      return "Copy address to clipboard: \"" + getMoteString(mote) + "\"";
    }

    @Override
    public void doAction(Visualizer visualizer, Mote mote) {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      StringSelection stringSelection = new StringSelection(getMoteString(mote));
      clipboard.setContents(stringSelection, null);
    }
  }

  @Override
  public Visualizer getVisualizer() {
    return visualizer;
  }
}
