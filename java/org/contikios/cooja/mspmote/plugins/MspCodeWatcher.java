/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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

package org.contikios.cooja.mspmote.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.table.AbstractTableModel;

import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MotePlugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.WatchpointMote;
import org.contikios.cooja.WatchpointMote.WatchpointListener;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.mspmote.MspMoteType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.ui.DebugUI;
import se.sics.mspsim.util.DebugInfo;
import se.sics.mspsim.util.ELFDebug;

@ClassDescription("Msp Code Watcher")
@PluginType(PluginType.PType.MOTE_PLUGIN)
@SupportedArguments(motes = {MspMote.class})
public class MspCodeWatcher extends VisPlugin implements MotePlugin, HasQuickHelp {
  private static final int SOURCECODE = 0;
  private static final int BREAKPOINTS = 2;

  private static final Logger logger = LoggerFactory.getLogger(MspCodeWatcher.class);
  private final Simulation simulation;
  private File currentCodeFile;
  private int currentLineNumber = -1;

  private final DebugUI assCodeUI;
  private final CodeUI sourceCodeUI;
  private final BreakpointsUI breakpointsUI;

  private final MspMote mspMote; /* currently the only supported mote */
  private final WatchpointMote watchpointMote;
  private WatchpointListener watchpointListener;

  private final JComboBox<String> fileComboBox = new JComboBox<>();
  private File[] sourceFiles;

  private final JTabbedPane mainPane;

  private ArrayList<Rule> rules;
  private final ELFDebug debug;
  private String[] debugSourceFiles;

