/*
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

import java.awt.EventQueue;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;

/**
 * Mote 3D position.
 *
 * <p>
 * This observable notifies when the position is changed.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Position")
public class Position implements MoteInterface {
  private final Mote mote;
  private double x;
  private double y;
  private double z;
  private final LinkedHashMap<JPanel, JLabel> labels = new LinkedHashMap<>();
  private final EventTriggers<EventTriggers.Update, Mote> eventTriggers = new EventTriggers<>();

  /**
   * Creates a position for given mote with coordinates (x=0, y=0, z=0).
   *
   * @param mote Mote the position belongs to.
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public Position(Mote mote) {
    this.mote = mote;
  }

  /**
   * Set position to (x,y,z).
   *
   * @param x New X coordinate
   * @param y New Y coordinate
   * @param z New Z coordinate
   */
  public void setCoordinates(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
    if (Cooja.isVisualized()) {
      EventQueue.invokeLater(() -> {
        final var number = NumberFormat.getNumberInstance();
        for (var label : labels.values()) {
          label.setText("x=" + number.format(getXCoordinate()) + " "
                  + "y=" + number.format(getYCoordinate()) + " "
                  + "z=" + number.format(getZCoordinate()));
        }
      });
    }
    eventTriggers.trigger(EventTriggers.Update.UPDATE, mote);
  }

  /**
   * @return X coordinate
   */
  public double getXCoordinate() {
    return x;
  }

  /**
   * @return Y coordinate
   */
  public double getYCoordinate() {
    return y;
  }

  /**
   * @return Z coordinate
   */
  public double getZCoordinate() {
    return z;
  }

  /**
   * Calculates distance from this position to given position.
   *
   * @param pos Compared position
   * @return Distance
   */
  public double getDistanceTo(Position pos) {
    var xDiff = Math.abs(x - pos.getXCoordinate());
    var yDiff = Math.abs(y - pos.getYCoordinate());
    var zDiff = Math.abs(z - pos.getZCoordinate());
    return Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
  }

  /**
   * Calculates distance from associated mote to another mote.
   *
   * @param m Another mote
   * @return Distance
   */
  public double getDistanceTo(Mote m) {
    return getDistanceTo(m.getInterfaces().getPosition());
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    final NumberFormat form = NumberFormat.getNumberInstance();

    final JLabel positionLabel = new JLabel();
    positionLabel.setText("x=" + form.format(getXCoordinate()) + " "
        + "y=" + form.format(getYCoordinate()) + " "
        + "z=" + form.format(getZCoordinate()));

    panel.add(positionLabel);
    labels.put(panel, positionLabel);
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }

  @Override
  public Collection<Element> getConfigXML() {
    var config = new ArrayList<Element>();

    var element = new Element("pos");
    element.setAttribute("x", String.valueOf(getXCoordinate()));
    element.setAttribute("y", String.valueOf(getYCoordinate()));
    var z = getZCoordinate();
    if (z != 0) {
      element.setAttribute("z", String.valueOf(z));
    }
    config.add(element);

    return config;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    double x = 0, y = 0, z = 0;

    for (Element element : configXML) {
      switch (element.getName()) {
        case "x" -> x = Double.parseDouble(element.getText());
        case "y" -> y = Double.parseDouble(element.getText());
        case "z" -> z = Double.parseDouble(element.getText());
        case "pos" -> {
          x = getAttributeAsDouble(element, "x", x);
          y = getAttributeAsDouble(element, "y", y);
          z = getAttributeAsDouble(element, "z", z);
        }
      }
    }

    setCoordinates(x, y, z);
  }

  public EventTriggers<EventTriggers.Update, Mote> getPositionTriggers() {
     return eventTriggers;
  }

  private static double getAttributeAsDouble(Element element, String name, double defaultValue) {
    String value = element.getAttributeValue(name);
    return value == null ? defaultValue : Double.parseDouble(value);
  }
}
