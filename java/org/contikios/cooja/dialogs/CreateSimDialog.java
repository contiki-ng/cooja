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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.NumberFormat;
import java.util.Random;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.contikios.cooja.Cooja;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.Simulation;

/**
 * A dialog for creating and configuring a simulation.
 *
 * @author Fredrik Osterlind
 */
public class CreateSimDialog extends JDialog {
  private final static int LABEL_WIDTH = 170;
  private final static int LABEL_HEIGHT = 25;

  private SimConfig config;

  /**
   * Shows a dialog for configuring a simulation.
   *
   * @param gui Cooja
   * @param cfg Initial configuration
   * @return Configuration selected by user, null if dialog is cancelled.
   */
  public static SimConfig showDialog(Cooja gui, SimConfig cfg) {
    final var dialog = new CreateSimDialog(gui, cfg);
    dialog.setVisible(true);
    // Simulation configured correctly
    return dialog.config;
  }

  private CreateSimDialog(Cooja gui, SimConfig cfg) {
    super(Cooja.getTopParentContainer(), "Create new simulation", ModalityType.APPLICATION_MODAL);
    Box vertBox = Box.createVerticalBox();
    NumberFormat integerFormat = NumberFormat.getIntegerInstance();

    // BOTTOM BUTTON PART
    Box buttonBox = Box.createHorizontalBox();
    buttonBox.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

    buttonBox.add(Box.createHorizontalGlue());

    final var cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(e -> dispose());
    buttonBox.add(cancelButton);

    var createButton = new JButton("Create");
    buttonBox.add(Box.createHorizontalStrut(5));
    getRootPane().setDefaultButton(createButton);
    buttonBox.add(createButton);


    // MAIN PART

    // Title
    var horizBox = Box.createHorizontalBox();
    horizBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,LABEL_HEIGHT));
    horizBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    var label = new JLabel("Simulation name");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));

    final var title = new JTextField();
    title.setColumns(25);

    horizBox.add(label);
    horizBox.add(Box.createHorizontalStrut(10));
    horizBox.add(title);

    vertBox.add(horizBox);
    vertBox.add(Box.createRigidArea(new Dimension(0,5)));

    // -- Advanced settings --
    Box advancedBox = Box.createVerticalBox();
    advancedBox.setBorder(BorderFactory.createTitledBorder("Advanced settings"));

    // Radio Medium selection
    horizBox = Box.createHorizontalBox();
    horizBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,LABEL_HEIGHT));
    horizBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    label = new JLabel("Radio medium");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));

    Vector<String> radioMediumDescriptions = new Vector<>();
    for (Class<? extends RadioMedium> radioMediumClass: gui.getRegisteredRadioMediums()) {
      String description = Cooja.getDescriptionOf(radioMediumClass);
      radioMediumDescriptions.add(description);
    }

    final var radioMediumBox = new JComboBox<>(radioMediumDescriptions);
    radioMediumBox.setSelectedIndex(0);
    label.setLabelFor(radioMediumBox);

    horizBox.add(label);
    horizBox.add(Box.createHorizontalStrut(10));
    horizBox.add(radioMediumBox);
    horizBox.setToolTipText("Determines the radio surroundings behaviour");

    advancedBox.add(horizBox);
    advancedBox.add(Box.createRigidArea(new Dimension(0,5)));

    // Delayed startup
    horizBox = Box.createHorizontalBox();
    horizBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,LABEL_HEIGHT));
    horizBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    label = new JLabel("Mote startup delay (ms)");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));

    final var delayedStartup = new JFormattedTextField(integerFormat);
    delayedStartup.setColumns(4);

    horizBox.add(label);
    horizBox.add(Box.createHorizontalStrut(150));
    horizBox.add(delayedStartup);
    horizBox.setToolTipText("Maximum mote startup delay (random interval: [0, time])");

    advancedBox.add(horizBox);
    advancedBox.add(Box.createVerticalStrut(5));

    // Random seed
    horizBox = Box.createHorizontalBox();
    horizBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,LABEL_HEIGHT));
    horizBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    label = new JLabel("Random seed");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));

    final var randomSeed = new JFormattedTextField(integerFormat);
    randomSeed.setValue(123456);
    randomSeed.setColumns(4);

    horizBox.add(label);
    horizBox.add(Box.createHorizontalStrut(150));
    horizBox.add(randomSeed);
    horizBox.setToolTipText("Simulation random seed. Controls the random behavior such as mote startup delays, node positions etc.");

    advancedBox.add(horizBox);
    advancedBox.add(Box.createVerticalStrut(5));

    horizBox = Box.createHorizontalBox();
    horizBox.setMaximumSize(new Dimension(Integer.MAX_VALUE,LABEL_HEIGHT));
    horizBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    label = new JLabel("New random seed on reload");
    label.setPreferredSize(new Dimension(LABEL_WIDTH,LABEL_HEIGHT));
    final var randomSeedGenerated = new JCheckBox();
    randomSeedGenerated.setToolTipText("Automatically generate random seed at simulation load");
    randomSeedGenerated.addActionListener(e -> {
      if (((JCheckBox)e.getSource()).isSelected()) {
        randomSeed.setEnabled(false);
        randomSeed.setText("[autogenerated]");
      } else {
        randomSeed.setEnabled(true);
        randomSeed.setValue(123456);
      }
    });

    horizBox.add(label);
    horizBox.add(Box.createHorizontalStrut(144));
    horizBox.add(randomSeedGenerated);

    advancedBox.add(horizBox);
    advancedBox.add(Box.createVerticalStrut(5));

    vertBox.add(advancedBox);
    vertBox.add(Box.createVerticalGlue());

    vertBox.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

    Container contentPane = getContentPane();
    contentPane.add(vertBox, BorderLayout.CENTER);
    contentPane.add(buttonBox, BorderLayout.SOUTH);

    pack();
    setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cancelButton.doClick();
      }
    });
    // Set title
    var simTitle = cfg.title();
    title.setText(simTitle == null ? "My simulation" : simTitle);

    // Select radio medium
    String currentDescription = cfg.radioMedium();
    if (currentDescription != null) {
      for (int i = 0; i < radioMediumBox.getItemCount(); i++) {
        String menuDescription = radioMediumBox.getItemAt(i);
        if (menuDescription.equals(currentDescription)) {
          radioMediumBox.setSelectedIndex(i);
          break;
        }
      }
    }

    // Set random seed
    if (cfg.generatedSeed()) {
      randomSeedGenerated.setSelected(true);
      randomSeed.setEnabled(false);
      randomSeed.setText("[autogenerated]");
    } else {
      randomSeed.setEnabled(true);
      randomSeed.setValue(cfg.randomSeed());
    }

    // Set delayed mote startup time (ms)
    delayedStartup.setValue(cfg.moteStartDelay() / Simulation.MILLISECOND);

    createButton.addActionListener(e -> {
      config = new SimConfig(title.getText(),
              gui.getRegisteredRadioMediums().get(radioMediumBox.getSelectedIndex()).getName(),
              randomSeedGenerated.isSelected(),
              randomSeedGenerated.isSelected()
                      ? new Random().nextLong() : ((Number) randomSeed.getValue()).longValue(),
              ((Number) delayedStartup.getValue()).intValue() * Simulation.MILLISECOND);
      dispose();
    });

    // Set position and focus of dialog
    setLocationRelativeTo(Cooja.getTopParentContainer());
    title.requestFocus();
    title.select(0, title.getText().length());

    // Dispose on escape key
    InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "dispose");
    getRootPane().getActionMap().put("dispose", new AbstractAction(){
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelButton.doClick();
      }
    });
  }

  /** Basic simulation configuration. */
  public record SimConfig(String title, String radioMedium, boolean generatedSeed, long randomSeed, long moteStartDelay) {}
}
