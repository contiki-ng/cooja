/*
 * Copyright (c) 2026, RISE Research Institutes of Sweden AB
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
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.mspmote.interfaces;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.mspmote.Msp430FR5969Mote;
import org.contikios.cooja.util.EventTriggers;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.platform.fr5969.FR5969Node;

/**
 * LED interface for MSP-EXP430FR5969 LaunchPad.
 *
 * LED1 (Red):   P1.0
 * LED2 (Green): P4.6
 */
@ClassDescription("MSP430FR5969 LEDs")
public class Msp430FR5969LED extends LED {

    private static final Color RED_ON = new Color(255, 0, 0);
    private static final Color RED_OFF = new Color(100, 0, 0);
    private static final Color GREEN_ON = new Color(0, 255, 0);
    private static final Color GREEN_OFF = new Color(0, 100, 0);

    private boolean redOn;
    private boolean greenOn;

    public Msp430FR5969LED(Mote mote) {
        var mspMote = (Msp430FR5969Mote) mote;

        // Listen to P1 for red LED (P1.0)
        IOPort port1 = mspMote.getCPU().getIOUnit(IOPort.class, "P1");
        if (port1 != null) {
            port1.addPortListener((source, data) -> {
                redOn = (data & FR5969Node.LED1_RED) != 0;
                triggers.trigger(EventTriggers.Update.UPDATE, mote);
            });
        }

        // Listen to P4 for green LED (P4.6)
        IOPort port4 = mspMote.getCPU().getIOUnit(IOPort.class, "P4");
        if (port4 != null) {
            port4.addPortListener((source, data) -> {
                greenOn = (data & FR5969Node.LED2_GREEN) != 0;
                triggers.trigger(EventTriggers.Update.UPDATE, mote);
            });
        }
    }

    @Override
    public boolean isAnyOn() {
        return redOn || greenOn;
    }

    @Override
    public boolean isGreenOn() {
        return greenOn;
    }

    @Override
    public boolean isRedOn() {
        return redOn;
    }

    @Override
    public boolean isYellowOn() {
        return false;  // No yellow LED on FR5969 LaunchPad
    }

    @Override
    public JPanel getInterfaceVisualizer() {
        var panel = new LedsPanel();
        triggers.addTrigger(panel, (operation, mote) -> EventQueue.invokeLater(panel::repaint));
        return panel;
    }

    @Override
    public void releaseInterfaceVisualizer(JPanel panel) {
        triggers.deleteTriggers(panel);
    }

    private class LedsPanel extends JPanel {
        private static final int D = 25;
        private static final int S = D + 15;

        LedsPanel() {
            super(null);
            Dimension d = new Dimension(5 + S + D + 5, 5 + D + 5);
            setMinimumSize(d);
            setPreferredSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final int height = getHeight();
            final int width = getWidth();
            final int y = (height - D) / 2;

            g.setColor(getBackground());
            g.fillRect(0, 0, width, height);

            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Red LED
            int x = 5;
            if (isRedOn()) {
                g.setColor(RED_ON);
                g.fillOval(x, y, D, D);
                g.setColor(Color.BLACK);
                g.drawOval(x, y, D, D);
            } else {
                g.setColor(RED_OFF);
                g.fillOval(x + 5, y + 5, D - 10, D - 10);
            }

            // Green LED
            x += S;
            if (isGreenOn()) {
                g.setColor(GREEN_ON);
                g.fillOval(x, y, D, D);
                g.setColor(Color.BLACK);
                g.drawOval(x, y, D, D);
            } else {
                g.setColor(GREEN_OFF);
                g.fillOval(x + 5, y + 5, D - 10, D - 10);
            }
        }
    }
}
