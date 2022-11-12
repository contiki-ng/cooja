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

import static org.contikios.cooja.GUI.WINDOW_TITLE;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.MoteType.MoteTypeCreationException;
import org.contikios.cooja.VisPlugin.PluginRequiresVisualizationException;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.dialogs.CreateSimDialog;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.mote.BaseContikiMoteType;
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
import org.contikios.cooja.plugins.Mobility;
import org.contikios.cooja.plugins.MoteInformation;
import org.contikios.cooja.plugins.MoteInterfaceViewer;
import org.contikios.cooja.plugins.MoteTypeInformation;
import org.contikios.cooja.plugins.Notes;
import org.contikios.cooja.plugins.PowerTracker;
import org.contikios.cooja.plugins.RadioLogger;
import org.contikios.cooja.plugins.ScriptRunner;
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
import org.contikios.mrm.MRM;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

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
  public static final String CONTIKI_NG_BUILD_VERSION = "2022071901";

  private static final Logger logger = LogManager.getLogger(Cooja.class);

  public static File externalToolsUserSettingsFile = null;
  private static boolean externalToolsUserSettingsFileReadOnly = false;

  private static String specifiedCoojaPath = null;
  private static String specifiedContikiPath = null;

  // External tools setting names
  public static Properties defaultExternalToolsSettings;
  public static Properties currentExternalToolsSettings;

  private static final String[] externalToolsSettingNames = new String[] {
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
    "COMMAND_DATA_START", "COMMAND_DATA_END",
    "COMMAND_BSS_START", "COMMAND_BSS_END",
    "COMMAND_COMMON_START", "COMMAND_COMMON_END",

    "HIDE_WARNINGS"
  };

  private static GUI gui = null;

  /** The Cooja startup configuration. */
  final Config configuration;
  private Simulation mySimulation;

  private final ArrayList<Class<? extends Plugin>> menuMotePluginClasses = new ArrayList<>();
  private final ArrayList<Plugin> startedPlugins = new ArrayList<>();

  // Platform configuration variables
  // Maintained via method reparseProjectConfig()
  private ProjectConfig projectConfig;

  final ArrayList<COOJAProject> currentProjects = new ArrayList<>();

  private ClassLoader projectDirClassLoader;

  private final ArrayList<Class<? extends MoteType>> moteTypeClasses = new ArrayList<>();

  private final ArrayList<Class<? extends Plugin>> pluginClasses = new ArrayList<>();

  private final ArrayList<Class<? extends RadioMedium>> radioMediumClasses = new ArrayList<>();

  private final ArrayList<Class<? extends Positioner>> positionerClasses = new ArrayList<>();

  /**
   * Mote relation (directed).
   */
  public record MoteRelation(Mote source, Mote dest, Color color) {}
  private final ArrayList<MoteRelation> moteRelations = new ArrayList<>();

  /**
   * Creates a new Cooja Simulator GUI and ensures Swing initialization is done in the right thread.
   *
   * @param cfg Cooja configuration
   */
  public static Cooja makeCooja(Config cfg) throws ParseProjectsException {
    if (cfg.vis) {
      assert !java.awt.EventQueue.isDispatchThread() : "Call from regular context";
      return new RunnableInEDT<Cooja>() {
        @Override
        public Cooja work() {
          try {
            return new Cooja(cfg);
          } catch (ParseProjectsException e) {
            throw new RuntimeException("Could not parse projects", e);
          }
        }
      }.invokeAndWait();
    }

    return new Cooja(cfg);
  }

  /**
   * Internal constructor for Cooja.
   *
   * @param cfg Cooja configuration
   */
  private Cooja(Config cfg) throws ParseProjectsException {
    configuration = cfg;
    mySimulation = null;
    // Load default and overwrite with user settings (if any).
    loadExternalToolsDefaultSettings();
    loadExternalToolsUserSettings();

    // Register default extension directories.
    String defaultProjectDirs = getExternalToolsSetting("DEFAULT_PROJECTDIRS", null);
    if (defaultProjectDirs != null && defaultProjectDirs.length() > 0) {
      String[] arr = defaultProjectDirs.split(";");
      for (String p : arr) {
        try {
          currentProjects.add(new COOJAProject(restorePortablePath(new File(p))));
        } catch (IOException e) {
          throw new ParseProjectsException("Failed to parse project: " + p, e);
        }
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
          try {
            currentProjects.add(new COOJAProject(p));
          } catch (IOException e) {
            throw new ParseProjectsException("Failed to parse project: " + p, e);
          }
        }
      }
    }

    if (cfg.vis) {
      gui = new GUI(this);
    } else {
      parseProjectConfig();
    }
    // Shutdown hook to close running simulations.
    Runtime.getRuntime().addShutdownHook(new ShutdownHandler(this));
  }


  /**
   * Add mote highlight observer.
   *
   * @see #deleteMoteHighlightObserver(Observer)
   * @param newObserver
   *          New observer
   */
  public static void addMoteHighlightObserver(Observer newObserver) {
    if (gui != null) {
      gui.moteHighlightObservable.addObserver(newObserver);
    }
  }

  /**
   * Delete mote highlight observer.
   *
   * @see #addMoteHighlightObserver(Observer)
   * @param observer
   *          Observer to delete
   */
  public static void deleteMoteHighlightObserver(Observer observer) {
    if (gui != null) {
      gui.moteHighlightObservable.deleteObserver(observer);
    }
  }

  /**
   * @return True if simulator is visualized
   */
  public static boolean isVisualized() {
    return gui != null;
  }

  public static JFrame getTopParentContainer() {
    return GUI.frame;
  }

  /**
   * Updates GUI state based on simulation status.
   * @param stoppedSimulation True if update was triggered by a stop event.
   */
  public static void updateProgress(boolean stoppedSimulation) {
    if (gui != null) {
      gui.updateProgress(stoppedSimulation);
    }
  }

  /**
   * Enables/disables menus and menu items depending on whether a simulation is loaded etc.
   */
  static void updateGUIComponentState() {
    if (gui != null) {
      gui.updateGUIComponentState();
    }
  }

  /**
   * @return Current desktop pane (simulator visualizer)
   */
  public static JDesktopPane getDesktopPane() {
    return gui.myDesktopPane;
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

  void parseProjectConfig() throws ParseProjectsException {
    /* Build cooja configuration */
    try {
      projectConfig = new ProjectConfig(true);
    } catch (FileNotFoundException e) {
      throw new ParseProjectsException("Could not find default extension config file: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new ParseProjectsException("Error when reading default extension config file: " + e.getMessage(), e);
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
    registerPlugin(SimInformation.class);
    registerPlugin(MoteTypeInformation.class);
    registerPlugin(Visualizer.class);
    registerPlugin(LogListener.class);
    registerPlugin(TimeLine.class);
    registerPlugin(Mobility.class);
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
        removePlugin(p);
      }
    }
  }

  /**
   * Remove a plugin from working area.
   *
   * @param plugin Plugin to remove
   */
  public void removePlugin(final Plugin plugin) {
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
  }

  /**
   * Same as the {@link #startPlugin(Class, Simulation, Mote, Element)} method,
   * but does not throw exceptions. If COOJA is visualised, an error dialog
   * is shown if plugin could not be started.
   *
   * @see #startPlugin(Class, Simulation, Mote, Element)
   * @param pluginClass Plugin class
   * @param sim Plugin simulation argument
   * @param mote Plugin mote argument
   * @return Started plugin
   */
  public Plugin tryStartPlugin(Class<? extends Plugin> pluginClass, Simulation sim, Mote mote) {
    try {
      return startPlugin(pluginClass, sim, mote, null);
    } catch (PluginConstructionException ex) {
      if (Cooja.isVisualized()) {
        Cooja.showErrorDialog("Error when starting plugin", ex, false);
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

  /**
   * Starts given plugin. If visualized, the plugin is also shown.
   *
   * @see PluginType
   * @param pluginClass Plugin class
   * @param sim Plugin simulation argument
   * @param argMote Plugin mote argument
   * @param root XML root element for plugin config
   * @return Started plugin
   * @throws PluginConstructionException At errors
   */
  private Plugin startPlugin(final Class<? extends Plugin> pluginClass, Simulation sim, Mote argMote, Element root)
  throws PluginConstructionException
  {
    // Check that plugin class is registered
    if (!pluginClasses.contains(pluginClass)) {
      throw new PluginConstructionException("Tool class not registered: " + pluginClass.getName());
    }

    int pluginType = pluginClass.getAnnotation(PluginType.class).value();
    if (pluginType != PluginType.COOJA_PLUGIN && pluginType != PluginType.COOJA_STANDARD_PLUGIN && sim == null) {
      throw new PluginConstructionException("No simulation argument for plugin: " + pluginClass.getName());
    }
    if (pluginType == PluginType.MOTE_PLUGIN && argMote == null) {
      throw new PluginConstructionException("No mote argument for mote plugin: " + pluginClass.getName());
    }
    if (!isVisualized() && VisPlugin.class.isAssignableFrom(pluginClass)) {
      throw new PluginConstructionException("Plugin " + pluginClass.getName() + " requires visualization");
    }

    // Construct plugin depending on plugin type
    Plugin plugin;
    try {
      plugin = switch (pluginType) {
        case PluginType.MOTE_PLUGIN -> pluginClass.getConstructor(Mote.class, Simulation.class, Cooja.class)
                .newInstance(argMote, sim, this);
        case PluginType.SIM_PLUGIN, PluginType.SIM_STANDARD_PLUGIN, PluginType.SIM_CONTROL_PLUGIN ->
                pluginClass.getConstructor(Simulation.class, Cooja.class).newInstance(sim, this);
        case PluginType.COOJA_PLUGIN, PluginType.COOJA_STANDARD_PLUGIN ->
                pluginClass.getConstructor(Cooja.class).newInstance(this);
        default -> throw new PluginConstructionException("Bad plugin type: " + pluginType);
      };
    } catch (PluginRequiresVisualizationException e) {
      throw new PluginConstructionException("Tool class requires visualization: " + pluginClass.getName(), e);
    } catch (Exception e) {
      throw new PluginConstructionException("Construction error for tool of class: " + pluginClass.getName(), e);
    }

    if (root != null) {
      for (var cfg : root.getChildren("plugin_config")) {
        if (!plugin.setConfigXML(((Element)cfg).getChildren(), isVisualized())) {
          throw new PluginConstructionException("Failed to set config for " + pluginClass.getName());
        }
      }
    }

    plugin.startPlugin();

    // Add to active plugins list
    startedPlugins.add(plugin);
    updateGUIComponentState();

    // Show plugin if visualizer type
    final var pluginFrame = plugin.getCooja();
    if (pluginFrame != null) {
      // If plugin is visualizer plugin, parse visualization arguments
      new RunnableInEDT<Boolean>() {
        private static final int FRAME_STANDARD_WIDTH = 150;

        private static final int FRAME_STANDARD_HEIGHT = 300;

        private static final int FRAME_NEW_OFFSET = 30;

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
                final var pluginGUI = plugin.getCooja();
                if (Boolean.parseBoolean(cfgElem.getText()) && pluginGUI != null) {
                  SwingUtilities.invokeLater(() -> {
                    try {
                      pluginGUI.setIcon(true);
                    } catch (PropertyVetoException e) {
                    }
                  });
                }
              }
            }
          }

          gui.myDesktopPane.add(pluginFrame);

          // Set size if not already specified by plugin.
          if (pluginFrame.getWidth() <= 0 || pluginFrame.getHeight() <= 0) {
            pluginFrame.setSize(FRAME_STANDARD_WIDTH, FRAME_STANDARD_HEIGHT);
          }
          // Set location if not already set.
          if (pluginFrame.getLocation().x <= 0 && pluginFrame.getLocation().y <= 0) {
            var iframes = gui.myDesktopPane.getAllFrames();
            Point topFrameLoc = iframes.length > 1
                    ? iframes[1].getLocation()
                    : new Point(gui.myDesktopPane.getSize().width / 2, gui.myDesktopPane.getSize().height / 2);
            pluginFrame.setLocation(new Point(topFrameLoc.x + FRAME_NEW_OFFSET, topFrameLoc.y + FRAME_NEW_OFFSET));
          }
          pluginFrame.setVisible(true);

          // Select plugin.
          try {
            for (var existingPlugin : gui.myDesktopPane.getAllFrames()) {
              existingPlugin.setSelected(false);
            }
            pluginFrame.setSelected(true);
          } catch (Exception e) {
          }
          gui.myDesktopPane.moveToFront(pluginFrame);
          return true;
        }
      }.invokeAndWait();
    }

    return plugin;
  }

  List<Class<? extends Plugin>> getMenuMotePluginClasses() {
    return menuMotePluginClasses;
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

  public boolean hasStartedPlugins() {
    return !startedPlugins.isEmpty();
  }

  public Plugin[] getStartedPlugins() {
    return startedPlugins.toArray(new Plugin[0]);
  }

  static boolean isMotePluginCompatible(Class<? extends Plugin> motePluginClass, Mote mote) {
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

  public static JMenu createMotePluginsSubmenu(Class<? extends Plugin> pluginClass) {
    return gui.createMotePluginsSubmenu(pluginClass);
  }

  /**
   * Return a mote plugins submenu for given mote.
   *
   * @param mote Mote
   * @return Mote plugins menu
   */
  public static JMenu createMotePluginsSubmenu(Mote mote) {
    return gui.createMotePluginsSubmenu(mote);
  }

  /**
   * @return Current simulation
   */
  public Simulation getSimulation() {
    return mySimulation;
  }

  void setSimulation(Simulation sim) {
    mySimulation = sim;
    updateGUIComponentState();

    // Set frame title
    if (gui != null) {
      GUI.frame.setTitle(sim.getTitle() + " - " + WINDOW_TITLE);
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
  boolean doRemoveSimulation(boolean askForConfirmation) {

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
        removePlugin(startedPlugin);
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
      GUI.frame.setTitle(WINDOW_TITLE);
    }

    setChanged();
    notifyObservers();

    return true;
  }

  /**
   * Save current simulation configuration to disk
   */
  public File doSaveConfig() {
    mySimulation.stopSimulation();
    return gui.doSaveConfig();
  }

  /**
   * Quit program
   *
   * @param askForConfirmation Should we ask for confirmation before quitting?
   */
  public void doQuit(boolean askForConfirmation) {
    if (getSimulation() != null && askForConfirmation) { // Save?
      Object[] opts = {"Yes", "No", "Cancel"};
      int n = JOptionPane.showOptionDialog(GUI.frame, "Do you want to save the current simulation?", WINDOW_TITLE,
              JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]);
      if (n == JOptionPane.CANCEL_OPTION || n == JOptionPane.YES_OPTION && doSaveConfig() == null) {
        return;
      }
    }
    doQuit(0);
  }

  public void doQuit(int exitCode) {
    // Clean up resources. Catch all exceptions to ensure that System.exit will be called.
    try {
      doRemoveSimulation(false);
      for (var plugin : startedPlugins.toArray(new Plugin[0])) {
        removePlugin(plugin);
      }
    } catch (Exception e) {
      logger.error("Failed to remove simulation/plugins on shutdown.", e);
    }

    /* Store frame size and position */
    if (isVisualized()) {
      setExternalToolsSetting("FRAME_SCREEN", GUI.frame.getGraphicsConfiguration().getDevice().getIDstring());
      setExternalToolsSetting("FRAME_POS_X", String.valueOf(GUI.frame.getLocationOnScreen().x));
      setExternalToolsSetting("FRAME_POS_Y", String.valueOf(GUI.frame.getLocationOnScreen().y));

      var maximized = GUI.frame.getExtendedState() == JFrame.MAXIMIZED_BOTH;
      setExternalToolsSetting("FRAME_WIDTH", String.valueOf(maximized ? Integer.MAX_VALUE : GUI.frame.getWidth()));
      setExternalToolsSetting("FRAME_HEIGHT", String.valueOf(maximized ? Integer.MAX_VALUE : GUI.frame.getHeight()));
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
    Properties settings = new Properties();
    settings.put("PATH_COOJA", "./");
    settings.put("PATH_CONTIKI", "../../");
    settings.put("PATH_MAKE", "make");
    settings.put("PATH_C_COMPILER", "gcc");
    settings.put("COMPILER_ARGS", "");
    settings.put("DEFAULT_PROJECTDIRS", "");

    settings.put("PARSE_WITH_COMMAND", "false");
    settings.put("READELF_COMMAND", "readelf -W --symbols $(LIBFILE)");

    settings.put("PARSE_COMMAND", "nm -aP $(LIBFILE)");
    settings.put("COMMAND_VAR_NAME_ADDRESS_SIZE", "^(?<symbol>[^.].*?) <SECTION> (?<address>[0-9a-fA-F]+) (?<size>[0-9a-fA-F])*");
    settings.put("COMMAND_VAR_SEC_DATA", "[DdGg]");
    settings.put("COMMAND_VAR_SEC_BSS", "[Bb]");
    settings.put("COMMAND_VAR_SEC_COMMON", "[C]");
    settings.put("COMMAND_VAR_SEC_READONLY", "[Rr]");
    settings.put("COMMAND_DATA_START", "^.data[ \t]d[ \t]([0-9A-Fa-f]*)[ \t]*$");
    settings.put("COMMAND_DATA_END", "^_edata[ \t]D[ \t]([0-9A-Fa-f]*)[ \t]*$");
    settings.put("COMMAND_BSS_START", "^__bss_start[ \t]B[ \t]([0-9A-Fa-f]*)[ \t]*$");
    settings.put("COMMAND_BSS_END", "^_end[ \t]B[ \t]([0-9A-Fa-f]*)[ \t]*$");
    settings.put("COMMAND_READONLY_START", "^.rodata[ \t]r[ \t]([0-9A-Fa-f]*)[ \t]*$");
    settings.put("COMMAND_READONLY_END", "^.eh_frame_hdr[ \t]r[ \t]([0-9A-Fa-f]*)[ \t]*$");

    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.startsWith("win")) {
      settings.put("PATH_C_COMPILER", "mingw32-gcc");
      settings.put("PARSE_WITH_COMMAND", "true");

      // Hack: nm with arguments -S --size-sort does not display __data_start symbols
      settings.put("PARSE_COMMAND", "/bin/nm -aP --size-sort -S $(LIBFILE) && /bin/nm -aP $(LIBFILE)");

      settings.put("COMMAND_VAR_NAME_ADDRESS_SIZE", "^[_](?<symbol>[^.].*?)[ \t]<SECTION>[ \t](?<address>[0-9a-fA-F]+)[ \t](?<size>[0-9a-fA-F]+)");
      settings.put("COMMAND_DATA_START", "^__data_start__[ \t]D[ \t]([0-9A-Fa-f]*)");
      settings.put("COMMAND_DATA_END", "^__data_end__[ \t]D[ \t]([0-9A-Fa-f]*)");
      settings.put("COMMAND_BSS_START", "^__bss_start__[ \t]B[ \t]([0-9A-Fa-f]*)");
      settings.put("COMMAND_BSS_END", "^__bss_end__[ \t]B[ \t]([0-9A-Fa-f]*)");
      settings.put("COMMAND_READONLY_START", "^.rodata[ \t]r[ \t]([0-9A-Fa-f]*)");
      settings.put("COMMAND_READONLY_END", "^.eh_frame_hdr[ \t]r[ \t]([0-9A-Fa-f]*)");
    } else if (osName.startsWith("mac os x")) {
      settings.put("PARSE_WITH_COMMAND", "true");
      settings.put("PARSE_COMMAND", "[COOJA_DIR]/tools/macos/nmandsize $(LIBFILE)");
      settings.put("COMMAND_VAR_NAME_ADDRESS", "^[ \t]*([0-9A-Fa-f][0-9A-Fa-f]*)[ \t]\\(__DATA,__[^ ]*\\) external _([^ ]*)$");
      settings.put("COMMAND_DATA_START", "^DATA SECTION START: 0x([0-9A-Fa-f]+)$");
      settings.put("COMMAND_DATA_END", "^DATA SECTION END: 0x([0-9A-Fa-f]+)$");
      settings.put("COMMAND_BSS_START", "^COMMON SECTION START: 0x([0-9A-Fa-f]+)$");
      settings.put("COMMAND_BSS_END", "^COMMON SECTION END: 0x([0-9A-Fa-f]+)$");
      settings.put("COMMAND_COMMON_START", "^BSS SECTION START: 0x([0-9A-Fa-f]+)$");
      settings.put("COMMAND_COMMON_END", "^BSS SECTION END: 0x([0-9A-Fa-f]+)$");

      settings.put("COMMAND_VAR_NAME_ADDRESS_SIZE", "^\\s*0x(?<address>[a-fA-F0-9]+) \\(\\s*0x(?<size>[a-fA-F0-9]+)\\) (?<symbol>[A-Za-z0-9_]+) \\[.*EXT.*\\]");
      settings.put("COMMAND_VAR_SEC_DATA", "(__DATA,__data)");
      settings.put("COMMAND_VAR_SEC_BSS", "(__DATA,__bss)");
      settings.put("COMMAND_VAR_SEC_COMMON", "(__DATA,__common)");
    } else if (osName.startsWith("freebsd")) {
      settings.put("PATH_MAKE", "gmake");
    } else {
      if (!osName.startsWith("linux")) {
        logger.warn("Unknown system: " + osName + ", using Linux settings");
      }
    }
    currentExternalToolsSettings = settings;
    defaultExternalToolsSettings = (Properties) currentExternalToolsSettings.clone();
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
   * @param config Cooja configuration
   */
  public static void go(Config config) {
    externalToolsUserSettingsFileReadOnly = config.externalToolsConfig != null;
    if (config.externalToolsConfig == null) {
      externalToolsUserSettingsFile = new File(System.getProperty("user.home"), ".cooja.user.properties");
    } else {
      externalToolsUserSettingsFile = new File(config.externalToolsConfig);
    }

    specifiedContikiPath = config.contikiPath;
    specifiedCoojaPath = config.coojaPath;

    Cooja gui = null;
    try {
      gui = makeCooja(config);
    } catch (Exception e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    // Check if simulator should be quick-started.
    int rv = 0;
    for (var simConfig : config.configs) {
      var file = new File(simConfig.file);
      Simulation sim = null;
      try {
        sim = config.vis
                ? Cooja.gui.doLoadConfig(file, config.updateSim, config.randomSeed)
                : gui.loadSimulationConfig(file, true, false, config.randomSeed);
      } catch (Exception e) {
        logger.fatal("Exception when loading simulation: ", e);
      }
      if (sim == null) {
        System.exit(1);
      }
      if (!config.vis) {
        sim.setSpeedLimit(null);
        var ret = sim.startSimulation(true);
        if (ret == null) {
          logger.info("TEST OK\n");
        } else {
          logger.warn("TEST FAILED\n");
          rv = Math.max(rv, ret);
        }
      }
    }
    if (!config.configs.isEmpty() && (!config.vis || config.updateSim)) {
      gui.doQuit(rv);
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
  Simulation loadSimulationConfig(File file, boolean quick, boolean rewriteCsc, Long manualRandomSeed)
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
  Simulation createSimulation(Element root, boolean quick, boolean rewriteCsc, Long manualRandomSeed)
  throws SimulationCreationException {
    boolean projectsOk = verifyProjects(root);

    // GENERATE UNIQUE MOTE TYPE IDENTIFIERS.

    // Locate Contiki mote types in config.
    var readNames = new ArrayList<String>();
    var moteTypes = root.getDescendants(new ElementFilter("motetype"));
    while (moteTypes.hasNext()) {
      var e = (Element)moteTypes.next();
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
        String newID = BaseContikiMoteType.generateUniqueMoteTypeID("mtype", reserved);
        moteTypeIDMappings.put(existingIdentifier, newID);
        reserved.add(newID);
      }

      // Replace all <motetype>..ContikiMoteType.class<identifier>mtypeXXX</identifier>...
      // in the config with the new identifiers.
      moteTypes = root.getDescendants(new ElementFilter("motetype"));
      while (moteTypes.hasNext()) {
        var e = (Element) moteTypes.next();
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
    var simCfg = root.getChild("simulation");
    var title = simCfg.getChild("title").getText();
    var cfgSeed = simCfg.getChild("randomseed").getText();
    long seed = manualRandomSeed != null ? manualRandomSeed
            : "generated".equals(cfgSeed) ? new Random().nextLong() : Long.parseLong(cfgSeed);
    var medium = simCfg.getChild("radiomedium").getText().trim();
    var cfgDelay = simCfg.getChild("motedelay");
    long delay = cfgDelay == null
            ? Integer.parseInt(simCfg.getChild("motedelay_us").getText())
            : Integer.parseInt(cfgDelay.getText()) * Simulation.MILLISECOND;
    if (Cooja.isVisualized() && !quick) {
      var cfg = CreateSimDialog.showDialog(this, new Simulation.SimConfig(title, medium,
              "generated".equals(cfgSeed), seed, delay));
      if (cfg == null) return null;
      title = cfg.title();
      seed = cfg.randomSeed();
      medium = cfg.radioMedium();
      delay = cfg.moteStartDelay();
    }
    Simulation newSim;
    try {
      newSim = new Simulation(this, title, configuration.logDir, seed, medium, delay, root.getChildren("plugin"));
      if (!newSim.setConfigXML(simCfg, quick)) {
        logger.info("Simulation not loaded");
        return null;
      }
    } catch (MoteTypeCreationException e) {
      throw new SimulationCreationException("Unknown error: " + e.getMessage(), e);
    }

    // Restart plugins from config
    for (var e : root.getChildren("plugin")) {
      final Element pluginElement = (Element) e;
      // Read plugin class
      String pluginClassName = pluginElement.getText().trim();
      if (pluginClassName.startsWith("se.sics")) {
        pluginClassName = pluginClassName.replaceFirst("se\\.sics", "org.contikios");
      }
      // Skip SimControl, functionality is now in Cooja class.
      if ("org.contikios.cooja.plugins.SimControl".equals(pluginClassName)) {
        continue;
      }
      // Backwards compatibility: old visualizers were replaced.
      if (pluginClassName.equals("org.contikios.cooja.plugins.VisUDGM") ||
              pluginClassName.equals("org.contikios.cooja.plugins.VisBattery") ||
              pluginClassName.equals("org.contikios.cooja.plugins.VisTraffic") ||
              pluginClassName.equals("org.contikios.cooja.plugins.VisState")) {
        logger.warn("Old simulation config detected: visualizers have been remade");
        pluginClassName = "org.contikios.cooja.plugins.Visualizer";
      }

      var pluginClass = tryLoadClass(this, Plugin.class, pluginClassName);
      if (pluginClass == null) {
        logger.fatal("Could not load plugin class: " + pluginClassName);
        throw new SimulationCreationException("Could not load plugin class " + pluginClassName, null);
      }
      // Skip plugins that require visualization in headless mode.
      if (!isVisualized() && VisPlugin.class.isAssignableFrom(pluginClass)) {
        continue;
      }
      // Parse plugin mote argument (if any)
      Mote mote = null;
      for (var pluginSubElement : pluginElement.getChildren("mote_arg")) {
        int moteNr = Integer.parseInt(((Element) pluginSubElement).getText());
        if (moteNr >= 0 && moteNr < newSim.getMotesCount()) {
          mote = newSim.getMote(moteNr);
        }
      }
      try {
        startPlugin(pluginClass, newSim, mote, pluginElement);
      } catch (PluginConstructionException ex) {
        throw new SimulationCreationException("Failed to start plugin: " + ex.getMessage(), ex);
      }
    }

    if (isVisualized()) { // Z order visualized plugins.
      for (int z = 0; z < getDesktopPane().getAllFrames().length; z++) {
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
            try {
              plugin.setSelected(true);
            } catch (Exception e) {
              logger.error("Could not select plugin {}", plugin.getTitle());
            }
          }
          plugin.putClientProperty("zorder", null);
          break;
        }
      }
      getDesktopPane().repaint();
    } else { // Non-GUI Cooja requires a simulation controller, ensure one is started.
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
   void saveSimulationConfig(File file) {
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

  /** Returns a root element containing the simulation config. */
  Element extractSimulationConfig() {
    // Create simulation config
    Element root = new Element("simconf");

    /* Store extension directories meta data */
    for (COOJAProject project: currentProjects) {
      Element projectElement = new Element("project");
      projectElement.addContent(createPortablePath(project.dir).getPath().replaceAll("\\\\", "/"));
      root.addContent(projectElement);
    }

    Element simulationElement = new Element("simulation");
    simulationElement.addContent(mySimulation.getConfigXML());
    root.addContent(simulationElement);

    // Create started plugins config
    ArrayList<Element> config = new ArrayList<>();
    for (Plugin startedPlugin : startedPlugins) {
      int pluginType = startedPlugin.getClass().getAnnotation(PluginType.class).value();

      // Ignore GUI plugins
      if (pluginType == PluginType.COOJA_PLUGIN || pluginType == PluginType.COOJA_STANDARD_PLUGIN) {
        continue;
      }

      var pluginElement = new Element("plugin");
      pluginElement.setText(startedPlugin.getClass().getName());

      // Create mote argument config (if mote plugin)
      if (pluginType == PluginType.MOTE_PLUGIN) {
        var pluginSubElement = new Element("mote_arg");
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
        var pluginSubElement = new Element("plugin_config");
        pluginSubElement.addContent(pluginXML);
        pluginElement.addContent(pluginSubElement);
      }

      // If plugin is visualizer plugin, create visualization arguments
      if (startedPlugin.getCooja() != null) {
        JInternalFrame pluginFrame = startedPlugin.getCooja();

        var pluginSubElement = new Element("width");
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
    root.addContent(config);
    return root;
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
   * @param title          Title of error window
   * @param exception      Exception causing window to be shown
   * @param retryAvailable If true, a retry option is presented
   * @return Retry failed operation
   */
  public static boolean showErrorDialog(final String title, final Throwable exception, final boolean retryAvailable) {
    return GUI.showErrorDialog(title, exception, retryAvailable);
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
  public static void signalMoteHighlight(Mote m) {
    if (gui != null) {
      gui.moteHighlightObservable.setChangedAndNotify(m);
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
    if (source == null || dest == null || gui == null) {
      return;
    }
    removeMoteRelation(source, dest); /* Unique relations */
    moteRelations.add(new MoteRelation(source, dest, color));
    gui.moteRelationObservable.setChangedAndNotify();
  }

  /**
   * Removes the relations between given motes.
   *
   * @param source Source mote
   * @param dest Destination mote
   */
  public void removeMoteRelation(Mote source, Mote dest) {
    if (source == null || dest == null || gui == null) {
      return;
    }
    MoteRelation[] arr = getMoteRelations();
    for (MoteRelation r: arr) {
      if (r.source == source && r.dest == dest) {
        moteRelations.remove(r);
        /* Relations are unique */
        gui.moteRelationObservable.setChangedAndNotify();
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
  public static void addMoteRelationsObserver(Observer newObserver) {
    if (gui != null) {
      gui.moteRelationObservable.addObserver(newObserver);
    }
  }

  /**
   * Removes mote relation observer.
   * Typically, used by visualizer plugins.
   *
   * @param observer Observer
   */
  public static void deleteMoteRelationsObserver(Observer observer) {
    if (gui != null) {
      gui.moteRelationObservable.deleteObserver(observer);
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
    int elem = PATH_IDENTIFIER.length;
    File[] path = new File[elem];
    String[] canonicals = new String[elem];
    int match = -1;
    // Not so nice, but goes along with GUI.getExternalToolsSetting
    String defp = Cooja.getExternalToolsSetting("PATH_COOJA", null);
    String fileCanonical;
    try {
      int mlength = 0;
      fileCanonical = file.getCanonicalPath();
      for (int i = 0; i < elem; i++) {
        path[i] = new File(Cooja.getExternalToolsSetting(PATH_IDENTIFIER[i][1], defp + PATH_IDENTIFIER[i][2]));
        canonicals[i] = path[i].getCanonicalPath();
        if (fileCanonical.startsWith(canonicals[i])) {
          if (mlength < canonicals[i].length()) {
            mlength = canonicals[i].length();
            match = i;
          }
        }
      }
    } catch (IOException e1) {
      return null;
    }
    if (match == -1) return null;
    // Replace Contiki's canonical path with Contiki identifier.
    File portable = new File(fileCanonical.replaceFirst(
            Matcher.quoteReplacement(canonicals[match]),
            Matcher.quoteReplacement(PATH_IDENTIFIER[match][0])));
    // Verify conversion.
    File verify = restoreContikiRelativePath(portable);
    if (verify == null || !verify.exists()) {
      // Error: did file even exist pre-conversion?
      return null;
    }
    return portable;
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

  public static void setProgressMessage(String msg) {
    setProgressMessage(msg, MessageListUI.NORMAL);
  }
  public static void setProgressMessage(String msg, int type) {
    if (gui != null) {
      GUI.setProgressMessage(msg, type);
    }
  }

  /**
   * Load quick help for given object or identifier. Note that this method does not
   * show the quick help pane.
   *
   * @param obj If string: help identifier. Else, the class name of the argument
   * is used as help identifier.
   */
  public static void loadQuickHelp(final Object obj) {
    if (obj != null) {
      gui.loadQuickHelp(obj);
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
        simulation.stopSimulation();
      }
    }
  }

  /** Structure to hold the simulation parameters. */
  public record SimConfig(Map<String, String> opts, String file) {}

  /** Structure to hold the Cooja startup configuration. */
  public record Config(boolean vis, Long randomSeed, String externalToolsConfig, boolean updateSim,
                       String logDir, String contikiPath, String coojaPath, String javac,
                       List<SimConfig> configs) {}
}
