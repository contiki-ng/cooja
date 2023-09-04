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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JMenu;
import org.contikios.cooja.MoteType.MoteTypeCreationException;
import org.contikios.cooja.VisPlugin.PluginRequiresVisualizationException;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.motes.DisturberMoteType;
import org.contikios.cooja.motes.ImportAppMoteType;
import org.contikios.cooja.mspmote.SkyMoteType;
import org.contikios.cooja.mspmote.Z1MoteType;
import org.contikios.cooja.positioners.EllipsePositioner;
import org.contikios.cooja.positioners.LinearPositioner;
import org.contikios.cooja.positioners.ManualPositioner;
import org.contikios.cooja.positioners.RandomPositioner;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class Cooja {
  /**
   * Version of Cooja.
   */
  public static final String VERSION = "4.9";

  /**
   *  Version used to detect incompatibility with the Cooja simulation config files.
   *  The format is &lt;YYYY&gt;&lt;MM&gt;&lt;DD&gt;&lt;2 digit sequence number&gt;.
   */
  public static final String SIMULATION_CONFIG_VERSION = "2023090101";

  /**
   *  Version used to detect incompatibility with the Contiki-NG
   *  build system. The format is &lt;YYYY&gt;&lt;MM&gt;&lt;DD&gt;&lt;2 digit sequence number&gt;.
   */
  public static final String CONTIKI_NG_BUILD_VERSION = "2023090701";

  private static final Logger logger = LoggerFactory.getLogger(Cooja.class);

  private static final String PATH_CONFIG_IDENTIFIER = "[CONFIG_DIR]";

  private static final PathIdentifier[] PATH_IDENTIFIER = {
          new PathIdentifier("[CONTIKI_DIR]","PATH_CONTIKI"),
          new PathIdentifier("[COOJA_DIR]","PATH_COOJA"),
          new PathIdentifier("[APPS_DIR]","PATH_APPS")
  };

  private static File externalToolsUserSettingsFile;
  private File currentConfigFile; /* Used to generate config relative paths */

  // External tools setting names
  private static final Properties defaultExternalToolsSettings = getExternalToolsDefaultSettings();
  private static Properties currentExternalToolsSettings;

  static GUI gui;

  /** The Cooja startup configuration. */
  public static Config configuration;

  /** Used mote type IDs. Used by mote types to ensure uniqueness during Cooja lifetime. */
  public static final Set<String> usedMoteTypeIDs = new HashSet<>();

  private Simulation mySimulation;

  private final ArrayList<Plugin> startedPlugins = new ArrayList<>();

  // Platform configuration variables
  // Maintained via method parseProjectConfig()
  private ProjectConfig projectConfig;

  final ArrayList<COOJAProject> currentProjects = new ArrayList<>();

  private ClassLoader projectDirClassLoader;

  private ArrayList<Class<? extends MoteType>> moteTypeClasses;

  private ArrayList<Class<? extends Plugin>> pluginClasses;

  private ArrayList<Class<? extends RadioMedium>> radioMediumClasses;

  private ArrayList<Class<? extends Positioner>> positionerClasses;

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
    if (defaultProjectDirs != null && !defaultProjectDirs.isEmpty()) {
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
    if (searchProjectDirs != null && !searchProjectDirs.isEmpty()) {
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
      parseProjectConfig(false);
    }
    // Shutdown hook to stop running simulations.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      if (mySimulation != null) {
        mySimulation.stopSimulation();
      }
    }));
  }

  /**
   * @return True if simulator is visualized
   */
  public static boolean isVisualized() {
    return configuration.vis;
  }

  public static JFrame getTopParentContainer() {
    // Touching GUI when headless pollutes performance profile with ClassLoader.loadClass()-chain.
    return gui == null ? null : GUI.frame;
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
    if (moteTypeClasses == null) {
      registerClasses();
    }
    moteTypeClasses.add(moteTypeClass);
  }

  /**
   * @return All registered mote type classes
   */
  public List<Class<? extends MoteType>> getRegisteredMoteTypes() {
    if (moteTypeClasses == null) {
      registerClasses();
    }
    return moteTypeClasses;
  }

  /**
   * Returns all registered plugins.
   */
  public List<Class<? extends Plugin>> getRegisteredPlugins() {
    return Objects.requireNonNullElseGet(pluginClasses, () -> List.copyOf(ExtensionManager.builtinPlugins.values()));
  }

  /**
   * Register new positioner class.
   *
   * @param positionerClass Class to register
   */
  public void registerPositioner(Class<? extends Positioner> positionerClass) {
    if (positionerClasses == null) {
      registerClasses();
    }
    positionerClasses.add(positionerClass);
  }

  /**
   * @return All registered positioner classes
   */
  public List<Class<? extends Positioner>> getRegisteredPositioners() {
    if (positionerClasses == null) {
      registerClasses();
    }
    return positionerClasses;
  }

  /**
   * Register new radio medium class.
   *
   * @param radioMediumClass Class to register
   */
  public void registerRadioMedium(Class<? extends RadioMedium> radioMediumClass) {
    Objects.requireNonNullElseGet(radioMediumClasses, () ->
            radioMediumClasses = new ArrayList<>(ExtensionManager.builtinRadioMediums.values())).add(radioMediumClass);
  }

  /**
   * @return All registered radio medium classes
   */
  public List<Class<? extends RadioMedium>> getRegisteredRadioMediums() {
    return Objects.requireNonNullElseGet(radioMediumClasses, () -> List.copyOf(ExtensionManager.builtinRadioMediums.values()));
  }

  void clearProjectConfig() {
    /* Remove current dependencies */
    moteTypeClasses = null;
    pluginClasses = null;
    positionerClasses = null;
    radioMediumClasses = null;
    projectDirClassLoader = null;
  }

  /**
   * Builds extension configuration using extension directories settings.
   * Registers mote types, plugins, positioners and radio mediums.
   */
  void parseProjectConfig(boolean eagerInit) throws ParseProjectsException {
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

    // Register plugins.
    var pluginClassNames = projectConfig.getStringArrayValue(Cooja.class, "PLUGINS");
    if (gui != null) {
      for (var pluginClass : ExtensionManager.builtinPlugins.values()) {
        if (pluginClass.getAnnotation(PluginType.class).value() == PluginType.PType.MOTE_PLUGIN) {
          gui.menuMotePluginClasses.add(pluginClass);
        }
      }
    }
    if (pluginClassNames != null) {
      pluginClasses = new ArrayList<>(ExtensionManager.builtinPlugins.values());
      for (var pluginClassName : pluginClassNames) {
        var pluginClass = tryLoadClass(this, Plugin.class, pluginClassName);
        if (pluginClass != null) {
          registerPlugin(pluginClass);
        } else {
          logger.error("Could not load plugin class: " + pluginClassName);
        }
      }
    }

    // Register radio mediums.
    var radioMediumsClassNames = projectConfig.getStringArrayValue(Cooja.class, "RADIOMEDIUMS");
    if (radioMediumsClassNames != null) {
      radioMediumClasses = new ArrayList<>(ExtensionManager.builtinRadioMediums.values());
      for (var radioMediumClassName : radioMediumsClassNames) {
        var radioMediumClass = tryLoadClass(this, RadioMedium.class, radioMediumClassName);
        if (radioMediumClass != null) {
          registerRadioMedium(radioMediumClass);
        } else {
          logger.error("Could not load radio medium class: " + radioMediumClassName);
        }
      }
    }

    if (eagerInit) {
      registerClasses();
    }
  }

  private void registerClasses() {
    // Register mote types.
    moteTypeClasses = new ArrayList<>();
    registerMoteType(ImportAppMoteType.class);
    registerMoteType(DisturberMoteType.class);
    registerMoteType(ContikiMoteType.class);
    registerMoteType(SkyMoteType.class);
    registerMoteType(Z1MoteType.class);
    var moteTypeClassNames = projectConfig.getStringArrayValue(Cooja.class,"MOTETYPES");
    if (moteTypeClassNames != null) {
      for (var moteTypeClassName : moteTypeClassNames) {
        if (moteTypeClassName.trim().isEmpty()) {
          continue;
        }
        var moteTypeClass = tryLoadClass(this, MoteType.class, moteTypeClassName);
        if (moteTypeClass != null) {
          registerMoteType(moteTypeClass);
        } else {
          logger.error("Could not load mote type class: " + moteTypeClassName);
        }
      }
    }

    // Register positioner classes.
    positionerClasses = new ArrayList<>();
    registerPositioner(RandomPositioner.class);
    registerPositioner(LinearPositioner.class);
    registerPositioner(EllipsePositioner.class);
    registerPositioner(ManualPositioner.class);
    var positionerClassNames = projectConfig.getStringArrayValue(Cooja.class, "POSITIONERS");
    if (positionerClassNames != null) {
      for (var positionerClassName : positionerClassNames) {
        var positionerClass = tryLoadClass(this, Positioner.class, positionerClassName);
        if (positionerClass != null) {
          registerPositioner(positionerClass);
        } else {
          logger.error("Could not load positioner class: " + positionerClassName);
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
              logger.error("Error when trying to read JAR-file in " + projectDir + ": " + e);
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
   * Remove a plugin from working area.
   *
   * @param plugin Plugin to remove
   */
  public void removePlugin(final Plugin plugin) {
    if (startedPlugins.contains(plugin)) {
      removePlugin(startedPlugins, plugin);
    } else if (mySimulation != null) {
      removePlugin(mySimulation.startedPlugins, plugin);
    }
  }

  static void removePlugin(List<Plugin> plugins, final Plugin plugin) {
    plugin.closePlugin();
    plugins.remove(plugin);
    if (isVisualized()) {
      new RunnableInEDT<Boolean>() {
        @Override
        public Boolean work() {
          gui.updateGUIComponentState();

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
      var pluginType = pluginClass.getAnnotation(PluginType.class).value();
      if (pluginType != PluginType.PType.COOJA_PLUGIN && pluginType != PluginType.PType.COOJA_STANDARD_PLUGIN && sim == null) {
        throw new PluginConstructionException("No simulation argument for plugin: " + pluginClass.getName());
      }
      if (!Objects.requireNonNullElseGet(pluginClasses, () ->
              List.copyOf(ExtensionManager.builtinPlugins.values())).contains(pluginClass)) {
        throw new PluginConstructionException("Tool class not registered: " + pluginClass.getName());
      }
      return startPlugin(pluginClass, sim, mote, null);
    } catch (PluginConstructionException ex) {
      logger.error("Error when starting plugin", ex);
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
    var pluginType = pluginClass.getAnnotation(PluginType.class).value();
    // Construct plugin depending on plugin type
    Plugin plugin;
    try {
      plugin = switch (pluginType) {
        case MOTE_PLUGIN -> {
          if (argMote == null) {
            throw new PluginConstructionException("No mote argument for mote plugin: " + pluginClass.getName());
          }
          yield pluginClass.getConstructor(Mote.class, Simulation.class, Cooja.class)
                  .newInstance(argMote, sim, this);
        }
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
      var pluginConfig = root.getChild("plugin_config");
      if (pluginConfig != null) {
        if (!plugin.setConfigXML(pluginConfig.getChildren(), isVisualized())) {
          throw new PluginConstructionException("Failed to set config for " + pluginClass.getName());
        }
      }
    }

    plugin.startPlugin();

    // Add to active plugins list
    var coojaPlugin = pluginType == PluginType.PType.COOJA_PLUGIN || pluginType == PluginType.PType.COOJA_STANDARD_PLUGIN;
    (coojaPlugin ? startedPlugins : sim.startedPlugins).add(plugin);
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
          boolean minimized = false;
          if (root != null) {
            var location = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
            var size = new Dimension();
            int zOrder = 0;
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

          // Minimize plugin or select and bring to front.
          try {
            if (minimized) {
              pluginFrame.setIcon(true);
            } else {
              pluginFrame.setSelected(true);
              gui.myDesktopPane.moveToFront(pluginFrame);
            }
          } catch (Exception e) {
            // Could not minimize/select.
          }
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
    if (pluginClasses != null) {
      pluginClasses.remove(pluginClass);
      if (pluginClasses.isEmpty()) {
        pluginClasses = null;
      }
    }
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
      logger.error("Could not register plugin, no plugin type found: " + pluginClass);
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
        Objects.requireNonNullElseGet(pluginClasses,
                () -> pluginClasses = new ArrayList<>(ExtensionManager.builtinPlugins.values())).add(pluginClass);
        return true;
    }
    logger.error("Could not register plugin, " + pluginClass + " has unknown plugin type");
    return false;
  }

  /**
   * Returns the first started plugin that ends with given class name, if any.
   *
   * @param classname Class name
   * @return Plugin instance
   */
  public Plugin getPlugin(String classname) {
    for (Plugin p: mySimulation.startedPlugins) {
      if (p.getClass().getName().endsWith(classname)) {
        return p;
      }
    }
    return null;
  }

  /**
   * Returns the first started plugin that is assignable by the given class, if any.
   *
   * @param pluginClass a class specifying the class of plugin
   * @return Plugin instance
   */
  public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
    for (Plugin p: mySimulation.startedPlugins) {
      if (pluginClass.isInstance(p)) {
        return pluginClass.cast(p);
      }
    }
    return null;
  }

  /**
   * Returns all started plugins that are assignable by the given class, if any.
   *
   * @param pluginClass a class specifying the class of plugin
   * @return A list of plugin instances
   */
  public <T extends Plugin> List<T> getPlugins(Class<T> pluginClass) {
    var list = new ArrayList<T>();
    for (Plugin p: mySimulation.startedPlugins) {
      if (pluginClass.isInstance(p)) {
        list.add(pluginClass.cast(p));
      }
    }
    return list;
  }

  public Plugin[] getStartedPlugins() {
    List<Plugin> l = mySimulation == null ? Collections.emptyList() : mySimulation.startedPlugins;
    return l.toArray(new Plugin[0]);
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

    // Delete simulation
    mySimulation.removed();
    mySimulation = null;

    // Reset frame title
    if (isVisualized()) {
      updateGUIComponentState();
      GUI.frame.setTitle(WINDOW_TITLE);
    }
    return true;
  }

  public void doQuit(int exitCode) {
    // Clean up resources. Catch all exceptions to ensure that System.exit will be called.
    try {
      doRemoveSimulation();
      for (var plugin : startedPlugins.toArray(new Plugin[0])) {
        removePlugin(startedPlugins, plugin);
      }
    } catch (Exception e) {
      logger.error("Failed to remove simulation/plugins on shutdown.", e);
    }
    System.exit(exitCode);
  }

    public static String resolvePathIdentifiers(String path) {
      for (var pathIdentifier : PATH_IDENTIFIER) {
        if (path.contains(pathIdentifier.id)) {
          String p = Cooja.getExternalToolsSetting(pathIdentifier.path);
          if (p != null) {
            path = path.replace(pathIdentifier.id, p);
          } else {
            logger.warn("could not resolve path identifier " + pathIdentifier.id);
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
    currentExternalToolsSettings = new Properties(defaultExternalToolsSettings);
  }

  /** Get default external tools settings. */
  private static Properties getExternalToolsDefaultSettings() {
    Properties settings = new Properties();
    settings.put("PATH_COOJA", "./");
    settings.put("PATH_CONTIKI", "../../");
    settings.put("PATH_MAKE", "make");
    settings.put("PATH_C_COMPILER", "gcc");
    settings.put("DEFAULT_PROJECTDIRS", "");

    settings.put("PARSE_WITH_COMMAND", "false");
    settings.put("READELF_COMMAND", "readelf -W --symbols $(LIBFILE)");

    settings.put("PARSE_COMMAND", "nm -aP $(LIBFILE)");
    settings.put("COMMAND_VAR_NAME_ADDRESS_SIZE", "^(?<symbol>[^.].*?) <SECTION> (?<address>[0-9a-fA-F]+) (?<size>[0-9a-fA-F])*");
    settings.put("COMMAND_VAR_SEC_DATA", "[DdGg]");
    settings.put("COMMAND_VAR_SEC_BSS", "[Bb]");
    settings.put("COMMAND_VAR_SEC_COMMON", "[C]");
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.startsWith("win")) {
      settings.put("PATH_C_COMPILER", "mingw32-gcc");
      settings.put("PARSE_WITH_COMMAND", "true");

      // Hack: nm with arguments -S --size-sort does not display __data_start symbols
      settings.put("PARSE_COMMAND", "/bin/nm -aP --size-sort -S $(LIBFILE) && /bin/nm -aP $(LIBFILE)");

      settings.put("COMMAND_VAR_NAME_ADDRESS_SIZE", "^[_](?<symbol>[^.].*?)[ \t]<SECTION>[ \t](?<address>[0-9a-fA-F]+)[ \t](?<size>[0-9a-fA-F]+)");
    } else if (osName.startsWith("mac os x")) {
      settings.put("PARSE_WITH_COMMAND", "true");
      settings.put("PARSE_COMMAND", "symbols $(LIBFILE)");
      settings.put("COMMAND_VAR_NAME_ADDRESS", "^[ \t]*([0-9A-Fa-f][0-9A-Fa-f]*)[ \t]\\(__DATA,__[^ ]*\\) external _([^ ]*)$");
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
  private static Properties getDifferingExternalToolsSettings() {
    var differingSettings = new Properties();
    for (var entry : currentExternalToolsSettings.entrySet()) {
      if (!(entry.getKey() instanceof String key && entry.getValue() instanceof String currentSetting)) continue;
      if (!getExternalToolsDefaultSetting(key, "").equals(currentSetting)) {
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
      logger.error("Could not save external tools user settings to " + externalToolsUserSettingsFile + ", aborting");
    } catch (IOException ex) {
      // Could not open settings file for writing, aborting
      logger.error("Error while saving external tools user settings to " + externalToolsUserSettingsFile + ", aborting");
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
        logger.error("Error when reading user settings from: " + externalToolsUserSettingsFile);
        System.exit(1);
      }
      for (var entry : settings.entrySet()) {
        if (!(entry.getKey() instanceof String key && entry.getValue() instanceof String value)) continue;
        setExternalToolsSetting(key, value);
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
    var failedTests = new ArrayList<Simulation.SimConfig>();
    for (var simConfig : simConfigs) {
      logger.info("Loading " + simConfig.file() + " random seed: " + simConfig.randomSeed());
      Simulation sim = null;
      try {
        sim = config.vis
                ? Cooja.gui.doLoadConfig(simConfig)
                : gui.createSimulation(simConfig, gui.readSimulationConfig(simConfig), true, simConfig.randomSeed());
      } catch (MoteTypeCreationException | SimulationCreationException e) {
        logger.error("Failed to load simulation: {}", e.getMessage());
      } catch (Exception e) {
        logger.error("Exception when loading simulation: ", e);
      }
      if (sim == null) {
        autoQuit = true;
        logger.error("TEST {} FAILED\n", simConfig.file());
        failedTests.add(simConfig);
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
          logger.error("TEST {} FAILED\n", simConfig.file());
          failedTests.add(simConfig);
          rv = Math.max(rv, ret);
        }
      }
    }
    if (autoQuit) {
      if (!failedTests.isEmpty()) {
        logger.error("Failed tests:\n{}", failedTests.stream().map(cfg ->
                cfg.file() + " seed: " + cfg.randomSeed()).collect(Collectors.joining("\n")));
      }
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
          throws MoteTypeCreationException, SimulationCreationException {
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
    doRemoveSimulation();
    var sim = new Simulation(cfg, this, title, generatedSeed, seed, medium, delay, quick, root);
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
      logger.error("Exception while saving simulation config: " + e);
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
    root.addContent(mySimulation.getPluginConfigXML());
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
      } catch (InterruptedException e) {
        logger.warn("Thread interrupted" + (e.getMessage() != null ? " " + e.getMessage() : ""));
      } catch (InvocationTargetException e) {
        logger.info("EDT thread call failed", e);
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
   * @param m
   *          Mote to highlight
   */
  public void signalMoteHighlight(Mote m) {
    if (Cooja.isVisualized() && mySimulation != null) {
      mySimulation.moteHighlightTriggers.trigger(EventTriggers.Update.UPDATE, m);
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
      for (var pathIdentifier : PATH_IDENTIFIER) {
        var path = Cooja.getExternalToolsSetting(pathIdentifier.path);
        if (path == null) {
          continue;
        }
        var candidate = new File(path).getCanonicalPath();
        if (fileCanonical.startsWith(candidate) && (best == null || best.length() < candidate.length())) {
          best = candidate;
          replacement = pathIdentifier.id;
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
      PathIdentifier found = null;
      for (var pathIdentifier : PATH_IDENTIFIER) {
        if (portablePath.startsWith(pathIdentifier.id)) {
          found = pathIdentifier;
          break;
        }
    	}
      if (found == null) return null;
      var value = Cooja.getExternalToolsSetting(found.path);
      if (value == null) return null;
      var absolute = new File(portablePath.replace(found.id, new File(value).getCanonicalPath()));
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
      gui.setProgressMessage(msg, type);
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
  public record Config(LogbackColors logColors, boolean vis, String externalToolsConfig,
                       String nashornArgs, String logDir,
                       String contikiPath, String coojaPath) {}

  public record LogbackColors(String error, String warn, String info, String fallback) {}
  private record PathIdentifier(String id, String path) {}
}
