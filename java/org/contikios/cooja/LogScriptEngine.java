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
 *
 */

package org.contikios.cooja;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.swing.JTextArea;
import org.contikios.cooja.SimEventCentral.LogOutputEvent;
import org.contikios.cooja.SimEventCentral.LogOutputListener;
import org.contikios.cooja.plugins.ScriptRunner;
import org.contikios.cooja.script.ScriptLog;
import org.contikios.cooja.script.ScriptMote;
import org.contikios.cooja.script.ScriptParser;
import org.openjdk.nashorn.api.scripting.NashornScriptEngine;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and executes a Contiki test script.
 * A Contiki test script is a Javascript that depends on a single simulation,
 * and reacts to mote log output (such as printf()s).
 *
 * @see ScriptRunner
 * @author Fredrik Osterlind
 */
public class LogScriptEngine {
  private static final Logger logger = LoggerFactory.getLogger(LogScriptEngine.class);
  private static final long DEFAULT_TIMEOUT = 20*60*1000*Simulation.MILLISECOND; /* 1200s = 20 minutes */

  private final NashornScriptEngine engine;

  private final BufferedWriter logWriter; // For non-GUI tests.

  private final LogOutputListener logOutputListener = new LogOutputListener() {
    @Override
    public void newLogOutput(LogOutputEvent ev) {
      if (scriptThread == null || !scriptThread.isAlive()) {
        return;
      }

      // Only called from the simulation loop.
      final var mote = ev.getMote();
      try {
        // Update script variables.
        engine.put("mote", mote);
        engine.put("id", mote.getID());
        engine.put("time", ev.getTime());
        engine.put("msg", ev.msg);

        stepScript();
      } catch (UndeclaredThrowableException e) {
        logger.error("Exception: " + e.getMessage(), e);
        if (Cooja.isVisualized()) {
          Cooja.showErrorDialog(e.getMessage(), e, false);
        }
        deactivateScript();
        simulation.stopSimulation(1);
      }
    }
  };

  private Semaphore semaphoreScript; /* Semaphores blocking script/simulation */
  private Semaphore semaphoreSim;
  private Thread scriptThread; /* Script thread */
  private final Simulation simulation;

  private long timeout;
  private long startTime;
  private long startRealTime;
  private final JTextArea textArea;

  LogScriptEngine(Simulation simulation, String nashornArgs, int logNumber, JTextArea logTextArea) {
    engine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine(nashornArgs);
    this.simulation = simulation;
    textArea = logTextArea;
    simulation.getEventCentral().addLogOutputListener(logOutputListener);
    if (!Cooja.isVisualized()) {
      var logName = logNumber == 0 ? "COOJA.testlog" : String.format("COOJA-%02d.testlog", logNumber);
      var logFile = Path.of(simulation.getCfg().logDir(), logName);
      try {
        logWriter = Files.newBufferedWriter(logFile, UTF_8);
        logWriter.write("Random seed: " + simulation.getRandomSeed() + "\n");
        logWriter.flush();
      } catch (IOException e) {
        logger.error("Could not create {}: {}", logFile, e.toString());
        throw new RuntimeException(e);
      }
      return;
    }
    logWriter = null;
  }

  /* Only called from the simulation loop */
  private void stepScript() {
    /* Release script - halt simulation */
    Semaphore semScript = semaphoreScript;
    Semaphore semSim = semaphoreSim;
    if (semScript == null || semSim == null) {
      return;
    }
    semScript.release();

    /* ... script executing ... */

    try {
      semSim.acquire();
    } catch (InterruptedException e1) {
      e1.printStackTrace();
      // FIXME: Something called interrupt() on this thread, computation should stop.
    }

    /* ... script is now again waiting for script semaphore ... */
  }

  public void scriptLog(String msg) {
    if (Cooja.isVisualized()) {
      java.awt.EventQueue.invokeLater(() -> {
        textArea.append(msg);
        textArea.setCaretPosition(textArea.getText().length());
      });
      return;
    }
    try {
      logWriter.write(msg);
      logWriter.flush();
    } catch (IOException e) {
      logger.error("Error when writing to test log file: " + msg, e);
    }
  }

  void closeLog() {
    simulation.getEventCentral().removeLogOutputListener(logOutputListener);
    if (Cooja.isVisualized()) {
      return;
    }
    try {
      logWriter.write("Test ended at simulation time: " + simulation.getSimulationTime() + "\n");
      logWriter.close();
    } catch (IOException e) {
      logger.error("Could not close log file:", e);
    }
  }

  /**
   * Deactivate script
   */
  public void deactivateScript() {
    timeoutEvent.remove();
    timeoutProgressEvent.remove();

    engine.put("SHUTDOWN", true);

    try {
      if (semaphoreScript != null) {
        semaphoreScript.release(100);
      }
    } catch (Exception e) {
    } finally {
      semaphoreScript = null;
    }
    try {
      if (semaphoreSim != null) {
        semaphoreSim.release(100);
      }
    } catch (Exception e) {
    } finally {
      semaphoreSim = null;
    }

    if (scriptThread != null &&
        scriptThread != Thread.currentThread() /* XXX May deadlock */ ) {
      try {
        scriptThread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
        // FIXME: Something called interrupt() on this thread, computation needs to stop.
      }
    }
    scriptThread = null;
  }

