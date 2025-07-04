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

package org.contikios.cooja.plugins;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MotePlugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.jdom2.Element;

/**
 * Mote Interface Viewer views information about a specific mote interface.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Mote Interface Viewer")
@PluginType(PluginType.PType.MOTE_PLUGIN)
public class MoteInterfaceViewer extends VisPlugin implements HasQuickHelp, MotePlugin {
  private final Mote mote;
  private MoteInterface selectedMoteInterface;
  private JPanel currentInterfaceVisualizer;
  private final JComboBox<String> selectInterfaceComboBox = new JComboBox<>();
  private final JScrollPane mainScrollPane;

  /**
   * Create a new mote interface viewer.
   *
   * @param moteToView Mote to view
   */
  public MoteInterfaceViewer(Mote moteToView, Simulation simulation, Cooja gui) {
    super("Mote Interface Viewer (" + moteToView + ")", gui);
    mote = moteToView;

    JLabel label;
    JPanel mainPane = new JPanel(new BorderLayout());
    JPanel smallPane;

    // Select interface combo box
    smallPane = new JPanel(new BorderLayout());
    smallPane.add(new JSeparator(), BorderLayout.SOUTH);

    label = new JLabel("Select interface:");

    final JPanel interfacePanel = new JPanel(new BorderLayout());

    Collection<MoteInterface> intfs = mote.getInterfaces().getInterfaces();
    for (MoteInterface intf : intfs) {
      selectInterfaceComboBox.addItem(Cooja.getDescriptionOf(intf));
    }

    selectInterfaceComboBox.addActionListener(e -> {

      // Release old interface visualizer if any
      if (selectedMoteInterface != null && currentInterfaceVisualizer != null) {
        selectedMoteInterface.releaseInterfaceVisualizer(currentInterfaceVisualizer);
      }

      // View selected interface if any
      interfacePanel.removeAll();
      String interfaceDescription = (String) selectInterfaceComboBox.getSelectedItem();
      selectedMoteInterface = null;
      for (var intf : mote.getInterfaces().getInterfaces()) {
        if (Cooja.getDescriptionOf(intf).equals(interfaceDescription)) {
          selectedMoteInterface = intf;
          Cooja.loadQuickHelp(MoteInterfaceViewer.this);
          break;
        }
      }
      currentInterfaceVisualizer = selectedMoteInterface.getInterfaceVisualizer();
      if (currentInterfaceVisualizer != null) {
        interfacePanel.add(BorderLayout.CENTER, currentInterfaceVisualizer);
        currentInterfaceVisualizer.setVisible(true);
      } else {
        interfacePanel.add(new JLabel("No interface visualizer", JLabel.CENTER));
        currentInterfaceVisualizer = null;
      }
      setSize(getSize());
    });
    selectInterfaceComboBox.setSelectedIndex(0);

    smallPane.add(BorderLayout.WEST, label);
    smallPane.add(BorderLayout.EAST, selectInterfaceComboBox);
    mainPane.add(BorderLayout.NORTH, smallPane);

    // Add selected interface
    if (selectInterfaceComboBox.getItemCount() > 0) {
      selectInterfaceComboBox.setSelectedIndex(0);
      selectInterfaceComboBox.dispatchEvent(new ActionEvent(selectInterfaceComboBox, ActionEvent.ACTION_PERFORMED, ""));
    }

    mainPane.add(BorderLayout.CENTER, interfacePanel);
    mainScrollPane = new JScrollPane(mainPane,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    mainScrollPane.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));
    this.setContentPane(mainScrollPane);
    pack();
    setPreferredSize(new Dimension(350,300));
    setSize(new Dimension(350,300));
  }

  /**
   * Tries to select the interface with the given class name.
   * @param description Interface description
   * @return True if selected, false otherwise
   */
  public boolean setSelectedInterface(String description) {
    for (int i=0; i < selectInterfaceComboBox.getItemCount(); i++) {
      if (selectInterfaceComboBox.getItemAt(i).equals(description)) {
        selectInterfaceComboBox.setSelectedIndex(i);
        return true;
      }
    }
    return false;
  }

  @Override
  public void closePlugin() {
    // Release old interface visualizer if any
    if (selectedMoteInterface != null && currentInterfaceVisualizer != null) {
      selectedMoteInterface.releaseInterfaceVisualizer(currentInterfaceVisualizer);
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    var config = new ArrayList<Element>();

    // Selected variable name
    var element = new Element("interface");
    element.setText((String) selectInterfaceComboBox.getSelectedItem());
    config.add(element);
    Point pos = mainScrollPane.getViewport().getViewPosition();

    element = new Element("scrollpos");
    element.setText(pos.x + "," + pos.y);
    config.add(element);
    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      if (element.getName().equals("interface")) {
        setSelectedInterface(element.getText());
      } else if (element.getName().equals("scrollpos")) {
        String[] scrollPos = element.getText().split(",");
        final Point pos = new Point(Integer.parseInt(scrollPos[0]), Integer.parseInt(scrollPos[1]));
        EventQueue.invokeLater(() -> mainScrollPane.getViewport().setViewPosition(pos));
      }
    }
    return true;
  }

  @Override
  public String getQuickHelp() {
    StringBuilder help = new StringBuilder();
    help.append("<b>").append(Cooja.getDescriptionOf(this)).append("</b>");
    help.append("<p>Lists mote interfaces, and allows mote inspection and interaction via mote interface visualizers.");

    MoteInterface intf = selectedMoteInterface;
    if (intf != null) {
      if (intf instanceof HasQuickHelp hasQuickHelp) {
        help.append("<p>").append(hasQuickHelp.getQuickHelp());
      } else {
        help.append("<p><b>").append(Cooja.getDescriptionOf(intf)).append("</b>");
        help.append("<p>No help available");
      }
    }

    return help.toString();
  }

  @Override
  public Mote getMote() {
    return mote;
  }

}
