/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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
 */

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.contikios.cooja.Cooja;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.interfaces.Clock;
import org.contikios.cooja.interfaces.MoteID;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mote.BaseContikiMoteType;
import org.contikios.cooja.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract configure mote type dialog used by Contiki-based mote type implementations.
 * <p>
 * The dialog uses tabs for configuring the mote type.
 * Three tabs are provided: compile commands, mote interfaces, and compilation output.
 * <p>
 * In addition, the dialog implementation can provide additional tabs.
 *
 * @see #tabbedPane
 * @see ContikiMoteCompileDialog
 *
 * @author Fredrik Osterlind
 */
public abstract class AbstractCompileDialog extends JDialog {
  private static final Logger logger = LoggerFactory.getLogger(AbstractCompileDialog.class);

  protected final static Dimension LABEL_DIMENSION = new Dimension(170, 25);

  private static File lastFile;

  public enum DialogState {
    NO_SELECTION,
    SELECTED_SOURCE, AWAITING_COMPILATION, IS_COMPILING, COMPILED_FIRMWARE,
    SELECTED_FIRMWARE,
  }

  protected final BaseContikiMoteType moteType;

  protected final JTabbedPane tabbedPane = new JTabbedPane();
  protected final Box moteIntfBox = Box.createVerticalBox();

  protected final JTextField contikiField = new JTextField(40);
  private final JTextField descriptionField = new JTextField(40);
  private final JTextArea commandsArea = new JTextArea(10, 1);
  private final JButton cleanButton = new JButton("Clean");
  private final JButton compileButton;
  private final JButton createButton = new JButton("Create");

  protected final String targetName;
  private Component currentCompilationOutput;
  private Process currentCompilationProcess;

  /* Accessible at Contiki compilation success */
  protected File contikiSource;
  protected File contikiFirmware;

