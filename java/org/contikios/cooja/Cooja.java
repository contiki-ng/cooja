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
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.MoteType.MoteTypeCreationException;
import org.contikios.cooja.VisPlugin.PluginRequiresVisualizationException;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.dialogs.CreateSimDialog;
import org.contikios.cooja.dialogs.MessageListUI;
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
   *  Version used to detect incompatibility with the Cooja simulation config files.
   *  The format is &lt;YYYY&gt;&lt;MM&gt;&lt;DD&gt;&lt;2 digit sequence number&gt;.
   */
  public static final String SIMULATION_CONFIG_VERSION = "2022112801";

  /**
   *  Version used to detect incompatibility with the Contiki-NG
   *  build system. The format is &lt;YYYY&gt;&lt;MM&gt;&lt;DD&gt;&lt;2 digit sequence number&gt;.
   */
  public static final String CONTIKI_NG_BUILD_VERSION = "2022071901";

  private static final Logger logger = LogManager.getLogger(Cooja.class);

  private static final String PATH_CONFIG_IDENTIFIER = "[CONFIG_DIR]";

  private static final String[][] PATH_IDENTIFIER = {
          {"[CONTIKI_DIR]","PATH_CONTIKI"},
          {"[COOJA_DIR]","PATH_COOJA"},
          {"[APPS_DIR]","PATH_APPS"}
  };

  public static File externalToolsUserSettingsFile = null;
  private File currentConfigFile = null; /* Used to generate config relative paths */

  // External tools setting names
  private static final Properties defaultExternalToolsSettings = getExternalToolsDefaultSettings();
  private static Properties currentExternalToolsSettings;

  static GUI gui = null;

  /** The Cooja startup configuration. */
  public static Config configuration = null;

  /** Used mote type IDs. Used by mote types to ensure uniqueness during Cooja lifetime. */
  public static final Set<String> usedMoteTypeIDs = new HashSet<>();

  private Simulation mySimulation = null;

  private final ArrayList<Plugin> startedPlugins = new ArrayList<>();

  // Platform configuration variables
  // Maintained via method parseProjectConfig()
  private ProjectConfig projectConfig;

  final ArrayList<COOJAProject> currentProjects = new ArrayList<>();

  private ClassLoader projectDirClassLoader;

  private final ArrayList<Class<? extends MoteType>> moteTypeClasses = new ArrayList<>();

  private final ArrayList<Class<? extends Plugin>> pluginClasses = new ArrayList<>();

  private final ArrayList<Class<? extends RadioMedium>> radioMediumClasses = new ArrayList<>();

  private final ArrayList<Class<? extends Positioner>> positionerClasses = new ArrayList<>();

  /**
   * Creates a new Cooja Simulator GUI and ensures Swing initialization is done in the right thread.
   */
  public static Cooja makeCooja() throws ParseProjectsException {
    if (configuration.vis) {
      assert !java.awt.EventQueue.isDispatchThread() : "Call from regular context";
      return new RunnableInEDT<Cooja>() {
        @Override
        public Cooja work() {
          GUI.setLookAndFeel();
          try {
            return new Cooja();
          } catch (ParseProjectsException e) {
            throw new RuntimeException("Could not parse projects", e);
          }
        }
      }.invokeAndWait();
    }

    return new Cooja();
  }

  /**
   * Internal constructor for Cooja.
   */
  private Cooja() throws ParseProjectsException {
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

    if (configuration.vis) {
      gui = new GUI(this);
      // Allocate GUI before parsing project config, otherwise gui is null and
      // gui.menuMotePluginClasses becomes empty.
      gui.parseProjectConfig();
      // Start all standard GUI plugins
      for (var pluginClass : getRegisteredPlugins()) {
        var pluginType = pluginClass.getAnnotation(PluginType.class).value();
        if (pluginType == PluginType.PType.COOJA_STANDARD_PLUGIN) {
          tryStartPlugin(pluginClass, null, null);
        }
      }
    } else {
      parseProjectConfig();
    }
    // Shutdown hook to stop running simulations.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (mySimulation != null) {
        mySimulation.stopSimulation();
      }
    }));
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
      java.awt.EventQueue.invokeLater(() -> gui.updateProgress(stoppedSimulation));
    }
  }

  /**
   * Enables/disables menus and menu items depending on whether a simulation is loaded etc.
   */
  static void updateGUIComponentState() {
    if (gui != null) {
      java.awt.EventQueue.invokeLater(() -> gui.updateGUIComponentState());
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
   * @param positionerClass Class to register
   */
  public void registerPositioner(Class<? extends Positioner> positionerClass) {
    positionerClasses.add(positionerClass);
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
   * @param radioMediumClass Class to register
   */
  public void registerRadioMedium(Class<? extends RadioMedium> radioMediumClass) {
    radioMediumClasses.add(radioMediumClass);
  }

  /**
   * @return All registered radio medium classes
   */
  public List<Class<? extends RadioMedium>> getRegisteredRadioMediums() {
    return radioMediumClasses;
  }

  void clearProjectConfig() {
    /* Remove current dependencies */
    moteTypeClasses.clear();
    pluginClasses.clear();
    positionerClasses.clear();
    radioMediumClasses.clear();
    projectDirClassLoader = null;
  }

  /**
   * Builds extension configuration using extension directories settings.
   * Registers mote types, plugins, positioners and radio mediums.
   */
  void parseProjectConfig() throws ParseProjectsException {
    /* Build cooja configuration */
    projectConfig = new ProjectConfig(true);
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
      var parent = ClassLoader.getSystemClassLoader();
      try {
        if (currentProjects.isEmpty()) {
          projectDirClassLoader = parent;
        } else { // Create class loader from JARs.
          ArrayList<URL> urls = new ArrayList<>();
          for (var project : currentProjects) {
            File projectDir = project.dir;
            try {
              urls.add(new File(projectDir, "java").toURI().toURL());
              // Read configuration to check if any JAR files should be loaded
              var projectConfig = new ProjectConfig(false);
              projectConfig.appendProjectDir(projectDir);
              var projectJarFiles = projectConfig.getStringArrayValue(Cooja.class, "JARFILES");
              if (projectJarFiles != null) {
                for (String jarfile : projectJarFiles) {
                  File jarpath = findJarFile(projectDir, jarfile);
                  if (jarpath == null) {
                    throw new FileNotFoundException(jarfile);
                  }
                  urls.add(jarpath.toURI().toURL());
                }
              }
            } catch (Exception e) {
              logger.fatal("Error when trying to read JAR-file in " + projectDir + ": " + e);
              throw new ClassLoaderCreationException("Error when trying to read JAR-file in " + projectDir, e);
            }
          }
          projectDirClassLoader = URLClassLoader.newInstance(urls.toArray(new URL[0]), parent);
        }
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
      logger.fatal("Error when starting plugin", ex);
      if (Cooja.isVisualized()) {
        Cooja.showErrorDialog("Error when starting plugin", ex, false);
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
  Plugin startPlugin(final Class<? extends Plugin> pluginClass, Simulation sim, Mote argMote, Element root)
  throws PluginConstructionException
  {
    // Check that plugin class is registered
    if (!pluginClasses.contains(pluginClass)) {
      throw new PluginConstructionException("Tool class not registered: " + pluginClass.getName());
    }

    var pluginType = pluginClass.getAnnotation(PluginType.class).value();
    if (pluginType != PluginType.PType.COOJA_PLUGIN && pluginType != PluginType.PType.COOJA_STANDARD_PLUGIN && sim == null) {
      throw new PluginConstructionException("No simulation argument for plugin: " + pluginClass.getName());
    }
    if (pluginType == PluginType.PType.MOTE_PLUGIN && argMote == null) {
      throw new PluginConstructionException("No mote argument for mote plugin: " + pluginClass.getName());
    }
    if (!isVisualized() && VisPlugin.class.isAssignableFrom(pluginClass)) {
      throw new PluginConstructionException("Plugin " + pluginClass.getName() + " requires visualization");
    }

    // Construct plugin depending on plugin type
    Plugin plugin;
    try {
      plugin = switch (pluginType) {
        case MOTE_PLUGIN -> pluginClass.getConstructor(Mote.class, Simulation.class, Cooja.class)
                .newInstance(argMote, sim, this);
        case SIM_PLUGIN, SIM_STANDARD_PLUGIN, SIM_CONTROL_PLUGIN ->
                pluginClass.getConstructor(Simulation.class, Cooja.class).newInstance(sim, this);
        case COOJA_PLUGIN, COOJA_STANDARD_PLUGIN ->
                pluginClass.getConstructor(Cooja.class).newInstance(this);
      };
    } catch (PluginRequiresVisualizationException e) {
      throw new PluginConstructionException("Tool class requires visualization: " + pluginClass.getName(), e);
    } catch (Exception e) {
      throw new PluginConstructionException("Construction error for tool of class: " + pluginClass.getName(), e);
    }

    if (root != null) {
      for (var cfg : root.getChildren("plugin_config")) {
        if (!plugin.setConfigXML(cfg.getChildren(), isVisualized())) {
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
            var location = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
            var size = new Dimension();
            int zOrder = 0;
            boolean minimized = false;
            for (var cfgElem : root.getChildren()) {
              switch (cfgElem.getName()) {
                case "width" -> size.width = Integer.parseInt(cfgElem.getText());
                case "height" -> size.height = Integer.parseInt(cfgElem.getText());
                case "z" -> zOrder = Integer.parseInt(cfgElem.getText());
                case "location_x" -> location.x = Integer.parseInt(cfgElem.getText());
                case "location_y" -> location.y = Integer.parseInt(cfgElem.getText());
                case "minimized" -> minimized = Boolean.parseBoolean(cfgElem.getText());
                case "bounds" -> {
                  location.x = getAttributeAsInt(cfgElem, "x", location.x);
                  location.y = getAttributeAsInt(cfgElem, "y", location.y);
                  size.width = getAttributeAsInt(cfgElem, "width", size.width);
                  size.height = getAttributeAsInt(cfgElem, "height", size.height);
                  zOrder = getAttributeAsInt(cfgElem, "z", zOrder);
                  minimized = minimized || Boolean.parseBoolean(cfgElem.getAttributeValue("minimized"));
                }
              }
            }
            if (size.width > 0 && size.height > 0) {
              pluginFrame.setSize(size);
            }
            if (location.x != Integer.MIN_VALUE && location.y != Integer.MIN_VALUE) {
              pluginFrame.setLocation(location);
            }
            if (zOrder != Integer.MIN_VALUE) {
              pluginFrame.putClientProperty("zorder", zOrder);
            }
            if (minimized) {
              SwingUtilities.invokeLater(() -> {
                try {
                  pluginFrame.setIcon(true);
                } catch (PropertyVetoException e1) {
                }
              });
            }
          }

          gui.myDesktopPane.add(pluginFrame);

          // Set size if not already specified by plugin.
          if (pluginFrame.getWidth() <= 0 || pluginFrame.getHeight() <= 0) {
            pluginFrame.setSize(FRAME_STANDARD_WIDTH, FRAME_STANDARD_HEIGHT);
          }
          // Set location if not already set.
          if (pluginFrame.getLocation().x < 0 && pluginFrame.getLocation().y < 0) {
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

  private static int getAttributeAsInt(Element element, String name, int defaultValue) {
    String v = element.getAttributeValue(name);
    return v == null ? defaultValue : Integer.parseInt(v);
  }

  /**
   * Unregister a plugin class. Removes any plugin menu items links as well.
   *
   * @param pluginClass Plugin class
   */
  public void unregisterPlugin(Class<? extends Plugin> pluginClass) {
    pluginClasses.remove(pluginClass);
    if (gui != null) {
      gui.menuMotePluginClasses.remove(pluginClass);
    }
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
      case MOTE_PLUGIN:
        if (gui != null) {
          gui.menuMotePluginClasses.add(pluginClass);
        }
      case COOJA_PLUGIN:
      case COOJA_STANDARD_PLUGIN:
      case SIM_PLUGIN:
      case SIM_STANDARD_PLUGIN:
      case SIM_CONTROL_PLUGIN:
        pluginClasses.add(pluginClass);
        return true;
    }
    logger.fatal("Could not register plugin, " + pluginClass + " has unknown plugin type");
    return false;
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
   * @return True if no simulation exists when method returns
   */
  boolean doRemoveSimulation() {
    if (mySimulation == null) {
      return true;
    }

    // Close all started non-GUI plugins
    for (var startedPlugin : startedPlugins.toArray(new Plugin[0])) {
      var pluginType = startedPlugin.getClass().getAnnotation(PluginType.class).value();
      if (pluginType != PluginType.PType.COOJA_PLUGIN && pluginType != PluginType.PType.COOJA_STANDARD_PLUGIN) {
        removePlugin(startedPlugin);
      }
    }

    // Delete simulation
    mySimulation.removed();
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
  public static File doSaveConfig() {
    return gui.doSaveConfig();
  }

  public void doQuit(int exitCode) {
    // Clean up resources. Catch all exceptions to ensure that System.exit will be called.
    try {
      doRemoveSimulation();
      for (var plugin : startedPlugins.toArray(new Plugin[0])) {
        removePlugin(plugin);
      }
    } catch (Exception e) {
      logger.error("Failed to remove simulation/plugins on shutdown.", e);
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
    if (configuration.contikiPath != null && "PATH_CONTIKI".equals(name)) {
      return configuration.contikiPath;
    }
    if (configuration.coojaPath != null && "PATH_COOJA".equals(name)) {
      return configuration.coojaPath;
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

  /** Set the external tools settings to default. */
  public static void resetExternalToolsSettings() {
    currentExternalToolsSettings = (Properties) defaultExternalToolsSettings.clone();
  }

  /** Get default external tools settings. */
  private static Properties getExternalToolsDefaultSettings() {
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
    return settings;
  }

  /** Returns the external tools settings that differ from default. */
  public static Properties getDifferingExternalToolsSettings() {
    var differingSettings = new Properties();
    var keyEnum = currentExternalToolsSettings.keys();
    while (keyEnum.hasMoreElements()) {
      String key = (String) keyEnum.nextElement();
      String defaultSetting = getExternalToolsDefaultSetting(key, "");
      String currentSetting = currentExternalToolsSettings.getProperty(key, "");
      if (!defaultSetting.equals(currentSetting)) {
        differingSettings.setProperty(key, currentSetting);
      }
    }
    return differingSettings;
  }

  /**
   * Save external tools user settings to file.
   */
  public static void saveExternalToolsUserSettings() {
    var differingSettings = getDifferingExternalToolsSettings();
    try (var out = new FileOutputStream(externalToolsUserSettingsFile)) {
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
   * @param simConfigs Simulation configurations
   */
  public static void go(Config config, List<Simulation.SimConfig> simConfigs) {
    configuration = config;
    // Load default and overwrite with user settings (if any).
    resetExternalToolsSettings();
    externalToolsUserSettingsFile = config.externalToolsConfig == null
            ? new File(System.getProperty("user.home"), ".cooja.user.properties")
            : new File(config.externalToolsConfig);
    if (externalToolsUserSettingsFile.exists()) {
      var settings = new Properties();
      try (var in = new FileInputStream(externalToolsUserSettingsFile)) {
        settings.load(in);
      } catch (IOException e1) {
        logger.warn("Error when reading user settings from: " + externalToolsUserSettingsFile);
        System.exit(1);
      }
      var en = settings.keys();
      while (en.hasMoreElements()) {
        String key = (String) en.nextElement();
        setExternalToolsSetting(key, settings.getProperty(key));
      }
    }

    Cooja gui = null;
    try {
      gui = makeCooja();
    } catch (Exception e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    // Check if simulator should be quick-started.
    int rv = 0;
    boolean autoQuit = !simConfigs.isEmpty() && !config.vis;
    for (var simConfig : simConfigs) {
      Simulation sim = null;
      try {
        sim = config.vis
                ? Cooja.gui.doLoadConfig(simConfig, config.randomSeed)
                : gui.loadSimulationConfig(simConfig, true, config.randomSeed);
      } catch (Exception e) {
        logger.fatal("Exception when loading simulation: ", e);
      }
      if (sim == null) {
        autoQuit = true;
        rv = Math.max(rv, 1);
      } else if (simConfig.updateSim()) {
        autoQuit = true;
        gui.saveSimulationConfig(new File(simConfig.file()));
      } else if (simConfig.autoStart()) {
        autoQuit = true;
        if (!config.vis) {
          sim.setSpeedLimit(null);
        }
        var ret = sim.startSimulation(true);
        if (ret == null) {
          logger.info("TEST OK\n");
        } else {
          logger.warn("TEST FAILED\n");
          rv = Math.max(rv, ret);
        }
      }
    }
    if (autoQuit) {
      gui.doQuit(rv);
    }
  }

  Element readSimulationConfig(Simulation.SimConfig cfg) throws SimulationCreationException {
    var file = new File(cfg.file());
    try {
      currentConfigFile = file.getCanonicalFile(); // Used to generate config relative paths.
    } catch (IOException e) {
      currentConfigFile = file;
    }

    Element root;
    try (InputStream in = file.getName().endsWith(".gz")
            ? new GZIPInputStream(new FileInputStream(file)) : new FileInputStream(file)) {
      final var doc = new SAXBuilder().build(in);
      root = doc.getRootElement();
      boolean projectsOk = verifyProjects(root);
    } catch (JDOMException e) {
      throw new SimulationCreationException("Config not well-formed", e);
    } catch (IOException e) {
      throw new SimulationCreationException("Load simulation error", e);
    } catch (Exception e) {
      // Wrap everything else in a SimulationCreationException, so the SwingWorker works as intended.
      // (SwingWorker communicates internally through SimulationCreationExceptions.)
      throw new SimulationCreationException("Unknown error", e);
    }
    if (!root.getName().equals("simconf")) {
      throw new SimulationCreationException("Not a valid simulation configuration file", null);
    }
    return root;
  }

  /**
   * Loads a simulation configuration from given file.
   * <p>
   * When loading Contiki mote types, the libraries must be recompiled. User may
   * change mote type settings at this point.
   *
   * @param cfg Configuration to load
   * @param manualRandomSeed The random seed.
   * @return New simulation or null if recompiling failed or aborted
   * @throws SimulationCreationException If loading fails.
   * @see #saveSimulationConfig(File)
   */
  Simulation loadSimulationConfig(Simulation.SimConfig cfg, boolean quick, Long manualRandomSeed)
  throws SimulationCreationException {
    Simulation sim;
    try {
      var root = readSimulationConfig(cfg);
      boolean projectsOk = verifyProjects(root);
      sim = createSimulation(cfg, root, quick, manualRandomSeed);
    } catch (SimulationCreationException e) {
      throw e;
    } catch (Exception e) {
      // Wrap everything else in a SimulationCreationException, so the SwingWorker works as intended.
      // (SwingWorker communicates internally through SimulationCreationExceptions.)
      throw new SimulationCreationException("Unknown error", e);
    }
    return sim;
  }

  /**
   * Create a new simulation object.
   *
   * @param cfg Configuration to use
   * @param root The XML config.
   * @param quick Do a quickstart.
   * @param manualRandomSeed The random seed.
   * @throws SimulationCreationException If creation fails.
   * @return Simulation object.
   */
  Simulation createSimulation(Simulation.SimConfig cfg, Element root, boolean quick, Long manualRandomSeed)
  throws SimulationCreationException {
    var simCfg = root.getChild("simulation");
    var title = simCfg.getChild("title").getText();
    var cfgSeed = simCfg.getChild("randomseed").getText();
    boolean generatedSeed = "generated".equals(cfgSeed);
    long seed = manualRandomSeed != null ? manualRandomSeed
            : generatedSeed ? new Random().nextLong() : Long.parseLong(cfgSeed);
    var medium = simCfg.getChild("radiomedium").getText().trim();
    var cfgDelay = simCfg.getChild("motedelay");
    long delay = cfgDelay == null
            ? Integer.parseInt(simCfg.getChild("motedelay_us").getText())
            : Integer.parseInt(cfgDelay.getText()) * Simulation.MILLISECOND;
    if (Cooja.isVisualized() && !quick) {
      var config = CreateSimDialog.showDialog(this, new CreateSimDialog.SimConfig(title, medium,
              generatedSeed, seed, delay));
      if (config == null) return null;
      title = config.title();
      generatedSeed = config.generatedSeed();
      seed = config.randomSeed();
      medium = config.radioMedium();
      delay = config.moteStartDelay();
    }
    doRemoveSimulation();
    System.gc();
    Simulation sim;
    try {
      sim = new Simulation(cfg, this, title, generatedSeed, seed, medium, delay, quick, root);
    } catch (MoteTypeCreationException e) {
      throw new SimulationCreationException("Unknown error: " + e.getMessage(), e);
    }
    setSimulation(sim);
    return sim;
  }

  /**
   * Saves current simulation configuration to given file and notifies observers.
   *
   * @param file File to write
   */
   void saveSimulationConfig(File file) {
    try {
      currentConfigFile = file.getCanonicalFile();  // Used to generate config relative paths.
    } catch (IOException e) {
      currentConfigFile = file;
    }

    try (var out = file.getName().endsWith(".gz")
            ? new GZIPOutputStream(new FileOutputStream(file)) : new FileOutputStream(file)) {
      var xmlOutput = new XMLOutputter(Format.getPrettyFormat());
      xmlOutput.getFormat().setLineSeparator("\n");
      xmlOutput.output(new Document(extractSimulationConfig()), out);
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
    root.setAttribute("version", Cooja.SIMULATION_CONFIG_VERSION);

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
      var pluginType = startedPlugin.getClass().getAnnotation(PluginType.class).value();
      // Ignore GUI plugins
      if (pluginType == PluginType.PType.COOJA_PLUGIN || pluginType == PluginType.PType.COOJA_STANDARD_PLUGIN) {
        continue;
      }

      var pluginElement = new Element("plugin");
      pluginElement.setText(startedPlugin.getClass().getName());

      // Create mote argument config (if mote plugin)
      if (pluginType == PluginType.PType.MOTE_PLUGIN) {
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

        var pluginSubElement = new Element("bounds");
        var bounds = pluginFrame.getBounds();
        pluginSubElement.setAttribute("x", String.valueOf(bounds.x));
        pluginSubElement.setAttribute("y", String.valueOf(bounds.y));
        pluginSubElement.setAttribute("height", String.valueOf(bounds.height));
        pluginSubElement.setAttribute("width", String.valueOf(bounds.width));

        int z = getDesktopPane().getComponentZOrder(pluginFrame);
        if (z != 0) {
          pluginSubElement.setAttribute("z", String.valueOf(z));
        }

        if (pluginFrame.isIcon()) {
          pluginSubElement.setAttribute("minimized", String.valueOf(true));
        }

        pluginElement.addContent(pluginSubElement);
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
    for (var pluginElement : root.getChildren("project")) {
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
    return new RunnableInEDT<Boolean>() {
      @Override
      public Boolean work() {
        return GUI.showErrorDialog(title, exception, retryAvailable);
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
  public static void signalMoteHighlight(Mote m) {
    if (gui != null) {
      gui.moteHighlightObservable.setChangedAndNotify(m);
    }
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
    File contikiBase = createContikiRelativePath(file);
    var configBase = createConfigRelativePath(file, currentConfigFile);
    if (configBase != null &&
        (contikiBase == null || configBase.toString().length() <= contikiBase.toString().length())) {
      return configBase;
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

  public static File createContikiRelativePath(File file) {
    String fileCanonical;
    String best = null;
    String replacement = null;
    try {
      fileCanonical = file.getCanonicalPath();
      for (var strings : PATH_IDENTIFIER) {
        var path = Cooja.getExternalToolsSetting(strings[1]);
        if (path == null) {
          continue;
        }
        var candidate = new File(path).getCanonicalPath();
        if (fileCanonical.startsWith(candidate) && (best == null || best.length() < candidate.length())) {
          best = candidate;
          replacement = strings[0];
        }
      }
    } catch (IOException e1) {
      return null;
    }
    if (replacement == null) return null;
    // Replace Contiki's canonical path with Contiki identifier.
    File portable = new File(fileCanonical.replaceFirst(
            Matcher.quoteReplacement(best), Matcher.quoteReplacement(replacement)));
    // Verify conversion.
    File verify = restoreContikiRelativePath(portable);
    if (verify == null || !verify.exists()) {
      // Error: did file even exist pre-conversion?
      return null;
    }
    return portable;
  }
  
  
  private static File restoreContikiRelativePath(File portable) {
    try {
    	String portablePath = portable.getPath();
      String[] found = null;
      for (var strings : PATH_IDENTIFIER) {
        if (portablePath.startsWith(strings[0])) {
          found = strings;
          break;
        }
    	}
      if (found == null) return null;
      var value = Cooja.getExternalToolsSetting(found[1]);
      if (value == null) return null;
      var absolute = new File(portablePath.replace(found[0], new File(value).getCanonicalPath()));
		if(!absolute.exists()){
      logger.warn("Replaced " + portable + " with " + absolute + ", but could not find it. This does not have to be an error, as the file might be created later.");
		}
    	return absolute;
    } catch (IOException e) {
    	return null;
    }
  }

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

  private static File createConfigRelativePath(File file, File configFile) {
    String id = PATH_CONFIG_IDENTIFIER;
    if (configFile == null) {
      return null;
    }
    File configPath = configFile.getParentFile();
    if (configPath == null) { // File is in current directory.
      configPath = new File("");
    }
    try {
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
      File verify = restoreConfigRelativePath(configFile, portable);
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

  /** Structure to hold the Cooja startup configuration.
   * <p>
   * When SimConfig contains an identical field, these values are the default
   * values when creating a new simulation in the File menu.
   */
  public record Config(boolean vis, Long randomSeed, String externalToolsConfig,
                       String logDir, String contikiPath, String coojaPath, String javac) {}
}