  /**
   * Mini-debugger for MSP Motes.
   * Visualizes instructions, source code and allows a user to manipulate breakpoints.
   *
   * @param mote MSP Mote
   * @param simulationToVisualize Simulation
   * @param gui Simulator
   */
  public MspCodeWatcher(Mote mote, Simulation simulationToVisualize, Cooja gui) {
    super("Msp Code Watcher - " + mote, gui);
    simulation = simulationToVisualize;
    this.mspMote = (MspMote) mote;
    this.watchpointMote = (WatchpointMote) mote;
    try {
      debug = ((MspMoteType)mspMote.getType()).getELF().getDebug();
      if (debug == null) {
        throw new RuntimeException("No debugging info found in firmware, aborting");
      }
      debugSourceFiles = debug.getSourceFiles();
      if (debugSourceFiles == null) {
        throw new RuntimeException("No debugging info found in firmware, aborting");
      }
    } catch (IOException e1) {
      throw new RuntimeException("No debugging info found in firmware, aborting");
    }

    /* XXX Temporary workaround: source file removing duplicates awaiting Mspsim update */
    {
      ArrayList<String> newDebugSourceFiles = new ArrayList<>();
      for (String sf: debugSourceFiles) {
        if (!newDebugSourceFiles.contains(sf)) {
          newDebugSourceFiles.add(sf);
        }
      }
      debugSourceFiles = newDebugSourceFiles.toArray(new String[0]);
    }


    rules = new ArrayList<>();

    loadDefaultRules();

    getContentPane().setLayout(new BorderLayout());

    /* Create source file list */
    fileComboBox.addActionListener(e -> sourceFileSelectionChanged());
    fileComboBox.setRenderer(new BasicComboBoxRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value,
          int index, boolean isSelected, boolean cellHasFocus) {
        if (isSelected) {
          setBackground(list.getSelectionBackground());
          setForeground(list.getSelectionForeground());
          if (index > 0) {
            list.setToolTipText(sourceFiles[index-1].getPath());
          }
        } else {
          setBackground(list.getBackground());
          setForeground(list.getForeground());
        }
        setFont(list.getFont());
        setText((value == null) ? "" : value.toString());
        return this;
      }
    });

    /* Browse code control (north) */
    Box sourceCodeControl = Box.createHorizontalBox();
    sourceCodeControl.add(new JButton(stepAction));
    sourceCodeControl.add(Box.createHorizontalStrut(10));
    sourceCodeControl.add(new JLabel("Current location: "));
    sourceCodeControl.add(new JButton(currentFileAction));
    sourceCodeControl.add(Box.createHorizontalGlue());
    sourceCodeControl.add(new JLabel("Source files: "));
    sourceCodeControl.add(fileComboBox);
    sourceCodeControl.add(Box.createHorizontalStrut(5));
    var mapAction = new AbstractAction("Locate sources...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        tryMapDebugInfo();
      }
    };
    sourceCodeControl.add(new JButton(mapAction));
    sourceCodeControl.add(Box.createHorizontalStrut(10));

    /* Execution control panel (south of source code panel) */

    /* Layout */
    mainPane = new JTabbedPane();

    sourceCodeUI = new CodeUI(watchpointMote);
    JPanel sourceCodePanel = new JPanel(new BorderLayout());
    sourceCodePanel.add(BorderLayout.CENTER, sourceCodeUI);
    mainPane.addTab("Source code", null, sourceCodePanel, null); /* SOURCECODE */

    assCodeUI = new DebugUI(this.mspMote.getCPU(), true);
    for (Component c: assCodeUI.getComponents()) {
      c.setBackground(Color.WHITE);
    }
    mainPane.addTab("Instructions", null, assCodeUI, null);

    breakpointsUI = new BreakpointsUI(mspMote, this);
    mainPane.addTab("Breakpoints", null, breakpointsUI, "Right-click source code to add"); /* BREAKPOINTS */

    add(BorderLayout.CENTER, mainPane);
    add(BorderLayout.SOUTH, sourceCodeControl);

    /* Listen for breakpoint changes */
    watchpointMote.addWatchpointListener(watchpointListener = new WatchpointListener() {
      @Override
      public void watchpointTriggered(final Watchpoint watchpoint) {
        SwingUtilities.invokeLater(() -> {
          logger.info("Watchpoint triggered: " + watchpoint);
          if (simulation.isRunning()) {
            return;
          }
          breakpointsUI.selectBreakpoint(watchpoint);
          sourceCodeUI.updateBreakpoints();
          showCurrentPC();
        });
      }
      @Override
      public void watchpointsChanged() {
        sourceCodeUI.updateBreakpoints();
      }
    });

    /* Observe when simulation starts/stops */
    simulation.getSimulationStateTriggers().addTrigger(this, (state, simulation) -> EventQueue.invokeLater(() -> {
      if (state == EventTriggers.Operation.STOP) {
        stepAction.setEnabled(true);
        showCurrentPC();
      } else {
        stepAction.setEnabled(false);
      }
    }));
    stepAction.setEnabled(!simulation.isRunning());

    setSize(750, 500);
    showCurrentPC();
  }

  @Override
  public void startPlugin() {
    super.startPlugin();
    updateFileComboBox();
  }

  private void updateFileComboBox() {
    sourceFiles = getSourceFiles(rules);
    fileComboBox.removeAllItems();
    fileComboBox.addItem("[view sourcefile]");
    for (File f: sourceFiles) {
      fileComboBox.addItem(f.getName());
    }
    fileComboBox.setSelectedIndex(0);
  }

  public void displaySourceFile(final File file, final int line, final boolean markCurrent) {
    SwingUtilities.invokeLater(() -> {
      mainPane.setSelectedIndex(SOURCECODE); /* code */
      sourceCodeUI.displayNewCode(file, line, markCurrent);
    });
  }

  private void sourceFileSelectionChanged() {
    int index = fileComboBox.getSelectedIndex();
    if (index <= 0) {
      return;
    }

    File selectedFile = sourceFiles[index-1];
    displaySourceFile(selectedFile, -1, false);
  }

  private void showCurrentPC() {
    /* Instructions */
    assCodeUI.updateRegs();
    assCodeUI.repaint();

    /* Source */
    updateCurrentSourceCodeFile();
    File file = currentCodeFile;
    int line = currentLineNumber;
    if (file == null || line <= 0) {
      currentFileAction.setEnabled(false);
      currentFileAction.putValue(Action.NAME, "[unknown]");
      currentFileAction.putValue(Action.SHORT_DESCRIPTION, null);
      return;
    }
    currentFileAction.setEnabled(true);
    currentFileAction.putValue(Action.NAME, file.getName() + ":" + line);
    currentFileAction.putValue(Action.SHORT_DESCRIPTION, file.getAbsolutePath() + ":" + line + ", PC:" + String.format("0x%04x", mspMote.getCPU().getPC()));
    fileComboBox.setSelectedItem(file.getName());

    displaySourceFile(file, line, true);
  }

  @Override
  public void closePlugin() {
    watchpointMote.removeWatchpointListener(watchpointListener);
    watchpointListener = null;

    simulation.getSimulationStateTriggers().deleteTriggers(this);

    /* TODO XXX Unregister breakpoints? */
  }

  private void updateCurrentSourceCodeFile() {
    currentCodeFile = null;

    try {
      int pc = mspMote.getCPU().getPC();
      DebugInfo debugInfo = debug.getDebugInfo(pc);
      if (pc <= 0) {
        return;
      }
      if (debugInfo == null) {
        logger.warn("No source info at " + String.format("0x%04x", pc));
        return;
      }
      File f = applySubstitutionRules(new File(debugInfo.getPath(), debugInfo.getFile()), rules);
      if (f == null || !f.exists()) {
        logger.warn("Unknown source at " + String.format("0x%04x", pc) + ": " + debugInfo.getPath() + " " + debugInfo.getFile());
        return;
      }

      currentCodeFile = f;
      currentLineNumber = debugInfo.getLine();
    } catch (Exception e) {
      logger.error("Exception: " + e.getMessage(), e);
      currentCodeFile = null;
      currentLineNumber = -1;
    }
  }

  private int getLocatedSourcesCount() {
    return getSourceFiles(rules).length;
  }

  private void updateRulesUsage() {
    for (Rule rule: rules) {
      rule.prefixMatches = 0;
      rule.locatesFile = 0;
    }
    getSourceFiles(rules);
    rulesMatched = new int[rules.size()];
    rulesOK = new int[rules.size()];
    for (int i=0; i < rules.size(); i++) {
      rulesMatched[i] = rules.get(i).prefixMatches;
      rulesOK[i] = rules.get(i).locatesFile;
    }
  }

  @Override
  public String getQuickHelp() {
    return "<b>MSPSim's Code Watcher</b><br>" +
            "<br>This plugin shows the source code for MSP430 based motes and can set or remove breakpoints." +
            "<br><br>" +
            "<b>Note:</b> You must compile the code with <i>DEBUG=1</i> to include information about the source code in the build." +
            "<br><br>Example: $(MAKE) -j$(CPUS) TARGET=sky DEBUG=1 hello-world";
  }

  private class Rule {
    String from;
    String to;
    int prefixMatches;
    int locatesFile;
    Rule(String from, String to) {
      this.from = from;
      this.to = to;
    }
    File apply(File f) {
      if (f == null) {
        return null;
      }
      if (from == null) {
        return null;
      }
      if (!f.getPath().startsWith(from)) {
        if (rulesWithDebuggingOutput) {
          rulesDebuggingOutput.addMessage(this + " does not match: " + f.getPath(), MessageList.ERROR);
        }
        return null;
      }
      prefixMatches++;
      if (rulesWithDebuggingOutput) {
        rulesDebuggingOutput.addMessage(this + " testing on: " + f.getPath());
      }
      if (to == null) {
        if (rulesWithDebuggingOutput) {
          rulesDebuggingOutput.addMessage(this + " enter substitution: " + f.getPath(), MessageList.ERROR);
        }
        return null;
      }
      File file = new File(to + f.getPath().substring(from.length()));
      if (!file.exists()) {
        if (rulesWithDebuggingOutput) {
          rulesDebuggingOutput.addMessage(this + " not found: " + file.getPath(), MessageList.ERROR);
        }
        return null;
      }
      if (rulesWithDebuggingOutput) {
        rulesDebuggingOutput.addMessage(this + " OK: " + f.getPath());
      }
      locatesFile++;
      return file;
    }
    @Override
    public String toString() {
      return "[" + from + "|" + to + "]";
    }
  }

  private static File applySubstitutionRules(String file, Collection<Rule> rules) {
    return applySubstitutionRules(new File(file), rules);
  }
  private static File applySubstitutionRules(File file, Collection<Rule> rules) {
    if (file == null) {
      return null;
    }
    if (file.exists()) {
      return file;
    }

    for (Rule rule: rules) {
      File f = rule.apply(file);
      if (f != null && f.exists()) {
        return f;
      }
    }
    return null;
  }

  private void loadDefaultRules() {
    String rulesString = Cooja.getExternalToolsSetting("MSPCODEWATCHER_RULES", "/cygdrive/c/*c:/");
    String[] rulesArr = rulesString.split("\\*");
    rules.clear();
    for (int i=0; i < rulesArr.length/2; i++) {
      Rule r = new Rule(rulesArr[i*2], rulesArr[i*2+1]);
      if (r.from.equals("[empty]")) {
        r.from = "";
      }
      if (r.to.equals("[empty]")) {
        r.to = "";
      }
      rules.add(r);
    }
  }

  private final MessageListUI rulesDebuggingOutput = new MessageListUI();
  private boolean rulesWithDebuggingOutput;
  private int[] rulesMatched;
  private int[] rulesOK;
  private JTable table;
  private void tryMapDebugInfo() {
    /* called from AWT */
    int r = JOptionPane.showConfirmDialog(Cooja.getTopParentContainer(),
        "The firmware file " + ((MspMoteType) mspMote.getType()).getContikiFirmwareFile().getName() + " references " + debugSourceFiles.length + " source files.\n" +
        "This function tries to locate these files on disk with a set of simple substitution rules.\n" +
        "\n" +
        "Right now " + getLocatedSourcesCount() + "/" + debugSourceFiles.length + " source files can be found.",
        "Locate source files", JOptionPane.OK_CANCEL_OPTION);
    if (r != JOptionPane.OK_OPTION) {
      return;
    }

    /* table with rules */
    rulesDebuggingOutput.clearMessages();
    final JDialog dialog = new JDialog(Cooja.getTopParentContainer(), "Locate source files");
    dialog.setModal(true);
    updateRulesUsage();
    AbstractTableModel model = new AbstractTableModel() {
      @Override
      public int getRowCount() {
        return 10;
      }
      @Override
      public int getColumnCount() {
        return 4;
      }
      @Override
      public String getColumnName(int col) {
        if (col == 0) {
          return "Prefix";
        }
        if (col == 1) {
          return "Substitution";
        }
        if (col == 2) {
          return "Matched";
        }
        if (col == 3) {
          return "OK";
        }
        return null;
      }
      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= rules.size()) {
          return null;
        }
        Rule rule = rules.get(rowIndex);
        if (columnIndex == 0) {
          return rule.from;
        }
        if (columnIndex == 1) {
          return rule.to;
        }
        if (columnIndex == 2) {
          if (rulesMatched == null || rowIndex >= rulesMatched.length) {
            return "[click Apply]";
          }
          return rulesMatched[rowIndex];
        }
        if (columnIndex == 3) {
          if (rulesOK == null || rowIndex >= rulesOK.length) {
            return "[click Apply]";
          }
          return rulesOK[rowIndex];
        }
        return null;
      }
      @Override
      public boolean isCellEditable(int row, int column) {
        if (column == 0) {
          return true;
        }
        return column == 1;
      }
      @Override
      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Rule rule;
        if (rowIndex < 0) {
          return;
        }
        if (rowIndex < rules.size()) {
          rule = rules.get(rowIndex);
        } else {
          do {
            rule = new Rule("", "");
            rules.add(rule);
          } while (rowIndex >= rules.size());
        }
        if (columnIndex == 0) {
          rule.from = (String) aValue;
        }
        if (columnIndex == 1) {
          rule.to = (String) aValue;
        }
        rulesMatched = null;
        rulesOK = null;
        table.invalidate();
        table.repaint();
      }
    };
    table = new JTable(model);

    /* control panel: save/load, clear/apply/close */
    final JButton applyButton = new JButton("Apply");
    applyButton.addActionListener(e -> {
      /* Remove trailing empty rules */
      ArrayList<Rule> trimmedRules = new ArrayList<>();
      for (Rule rule: rules) {
        if (rule.from == null || rule.from.trim().isEmpty()) {
          rule.from = "";
        }
        if (rule.to == null || rule.to.trim().isEmpty()) {
          rule.to = "";
        }
        if (rule.from.isEmpty() && rule.to.isEmpty()) {
          /* ignore */
          continue;
        }
        trimmedRules.add(rule);
      }
      rules = trimmedRules;

      rulesDebuggingOutput.clearMessages();
      rulesDebuggingOutput.addMessage("Applying " + rules.size() + " substitution rules");
      rulesWithDebuggingOutput = true;
      updateRulesUsage();
      rulesWithDebuggingOutput = false;
      rulesDebuggingOutput.addMessage("Done! " + "Located sources: " + getLocatedSourcesCount() + "/" + debugSourceFiles.length);
      rulesDebuggingOutput.addMessage(" ");
      for (String s: debugSourceFiles) {
        File f = applySubstitutionRules(s, rules);
        if (f == null || !f.exists()) {
          rulesDebuggingOutput.addMessage("Not yet located: " + s, MessageList.ERROR);
        }
      }

      table.invalidate();
      table.repaint();
    });
    JButton clearButton = new JButton("Clear");
    clearButton.addActionListener(e -> {
      rules.clear();
      applyButton.doClick();
    });

    JButton loadButton = new JButton("Load default");
    loadButton.addActionListener(e -> {
      loadDefaultRules();
      applyButton.doClick();
    });
    JButton saveButton = new JButton("Save as default");
    saveButton.addActionListener(e -> {
      StringBuilder sb = new StringBuilder();
      for (Rule r1 : rules) {
        sb.append("*");
        if (r1.from.isEmpty()) {
          sb.append("[empty]");
        } else {
          sb.append(r1.from);
        }
        sb.append("*");
        if (r1.to.isEmpty()) {
          sb.append("[empty]");
        } else {
          sb.append(r1.to);
        }
      }
      if (sb.length() >= 1) {
        Cooja.setExternalToolsSetting("MSPCODEWATCHER_RULES", sb.substring(1));
      } else {
        Cooja.setExternalToolsSetting("MSPCODEWATCHER_RULES", "");
      }
    });

    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(e -> {
      updateFileComboBox();
      dialog.dispose();
    });
    Box control = Box.createHorizontalBox();
    control.add(loadButton);
    control.add(saveButton);
    control.add(Box.createHorizontalGlue());
    control.add(clearButton);
    control.add(applyButton);
    control.add(closeButton);

    final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
        new JScrollPane(table),
        new JScrollPane(rulesDebuggingOutput));
    SwingUtilities.invokeLater(() -> {
      split.setDividerLocation(0.5);
      applyButton.doClick();
    });
    dialog.getContentPane().add(BorderLayout.CENTER, split);
    dialog.getContentPane().add(BorderLayout.SOUTH, control);
    dialog.getRootPane().setDefaultButton(closeButton);
    dialog.setSize(550, 500);
    dialog.setLocationRelativeTo(Cooja.getTopParentContainer());
    dialog.setVisible(true);
  }

  private File[] getSourceFiles(ArrayList<Rule> rules) {
    /* Verify that files exist */
    ArrayList<File> existing = new ArrayList<>();
    for (String sourceFile: debugSourceFiles) {
      /* Debug info to source file map */
      File f = applySubstitutionRules(sourceFile, rules);
      if (f != null && f.exists()) {
        existing.add(f);
      }
    }

    /* Sort alphabetically */
    File[] sorted = existing.toArray(new File[0]);
    Arrays.sort(sorted, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
    return sorted;
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    element = new Element("tab");
    element.addContent(String.valueOf(mainPane.getSelectedIndex()));
    config.add(element);

    for (Rule rule: rules) {
      element = new Element("rule");
      element.setAttribute("from", rule.from==null?"":rule.from);
      element.setAttribute("to", rule.to==null?"":rule.to);
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    boolean clearedRules = false;
    for (Element element : configXML) {
      if (element.getName().equals("tab")) {
        mainPane.setSelectedIndex(Integer.parseInt(element.getText()));
      } else if (element.getName().equals("rule")) {
        if (!clearedRules) {
          rules.clear();
          clearedRules = true;
        }
        rules.add(new Rule(element.getAttributeValue("from"), element.getAttributeValue("to")));
      }
    }
    return true;
  }

  private final AbstractAction currentFileAction = new AbstractAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (currentCodeFile == null) {
        return;
      }
      displaySourceFile(currentCodeFile, currentLineNumber, true);
    }
  };

  private final AbstractAction stepAction = new AbstractAction("Step instruction") {
    @Override
    public void actionPerformed(ActionEvent e) {
      try {
        mspMote.getCPU().stepInstructions(1);
      } catch (EmulationException ex) {
        logger.error("Error: ", ex);
      }
      showCurrentPC();
    }
  };

  @Override
  public Mote getMote() {
    return mspMote;
  }

}