  public AbstractCompileDialog(Cooja gui, final BaseContikiMoteType moteType, BaseContikiMoteType.MoteTypeConfig cfg) {
    super(Cooja.getTopParentContainer(), "Create Mote Type: Compile Contiki", ModalityType.APPLICATION_MODAL);
    this.moteType = moteType;
    this.targetName = cfg.targetName();

    /* Top: Contiki source */
    JPanel topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
    topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    Box descriptionBox = Box.createHorizontalBox();
    var label = new JLabel("Description:");
    label.setPreferredSize(LABEL_DIMENSION);
    descriptionBox.add(label);
    descriptionField.setText("[enter mote type description]");
    descriptionBox.add(descriptionField);

    topPanel.add(descriptionBox);

    JPanel sourcePanel = new JPanel();
    sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.X_AXIS));
    label = new JLabel("Contiki process / Firmware:");
    label.setPreferredSize(LABEL_DIMENSION);
    sourcePanel.add(label);
    sourcePanel.add(contikiField);
    JButton browseButton = new JButton("Browse");
    browseButton.addActionListener(e -> {
      JFileChooser fc = new JFileChooser();
      File fp = new File(contikiField.getText());
      if (fp.exists() && fp.isFile()) {
          lastFile = fp;
      }
      if (lastFile == null) {
        String path = Cooja.getExternalToolsSetting("COMPILE_LAST_FILE", null);
        if (path != null) {
          lastFile = gui.restorePortablePath(new File(path));
        } else {
          lastFile = new File(Cooja.getExternalToolsSetting("PATH_CONTIKI"), "examples/hello-world/hello-world.c");
        }
      }
      if (lastFile.isDirectory()) {
        fc.setCurrentDirectory(lastFile);
      } else if (lastFile.isFile() && lastFile.exists()) {
        fc.setCurrentDirectory(lastFile.getParentFile());
        fc.setSelectedFile(lastFile);
      } else if (lastFile.isFile() && !lastFile.exists()) {
        fc.setCurrentDirectory(lastFile.getParentFile());
      }

      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setFileFilter(new FileNameExtensionFilter("Contiki process source or Precompiled firmware",
              "c", moteType.getMoteType()));
      fc.setDialogTitle("Select Contiki process source");
      if (fc.showOpenDialog(AbstractCompileDialog.this) == JFileChooser.APPROVE_OPTION) {
        contikiField.setText(fc.getSelectedFile().getAbsolutePath());
      }
    });
    sourcePanel.add(browseButton);

    topPanel.add(sourcePanel);

    final JMenuItem abortMenuItem = new JMenuItem("Abort compilation");
    abortMenuItem.setEnabled(true);
    abortMenuItem.addActionListener(e1 -> abortAnyCompilation());

    // Called when last command has finished (success only).
    final Action compilationSuccessAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e1) {
        abortMenuItem.setEnabled(false);
        setDialogState(DialogState.COMPILED_FIRMWARE);
      }
    };

    // Called immediately if any command fails.
    final Action compilationFailureAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e1) {
        abortMenuItem.setEnabled(false);
        setDialogState(DialogState.AWAITING_COMPILATION);
      }
    };

    Action compileAction = new AbstractAction("Compile") {
      @Override
      public void actionPerformed(ActionEvent e) {
    		if (!compileButton.isEnabled()) {
    			return;
    		}
        final var commands = StringUtils.splitOnNewline(commandsArea.getText());
        if (commands.isEmpty()) {
          return;
        }
        setDialogState(DialogState.IS_COMPILING);
        final MessageListUI taskOutput = new MessageListUI();
        tabbedPane.remove(currentCompilationOutput);
        currentCompilationOutput = new JScrollPane(taskOutput);
        tabbedPane.addTab("Compilation output", null, currentCompilationOutput, "Shows Contiki compilation output");
        tabbedPane.setSelectedComponent(currentCompilationOutput);
        taskOutput.addPopupMenuItem(abortMenuItem, true);

        // Called once per command.
        final Action nextCommandAction = new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e1) {
            String command = commands.remove(0);
            try {
              currentCompilationProcess = BaseContikiMoteType.compile(
                  command,
                  moteType.getCompilationEnvironment(),
                  new File(contikiField.getText()).getParentFile(),
                  commands.isEmpty() ? compilationSuccessAction : this,
                  compilationFailureAction,
                  taskOutput,
                  false
              );
            } catch (Exception ex) {
              logger.error("Exception when compiling: " + ex.getMessage());
              taskOutput.addMessage(ex.getMessage(), MessageList.ERROR);
              compilationFailureAction.actionPerformed(null);
            }
          }
        };
        nextCommandAction.actionPerformed(null); /* Recursive calls for all commands */
      }
    };
    cleanButton.setToolTipText("$(MAKE) clean TARGET=" + targetName);
    cleanButton.addActionListener(e -> {
      createButton.setEnabled(false);
      try {
        currentCompilationProcess = BaseContikiMoteType.compile("$(MAKE) clean TARGET=" + targetName,
            moteType.getCompilationEnvironment(), new File(contikiField.getText()).getParentFile(),
            null, null, new MessageListUI(), true);
      } catch (Exception e1) {
        logger.error("Clean failed: " + e1.getMessage(), e1);
      }
    });

    compileButton = new JButton(compileAction);
    getRootPane().setDefaultButton(compileButton);

    createButton.addActionListener(e -> AbstractCompileDialog.this.dispose());

    Box buttonPanel = Box.createHorizontalBox();
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
    buttonPanel.add(Box.createHorizontalGlue());
    buttonPanel.add(cleanButton);
    buttonPanel.add(Box.createHorizontalStrut(5));
    buttonPanel.add(compileButton);
    buttonPanel.add(Box.createHorizontalStrut(5));
    buttonPanel.add(createButton);
    buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

    topPanel.add(buttonPanel);

    /* Center: Tabs showing configuration, compilation output, ... */
    // Loading firmware sets the create button to default, so do not update dialog state after that.
    commandsArea.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void changedUpdate(DocumentEvent e) {
        if (contikiSource != null || getRootPane().getDefaultButton() != createButton) {
          setDialogState(DialogState.AWAITING_COMPILATION);
        }
      }
      @Override
      public void insertUpdate(DocumentEvent e) {
        if (contikiSource != null || getRootPane().getDefaultButton() != createButton) {
          setDialogState(DialogState.AWAITING_COMPILATION);
        }
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        if (contikiSource != null || getRootPane().getDefaultButton() != createButton) {
          setDialogState(DialogState.AWAITING_COMPILATION);
        }
      }
    });
    tabbedPane.addTab("Compile commands", null, new JScrollPane(commandsArea), "Manually alter Contiki compilation commands");
    JPanel panel = new JPanel(new BorderLayout());
    JLabel label1 = new JLabel("Cooja interacts with simulated motes via mote interfaces. These settings normally do not need to be changed.");
    Box b = Box.createHorizontalBox();
    b.add(new JButton(new AbstractAction("Use default") {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (var c : moteIntfBox.getComponents()) {
          if (c instanceof JCheckBox checkbox) {
            checkbox.setSelected(false);
          }
        }
        // Select default.
        for (var moteIntf : cfg.defaultInterfaces()) {
          addMoteInterface(moteIntf, true);
        }
      }
    }));
    b.add(label1);
    panel.add(BorderLayout.NORTH, b);
    panel.add(BorderLayout.CENTER, new JScrollPane(moteIntfBox));
    tabbedPane.addTab("Mote interfaces", null, panel, "Mote interfaces");
    for (var moteInterfaces : cfg.allInterfaces()) {
      addMoteInterface(moteInterfaces, false);
    }

    /* Build panel */
    var mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(BorderLayout.NORTH, topPanel);
    mainPanel.add(BorderLayout.CENTER, tabbedPane);
    mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    setContentPane(mainPanel);

    setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        abortAnyCompilation();
        contikiSource = null;
        contikiFirmware = null;
        AbstractCompileDialog.this.dispose();
      }
    });

    descriptionField.requestFocus();
    descriptionField.select(0, descriptionField.getText().length());

    /* Add listener only after restoring old config */
    contikiField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void changedUpdate(DocumentEvent e) {
        fileSelected(contikiField.getText());
      }
      @Override
      public void insertUpdate(DocumentEvent e) {
        fileSelected(contikiField.getText());
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        fileSelected(contikiField.getText());
      }
      private void fileSelected(String name) {
        if (!Files.exists(Path.of(name))) {
          setDialogState(DialogState.NO_SELECTION);
          return;
        }
        lastFile = new File(name);
        Cooja.setExternalToolsSetting("COMPILE_LAST_FILE", gui.createPortablePath(lastFile).getPath());
        setDialogState(name.endsWith(".c") || !canLoadFirmware(name)
                ? DialogState.SELECTED_SOURCE : DialogState.SELECTED_FIRMWARE);
      }
    });

    /* Final touches: respect window size, focus on description etc */
    Rectangle maxSize = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    if (maxSize != null &&
        (getSize().getWidth() > maxSize.getWidth() || getSize().getHeight() > maxSize.getHeight())) {
      Dimension newSize = new Dimension();
      newSize.height = Math.min((int) maxSize.getHeight(), (int) getSize().getHeight());
      newSize.width = Math.min((int) maxSize.getWidth(), (int) getSize().getWidth());
      setSize(newSize);
    }

    /* Recompile at Ctrl+R */
    InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK, false), "recompile");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "dispose");
    getRootPane().getActionMap().put("recompile", compileAction);
    getRootPane().getActionMap().put("dispose", new AbstractAction("Cancel") {
      @Override
      public void actionPerformed(ActionEvent e) {
        AbstractCompileDialog.this.dispose();
      }
    });

    pack();
    setLocationRelativeTo(Cooja.getTopParentContainer());

    // Set default values of text fields from the MoteTypeConfig contents.
    descriptionField.setText(cfg.desc());
    var file = cfg.file();
    contikiField.setText(file == null ? "" : file);
    var dialogState = file == null
            ? DialogState.NO_SELECTION
            : file.endsWith(".c") ? DialogState.SELECTED_SOURCE : DialogState.SELECTED_FIRMWARE;
    for (var intf : cfg.interfaces()) {
      addMoteInterface(intf, true);
    }
    final var commands = cfg.commands();
    if (commands != null) {
      commandsArea.setText(commands);
      dialogState = DialogState.AWAITING_COMPILATION;
    }
    setDialogState(dialogState);
  }

  /** Returns the results as a mote type configuration, or null on failure. */
  public BaseContikiMoteType.MoteTypeConfig results() {
    if (contikiFirmware == null || !contikiFirmware.exists()) {
      return null;
    }
    ArrayList<Class<? extends MoteInterface>> selected = new ArrayList<>();
    for (var c : moteIntfBox.getComponents()) {
      if (c instanceof JCheckBox checkbox) {
        if (checkbox.isSelected()) {
          selected.add((Class<? extends MoteInterface>) checkbox.getClientProperty("class"));
        }
      }
    }
    return new BaseContikiMoteType.MoteTypeConfig(descriptionField.getText(), null, contikiField.getText(),
            commandsArea.getText(), selected, null, null);
  }

  public abstract boolean canLoadFirmware(String name);

  /**
   * @see DialogState
   * @param dialogState New dialog state
   */
  protected void setDialogState(DialogState dialogState) {
    final var input = contikiField.getText();
    final Path inputPath = Path.of(input);
    compileButton.setText("Compile");
    getRootPane().setDefaultButton(compileButton);
    switch (dialogState) {
      case NO_SELECTION -> {
        cleanButton.setEnabled(false);
        compileButton.setEnabled(false);
        createButton.setEnabled(false);
        commandsArea.setEnabled(false);
      }
      case SELECTED_SOURCE, AWAITING_COMPILATION -> {
        if (!input.endsWith(".c") || !Files.exists(inputPath)) {
          setDialogState(DialogState.NO_SELECTION);
          return;
        }
        cleanButton.setEnabled(true);
        compileButton.setEnabled(true);
        createButton.setEnabled(false);
        commandsArea.setEnabled(true);
        if (dialogState == DialogState.SELECTED_SOURCE) {
          contikiSource = new File(input);
          commandsArea.setText(getDefaultCompileCommands(input));
          contikiFirmware = moteType.getExpectedFirmwareFile(input);
        }
      }
      case IS_COMPILING -> {
        cleanButton.setEnabled(false);
        compileButton.setEnabled(false);
        compileButton.setText("Compiling");
        createButton.setEnabled(false);
        commandsArea.setEnabled(false);
      }
      case COMPILED_FIRMWARE -> {
        cleanButton.setEnabled(true);
        compileButton.setEnabled(true);
        createButton.setEnabled(true);
        commandsArea.setEnabled(true);
        getRootPane().setDefaultButton(createButton);
      }
      case SELECTED_FIRMWARE -> {
        if (!Files.exists(inputPath) || !canLoadFirmware(input)) {
          setDialogState(DialogState.NO_SELECTION);
          return;
        }
        contikiSource = null;
        contikiFirmware = new File(input);
        cleanButton.setEnabled(false);
        compileButton.setEnabled(false);
        createButton.setEnabled(true);
        commandsArea.setEnabled(false);
        getRootPane().setDefaultButton(createButton);
        commandsArea.setText("");
      }
    }
  }

  /**
   * Adds given mote interface to mote interface list, represented by a checkbox.
   * If mote interface already exists, this method call is ignored.
   *
   * @param intfClass Mote interface class
   * @param selected If true, interface will initially be selected
   */
  protected void addMoteInterface(Class<? extends MoteInterface> intfClass, boolean selected) {
    /* If mote interface was already added  */
    for (Component c : moteIntfBox.getComponents()) {
      if (!(c instanceof JCheckBox checkBox)) {
        continue;
      }
      if (checkBox.getClientProperty("class") == intfClass) {
        checkBox.setSelected(selected);
        return;
      }
    }

    /* Create new mote interface checkbox */
    JCheckBox intfCheckBox = new JCheckBox(Cooja.getDescriptionOf(intfClass));
    intfCheckBox.setSelected(selected);
    intfCheckBox.putClientProperty("class", intfClass);
    intfCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    intfCheckBox.setToolTipText(intfClass.getName());
    intfCheckBox.addActionListener(e -> {
      if (contikiSource == null && contikiFirmware != null) {
        setDialogState(DialogState.SELECTED_FIRMWARE);
      } else if (contikiSource != null){
        setDialogState(DialogState.AWAITING_COMPILATION);
      } else {
        setDialogState(DialogState.SELECTED_SOURCE);
      }
    });

    // Always select clock, ID, and position interfaces.
    if (Clock.class.isAssignableFrom(intfClass) ||
            MoteID.class.isAssignableFrom(intfClass) ||
            Position.class.isAssignableFrom(intfClass)) {
      intfCheckBox.setEnabled(false);
      intfCheckBox.setSelected(true);
    }

    moteIntfBox.add(intfCheckBox);
  }

  /**
   * @param name Contiki source
   * @return Suggested compile commands for compiling source
   */
  protected String getDefaultCompileCommands(String name) {
    String sourceNoExtension = new File(name.substring(0, name.length() - 2)).getName();
    return "$(MAKE) -j$(CPUS) " +
            sourceNoExtension + "." + targetName + " TARGET=" + targetName;
  }

  private void abortAnyCompilation() {
    if (currentCompilationProcess == null) {
      return;
    }
    currentCompilationProcess.destroy();
    currentCompilationProcess = null;
  }

}
