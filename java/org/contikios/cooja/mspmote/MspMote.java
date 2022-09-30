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
 *
 */

package org.contikios.cooja.mspmote;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jdom.Element;
import org.contikios.cooja.ContikiError;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.WatchpointMote;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.motes.AbstractEmulatedMote;
import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.mspmote.interfaces.MspSerial;
import org.contikios.cooja.mspmote.plugins.CodeVisualizerSkin;
import org.contikios.cooja.mspmote.plugins.MspBreakpoint;
import org.contikios.cooja.plugins.Visualizer;

import se.sics.mspsim.cli.CommandContext;
import se.sics.mspsim.cli.CommandHandler;
import se.sics.mspsim.cli.LineListener;
import se.sics.mspsim.cli.LineOutputStream;
import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.LogListener;
import se.sics.mspsim.core.Loggable;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.EmulationLogger.WarningType;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.ui.ManagedWindow;
import se.sics.mspsim.ui.WindowManager;
import se.sics.mspsim.util.ComponentRegistry;
import se.sics.mspsim.util.ConfigManager;
import se.sics.mspsim.util.DebugInfo;
import se.sics.mspsim.util.ELF;
import se.sics.mspsim.util.MapEntry;
import se.sics.mspsim.util.MapTable;
import se.sics.mspsim.profiler.SimpleProfiler;

import org.contikios.cooja.mspmote.interfaces.MspClock;

/**
 * @author Fredrik Osterlind
 */
public abstract class MspMote extends AbstractEmulatedMote implements Mote, WatchpointMote {
  private static final Logger logger = LogManager.getLogger(MspMote.class);

  private final static int EXECUTE_DURATION_US = 1; /* We always execute in 1 us steps */

  {
    Visualizer.registerVisualizerSkin(CodeVisualizerSkin.class);
  }

  private final CommandHandler commandHandler;
  private MSP430 myCpu = null;
  private final MspMoteType myMoteType;
  private MspMoteMemory myMemory = null;
  protected MoteInterfaceHandler myMoteInterfaceHandler;
  public ComponentRegistry registry = null;

  /* Stack monitoring variables */
  private boolean stopNextInstruction = false;

  public GenericNode mspNode = null;

  public MspMote(MspMoteType moteType, Simulation simulation) throws MoteType.MoteTypeCreationException {
    super(simulation);
    myMoteType = moteType;
    try {
      debuggingInfo = moteType.getFirmwareDebugInfo();
    } catch (IOException e) {
      throw new MoteType.MoteTypeCreationException("Error: " + e.getMessage(), e);
    }
    commandHandler = new CommandHandler(System.out, System.err);
    /* Schedule us immediately */
    requestImmediateWakeup();
  }

  protected void initMote() {
    /* TODO Create COOJA-specific window manager */
    registry.removeComponent("windowManager");
    registry.registerComponent("windowManager", new WindowManager() {
      @Override
      public ManagedWindow createWindow(String name) {
        return new ManagedWindow() {
          @Override
          public void setVisible(boolean b) {
            logger.warn("setVisible() ignored");
          }

          @Override
          public void setTitle(String string) {
            logger.warn("setTitle() ignored");
          }

          @Override
          public void setSize(int width, int height) {
            logger.warn("setSize() ignored");
          }

          @Override
          public void setBounds(int x, int y, int width, int height) {
            logger.warn("setBounds() ignored");
          }

          @Override
          public void removeAll() {
            logger.warn("removeAll() ignored");
          }

          @Override
          public void pack() {
            logger.warn("pack() ignored");
          }

          @Override
          public boolean isVisible() {
            logger.warn("isVisible() return false");
            return false;
          }

          @Override
          public String getTitle() {
            logger.warn("getTitle() return \"\"");
            return "";
          }

          @Override
          public void add(Component component) {
            logger.warn("add() ignored");
          }
        };
      }
    });
  }

  /**
   * Abort execution immediately.
   * May for example be called by a breakpoint handler.
   */
  public void stopNextInstruction() {
    stopNextInstruction = true;
    getCPU().stop();
  }

  /**
   * @return MSP430 CPU
   */
  public MSP430 getCPU() {
    return myCpu;
  }

  public void setCPU(MSP430 cpu) {
    myCpu = cpu;
  }

  @Override
  public MemoryInterface getMemory() {
    return myMemory;
  }

