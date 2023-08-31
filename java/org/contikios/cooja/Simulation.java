/*
 * Copyright (c) 2009, Swedish Institute of Computer Science. All rights
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
import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import javax.swing.JTextArea;
import org.contikios.cooja.Cooja.PluginConstructionException;
import org.contikios.cooja.Cooja.SimulationCreationException;
import org.contikios.cooja.util.EventTriggers;
import org.contikios.cooja.util.EventTriggers.AddRemove;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simulation consists of a number of motes and mote types.
 * <p>
 * A simulation is observable:
 * changed simulation state, added or deleted motes etc. are observed.
 * To track mote changes, observe the mote (interfaces) itself.
 *
 * @author Fredrik Osterlind
 */
public final class Simulation {

  /** Commands sent to the simulation thread to start, stop, or shutdown the simulation */
  private enum Command {
    START, STOP, QUIT
  }

  public static final String PROPERTY_TITLE = "title";
  public static final String PROPERTY_SPEED_LIMIT = "speedLimit";

  public static final long MICROSECOND = 1L;
  public static final long MILLISECOND = 1000*MICROSECOND;

  /** Lock used to wait for simulation state changes */
  private final Object stateLock = new Object();

  /* indicator to components setting up that they need to respect the fast setup mode */
  private boolean quick;

  /** Started simulation plugins. */
  final ArrayList<Plugin> startedPlugins = new ArrayList<>();

  private final ArrayList<Mote> motes = new ArrayList<>();
  private final ArrayList<MoteType> moteTypes = new ArrayList<>();

  private final LinkedBlockingDeque<Object> commandQueue = new LinkedBlockingDeque<>();

  private final Thread simulationThread;

  /* If true, run simulation at full speed */
  private boolean speedLimitNone = true;
  /* Limit simulation speed to maxSpeed; if maxSpeed is 1.0 simulation is run at real-time speed */
  private double speedLimit;
  /* Used to restrict simulation speed */
  private long speedLimitLastSimtime;
  private long speedLimitLastRealtime;

  private long lastStartRealTime;
  private long lastStartSimulationTime;
  private long currentSimulationTime;

  private String title;

  private final RadioMedium currentRadioMedium;

  private static final Logger logger = LoggerFactory.getLogger(Simulation.class);

  private volatile boolean isRunning;
  private volatile boolean isShutdown;

  private final Cooja cooja;

  private final long randomSeed;

  private final boolean randomSeedGenerated;

  private final long maxMoteStartupDelay;

  private final SafeRandom randomGenerator;

  /* Event queue */
  private final EventQueue eventQueue = new EventQueue();

  /** Simulation state change triggers */
  private final EventTriggers<EventTriggers.Operation, Simulation> simulationStateTriggers = new EventTriggers<>();

  /** Mote type add and remove triggers. */
  private final EventTriggers<EventTriggers.AddRemove, MoteType> moteTypeTriggers = new EventTriggers<>();

  /** Mote add and remove triggers. */
  private final EventTriggers<EventTriggers.AddRemove, Mote> moteTriggers = new EventTriggers<>();

  /** Mote highlight triggers. */
  final EventTriggers<EventTriggers.Update, Mote> moteHighlightTriggers = new EventTriggers<>();

  /** Property change support */
  private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

  /** List of active script engines. */
  private final ArrayList<LogScriptEngine> scriptEngines = new ArrayList<>();

  private final SimEventCentral eventCentral = new SimEventCentral(this);

  /** The return value from startSimulation. */
  private volatile Integer returnValue;

  /** Mote relation (directed). */
  public record MoteRelation(Mote source, Mote dest, Color color) {}
  private final ArrayList<MoteRelation> moteRelations = new ArrayList<>();
  private final EventTriggers<AddRemove, MoteRelation> moteRelationsTriggers = new EventTriggers<>();
  private final SimConfig cfg;

