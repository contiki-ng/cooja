/*
 * Copyright (c) 2014, TU Braunschweig.
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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

package org.contikios.cooja.interfaces;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.Simulation;

/**
 * A Button represents a mote button. An implementation should notify all
 * observers when the button changes state, and may simulate external interrupts
 * by waking up a mote if button state changes.
 * 
 * @author Fredrik Osterlind
 */
@ClassDescription("Button")
public interface Button extends MoteInterface {
  /**
   * @return True if button is pressed
   */
  boolean isPressed();

  /**
   * Clicks button. Button will be pressed for some time and then automatically
   * released.
   */
  void clickButton();

  /**
   * Presses button.
   */
  void pressButton();

  /**
   * Releases button.
   */
  void releaseButton();

  abstract class AbstractButton implements Button {

    private final Simulation sim;

    private final MoteTimeEvent pressButtonEvent;
    private final MoteTimeEvent releaseButtonEvent;

    public AbstractButton(Mote mote) {
      sim = mote.getSimulation();
      pressButtonEvent = new MoteTimeEvent(mote) {
        @Override
        public void execute(long t) {
          doPressButton();
        }
      };
      releaseButtonEvent = new MoteTimeEvent(mote) {
        @Override
        public void execute(long t) {
          doReleaseButton();
        }
      };
    }

    @Override
    public void clickButton() {
      sim.invokeSimulationThread(() -> {
        sim.scheduleEvent(pressButtonEvent, sim.getSimulationTime());
        sim.scheduleEvent(releaseButtonEvent, sim.getSimulationTime() + Simulation.MILLISECOND);
      });
    }

    @Override
    public void pressButton() {
      sim.invokeSimulationThread(() -> sim.scheduleEvent(pressButtonEvent, sim.getSimulationTime()));
    }

    /**
     * Node-type dependent implementation of pressing a button.
     */
    protected abstract void doPressButton();

    @Override
    public void releaseButton() {
      sim.invokeSimulationThread(() -> sim.scheduleEvent(releaseButtonEvent, sim.getSimulationTime()));
    }

    /**
     * Node-type dependent implementation of releasing a button.
     */
    protected abstract void doReleaseButton();

    @Override
    public JPanel getInterfaceVisualizer() {
      final var clickButton = new JButton("Click button");
      clickButton.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          sim.invokeSimulationThread(() -> doPressButton());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          sim.invokeSimulationThread(() -> doReleaseButton());
        }
      });
      clickButton.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          sim.invokeSimulationThread(() -> doPressButton());
        }

        @Override
        public void keyReleased(KeyEvent e) {
          sim.invokeSimulationThread(() -> doReleaseButton());
        }
      });
      var panel = new JPanel();
      panel.add(clickButton);
      return panel;
    }

    @Override
    public void releaseInterfaceVisualizer(JPanel panel) {}
  }
}
