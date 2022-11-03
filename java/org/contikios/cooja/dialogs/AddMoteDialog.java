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

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Positioner;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.MoteID;
import org.contikios.cooja.interfaces.Position;

/**
 * A dialog for adding motes.
 *
 * @author Fredrik Osterlind
 */
public class AddMoteDialog extends JDialog {
  private static final Logger logger = LogManager.getLogger(AddMoteDialog.class);

  private final static int LABEL_WIDTH = 170;
  private final static int LABEL_HEIGHT = 15;

  private final ArrayList<Mote> newMotes = new ArrayList<>();

  private final JButton addButton = new JButton("Add motes");

  private final MoteType moteType;
  private final Simulation simulation;

  private final JFormattedTextField numberOfMotesField;
  private final JFormattedTextField startX;
  private final JFormattedTextField endX;
  private final JFormattedTextField startY;
  private final JFormattedTextField endY;
  private final JFormattedTextField startZ;
  private final JFormattedTextField endZ;
  private final JComboBox<String> positionDistributionBox;


  /**
   * Shows a dialog which enables a user to create and add motes of the given
   * type.
   *
   * @param sim Simulation
   * @param moteType Mote type
   * @return New motes or null if aborted
   */
  public static ArrayList<Mote> showDialog(Simulation sim, MoteType moteType) {
    var myDialog = new AddMoteDialog(sim, moteType);
    myDialog.setVisible(true);
    return myDialog.newMotes;
  }