  /**
   * Prepares CPU, memory and ELF module.
   *
   * @param node MSP430 cpu
   * @throws IOException Preparing mote failed
   */
  protected void prepareMote(GenericNode node) throws IOException {
    this.mspNode = node;

    node.setCommandHandler(commandHandler);

    node.setup(new ConfigManager());

    this.myCpu = node.getCPU();
    this.myCpu.setMonitorExec(true);
    this.myCpu.setTrace(0); /* TODO Enable */

    LogListener ll = new LogListener() {
      private final Logger mlogger = LogManager.getLogger("MSPSim");
      @Override
      public void log(Loggable source, String message) {
        mlogger.debug(getID() + ": " + source.getID() + ": " + message);
      }

      @Override
      public void logw(Loggable source, WarningType type, String message) throws EmulationException {
        mlogger.warn(getID() + ": " + "# " + source.getID() + "[" + type + "]: " + message);
      }
    };

    this.myCpu.getLogger().addLogListener(ll);

    Cooja.setProgressMessage("Loading " + myMoteType.getContikiFirmwareFile().getName());
    node.loadFirmware(((MspMoteType)getType()).getELF());

    /* Throw exceptions at bad memory access */
    /*myCpu.setThrowIfWarning(true);*/

    /* Create mote address memory */
    MapTable map = ((MspMoteType)getType()).getELF().getMap();
    myMemory = new MspMoteMemory(this, map.getAllEntries(), myCpu);

    myCpu.reset();
  }

  public CommandHandler getCLICommandHandler() {
    return commandHandler;
  }

  /* called when moteID is updated */
  public void idUpdated(int newID) {
  }

  @Override
  public MoteType getType() {
    return myMoteType;
  }

  @Override
  public MoteInterfaceHandler getInterfaces() {
    return myMoteInterfaceHandler;
  }

  private boolean booted = false;

  private long lastExecute = -1; /* Last time mote executed */

  private double jumpError = 0.;

  @Override
  public void execute(long time) {
    execute(time, EXECUTE_DURATION_US);
  }

  public void execute(long t, int duration) {
    MspClock clock = ((MspClock) (myMoteInterfaceHandler.getClock()));
    if(clock.getDeviation() == 1.0)
      regularExecute(clock, t, duration);
    else
      driftExecute(clock, t, duration);
  }

  private void regularExecute(MspClock clock, long t, int duration) {
    long nextExecute;
    /* Wait until mote boots */
    if (!booted && clock.getTime() < 0) {
      scheduleNextWakeup(t - clock.getTime());
      return;
    }
    booted = true;

    if (stopNextInstruction) {
      stopNextInstruction = false;
      scheduleNextWakeup(t);
      throw new RuntimeException("MSPSim requested simulation stop");
    }

    if (lastExecute < 0) {
      /* Always execute one microsecond the first time */
      lastExecute = t;
    }
    if (t < lastExecute) {
      throw new RuntimeException("Bad event ordering: " + lastExecute + " < " + t);
    }

    /* Execute MSPSim-based mote */
    /* TODO Try-catch overhead */
    try {
      nextExecute = myCpu.stepMicros(Math.max(0, t - lastExecute), duration) + duration + t;
      lastExecute = t;
    } catch (EmulationException e) {
      throw new ContikiError(e.getMessage(), getStackTrace(), e);
    }

    /* Schedule wakeup */
    if (nextExecute < t) {
      throw new RuntimeException(t + ": MSPSim requested early wakeup: " + nextExecute);
    }

    /*logger.debug(t + ": Schedule next wakeup at " + nextExecute);*/
    scheduleNextWakeup(nextExecute);

    if (stopNextInstruction) {
      stopNextInstruction = false;
      throw new RuntimeException("MSPSim requested simulation stop");
    }

    /* XXX TODO Reimplement stack monitoring using MSPSim internals */
  }

  private void driftExecute(MspClock clock, long t, int duration) {
    double deviation = clock.getDeviation();
    double invDeviation = 1.0 / deviation;
    long jump, executeDelta;
    double exactJump, exactExecuteDelta;

    /* Wait until mote boots */
    if (!booted && clock.getTime() < 0) {
      scheduleNextWakeup(t - clock.getTime());
      return;
    }
    booted = true;

    if (stopNextInstruction) {
      stopNextInstruction = false;
      scheduleNextWakeup(t);
      throw new RuntimeException("MSPSim requested simulation stop");
    }

    if (lastExecute < 0) {
      /* Always execute one microsecond the first time */
      lastExecute = t;
    }
    if (t < lastExecute) {
      throw new RuntimeException("Bad event ordering: " + lastExecute + " < " + t);
    }

    jump = Math.max(0, t - lastExecute);
    exactJump = jump * deviation;
    jump = (int)Math.floor(exactJump);
    jumpError += exactJump - jump;

    if(jumpError > 1.0) {
      jump++;
      jumpError -= 1.0;
    }

    /* Execute MSPSim-based mote */
    /* TODO Try-catch overhead */
    try {
      executeDelta = myCpu.stepMicros(jump, duration) + duration;
      lastExecute = t;
    } catch (EmulationException e) {
      throw new ContikiError(e.getMessage(), getStackTrace(), e);
    }

    exactExecuteDelta = executeDelta * invDeviation;
    executeDelta = (int)Math.floor(exactExecuteDelta);

    var nextExecute = executeDelta + t;

    /* Schedule wakeup */
    if (nextExecute < t) {
      throw new RuntimeException(t + ": MSPSim requested early wakeup: " + nextExecute);
    }

    /*logger.debug(t + ": Schedule next wakeup at " + nextExecute);*/
    scheduleNextWakeup(nextExecute);

    if (stopNextInstruction) {
      stopNextInstruction = false;
      throw new RuntimeException("MSPSim requested simulation stop");
    }

    /* XXX TODO Reimplement stack monitoring using MSPSim internals */
  }

