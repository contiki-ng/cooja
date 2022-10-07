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

import de.sciss.syntaxpane.DefaultSyntaxKit;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.LogScriptEngine;
import org.contikios.cooja.Plugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.util.StringUtils;
import org.jdom.Element;

@ClassDescription("Simulation script editor")
@PluginType(PluginType.SIM_CONTROL_PLUGIN)
public class ScriptRunner implements Plugin {
  private static final Logger logger = LogManager.getLogger(ScriptRunner.class);

  private final Cooja gui;

  private static final String[] EXAMPLE_SCRIPTS = new String[] {
      "basic.js", "Various commands",
      "helloworld.js", "Wait for 'Hello, world'",
      "log_all.js", "Just log all printf()'s and timeout",
      "shell.js", "Basic shell interaction",
      "plugins.js", "Interact with surrounding Cooja plugins",
  };

  private final Simulation simulation;
  private final LogScriptEngine engine;

  private boolean activated = false;

  /** The script text when running in headless mode. */
  private String headlessScript = null;
  private final JEditorPane codeEditor;
  private boolean codeEditorChanged = false;
  private final JTextArea logTextArea;

  private File linkedFile = null;
  private final VisPlugin frame;

  public ScriptRunner(Simulation simulation, Cooja gui) {
    this.gui = gui;
    this.simulation = simulation;

    if (!Cooja.isVisualized()) {
      frame = null;
      codeEditor = null;
      logTextArea = null;
      engine = simulation.newScriptEngine(null);
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
    codeEditor = newEditor();

    logTextArea = new JTextArea(12,50);
    logTextArea.setMargin(new Insets(5,5,5,5));
    logTextArea.setEditable(false);
    logTextArea.setCursor(null);
    engine = simulation.newScriptEngine(logTextArea);

    var newScript = new JMenuItem("New");
    newScript.addActionListener(l -> {
      checkForUpdatesAndSave();
      linkedFile = null;
      updateScript("");
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
    /* Example scripts */
    final JMenu examplesMenu = new JMenu("Load example script");
    for (int i=0; i < EXAMPLE_SCRIPTS.length; i += 2) {
      final String file = EXAMPLE_SCRIPTS[i];
      JMenuItem exampleItem = new JMenuItem(EXAMPLE_SCRIPTS[i+1]);
      exampleItem.addActionListener(e -> {
        checkForUpdatesAndSave();
        linkedFile = null;
        updateScript(loadScript(file));
      });
      examplesMenu.add(exampleItem);
    }
    fileMenu.add(examplesMenu);

    final JCheckBoxMenuItem activateMenuItem = new JCheckBoxMenuItem("Activate");
    activateMenuItem.addActionListener(ev -> {
      if (activated) {
        deactivateScript();
      } else {
        activateScript();
      }
    });
    runMenu.add(activateMenuItem);

    frame.doLayout();
    var centerPanel = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(codeEditor),
            new JScrollPane(logTextArea)
    );

    MenuListener toggleMenuItems = new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        activateMenuItem.setSelected(activated);
        examplesMenu.setEnabled(!activated);
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
    Dimension maxSize = gui.getDesktopPane().getSize();
    if (frame.getWidth() > maxSize.getWidth()) {
      frame.setSize((int)maxSize.getWidth(), frame.getHeight());
    }
    if (frame.getHeight() > maxSize.getHeight()) {
      frame.setSize(frame.getWidth(), (int)maxSize.getHeight());
    }

    /* Set default script */
    updateScript(loadScript(EXAMPLE_SCRIPTS[0]));
  }

  private void saveScript(boolean saveToLinkedFile) {
    var f = !saveToLinkedFile ? linkedFile : showFileChooser(false);
    if (f == null) {
      return;
    }
    try (var writer = Files.newBufferedWriter(f.toPath(), UTF_8)) {
      writer.write(codeEditor.getText());
      linkedFile = f;
      codeEditorChanged = false;
      updateTitle();
    } catch (IOException e) {
      logger.error("Failed to save script file: {}", f, e);
    }
  }

  @Override
  public JInternalFrame getCooja() {
    return frame;
  }

  @Override
  public void startPlugin() {
  }

  public boolean setLinkFile(File source) {
    String script = StringUtils.loadFromFile(source);
    if (script == null) {
      logger.error("Could not read " + source);
      return false;
    }
    linkedFile = source;
    updateScript(script);
    if (Cooja.isVisualized()) {
      Cooja.setExternalToolsSetting("SCRIPTRUNNER_LAST_SCRIPTFILE", source.getAbsolutePath());
    }
    return true;
  }

  private void deactivateScript() {
    engine.deactivateScript();
    if (Cooja.isVisualized()) {
      codeEditor.setEnabled(true);
      updateTitle();
    }
    activated = false;
  }

  private boolean activateScript() {
    CompiledScript script;
    try {
      script = engine.compileScript(Cooja.isVisualized() ? codeEditor.getText() : headlessScript);
    } catch (ScriptException e) {
      logger.fatal("Test script error: ", e);
      if (Cooja.isVisualized()) {
        Cooja.showErrorDialog(Cooja.getTopParentContainer(), "Script error", e, false);
      }
      return false;
    }

    if (!engine.activateScript(script)) {
      return false;
    }
    if (Cooja.isVisualized()) {
      logTextArea.setText("");
      codeEditor.setEnabled(false);
      updateTitle();
    }
    activated = true;
    return true;
  }

  private void updateTitle() {
    String title = "Simulation script editor";
    if (linkedFile != null) {
      title += " (" + linkedFile.getName() + ")";
    }
    if (activated) {
      title += " *active*";
    }
    frame.setTitle(title);
  }

  /** Check if the script has been updated and offer the user to save the changes. */
  private void checkForUpdatesAndSave() {
    if (!codeEditorChanged) {
      return;
    }
    if (JOptionPane.showConfirmDialog(Cooja.getTopParentContainer(),
            "Do you want to save the changes to " + linkedFile.getAbsolutePath() + "?",
            "Save script changes", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
      saveScript(false);
    }
    // User has chosen, do not ask again for the current modifications.
    codeEditorChanged = false;
  }

  /** Make a new code editor. */
  private JEditorPane newEditor() {
    var editor = new JEditorPane();
    editor.setContentType("text/javascript");
    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent documentEvent) {
        codeEditorChanged = linkedFile != null;
      }

      @Override
      public void removeUpdate(DocumentEvent documentEvent) {
        codeEditorChanged = linkedFile != null;
      }

      @Override
      public void changedUpdate(DocumentEvent documentEvent) {
        codeEditorChanged = linkedFile != null;
      }
    });
    return editor;
  }

  private void updateScript(String script) {
    if (Cooja.isVisualized()) {
      codeEditor.setText(script);
      codeEditorChanged = false;
      logTextArea.setText("");
      updateTitle();
    } else {
      headlessScript = script;
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    checkForUpdatesAndSave();
    ArrayList<Element> config = new ArrayList<>();
    if (linkedFile != null) {
      Element element = new Element("scriptfile");
      element.setText(simulation.getCooja().createPortablePath(linkedFile).getPath().replace('\\', '/'));
      config.add(element);
    } else {
      Element element = new Element("script");
      element.setText(codeEditor.getText());
      config.add(element);
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
    deactivateScript();
    simulation.removeScriptEngine(engine);
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    boolean activate = false;
    for (Element element : configXML) {
      String name = element.getName();
      if ("script".equals(name)) {
        updateScript(element.getText());
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
    chooser.setFileFilter(new FileFilter() {
      @Override
      public boolean accept(File file) {
        return file.isDirectory() || file.getName().endsWith(".js");
      }
      @Override
      public String getDescription() {
        return "JavaScript";
      }
    });
    var choice = open ? chooser.showOpenDialog(frame) : chooser.showSaveDialog(frame);
    return choice == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
  }
}
