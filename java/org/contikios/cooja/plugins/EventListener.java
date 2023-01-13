/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja.plugins;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Observable;
import java.util.Observer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.jdom2.Element;

/**
 * Allows a user to observe several parts of the simulator, stopping a
 * simulation whenever an object changes.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Breakpoints")
@PluginType(PluginType.PType.SIM_PLUGIN)
public class EventListener extends VisPlugin {
  private final Simulation mySimulation;

  private final ArrayList<EventObserver> allObservers = new ArrayList<>();

  private final EventListener myPlugin;

  private final JLabel messageLabel;

  private final JButton actionButton;

  private final JPanel interfacePanel;

  private final JPanel generalPanel;

  protected abstract static class EventObserver implements Observer {
    protected final Observable myObservation;

    protected final EventListener myParent;

    public EventObserver(EventListener parent, Observable objectToObserve) {
      myParent = parent;
      myObservation = objectToObserve;
      objectToObserve.addObserver(this);
    }

    /**
     * Stop observing object (for cleaning up).
     */
    public void detachFromObject() {
      myObservation.deleteObserver(this);
    }

    /**
     * @return Object being observed.
     */
    public Observable getObservable() {
      return myObservation;
    }
  }

  protected class InterfaceEventObserver extends EventObserver {
    private final Mote myMote;

    public InterfaceEventObserver(EventListener parent, Mote mote,
        Observable objectToObserve) {
      super(parent, objectToObserve);
      myMote = mote;
    }

    @Override
    public void update(Observable obs, Object obj) {
      final MoteInterface moteInterface = (MoteInterface) obs;
      int moteID = myMote.getID();

      myParent.actOnChange("'" + Cooja.getDescriptionOf(moteInterface.getClass())
          + "'" + " of mote '" + (moteID > 0 ? Integer.toString(moteID) : "?")
          + "'" + " changed at time "
          + myParent.mySimulation.getSimulationTime(), new AbstractAction(
          "View interface visualizer") {
        @Override
        public void actionPerformed(ActionEvent e) {
          MoteInterfaceViewer plugin =
            (MoteInterfaceViewer) mySimulation.getCooja().tryStartPlugin(MoteInterfaceViewer.class, mySimulation, myMote);
          plugin.setSelectedInterface(Cooja.getDescriptionOf(moteInterface.getClass()));
        }
      });
    }
  }

  protected static class GeneralEventObserver extends EventObserver {
    public GeneralEventObserver(EventListener parent, Observable objectToObserve) {
      super(parent, objectToObserve);
    }

    @Override
    public void update(Observable obs, Object obj) {
      myParent.actOnChange("'" + Cooja.getDescriptionOf(obs.getClass()) + "'"
          + " changed at time " + myParent.mySimulation.getSimulationTime(),
          null);
    }
  }

  /**
   * @param simulationToControl
   *          Simulation to control
   */
  public EventListener(Simulation simulationToControl, Cooja gui) {
    super("Event Listener", gui);

    mySimulation = simulationToControl;
    myPlugin = this;

    // Create selectable interfaces list (only supports Contiki mote types).
    var allInterfaces = new LinkedHashSet<MoteInterface>();
    for (var mote : simulationToControl.getMotes()) {
      if (mote instanceof ContikiMote) {
        allInterfaces.addAll(mote.getInterfaces().getInterfaces());
      }
    }

    interfacePanel = new JPanel();
    interfacePanel.setLayout(new BoxLayout(interfacePanel, BoxLayout.Y_AXIS));
    interfacePanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
    for (var interfaceClass : allInterfaces) {
      var checkBox = new JCheckBox(Cooja.getDescriptionOf(interfaceClass.getClass()), false);
      checkBox.setToolTipText(interfaceClass.getClass().getName());
      // FIXME: Stop storing the class as client property.
      checkBox.putClientProperty("interface_class", interfaceClass.getClass());
      checkBox.addActionListener(interfaceCheckBoxListener);
      interfacePanel.add(checkBox);
    }
    if (allInterfaces.isEmpty()) {
      interfacePanel.add(new JLabel("No used interface classes detected"));
    }

    // Create general selectable list
    generalPanel = new JPanel();
    generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
    generalPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

    JCheckBox simCheckBox = new JCheckBox("Simulation event", false);
    simCheckBox.putClientProperty("observable", mySimulation);
    simCheckBox.addActionListener(generalCheckBoxListener);
    generalPanel.add(simCheckBox);

    JCheckBox radioMediumCheckBox = new JCheckBox("Radio medium event", false);
    radioMediumCheckBox.putClientProperty("observable", mySimulation.getRadioMedium().getRadioTransmissionTriggers());
    radioMediumCheckBox.addActionListener(generalCheckBoxListener);
    generalPanel.add(radioMediumCheckBox);

    // Add components
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    setContentPane(mainPanel);

    mainPanel.add(new JLabel("Break on general changes:"));
    mainPanel.add(generalPanel);

    mainPanel.add(new JLabel("Break on mote interface changes:"));
    mainPanel.add(new JScrollPane(interfacePanel));

    messageLabel = new JLabel("[no change detected yet]");
    actionButton = new JButton("[no action available]");
    actionButton.setEnabled(false);
    mainPanel.add(new JLabel("Last message:"));
    mainPanel.add(messageLabel);
    mainPanel.add(actionButton);
    pack();
  }

  private void actOnChange(final String message, final Action action) {
    if (!mySimulation.isRunning()) {
      return;
    }

    mySimulation.stopSimulation();

    SwingUtilities.invokeLater(() -> {
      messageLabel.setText(message);
      actionButton.setAction(action);
      actionButton.setVisible(action != null);
    });
  }

  private final ActionListener interfaceCheckBoxListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      Class<? extends MoteInterface> interfaceClass = (Class<? extends MoteInterface>) ((JCheckBox) e
          .getSource()).getClientProperty("interface_class");
      boolean shouldObserve = ((JCheckBox) e.getSource()).isSelected();

      if (!shouldObserve) {
        // Remove existing observers
        for (EventObserver obs : allObservers.toArray(new EventObserver[0])) {
          Class<? extends Observable> objClass = obs.getObservable().getClass();
          if (objClass == interfaceClass ||
              interfaceClass.isAssignableFrom(objClass)) {
            obs.detachFromObject();
            allObservers.remove(obs);
          }
        }
      } else {
        // Register new observers
        for (int i = 0; i < mySimulation.getMotesCount(); i++) {
          MoteInterface moteInterface = mySimulation.getMote(i).getInterfaces()
              .getInterfaceOfType(interfaceClass);
          if (moteInterface instanceof Observable obs) {
            allObservers.add(new InterfaceEventObserver(myPlugin, mySimulation.getMote(i), obs));
          }
        }
      }
    }
  };

  private final ActionListener generalCheckBoxListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      Observable observable = (Observable) ((JCheckBox) e.getSource())
          .getClientProperty("observable");
      boolean shouldObserve = ((JCheckBox) e.getSource()).isSelected();

      if (!shouldObserve) {
        // Remove existing observers
        for (EventObserver obs : allObservers.toArray(new EventObserver[0])) {
          if (obs.getObservable() == observable) {
            obs.detachFromObject();
            allObservers.remove(obs);
          }
        }
      } else {
        // Register new observers
        allObservers.add(new GeneralEventObserver(myPlugin, observable));
      }
    }
  };

  @Override
  public void closePlugin() {
    // Remove all existing observers
    for (EventObserver obs : allObservers) {
      obs.detachFromObject();
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    var config = new ArrayList<Element>();

    /* Save general observers */
    for (Component comp: generalPanel.getComponents()) {
      if (comp instanceof JCheckBox checkBox) {
        if (checkBox.isSelected()) {
          var element = new Element("general");
          element.setText(checkBox.getText());
          config.add(element);
        }
      }
    }

    /* Save interface observers */
    for (Component comp: interfacePanel.getComponents()) {
      if (comp instanceof JCheckBox checkBox) {
        if (checkBox.isSelected()) {
          var element = new Element("interface");
          element.setText(checkBox.getText());
          config.add(element);
        }
      }
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {

    /* Load general observers */
    for (Element element : configXML) {
      if (element.getName().equals("general")) {
        for (Component comp: generalPanel.getComponents()) {
          if (comp instanceof JCheckBox checkBox) {
            if (checkBox.getText().equals(element.getText())) {
              checkBox.setSelected(true);
              generalCheckBoxListener.actionPerformed(new ActionEvent(checkBox, ActionEvent.ACTION_PERFORMED, ""));
            }
          }
        }
      }

      /* Load interface observers */
      else if (element.getName().equals("interface")) {
        for (Component comp: interfacePanel.getComponents()) {
          if (comp instanceof JCheckBox checkBox) {
            if (checkBox.getText().equals(element.getText())) {
              checkBox.setSelected(true);
              interfaceCheckBoxListener.actionPerformed(new ActionEvent(checkBox, ActionEvent.ACTION_PERFORMED, ""));
            }
          }
        }
      }

      else {
        return false;
      }
    }

    return true;
  }

}
