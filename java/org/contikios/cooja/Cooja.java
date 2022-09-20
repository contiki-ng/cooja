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

package org.contikios.cooja;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultDesktopManager;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.MoteType.MoteTypeCreationException;
import org.contikios.cooja.VisPlugin.PluginRequiresVisualizationException;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.dialogs.AddMoteDialog;
import org.contikios.cooja.dialogs.BufferSettings;
import org.contikios.cooja.dialogs.CreateSimDialog;
import org.contikios.cooja.dialogs.ExternalToolsDialog;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.dialogs.ProjectDirectoriesDialog;
import org.contikios.cooja.motes.DisturberMoteType;
import org.contikios.cooja.motes.ImportAppMoteType;
import org.contikios.cooja.mspmote.SkyMoteType;
import org.contikios.cooja.mspmote.Z1MoteType;
import org.contikios.cooja.mspmote.plugins.MspCLI;
import org.contikios.cooja.mspmote.plugins.MspCodeWatcher;
import org.contikios.cooja.mspmote.plugins.MspCycleWatcher;
import org.contikios.cooja.mspmote.plugins.MspStackWatcher;
import org.contikios.cooja.plugins.BaseRSSIconf;
import org.contikios.cooja.plugins.BufferListener;
import org.contikios.cooja.plugins.DGRMConfigurator;
import org.contikios.cooja.plugins.EventListener;
import org.contikios.cooja.plugins.LogListener;
import org.contikios.cooja.plugins.MoteInformation;
import org.contikios.cooja.plugins.MoteInterfaceViewer;
import org.contikios.cooja.plugins.MoteTypeInformation;
import org.contikios.cooja.plugins.Notes;
import org.contikios.cooja.plugins.PowerTracker;
import org.contikios.cooja.plugins.RadioLogger;
import org.contikios.cooja.plugins.ScriptRunner;
import org.contikios.cooja.plugins.SimControl;
import org.contikios.cooja.plugins.SimInformation;
import org.contikios.cooja.plugins.TimeLine;
import org.contikios.cooja.plugins.VariableWatcher;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.positioners.EllipsePositioner;
import org.contikios.cooja.positioners.LinearPositioner;
import org.contikios.cooja.positioners.ManualPositioner;
import org.contikios.cooja.positioners.RandomPositioner;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;
import org.contikios.cooja.radiomediums.LogisticLoss;
import org.contikios.cooja.radiomediums.SilentRadioMedium;
import org.contikios.cooja.radiomediums.UDGM;
import org.contikios.cooja.radiomediums.UDGMConstantLoss;
import org.contikios.cooja.serialsocket.SerialSocketClient;
import org.contikios.cooja.serialsocket.SerialSocketServer;
import org.contikios.cooja.util.ScnObservable;
import org.contikios.mrm.MRM;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Main file of COOJA Simulator. Typically, contains a visualizer for the
 * simulator, but can also be started without visualizer.
 * <p>
 * This class loads external Java classes (in extension directories), and handles the
 * COOJA plugins as well as the configuration system. If provides a number of
 * help methods for the rest of the COOJA system, and is the starting point for
 * loading and saving simulation configs.
 *
 * @author Fredrik Osterlind
 */
public class Cooja extends Observable {
  /**
   * Version of Cooja.
   */
  public static final String VERSION = "4.8";

  /**
   *  Version used to detect incompatibility with the Contiki-NG
   *  build system. The format is &lt;YYYY&gt;&lt;MM&gt;&lt;DD&gt;&lt;2 digit sequence number&gt;.
   */
  public static final String CONTIKI_NG_BUILD_VERSION = "2022052601";

  private static JFrame frame = null;
  private static final Logger logger = LogManager.getLogger(Cooja.class);

  public static File externalToolsUserSettingsFile = null;
  private static boolean externalToolsUserSettingsFileReadOnly = false;

  private static String specifiedCoojaPath = null;
  private static String specifiedContikiPath = null;

  /**
   * Default extension configuration filename.
   */
  public static final String PROJECT_DEFAULT_CONFIG_FILENAME = "/cooja_default.config";

  /**
   * User extension configuration filename.
   */
  public static final String PROJECT_CONFIG_FILENAME = "cooja.config";

  // External tools setting names
  public static Properties defaultExternalToolsSettings;
  public static Properties currentExternalToolsSettings;

  /**
   * The name of the directory to output logs to.
   */
  public final String logDirectory;

  private static final String[] externalToolsSettingNames = new String[] {
    "PATH_COOJA",
    "PATH_CONTIKI", "PATH_APPS",
    "PATH_APPSEARCH",

    "PATH_MAKE",
    "PATH_C_COMPILER", "COMPILER_ARGS",
    "PATH_JAVAC",

    "DEFAULT_PROJECTDIRS",

    "PARSE_WITH_COMMAND",

    "MAPFILE_DATA_START", "MAPFILE_DATA_SIZE",
    "MAPFILE_BSS_START", "MAPFILE_BSS_SIZE",
    "MAPFILE_COMMON_START", "MAPFILE_COMMON_SIZE",
    "MAPFILE_VAR_NAME",
    "MAPFILE_VAR_ADDRESS_1", "MAPFILE_VAR_ADDRESS_2",
    "MAPFILE_VAR_SIZE_1", "MAPFILE_VAR_SIZE_2",

    "PARSE_COMMAND",
    "COMMAND_VAR_NAME_ADDRESS_SIZE",
    "COMMAND_DATA_START", "COMMAND_DATA_END",
    "COMMAND_BSS_START", "COMMAND_BSS_END",
    "COMMAND_COMMON_START", "COMMAND_COMMON_END",

    "HIDE_WARNINGS"
  };

  private static final int FRAME_NEW_OFFSET = 30;

  private static final String WINDOW_TITLE = "Cooja: The Contiki Network Simulator";

  private final Cooja cooja;

  private Simulation mySimulation;

  private final JMenu menuMoteTypeClasses;
  private final JMenu menuMoteTypes;

  private boolean hasFileHistoryChanged;

  private final ArrayList<Class<? extends Plugin>> menuMotePluginClasses = new ArrayList<>();

  private final JDesktopPane myDesktopPane;

  private final ArrayList<Plugin> startedPlugins = new ArrayList<>();

  private final ArrayList<GUIAction> guiActions = new ArrayList<>();

  // Platform configuration variables
  // Maintained via method reparseProjectConfig()
  private ProjectConfig projectConfig;

  private final ArrayList<COOJAProject> currentProjects = new ArrayList<>();

  private ClassLoader projectDirClassLoader;

  private final ArrayList<Class<? extends MoteType>> moteTypeClasses = new ArrayList<>();

  private final ArrayList<Class<? extends Plugin>> pluginClasses = new ArrayList<>();

  private final ArrayList<Class<? extends RadioMedium>> radioMediumClasses = new ArrayList<>();

  private final ArrayList<Class<? extends Positioner>> positionerClasses = new ArrayList<>();


  private final ScnObservable moteHighlightObservable;

  private final ScnObservable moteRelationObservable;
  private final JTextPane quickHelpTextPane;

  /**
   * Mote relation (directed).
   */
  public static class MoteRelation {
    public final Mote source;
    public final Mote dest;
    public final Color color;
    public MoteRelation(Mote source, Mote dest, Color color) {
      this.source = source;
      this.dest = dest;
      this.color = color;
    }
  }
  private final ArrayList<MoteRelation> moteRelations = new ArrayList<>();

  /**
   * Creates a new Cooja Simulator GUI and ensures Swing initialization is done in the right thread.
   *
   * @param logDirectory Directory for log files
   * @param vis          True if running in visual mode
   */
  public static Cooja makeCooja(final String logDirectory, final boolean vis) {
    if (vis) {
      assert !java.awt.EventQueue.isDispatchThread() : "Call from regular context";
      return new RunnableInEDT<Cooja>() {
        @Override
        public Cooja work() {
          return new Cooja(logDirectory, vis);
        }
      }.invokeAndWait();
    }

    return new Cooja(logDirectory, vis);
  }

  /**
   * Internal constructor for Cooja.
   *
   * @param logDirectory Directory for log files
   * @param vis          True if running in visual mode
   */
  private Cooja(String logDirectory, boolean vis) {
    cooja = this;
    this.logDirectory = logDirectory;
    mySimulation = null;
    // Load default and overwrite with user settings (if any).
    loadExternalToolsDefaultSettings();
    loadExternalToolsUserSettings();

    // Shutdown hook to close running simulations.
    Runtime.getRuntime().addShutdownHook(new ShutdownHandler(this));

    // Register default extension directories.
    String defaultProjectDirs = getExternalToolsSetting("DEFAULT_PROJECTDIRS", null);
    if (defaultProjectDirs != null && defaultProjectDirs.length() > 0) {
      String[] arr = defaultProjectDirs.split(";");
      for (String p : arr) {
        File projectDir = restorePortablePath(new File(p));
        currentProjects.add(new COOJAProject(projectDir));
      }
    }

    // Scan for projects.
    String searchProjectDirs = getExternalToolsSetting("PATH_APPSEARCH", null);
    if (searchProjectDirs != null && searchProjectDirs.length() > 0) {
      String[] arr = searchProjectDirs.split(";");
      for (String d : arr) {
        File searchDir = restorePortablePath(new File(d));
        File[] projects = COOJAProject.searchProjects(searchDir, 3);
        if(projects == null) continue;
        for(File p : projects){
          currentProjects.add(new COOJAProject(p));
        }
      }
    }

    if (!vis) {
      myDesktopPane = null;
      quickHelpTextPane = null;
      menuMoteTypeClasses = null;
      menuMoteTypes = null;
      moteHighlightObservable = null;
      moteRelationObservable = null;
      try {
        parseProjectConfig();
      } catch (ParseProjectsException e) {
        logger.fatal("Error when loading extensions: " + e.getMessage(), e);
      }
      return;
    }

    // Visualization enabled past this point.
    moteHighlightObservable = new ScnObservable();
    moteRelationObservable = new ScnObservable();
    myDesktopPane = new JDesktopPane() {
      @Override
      public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        updateDesktopSize(this);
      }
      @Override
      public void remove(Component c) {
        super.remove(c);
        updateDesktopSize(this);
      }
      @Override
      public Component add(Component comp) {
        Component c = super.add(comp);
        updateDesktopSize(this);
        return c;
      }
    };
    myDesktopPane.setDesktopManager(new DefaultDesktopManager() {
      @Override
      public void endResizingFrame(JComponent f) {
        super.endResizingFrame(f);
        updateDesktopSize(myDesktopPane);
      }
      @Override
      public void endDraggingFrame(JComponent f) {
        super.endDraggingFrame(f);
        updateDesktopSize(myDesktopPane);
      }
    });
    myDesktopPane.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
    frame = new JFrame(WINDOW_TITLE);

    /* Help panel */
    quickHelpTextPane = new JTextPane();
    quickHelpTextPane.setContentType("text/html");
    quickHelpTextPane.setEditable(false);
    quickHelpTextPane.setVisible(false);

    // Print a warning when repainting outside EDT with: gradlew run -Dcooja.debug.repaint=true
    if (System.getProperty("debug.repaint") != null) {
      RepaintManager.setCurrentManager(new RepaintManager() {
        public void addDirtyRegion(JComponent comp, int a, int b, int c, int d) {
          if (!java.awt.EventQueue.isDispatchThread()) {
            // Log to console so the thread name is printed along with the complete backtrace.
            logger.warn("Repainting outside EDT", new IllegalStateException("Repainting outside EDT"));
          }
          super.addDirtyRegion(comp, a, b, c, d);
        }
      });
    }

    /* Parse current extension configuration */
    try {
      parseProjectConfig();
    } catch (ParseProjectsException e) {
      logger.fatal("Error when loading extensions: " + e.getMessage(), e);
      JOptionPane.showMessageDialog(Cooja.getTopParentContainer(),
              "All Cooja extensions could not load.\n\n" +
                      "To manage Cooja extensions:\n" +
                      "Menu->Settings->Cooja extensions",
              "Reconfigure Cooja extensions", JOptionPane.INFORMATION_MESSAGE);
      showErrorDialog(frame, "Cooja extensions load error", e, false);
    }

    // Start all standard GUI plugins
    for (Class<? extends Plugin> pluginClass : pluginClasses) {
      int pluginType = pluginClass.getAnnotation(PluginType.class).value();
      if (pluginType == PluginType.COOJA_STANDARD_PLUGIN) {
        tryStartPlugin(pluginClass, this, null, null);
      }
    }

    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    // Menu bar.
    menuMoteTypeClasses = new JMenu("Create new mote type");
    menuMoteTypeClasses.setMnemonic(KeyEvent.VK_C);
    menuMoteTypes = new JMenu("Add motes");
    menuMoteTypes.setMnemonic(KeyEvent.VK_A);
    var container = new JPanel(new BorderLayout());
    frame.setJMenuBar(createMenuBar(container));

    // Scrollable desktop.
    myDesktopPane.setOpaque(true);
    frame.setContentPane(container);