  private final TimeEvent delayEvent = new TimeEvent() {
    @Override
    public void execute(long t) {
      if (speedLimitNone) {
        /* As fast as possible: no need to reschedule delay event */
        return;
      }

      long diffSimtime = getSimulationTimeMillis() - speedLimitLastSimtime; /* ms */
      long diffRealtime = System.currentTimeMillis() - speedLimitLastRealtime; /* ms */
      long expectedDiffRealtime = (long) (diffSimtime/speedLimit);
      long sleep = expectedDiffRealtime - diffRealtime;
      if (sleep > 0) { // Slow down simulation.
        scheduleEvent(this, t+MILLISECOND);
        try {
          Thread.sleep(sleep);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt(); // Restore interrupted status.
        }
      } else { // Speed up simulation.
        scheduleEvent(this, t - Math.min(-1, sleep) * MILLISECOND);
      }

      /* Update counters every second */
      if (diffRealtime > 1000) {
        speedLimitLastRealtime = System.currentTimeMillis();
        speedLimitLastSimtime = getSimulationTimeMillis();
      }
    }
    @Override
    public String toString() {
      return "DELAY";
    }
  };

  /**
   * Creates a new simulation
   */
  public Simulation(SimConfig cfg, Cooja cooja, String title, boolean generateSeed, long seed,
                    String radioMediumClass, long moteStartDelay, boolean quick, Element root)
          throws MoteType.MoteTypeCreationException, SimulationCreationException {
    this.cfg = cfg;
    this.cooja = cooja;
    this.title = title;
    this.quick = quick;
    randomSeed = seed;
    randomSeedGenerated = generateSeed;
    randomGenerator = new SafeRandom(seed, this);
    currentRadioMedium = ExtensionManager.createRadioMedium(cooja, this, radioMediumClass);
    maxMoteStartupDelay = Math.max(0, moteStartDelay);
    simulationThread = new Thread(() -> {
      boolean isAlive = true;
      do {
        boolean isSimulationRunning = false;
        EventQueue.Pair nextEvent = null;
        try {
          while (isAlive) {
            Object cmd;
            do {
              cmd = isSimulationRunning ? commandQueue.poll() : commandQueue.take();
              if (cmd instanceof Runnable r) {
                r.run();
              } else if (cmd instanceof Command c) {
                isAlive = c != Command.QUIT;
                isShutdown = !isAlive;
                isSimulationRunning = c == Command.START;
                setRunning(isSimulationRunning);
              }
            } while (cmd != null && isAlive);

            if (isSimulationRunning) {
              // Handle one simulation event, and update simulation time.
              nextEvent = eventQueue.popFirst();
              assert nextEvent != null : "Ran out of events in eventQueue";
              assert nextEvent.time >= currentSimulationTime : "Event from the past";
              currentSimulationTime = nextEvent.time;
              nextEvent.event.execute(currentSimulationTime);
            }
          }
        } catch (SimulationStop e) {
          logger.info("Simulation stopped: {}", e.getMessage());
        } catch (RuntimeException e) {
          logger.error("Simulation stopped due to error: " + e.getMessage(), e);
          if (Cooja.isVisualized()) {
            String errorTitle = "Simulation error";
            if (nextEvent != null && nextEvent.event instanceof MoteTimeEvent moteTimeEvent) {
              errorTitle += ": " + moteTimeEvent.getMote();
            }
            Cooja.showErrorDialog(errorTitle, e, false);
          } else {
            isAlive = false;
            isShutdown = true;
            returnValue = 1;
          }
        } catch (InterruptedException e) {
          // Simulation thread interrupted - quit
          logger.warn("simulation thread interrupted");
          Thread.currentThread().interrupt();
          isAlive = false;
          isShutdown = true;
        }
        setRunning(false);
      } while (isAlive);
      isShutdown = true;
      commandQueue.clear();
      eventQueue.clear();

      // Deactivate all script engines
      for (var engine : scriptEngines) {
        engine.deactivateScript();
        engine.closeLog();
      }

      // Clear current mote relations.
      for (var r: getMoteRelations()) {
        removeMoteRelation(r.source, r.dest);
      }

      // Remove all motes and mote types.
      for (var m : motes.toArray(new Mote[0])) {
        doRemoveMote(m);
      }
      for (var m : moteTypes.toArray(new MoteType[0])) {
        removeMoteType(m);
      }

      // Remove the radio medium
      currentRadioMedium.removed();

      simulationStateTriggers.trigger(EventTriggers.Operation.REMOVE, this);
    }, "sim");
    simulationThread.start();
    if (root != null) {
      // Track identifier of mote types to deal with the legacy-XML format that used <motetype_identifier>.
      var moteTypesMap = new HashMap<String, MoteType>();
      // Parse elements
      for (var element : root.getChild("simulation").getChildren()) {
        switch (element.getName()) {
          case "speedlimit" -> setSpeedLimit(element.getText().equals("null") ? null : Double.parseDouble(element.getText()));
          case "events" -> eventCentral.setConfigXML(element.getChildren());
          case "motetype" -> {
            String moteTypeClassName = element.getText().trim();
            var moteType = ExtensionManager.createMoteType(cooja, moteTypeClassName);
            if (!moteType.setConfigXML(this, element.getChildren(), Cooja.isVisualized())) {
              logger.error("Mote type could not be configured: " + element.getText().trim());
              throw new MoteType.MoteTypeCreationException("Mote type could not be configured: " + element.getText().trim());
            }
            addMoteType(moteType);
            for (var mote : element.getChildren("mote")) {
              createMote(moteType, mote);
            }
            var id = element.getChild("identifier");
            if (id != null) {
              moteTypesMap.put(id.getText(), moteType);
            }
          }
          case "mote" -> {
            var subElement = element.getChild("motetype_identifier");
            if (subElement == null) {
              throw new MoteType.MoteTypeCreationException("No motetype_identifier specified for mote");
            }
            var moteType = moteTypesMap.get(subElement.getText());
            if (moteType == null) {
              throw new MoteType.MoteTypeCreationException("No mote type '" + subElement.getText() + "' for mote");
            }
            createMote(moteType, element);
          }
        }
      }
      var mediumCfg = root.getChild("simulation").getChild("radiomedium");
      currentRadioMedium.setConfigXML(mediumCfg.getChildren(), Cooja.isVisualized());

      // Quick load mode only during loading
      this.quick = false;
    }
    SimulationCreationException ret = null;
    if (root == null) {
      for (var pluginClass : cooja.getRegisteredPlugins()) {
        if (pluginClass.getAnnotation(PluginType.class).value() == PluginType.PType.SIM_STANDARD_PLUGIN) {
          try {
            cooja.startPlugin(pluginClass, this, null, null);
          } catch (PluginConstructionException e) {
            ret = new SimulationCreationException("Failed to start plugin: " + e.getMessage(), e);
            break;
          }
        }
      }
    } else {
      // Wait for simulation thread to complete configuration (getMote() can fail otherwise).
      final var simThreadIdle = new CountDownLatch(1);
      invokeSimulationThread(simThreadIdle::countDown);
      try {
        simThreadIdle.await();
      } catch (InterruptedException e) {
        throw new SimulationCreationException("Simulation creation interrupted", e);
      }
      boolean hasController = Cooja.isVisualized();
      for (var pluginElement : root.getChildren("plugin")) {
        var pluginClassName = pluginElement.getText().trim();
        if (pluginClassName.startsWith("se.sics")) {
          pluginClassName = pluginClassName.replaceFirst("^se\\.sics", "org.contikios");
        }
        // Skip plugins that have been removed or merged into other classes.
        if ("org.contikios.cooja.plugins.SimControl".equals(pluginClassName) ||
                "org.contikios.cooja.plugins.SimInformation".equals(pluginClassName) ||
                "org.contikios.cooja.plugins.MoteTypeInformation".equals(pluginClassName)) {
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

        var pluginClass = ExtensionManager.getPluginClass(cooja, pluginClassName);
        if (pluginClass == null) {
          logger.error("Plugin class " + pluginClassName + " not registered as a plugin");
          ret = new SimulationCreationException("Plugin class " + pluginClassName + " not registered as a plugin", null);
          break;
        }
        // Skip plugins that require visualization in headless mode.
        if (!Cooja.isVisualized() && VisPlugin.class.isAssignableFrom(pluginClass)) {
          continue;
        }
        if (!hasController && pluginClass.getAnnotation(PluginType.class).value() == PluginType.PType.SIM_CONTROL_PLUGIN) {
          hasController = true;
        }
        // Parse plugin mote argument (if any).
        Mote mote = null;
        for (var pluginSubElement : pluginElement.getChildren("mote_arg")) {
          int moteNr = Integer.parseInt(pluginSubElement.getText());
          if (moteNr >= 0 && moteNr < getMotesCount()) {
            mote = getMote(moteNr);
          }
        }
        final var finalMote = mote;
        if (Cooja.isVisualized()) {
          ret = new Cooja.RunnableInEDT<SimulationCreationException>() {
            @Override
            public SimulationCreationException work() {
              return startPlugin(pluginClass, pluginElement, cooja, finalMote);
            }
          }.invokeAndWait();
        } else {
          ret = startPlugin(pluginClass, pluginElement, cooja, finalMote);
        }
        if (ret != null) break;
      }
      // Non-GUI Cooja requires a simulation controller, ensure one is started.
      if (ret == null && !Cooja.isVisualized() && !hasController) {
        ret = new SimulationCreationException("No plugin controlling simulation, aborting", null);
      }
    }
    if (ret != null) {
      removed();
      throw ret;
    }
  }

  private void createMote(MoteType moteType, Element root) throws MoteType.MoteTypeCreationException {
    var mote = moteType.generateMote(this);
    if (!mote.setConfigXML(this, root.getChildren(), Cooja.isVisualized())) {
      logger.error("Mote was not created: " + root.getText().trim());
      throw new MoteType.MoteTypeCreationException("Could not configure mote " + moteType);
    }
    addMote(mote);
  }

  private SimulationCreationException startPlugin(Class<? extends Plugin> pluginClass, Element pluginElement,
                                                  Cooja cooja, Mote mote) {
    Cooja.setProgressMessage("Starting " + pluginClass.getName());
    try {
      cooja.startPlugin(pluginClass, this, mote, pluginElement);
    } catch (PluginConstructionException ex) {
      return new SimulationCreationException("Failed to start plugin: " + ex.getMessage(), ex);
    }
    return null;
  }

  /**
   * Set if the simulation is running or not.
   * May only be called by the simulation thread to notify about its state.
   */
  private void setRunning(boolean isRunning) {
    if (this.isRunning == isRunning) {
      return;
    }

    if (isRunning) {
      // Simulation starting
      speedLimitLastRealtime = lastStartRealTime = System.currentTimeMillis();
      speedLimitLastSimtime = lastStartSimulationTime = getSimulationTimeMillis();
    } else {
      // Simulation stopped
      var realTimeDuration = System.currentTimeMillis() - lastStartRealTime;
      var simulationDuration = getSimulationTimeMillis() - lastStartSimulationTime;
      logger.info("Runtime: {} ms. Simulated time: {} ms. Speedup: {}",
                  realTimeDuration, simulationDuration,
                  ((double) simulationDuration / Math.max(1, realTimeDuration)));
    }

    synchronized (stateLock) {
      this.isRunning = isRunning;
      stateLock.notifyAll();
    }

    Cooja.updateProgress(!isRunning);
    simulationStateTriggers.trigger(isRunning ? EventTriggers.Operation.START : EventTriggers.Operation.STOP, this);
  }

  /**
   * Request poll from simulation thread.
   * Poll requests are prioritized over simulation events, and are
   * executed between each simulation event.
   *
   * @param r Simulation thread action
   */
  public void invokeSimulationThread(Runnable r) {
    if (!isShutdown) {
      commandQueue.add(r);
    }
  }

  /**
   * @return True iff current thread is the simulation thread
   */
  public boolean isSimulationThread() {
    return simulationThread == Thread.currentThread();
  }

  /**
   * Schedule simulation event for given time.
   * Already scheduled events must be removed before they are rescheduled.
   * <p>
   * If the simulation is running, this method may only be called from the simulation thread.
   *
   * @see #invokeSimulationThread(Runnable)
   *
   * @param e Event
   * @param time Execution time
   */
  public void scheduleEvent(final TimeEvent e, final long time) {
    assert isSimulationThread() : "Scheduling event from non-simulation thread: " + e;
    eventQueue.addEvent(e, time);
  }

  /** Create a new script engine that logs to the logTextArea and add it to the list
   *  of active script engines. */
  public LogScriptEngine newScriptEngine(JTextArea logTextArea, String nashornArgs) {
    var engine = new LogScriptEngine(this, nashornArgs, scriptEngines.size(), logTextArea);
    scriptEngines.add(engine);
    return engine;
  }

  /** Remove a script engine from the list of active script engines. */
  public void removeScriptEngine(LogScriptEngine engine) {
    engine.deactivateScript();
    scriptEngines.remove(engine);
  }

  /**
   * Starts this simulation (notifies observers).
   */
  public void startSimulation() {
    startSimulation(false);
  }

  public Integer startSimulation(boolean block) {
    if (!isRunning() && !isShutdown) {
      commandQueue.add(Command.START);
      if (block) {
        try {
          // Wait for simulation to be shutdown
          simulationThread.join();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        return returnValue;
      }
    }
    return null;
  }

  /**
   * Stop simulation and block until it has stopped.
   */
  public void stopSimulation() {
    if (stopSimulation(null)) {
      waitFor(false, 250);
    }
  }

  /**
   * Stop simulation
   *
   * @param rv Return value from startSimulation, should be null unless > 0
   * @return True if action was taken
   */
  public boolean stopSimulation(Integer rv) {
    if (!isRunning() || isShutdown) {
      return false;
    }
    assert rv == null || rv > 0 : "Pass in rv = null or rv > 0";
    if (rv != null) {
      returnValue = rv;
    }
    commandQueue.add(Cooja.isVisualized() ? Command.STOP : Command.QUIT);
    return true;
  }

  private void waitFor(boolean isRunning, long timeout) {
    if (Thread.currentThread() == simulationThread) {
      return;
    }
    long startTime = System.currentTimeMillis();
    try {
      synchronized (stateLock) {
        while (this.isRunning != isRunning && !isShutdown) {
          long maxWaitTime = timeout - (System.currentTimeMillis() - startTime);
          if (timeout != 0 && maxWaitTime <= 0) {
            return;
          }
          stateLock.wait(timeout == 0 ? 0 : maxWaitTime);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Starts simulation if stopped, executes one millisecond, and finally stops
   * simulation again.
   */
  public void stepMillisecondSimulation() {
    if (isRunning()) {
      return;
    }
    TimeEvent stopEvent = new TimeEvent() {
      @Override
      public void execute(long t) {
        stopSimulation();
      }
    };
    invokeSimulationThread(() -> scheduleEvent(stopEvent, getSimulationTime() + Simulation.MILLISECOND));
    startSimulation();
  }

  public Cooja getCooja() {
    return cooja;
  }

  /**
   * Returns the maximal mote startup delay.
   * @return the configured mote startup delay.
   */
  public long getMaxMoteStartupDelay() {
    return maxMoteStartupDelay;
  }

  /**
   * @return Random seed
   */
  public long getRandomSeed() {
    return randomSeed;
  }

  public Random getRandomGenerator() {
    return randomGenerator;
  }

  public SimEventCentral getEventCentral() {
    return eventCentral;
  }

  /**
   * Returns the current simulation config represented by XML elements. This
   * config also includes the current radio medium, all mote types and motes.
   *
   * @return Current simulation config
   */
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    // Title
    var element = new Element("title");
    element.setText(title);
    config.add(element);

    /* Max simulation speed */
    if (!speedLimitNone) {
      element = new Element("speedlimit");
      element.setText(String.valueOf(getSpeedLimit()));
      config.add(element);
    }

    // Random seed
    element = new Element("randomseed");
    element.setText(randomSeedGenerated ? "generated" : String.valueOf(randomSeed));
    config.add(element);

    // Max mote startup delay
    element = new Element("motedelay_us");
    element.setText(Long.toString(maxMoteStartupDelay));
    config.add(element);

    // Radio Medium
    element = new Element("radiomedium");
    element.setText(currentRadioMedium.getClass().getName());

    Collection<Element> radioMediumXML = currentRadioMedium.getConfigXML();
    if (radioMediumXML != null) {
      element.addContent(radioMediumXML);
    }
    config.add(element);

    /* Event central */
    element = new Element("events");
    element.addContent(eventCentral.getConfigXML());
    config.add(element);

    // Mote types
    for (MoteType moteType : moteTypes) {
      element = new Element("motetype");
      element.setText(moteType.getClass().getName());

      Collection<Element> moteTypeXML = moteType.getConfigXML(this);
      if (moteTypeXML != null) {
        element.addContent(moteTypeXML);
      }
      // Motes
      var moteTypeId = moteType.getIdentifier();
      var moteConfigs = new ArrayList<Element>();
      for (var mote : motes) {
        if (!moteTypeId.equals(mote.getType().getIdentifier())) {
          continue;
        }
        var moteElem = new Element("mote");
        moteElem.addContent(mote.getConfigXML());
        moteConfigs.add(moteElem);
      }
      element.addContent(moteConfigs);
      config.add(element);
    }
    return config;
  }

  Collection<Element> getPluginConfigXML() {
    var config = new ArrayList<Element>();
    for (var startedPlugin : startedPlugins) {
      var pluginElement = new Element("plugin");
      pluginElement.setText(startedPlugin.getClass().getName());

      // Create mote argument config (if mote plugin).
      if (startedPlugin instanceof MotePlugin motePlugin) {
        Mote taggedMote = motePlugin.getMote();
        for (int moteNr = 0; moteNr < getMotesCount(); moteNr++) {
          if (getMote(moteNr) == taggedMote) {
            var pluginSubElement = new Element("mote_arg");
            pluginSubElement.setText(Integer.toString(moteNr));
            pluginElement.addContent(pluginSubElement);
            break;
          }
        }
      }

      // Create plugin specific configuration.
      var pluginXML = startedPlugin.getConfigXML();
      if (pluginXML != null) {
        var pluginSubElement = new Element("plugin_config");
        pluginSubElement.addContent(pluginXML);
        pluginElement.addContent(pluginSubElement);
      }

      // If plugin is visualizer plugin, create visualization arguments
      var pluginFrame = startedPlugin.getCooja();
      if (pluginFrame != null) {
        var pluginSubElement = new Element("bounds");
        var bounds = pluginFrame.getBounds();
        pluginSubElement.setAttribute("x", String.valueOf(bounds.x));
        pluginSubElement.setAttribute("y", String.valueOf(bounds.y));
        pluginSubElement.setAttribute("height", String.valueOf(bounds.height));
        pluginSubElement.setAttribute("width", String.valueOf(bounds.width));

        var z = Cooja.getDesktopPane().getComponentZOrder(pluginFrame);
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
    return config;
  }

  public boolean isQuickSetup() {
      return quick;
  }

  public boolean hasStartedPlugins() {
    return !startedPlugins.isEmpty();
  }

  /**
   * Removes a mote from this simulation
   *
   * @param mote
   *          Mote to remove
   */
  public void removeMote(final Mote mote) {
    invokeSimulationThread(() -> doRemoveMote(mote));
  }

  private void doRemoveMote(Mote mote) {
    boolean removed = motes.remove(mote);
    mote.removed();
    if (removed) {
      moteTriggers.trigger(AddRemove.REMOVE, mote);
      eventCentral.removeMote(mote);
    }
    // Delete all events associated with deleted mote.
    eventQueue.removeIf(ev -> ev instanceof MoteTimeEvent moteTimeEvent && moteTimeEvent.getMote() == mote);
    for (var p : startedPlugins.toArray(new Plugin[0])) {
      if (p instanceof MotePlugin plugin) {
        if (mote == plugin.getMote()) {
          Cooja.removePlugin(startedPlugins, p);
        }
      }
    }
  }

  /**
   * Called to free resources used by the simulation.
   * This method is called just before the simulation is removed.
   */
  void removed() {
    // Close all simulation plugins.
    for (var startedPlugin : startedPlugins.toArray(new Plugin[0])) {
      Cooja.removePlugin(startedPlugins, startedPlugin);
    }
    if (!isShutdown) {
      commandQueue.add(Command.QUIT);
    }
  }

  /**
   * Adds a mote to this simulation
   *
   * @param mote
   *          Mote to add
   */
  public void addMote(final Mote mote) {
    invokeSimulationThread(() -> {
      motes.add(mote);
      mote.added();
      moteTriggers.trigger(AddRemove.ADD, mote);
      eventCentral.addMote(mote);
      Cooja.updateGUIComponentState();
    });
  }

  /**
   * Returns simulation mote at given list position.
   *
   * @param pos Internal list position of mote
   * @return Mote
   * @see #getMotesCount()
   * @see #getMoteWithID(int)
   */
  public Mote getMote(int pos) {
    return motes.get(pos);
  }

  /**
   * Returns simulation with given ID.
   *
   * @param id ID
   * @return Mote or null
   * @see Mote#getID()
   */
  public Mote getMoteWithID(int id) {
    for (Mote m: motes) {
      if (m.getID() == id) {
        return m;
      }
    }
    return null;
  }

  /**
   * Returns number of motes in this simulation.
   *
   * @return Number of motes
   */
  public int getMotesCount() {
    return motes.size();
  }

  /**
   * Returns all motes in this simulation.
   *
   * @return Motes
   */
  public Mote[] getMotes() {
    Mote[] arr = new Mote[motes.size()];
    motes.toArray(arr);
    return arr;
  }

  /**
   * Returns all mote types in simulation.
   *
   * @return All mote types
   */
  public MoteType[] getMoteTypes() {
    MoteType[] types = new MoteType[moteTypes.size()];
    moteTypes.toArray(types);
    return types;
  }

  /**
   * Adds given mote type to simulation.
   *
   * @param newMoteType Mote type
   */
  public void addMoteType(MoteType newMoteType) {
    Cooja.usedMoteTypeIDs.add(newMoteType.getIdentifier());
    moteTypes.add(newMoteType);
    newMoteType.added();
    moteTypeTriggers.trigger(AddRemove.ADD, newMoteType);
  }

  /**
   * Remove given mote type from simulation.
   *
   * @param type Mote type
   */
  public void removeMoteType(MoteType type) {
    for (Mote m: getMotes()) {
      if (m.getType() == type) {
        removeMote(m);
      }
    }
    if (moteTypes.remove(type)) {
      moteTypeTriggers.trigger(AddRemove.REMOVE, type);
    }
    type.removed();
  }

  /**
   * Adds directed relation between given motes.
   *
   * @param source Source mote
   * @param dest Destination mote
   * @param color The color to use when visualizing the mote relation
   */
  public void addMoteRelation(Mote source, Mote dest, Color color) {
    if (source == null || dest == null || !Cooja.isVisualized()) {
      return;
    }
    removeMoteRelation(source, dest); // Unique relations.
    var r = new MoteRelation(source, dest, color);
    moteRelations.add(r);
    moteRelationsTriggers.trigger(AddRemove.ADD, r);
  }

  /**
   * Removes the relations between given motes.
   *
   * @param source Source mote
   * @param dest Destination mote
   */
  public void removeMoteRelation(Mote source, Mote dest) {
    if (source == null || dest == null || !Cooja.isVisualized()) {
      return;
    }
    var arr = getMoteRelations();
    for (var r: arr) {
      if (r.source == source && r.dest == dest) {
        moteRelations.remove(r); // Relations are unique.
        moteRelationsTriggers.trigger(AddRemove.REMOVE, r);
        break;
      }
    }
  }

  /**
   * Returns all mote relations.
   *
   * @return All current mote relations.
   */
  public MoteRelation[] getMoteRelations() {
    return moteRelations.toArray(new MoteRelation[0]);
  }

  /**
   * Limit simulation speed to given ratio.
   * This method may be called from outside the simulation thread.
   * @param newSpeedLimit Speed limit, or null for unlimited.
   */
  public void setSpeedLimit(final Double newSpeedLimit) {
    invokeSimulationThread(() -> {
      var oldSpeedLimit = getSpeedLimit();
      speedLimitNone = newSpeedLimit == null;
      if (speedLimitNone) {
        if (oldSpeedLimit != null) {
          propertyChangeSupport.firePropertyChange(PROPERTY_SPEED_LIMIT, oldSpeedLimit, null);
        }
        return;
      }
      speedLimitLastRealtime = System.currentTimeMillis();
      speedLimitLastSimtime = getSimulationTimeMillis();
      speedLimit = newSpeedLimit;

      if (delayEvent.isScheduled()) {
        delayEvent.remove();
      }
      scheduleEvent(delayEvent, currentSimulationTime);
      propertyChangeSupport.firePropertyChange(PROPERTY_SPEED_LIMIT, oldSpeedLimit, speedLimit);
    });
  }

  /**
   * @return Max simulation speed ratio. Returns null if no limit.
   */
  public Double getSpeedLimit() {
    return speedLimitNone ? null : speedLimit;
  }

  /**
   * Returns current simulation time.
   *
   * @return Simulation time (microseconds)
   */
  public long getSimulationTime() {
    return currentSimulationTime;
  }

  /**
   * Returns current simulation time rounded to milliseconds.
   *
   * @see #getSimulationTime()
   * @return Time rounded to milliseconds
   */
  public long getSimulationTimeMillis() {
    return currentSimulationTime / MILLISECOND;
  }

  /**
   * Return the actual time value corresponding to an argument which
   * is a simulation time value in microseconds.
   *
   * @return Actual time (microseconds)
   */
  public long convertSimTimeToActualTime(long simTime) {
    return simTime + lastStartRealTime * 1000;
  }

  /**
   * Get currently used radio medium.
   *
   * @return Currently used radio medium
   */
  public RadioMedium getRadioMedium() {
    return currentRadioMedium;
  }

  /**
   * Return true is simulation is running.
   *
   * @return True if simulation is running
   */
  public boolean isRunning() {
    return isRunning;
  }

  /**
   * Get current simulation title (short description).
   *
   * @return Title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set simulation title.
   *
   * @param title
   *          New title
   */
  public void setTitle(String title) {
    var oldTitle = this.title;
    this.title = title;
    propertyChangeSupport.firePropertyChange(PROPERTY_TITLE, oldTitle, title);
  }

  /** Returns the simulation configuration. */
  public SimConfig getCfg() {
    return cfg;
  }

  /**
   * Returns the simulation state change triggers.
   */
  public EventTriggers<EventTriggers.Operation, Simulation> getSimulationStateTriggers() {
    return simulationStateTriggers;
  }

  /*
   * Returns the mote type add and remove triggers.
   */
  public EventTriggers<AddRemove, MoteType> getMoteTypeTriggers() {
    return moteTypeTriggers;
  }

  /*
   * Returns the mote add and remove triggers.
   */
  public EventTriggers<AddRemove, Mote> getMoteTriggers() {
    return moteTriggers;
  }

  /** Returns the mote relations triggers. */
  public EventTriggers<AddRemove, MoteRelation> getMoteRelationsTriggers() {
    return moteRelationsTriggers;
  }

  /** Returns the mote highlight triggers. */
  public EventTriggers<EventTriggers.Update, Mote> getMoteHighlightTriggers() {
    return moteHighlightTriggers;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.removePropertyChangeListener(listener);
  }

  public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(property, listener);
  }

  public void removePropertyChangeListener(String property, PropertyChangeListener listener) {
    propertyChangeSupport.removePropertyChangeListener(property, listener);
  }

  /** Exception for signaling the simulation that an emulator has stopped. */
  public static class SimulationStop extends RuntimeException {
    public SimulationStop(String emulator, String reason) {
      super(emulator + " requested simulation stop for " + reason);
    }
  }

  /** Structure to hold the simulation parameters. */
  public record SimConfig(String file, Long randomSeed, boolean autoStart, boolean updateSim,
                          String logDir,
                          Map<String, String> opts) {}
}
