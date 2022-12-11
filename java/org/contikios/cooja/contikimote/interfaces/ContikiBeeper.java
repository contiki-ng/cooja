/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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

package org.contikios.cooja.contikimote.interfaces;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.LinkedHashMap;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Beeper;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;

/**
 * Beeper mote interface.
 * <p>
 * Contiki variables:
 * <ul>
 * <li>char simBeeped (1=on, else off)
 * </ul>
 * <p>
 *
 * This observable is changed and notifies observers when the mote beeps.
 *
 * @author Fredrik Osterlind
 */
public class ContikiBeeper implements Beeper, PolledAfterActiveTicks {
  private final Mote mote;
  private final VarMemory moteMem;
  /** Ordered map of labels that are updated when mote beeps. */
  private final LinkedHashMap<JPanel, JLabel> labels = new LinkedHashMap<>();
  /** The time of the last beep */
  private long lastBeepTime;

  /**
   * Creates an interface to the beeper at mote.
   *
   * @param mote
   *          Beeper's mote.
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiBeeper(Mote mote) {
    this.mote = mote;
    this.moteMem = new VarMemory(mote.getMemory());
  }

  @Override
  public boolean isBeeping() {
    return moteMem.getByteValueOf("simBeeped") == 1;
  }

  @Override
  public void doActionsAfterTick() {
    if (moteMem.getByteValueOf("simBeeped") == 1) {
      lastBeepTime = mote.getSimulation().getSimulationTime();
      if (Cooja.isVisualized()) {
        java.awt.EventQueue.invokeLater(() -> {
          if (labels.isEmpty()) {
            return;
          }
          for (var label : labels.values()) {
            label.setText("Last beep at time: " + lastBeepTime);
          }
          // Beep on speakers.
          Toolkit.getDefaultToolkit().beep();
        });
      }
      moteMem.setByteValueOf("simBeeped", (byte) 0);
    }
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    final JLabel statusLabel = new JLabel("Last beep at time: " + (lastBeepTime == 0 ? "?" : String.valueOf(lastBeepTime)));
    panel.add(statusLabel);
    panel.setMinimumSize(new Dimension(140, 60));
    panel.setPreferredSize(new Dimension(140, 60));
    labels.put(panel, statusLabel);
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }
}
