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

package org.contikios.cooja.plugins;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT;

import de.sciss.syntaxpane.DefaultSyntaxKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.LogScriptEngine;
import org.contikios.cooja.Plugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.dialogs.JResourceChooser;
import org.contikios.cooja.util.StringUtils;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ClassDescription("Simulation script editor")
@PluginType(PluginType.PType.SIM_CONTROL_PLUGIN)
public class ScriptRunner implements Plugin, HasQuickHelp {
  private static final Logger logger = LoggerFactory.getLogger(ScriptRunner.class);

  private final Cooja gui;

  private final Simulation simulation;
  private final LogScriptEngine engine;

  private boolean activated;

  /** The script text when running in headless mode. */
  private final ArrayList<String> headlessScript = new ArrayList<>();
  private final JTabbedPane editorTabs;
  private final ArrayList<Boolean> codeEditorChanged = new ArrayList<>();
  private final JTextArea logTextArea;

  private final VisPlugin frame;

  public ScriptRunner(Simulation simulation, Cooja gui) {
    this.gui = gui;
    this.simulation = simulation;

    if (!Cooja.isVisualized()) {
      frame = null;
      editorTabs = null;
      logTextArea = null;
      engine = simulation.newScriptEngine(null, Cooja.configuration.nashornArgs());
      return;
    }

    DefaultSyntaxKit.initKit();
    frame = new VisPlugin("Simulation script editor", gui, this);

    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    JMenu runMenu = new JMenu("Run");

    menuBar.add(fileMenu);
    menuBar.add(runMenu);

    frame.setJMenuBar(menuBar);

    /* Script area */
    frame.setLayout(new BorderLayout());
    editorTabs = new JTabbedPane();
    editorTabs.setTabLayoutPolicy(SCROLL_TAB_LAYOUT);

    logTextArea = new JTextArea(12,50);
    logTextArea.setMargin(new Insets(5,5,5,5));
    logTextArea.setEditable(false);
    logTextArea.setCursor(null);
    engine = simulation.newScriptEngine(logTextArea, Cooja.configuration.nashornArgs());

    var newScript = new JMenuItem("New");
    newScript.addActionListener(l -> {
      checkForUpdatesAndSave();
      updateScript("", null);
    });
    fileMenu.add(newScript);
    var open = new JMenuItem("Open...");
    open.addActionListener(l -> {
      var f = showFileChooser(true);
      if (f == null) {
        return;
      }
      checkForUpdatesAndSave();
      setLinkFile(f);
    });
    fileMenu.add(open);
    var saveAs = new JMenuItem("Save as...");
    saveAs.addActionListener(l -> saveScript(true));
    fileMenu.add(saveAs);
    var closeTab = new JMenuItem("Close tab");
    closeTab.addActionListener(l -> {
      var ix = editorTabs.getSelectedIndex();
      editorTabs.remove(ix);
      codeEditorChanged.remove(ix);
      if (editorTabs.getTabCount() > 0) {
        editorTabs.setSelectedIndex(Math.max(0, ix - 1));
      }
    });
    fileMenu.add(closeTab);
    // Example scripts.
    var examplesMenu = new JMenuItem("Load example script...");
    examplesMenu.addActionListener(e -> {
      var f = JResourceChooser.newResourceChooser(ScriptRunner.class, "/scripts");
      if (f == null) {
        return;
      }
      checkForUpdatesAndSave();
      updateScript(loadScript(f), null);
    });
    fileMenu.add(examplesMenu);

    final JCheckBoxMenuItem activateMenuItem = new JCheckBoxMenuItem("Activate");
    activateMenuItem.addActionListener(ev -> {
      if (activated) {
        engine.deactivateScript();
        activated = false;
        editorTabs.setEnabled(true);
        getEditor(editorTabs.getSelectedIndex()).setEnabled(true);
        updateTitle();
      } else {
        activateScript();
      }
    });
    runMenu.add(activateMenuItem);

    frame.doLayout();
    var centerPanel = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            editorTabs,
            new JScrollPane(logTextArea)
    );

