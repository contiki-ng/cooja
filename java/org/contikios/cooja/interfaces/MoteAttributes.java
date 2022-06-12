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

package org.contikios.cooja.interfaces;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.plugins.skins.AttributeVisualizerSkin;

/**
 * MoteAttributes used to store mote attributes for debugging and statistics
 * The interface is write-only: the simulated Contiki has no knowledge of current attributes
 * with other motes. The Contiki application can, however, add and remove attributes.
 *
 * A Contiki application adds/removes a relation by outputting a simple messages on its log interface,
 * typically via printf()'s of the serial port.
 *
 * Mote attributes are visualized by {@link AttributeVisualizerSkin}.
 * 
 * Syntax:
 * "#A <Attribute Name>=<Attribute Value>"
 * "#A <Attribute Name>=<Attribute Value>;<Color>"
 *
 * Example, add an attribute 'sent' with value 41:
 * "#A sent=41"
 * 
 * Example, add an attribute 'sent' with value 41, visualized in red:
 * "#A sent=41;RED"
 *
 * Example, remove attribute 'sent' (if any):
 * "#A sent"
 *
 * Special attribute example, visualizes mote in red:
 * "#A color=RED"
 *
 * @see AttributeVisualizerSkin
 * @author Joakim Eriksson
 */
@ClassDescription("Mote Attributes")
public class MoteAttributes extends MoteInterface {
  private static final Logger logger = LogManager.getLogger(MoteAttributes.class);
  private Mote mote = null;

  private final HashMap<String, Object> attributes = new HashMap<>();

  private Observer logObserver = new Observer() {
    @Override
    public void update(Observable o, Object arg) {
      String msg = ((Log) o).getLastLogMessage();
      handleNewLog(msg);
    }
  };
  
  public MoteAttributes(Mote mote) {
    this.mote = mote;
  }

  @Override
  public void added() {
    super.added();
    
    /* Observe log interfaces */
    for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
      if (mi instanceof Log) {
        mi.addObserver(logObserver);
      }
    }
  }
  
  @Override
  public void removed() {
    super.removed();

    /* Stop observing log interfaces */
    for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
      if (mi instanceof Log) {
        mi.deleteObserver(logObserver);
      }
    }
    logObserver = null;
  }

  private void handleNewLog(String msg) {
    if (msg == null) {
      return;
    }

    if (msg.startsWith("DEBUG: ")) {
      msg = msg.substring("DEBUG: ".length());
    }
    
    if (!msg.startsWith("#A ")) {
      return;
    }
    /* remove "#A " */
    msg = msg.substring(3);
    
    setAttributes(msg);

    setChanged();
    notifyObservers();
  }

  private void setAttributes(String att) {
    if (att.indexOf(",") >= 0) {
      /* Handle each attribute separately */
      for (String s : att.split(",")) {
        setAttributes(s);
      }
      return;
    }

    String[] args = att.split("=");
    if (args.length == 2) {
      attributes.put(args[0], args[1]);
    } else if (args.length == 1) {
      attributes.remove(args[0]);
    } else {
      /* ignore */
      logger.warn(mote + ": Malformed attribute was ignored: " + att);
    }
  }
  
  public String getText() {
      StringBuilder sb = new StringBuilder();
    for (Object key : attributes.keySet().toArray()) {
      sb.append(key).append("=").append(attributes.get(key)).append("\n");
    }
      return sb.toString();
  }
  
  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());

    final JTextArea attributes = new JTextArea();
    attributes.setEditable(false);
    panel.add(attributes);
    attributes.setText(getText());

    Observer observer;
    this.addObserver(observer = new Observer() {
      @Override
      public void update(Observable obs, Object obj) {
          attributes.setText(getText());
      }
    });

    // Saving observer reference for releaseInterfaceVisualizer
    panel.putClientProperty("intf_obs", observer);

    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    Observer observer = (Observer) panel.getClientProperty("intf_obs");
    if (observer == null) {
      logger.fatal("Error when releasing panel, observer is null");
      return;
    }
    this.deleteObserver(observer);
  }

  @Override
  public Collection<Element> getConfigXML() {
    return null;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
  }

}
