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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.jdom2.Element;

/**
 * A MoteID represents a mote ID number. An implementation should notify all
 * observers if the mote ID is set or changed.
 * 
 * @author Fredrik Osterlind
 */
@ClassDescription("ID")
public abstract class MoteID<M extends Mote> implements MoteInterface {
  protected final LinkedHashMap<JPanel, JLabel> labels = new LinkedHashMap<>();
  protected final M mote;
  protected int moteID = -1;

  protected MoteID(M mote) {
    this.mote = mote;
  }

  /**
   * @return Current mote ID number
   */
  public int getMoteID() {
    return moteID;
  }

  /**
   * Sets mote ID to given number.
   * @param id New mote ID number
   */
  public void setMoteID(int id) {
    moteID = id;
    if (Cooja.isVisualized()) {
      EventQueue.invokeLater(() -> {
        for (var label : labels.values()) {
          label.setText("Mote ID: " + id);
        }
      });
    }
  }
  
  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element = new Element("id");
    element.setText(Integer.toString(getMoteID()));
    config.add(element);
    return config;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      if (element.getName().equals("id")) {
        setMoteID(Integer.parseInt(element.getText()));
        break;
      }
    }
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    var panel = new JPanel();
    final var idLabel = new JLabel("Mote ID: " + getMoteID());
    panel.add(idLabel);
    labels.put(panel, idLabel);
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }
}