    MenuListener toggleMenuItems = new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        newScript.setEnabled(!activated);
        open.setEnabled(!activated);
        saveAs.setEnabled(editorTabs.getTabCount() > 0);
        closeTab.setEnabled(!activated && editorTabs.getTabCount() > 0);
        examplesMenu.setEnabled(!activated);
        activateMenuItem.setSelected(activated);
        activateMenuItem.setEnabled(editorTabs.getTabCount() > 0);
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }
      @Override
      public void menuCanceled(MenuEvent e) {
      }
    };
    fileMenu.addMenuListener(toggleMenuItems);
    runMenu.addMenuListener(toggleMenuItems);

    centerPanel.setOneTouchExpandable(true);
    centerPanel.setResizeWeight(0.5);

    JPanel southPanel = new JPanel(new BorderLayout());
    frame.getContentPane().add(BorderLayout.CENTER, centerPanel);
    frame.getContentPane().add(BorderLayout.SOUTH, southPanel);

    frame.setSize(600, 700);
    Dimension maxSize = Cooja.getDesktopPane().getSize();
    if (frame.getWidth() > maxSize.getWidth()) {
      frame.setSize((int)maxSize.getWidth(), frame.getHeight());
    }
    if (frame.getHeight() > maxSize.getHeight()) {
      frame.setSize(frame.getWidth(), (int)maxSize.getHeight());
    }

    /* Set default script */
    updateScript(loadScript("basic.js"), null);
  }

  private void saveScript(boolean saveToLinkedFile) {
    var ix = editorTabs.getSelectedIndex();
    var f = !saveToLinkedFile ? getLinkedFile(ix) : showFileChooser(false);
    if (f == null) {
      return;
    }
    try (var writer = Files.newBufferedWriter(f.toPath(), UTF_8)) {
      writer.write(getEditor(ix).getText());
      codeEditorChanged.set(ix, false);
      updateTabTitle(f);
    } catch (IOException e) {
      logger.error("Failed to save script file: {}", f, e);
    }
  }

  /** Returns the editor in a specific tab.
   *
   * @param ix Index for tab
   * @return The editor
   */
  private JEditorPane getEditor(int ix) {
    return (JEditorPane) ((JScrollPane) editorTabs.getComponentAt(ix)).getViewport().getView();
  }

  /** Returns the file belonging to an editor in a specific tab.
   *
   * @param ix Index for tab
   * @return The file
   */
  private File getLinkedFile(int ix){
    var name = editorTabs.getToolTipTextAt(ix);
    return name.endsWith(".js") && Files.exists(Path.of(name)) ? new File(name) : null;
  }

  @Override
  public JInternalFrame getCooja() {
    return frame;
  }

  @Override
  public void startPlugin() {
  }

  private boolean setLinkFile(File source) {
    String script = StringUtils.loadFromFile(source);
    if (script == null) {
      logger.error("Could not read " + source);
      return false;
    }
    updateScript(script, source);
    if (Cooja.isVisualized()) {
      Cooja.setExternalToolsSetting("SCRIPTRUNNER_LAST_SCRIPTFILE", source.getAbsolutePath());
    }
    return true;
  }

  /** Returns the scripts concatenated, tabs from left to right in visual mode. */
  private String getCombinedScripts() {
    if (!Cooja.isVisualized()) {
      return String.join("\n", headlessScript);
    }
    var sb = new StringBuilder();
    for (int i = 0; i < editorTabs.getTabCount(); i++) {
      sb.append(getEditor(i).getText());
    }
    return sb.toString();
  }

  private boolean activateScript() {
    CompiledScript script;
    try {
      script = engine.compileScript(getCombinedScripts());
    } catch (ScriptException e) {
      logger.error("Test script error: ", e);
      if (Cooja.isVisualized()) {
        Cooja.showErrorDialog("Script error", e, false);
      }
      return false;
    }

    if (!engine.activateScript(script)) {
      return false;
    }
    activated = true;
    if (Cooja.isVisualized()) {
      logTextArea.setText("");
      editorTabs.setEnabled(false);
      getEditor(editorTabs.getSelectedIndex()).setEnabled(false);
      updateTitle();
    }
    return true;
  }

  private void updateTitle() {
    String title = "Simulation script editor";
    if (activated) {
      title += " *active*";
    }
    frame.setTitle(title);
  }

  private void updateTabTitle(File file) {
    editorTabs.setTitleAt(editorTabs.getSelectedIndex(), file == null ? "Script" : file.getName());
    editorTabs.setToolTipTextAt(editorTabs.getSelectedIndex(), file == null ? "Script" : file.getAbsolutePath());
  }

  /** Check if the script has been updated and offer the user to save the changes. */
  private void checkForUpdatesAndSave() {
    for (int i = 0; editorTabs != null && i < editorTabs.getTabCount(); i++) {
      var linkedFile = getLinkedFile(i);
      if (!codeEditorChanged.get(i) || linkedFile == null) continue;
      if (JOptionPane.showConfirmDialog(Cooja.getTopParentContainer(),
              "Do you want to save the changes to " + linkedFile.getAbsolutePath() + "?",
              "Save script changes", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
        saveScript(false);
      }
      // User has chosen, do not ask again for the current modifications.
      codeEditorChanged.set(i, false);
    }
  }

  /** Make a new tab containing an editor. */
  private void newTab(String script, File file) {
    var editor = new JEditorPane();
    editor.setContentType("text/javascript");
    editorTabs.addTab(null, new JScrollPane(editor));
    editorTabs.setSelectedIndex(editorTabs.getTabCount() - 1);
    editor.setText(script);
    codeEditorChanged.add(false);
    updateTabTitle(file);
    editor.getDocument().addDocumentListener(new DocumentListener() {
      private void modified() {
        var ix = editorTabs.getSelectedIndex();
        if (getLinkedFile(ix) != null && editor == getEditor(ix)) {
          codeEditorChanged.set(ix, true);
        }
      }
      @Override
      public void insertUpdate(DocumentEvent documentEvent) {
        modified();
      }

      @Override
      public void removeUpdate(DocumentEvent documentEvent) {
        modified();
      }

      @Override
      public void changedUpdate(DocumentEvent documentEvent) {
        modified();
      }
    });
  }

  private void updateScript(String script, File file) {
    if (Cooja.isVisualized()) {
      newTab(script, file);
      logTextArea.setText("");
    } else {
      headlessScript.add(script);
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    checkForUpdatesAndSave();
    ArrayList<Element> config = new ArrayList<>();
    for (int i = 0; i < editorTabs.getTabCount(); i++) {
      var file = getLinkedFile(i);
      if (file != null) {
        Element element = new Element("scriptfile");
        element.setText(gui.createPortablePath(file).getPath().replace('\\', '/'));
        config.add(element);
      } else {
        Element element = new Element("script");
        element.setText(getEditor(i).getText());
        config.add(element);
      }
    }

    if (activated) {
      Element element = new Element("active");
      element.setText(String.valueOf(true));
      config.add(element);
    }

    return config;
  }

  @Override
  public void closePlugin() {
    checkForUpdatesAndSave();
    simulation.removeScriptEngine(engine);
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    if (Cooja.isVisualized()) {
      editorTabs.removeAll(); // Remove the example tab opened by the constructor.
    }
    boolean activate = false;
    for (Element element : configXML) {
      String name = element.getName();
      if ("script".equals(name)) {
        updateScript(element.getText(), null);
      } else if ("scriptfile".equals(name)) {
        if (!setLinkFile(gui.restorePortablePath(new File(element.getText().trim())))) {
          return false;
        }
      } else if ("active".equals(name)) {
        activate = Boolean.parseBoolean(element.getText());
      }
    }

    // Automatically activate script in headless mode.
    if (activate || !Cooja.isVisualized()) {
      return activateScript();
    }

    return true;
  }

  private static String loadScript(String file) {
    return StringUtils.loadFromStream(ScriptRunner.class.getResourceAsStream("/scripts/" + file));
  }

  /**
   * Show a file chooser dialog for opening or saving a JavaScript file.
   *
   * @param open True shows an open dialog, false shows a save dialog.
   * @return The file chosen, null on cancel.
   */
  private File showFileChooser(boolean open) {
    var chooser = new JFileChooser();
    String suggest = Cooja.getExternalToolsSetting("SCRIPTRUNNER_LAST_SCRIPTFILE", null);
    if (suggest != null) {
      chooser.setSelectedFile(new File(suggest));
    } else {
      chooser.setCurrentDirectory(new File("."));
    }
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setDialogTitle("Select script file");
    chooser.setFileFilter(new FileNameExtensionFilter("JavaScript", "js"));
    var choice = open ? chooser.showOpenDialog(frame) : chooser.showSaveDialog(frame);
    return choice == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
  }

  @Override
  public String getQuickHelp() {
    return """
            <b>Script Editor</b>
            <p>Perform logging and computation based on simulation events.
            <p>The tabs represent a single program, with the leftmost tab containing line 1 of the program,
            and the rightmost tab containing the last line of the program.
            """;
  }
}
