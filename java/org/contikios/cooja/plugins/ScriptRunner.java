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
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import de.sciss.syntaxpane.DefaultSyntaxKit;
import de.sciss.syntaxpane.actions.DefaultSyntaxAction;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import javax.script.ScriptException;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
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

  final String[] EXAMPLE_SCRIPTS = new String[] {
      "basic.js", "Various commands",
      "helloworld.js", "Wait for 'Hello, world'",
      "log_all.js", "Just log all printf()'s and timeout",
      "shell.js", "Basic shell interaction",
      "plugins.js", "Interact with surrounding Cooja plugins",
  };

  private final Simulation simulation;
  private LogScriptEngine engine;

  private static BufferedWriter logWriter = null; /* For non-GUI tests */

  /** The script text when running in headless mode. */
  private String headlessScript = null;
  private final JEditorPane codeEditor;
  private final JTextArea logTextArea;

  private JSyntaxLinkFile actionLinkFile = null;
  private File linkedFile = null;
  private final VisPlugin frame;

  public ScriptRunner(Simulation simulation, Cooja gui) {
    this.gui = gui;
    this.simulation = simulation;
    this.engine = null;

    if (!Cooja.isVisualized()) {
      frame = null;
      codeEditor = null;
      logTextArea = null;
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

    /* Example scripts */
    final JMenu examplesMenu = new JMenu("Load example script");
    for (int i=0; i < EXAMPLE_SCRIPTS.length; i += 2) {
      final String file = EXAMPLE_SCRIPTS[i];
      JMenuItem exampleItem = new JMenuItem(EXAMPLE_SCRIPTS[i+1]);
      exampleItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          String script = loadScript(file);
          if (script == null) {
            JOptionPane.showMessageDialog(Cooja.getTopParentContainer(),
                "Could not load example script: scripts/" + file,
                "Could not load script", JOptionPane.ERROR_MESSAGE);
            return;
          }
          updateScript(script);
        }
      });
      examplesMenu.add(exampleItem);
    }
    fileMenu.add(examplesMenu);

    /* Script area */
    frame.setLayout(new BorderLayout());
    codeEditor = new JEditorPane();
    codeEditor.setContentType("text/javascript");
    if (codeEditor.getEditorKit() instanceof DefaultSyntaxKit) {
      DefaultSyntaxKit kit = (DefaultSyntaxKit) codeEditor.getEditorKit();
      kit.setProperty(DefaultSyntaxKit.CONFIG_MENU, "copy-to-clipboard,-,find,find-next,goto-line,-,linkfile");
      kit.setProperty("Action.linkfile", JSyntaxLinkFile.class.getName());
      kit.install(codeEditor);
    }

    logTextArea = new JTextArea(12,50);
    logTextArea.setMargin(new Insets(5,5,5,5));
    logTextArea.setEditable(true);
    logTextArea.setCursor(null);

    final JCheckBoxMenuItem activateMenuItem = new JCheckBoxMenuItem("Activate");
    activateMenuItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent ev) {
        try {
          setScriptActive(!isActive());
        } catch (Exception e) {
          logger.fatal("Error: " + e.getMessage(), e);
        }
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
        activateMenuItem.setSelected(isActive());
        examplesMenu.setEnabled(!isActive());
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

    JPopupMenu p = codeEditor.getComponentPopupMenu();
    if (p != null) {
      for (Component c: p.getComponents()) {
        if (!(c instanceof JMenuItem)) {
          continue;
        }
        Action a = ((JMenuItem) c).getAction();
        if (a instanceof JSyntaxLinkFile) {
          actionLinkFile = (JSyntaxLinkFile) a;
          actionLinkFile.setMenuText("Link script to disk file");
          actionLinkFile.putValue("ScriptRunner", this);
        }
      }
    }

    centerPanel.setOneTouchExpandable(true);
    centerPanel.setResizeWeight(0.5);

    JPanel buttonPanel = new JPanel(new BorderLayout());
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(BorderLayout.EAST, buttonPanel);

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
    String script = loadScript(EXAMPLE_SCRIPTS[0]);
    if (script != null) {
      updateScript(script);
    }
  }

  @Override
  public JInternalFrame getCooja() {
    return frame;
  }

  @Override
  public void startPlugin() {
  }

  public void setLinkFile(File source) {
    linkedFile = source;
    String script = source == null ? "" : StringUtils.loadFromFile(linkedFile);
    if (script != null) {
      updateScript(script);
    }
    if (!Cooja.isVisualized()) {
      return;
    }

    if (source == null) {
      if (actionLinkFile != null) {
        actionLinkFile.setMenuText("Link script to disk file");
        actionLinkFile.putValue("JavascriptSource", null);
      }
      codeEditor.setEditable(true);
    } else {
      Cooja.setExternalToolsSetting("SCRIPTRUNNER_LAST_SCRIPTFILE", source.getAbsolutePath());
      if (actionLinkFile != null) {
        actionLinkFile.setMenuText("Unlink script: " + source.getName());
        actionLinkFile.putValue("JavascriptSource", source);
      }
      codeEditor.setEditable(false);
    }
    updateTitle();
  }

  public void setScriptActive(boolean active) {
    // Always clean up the resources for the currently running script.
    if (engine != null) {
      /* Deactivate script */
      engine.deactivateScript();
      engine.setScriptLogObserver(null);
      engine = null;
    }

    if (logWriter != null) {
      try {
        logWriter.write(
                "Test ended at simulation time: " +
                        (simulation!=null?simulation.getSimulationTime():"?") + "\n");
        logWriter.flush();
        logWriter.close();
      } catch (IOException e) {
      }
      logWriter = null;
    }

    if (Cooja.isVisualized()) {
      if (actionLinkFile != null) {
        actionLinkFile.setEnabled(true);
      }
      codeEditor.setEnabled(true);
      updateTitle();
    }

    // Previous script deactivated at this point.
    if (!active) {
      return;
    }

    // Activate new script.
    if (linkedFile != null) {
      String script = StringUtils.loadFromFile(linkedFile);
      if (script == null) {
        logger.fatal("Failed to load script from: " + linkedFile.getAbsolutePath());
        return;
      }
      updateScript(script);
    }

    /* Create new engine */
    engine = new LogScriptEngine(simulation);
    if (Cooja.isVisualized()) {
      /* Attach visualized log observer */
      engine.setScriptLogObserver(new Observer() {
        @Override
        public void update(Observable obs, Object obj) {
          logTextArea.append((String) obj);
          logTextArea.setCaretPosition(logTextArea.getText().length());
        }
      });
    } else {
      try {
        /* Continuously write test output to file */
        if (logWriter == null) {
          /* Warning: static variable, used by all active test editor plugins */
          Path logDirPath = Path.of(gui.logDirectory);
          if (!Files.exists(logDirPath)) {
            Files.createDirectory(logDirPath);
          }
          var logFile = Paths.get(gui.logDirectory, "COOJA.testlog");
          logWriter = Files.newBufferedWriter(logFile, UTF_8, WRITE, CREATE, TRUNCATE_EXISTING);
          logWriter.write("Random seed: " + simulation.getRandomSeed() + "\n");
          logWriter.flush();
        }
        engine.setScriptLogObserver(new Observer() {
          @Override
          public void update(Observable obs, Object obj) {
            try {
              if (logWriter != null) {
                logWriter.write((String) obj);
                logWriter.flush();
              } else {
                logger.fatal("No log writer: " + obj);
              }
            } catch (IOException e) {
              logger.fatal("Error when writing to test log file: " + obj, e);
            }
          }
        });
      } catch (Exception e) {
        logger.fatal("Create log writer error: ", e);
        setScriptActive(false);
        return;
      }
    }

    /* Activate engine */
    boolean activated;
    try {
      activated = engine.activateScript(Cooja.isVisualized() ? codeEditor.getText() : headlessScript);
    } catch (RuntimeException | ScriptException e) {
      logger.fatal("Test script error: ", e);
      setScriptActive(false);
      if (Cooja.isVisualized()) {
        Cooja.showErrorDialog(Cooja.getTopParentContainer(),
                "Script error", e, false);
      }
      return;
    }
    if (activated && Cooja.isVisualized()) {
      if (actionLinkFile != null) {
        actionLinkFile.setEnabled(false);
      }
      logTextArea.setText("");
      codeEditor.setEnabled(false);
      updateTitle();
    }
  }

  private void updateTitle() {
    String title = "Simulation script editor ";
    if (linkedFile != null) {
      title += "(" + linkedFile.getName() + ") ";
    }
    if (isActive()) {
      title += "*active*";
    }
    frame.setTitle(title);
  }

  private void updateScript(String script) {
    if (script == null) {
      return;
    }

    if (Cooja.isVisualized()) {
      codeEditor.setText(script);
      logTextArea.setText("");
    } else {
      headlessScript = script;
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
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

    if (isActive()) {
      Element element = new Element("active");
      element.setText(String.valueOf(true));
      config.add(element);
    }

    return config;
  }

  public boolean isActive() {
    return engine != null;
  }

  @Override
  public void closePlugin() {
    try {
      setScriptActive(false);
    } catch (Exception e) {
    }
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    boolean activate = false;
    for (Element element : configXML) {
      String name = element.getName();
      if ("script".equals(name)) {
        if (!element.getText().isEmpty()) {
          updateScript(element.getText());
        }
      } else if ("scriptfile".equals(name)) {
        File file = simulation.getCooja().restorePortablePath(new File(element.getText().trim()));
        setLinkFile(file);
      } else if ("active".equals(name)) {
        activate = Boolean.parseBoolean(element.getText());
      }
    }

    // Automatically activate script in headless mode.
    if (activate || !Cooja.isVisualized()) {
      try {
        setScriptActive(true);
      } catch (Exception e) {
        logger.fatal("Error: failed to start script: {}", e.getMessage(), e);
        return false;
      }
    }

    return true;
  }

  private static String loadScript(String file) {
    return StringUtils.loadFromStream(ScriptRunner.class.getResourceAsStream("/scripts/" + file));
  }

  public static class JSyntaxLinkFile extends DefaultSyntaxAction {
    public JSyntaxLinkFile() {
      super("linkfile");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      JMenuItem menuItem = (JMenuItem) e.getSource();
      Action action = menuItem.getAction();
      ScriptRunner scriptRunner = (ScriptRunner) action.getValue("ScriptRunner");
      File currentSource = (File) action.getValue("JavascriptSource");

      if (currentSource != null) {
        scriptRunner.setLinkFile(null);
        return;
      }

      JFileChooser fileChooser = new JFileChooser();
      String suggest = Cooja.getExternalToolsSetting("SCRIPTRUNNER_LAST_SCRIPTFILE", null);
      if (suggest != null) {
        fileChooser.setSelectedFile(new File(suggest));
      } else {
        fileChooser.setCurrentDirectory(new java.io.File("."));
      }
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fileChooser.setDialogTitle("Select script file");
      fileChooser.setFileFilter(new FileFilter() {
        @Override
        public boolean accept(File file) {
          if (file.isDirectory()) { return true; }
          return file.getName().endsWith(".js");
        }
        @Override
        public String getDescription() {
          return "Javascript";
        }
      });
      if (fileChooser.showOpenDialog(scriptRunner.frame) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      scriptRunner.setLinkFile(fileChooser.getSelectedFile());
    }
  }
}