    frame.setSize(700, 700);
    frame.setLocationRelativeTo(null);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        doQuit(true);
      }
    });
    frame.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateDesktopSize(getDesktopPane());
      }
    });

    int framePosX = Integer.parseInt(getExternalToolsSetting("FRAME_POS_X", "0"));
    int framePosY = Integer.parseInt(getExternalToolsSetting("FRAME_POS_Y", "0"));
    int frameWidth = Integer.parseInt(getExternalToolsSetting("FRAME_WIDTH", "0"));
    int frameHeight = Integer.parseInt(getExternalToolsSetting("FRAME_HEIGHT", "0"));
    String frameScreen = getExternalToolsSetting("FRAME_SCREEN", "");

    // Restore position to the same graphics device.
    GraphicsDevice device = null;
    for (var gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      if (gd.getIDstring().equals(frameScreen)) {
        device = gd;
        break;
      }
    }

    // Restore frame size and position.
    if (device != null) {
      if (frameWidth == Integer.MAX_VALUE && frameHeight == Integer.MAX_VALUE) {
        frame.setLocation(device.getDefaultConfiguration().getBounds().getLocation());
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
      } else if (frameWidth > 0 && frameHeight > 0) {
        // Ensure Cooja is visible on screen.
        boolean intersects =
                device.getDefaultConfiguration().getBounds().intersects(
                        new Rectangle(framePosX, framePosY, frameWidth, frameHeight));
        if (intersects) {
          frame.setLocation(framePosX, framePosY);
          frame.setSize(frameWidth, frameHeight);
        }
      }
    }
    frame.setVisible(true);
  }


  /**
   * Add mote highlight observer.
   *
   * @see #deleteMoteHighlightObserver(Observer)
   * @param newObserver
   *          New observer
   */
  public void addMoteHighlightObserver(Observer newObserver) {
    if (moteHighlightObservable != null) {
      moteHighlightObservable.addObserver(newObserver);
    }
  }

  /**
   * Delete mote highlight observer.
   *
   * @see #addMoteHighlightObserver(Observer)
   * @param observer
   *          Observer to delete
   */
  public void deleteMoteHighlightObserver(Observer observer) {
    if (moteHighlightObservable != null) {
      moteHighlightObservable.deleteObserver(observer);
    }
  }

  /**
   * @return True if simulator is visualized
   */
  public static boolean isVisualized() {
    return frame != null;
  }

  public static JFrame getTopParentContainer() {
    return frame;
  }

  private static File getLastOpenedFile() {
    // Fetch current history
    String[] historyArray = getExternalToolsSetting("SIMCFG_HISTORY", "").split(";");
    return historyArray.length > 0 ? new File(historyArray[0]) : null;
  }

  private static File[] getFileHistory() {
    // Fetch current history
    String[] historyArray = getExternalToolsSetting("SIMCFG_HISTORY", "").split(";");
    File[] history = new File[historyArray.length];
    for (int i = 0; i < historyArray.length; i++) {
      history[i] = new File(historyArray[i]);
    }
    return history;
  }

  /** Adds a file first to the file history. Method avoids adding duplicates. */
  private void addToFileHistory(File file) {
    // Fetch current history
    String[] history = getExternalToolsSetting("SIMCFG_HISTORY", "").split(";");
    String newFile;
    try {
      newFile = file.getCanonicalPath();
    } catch (IOException e) {
      return;
    }
    if (history.length > 0 && history[0].equals(newFile)) {
      // File already added
      return;
    }
    // Create new history
    StringBuilder newHistory = new StringBuilder();
    newHistory.append(newFile);
    for (int i = 0, count = 1; i < history.length && count < 10; i++) {
      String historyFile = history[i];
      if (!newFile.equals(historyFile) && historyFile.length() != 0) {
        newHistory.append(';').append(historyFile);
        count++;
      }
    }
    setExternalToolsSetting("SIMCFG_HISTORY", newHistory.toString());
    saveExternalToolsUserSettings();
    hasFileHistoryChanged = true;
  }

  private void populateMenuWithHistory(JMenu menu, final boolean quick, File[] openFilesHistory) {
    JMenuItem lastItem;
    int index = 0;
    for (File file: openFilesHistory) {
      if (index < 10) {
        char mnemonic = (char) ('0' + (++index % 10));
        lastItem = new JMenuItem(mnemonic + " " + file.getName());
        lastItem.setMnemonic(mnemonic);
      } else {
        lastItem = new JMenuItem(file.getName());
      }
      final File f = file;
      lastItem.addActionListener(e -> doLoadConfigAsync(quick, f));
      lastItem.putClientProperty("file", file);
      lastItem.setToolTipText(file.getAbsolutePath());
      menu.add(lastItem);
    }
  }

  /** Opens a file chooser if the file cannot be read. */
  private static File validateFileOrSelectNew(File file) {
    if (file != null && file.canRead()) {
      return file;
    }
    final File suggestedFile = file;
    return new RunnableInEDT<File>() {
      @Override
      public File work() {
        JFileChooser fc = newFileChooser();

        if (suggestedFile != null && suggestedFile.isDirectory()) {
          fc.setCurrentDirectory(suggestedFile);
        } else {
          // Suggest file using file history.
          File suggestedFile = getLastOpenedFile();
          if (suggestedFile != null) {
            fc.setSelectedFile(suggestedFile);
          }
        }

        if (fc.showOpenDialog(Cooja.getTopParentContainer()) != JFileChooser.APPROVE_OPTION) {
          return null;
        }

        File file = fc.getSelectedFile();
        if (!file.exists()) {  // Try default file extension.
          file = new File(file.getParent(), file.getName() + fc.getFileFilter());
        }
        if (!file.exists() || !file.canRead()) {
          logger.fatal("No read access to file");
          return null;
        }
        return file;
      }
    }.invokeAndWait();
  }

  /**
   * Load a simulation configuration file from disk asynchronously.
   *
   * @param quick Quick-load simulation
   * @param file Configuration file to load, if null a dialog will appear
   */
  private void doLoadConfigAsync(final boolean quick, File file) {
    // Warn about memory usage.
    if (warnMemory()) {
      return;
    }

    final var cfgFile = validateFileOrSelectNew(file);
    if (cfgFile == null) return;

    new Thread(() -> cooja.doLoadConfig(cfgFile, quick, false, null), "asyncld").start();
  }

  /**
   * Enables/disables menus and menu items depending on whether a simulation is loaded etc.
   */
  void updateGUIComponentState() {
    if (!isVisualized()) {
      return;
    }

    /* Update action state */
    for (GUIAction a: guiActions) {
      a.setEnabled(a.shouldBeEnabled());
    }

    // Mote and mote type menus.
    menuMoteTypeClasses.setEnabled(getSimulation() != null);
    menuMoteTypes.setEnabled(getSimulation() != null);
  }

  private JMenuBar createMenuBar(JPanel desktop) {
    final var newSimulationAction = new GUIAction("New simulation...", KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!cooja.doRemoveSimulation(true)) {
          return;
        }

        var sim = new Simulation(cooja);
        if (CreateSimDialog.showDialog(Cooja.getTopParentContainer(), sim)) {
          // Start GUI plugins.
          for (var pluginClass : pluginClasses) {
            int type = pluginClass.getAnnotation(PluginType.class).value();
            if (type == PluginType.SIM_STANDARD_PLUGIN) {
              tryStartPlugin(pluginClass, cooja, sim, null);
            }
          }
          cooja.setSimulation(sim);
        }
      }
      @Override
      public boolean shouldBeEnabled() {
        return true;
      }
    };
    final var closeSimulationAction = new GUIAction("Close simulation", KeyEvent.VK_C) {
      @Override
      public void actionPerformed(ActionEvent e) {
        cooja.doRemoveSimulation(true);
      }
      @Override
      public boolean shouldBeEnabled() {
        return getSimulation() != null;
      }
    };
    final var reloadSimulationAction = new GUIAction("Reload with same random seed", KeyEvent.VK_K, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (getSimulation() == null) {
          // Reload last opened simulation.
          final File file = getLastOpenedFile();
          cooja.doLoadConfigAsync(true, file);
          return;
        }
        reloadCurrentSimulation();
      }
      @Override
      public boolean shouldBeEnabled() {
        return true;
      }
    };
    final var reloadRandomSimulationAction = new GUIAction("Reload with new random seed", KeyEvent.VK_N, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (getSimulation() != null) {
          getSimulation().setRandomSeed(getSimulation().getRandomSeed()+1);
          reloadCurrentSimulation();
        }
      }
      @Override
      public boolean shouldBeEnabled() {
        return getSimulation() != null;
      }
    };
    final var saveSimulationAction = new GUIAction("Save simulation as...", KeyEvent.VK_S) {
      @Override
      public void actionPerformed(ActionEvent e) {
        cooja.doSaveConfig(true);
      }
      @Override
      public boolean shouldBeEnabled() {
        return getSimulation() != null;
      }
    };
    final var exitCoojaAction = new GUIAction("Exit", 'x') {
      @Override
      public void actionPerformed(ActionEvent e) {
        cooja.doQuit(true);
      }
      @Override
      public boolean shouldBeEnabled() {
        return true;
      }
    };
    final var startStopSimulationAction = new GUIAction("Start simulation", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Start/Stop current simulation.
        Simulation s = getSimulation();
        if (s == null) {
          return;
        }
        if (s.isRunning()) {
          s.stopSimulation();
        } else {
          s.startSimulation();
        }
      }
      @Override
      public void setEnabled(boolean newValue) {
        if (getSimulation() == null) {
          putValue(NAME, "Start simulation");
        } else if (getSimulation().isRunning()) {
          putValue(NAME, "Pause simulation");
        } else {
          putValue(NAME, "Start simulation");
        }
        super.setEnabled(newValue);
      }
      @Override
      public boolean shouldBeEnabled() {
        return getSimulation() != null && getSimulation().isRunnable();
      }
    };
    final var removeAllMotesAction = new GUIAction("Remove all motes") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Simulation s = getSimulation();
        s.stopSimulation();

        while (s.getMotesCount() > 0) {
          s.removeMote(getSimulation().getMote(0));
        }
      }
      @Override
      public boolean shouldBeEnabled() {
        Simulation s = getSimulation();
        return s != null && s.getMotesCount() > 0;
      }
    };
    final var showBufferSettingsAction = new GUIAction("Buffer sizes...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySimulation == null) {
          return;
        }
        BufferSettings.showDialog(myDesktopPane, mySimulation);
      }
      @Override
      public boolean shouldBeEnabled() {
        return mySimulation != null;
      }
    };

    JMenuItem menuItem;

    /* Prepare GUI actions */
    guiActions.add(newSimulationAction);
    guiActions.add(closeSimulationAction);
    guiActions.add(reloadSimulationAction);
    guiActions.add(reloadRandomSimulationAction);
    guiActions.add(saveSimulationAction);
    /*    guiActions.add(closePluginsAction);*/
    guiActions.add(exitCoojaAction);
    guiActions.add(startStopSimulationAction);
    guiActions.add(removeAllMotesAction);
    guiActions.add(showBufferSettingsAction);

    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    JMenu simulationMenu = new JMenu("Simulation");
    JMenu motesMenu = new JMenu("Motes");
    final JMenu toolsMenu = new JMenu("Tools");
    JMenu settingsMenu = new JMenu("Settings");
    JMenu helpMenu = new JMenu("Help");
    var menuOpenSimulation = new JMenu("Open simulation");
    menuOpenSimulation.setMnemonic(KeyEvent.VK_O);

    menuBar.add(fileMenu);
    menuBar.add(simulationMenu);
    menuBar.add(motesMenu);
    menuBar.add(toolsMenu);
    menuBar.add(settingsMenu);
    menuBar.add(helpMenu);

    fileMenu.setMnemonic(KeyEvent.VK_F);
    simulationMenu.setMnemonic(KeyEvent.VK_S);
    motesMenu.setMnemonic(KeyEvent.VK_M);
    toolsMenu.setMnemonic(KeyEvent.VK_T);
    helpMenu.setMnemonic(KeyEvent.VK_H);

    /* File menu */
    fileMenu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        updateGUIComponentState();
        if (!hasFileHistoryChanged) {
          return;
        }
        hasFileHistoryChanged = false;

        // Reconfigure submenu.
        menuOpenSimulation.removeAll();
        JMenu reconfigureMenu = new JMenu("Open and Reconfigure");
        JMenuItem browseItem2 = new JMenuItem("Browse...");
        browseItem2.addActionListener(e1 -> doLoadConfigAsync(false, null));
        reconfigureMenu.add(browseItem2);
        reconfigureMenu.add(new JSeparator());
        File[] openFilesHistory = getFileHistory();
        populateMenuWithHistory(reconfigureMenu, false, openFilesHistory);

        // Open menu.
        JMenuItem browseItem = new JMenuItem("Browse...");
        browseItem.addActionListener(e1 -> doLoadConfigAsync(true, null));
        menuOpenSimulation.add(browseItem);
        menuOpenSimulation.add(new JSeparator());
        menuOpenSimulation.add(reconfigureMenu);
        menuOpenSimulation.add(new JSeparator());
        populateMenuWithHistory(menuOpenSimulation, true, openFilesHistory);
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    fileMenu.add(new JMenuItem(newSimulationAction));
    fileMenu.add(menuOpenSimulation);
    fileMenu.add(new JMenuItem(closeSimulationAction));

    hasFileHistoryChanged = true;

    fileMenu.add(new JMenuItem(saveSimulationAction));

    /*    menu.addSeparator();*/

    /*    menu.add(new JMenuItem(closePluginsAction));*/

    fileMenu.addSeparator();

    fileMenu.add(new JMenuItem(exitCoojaAction));

    /* Simulation menu */
    simulationMenu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        updateGUIComponentState();
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }
      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    simulationMenu.add(new JMenuItem(startStopSimulationAction));

    JMenuItem reloadSimulationMenuItem = new JMenu("Reload simulation");
    reloadSimulationMenuItem.add(new JMenuItem(reloadSimulationAction));
    reloadSimulationMenuItem.add(new JMenuItem(reloadRandomSimulationAction));
    simulationMenu.add(reloadSimulationMenuItem);

    GUIAction guiAction = new StartPluginGUIAction("Control panel...");
    menuItem = new JMenuItem(guiAction);
    guiActions.add(guiAction);
    menuItem.setMnemonic(KeyEvent.VK_C);
    menuItem.putClientProperty("class", SimControl.class);
    simulationMenu.add(menuItem);

    guiAction = new StartPluginGUIAction("Information...");
    menuItem = new JMenuItem(guiAction);
    guiActions.add(guiAction);
    menuItem.setMnemonic(KeyEvent.VK_I);
    menuItem.putClientProperty("class", SimInformation.class);
    simulationMenu.add(menuItem);

    // Mote type menu
    motesMenu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        updateGUIComponentState();
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }
      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });
    final var guiEventHandler = new GUIEventHandler();
    // Mote type classes sub menu
    menuMoteTypeClasses.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        // Clear menu
        menuMoteTypeClasses.removeAll();

        // Recreate menu items
        JMenuItem menuItem;

        for (Class<? extends MoteType> moteTypeClass : moteTypeClasses) {
          /* Sort mote types according to abstraction level */
          String abstractionLevelDescription = Cooja.getAbstractionLevelDescriptionOf(moteTypeClass);
          if(abstractionLevelDescription == null) {
            abstractionLevelDescription = "[unknown cross-level]";
          }

          /* Check if abstraction description already exists */
          JSeparator abstractionLevelSeparator = null;
          for (Component component: menuMoteTypeClasses.getMenuComponents()) {
            if (!(component instanceof JSeparator)) {
              continue;
            }
            JSeparator existing = (JSeparator) component;
            if (abstractionLevelDescription.equals(existing.getToolTipText())) {
              abstractionLevelSeparator = existing;
              break;
            }
          }
          if (abstractionLevelSeparator == null) {
            abstractionLevelSeparator = new JSeparator();
            abstractionLevelSeparator.setToolTipText(abstractionLevelDescription);
            menuMoteTypeClasses.add(abstractionLevelSeparator);
          }

          String description = Cooja.getDescriptionOf(moteTypeClass);
          menuItem = new JMenuItem(description + "...");
          menuItem.setActionCommand("create mote type");
          menuItem.putClientProperty("class", moteTypeClass);
        /*  menuItem.setToolTipText(abstractionLevelDescription);*/
          menuItem.addActionListener(guiEventHandler);

          /* Add new item directly after cross level separator */
          for (int i=0; i < menuMoteTypeClasses.getMenuComponentCount(); i++) {
            if (menuMoteTypeClasses.getMenuComponent(i) == abstractionLevelSeparator) {
              menuMoteTypeClasses.add(menuItem, i+1);
              break;
            }
          }
        }
      }

      @Override
      public void menuDeselected(MenuEvent e) {
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });




    // Mote menu
    motesMenu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        updateGUIComponentState();
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }
      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });


    // Mote types sub menu
    menuMoteTypes.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        // Clear menu
        menuMoteTypes.removeAll();



        if (mySimulation != null) {

          // Recreate menu items
          JMenuItem menuItem;

          for (MoteType moteType : mySimulation.getMoteTypes()) {
            menuItem = new JMenuItem(moteType.getDescription());
            menuItem.setActionCommand("add motes");
            menuItem.setToolTipText(getDescriptionOf(moteType.getClass()));
            menuItem.putClientProperty("motetype", moteType);
            menuItem.addActionListener(guiEventHandler);
            menuMoteTypes.add(menuItem);
          }

          if(mySimulation.getMoteTypes().length > 0) {
            menuMoteTypes.add(new JSeparator());
          }
        }


        menuMoteTypes.add(menuMoteTypeClasses);
      }

      @Override
      public void menuDeselected(MenuEvent e) {
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });
    motesMenu.add(menuMoteTypes);

    guiAction = new StartPluginGUIAction("Mote types...");
    menuItem = new JMenuItem(guiAction);
    guiActions.add(guiAction);
    menuItem.putClientProperty("class", MoteTypeInformation.class);

    motesMenu.add(menuItem);

    motesMenu.add(new JMenuItem(removeAllMotesAction));

    /* Tools menu */
    toolsMenu.addMenuListener(new MenuListener() {
      private final ActionListener menuItemListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Object pluginClass = ((JMenuItem)e.getSource()).getClientProperty("class");
          Object mote = ((JMenuItem)e.getSource()).getClientProperty("mote");
          tryStartPlugin((Class<? extends Plugin>) pluginClass, cooja, getSimulation(), (Mote)mote);
        }
      };
      private JMenuItem createMenuItem(Class<? extends Plugin> newPluginClass) {
        String description = getDescriptionOf(newPluginClass);
        JMenuItem menuItem = new JMenuItem(description + "...");
        menuItem.putClientProperty("class", newPluginClass);
        menuItem.addActionListener(menuItemListener);
        // Only enable items when there is a simulation, otherwise the user gets a dialog with a backtrace.
        menuItem.setEnabled(getSimulation() != null);
        return menuItem;
      }

      @Override
      public void menuSelected(MenuEvent e) {
        /* Populate tools menu */
        toolsMenu.removeAll();

        /* Cooja plugins */
        boolean hasCoojaPlugins = false;
        for (Class<? extends Plugin> pluginClass: pluginClasses) {
          int pluginType = pluginClass.getAnnotation(PluginType.class).value();
          if (pluginType != PluginType.COOJA_PLUGIN && pluginType != PluginType.COOJA_STANDARD_PLUGIN) {
            continue;
          }
          toolsMenu.add(createMenuItem(pluginClass));
          hasCoojaPlugins = true;
        }

        /* Simulation plugins */
        boolean hasSimPlugins = false;
        for (Class<? extends Plugin> pluginClass: pluginClasses) {
          if (pluginClass.equals(SimControl.class)) {
            continue; /* ignore */
          }
          if (pluginClass.equals(SimInformation.class)) {
            continue; /* ignore */
          }
          if (pluginClass.equals(MoteTypeInformation.class)) {
            continue; /* ignore */
          }

          int pluginType = pluginClass.getAnnotation(PluginType.class).value();
          if (pluginType != PluginType.SIM_PLUGIN && pluginType != PluginType.SIM_STANDARD_PLUGIN
        		  && pluginType != PluginType.SIM_CONTROL_PLUGIN) {
            continue;
          }

          if (hasCoojaPlugins) {
            hasCoojaPlugins = false;
            toolsMenu.addSeparator();
          }

          toolsMenu.add(createMenuItem(pluginClass));
          hasSimPlugins = true;
        }

        for (Class<? extends Plugin> pluginClass: pluginClasses) {
          int pluginType = pluginClass.getAnnotation(PluginType.class).value();
          if (pluginType != PluginType.MOTE_PLUGIN) {
            continue;
          }

          if (hasSimPlugins) {
            hasSimPlugins = false;
            toolsMenu.addSeparator();
          }

          toolsMenu.add(createMotePluginsSubmenu(pluginClass));
        }
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }
      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    // Settings menu
    settingsMenu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        updateGUIComponentState();
      }
      @Override
      public void menuDeselected(MenuEvent e) {
      }
      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });

    menuItem = new JMenuItem("External tools paths...");
    menuItem.setActionCommand("edit paths");
    menuItem.addActionListener(guiEventHandler);
    settingsMenu.add(menuItem);

    menuItem = new JMenuItem("Cooja extensions...");
    menuItem.setActionCommand("manage extensions");
    menuItem.addActionListener(guiEventHandler);
    settingsMenu.add(menuItem);

    settingsMenu.add(new JMenuItem(showBufferSettingsAction));

    /* Help */
    var quickHelpScroll = new JScrollPane(quickHelpTextPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    quickHelpScroll.setPreferredSize(new Dimension(200, 0));
    quickHelpScroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(0, 3, 0, 0)
    ));
    quickHelpScroll.setVisible(false);
    var scroll = new JScrollPane(myDesktopPane);
    scroll.setBorder(null);
    desktop.add(BorderLayout.CENTER, scroll);
    desktop.add(BorderLayout.EAST, quickHelpScroll);
    var showQuickHelpAction = new GUIAction("Quick help", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)) {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!(e.getSource() instanceof JCheckBoxMenuItem)) {
          return;
        }
        boolean show = ((JCheckBoxMenuItem) e.getSource()).isSelected();
        quickHelpTextPane.setVisible(show);
        quickHelpScroll.setVisible(show);
        setExternalToolsSetting("SHOW_QUICKHELP", Boolean.toString(show));
        frame.getContentPane().revalidate();
        updateDesktopSize(getDesktopPane());
      }

      @Override
      public boolean shouldBeEnabled() {
        return true;
      }
    };
    loadQuickHelp("GETTING_STARTED");
    final boolean showQuickhelp = getExternalToolsSetting("SHOW_QUICKHELP", "true").equalsIgnoreCase("true");
    if (showQuickhelp) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          JCheckBoxMenuItem checkBox = ((JCheckBoxMenuItem)showQuickHelpAction.getValue("checkbox"));
          if (checkBox == null) {
            return;
          }
          if (checkBox.isSelected()) {
            return;
          }
          checkBox.doClick();
        }
      });
    }
    final var showGettingStartedAction = new GUIAction("Getting started") {
      @Override
      public void actionPerformed(ActionEvent e) {
        loadQuickHelp("GETTING_STARTED");
        var checkBox = ((JCheckBoxMenuItem)showQuickHelpAction.getValue("checkbox"));
        if (checkBox == null || checkBox.isSelected()) {
          return;
        }
        checkBox.doClick();
      }

      @Override
      public boolean shouldBeEnabled() {
        return true;
      }
    };
    helpMenu.add(new JMenuItem(showGettingStartedAction));
    final var showKeyboardShortcutsAction = new GUIAction("Keyboard shortcuts") {
      @Override
      public void actionPerformed(ActionEvent e) {
        loadQuickHelp("KEYBOARD_SHORTCUTS");
        var checkBox = ((JCheckBoxMenuItem)showQuickHelpAction.getValue("checkbox"));
        if (checkBox == null || checkBox.isSelected()) {
          return;
        }
        checkBox.doClick();
      }

      @Override
      public boolean shouldBeEnabled() {
        return true;
      }
    };
    helpMenu.add(new JMenuItem(showKeyboardShortcutsAction));
    JCheckBoxMenuItem checkBox = new JCheckBoxMenuItem(showQuickHelpAction);
    showQuickHelpAction.putValue("checkbox", checkBox);
    helpMenu.add(checkBox);

    helpMenu.addSeparator();

    menuItem = new JMenuItem("Java version: "
        + System.getProperty("java.version") + " ("
        + System.getProperty("java.vendor") + ")");
    menuItem.setEnabled(false);
    helpMenu.add(menuItem);
    menuItem = new JMenuItem("System \"os.arch\": "
        + System.getProperty("os.arch"));
    menuItem.setEnabled(false);
    helpMenu.add(menuItem);
    menuItem = new JMenuItem("System \"sun.arch.data.model\": "
        + System.getProperty("sun.arch.data.model"));
    menuItem.setEnabled(false);
    helpMenu.add(menuItem);

    return menuBar;
  }

  /**
   * @return Current desktop pane (simulator visualizer)
   */
  public JDesktopPane getDesktopPane() {
    return myDesktopPane;
  }

  private static void setLookAndFeel() throws InterruptedException, InvocationTargetException {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      JFrame.setDefaultLookAndFeelDecorated(true);
      JDialog.setDefaultLookAndFeelDecorated(true);

      ToolTipManager.sharedInstance().setDismissDelay(60000);

      /* Nimbus */
      try {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("linux")) {
          try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
              if ("Nimbus".equals(info.getName())) {
                UIManager.setLookAndFeel(info.getClassName());
                break;
              }
            }
          } catch (UnsupportedLookAndFeelException e) {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
          }
        } else {
          UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        }
        return;
      } catch (Exception e) {
      }

      /* System */
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e) {
      }
    });
  }

  private static void updateDesktopSize(final JDesktopPane desktop) {
    if (desktop == null || !desktop.isVisible() || desktop.getParent() == null) {
      return;
    }

    Rectangle rect = desktop.getVisibleRect();
    Dimension pref = new Dimension(rect.width - 1, rect.height - 1);
    for (JInternalFrame frame : desktop.getAllFrames()) {
      if (pref.width < frame.getX() + frame.getWidth() - 20) {
        pref.width = frame.getX() + frame.getWidth();
      }
      if (pref.height < frame.getY() + frame.getHeight() - 20) {
        pref.height = frame.getY() + frame.getHeight();
      }
    }
    desktop.setPreferredSize(pref);
    desktop.revalidate();
  }

  //// PROJECT CONFIG AND EXTENDABLE PARTS METHODS ////

  /**
   * Register new mote type class.
   *
   * @param moteTypeClass
   *          Class to register
   */
  public void registerMoteType(Class<? extends MoteType> moteTypeClass) {
    moteTypeClasses.add(moteTypeClass);
  }

  /**
   * @return All registered mote type classes
   */
  public List<Class<? extends MoteType>> getRegisteredMoteTypes() {
    return moteTypeClasses;
  }

  /**
   * Returns all registered plugins.
   */
  public List<Class<? extends Plugin>> getRegisteredPlugins() {
    return pluginClasses;
  }

  /**
   * Register new positioner class.
   *
   * @param positionerClass
   *          Class to register
   * @return True if class was registered
   */
  public boolean registerPositioner(Class<? extends Positioner> positionerClass) {
    positionerClasses.add(positionerClass);
    return true;
  }

  /**
   * @return All registered positioner classes
   */
  public List<Class<? extends Positioner>> getRegisteredPositioners() {
    return positionerClasses;
  }

  /**
   * Register new radio medium class.
   *
   * @param radioMediumClass
   *          Class to register
   * @return True if class was registered
   */
  public boolean registerRadioMedium(Class<? extends RadioMedium> radioMediumClass) {
    radioMediumClasses.add(radioMediumClass);
    return true;
  }

  /**
   * @return All registered radio medium classes
   */
  public List<Class<? extends RadioMedium>> getRegisteredRadioMediums() {
    return radioMediumClasses;
  }

  /**
   * Builds new extension configuration using current extension directories settings.
   * Re-registers mote types, plugins, positioners and radio mediums.
   */
  public void reparseProjectConfig() throws ParseProjectsException {
    /* Remove current dependencies */
    moteTypeClasses.clear();
    menuMotePluginClasses.clear();
    pluginClasses.clear();
    positionerClasses.clear();
    radioMediumClasses.clear();
    projectDirClassLoader = null;
    parseProjectConfig();
  }

  private void parseProjectConfig() throws ParseProjectsException {
    /* Build cooja configuration */
    try {
      projectConfig = new ProjectConfig(true);
    } catch (FileNotFoundException e) {
      logger.fatal("Could not find default extension config file: " + PROJECT_DEFAULT_CONFIG_FILENAME);
      throw new ParseProjectsException(
          "Could not find default extension config file: " + PROJECT_DEFAULT_CONFIG_FILENAME, e);
    } catch (IOException e) {
      logger.fatal("Error when reading default extension config file: " + PROJECT_DEFAULT_CONFIG_FILENAME);
      throw new ParseProjectsException(
          "Error when reading default extension config file: " + PROJECT_DEFAULT_CONFIG_FILENAME, e);
    }
    for (COOJAProject project: currentProjects) {
      try {
        projectConfig.appendProjectDir(project.dir);
      } catch (FileNotFoundException e) {
        throw new ParseProjectsException("Error when loading extension: " + e.getMessage(), e);
      } catch (IOException e) {
        throw new ParseProjectsException("Error when reading extension config: " + e.getMessage(), e);
      }
    }

    // Register mote types
    registerMoteType(ImportAppMoteType.class);
    registerMoteType(DisturberMoteType.class);
    registerMoteType(ContikiMoteType.class);
    registerMoteType(SkyMoteType.class);
    registerMoteType(Z1MoteType.class);
    String[] moteTypeClassNames = projectConfig.getStringArrayValue(Cooja.class,
    "MOTETYPES");
    if (moteTypeClassNames != null) {
      for (String moteTypeClassName : moteTypeClassNames) {
        if (moteTypeClassName.trim().isEmpty()) {
          continue;
        }
        Class<? extends MoteType> moteTypeClass = tryLoadClass(this,
            MoteType.class, moteTypeClassName);

        if (moteTypeClass != null) {
          registerMoteType(moteTypeClass);
        } else {
          logger.warn("Could not load mote type class: " + moteTypeClassName);
        }
      }
    }

    // Register plugins
    registerPlugin(SimControl.class);
    registerPlugin(SimInformation.class);
    registerPlugin(MoteTypeInformation.class);
    registerPlugin(Visualizer.class);
    registerPlugin(LogListener.class);
    registerPlugin(TimeLine.class);
    registerPlugin(MoteInformation.class);
    registerPlugin(MoteInterfaceViewer.class);
    registerPlugin(VariableWatcher.class);
    registerPlugin(EventListener.class);
    registerPlugin(RadioLogger.class);
    registerPlugin(ScriptRunner.class);
    registerPlugin(Notes.class);
    registerPlugin(BufferListener.class);
    registerPlugin(DGRMConfigurator.class);
    registerPlugin(BaseRSSIconf.class);
    registerPlugin(PowerTracker.class);
    registerPlugin(SerialSocketClient.class);
    registerPlugin(SerialSocketServer.class);
    registerPlugin(MspCLI.class);
    registerPlugin(MspCodeWatcher.class);
    registerPlugin(MspStackWatcher.class);
    registerPlugin(MspCycleWatcher.class);
    String[] pluginClassNames = projectConfig.getStringArrayValue(Cooja.class,
    "PLUGINS");
    if (pluginClassNames != null) {
      for (String pluginClassName : pluginClassNames) {
        Class<? extends Plugin> pluginClass = tryLoadClass(this, Plugin.class,
            pluginClassName);

        if (pluginClass != null) {
          registerPlugin(pluginClass);
        } else {
          logger.warn("Could not load plugin class: " + pluginClassName);
        }
      }
    }

    // Register positioners
    registerPositioner(RandomPositioner.class);
    registerPositioner(LinearPositioner.class);
    registerPositioner(EllipsePositioner.class);
    registerPositioner(ManualPositioner.class);
    String[] positionerClassNames = projectConfig.getStringArrayValue(
        Cooja.class, "POSITIONERS");
    if (positionerClassNames != null) {
      for (String positionerClassName : positionerClassNames) {
        Class<? extends Positioner> positionerClass = tryLoadClass(this,
            Positioner.class, positionerClassName);

        if (positionerClass != null) {
          registerPositioner(positionerClass);
        } else {
          logger
          .warn("Could not load positioner class: " + positionerClassName);
        }
      }
    }

    // Register radio mediums.
    registerRadioMedium(UDGM.class);
    registerRadioMedium(UDGMConstantLoss.class);
    registerRadioMedium(DirectedGraphMedium.class);
    registerRadioMedium(SilentRadioMedium.class);
    registerRadioMedium(LogisticLoss.class);
    registerRadioMedium(MRM.class);
    String[] radioMediumsClassNames = projectConfig.getStringArrayValue(
        Cooja.class, "RADIOMEDIUMS");
    if (radioMediumsClassNames != null) {
      for (String radioMediumClassName : radioMediumsClassNames) {
        Class<? extends RadioMedium> radioMediumClass = tryLoadClass(this,
            RadioMedium.class, radioMediumClassName);

        if (radioMediumClass != null) {
          registerRadioMedium(radioMediumClass);
        } else {
          logger.warn("Could not load radio medium class: "
              + radioMediumClassName);
        }
      }
    }
  }

  /**
   * Allocates and returns the project classloader.
   *
   * @return Project classloader
   * @throws ParseProjectsException when failing to create the classloader
   */
  public ClassLoader getProjectClassLoader() throws ParseProjectsException {
    if (projectDirClassLoader == null) {
      try {
        projectDirClassLoader = createClassLoader(ClassLoader.getSystemClassLoader(), currentProjects);
      } catch (ClassLoaderCreationException e) {
        throw new ParseProjectsException("Error when creating class loader", e);
      }
    }
    return projectDirClassLoader;
  }

  /**
   * Returns the current extension configuration common to the entire simulator.
   *
   * @return Current extension configuration
   */
  public ProjectConfig getProjectConfig() {
    return projectConfig;
  }

  /**
   * Returns the current extension directories common to the entire simulator.
   *
   * @return Current extension directories.
   */
  public COOJAProject[] getProjects() {
    return currentProjects.toArray(new COOJAProject[0]);
  }

  // // PLUGIN METHODS ////

  /**
   * Show a started plugin in working area.
   *
   * @param plugin Plugin
   */
  public void showPlugin(final Plugin plugin) {
    new RunnableInEDT<Boolean>() {
      private static final int FRAME_STANDARD_WIDTH = 150;

      private static final int FRAME_STANDARD_HEIGHT = 300;

      @Override
      public Boolean work() {
        JInternalFrame pluginFrame = plugin.getCooja();
        if (pluginFrame == null) {
          logger.fatal("Failed trying to show plugin without visualizer.");
          return false;
        }

        myDesktopPane.add(pluginFrame);

        /* Set size if not already specified by plugin */
        if (pluginFrame.getWidth() <= 0 || pluginFrame.getHeight() <= 0) {
          pluginFrame.setSize(FRAME_STANDARD_WIDTH, FRAME_STANDARD_HEIGHT);
        }

        /* Set location if not already set */
        if (pluginFrame.getLocation().x <= 0 && pluginFrame.getLocation().y <= 0) {
          JInternalFrame[] iframes = myDesktopPane.getAllFrames();
          Point topFrameLoc = iframes.length > 1
                  ? iframes[1].getLocation()
                  :  new Point(myDesktopPane.getSize().width / 2, myDesktopPane.getSize().height / 2);
          pluginFrame.setLocation(new Point(
                  topFrameLoc.x + FRAME_NEW_OFFSET,
                  topFrameLoc.y + FRAME_NEW_OFFSET));
        }

        pluginFrame.setVisible(true);

        /* Select plugin */
        try {
          for (JInternalFrame existingPlugin : myDesktopPane.getAllFrames()) {
            existingPlugin.setSelected(false);
          }
          pluginFrame.setSelected(true);
        } catch (Exception e) { }
        myDesktopPane.moveToFront(pluginFrame);

        return true;
      }
    }.invokeAndWait();
  }

  /**
   * Close all mote plugins for given mote.
   *
   * @param mote Mote
   */
  public void closeMotePlugins(Mote mote) {
    for (Plugin p: startedPlugins.toArray(new Plugin[0])) {
      if (!(p instanceof MotePlugin)) {
        continue;
      }

      Mote pluginMote = ((MotePlugin)p).getMote();
      if (pluginMote == mote) {
        removePlugin(p, false);
      }
    }
  }

  /**
   * Remove a plugin from working area.
   *
   * @param plugin
   *          Plugin to remove
   * @param askUser
   *          If plugin is the last one, ask user if we should remove current
   *          simulation also?
   */
  public void removePlugin(final Plugin plugin, final boolean askUser) {
    plugin.closePlugin();
    startedPlugins.remove(plugin);

    if (isVisualized()) {
      new RunnableInEDT<Boolean>() {
        @Override
        public Boolean work() {
          updateGUIComponentState();

          // Dispose visualized components.
          if (plugin.getCooja() != null) {
            plugin.getCooja().dispose();
          }

          return true;
        }
      }.invokeAndWait();
    }
    // Remove simulation if requested (and all plugins are closed).
    if (askUser && getSimulation() != null && startedPlugins.isEmpty()) {
      doRemoveSimulation(true);
    }
  }

  /**
   * Same as the {@link #startPlugin(Class, Cooja, Simulation, Mote, Element)} method,
   * but does not throw exceptions. If COOJA is visualised, an error dialog
   * is shown if plugin could not be started.
   *
   * @see #startPlugin(Class, Cooja, Simulation, Mote, Element)
   * @param pluginClass Plugin class
   * @param argGUI Plugin GUI argument
   * @param argSimulation Plugin simulation argument
   * @param argMote Plugin mote argument
   * @param root XML root element for plugin config
   * @return Started plugin
   */
  private Plugin tryStartPlugin(final Class<? extends Plugin> pluginClass,
     final Cooja argGUI, final Simulation argSimulation, final Mote argMote, Element root) {
    try {
      return startPlugin(pluginClass, argGUI, argSimulation, argMote, root);
    } catch (PluginConstructionException ex) {
      if (Cooja.isVisualized()) {
        Cooja.showErrorDialog(Cooja.getTopParentContainer(), "Error when starting plugin", ex, false);
      } else {
        /* If the plugin requires visualization, inform user */
        Throwable cause = ex;
        do {
          if (cause instanceof PluginRequiresVisualizationException) {
            logger.debug("Visualized plugin was not started: " + pluginClass);
            return null;
          }
        } while ((cause = cause.getCause()) != null);

        logger.fatal("Error when starting plugin", ex);
      }
    }
    return null;
  }

  public Plugin tryStartPlugin(final Class<? extends Plugin> pluginClass,
      final Cooja argGUI, final Simulation argSimulation, final Mote argMote) {
    return tryStartPlugin(pluginClass, argGUI, argSimulation, argMote, null);
  }

  /**
   * Starts given plugin. If visualized, the plugin is also shown.
   *
   * @see PluginType
   * @param pluginClass Plugin class
   * @param argGUI Plugin GUI argument
   * @param argSimulation Plugin simulation argument
   * @param argMote Plugin mote argument
   * @param root XML root element for plugin config
   * @return Started plugin
   * @throws PluginConstructionException At errors
   */
  private Plugin startPlugin(final Class<? extends Plugin> pluginClass,
      final Cooja argGUI, final Simulation argSimulation, final Mote argMote, Element root)
  throws PluginConstructionException
  {
    // Check that plugin class is registered
    if (!pluginClasses.contains(pluginClass)) {
      throw new PluginConstructionException("Tool class not registered: " + pluginClass);
    }

    // Construct plugin depending on plugin type
    int pluginType = pluginClass.getAnnotation(PluginType.class).value();
    Plugin plugin;

    try {
      if (!isVisualized() && VisPlugin.class.isAssignableFrom(pluginClass)) {
        throw new PluginRequiresVisualizationException();
      }

      if (pluginType == PluginType.MOTE_PLUGIN) {
        if (argGUI == null) {
          throw new PluginConstructionException("No GUI argument for mote plugin");
        }
        if (argSimulation == null) {
          throw new PluginConstructionException("No simulation argument for mote plugin");
        }
        if (argMote == null) {
          throw new PluginConstructionException("No mote argument for mote plugin");
        }

        plugin =
          pluginClass.getConstructor(Mote.class, Simulation.class, Cooja.class)
          .newInstance(argMote, argSimulation, argGUI);

      } else if (pluginType == PluginType.SIM_PLUGIN || pluginType == PluginType.SIM_STANDARD_PLUGIN
    		  || pluginType == PluginType.SIM_CONTROL_PLUGIN) {
        if (argGUI == null) {
          throw new PluginConstructionException("No GUI argument for simulation plugin");
        }
        if (argSimulation == null) {
          throw new PluginConstructionException("No simulation argument for simulation plugin");
        }

        plugin =
          pluginClass.getConstructor(Simulation.class, Cooja.class)
          .newInstance(argSimulation, argGUI);

      } else if (pluginType == PluginType.COOJA_PLUGIN
          || pluginType == PluginType.COOJA_STANDARD_PLUGIN) {
        if (argGUI == null) {
          throw new PluginConstructionException("No GUI argument for GUI plugin");
        }

        plugin = pluginClass.getConstructor(Cooja.class).newInstance(argGUI);

      } else {
        throw new PluginConstructionException("Bad plugin type: " + pluginType);
      }
    } catch (PluginRequiresVisualizationException e) {
      throw new PluginConstructionException("Tool class requires visualization: " + pluginClass.getName(), e);
    } catch (Exception e) {
      throw new PluginConstructionException("Construction error for tool of class: " + pluginClass.getName(), e);
    }

    if (root != null) {
      for (var cfg : root.getChildren("plugin_config")) {
        plugin.setConfigXML(((Element)cfg).getChildren(), isVisualized());
      }
    }

    plugin.startPlugin();

    // Add to active plugins list
    startedPlugins.add(plugin);
    updateGUIComponentState();

    // Show plugin if visualizer type
    if (plugin.getCooja() != null) {
      // If plugin is visualizer plugin, parse visualization arguments
      new RunnableInEDT<Boolean>() {
        @Override
        public Boolean work() {
          if (root != null) {
            var size = new Dimension(100, 100);
            var location = new Point(100, 100);
            for (var cfgElem : (List<Element>) root.getChildren()) {
              if (cfgElem.getName().equals("width")) {
                size.width = Integer.parseInt(cfgElem.getText());
                plugin.getCooja().setSize(size);
              } else if (cfgElem.getName().equals("height")) {
                size.height = Integer.parseInt(cfgElem.getText());
                plugin.getCooja().setSize(size);
              } else if (cfgElem.getName().equals("z")) {
                int zOrder = Integer.parseInt(cfgElem.getText());
                plugin.getCooja().putClientProperty("zorder", zOrder);
              } else if (cfgElem.getName().equals("location_x")) {
                location.x = Integer.parseInt(cfgElem.getText());
                plugin.getCooja().setLocation(location);
              } else if (cfgElem.getName().equals("location_y")) {
                location.y = Integer.parseInt(cfgElem.getText());
                plugin.getCooja().setLocation(location);
              } else if (cfgElem.getName().equals("minimized")) {
                boolean minimized = Boolean.parseBoolean(cfgElem.getText());
                final var pluginGUI = plugin.getCooja();
                if (minimized && pluginGUI != null) {
                  SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      try {
                        pluginGUI.setIcon(true);
                      } catch (PropertyVetoException e) {
                      }
                    }
                  });
                }
              }
            }
          }

          showPlugin(plugin);
          return true;
        }
      }.invokeAndWait();
    }

    return plugin;
  }

  /**
   * Unregister a plugin class. Removes any plugin menu items links as well.
   *
   * @param pluginClass Plugin class
   */
  public void unregisterPlugin(Class<? extends Plugin> pluginClass) {
    pluginClasses.remove(pluginClass);
    menuMotePluginClasses.remove(pluginClass);
  }

  /**
   * Register a plugin to be included in the GUI.
   *
   * @param pluginClass New plugin to register
   * @return True if this plugin was registered ok, false otherwise
   */
  public boolean registerPlugin(final Class<? extends Plugin> pluginClass) {
    var annotation = pluginClass.getAnnotation(PluginType.class);
    if (annotation == null) {
      logger.fatal("Could not register plugin, no plugin type found: " + pluginClass);
      return false;
    }

    switch (annotation.value()) {
      case PluginType.MOTE_PLUGIN:
        menuMotePluginClasses.add(pluginClass);
      case PluginType.COOJA_PLUGIN:
      case PluginType.COOJA_STANDARD_PLUGIN:
      case PluginType.SIM_PLUGIN:
      case PluginType.SIM_STANDARD_PLUGIN:
      case PluginType.SIM_CONTROL_PLUGIN:
        pluginClasses.add(pluginClass);
        return true;
    }
    logger.fatal("Could not register plugin, " + pluginClass + " has unknown plugin type");
    return false;
  }

  /**
   * Returns started plugin that ends with given class name, if any.
   *
   * @param classname Class name
   * @return Plugin instance
   */
  public Plugin getPlugin(String classname) {
    for (Plugin p: startedPlugins) {
      if (p.getClass().getName().endsWith(classname)) {
        return p;
      }
    }
    return null;
  }

  public Plugin[] getStartedPlugins() {
    return startedPlugins.toArray(new Plugin[0]);
  }

  private static boolean isMotePluginCompatible(Class<? extends Plugin> motePluginClass, Mote mote) {
    var supportedArgs = motePluginClass.getAnnotation(SupportedArguments.class);
    if (supportedArgs == null) {
      return true;
    }

    /* Check mote interfaces */
    final var moteInterfaces = mote.getInterfaces();
    for (Class<? extends MoteInterface> requiredMoteInterface: supportedArgs.moteInterfaces()) {
      if (moteInterfaces.getInterfaceOfType(requiredMoteInterface) == null) {
        return false;
      }
    }

    /* Check mote type */
    final var clazz = mote.getClass();
    for (Class<? extends Mote> supportedMote: supportedArgs.motes()) {
      if (supportedMote.isAssignableFrom(clazz)) {
        return true;
      }
    }

    return false;
  }

  public JMenu createMotePluginsSubmenu(Class<? extends Plugin> pluginClass) {
    JMenu menu = new JMenu(getDescriptionOf(pluginClass));
    if (getSimulation() == null || getSimulation().getMotesCount() == 0) {
      menu.setEnabled(false);
      return menu;
    }

    ActionListener menuItemListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object pluginClass = ((JMenuItem)e.getSource()).getClientProperty("class");
        Object mote = ((JMenuItem)e.getSource()).getClientProperty("mote");
        tryStartPlugin((Class<? extends Plugin>) pluginClass, cooja, getSimulation(), (Mote)mote);
      }
    };

    final int MAX_PER_ROW = 30;
    final int MAX_COLUMNS = 5;

    int added = 0;
    for (Mote mote: getSimulation().getMotes()) {
      if (!isMotePluginCompatible(pluginClass, mote)) {
        continue;
      }

      JMenuItem menuItem = new JMenuItem(mote.toString() + "...");
      menuItem.putClientProperty("class", pluginClass);
      menuItem.putClientProperty("mote", mote);
      menuItem.addActionListener(menuItemListener);

      menu.add(menuItem);
      added++;

      if (added == MAX_PER_ROW) {
        menu.getPopupMenu().setLayout(new GridLayout(MAX_PER_ROW, MAX_COLUMNS));
      }
      if (added >= MAX_PER_ROW*MAX_COLUMNS) {
        break;
      }
    }
    if (added == 0) {
      menu.setEnabled(false);
    }

    return menu;
  }

  /**
   * Return a mote plugins submenu for given mote.
   *
   * @param mote Mote
   * @return Mote plugins menu
   */
  public JMenu createMotePluginsSubmenu(Mote mote) {
    JMenu menuMotePlugins = new JMenu("Mote tools for " + mote);

    for (Class<? extends Plugin> motePluginClass: menuMotePluginClasses) {
      if (!isMotePluginCompatible(motePluginClass, mote)) {
        continue;
      }

      GUIAction guiAction = new StartPluginGUIAction(getDescriptionOf(motePluginClass) + "...");
      JMenuItem menuItem = new JMenuItem(guiAction);
      menuItem.putClientProperty("class", motePluginClass);
      menuItem.putClientProperty("mote", mote);

      menuMotePlugins.add(menuItem);
    }
    return menuMotePlugins;
  }

  // // GUI CONTROL METHODS ////

  /**
   * @return Current simulation
   */
  public Simulation getSimulation() {
    return mySimulation;
  }

  private void setSimulation(Simulation sim) {
    mySimulation = sim;
    updateGUIComponentState();

    // Set frame title
    if (frame != null) {
      frame.setTitle(sim.getTitle() + " - " + WINDOW_TITLE);
    }

    setChanged();
    notifyObservers();
  }

  /**
   * Remove current simulation
   *
   * @param askForConfirmation
   *          Should we ask for confirmation if a simulation is already active?
   * @return True if no simulation exists when method returns
   */
  private boolean doRemoveSimulation(boolean askForConfirmation) {

    if (mySimulation == null) {
      return true;
    }

    if (askForConfirmation) {
      boolean ok = new RunnableInEDT<Boolean>() {
        @Override
        public Boolean work() {
          String s1 = "Remove";
          String s2 = "Cancel";
          Object[] options = { s1, s2 };
          int n = JOptionPane.showOptionDialog(Cooja.getTopParentContainer(),
              "You have an active simulation.\nDo you want to remove it?",
              "Remove current simulation?", JOptionPane.YES_NO_OPTION,
              JOptionPane.QUESTION_MESSAGE, null, options, s2);
          return n == JOptionPane.YES_OPTION;
        }
      }.invokeAndWait();

      if (!ok) {
        return false;
      }
    }

    // Close all started non-GUI plugins
    for (var startedPlugin : startedPlugins.toArray(new Plugin[0])) {
      int pluginType = startedPlugin.getClass().getAnnotation(PluginType.class).value();
      if (pluginType != PluginType.COOJA_PLUGIN && pluginType != PluginType.COOJA_STANDARD_PLUGIN) {
        removePlugin(startedPlugin, false);
      }
    }

    // Delete simulation
    mySimulation.deleteObservers();
    mySimulation.stopSimulation();
    mySimulation.removed();

    /* Clear current mote relations */
    MoteRelation[] relations = getMoteRelations();
    for (MoteRelation r: relations) {
      removeMoteRelation(r.source, r.dest);
    }

    mySimulation = null;
    updateGUIComponentState();

    // Reset frame title
    if (isVisualized()) {
      frame.setTitle(WINDOW_TITLE);
    }

    setChanged();
    notifyObservers();

    return true;
  }

  /**
   * Load a simulation configuration file from disk
   *
   * @param configFile Configuration file to load, reloads current sim if null
   * @param quick      Quick-load simulation
   * @param rewriteCsc Rewrite simulation config
   * @param manualRandomSeed The random seed to use for the simulation
   */
  private Simulation doLoadConfig(File configFile, final boolean quick, boolean rewriteCsc, Long manualRandomSeed) {
    final var autoStart = configFile == null && mySimulation.isRunning();
    if (configFile != null && !doRemoveSimulation(true)) {
      return null;
    }

    if (configFile != null) {
      addToFileHistory(configFile);
    }

    final JDialog progressDialog;
    if (quick) {
      final String progressTitle = "Loading " + (configFile == null ? "" : configFile.getAbsolutePath());

      progressDialog = new RunnableInEDT<JDialog>() {
        @Override
        public JDialog work() {
          final JDialog progressDialog = new JDialog(Cooja.getTopParentContainer(), progressTitle, ModalityType.APPLICATION_MODAL);

          JPanel progressPanel = new JPanel(new BorderLayout());
          var progressBar = new JProgressBar(0, 100);
          progressBar.setValue(0);
          progressBar.setIndeterminate(true);

          PROGRESS_BAR = progressBar; /* Allow various parts of COOJA to show messages */

          var button = new JButton("Abort");

          progressPanel.add(BorderLayout.CENTER, progressBar);
          progressPanel.add(BorderLayout.SOUTH, button);
          progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

          progressPanel.setVisible(true);

          progressDialog.getContentPane().add(progressPanel);
          progressDialog.setSize(400, 200);

          progressDialog.getRootPane().setDefaultButton(button);
          progressDialog.setLocationRelativeTo(Cooja.getTopParentContainer());
          progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
          return progressDialog;
        }
      }.invokeAndWait();
    } else {
      progressDialog = null;
    }

    final var cfgFile = configFile;
    // SwingWorker can pass information from worker to process() through publish().
    // Communicate information the other way through this shared queue.
    final var channel = new SynchronousQueue<Integer>(true);
    var worker = new SwingWorker<Simulation, SimulationCreationException>() {
      @Override
      public Simulation doInBackground() {
        Element root = new Element("simconf");
        if (cfgFile == null) {
          var simElem = new Element("simulation");
          simElem.addContent(getSimulation().getConfigXML());
          root.addContent(simElem);
          root.addContent(getPluginsConfigXML());
        }
        boolean shouldRetry;
        Simulation newSim = null;
        do {
          try {
            shouldRetry = false;
            PROGRESS_WARNINGS.clear();
            if (cfgFile == null) {
              cooja.doRemoveSimulation(false);
              newSim = createSimulation(root, quick, rewriteCsc, manualRandomSeed);
              setSimulation(newSim);
            } else {
              newSim = loadSimulationConfig(cfgFile, quick, rewriteCsc, manualRandomSeed);
            }
            if (newSim != null && autoStart) {
              newSim.startSimulation();
            }
          } catch (SimulationCreationException e) {
            publish(e);
            try {
              shouldRetry = channel.take() == 1;
            } catch (InterruptedException ex) {
              cooja.doRemoveSimulation(false);
              return null;
            }
          }
        } while (shouldRetry);
        return newSim;
      }

      @Override
      protected void process(List<SimulationCreationException> exs) {
        for (var e : exs) {
          var retry = showErrorDialog(Cooja.getTopParentContainer(), "Simulation load error", e, true);
          try {
            channel.put(retry ? 1 : 0);
          } catch (InterruptedException ex) {
            cancel(true);
            return;
          }
        }
      }

      @Override
      protected void done() {
        // Optionally show compilation warnings.
        var hideWarn = Boolean.parseBoolean(Cooja.getExternalToolsSetting("HIDE_WARNINGS", "false"));
        if (quick && !hideWarn && !PROGRESS_WARNINGS.isEmpty()) {
          showWarningsDialog(frame, PROGRESS_WARNINGS.toArray(new String[0]));
        }
        PROGRESS_WARNINGS.clear();
        if (progressDialog != null && progressDialog.isDisplayable()) {
          progressDialog.dispose();
        }
      }
    };

    if (progressDialog != null) {
      java.awt.EventQueue.invokeLater(() -> {
        progressDialog.getRootPane().getDefaultButton().addActionListener(e -> worker.cancel(true));
        progressDialog.setVisible(true);
      });
    }

    worker.execute();
    Simulation sim;
    try {
      sim = worker.get();
    } catch (CancellationException | ExecutionException | InterruptedException e) {
      cooja.doRemoveSimulation(false);
      return null;
    }

    return sim;
  }

  /**
   * Reload currently configured simulation.
   * Reloading a simulation may include recompiling Contiki.
   */
  public void reloadCurrentSimulation() {
    final Simulation sim = getSimulation();
    if (sim == null) {
      logger.fatal("No simulation to reload");
      return;
    }

    /* Warn about memory usage */
    if (warnMemory()) {
      return;
    }

    final long randomSeed = sim.getRandomSeed();
    new Thread(() -> cooja.doLoadConfig(null, true, false, randomSeed), "asyncld").start();
  }

  private static boolean warnMemory() {
    long max = Runtime.getRuntime().maxMemory();
    long used  = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    double memRatio = (double) used / (double) max;
    if (memRatio < 0.8) {
      return false;
    }

    DecimalFormat format = new DecimalFormat("0.000");
    logger.warn("Memory usage is getting critical. Reboot Cooja to avoid out of memory error. Current memory usage is " + format.format(100*memRatio) + "%.");
    if (isVisualized()) {
      int n = JOptionPane.showOptionDialog(
          Cooja.getTopParentContainer(),
          "Reboot Cooja to avoid out of memory error.\n" +
          "Current memory usage is " + format.format(100*memRatio) + "%.",
          "Out of memory warning",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.WARNING_MESSAGE, null,
          new String[] { "Continue", "Abort"}, "Abort");
      return n != JOptionPane.YES_OPTION;
    }

    return false;
  }

  /**
   * Save current simulation configuration to disk
   *
   * @param askForConfirmation
   *          Ask for confirmation before overwriting file
   */
  public File doSaveConfig(boolean askForConfirmation) {
    if (mySimulation == null) {
      return null;
    }

    mySimulation.stopSimulation();

    JFileChooser fc = newFileChooser();

    int returnVal = fc.showSaveDialog(myDesktopPane);
    if (returnVal == JFileChooser.APPROVE_OPTION) {
      File saveFile = fc.getSelectedFile();
      if (!fc.accept(saveFile)) {
        saveFile = new File(saveFile.getParent(), saveFile.getName() + fc.getFileFilter());
      }
      if (saveFile.exists()) {
        if (askForConfirmation) {
          String s1 = "Overwrite";
          String s2 = "Cancel";
          Object[] options = { s1, s2 };
          int n = JOptionPane.showOptionDialog(
              Cooja.getTopParentContainer(),
              "A file with the same name already exists.\nDo you want to remove it?",
              "Overwrite existing file?", JOptionPane.YES_NO_OPTION,
              JOptionPane.QUESTION_MESSAGE, null, options, s1);
          if (n != JOptionPane.YES_OPTION) {
            return null;
          }
        }
      }
      if (!saveFile.exists() || saveFile.canWrite()) {
        saveSimulationConfig(saveFile);
        addToFileHistory(saveFile);
        return saveFile;
      } else {
      	JOptionPane.showMessageDialog(
      			getTopParentContainer(), "No write access to " + saveFile, "Save failed",
      			JOptionPane.ERROR_MESSAGE);
        logger.fatal("No write access to file: " + saveFile.getAbsolutePath());
      }
    }
    return null;
  }

  /** Allocate a file chooser for Cooja simulation files. */
  private static JFileChooser newFileChooser() {
    JFileChooser fc = new JFileChooser();
    fc.setFileFilter(new FileFilter() {
      @Override
      public boolean accept(File file) {
        if (file.isDirectory()) {
          return true;
        }
        return file.getName().endsWith(".csc") || file.getName().endsWith(".csc.gz");
      }
      @Override
      public String getDescription() {
        return "Cooja simulation (.csc, .csc.gz)";
      }
      @Override
      public String toString() {
        return ".csc";
      }
    });

    // Suggest file using history
    File suggestedFile = getLastOpenedFile();
    if (suggestedFile != null) {
      fc.setSelectedFile(suggestedFile);
    }
    return fc;
  }

  /**
   * Quit program
   *
   * @param askForConfirmation Should we ask for confirmation before quitting?
   */
  public void doQuit(boolean askForConfirmation) {
    doQuit(askForConfirmation, 0);
  }
  
  public void doQuit(boolean askForConfirmation, int exitCode) {
    if (askForConfirmation) {
      if (getSimulation() != null) {
        /* Save? */
        String s1 = "Yes";
        String s2 = "No";
        String s3 = "Cancel";
        Object[] options = { s1, s2, s3 };
        int n = JOptionPane.showOptionDialog(Cooja.getTopParentContainer(),
            "Do you want to save the current simulation?",
            WINDOW_TITLE, JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE, null, options, s1);
        if (n == JOptionPane.YES_OPTION) {
          if (cooja.doSaveConfig(true) == null) {
            return;
          }
        } else if (n == JOptionPane.CANCEL_OPTION) {
          return;
        } else if (n != JOptionPane.NO_OPTION) {
          return;
        }
      }
    }

    if (getSimulation() != null) {
      doRemoveSimulation(false);
    }

    // Clean up resources
    for (var plugin : startedPlugins.toArray(new Plugin[0])) {
      removePlugin(plugin, false);
    }

    /* Store frame size and position */
    if (isVisualized()) {
      setExternalToolsSetting("FRAME_SCREEN", frame.getGraphicsConfiguration().getDevice().getIDstring());
      setExternalToolsSetting("FRAME_POS_X", String.valueOf(frame.getLocationOnScreen().x));
      setExternalToolsSetting("FRAME_POS_Y", String.valueOf(frame.getLocationOnScreen().y));

      if (frame.getExtendedState() == JFrame.MAXIMIZED_BOTH) {
        setExternalToolsSetting("FRAME_WIDTH", "" + Integer.MAX_VALUE);
        setExternalToolsSetting("FRAME_HEIGHT", "" + Integer.MAX_VALUE);
      } else {
        setExternalToolsSetting("FRAME_WIDTH", String.valueOf(frame.getWidth()));
        setExternalToolsSetting("FRAME_HEIGHT", String.valueOf(frame.getHeight()));
      }
      saveExternalToolsUserSettings();
    }
    System.exit(exitCode);
  }

    public static String resolvePathIdentifiers(String path) {
      for (String[] pair : PATH_IDENTIFIER) {
        if (path.contains(pair[0])) {
          String p = Cooja.getExternalToolsSetting(pair[1]);
          if (p != null) {
            path = path.replace(pair[0], p);
          } else {
            logger.warn("could not resolve path identifier " + pair[0]);
          }
        }
      }
        return path;
    }

  // // EXTERNAL TOOLS SETTINGS METHODS ////

  /**
   * @return Number of external tools settings
   */
  public static int getExternalToolsSettingsCount() {
    return externalToolsSettingNames.length;
  }

  /**
   * Get name of external tools setting at given index.
   *
   * @param index
   *          Setting index
   * @return Name
   */
  public static String getExternalToolsSettingName(int index) {
    return externalToolsSettingNames[index];
  }

  /**
   * @param name
   *          Name of setting
   * @return Value
   */
  public static String getExternalToolsSetting(String name) {
    return getExternalToolsSetting(name, null);
  }

  /**
   * @param name
   *          Name of setting
   * @param defaultValue
   *          Default value
   * @return Value
   */
  public static String getExternalToolsSetting(String name, String defaultValue) {
    if (specifiedContikiPath != null && "PATH_CONTIKI".equals(name)) {
      return specifiedContikiPath;
    }
    if (Cooja.specifiedCoojaPath != null && "PATH_COOJA".equals(name)) {
      return Cooja.specifiedCoojaPath;
    }
    return currentExternalToolsSettings.getProperty(name, defaultValue);
  }

  /**
   * @param name
   *          Name of setting
   * @param defaultValue
   *          Default value
   * @return Value
   */
  public static String getExternalToolsDefaultSetting(String name, String defaultValue) {
    return defaultExternalToolsSettings.getProperty(name, defaultValue);
  }

  /**
   * @param name
   *          Name of setting
   * @param newVal
   *          New value
   */
  public static void setExternalToolsSetting(String name, String newVal) {
    currentExternalToolsSettings.setProperty(name, newVal);
  }

  /**
   * Load external tools settings from default file.
   */
  public static void loadExternalToolsDefaultSettings() {
    final var toolsConfigFileName = "/external_tools.config";
    Properties settings = new Properties();
    try (var in = Cooja.class.getResourceAsStream(toolsConfigFileName)) {
      if (in == null) {
        throw new MissingResourceException(
                "Could not find " + toolsConfigFileName + ", jar file seems broken", "Cooja", "");
      }
      settings.load(in);
    } catch (IOException e) {
      throw new MissingResourceException(e.getMessage(), "Cooja", "");
    }

    final var toolsLinux64ConfigFileName = "/external_tools_linux_64.config";
    String osName = System.getProperty("os.name").toLowerCase();
    String filename;
    if (osName.startsWith("win")) {
      filename = "/external_tools_win32.config";
    } else if (osName.startsWith("mac os x")) {
      filename = "/external_tools_macosx.config";
    } else if (osName.startsWith("freebsd")) {
      filename = "/external_tools_freebsd.config";
    } else if (osName.startsWith("linux")) {
      filename = "/external_tools_linux.config";
      String osArch = System.getProperty("os.arch").toLowerCase();
      if (osArch.startsWith("amd64")) {
        filename = toolsLinux64ConfigFileName;
      }
    } else {
      filename = toolsLinux64ConfigFileName;
      logger.warn("Unknown system: " + osName + ", using default: " + filename);
    }

    try (var in = Cooja.class.getResourceAsStream(filename)) {
      if (in == null) {
        throw new MissingResourceException(
                "Could not find " + filename + ", jar file seems broken", "Cooja", "");
      }
      settings.load(in);

      currentExternalToolsSettings = settings;
      defaultExternalToolsSettings = (Properties) currentExternalToolsSettings.clone();
      logger.info("External tools default settings: " + filename);
    } catch (IOException e) {
      throw new MissingResourceException(e.getMessage(), "Cooja", "");
    }
  }

  /**
   * Load user values from external properties file
   */
  private static void loadExternalToolsUserSettings() {
    if (externalToolsUserSettingsFile == null || !externalToolsUserSettingsFile.exists()) {
      return;
    }

    try (var in = new FileInputStream(externalToolsUserSettingsFile)) {
      Properties settings = new Properties();
      settings.load(in);

      Enumeration<Object> en = settings.keys();
      while (en.hasMoreElements()) {
        String key = (String) en.nextElement();
        setExternalToolsSetting(key, settings.getProperty(key));
      }
      logger.info("External tools user settings: " + externalToolsUserSettingsFile);
    } catch (IOException e) {
      logger.warn("Error when reading user settings from: " + externalToolsUserSettingsFile);
    }
  }

  /**
   * Save external tools user settings to file.
   */
  public static void saveExternalToolsUserSettings() {
    if (externalToolsUserSettingsFileReadOnly || externalToolsUserSettingsFile == null) {
      return;
    }

    try (var out = new FileOutputStream(externalToolsUserSettingsFile)) {
      Properties differingSettings = new Properties();
      var keyEnum = currentExternalToolsSettings.keys();
      while (keyEnum.hasMoreElements()) {
        String key = (String) keyEnum.nextElement();
        String defaultSetting = getExternalToolsDefaultSetting(key, "");
        String currentSetting = currentExternalToolsSettings.getProperty(key, "");
        if (!defaultSetting.equals(currentSetting)) {
          differingSettings.setProperty(key, currentSetting);
        }
      }

      differingSettings.store(out, "Cooja External Tools (User specific)");
    } catch (FileNotFoundException ex) {
      // Could not open settings file for writing, aborting
      logger.warn("Could not save external tools user settings to "
          + externalToolsUserSettingsFile + ", aborting");
    } catch (IOException ex) {
      // Could not open settings file for writing, aborting
      logger.warn("Error while saving external tools user settings to "
          + externalToolsUserSettingsFile + ", aborting");
    }
  }

  // // GUI EVENT HANDLER ////

  private class GUIEventHandler implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      MoteType newMoteType = null;
      final var cmd = e.getActionCommand();
      if (cmd.equals("create mote type")) {
        // FIXME: Simulation should never be null if this action is enabled.
        if (cooja.mySimulation == null) {
          logger.fatal("Can't create mote type (no simulation)");
          return;
        }
        cooja.mySimulation.stopSimulation();

        // Create mote type
        var clazz = (Class<? extends MoteType>) ((JMenuItem) e.getSource()).getClientProperty("class");
        try {
          newMoteType = clazz == ContikiMoteType.class
                  ? new ContikiMoteType(cooja) : clazz.getDeclaredConstructor().newInstance();
          if (!newMoteType.configureAndInit(Cooja.getTopParentContainer(), cooja.mySimulation, isVisualized())) {
            return;
          }
          cooja.mySimulation.addMoteType(newMoteType);
        } catch (Exception e1) {
          logger.fatal("Exception when creating mote type", e1);
          showErrorDialog(getTopParentContainer(), "Mote type creation error", e1, false);
          newMoteType = null;
        }
      } else if (cmd.equals("add motes")) {
        // FIXME: Simulation should never be null if this action is enabled.
        if (cooja.mySimulation == null) {
          logger.warn("No simulation active");
          return;
        }
        cooja.mySimulation.stopSimulation();
        newMoteType = (MoteType) ((JMenuItem) e.getSource()).getClientProperty("motetype");
      } else if (cmd.equals("edit paths")) {
        ExternalToolsDialog.showDialog(Cooja.getTopParentContainer());
      } else if (cmd.equals("manage extensions")) {
        COOJAProject[] newProjects = ProjectDirectoriesDialog.showDialog(
            Cooja.getTopParentContainer(),
            Cooja.this,
            getProjects()
        );
        if (newProjects != null) {
        	currentProjects.clear();
          currentProjects.addAll(Arrays.asList(newProjects));
          try {
            reparseProjectConfig();
          } catch (ParseProjectsException ex) {
            logger.fatal("Error when loading extensions: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(Cooja.getTopParentContainer(),
                    "All Cooja extensions could not load.\n\n" +
                            "To manage Cooja extensions:\n" +
                            "Menu->Settings->Cooja extensions",
                    "Reconfigure Cooja extensions", JOptionPane.INFORMATION_MESSAGE);
            showErrorDialog(getTopParentContainer(), "Cooja extensions load error", ex, false);
          }
        }
      } else {
        logger.warn("Unhandled action: " + cmd);
      }
      if (newMoteType != null) {
        for (var mote : AddMoteDialog.showDialog(frame, cooja.mySimulation, newMoteType)) {
          cooja.mySimulation.addMote(mote);
        }
      }
    }
  }

  // // VARIOUS HELP METHODS ////

  /**
   * Help method that tries to load and initialize a class with given name.
   *
   * @param <N> Class extending given class type
   * @param classType Class type
   * @param className Class name
   * @return Class extending given class type or null if not found
   */
  public <N> Class<? extends N> tryLoadClass(
      Object callingObject, Class<N> classType, String className) {

    if (callingObject != null) {
      try {
        return callingObject.getClass().getClassLoader().loadClass(className).asSubclass(classType);
      } catch (ClassNotFoundException | UnsupportedClassVersionError e) {
      }
    }

    try {
      return Class.forName(className).asSubclass(classType);
    } catch (ClassNotFoundException | UnsupportedClassVersionError e) {
    }

    try {
      return getProjectClassLoader().loadClass(className).asSubclass(classType);
    } catch (ParseProjectsException | NoClassDefFoundError | UnsupportedClassVersionError | ClassNotFoundException e) {
    }

    return null;
  }

  public static File findJarFile(File projectDir, String jarfile) {
    File fp = new File(jarfile);
    if (!fp.exists()) {
      fp = new File(projectDir, jarfile);
    }
    if (!fp.exists()) {
      fp = new File(projectDir, "java/" + jarfile);
    }
    if (!fp.exists()) {
      fp = new File(projectDir, "java/lib/" + jarfile);
    }
    if (!fp.exists()) {
      fp = new File(projectDir, "lib/" + jarfile);
    }
    return fp.exists() ? fp : null;
  }

  private static ClassLoader createClassLoader(ClassLoader parent, Collection<COOJAProject> projects)
  throws ClassLoaderCreationException {
    if (projects == null || projects.isEmpty()) {
      return parent;
    }

    /* Create class loader from JARs */
    ArrayList<URL> urls = new ArrayList<>();
    for (COOJAProject project: projects) {
    	File projectDir = project.dir;
      try {
        urls.add(new File(projectDir, "java").toURI().toURL());

        // Read configuration to check if any JAR files should be loaded
        ProjectConfig projectConfig = new ProjectConfig(false);
        projectConfig.appendProjectDir(projectDir);
        String[] projectJarFiles = projectConfig.getStringArrayValue(
            Cooja.class, "JARFILES");
        if (projectJarFiles != null && projectJarFiles.length > 0) {
          for (String jarfile : projectJarFiles) {
            File jarpath = findJarFile(projectDir, jarfile);
            if (jarpath == null) {
              throw new FileNotFoundException(jarfile);
            }
            urls.add(jarpath.toURI().toURL());
          }
        }

      } catch (Exception e) {
        logger.fatal("Error when trying to read JAR-file in " + projectDir
            + ": " + e);
        throw new ClassLoaderCreationException("Error when trying to read JAR-file in " + projectDir, e);
      }
    }

    URL[] urlsArray = urls.toArray(new URL[0]);
    return new URLClassLoader(urlsArray, parent);
  }

  /**
   * Help method that returns the description for given object. This method
   * reads from the object's class annotations if existing. Otherwise, it returns
   * the simple class name of object's class.
   *
   * @param object
   *          Object
   * @return Description
   */
  public static String getDescriptionOf(Object object) {
    return getDescriptionOf(object.getClass());
  }

  /**
   * Help method that returns the description for given class. This method reads
   * from class annotations if existing. Otherwise, it returns the simple class
   * name.
   *
   * @param clazz
   *          Class
   * @return Description
   */
  public static String getDescriptionOf(Class<?> clazz) {
    if (clazz.isAnnotationPresent(ClassDescription.class)) {
      return clazz.getAnnotation(ClassDescription.class).value();
    }
    return clazz.getSimpleName();
  }

  /**
   * Help method that returns the abstraction level description for given mote type class.
   *
   * @param clazz
   *          Class
   * @return Description
   */
  public static String getAbstractionLevelDescriptionOf(Class<? extends MoteType> clazz) {
    if (clazz.isAnnotationPresent(AbstractionLevelDescription.class)) {
      return clazz.getAnnotation(AbstractionLevelDescription.class).value();
    }
    return null;
  }

  /**
   * Load configurations and create a GUI.
   *
   * @param options Parsed command line options
   */
  public static void go(Main options) {
    externalToolsUserSettingsFileReadOnly = options.externalToolsConfig != null;
    if (options.externalToolsConfig == null) {
      externalToolsUserSettingsFile = new File(System.getProperty("user.home"), ".cooja.user.properties");
    } else {
      externalToolsUserSettingsFile = new File(options.externalToolsConfig);
    }

    specifiedContikiPath = options.contikiPath;
    specifiedCoojaPath = options.coojaPath;

    // Is Cooja started in GUI mode?
    var vis = options.action == null || options.action.quickstart != null;

    if (vis) {
      try {
        setLookAndFeel();
      } catch (InterruptedException e) {
        logger.fatal("Thread interrupted: " + e.getMessage());
        return;
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }

    Cooja gui = null;
    try {
      gui = makeCooja(options.logDir, vis);
    } catch (Exception e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    // Check if simulator should be quick-started.
    if (options.action != null) {
      var config = new File(vis ? options.action.quickstart : options.action.nogui);
      Simulation sim = null;
      try {
        sim = vis
                ? gui.doLoadConfig(config, true, options.updateSimulation, options.randomSeed)
                : gui.loadSimulationConfig(config, true, false, options.randomSeed);
      } catch (Exception e) {
        logger.fatal("Exception when loading simulation: ", e);
      }
      if (sim == null) {
        System.exit(1);
      }
      if (!vis) {
        sim.setSpeedLimit(null);
        sim.startSimulation();
      }
    }
  }

  /**
   * Loads a simulation configuration from given file.
   * <p>
   * When loading Contiki mote types, the libraries must be recompiled. User may
   * change mote type settings at this point.
   *
   * @param file       File to read
   * @param rewriteCsc Should Cooja update the .csc file.
   * @param manualRandomSeed The random seed.
   * @return New simulation or null if recompiling failed or aborted
   * @throws SimulationCreationException If loading fails.
   * @see #saveSimulationConfig(File)
   */
  private Simulation loadSimulationConfig(File file, boolean quick, boolean rewriteCsc, Long manualRandomSeed)
  throws SimulationCreationException {
    this.currentConfigFile = file; /* Used to generate config relative paths */
    try {
      this.currentConfigFile = this.currentConfigFile.getCanonicalFile();
    } catch (IOException e) {
    }

    Simulation sim;
    try (InputStream in = file.getName().endsWith(".gz")
            ? new GZIPInputStream(new FileInputStream(file)) : new FileInputStream(file)) {
      final var doc = new SAXBuilder().build(in);
      var root = doc.getRootElement();
      // Check that config file version is correct
      if (!root.getName().equals("simconf")) {
        logger.fatal("Not a valid Cooja simulation config.");
        return null;
      }
      doRemoveSimulation(false);
      sim = createSimulation(root, quick, rewriteCsc, manualRandomSeed);
      setSimulation(sim);
    } catch (JDOMException e) {
      throw new SimulationCreationException("Config not well-formed", e);
    } catch (IOException e) {
      throw new SimulationCreationException("Load simulation error", e);
    }
    // Rewrite simulation config after the InputStream is closed.
    if (rewriteCsc) {
      saveSimulationConfig(file);
      System.exit(0);
    }
    return sim;
  }

  /** Create a new simulation object.
   * @param root The XML config.
   * @param quick Do a quickstart.
   * @param rewriteCsc Should Cooja update the .csc file.
   * @param manualRandomSeed The random seed.
   * @throws SimulationCreationException If creation fails.
   * @return Simulation object.
   * */
  private Simulation createSimulation(Element root, boolean quick, boolean rewriteCsc, Long manualRandomSeed)
  throws SimulationCreationException {
    boolean projectsOk = verifyProjects(root);

    Simulation newSim = new Simulation(this);
    try {
      /* GENERATE UNIQUE MOTE TYPE IDENTIFIERS */

      /* Locate Contiki mote types in config */
      var readNames = new ArrayList<String>();
      var motetypes = root.getDescendants(new ElementFilter("motetype"));
      while (motetypes.hasNext()) {
        var e = (Element)motetypes.next();
        if (ContikiMoteType.class.getName().equals(e.getContent(0).getValue().trim())) {
          readNames.add(e.getChild("identifier").getValue());
        }
      }

      // Only renumber motes if their names can collide with existing motes.
      if (!rewriteCsc) {
        // Create old to new identifier mappings.
        var moteTypeIDMappings = new HashMap<String, String>();
        var reserved = new HashSet<>(readNames);
        var existingMoteTypes = mySimulation == null ? null : mySimulation.getMoteTypes();
        if (existingMoteTypes != null) {
          for (var mote : existingMoteTypes) {
            reserved.add(mote.getIdentifier());
          }
        }
        for (var existingIdentifier : readNames) {
          String newID = ContikiMoteType.generateUniqueMoteTypeID(reserved);
          moteTypeIDMappings.put(existingIdentifier, newID);
          reserved.add(newID);
        }

        // Replace all <motetype>..ContikiMoteType.class<identifier>mtypeXXX</identifier>...
        // in the config with the new identifiers.
        motetypes = root.getDescendants(new ElementFilter("motetype"));
        while (motetypes.hasNext()) {
          var e = (Element) motetypes.next();
          if (ContikiMoteType.class.getName().equals(e.getContent(0).getValue().trim())) {
            var idNode = e.getChild("identifier");
            var newName = moteTypeIDMappings.get(idNode.getValue());
            idNode.setText(newName);
          }
        }
        // Replace all <mote>...<motetype_identifier>mtypeXXX</motetype_identifier>...
        // in the config with the new identifiers.
        var motes = root.getDescendants(new ElementFilter("mote"));
        while (motes.hasNext()) {
          var e = (Element) motes.next();
          var idNode = e.getChild("motetype_identifier");
          if (idNode == null) {
            continue;
          }
          var newName = moteTypeIDMappings.get(idNode.getValue());
          if (newName != null) {
            idNode.setText(newName);
          }
        }
      }
      System.gc();

      if (!newSim.setConfigXML(root.getChild("simulation"), isVisualized(), quick, manualRandomSeed)) {
        logger.info("Simulation not loaded");
        return null;
      }

      // Restart plugins from config
      setPluginsConfigXML(root, newSim);

    } catch (JDOMException e) {
      throw new SimulationCreationException("Configuration file not wellformed: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new SimulationCreationException("No access to configuration file: " + e.getMessage(), e);
    } catch (MoteTypeCreationException e) {
      throw new SimulationCreationException("Mote type creation error: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new SimulationCreationException("Unknown error: " + e.getMessage(), e);
    }

    // Non-GUI Cooja requires a simulation controller, ensure one is started.
    if (!isVisualized()) {
      boolean hasController = false;
      for (var p : startedPlugins) {
        int pluginType = p.getClass().getAnnotation(PluginType.class).value();
        if (pluginType == PluginType.SIM_CONTROL_PLUGIN) {
          hasController = true;
          break;
        }
      }
      if (!hasController) {
        logger.fatal("No plugin controlling simulation, aborting");
        return null;
      }
    }
    return newSim;
  }

  /**
   * Saves current simulation configuration to given file and notifies
   * observers.
   *
   * @see #loadSimulationConfig(File, boolean, boolean, Long)
   * @param file
   *          File to write
   */
   private void saveSimulationConfig(File file) {
    this.currentConfigFile = file; /* Used to generate config relative paths */
    try {
      this.currentConfigFile = this.currentConfigFile.getCanonicalFile();
    } catch (IOException e) {
    }

    try (var out = file.getName().endsWith(".gz")
            ? new GZIPOutputStream(new FileOutputStream(file)) : new FileOutputStream(file)) {
      // Create and write to document
      Document doc = new Document(extractSimulationConfig());

      XMLOutputter outputter = new XMLOutputter();
      Format fmt = Format.getPrettyFormat();
      fmt.setLineSeparator("\n");
      outputter.setFormat(fmt);
      outputter.output(doc, out);
      logger.info("Saved to file: " + file.getAbsolutePath());
    } catch (Exception e) {
      logger.warn("Exception while saving simulation config: " + e);
      e.printStackTrace();
    }
  }

  private Element extractSimulationConfig() {
    // Create simulation config
    Element root = new Element("simconf");

    /* Store extension directories meta data */
    for (COOJAProject project: currentProjects) {
      Element projectElement = new Element("project");
      projectElement.addContent(createPortablePath(project.dir).getPath().replaceAll("\\\\", "/"));
      projectElement.setAttribute("EXPORT", "discard");
      root.addContent(projectElement);
    }

    Element simulationElement = new Element("simulation");
    simulationElement.addContent(mySimulation.getConfigXML());
    root.addContent(simulationElement);

    // Create started plugins config
    root.addContent(getPluginsConfigXML());

    return root;
  }

  /**
   * Returns started plugins config.
   *
   * @return Config or null
   */
  private Collection<Element> getPluginsConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element pluginElement, pluginSubElement;

    /* Loop over all plugins */
    for (Plugin startedPlugin : startedPlugins) {
      int pluginType = startedPlugin.getClass().getAnnotation(PluginType.class).value();

      // Ignore GUI plugins
      if (pluginType == PluginType.COOJA_PLUGIN
          || pluginType == PluginType.COOJA_STANDARD_PLUGIN) {
        continue;
      }

      pluginElement = new Element("plugin");
      pluginElement.setText(startedPlugin.getClass().getName());

      // Create mote argument config (if mote plugin)
      if (pluginType == PluginType.MOTE_PLUGIN) {
        pluginSubElement = new Element("mote_arg");
        Mote taggedMote = ((MotePlugin) startedPlugin).getMote();
        for (int moteNr = 0; moteNr < mySimulation.getMotesCount(); moteNr++) {
          if (mySimulation.getMote(moteNr) == taggedMote) {
            pluginSubElement.setText(Integer.toString(moteNr));
            pluginElement.addContent(pluginSubElement);
            break;
          }
        }
      }

      // Create plugin specific configuration
      Collection<Element> pluginXML = startedPlugin.getConfigXML();
      if (pluginXML != null) {
        pluginSubElement = new Element("plugin_config");
        pluginSubElement.addContent(pluginXML);
        pluginElement.addContent(pluginSubElement);
      }

      // If plugin is visualizer plugin, create visualization arguments
      if (startedPlugin.getCooja() != null) {
        JInternalFrame pluginFrame = startedPlugin.getCooja();

        pluginSubElement = new Element("width");
        pluginSubElement.setText(String.valueOf(pluginFrame.getSize().width));
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("z");
        pluginSubElement.setText(String.valueOf(getDesktopPane().getComponentZOrder(pluginFrame)));
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("height");
        pluginSubElement.setText(String.valueOf(pluginFrame.getSize().height));
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("location_x");
        pluginSubElement.setText(String.valueOf(pluginFrame.getLocation().x));
        pluginElement.addContent(pluginSubElement);

        pluginSubElement = new Element("location_y");
        pluginSubElement.setText(String.valueOf(pluginFrame.getLocation().y));
        pluginElement.addContent(pluginSubElement);

        if (pluginFrame.isIcon()) {
          pluginSubElement = new Element("minimized");
          pluginSubElement.setText("" + true);
          pluginElement.addContent(pluginSubElement);
        }
      }

      config.add(pluginElement);
    }

    return config;
  }

  /** Verify project extension directories. */
  private boolean verifyProjects(Element root) {
    boolean allOk = true;

    /* Match current extensions against extensions in simulation config */
    for (var project : root.getChildren("project")) {
      var pluginElement = (Element)project;
      // Skip check for plugins that are Cooja-internal in v4.8.
      // FIXME: v4.9: remove these special cases.
      if ("[APPS_DIR]/mrm".equals(pluginElement.getText())) continue;
      if ("[APPS_DIR]/mspsim".equals(pluginElement.getText())) continue;
      if ("[APPS_DIR]/powertracker".equals(pluginElement.getText())) continue;
      if ("[APPS_DIR]/serial_socket".equals(pluginElement.getText())) continue;
      File projectFile = restorePortablePath(new File(pluginElement.getText()));
      try {
        projectFile = projectFile.getCanonicalFile();
      } catch (IOException e) {
      }

      boolean found = false;
      for (COOJAProject currentProject : currentProjects) {
        if (projectFile.getPath().replaceAll("\\\\", "/").
                equals(currentProject.dir.getPath().replaceAll("\\\\", "/"))) {
          found = true;
          break;
        }
      }

      if (!found) {
        logger.warn("Loaded simulation may depend on not found extension: '" + projectFile + "'");
        allOk = false;
      }
    }

    return allOk;
  }


  /**
   * Starts plugins with arguments in given config.
   *
   * @param root XML config root element
   * @param sim Simulation on which to start plugins
   * @return True if all plugins started, false otherwise
   */
  private boolean setPluginsConfigXML(Element root, Simulation sim) {
    for (var e : root.getChildren("plugin")) {
      final Element pluginElement = (Element)e;
      // Read plugin class
      String pluginClassName = pluginElement.getText().trim();

      /* Backwards compatibility: se.sics -> org.contikios */
      if (pluginClassName.startsWith("se.sics")) {
        pluginClassName = pluginClassName.replaceFirst("se\\.sics", "org.contikios");
      }

      /* Backwards compatibility: old visualizers were replaced */
      if (pluginClassName.equals("org.contikios.cooja.plugins.VisUDGM") ||
              pluginClassName.equals("org.contikios.cooja.plugins.VisBattery") ||
              pluginClassName.equals("org.contikios.cooja.plugins.VisTraffic") ||
              pluginClassName.equals("org.contikios.cooja.plugins.VisState")) {
        logger.warn("Old simulation config detected: visualizers have been remade");
        pluginClassName = "org.contikios.cooja.plugins.Visualizer";
      }

      Class<? extends Plugin> pluginClass =
              tryLoadClass(this, Plugin.class, pluginClassName);
      if (pluginClass == null) {
        logger.fatal("Could not load plugin class: " + pluginClassName);
        return false;
      }
      // Skip plugins that require visualization in headless mode.
      if (!isVisualized() && VisPlugin.class.isAssignableFrom(pluginClass)) {
        continue;
      }

      // Parse plugin mote argument (if any)
      Mote mote = null;
      for (var pluginSubElement : pluginElement.getChildren("mote_arg")) {
        int moteNr = Integer.parseInt(((Element)pluginSubElement).getText());
        if (moteNr >= 0 && moteNr < sim.getMotesCount()) {
          mote = sim.getMote(moteNr);
        }
      }

      tryStartPlugin(pluginClass, this, sim, mote, pluginElement);
    }

    if (!isVisualized()) {
      return true;
    }

    /* Z order visualized plugins */
    try {
    	for (int z=0; z < getDesktopPane().getAllFrames().length; z++) {
        for (JInternalFrame plugin : getDesktopPane().getAllFrames()) {
          if (plugin.getClientProperty("zorder") == null) {
          	continue;
          }
          int zOrder = (Integer) plugin.getClientProperty("zorder");
          if (zOrder != z) {
          	continue;
          }
          getDesktopPane().setComponentZOrder(plugin, zOrder);
          if (z == 0) {
            plugin.setSelected(true);
          }
          plugin.putClientProperty("zorder", null);
          break;
        }
        getDesktopPane().repaint();
    	}
    } catch (Exception e) { }

    return true;
  }

  public static class ParseProjectsException extends Exception {
    public ParseProjectsException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class ClassLoaderCreationException extends Exception {
    public ClassLoaderCreationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class SimulationCreationException extends Exception {
    public SimulationCreationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class PluginConstructionException extends Exception {
		public PluginConstructionException(String message) {
      super(message);
    }
    public PluginConstructionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * A simple error dialog with compilation output and stack trace.
   *
   * @param parentComponent
   *          Parent component
   * @param title
   *          Title of error window
   * @param exception
   *          Exception causing window to be shown
   * @param retryAvailable
   *          If true, a retry option is presented
   * @return Retry failed operation
   */
  public static boolean showErrorDialog(final Component parentComponent,
      final String title, final Throwable exception, final boolean retryAvailable) {

    return new RunnableInEDT<Boolean>() {
      @Override
      public Boolean work() {
        JTabbedPane tabbedPane = new JTabbedPane();
        final JDialog errorDialog;
        if (parentComponent instanceof Dialog) {
          errorDialog = new JDialog((Dialog) parentComponent, title, true);
        } else if (parentComponent instanceof Frame) {
          errorDialog = new JDialog((Frame) parentComponent, title, true);
        } else {
          errorDialog = new JDialog((Frame) null, title);
        }
        Box buttonBox = Box.createHorizontalBox();

        if (exception != null) {
          /* Contiki error */
          if (exception instanceof ContikiError) {
            String contikiError = ((ContikiError) exception).getContikiError();
            MessageListUI list = new MessageListUI();
            list.addMessage(exception.getMessage());
            list.addMessage("");
            list.addMessage("");
            for (String l: contikiError.split("\n")) {
              list.addMessage(l);
            }
            list.addPopupMenuItem(null, true);
            tabbedPane.addTab("Contiki error", new JScrollPane(list));
          }

          /* Compilation output */
          MessageListUI compilationOutput = null;
          if (exception instanceof MoteTypeCreationException
              && ((MoteTypeCreationException) exception).hasCompilationOutput()) {
            compilationOutput = (MessageListUI) ((MoteTypeCreationException) exception).getCompilationOutput();
          } else if (exception.getCause() != null
              && exception.getCause() instanceof MoteTypeCreationException
              && ((MoteTypeCreationException) exception.getCause()).hasCompilationOutput()) {
            compilationOutput = (MessageListUI) ((MoteTypeCreationException) exception.getCause()).getCompilationOutput();
          }
          if (compilationOutput != null) {
            compilationOutput.addPopupMenuItem(null, true);
            tabbedPane.addTab("Compilation output", new JScrollPane(compilationOutput));
          }

          /* Stack trace */
          MessageListUI stackTrace = new MessageListUI();
          PrintStream printStream = stackTrace.getInputStream(MessageListUI.NORMAL);
          exception.printStackTrace(printStream);
          stackTrace.addPopupMenuItem(null, true);
          tabbedPane.addTab("Java stack trace", new JScrollPane(stackTrace));

          /* Exception message */
          buttonBox.add(Box.createHorizontalStrut(10));
          buttonBox.add(new JLabel(exception.getMessage()));
          buttonBox.add(Box.createHorizontalStrut(10));
        }

        buttonBox.add(Box.createHorizontalGlue());

        if (retryAvailable) {
          Action retryAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              errorDialog.setTitle("-RETRY-");
              errorDialog.dispose();
            }
          };
          JButton retryButton = new JButton(retryAction);
          retryButton.setText("Retry Ctrl+R");
          buttonBox.add(retryButton);

          InputMap inputMap = errorDialog.getRootPane().getInputMap(
              JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
          inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK, false), "retry");
          errorDialog.getRootPane().getActionMap().put("retry", retryAction);
        }

        AbstractAction closeAction = new AbstractAction(){
          @Override
          public void actionPerformed(ActionEvent e) {
            errorDialog.dispose();
          }
        };

        JButton closeButton = new JButton(closeAction);
        closeButton.setText("Close");
        buttonBox.add(closeButton);

        InputMap inputMap = errorDialog.getRootPane().getInputMap(
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "close");
        errorDialog.getRootPane().getActionMap().put("close", closeAction);


        errorDialog.getRootPane().setDefaultButton(closeButton);
        errorDialog.getContentPane().add(BorderLayout.CENTER, tabbedPane);
        errorDialog.getContentPane().add(BorderLayout.SOUTH, buttonBox);
        errorDialog.setSize(700, 500);
        errorDialog.setLocationRelativeTo(parentComponent);
        errorDialog.setVisible(true); /* BLOCKS */

        return errorDialog.getTitle().equals("-RETRY-");

      }
    }.invokeAndWait();

  }

  private static void showWarningsDialog(final Frame parent, final String[] warnings) {
    new RunnableInEDT<Boolean>() {
      @Override
      public Boolean work() {
        final JDialog dialog = new JDialog(parent, "Compilation warnings", false);
        Box buttonBox = Box.createHorizontalBox();

        /* Warnings message list */
        MessageListUI compilationOutput = new MessageListUI();
        for (String w: warnings) {
          compilationOutput.addMessage(w, MessageList.ERROR);
        }
        compilationOutput.addPopupMenuItem(null, true);

        /* Checkbox */
        buttonBox.add(Box.createHorizontalGlue());
        JCheckBox hideButton = new JCheckBox("Hide compilation warnings", false);
        hideButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Cooja.setExternalToolsSetting("HIDE_WARNINGS",
                    String.valueOf(((JCheckBox) e.getSource()).isSelected()));
          }
        });
        buttonBox.add(Box.createHorizontalStrut(10));
        buttonBox.add(hideButton);

        /* Close on escape */
        AbstractAction closeAction = new AbstractAction(){
          @Override
          public void actionPerformed(ActionEvent e) {
            dialog.dispose();
          }
        };
        InputMap inputMap = dialog.getRootPane().getInputMap(
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "close");
        dialog.getRootPane().getActionMap().put("close", closeAction);

        /* Layout */
        dialog.getContentPane().add(BorderLayout.CENTER, new JScrollPane(compilationOutput));
        dialog.getContentPane().add(BorderLayout.SOUTH, buttonBox);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return true;
      }
    }.invokeAndWait();
  }

  /**
   * Runs work method in event dispatcher thread.
   * Worker method returns a value.
   *
   * @author Fredrik Osterlind
   */
  public static abstract class RunnableInEDT<T> {
    private T val;

    /**
     * Work method to be implemented.
     *
     * @return Return value
     */
    public abstract T work();

    /**
     * Runs worker method in event dispatcher thread.
     *
     * @see #work()
     * @return Worker method return value
     */
    public T invokeAndWait() {
      if(java.awt.EventQueue.isDispatchThread()) {
        return RunnableInEDT.this.work();
      }

      try {
        java.awt.EventQueue.invokeAndWait(() -> val = RunnableInEDT.this.work());
      } catch (InterruptedException | InvocationTargetException e) {
        e.printStackTrace();
      }

      return val;
    }
  }

  /**
   * This method can be used by various different modules in the simulator to
   * indicate for example that a mote has been selected. All mote highlight
   * listeners will be notified. An example application of mote highlighting is
   * a simulator visualizer that highlights the mote.
   *
   * @see #addMoteHighlightObserver(Observer)
   * @param m
   *          Mote to highlight
   */
  public void signalMoteHighlight(Mote m) {
    if (moteHighlightObservable != null) {
      moteHighlightObservable.setChangedAndNotify(m);
    }
  }

  /**
   * Adds directed relation between given motes.
   *
   * @param source Source mote
   * @param dest Destination mote
   * @param color The color to use when visualizing the mote relation
   */
  public void addMoteRelation(Mote source, Mote dest, Color color) {
    if (source == null || dest == null || moteRelationObservable == null) {
      return;
    }
    removeMoteRelation(source, dest); /* Unique relations */
    moteRelations.add(new MoteRelation(source, dest, color));
    moteRelationObservable.setChangedAndNotify();
  }

  /**
   * Removes the relations between given motes.
   *
   * @param source Source mote
   * @param dest Destination mote
   */
  public void removeMoteRelation(Mote source, Mote dest) {
    if (source == null || dest == null || moteRelationObservable == null) {
      return;
    }
    MoteRelation[] arr = getMoteRelations();
    for (MoteRelation r: arr) {
      if (r.source == source && r.dest == dest) {
        moteRelations.remove(r);
        /* Relations are unique */
        moteRelationObservable.setChangedAndNotify();
        break;
      }
    }
  }

  /**
   * @return All current mote relations.
   *
   * @see #addMoteRelationsObserver(Observer)
   */
  public MoteRelation[] getMoteRelations() {
    return moteRelations.toArray(new MoteRelation[0]);
  }

  /**
   * Adds mote relation observer.
   * Typically, used by visualizer plugins.
   *
   * @param newObserver Observer
   */
  public void addMoteRelationsObserver(Observer newObserver) {
    if (moteRelationObservable != null) {
      moteRelationObservable.addObserver(newObserver);
    }
  }

  /**
   * Removes mote relation observer.
   * Typically, used by visualizer plugins.
   *
   * @param observer Observer
   */
  public void deleteMoteRelationsObserver(Observer observer) {
    if (moteRelationObservable != null) {
      moteRelationObservable.deleteObserver(observer);
    }
  }

  /**
   * Tries to convert given file to be "portable".
   * The portable path is either relative to Contiki, or to the configuration (.csc) file.
   * The config relative path is preferred if the two paths are the same length, otherwise
   * the shorter relative path is preferred.
   * <p>
   * If this method fails, it returns the original file.
   *
   * @param file Original file
   * @return Portable file, or original file is conversion failed
   */
  public File createPortablePath(File file) {
    return createPortablePath(file, true);
  }

  public File createPortablePath(File file, boolean allowConfigRelativePaths) {
    File contikiBase = createContikiRelativePath(file);
    if (allowConfigRelativePaths) {
      var configBase = createConfigRelativePath(file);
      if (configBase != null &&
          (contikiBase == null || configBase.toString().length() <= contikiBase.toString().length())) {
        return configBase;
      }
    }

    if (contikiBase != null) {
      return contikiBase;
    }

    logger.warn("Path is not portable: '" + file.getPath());
    return file;
  }

  /**
   * Tries to restore a previously "portable" file to be "absolute".
   * If the given file already exists, no conversion is performed.
   *
   * @see #createPortablePath(File)
   * @param file Portable file
   * @return Absolute file
   */
  public File restorePortablePath(File file) {
    if (file == null || file.exists()) {
      /* No conversion possible/needed */
      return file;
    }

    File absolute = restoreContikiRelativePath(file);
    if (absolute != null) {
      return absolute;
    }

    absolute = restoreConfigRelativePath(currentConfigFile, file);
    if (absolute != null) {
      return absolute;
    }

    return file;
  }

  private final static String[][] PATH_IDENTIFIER = {
	  {"[CONTIKI_DIR]","PATH_CONTIKI",""},
	  {"[COOJA_DIR]","PATH_COOJA",""},
	  {"[APPS_DIR]","PATH_APPS","apps"}
  };

  private static File createContikiRelativePath(File file) {
    try {
    	int elem = PATH_IDENTIFIER.length;
    	File[] path = new File [elem];
    	String[] canonicals = new String[elem];
    	int match = -1;
    	int mlength = 0;
    	String fileCanonical = file.getCanonicalPath();
      // Not so nice, but goes along with GUI.getExternalToolsSetting
			String defp = Cooja.getExternalToolsSetting("PATH_COOJA", null);
		for(int i = 0; i < elem; i++){
			path[i] = new File(Cooja.getExternalToolsSetting(PATH_IDENTIFIER[i][1], defp + PATH_IDENTIFIER[i][2]));			
			canonicals[i] = path[i].getCanonicalPath();
			if (fileCanonical.startsWith(canonicals[i])){
				if(mlength < canonicals[i].length()){
					mlength = canonicals[i].length();
					match = i;
				}
	    	}
		}
      
	    if(match == -1) return null;

	    /* Replace Contiki's canonical path with Contiki identifier */
        String portablePath = fileCanonical.replaceFirst(
          java.util.regex.Matcher.quoteReplacement(canonicals[match]), 
          java.util.regex.Matcher.quoteReplacement(PATH_IDENTIFIER[match][0]));
        File portable = new File(portablePath);
      
        /* Verify conversion */
        File verify = restoreContikiRelativePath(portable);
        if (verify == null || !verify.exists()) {
        	/* Error: did file even exist pre-conversion? */
        	return null;
        }

        return portable;
    } catch (IOException e1) {
      return null;
    }
  }
  
  
  private static File restoreContikiRelativePath(File portable) {
  	int elem = PATH_IDENTIFIER.length;

    try {
    	String portablePath = portable.getPath();
        int i = 0;
    	for(; i < elem; i++){
    		if (portablePath.startsWith(PATH_IDENTIFIER[i][0])) break;
    	}
    	if(i == elem) return null;

      // Not so nice, but goes along with GUI.getExternalToolsSetting
			String defp = Cooja.getExternalToolsSetting("PATH_COOJA", null);
      var path = new File(Cooja.getExternalToolsSetting(PATH_IDENTIFIER[i][1], defp + PATH_IDENTIFIER[i][2]));
    	
      var canonical = path.getCanonicalPath();
    	File absolute = new File(portablePath.replace(PATH_IDENTIFIER[i][0], canonical));
		if(!absolute.exists()){
			logger.warn("Replaced " + portable  + " with " + absolute + " (default: "+ defp + PATH_IDENTIFIER[i][2] +"), but could not find it. This does not have to be an error, as the file might be created later.");
		}
    	return absolute;
    } catch (IOException e) {
    	return null;
    }
  }

  private final static String PATH_CONFIG_IDENTIFIER = "[CONFIG_DIR]";
  public File currentConfigFile = null; /* Used to generate config relative paths */

  /**
   * Returns the config dir.
   */
  public String getConfigDir() {
    if (currentConfigFile == null) {
      return null;
    }
    var parent = currentConfigFile.getParentFile();
    return parent == null ? null : parent.toString();
  }

  /**
   * Replaces all occurrences of [CONFIG_DIR] in s with the config dir.
   */
  public String resolveConfigDir(String s) {
    var cfgDir = getConfigDir();
    return s.replace(PATH_CONFIG_IDENTIFIER, cfgDir == null ? "." : cfgDir);
  }

  private File createConfigRelativePath(File file) {
    String id = PATH_CONFIG_IDENTIFIER;
    if (currentConfigFile == null) {
      return null;
    }
    try {
      File configPath = currentConfigFile.getParentFile();
      if (configPath == null) {
        /* File is in current directory */
        configPath = new File("");
      }
      String configCanonical = configPath.getCanonicalPath();

      String fileCanonical = file.getCanonicalPath();
      if (!fileCanonical.startsWith(configCanonical)) {
        /* SPECIAL CASE: Allow one parent directory */
        File parent = new File(configCanonical).getParentFile();
        if (parent != null) {
          configCanonical = parent.getCanonicalPath();
          id += "/..";
        }
      }
      if (!fileCanonical.startsWith(configCanonical)) {
        /* SPECIAL CASE: Allow two parent directories */
        File parent = new File(configCanonical).getParentFile();
        if (parent != null) {
          configCanonical = parent.getCanonicalPath();
          id += "/..";
        }
      }
      if (!fileCanonical.startsWith(configCanonical)) {
        /* SPECIAL CASE: Allow three parent directories */
        File parent = new File(configCanonical).getParentFile();
        if (parent != null) {
          configCanonical = parent.getCanonicalPath();
          id += "/..";
        }
      }
      if (!fileCanonical.startsWith(configCanonical)) {
        /* File is not in a config subdirectory */
        return null;
      }

      /* Replace config's canonical path with config identifier */
      String portablePath = fileCanonical.replaceFirst(
          java.util.regex.Matcher.quoteReplacement(configCanonical),
          java.util.regex.Matcher.quoteReplacement(id));
      File portable = new File(portablePath);

      /* Verify conversion */
      File verify = restoreConfigRelativePath(currentConfigFile, portable);
      if (verify == null || !verify.exists()) {
        /* Error: did file even exist pre-conversion? */
        return null;
      }
      return portable;
    } catch (IOException e1) {
      return null;
    }
  }

  private static File restoreConfigRelativePath(File configFile, File portable) {
    if (configFile == null) {
      return null;
    }
    File configPath = configFile.getParentFile();
    if (configPath == null) {
        /* File is in current directory */
        configPath = new File("");
    }
    String portablePath = portable.getPath();
    if (!portablePath.startsWith(PATH_CONFIG_IDENTIFIER)) {
      return null;
    }
    return new File(portablePath.replace(PATH_CONFIG_IDENTIFIER, configPath.getAbsolutePath()));
  }

  private static JProgressBar PROGRESS_BAR = null;
  private static final ArrayList<String> PROGRESS_WARNINGS = new ArrayList<>();
  public static void setProgressMessage(String msg) {
    setProgressMessage(msg, MessageListUI.NORMAL);
  }
  public static void setProgressMessage(String msg, int type) {
    if (PROGRESS_BAR != null && PROGRESS_BAR.isShowing()) {
      PROGRESS_BAR.setString(msg);
      PROGRESS_BAR.setStringPainted(true);
    }
    if (type != MessageListUI.NORMAL) {
      PROGRESS_WARNINGS.add(msg);
    }
  }

  /**
   * Load quick help for given object or identifier. Note that this method does not
   * show the quick help pane.
   *
   * @param obj If string: help identifier. Else, the class name of the argument
   * is used as help identifier.
   */
  public void loadQuickHelp(final Object obj) {
    if (obj == null) {
      return;
    }

    String help;
    if (obj instanceof HasQuickHelp) {
      help = ((HasQuickHelp) obj).getQuickHelp();
    } else {
      String key;
      if (obj instanceof String) {
        key = (String) obj;
      } else {
        key = obj.getClass().getName();
      }
      switch (key) {
        case "KEYBOARD_SHORTCUTS":
          help = "<b>Keyboard shortcuts</b><br>" +
                  "<br><i>Ctrl+N:</i> New simulation" +
                  "<br><i>Ctrl+S:</i> Start/pause simulation" +
                  "<br><i>Ctrl+R:</i> Reload current simulation. If no simulation exists, the last used simulation config is loaded" +
                  "<br><i>Ctrl+Shift+R:</i> Reload current simulation with another random seed" +
                  "<br>" +
                  "<br><i>F1:</i> Toggle quick help";
          break;
        case "GETTING_STARTED":
          help = "<b>Getting started</b><br>" +
                  "<br>" +
                  "<br><i>F1:</i> Toggle quick help</i>";
          break;
        default:
          help = null;
          break;
      }
    }

    if (help != null) {
      quickHelpTextPane.setText("<html>" + help + "</html>");
    } else {
      quickHelpTextPane.setText(
          "<html><b>" + getDescriptionOf(obj) +"</b>" +
          "<p>No help available</html>");
    }
    quickHelpTextPane.setCaretPosition(0);
  }

  /* GUI actions */
  abstract static class GUIAction extends AbstractAction {
		public GUIAction(String name) {
      super(name);
    }
    public GUIAction(String name, int mnemonic) {
      this(name);
      putValue(Action.MNEMONIC_KEY, mnemonic);
    }
    public GUIAction(String name, KeyStroke accelerator) {
      this(name);
      putValue(Action.ACCELERATOR_KEY, accelerator);
    }
    public GUIAction(String name, int mnemonic, KeyStroke accelerator) {
      this(name, mnemonic);
      putValue(Action.ACCELERATOR_KEY, accelerator);
    }
    public abstract boolean shouldBeEnabled();
  }
  class StartPluginGUIAction extends GUIAction {
               public StartPluginGUIAction(String name) {
      super(name);
    }
    @Override
    public void actionPerformed(final ActionEvent e) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          Class<Plugin> pluginClass =
            (Class<Plugin>) ((JMenuItem) e.getSource()).getClientProperty("class");
          Mote mote = (Mote) ((JMenuItem) e.getSource()).getClientProperty("mote");
          tryStartPlugin(pluginClass, cooja, mySimulation, mote);
        }
      }, "StartPluginGUIAction").start();
    }
    @Override
    public boolean shouldBeEnabled() {
      return getSimulation() != null;
    }
  }

  private static final class ShutdownHandler extends Thread {
    private final Cooja cooja;

    public ShutdownHandler(Cooja cooja) {
      super("Cooja-Shutdown");
      this.cooja = cooja;
    }

    @Override
    public void run() {
      // Stop the simulation if it is running.
      Simulation simulation = cooja.getSimulation();
      if (simulation != null) {
        simulation.stopSimulation(true);
      }
    }
  }

}