  @Override
  public String getStackTrace() {
    return executeCLICommand("stacktrace");
  }

  public int executeCLICommand(String cmd, CommandContext context) {
    return commandHandler.executeCommand(cmd, context);
  }

  public String executeCLICommand(String cmd) {
    final StringBuilder sb = new StringBuilder();
    LineListener ll = new LineListener() {
      @Override
      public void lineRead(String line) {
        sb.append(line).append("\n");
      }
    };
    PrintStream po = new PrintStream(new LineOutputStream(ll));
    CommandContext c = new CommandContext(commandHandler, null, "", new String[0], 1, null);
    c.out = po;
    c.err = po;

    if (0 != executeCLICommand(cmd, c)) {
      sb.append("\nWarning: command failed");
    }

    return sb.toString();
  }

  @Override
  public int getCPUFrequency() {
    return myCpu.getDCOFrequency();
  }

  @Override
  public int getID() {
    return getInterfaces().getMoteID().getMoteID();
  }

  @Override
  public boolean setConfigXML(Simulation simulation, Collection<Element> configXML, boolean visAvailable) throws MoteType.MoteTypeCreationException {
    for (Element element: configXML) {
      String name = element.getName();
      if ("breakpoints".equals(name)) {
        for (Element elem : (Collection<Element>) element.getChildren()) {
          if (elem.getName().equals("breakpoint")) {
            MspBreakpoint breakpoint = new MspBreakpoint(this);
            if (!breakpoint.setConfigXML(elem.getChildren(), visAvailable)) {
              logger.warn("Could not restore breakpoint: " + breakpoint);
            } else {
              watchpoints.add(breakpoint);
            }
          }
        }
      } else if (name.equals("interface_config")) {
        String intfClass = element.getText().trim();

        /* Backwards compatibility: se.sics -> org.contikios */
        if (intfClass.startsWith("se.sics")) {
          intfClass = intfClass.replaceFirst("se\\.sics", "org.contikios");
        }

        var moteInterfaceClass = MoteInterfaceHandler.getInterfaceClass(simulation.getCooja(), this, intfClass);
        if (moteInterfaceClass == null) {
          logger.fatal("Could not load mote interface class: " + intfClass);
          return false;
        }

        MoteInterface moteInterface = getInterfaces().getInterfaceOfType(moteInterfaceClass);
        if (moteInterface == null) {
            logger.fatal("Could not find mote interface of class: " + moteInterfaceClass);
            return false;
        }
        moteInterface.setConfigXML(element.getChildren(), visAvailable);
      }
    }

    /* Schedule us immediately */
    requestImmediateWakeup();
    return true;
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    /* Breakpoints */
    Collection<Element> breakpoints = getWatchpointConfigXML();
    if (breakpoints != null && !breakpoints.isEmpty()) {
      var element = new Element("breakpoints");
      element.addContent(breakpoints);
      config.add(element);
    }

    // Mote interfaces
    for (MoteInterface moteInterface: getInterfaces().getInterfaces()) {
      var element = new Element("interface_config");
      element.setText(moteInterface.getClass().getName());

      Collection<Element> interfaceXML = moteInterface.getConfigXML();
      if (interfaceXML != null) {
        element.addContent(interfaceXML);
        config.add(element);
      }
    }

    return config;
  }

  @Override
  public String getExecutionDetails() {
    return executeCLICommand("stacktrace");
  }