  /** Take a user script and return a compiled script that can be activated.
   *  Does not alter internal engine state that is difficult to undo. */
  public CompiledScript compileScript(String code) throws ScriptException {
    ScriptParser parser = new ScriptParser(code);
    timeout = parser.getTimeoutTime();
    if (timeout < 0) {
      timeout = DEFAULT_TIMEOUT;
    }
    logger.info("Script timeout in " + (timeout/Simulation.MILLISECOND) + " ms");
    return engine.compile(parser.getJSCode());
  }

  /** Allocate semaphores, set up the internal state of the engine, and start the script thread. */
  public boolean activateScript(final CompiledScript script) {
    semaphoreScript = new Semaphore(1);
    semaphoreSim = new Semaphore(0);
    try {
      semaphoreScript.acquire();
    } catch (InterruptedException e) {
      logger.error("Error when creating engine: " + e.getMessage(), e);
      return false;
    }
    // Setup script variables.
    engine.put("TIMEOUT", false);
    engine.put("SHUTDOWN", false);
    engine.put("SEMAPHORE_SCRIPT", semaphoreScript);
    engine.put("SEMAPHORE_SIM", semaphoreSim);
    engine.put("log", scriptLog);
    engine.put("global", new HashMap<>());
    engine.put("sim", simulation);
    engine.put("gui", simulation.getCooja());
    engine.put("mote", null);
    engine.put("msg", "");
    engine.put("node", new ScriptMote());
    scriptThread = new Thread(() -> {
      int rv = 1;
      try {
        rv = (int) Objects.requireNonNullElse(script.eval(), 1);
      } catch (Exception e) {
        logger.error("Script error:", e);
        if (Cooja.isVisualized()) {
          Cooja.showErrorDialog("Script error", e, false);
        }
      }
      // rv == -1 means something else is shutting down Cooja, for example the SerialSocket commands in 17-tun-rpl-br.
      if (rv != -1) {
        scriptLog(rv == 0 ? "TEST OK\n" : "TEST FAILED\n");
      }
      deactivateScript();
      simulation.stopSimulation(rv > 0 ? rv : null);
    }, "script");
    scriptThread.start();
    try {
      semaphoreSim.acquire();
    } catch (InterruptedException e) {
      logger.error("Thread interrupted:", e);
      deactivateScript();
      return false;
    }
    startRealTime = System.currentTimeMillis();
    startTime = simulation.getSimulationTime();
    simulation.invokeSimulationThread(() -> {
      simulation.scheduleEvent(timeoutProgressEvent, startTime + Math.max(1000, timeout / 20));
      simulation.scheduleEvent(timeoutEvent, startTime + timeout);
    });
    return true;
  }

  private final TimeEvent timeoutEvent = new TimeEvent() {
    @Override
    public void execute(long t) {
      logger.info("Timeout event @ " + t);
      engine.put("TIMEOUT", true);
      stepScript();
      deactivateScript();
      simulation.stopSimulation(); // stepScript will set return value.
    }
  };
  private final TimeEvent timeoutProgressEvent = new TimeEvent() {
    @Override
    public void execute(long t) {
      simulation.scheduleEvent(this, t + Math.max(1000, timeout / 20));

      double progress = 1.0*(t - startTime)/timeout;
      long realDuration = System.currentTimeMillis()-startRealTime;
      double estimatedLeft = 1.0*realDuration/progress - realDuration;
      if (estimatedLeft == 0) estimatedLeft = 1;
      // String.format is still slow(ish) in Java 17 and will show up in performance profiles,
      // so compute+format the percentage completed and time remaining by hand.
      int percentage = (int) (100 * progress);
      double secondsRemaining = estimatedLeft / 1000;
      long seconds = (long) secondsRemaining;
      int tenthOfSeconds = (int) Math.round((10 * (secondsRemaining - (double)seconds)));
      if (tenthOfSeconds == 10) {
        seconds++;
        tenthOfSeconds = 0;
      }
      logger.info("{}{}% completed, {}{}.{} sec remaining", (percentage < 10 ? " " : ""), percentage,
              (seconds < 10 ? " " : ""), seconds, tenthOfSeconds);
    }
  };

  private final ScriptLog scriptLog = new ScriptLog() {
    @Override
    public void log(String msg) {
      scriptLog(msg);
    }
    @Override
    public void append(String filename, String msg) {
      try (var out = Files.newBufferedWriter(Path.of(filename), UTF_8, CREATE, APPEND)) {
        out.write(msg);
      } catch (Exception e) {
        logger.warn("Test append failed: " + filename + ": " + e.getMessage());
      }
    }
    @Override
    public void writeFile(String filename, String msg) {
      try (var out = Files.newBufferedWriter(Path.of(filename), UTF_8)) {
        out.write(msg);
      } catch (Exception e) {
        logger.warn("Write file failed: " + filename + ": " + e.getMessage());
      }
    }

    @Override
    public void generateMsg(final Mote currentMote, final long delay, final String msg) {
      final TimeEvent generateEvent = new TimeEvent() {
        @Override
        public void execute(long t) {
          if (scriptThread == null || !scriptThread.isAlive()) {
            logger.info("script thread not alive. try deactivating script.");
            return;
          }

          /* Update script variables */
          engine.put("mote", currentMote);
          engine.put("id", currentMote.getID());
          engine.put("time", t);
          engine.put("msg", msg);

          stepScript();
        }
      };
      simulation.invokeSimulationThread(() ->
          simulation.scheduleEvent(generateEvent, simulation.getSimulationTime() + delay*Simulation.MILLISECOND));
    }
  };
}