  private AddMoteDialog(Simulation simulation, MoteType moteType) {
    super(Cooja.getTopParentContainer(), "Add motes (" + moteType.getDescription() + ")", ModalityType.APPLICATION_MODAL);
    this.moteType = moteType;
    this.simulation = simulation;

    JPanel mainPane = new JPanel();
    mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));
    NumberFormat integerFormat = NumberFormat.getIntegerInstance();
    NumberFormat doubleFormat = NumberFormat.getNumberInstance();

    // BOTTOM BUTTON PART
    JPanel buttonPane = new JPanel();
    buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.X_AXIS));
    buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

    buttonPane.add(Box.createHorizontalGlue());

    var button = new JButton("Do not add motes");
    button.setActionCommand("cancel");
    var myEventHandler = new AddMotesEventHandler();
    button.addActionListener(myEventHandler);
    buttonPane.add(button);

    addButton.setActionCommand("add");
    addButton.addActionListener(myEventHandler);
    this.getRootPane().setDefaultButton(addButton);
    buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
    buttonPane.add(addButton);

    // MAIN PART

    // Number of new motes
    var smallPane = new JPanel();
    smallPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    smallPane.setLayout(new BoxLayout(smallPane, BoxLayout.X_AXIS));
    var label = new JLabel("Number of new motes");
    label.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));

    numberOfMotesField = new JFormattedTextField(integerFormat);
    numberOfMotesField.setFocusLostBehavior(JFormattedTextField.PERSIST);
    numberOfMotesField.setValue(1);
    numberOfMotesField.setColumns(10);
    numberOfMotesField.addFocusListener(myEventHandler);
    numberOfMotesField.addPropertyChangeListener("value", myEventHandler);

    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));
    smallPane.add(numberOfMotesField);

    mainPane.add(smallPane);
    mainPane.add(Box.createRigidArea(new Dimension(0, 5)));

    // Position distribution
    smallPane = new JPanel();
    smallPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    smallPane.setLayout(new BoxLayout(smallPane, BoxLayout.X_AXIS));
    label = new JLabel("Positioning");
    label.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));

    var positioners = simulation.getCooja().getRegisteredPositioners();
    String[] posDistributions = new String[positioners.size()];
    for (int i = 0; i < posDistributions.length; i++) {
      posDistributions[i] = Cooja.getDescriptionOf(positioners.get(i));
    }

    positionDistributionBox = new JComboBox<>(posDistributions);
    positionDistributionBox.setSelectedIndex(0);
    positionDistributionBox.addActionListener(myEventHandler);
    positionDistributionBox.addFocusListener(myEventHandler);
    label.setLabelFor(positionDistributionBox);

    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));
    smallPane.add(positionDistributionBox);

    mainPane.add(smallPane);
    mainPane.add(Box.createRigidArea(new Dimension(0, 5)));

    // Position interval X
    smallPane = new JPanel();
    smallPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    smallPane.setLayout(new BoxLayout(smallPane, BoxLayout.X_AXIS));

    label = new JLabel("Position interval");
    label.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));
    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));

    label = new JLabel("X ");
    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));

    startX = new JFormattedTextField(doubleFormat);
    startX.setFocusLostBehavior(JFormattedTextField.PERSIST);
    startX.setValue(0.0);
    startX.setColumns(4);
    startX.addFocusListener(myEventHandler);
    startX.addPropertyChangeListener("value", myEventHandler);
    smallPane.add(startX);
    smallPane.add(Box.createHorizontalStrut(10));

    label = new JLabel("<->");
    label.setPreferredSize(new Dimension(LABEL_WIDTH / 4, LABEL_HEIGHT));
    smallPane.add(label);

    endX = new JFormattedTextField(doubleFormat);
    endX.setFocusLostBehavior(JFormattedTextField.PERSIST);
    endX.setValue(100.0);
    endX.setColumns(4);
    endX.addFocusListener(myEventHandler);
    endX.addPropertyChangeListener("value", myEventHandler);
    smallPane.add(endX);
    smallPane.add(Box.createHorizontalStrut(10));

    mainPane.add(smallPane);
    mainPane.add(Box.createRigidArea(new Dimension(0, 5)));

    // Position interval Y
    smallPane = new JPanel();
    smallPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    smallPane.setLayout(new BoxLayout(smallPane, BoxLayout.X_AXIS));

    label = new JLabel("");
    label.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));
    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));

    label = new JLabel("Y ");
    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));

    startY = new JFormattedTextField(doubleFormat);
    startY.setFocusLostBehavior(JFormattedTextField.PERSIST);
    startY.setValue(0.0);
    startY.setColumns(4);
    startY.addFocusListener(myEventHandler);
    startY.addPropertyChangeListener("value", myEventHandler);
    smallPane.add(startY);
    smallPane.add(Box.createHorizontalStrut(10));

    label = new JLabel("<->");
    label.setPreferredSize(new Dimension(LABEL_WIDTH / 4, LABEL_HEIGHT));
    smallPane.add(label);

    endY = new JFormattedTextField(doubleFormat);
    endY.setFocusLostBehavior(JFormattedTextField.PERSIST);
    endY.setValue(100.0);
    endY.setColumns(4);
    endY.addFocusListener(myEventHandler);
    endY.addPropertyChangeListener("value", myEventHandler);
    smallPane.add(endY);
    smallPane.add(Box.createHorizontalStrut(10));

    mainPane.add(smallPane);
    mainPane.add(Box.createRigidArea(new Dimension(0, 5)));

    // Position interval Z
    smallPane = new JPanel();
    smallPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    smallPane.setLayout(new BoxLayout(smallPane, BoxLayout.X_AXIS));

    label = new JLabel("");
    label.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));
    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));

    label = new JLabel("Z ");
    smallPane.add(label);
    smallPane.add(Box.createHorizontalStrut(10));

    startZ = new JFormattedTextField(doubleFormat);
    startZ.setFocusLostBehavior(JFormattedTextField.PERSIST);
    startZ.setValue(0.0);
    startZ.setColumns(4);
    startZ.addFocusListener(myEventHandler);
    startZ.addPropertyChangeListener("value", myEventHandler);
    smallPane.add(startZ);
    smallPane.add(Box.createHorizontalStrut(10));

    label = new JLabel("<->");
    label.setPreferredSize(new Dimension(LABEL_WIDTH / 4, LABEL_HEIGHT));
    smallPane.add(label);

    endZ = new JFormattedTextField(doubleFormat);
    endZ.setFocusLostBehavior(JFormattedTextField.PERSIST);
    endZ.setValue(0.0);
    endZ.setColumns(4);
    endZ.addFocusListener(myEventHandler);
    endZ.addPropertyChangeListener("value", myEventHandler);
    smallPane.add(endZ);
    smallPane.add(Box.createHorizontalStrut(10));

    mainPane.add(smallPane);
    mainPane.add(Box.createRigidArea(new Dimension(0, 5)));

    mainPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    Container contentPane = getContentPane();
    contentPane.add(mainPane, BorderLayout.NORTH);
    contentPane.add(buttonPane, BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(Cooja.getTopParentContainer());
    checkSettings();
  }

  private boolean checkSettings() {
    // Check settings
    boolean settingsOK = checkSettings(startX, endX);

    if (!checkSettings(startY, endY)) {
      settingsOK = false;
    }
    if (!checkSettings(startZ, endZ)) {
      settingsOK = false;
    }

    // Check number of new motes
    try {
      numberOfMotesField.commitEdit();
      if (((Number) numberOfMotesField.getValue()).intValue() < 0) {
	throw new ParseException("Malformed", 0);
      }
      numberOfMotesField.setBackground(Color.WHITE);
      numberOfMotesField.setToolTipText(null);
    } catch (ParseException e) {
      numberOfMotesField.setBackground(Color.RED);
      numberOfMotesField.setToolTipText("Must be >= 1");
      settingsOK = false;
    }

    addButton.setEnabled(settingsOK);

    return settingsOK;
  }

  private static boolean checkSettings(JFormattedTextField start,
				JFormattedTextField end) {
    try {
      start.commitEdit();
      end.commitEdit();

      if (((Number) start.getValue()).doubleValue() <=
	  ((Number) end.getValue()).doubleValue()) {
	start.setBackground(Color.WHITE);
	start.setToolTipText(null);
	end.setBackground(Color.WHITE);
	end.setToolTipText(null);
	return true;
      }
    } catch (ParseException e) {
      // Malformed interval
    }
    start.setBackground(Color.RED);
    start.setToolTipText("Malformed interval");
    end.setBackground(Color.RED);
    end.setToolTipText("Malformed interval");
    return false;
  }

  private class AddMotesEventHandler
      implements
        ActionListener,
        FocusListener,
        PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent e) {
      checkSettings();
    }
    @Override
    public void focusGained(final FocusEvent e) {
      if (e.getSource() instanceof JFormattedTextField src) {
        SwingUtilities.invokeLater(src::selectAll);
      }
    }
    @Override
    public void focusLost(FocusEvent e) {
      checkSettings();
    }
    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getActionCommand().equals("cancel")) {
        newMotes.clear();
        dispose();
      } else if (e.getActionCommand().equals("add")) {
        // Validate input
        if (!checkSettings()) {
          return;
        }
        Class<? extends Positioner> positionerClass = null;
        for (var positioner : simulation.getCooja().getRegisteredPositioners()) {
          if (Cooja.getDescriptionOf(positioner).equals(positionDistributionBox.getSelectedItem())) {
            positionerClass = positioner;
          }
        }
        if (positionerClass == null) {
          return;
        }
        int motesToAdd = ((Number) numberOfMotesField.getValue()).intValue();
        Positioner positioner;
        try {
          var constr = positionerClass.getConstructor(int.class, double.class, double.class,
                  double.class, double.class, double.class, double.class);
          positioner = constr.newInstance(motesToAdd,
                  ((Number) startX.getValue()).doubleValue(), ((Number) endX.getValue()).doubleValue(),
                  ((Number) startY.getValue()).doubleValue(), ((Number) endY.getValue()).doubleValue(),
                  ((Number) startZ.getValue()).doubleValue(), ((Number) endZ.getValue()).doubleValue());
        } catch (Exception e1) {
          logger.fatal("Exception when creating " + positionerClass + ": ", e1);
          return;
        }

        // Create new motes
        while (newMotes.size() < motesToAdd) {
          try {
            newMotes.add(moteType.generateMote(simulation));
          } catch (MoteType.MoteTypeCreationException e2) {
            newMotes.clear();
            JOptionPane.showMessageDialog(AddMoteDialog.this,
                    "Could not create mote.\nException message: \"" + e2.getMessage() + "\"\n\n",
                    "Mote creation failed", JOptionPane.ERROR_MESSAGE);
            return;
          }
        }
        // Position new motes
        for (Mote newMote : newMotes) {
          Position newPosition = newMote.getInterfaces().getPosition();
          if (newPosition != null) {
            double[] next = positioner.getNextPosition();
            if (next.length >= 3) {
              newPosition.setCoordinates(next[0], next[1], next[2]);
            } else if (next.length == 2) {
              newPosition.setCoordinates(next[0], next[1], 0);
            } else if (next.length == 1) {
              newPosition.setCoordinates(next[0], 0, 0);
            } else {
              newPosition.setCoordinates(0, 0, 0);
            }
          }
        }

        /* Set unique mote id's for all new motes
         * TODO ID should be provided differently; not rely on the unsafe MoteID interface */
        int nextMoteID = 1;
        for (Mote m : simulation.getMotes()) {
          int existing = m.getID();
          if (existing >= nextMoteID) {
            nextMoteID = existing + 1;
          }
        }
        for (Mote m : newMotes) {
          MoteID moteID = m.getInterfaces().getMoteID();
          if (moteID != null) {
            moteID.setMoteID(nextMoteID++);
          } else {
            logger.warn("Can't set mote ID (no mote ID interface): " + m);
          }
        }
        dispose();
      }
    }
  }

}
