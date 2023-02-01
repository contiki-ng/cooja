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
import java.io.PrintStream;
import org.contikios.cooja.Simulation.SimulationStop;
import org.contikios.cooja.WatchpointMote;
import org.contikios.cooja.ContikiError;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.motes.AbstractEmulatedMote;
import org.contikios.cooja.mspmote.plugins.CodeVisualizerSkin;
import org.contikios.cooja.mspmote.plugins.MspBreakpoint;
import org.contikios.cooja.plugins.Visualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import se.sics.mspsim.profiler.SimpleProfiler;

/**
 * @author Fredrik Osterlind
 */
public abstract class MspMote extends AbstractEmulatedMote<MspMoteType, MSP430, MspMoteMemory> implements WatchpointMote {
  private static final Logger logger = LoggerFactory.getLogger(MspMote.class);

  private final static int EXECUTE_DURATION_US = 1; /* We always execute in 1 us steps */

  static {
    if (Cooja.isVisualized()) {
      Visualizer.registerVisualizerSkin(CodeVisualizerSkin.class);
    }
  }

  private final CommandHandler commandHandler = new CommandHandler(System.out, System.err);
  public final ComponentRegistry registry;

  /* Stack monitoring variables */
  private boolean stopNextInstruction;

  public MspMote(MspMoteType moteType, Simulation sim, GenericNode node) throws MoteType.MoteTypeCreationException {
    super(moteType, node.getCPU(), new MspMoteMemory(moteType.getEntries(node), node.getCPU()), sim);
    registry = node.getRegistry();
    node.setCommandHandler(commandHandler);
    node.setup(new ConfigManager());
    myCpu.setMonitorExec(true);
    myCpu.setTrace(0); /* TODO Enable */
    myCpu.getLogger().addLogListener(new LogListener() {
      private static final Logger mlogger = LoggerFactory.getLogger("MSPSim");
      @Override
      public void log(Loggable source, String message) {
        mlogger.debug(getID() + ": " + source.getID() + ": " + message);
      }

      @Override
      public void logw(Loggable source, WarningType type, String message) throws EmulationException {
        mlogger.warn(getID() + ": " + "# " + source.getID() + "[" + type + "]: " + message);
      }
    });
    // Throw exceptions at bad memory access.
    //myCpu.setThrowIfWarning(true);
    myCpu.reset();
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
    // Schedule us immediately.
    requestImmediateWakeup();
  }

  @Override
  public long getCPUCycles() {
    return myCpu.cpuCycles;
  }

  @Override
  public void stopNextInstruction() {
    stopNextInstruction = true;
    getCPU().stop();
  }

  public CommandHandler getCLICommandHandler() {
    return commandHandler;
  }

  /* called when moteID is updated */
  public void idUpdated(int newID) {
  }

  private boolean booted;

  private long lastExecute = -1; /* Last time mote executed */

  private double jumpError;

  @Override
  protected void execute(long time) {
    execute(time, EXECUTE_DURATION_US);
  }

  void execute(long t, int duration) {
    var clock = moteInterfaces.getClock();
    // Wait until mote boots.
    if (!booted && clock.getTime() < 0) {
      scheduleNextWakeup(t - clock.getTime());
      return;
    }
    booted = true;

    if (stopNextInstruction) {
      stopNextInstruction = false;
      scheduleNextWakeup(t);
      throw new SimulationStop("MSPSim", "breakpoint");
    }

    if (lastExecute < 0) {
      // Always execute one microsecond the first time.
      lastExecute = t;
    }
    assert t >= lastExecute : "Bad event ordering: " + lastExecute + " < " + t;
    long nextExecute = driftExecute(clock.getDeviation(), t, duration);
    lastExecute = t;
    // Schedule wakeup.
    assert nextExecute >= t : t + ": MSPSim requested early wakeup: " + nextExecute;
    scheduleNextWakeup(nextExecute);

    if (stopNextInstruction) {
      stopNextInstruction = false;
      throw new SimulationStop("MSPSim", "breakpoint");
    }

    // TODO: Reimplement stack monitoring using MSPSim internals.
  }

  private long driftExecute(double deviation, long t, int duration) {
    long jump = Math.max(0, t - lastExecute);
    if (deviation != 1.0) {
      double exactJump = jump * deviation;
      jump = (int) Math.floor(exactJump);
      jumpError += exactJump - jump;

      if (jumpError > 1.0) {
        jump++;
        jumpError -= 1.0;
      }
    }
    /* Execute MSPSim-based mote */
    /* TODO Try-catch overhead */
    long executeDelta;
    try {
      executeDelta = myCpu.stepMicros(jump, duration) + duration;
    } catch (EmulationException e) {
      throw new ContikiError(e.getMessage(), getStackTrace(), e);
    }

    if (deviation != 1.0) {
      double invDeviation = 1.0 / deviation;
      double exactExecuteDelta = executeDelta * invDeviation;
      executeDelta = (int) Math.floor(exactExecuteDelta);
    }
    return executeDelta + t;
  }

  @Override
  public String getStackTrace() {
    return executeCLICommand("stacktrace");
  }

  public int executeCLICommand(String cmd, CommandContext context) {
    return commandHandler.executeCommand(cmd, context);
  }

  String executeCLICommand(String cmd) {
    final StringBuilder sb = new StringBuilder();
    LineListener ll = line -> sb.append(line).append("\n");
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
  public String getExecutionDetails() {
    return getStackTrace();
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

  @Override
  protected Watchpoint createBreakpoint() {
    return new MspBreakpoint(this);
  }

  @Override
  protected Watchpoint createBreakpoint(long address, File codeFile, int lineNr) {
    return new MspBreakpoint(this, address, codeFile, lineNr);
  }
}
