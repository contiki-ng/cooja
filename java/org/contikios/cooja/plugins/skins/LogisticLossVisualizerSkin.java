/*
 * Copyright (c) 2009-2013, Swedish Institute of Computer Science, TU Braunscheig
 * Copyright (c) 2018, University of Bristol
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
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.util.Set;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.radiomediums.LogisticLoss;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visualizer skin for configuring the LogisticLoss radio medium.
 * <p>
 * To also see radio traffic, this skin can be combined with {@link
 * TrafficVisualizerSkin}.
 *
 * @see TrafficVisualizerSkin
 * @see LogisticLoss
 * @author Fredrik Osterlind
 * @author Enrico Joerns
 * @author Atis Elsts
 */
@ClassDescription("Radio environment (LogisticLoss)")
@SupportedArguments(radioMediums = {LogisticLoss.class})
public class LogisticLossVisualizerSkin implements VisualizerSkin {

  private static final Logger logger = LoggerFactory.getLogger(LogisticLossVisualizerSkin.class);

  private static final Color COLOR_TX = new Color(0, 255, 0, 100);
  private static final Color COLOR_INT = new Color(50, 50, 50, 100);

  private Simulation simulation;
  private Visualizer visualizer;
  private LogisticLoss radioMedium;

  @Override
  public void setActive(Simulation simulation, Visualizer vis) {
    if (!(simulation.getRadioMedium() instanceof LogisticLoss)) {
      logger.error("Cannot activate LogisticLoss skin for unknown radio medium: " + simulation.getRadioMedium());
      return;
    }
    this.simulation = simulation;
    this.visualizer = vis;
    this.radioMedium = (LogisticLoss) simulation.getRadioMedium();
  }

  @Override
  public void setInactive() {
    if (simulation == null) {
      /* Skin was never activated */
    }
  }

  @Override
  public Color[] getColorOf(Mote mote) {
    return null;
  }

  @Override
  public void paintBeforeMotes(Graphics g) {
    Set<Mote> selectedMotes = visualizer.getSelectedMotes();
    if (simulation == null || selectedMotes == null) {
      return;
    }

    Area intRangeArea = new Area();
    Area intRangeMaxArea = new Area();
    Area trxRangeArea = new Area();
    Area trxRangeMaxArea = new Area();

    for (Mote selectedMote : selectedMotes) {
      if (selectedMote.getInterfaces().getRadio() == null) {
        continue;
      }

      /* Paint transmission and interference range for selected mote */
      Position motePos = selectedMote.getInterfaces().getPosition();

      Point pixelCoord = visualizer.transformPositionToPixel(motePos);
      int x = pixelCoord.x;
      int y = pixelCoord.y;

      // Fetch current output power indicator (scale with as percent)
      double moteInterferenceRange
              = radioMedium.INTERFERENCE_RANGE;
      double moteTransmissionRange
              = radioMedium.TRANSMITTING_RANGE;

      Point translatedZero = visualizer.transformPositionToPixel(0.0, 0.0, 0.0);
      Point translatedInterference
              = visualizer.transformPositionToPixel(moteInterferenceRange, moteInterferenceRange, 0.0);
      Point translatedTransmission
              = visualizer.transformPositionToPixel(moteTransmissionRange, moteTransmissionRange, 0.0);
      Point translatedInterferenceMax
              = visualizer.transformPositionToPixel(radioMedium.INTERFERENCE_RANGE, radioMedium.INTERFERENCE_RANGE, 0.0);
      Point translatedTransmissionMax
              = visualizer.transformPositionToPixel(radioMedium.TRANSMITTING_RANGE, radioMedium.TRANSMITTING_RANGE, 0.0);

      translatedInterference.x = Math.abs(translatedInterference.x - translatedZero.x);
      translatedInterference.y = Math.abs(translatedInterference.y - translatedZero.y);
      translatedTransmission.x = Math.abs(translatedTransmission.x - translatedZero.x);
      translatedTransmission.y = Math.abs(translatedTransmission.y - translatedZero.y);
      translatedInterferenceMax.x = Math.abs(translatedInterferenceMax.x - translatedZero.x);
      translatedInterferenceMax.y = Math.abs(translatedInterferenceMax.y - translatedZero.y);
      translatedTransmissionMax.x = Math.abs(translatedTransmissionMax.x - translatedZero.x);
      translatedTransmissionMax.y = Math.abs(translatedTransmissionMax.y - translatedZero.y);

      /* Interference range */
      intRangeArea.add(new Area(new Ellipse2D.Double(
              x - translatedInterference.x,
              y - translatedInterference.y,
              2 * translatedInterference.x,
              2 * translatedInterference.y)));

      /* Interference range (MAX) */
      trxRangeArea.add(new Area(new Ellipse2D.Double(
              x - translatedTransmission.x,
              y - translatedTransmission.y,
              2 * translatedTransmission.x,
              2 * translatedTransmission.y)));

      intRangeMaxArea.add(new Area(new Ellipse2D.Double(
              x - translatedInterferenceMax.x,
              y - translatedInterferenceMax.y,
              2 * translatedInterferenceMax.x,
              2 * translatedInterferenceMax.y)));

      /* Transmission range (MAX) */
      trxRangeMaxArea.add(new Area(new Ellipse2D.Double(
              x - translatedTransmissionMax.x,
              y - translatedTransmissionMax.y,
              2 * translatedTransmissionMax.x,
              2 * translatedTransmissionMax.y)));

    }

    Graphics2D g2d = (Graphics2D) g;

    g2d.setColor(COLOR_INT);
    g2d.fill(intRangeArea);
    g.setColor(Color.GRAY);
    g2d.draw(intRangeMaxArea);

    g.setColor(COLOR_TX);
    g2d.fill(trxRangeArea);
    g.setColor(Color.GRAY);
    g2d.draw(trxRangeMaxArea);

    FontMetrics fm = g.getFontMetrics();
    g.setColor(Color.BLACK);

    /* Print transmission success probabilities only if single mote is selected */
    if (selectedMotes.size() == 1) {
      Mote selectedMote = selectedMotes.toArray(new Mote[0])[0];
      Radio selectedRadio = selectedMote.getInterfaces().getRadio();
      for (Mote m : simulation.getMotes()) {
        if (m == selectedMote) {
          continue;
        }
        double prob
                = ((LogisticLoss) simulation.getRadioMedium()).getSuccessProbability(selectedRadio, m.getInterfaces().getRadio());
        if (prob == 0.0d) {
          continue;
        }
        String msg = (((int) (1000 * prob)) / 10.0) + "%";
        Position pos = m.getInterfaces().getPosition();
        Point pixel = visualizer.transformPositionToPixel(pos);
        int msgWidth = fm.stringWidth(msg);
        g.drawString(msg, pixel.x - msgWidth / 2, pixel.y + 2 * Visualizer.MOTE_RADIUS + 3);
      }
    }

  }

  @Override
  public void paintAfterMotes(Graphics g) {
  }

  @Override
  public Visualizer getVisualizer() {
    return visualizer;
  }
}