  @Override
  public String getPCString() {
    int pc = myCpu.getPC();
    ELF elf = myCpu.getRegistry().getComponent(ELF.class);
    DebugInfo di = elf.getDebugInfo(pc);

    /* Following code examples from MSPsim, DebugCommands.java */
    if (di == null) {
      di = elf.getDebugInfo(pc + 1);
    }
    if (di == null) {
      /* Return PC value */
      SimpleProfiler sp = (SimpleProfiler)myCpu.getProfiler();
      try {
        MapEntry mapEntry = sp.getCallMapEntry(0);
        if (mapEntry != null) {
          String file = mapEntry.getFile();
          if (file != null) {
            if (file.indexOf('/') >= 0) {
              file = file.substring(file.lastIndexOf('/')+1);
            }
          }
          String name = mapEntry.getName();
          return file + ":?:" + name;
        }
        return String.format("*%02x", pc);
      } catch (Exception e) {
        return null;
      }
    }

    int lineNo = di.getLine();
    String file = di.getFile();
    file = file==null?"?":file;
    if (file.contains("/")) {
      /* strip path */
      file = file.substring(file.lastIndexOf('/')+1);
    }

    String function = di.getFunction();
    function = function==null?"":function;
    if (function.contains(":")) {
      /* strip arguments */
      function = function.substring(0, function.lastIndexOf(':'));
    }
    if (function.equals("* not available")) {
      function = "?";
    }
    return file + ":" + lineNo + ":" + function;

    /*return executeCLICommand("line " + myCpu.getPC());*/
  }


  /* WatchpointMote */
  private final ArrayList<WatchpointListener> watchpointListeners = new ArrayList<>();
  private final ArrayList<MspBreakpoint> watchpoints = new ArrayList<>();
  private final HashMap<File, HashMap<Integer, Integer>> debuggingInfo;

  @Override
  public void addWatchpointListener(WatchpointListener listener) {
    watchpointListeners.add(listener);
  }
  @Override
  public void removeWatchpointListener(WatchpointListener listener) {
    watchpointListeners.remove(listener);
  }
  @Override
  public WatchpointListener[] getWatchpointListeners() {
    return watchpointListeners.toArray(new WatchpointListener[0]);
  }

  @Override
  public Watchpoint addBreakpoint(File codeFile, int lineNr, int address) {
    MspBreakpoint bp = new MspBreakpoint(this, address, codeFile, lineNr);
    watchpoints.add(bp);

    for (WatchpointListener listener: watchpointListeners) {
      listener.watchpointsChanged();
    }
    return bp;
  }
  @Override
  public void removeBreakpoint(Watchpoint watchpoint) {
    ((MspBreakpoint)watchpoint).unregisterBreakpoint();
    watchpoints.remove(watchpoint);

    for (WatchpointListener listener: watchpointListeners) {
      listener.watchpointsChanged();
    }
  }
  @Override
  public Watchpoint[] getBreakpoints() {
    return watchpoints.toArray(new Watchpoint[0]);
  }

  @Override
  public boolean breakpointExists(int address) {
    if (address < 0) {
      return false;
    }
    for (Watchpoint watchpoint: watchpoints) {
      if (watchpoint.getExecutableAddress() == address) {
        return true;
      }
    }
    return false;
  }
  @Override
  public boolean breakpointExists(File file, int lineNr) {
    for (Watchpoint watchpoint: watchpoints) {
      if (watchpoint.getCodeFile() == null) {
        continue;
      }
      if (watchpoint.getCodeFile().compareTo(file) != 0) {
        continue;
      }
      if (watchpoint.getLineNumber() != lineNr) {
        continue;
      }
      return true;
    }
    return false;
  }

  @Override
  public int getExecutableAddressOf(File file, int lineNr) {
    if (file == null || lineNr < 0 || debuggingInfo == null) {
      return -1;
    }

    /* Match file */
    HashMap<Integer, Integer> lineTable = debuggingInfo.get(file);
    if (lineTable == null) {
      for (var entry : debuggingInfo.entrySet()) {
        File f = entry.getKey();
        if (f != null && f.getName().equals(file.getName())) {
          lineTable = entry.getValue();
          break;
        }
      }
    }
    if (lineTable == null) {
      return -1;
    }

    /* Match line number */
    Integer address = lineTable.get(lineNr);
    if (address != null) {
      for (var entry : lineTable.entrySet()) {
        Integer l = entry.getKey();
        if (l != null && l == lineNr) {
          /* Found line address */
          return entry.getValue();
        }
      }
    }

    return -1;
  }

  private long lastBreakpointCycles = -1;
  public void signalBreakpointTrigger(MspBreakpoint b) {
    if (lastBreakpointCycles == myCpu.cycles) {
      return;
    }

    lastBreakpointCycles = myCpu.cycles;
    if (b.stopsSimulation() && getSimulation().isRunning()) {
      /* Stop simulation immediately */
      stopNextInstruction();
    }

    /* Notify listeners */
    WatchpointListener[] listeners = getWatchpointListeners();
    for (WatchpointListener listener: listeners) {
      listener.watchpointTriggered(b);
    }
  }

  public Collection<Element> getWatchpointConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    for (MspBreakpoint breakpoint: watchpoints) {
      Element element = new Element("breakpoint");
      element.addContent(breakpoint.getConfigXML());
      config.add(element);
    }

    return config;
  }
}
