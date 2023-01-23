/*
 * Copyright (c) 2011, Swedish Institute of Computer Science.
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
 */

package org.contikios.cooja.mspmote.interfaces;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.mspmote.Exp5438Mote;
import org.contikios.cooja.util.EventTriggers;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.IOUnit;
import se.sics.mspsim.platform.ti.Exp5438Node;

/**
 * @author Fredrik Osterlind
 */
@ClassDescription("Exp5438 LEDs")
public class Exp5438LED extends LED {
  private boolean redOn;
  private boolean yellowOn;

  private static final Color RED = new Color(255, 0, 0);
  private static final Color DARK_RED = new Color(100, 0, 0);
  private static final Color YELLOW = new Color(255, 255, 0);
  private static final Color DARK_YELLOW = new Color(184,134,11);

  public Exp5438LED(Mote mote) {
    var mspMote = (Exp5438Mote) mote;
    IOUnit unit = mspMote.getCPU().getIOUnit("P1");
    if (unit instanceof IOPort) {
      ((IOPort) unit).addPortListener((source, data) -> {
        boolean oldRedOn = redOn;
        boolean oldYellowOn = yellowOn;
        redOn = (data & Exp5438Node.LEDS_CONF_RED) != 0;
        yellowOn = (data & Exp5438Node.LEDS_CONF_YELLOW) != 0;
        if (oldRedOn != redOn || oldYellowOn != yellowOn) {
          triggers.trigger(EventTriggers.Update.UPDATE, mote);
        }
      });
    }
  }

  @Override
  public boolean isAnyOn() {
    return redOn || yellowOn;
  }

  @Override
  public boolean isGreenOn() {
    return false; /* does not exist */
  }

  @Override
  public boolean isRedOn() {
	  return redOn;
  }

  @Override
  public boolean isYellowOn()  {
    return yellowOn;
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    final JPanel panel = new JPanel() {
	@Override
	public void paintComponent(Graphics g) {
        super.paintComponent(g);

        int x = 20;
        int y = 25;
        int d = 25;

        if (isRedOn()) {
          g.setColor(RED);
          g.fillOval(x, y, d, d);
          g.setColor(Color.BLACK);
          g.drawOval(x, y, d, d);
        } else {
          g.setColor(DARK_RED);
          g.fillOval(x + 5, y + 5, d-10, d-10);
        }

        x += 40;

        if (isYellowOn()) {
          g.setColor(YELLOW);
          g.fillOval(x, y, d, d);
          g.setColor(Color.BLACK);
          g.drawOval(x, y, d, d);
        } else {
          g.setColor(DARK_YELLOW);
          g.fillOval(x + 5, y + 5, d-10, d-10);
        }
      }
    };
    panel.setMinimumSize(new Dimension(140, 60));
    panel.setPreferredSize(new Dimension(140, 60));
    triggers.addTrigger(panel, (operation, mote) -> EventQueue.invokeLater(panel::repaint));
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    triggers.deleteTriggers(panel);
  }
}
