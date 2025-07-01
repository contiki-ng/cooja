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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.contikios.cooja.Cooja;

/**
 * A dialog for viewing/editing external tools settings.
 * Allows user to change paths and arguments to compilers, linkers etc.
 *
 * @author Fredrik Osterlind
 */
public class ExternalToolsDialog extends JDialog {
  private final static int LABEL_WIDTH = 220;
  private final static int LABEL_HEIGHT = 15;
  private static final String[] settingsNames = {
    "PATH_COOJA",
    "PATH_CONTIKI", "PATH_APPS",
    "PATH_APPSEARCH",

    "PATH_MAKE",
    "PATH_C_COMPILER", "COMPILER_ARGS",

    "DEFAULT_PROJECTDIRS",

    "PARSE_WITH_COMMAND",

    "READELF_COMMAND",

    "PARSE_COMMAND",
    "COMMAND_VAR_NAME_ADDRESS_SIZE",

    "HIDE_WARNINGS"
  };

  private final JTextField[] textFields = new JTextField[settingsNames.length];

  /** Creates a dialog for viewing/editing external tools settings. */
  public static void showDialog() {
    new ExternalToolsDialog().setVisible(true);
  }

  private ExternalToolsDialog() {
    super(Cooja.getTopParentContainer(), "Edit Settings", ModalityType.APPLICATION_MODAL);
    JPanel mainPane = new JPanel();
    mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));

    // BOTTOM BUTTON PART
    JPanel buttonPane = new JPanel();
    buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.X_AXIS));
    buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

    buttonPane.add(Box.createHorizontalGlue());

    var button = new JButton("Cancel");
    button.addActionListener(actionEvent -> dispose());
    buttonPane.add(button);

    button = new JButton("Reset");
    button.addActionListener(e -> {
      Cooja.resetExternalToolsSettings();
      updateTextFields();
    });
    buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
    buttonPane.add(button);

    button = new JButton("Save");
    button.addActionListener(e -> {
      for (int i = 0; i < textFields.length; i++) {
        Cooja.setExternalToolsSetting(settingsNames[i], textFields[i].getText().trim());
      }
      Cooja.saveExternalToolsUserSettings();
      dispose();
    });
    buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
    buttonPane.add(button);

    // MAIN PART
    final var focusListener = new FocusListener() {
      @Override
      public void focusGained(FocusEvent focusEvent) {}

      @Override
      public void focusLost(FocusEvent focusEvent) {
        compareWithDefaults();
      }
    };
    for (int i = 0; i < textFields.length; i++) {
      // Add text fields for every changeable property
      var smallPane = new JPanel();
      smallPane.setAlignmentX(Component.LEFT_ALIGNMENT);
      smallPane.setLayout(new BoxLayout(smallPane, BoxLayout.X_AXIS));
      var label = new JLabel(settingsNames[i]);
      label.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));

      var textField = new JTextField(35);
      textField.setText("");
      textField.addFocusListener(focusListener);
      textFields[i] = textField;

      smallPane.add(label);
      smallPane.add(Box.createHorizontalStrut(10));
      smallPane.add(textField);

      mainPane.add(smallPane);
      mainPane.add(Box.createRigidArea(new Dimension(0, 5)));
    }

    // Set actual used values into all text fields
    updateTextFields();

    mainPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    Container contentPane = getContentPane();
    JScrollPane scrollPane = new JScrollPane(mainPane);
    scrollPane.setPreferredSize(new Dimension(700, 500));
    contentPane.add(scrollPane, BorderLayout.CENTER);
    contentPane.add(buttonPane, BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(Cooja.getTopParentContainer());
  }

  private void updateTextFields() {
    for (int i = 0; i < textFields.length; i++) {
      textFields[i].setText(Cooja.getExternalToolsSetting(settingsNames[i], ""));
    }
    compareWithDefaults();
  }

  private void compareWithDefaults() {
    for (int i = 0; i < textFields.length; i++) {
      String currentValue = textFields[i].getText();

      // Compare with default value
      var defaultValue = Cooja.getExternalToolsDefaultSetting(settingsNames[i], "");
      if (currentValue.equals(defaultValue)) {
        textFields[i].setBackground(Color.WHITE);
        textFields[i].setToolTipText("");
      } else {
        textFields[i].setBackground(Color.LIGHT_GRAY);
        textFields[i].setToolTipText("Default value: " + defaultValue);
      }
    }
  }

}
