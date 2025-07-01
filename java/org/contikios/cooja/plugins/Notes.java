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

package org.contikios.cooja.plugins;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.plaf.basic.BasicInternalFrameUI;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.jdom2.Element;

@ClassDescription("Notes")
@PluginType(PluginType.PType.SIM_STANDARD_PLUGIN)
public class Notes extends VisPlugin {
  private final JTextArea notes = new JTextArea("Enter notes here");
  private boolean decorationsVisible = true;

  public Notes(Simulation simulation, Cooja gui) {
    super("Notes", gui);

    add(BorderLayout.CENTER, new JScrollPane(notes));

    /* Popup menu */
    if (Notes.this.getUI() instanceof BasicInternalFrameUI) {
      final JPopupMenu popup = new JPopupMenu();
      JMenuItem headerMenuItem = new JMenuItem("Toggle decorations");
      headerMenuItem.setEnabled(true);
      headerMenuItem.addActionListener(e -> setDecorationsVisible(!decorationsVisible));
      popup.add(headerMenuItem);
      notes.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popup.show(Notes.this, e.getX(), e.getY());
          }
        }
        @Override
        public void mouseReleased(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popup.show(Notes.this, e.getX(), e.getY());
          }
        }
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popup.show(Notes.this, e.getX(), e.getY());
          }
        }
      });
    }


    /* XXX HACK: here we set the position and size of the window when it appears on a blank simulation screen. */
    this.setLocation(400, 0);
    this.setSize(Cooja.getDesktopPane().getWidth() - 400, 160);
  }

  public String getNotes() {
    return notes.getText();
  }

  public void setNotes(String text) {
    this.notes.setText(text);
  }

  private void setDecorationsVisible(boolean visible) {
    // Some look and feels can make the north pane null.
    if (getUI() instanceof BasicInternalFrameUI frameUI && frameUI.getNorthPane() != null) {
      frameUI.getNorthPane().setPreferredSize(visible ? null : new Dimension(0, 0));
      revalidate();
      EventQueue.invokeLater(Notes.this::repaint);
      decorationsVisible = visible;
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    element = new Element("notes");
    element.setText(notes.getText());
    config.add(element);

    element = new Element("decorations");
    element.setText(String.valueOf(decorationsVisible));
    config.add(element);

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      if (element.getName().equals("notes")) {
        notes.setText(element.getText());
      }
      if (element.getName().equals("decorations")) {
        decorationsVisible = Boolean.parseBoolean(element.getText());
        setDecorationsVisible(decorationsVisible);
      }
    }
    return true;
  }
}
