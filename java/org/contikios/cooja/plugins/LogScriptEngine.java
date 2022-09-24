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

package org.contikios.cooja.plugins;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Observer;
import java.util.concurrent.Semaphore;
import javax.script.ScriptException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.SimEventCentral.LogOutputEvent;
import org.contikios.cooja.SimEventCentral.LogOutputListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.openjdk.nashorn.api.scripting.NashornScriptEngine;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

/**
 * Loads and executes a Contiki test script.
 * A Contiki test script is a Javascript that depends on a single simulation,
 * and reacts to mote log output (such as printf()s).
 *
 * @see ScriptRunner
 * @author Fredrik Osterlind
 */
public class LogScriptEngine {
  private static final Logger logger = LogManager.getLogger(LogScriptEngine.class);
  private static final long DEFAULT_TIMEOUT = 20*60*1000*Simulation.MILLISECOND; /* 1200s = 20 minutes */

  private final NashornScriptEngine engine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();

  /* Log output listener */
  private final LogOutputListener logOutputListener = new LogOutputListener() {
    @Override
    public void moteWasAdded(Mote mote) {
    }
    @Override
    public void moteWasRemoved(Mote mote) {
    }
    @Override
    public void newLogOutput(LogOutputEvent ev) {
      if (scriptThread == null || !scriptThread.isAlive()) {
        logger.warn("No script thread, deactivate script.");
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
        logger.fatal("Exception: " + e.getMessage(), e);
        if (Cooja.isVisualized()) {
          Cooja.showErrorDialog(Cooja.getTopParentContainer(),
              e.getMessage(),
              e, false);
        }
        simulation.stopSimulation();
      }
    }
    @Override
    public void removedLogOutput(LogOutputEvent ev) {
    }
  };

  private Semaphore semaphoreScript = null; /* Semaphores blocking script/simulation */
  private Semaphore semaphoreSim = null;
  private Thread scriptThread = null; /* Script thread */
  private Observer scriptLogObserver = null;

  private final Simulation simulation;

  private boolean scriptActive = false;

  private long timeout;
  private long startTime;
  private long startRealTime;

  public LogScriptEngine(Simulation simulation) {
    this.simulation = simulation;
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

  public void setScriptLogObserver(Observer observer) {
    scriptLogObserver = observer;
  }

  /**
   * Deactivate script
   */
  public void deactivateScript() {
    if (!scriptActive) {
      return;
    }
    scriptActive = false;

    timeoutEvent.remove();
    timeoutProgressEvent.remove();

    simulation.getEventCentral().removeLogOutputListener(logOutputListener);

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

  public boolean activateScript(String scriptCode) throws ScriptException {
    if (scriptActive) {
      return false;
    }
    scriptActive = true;

    if (semaphoreScript != null) {
      logger.fatal("semaphoreScript was not reset correctly");
      semaphoreScript.release(100);
      semaphoreScript = null;
      throw new RuntimeException("semaphoreScript was not reset correctly");
    }
    if (semaphoreSim != null) {
      logger.fatal("semaphoreSim was not reset correctly");
      semaphoreSim.release(100);
      semaphoreSim = null;
      throw new RuntimeException("semaphoreSim was not reset correctly");
    }
    scriptThread = null;

    /* Parse current script */
    ScriptParser parser = new ScriptParser(scriptCode);
    String jsCode = parser.getJSCode();

    timeout = parser.getTimeoutTime();
    if (timeout < 0) {
      timeout = DEFAULT_TIMEOUT;
      logger.info("Default script timeout in " + (timeout/Simulation.MILLISECOND) + " ms");
    } else {
      logger.info("Script timeout in " + (timeout/Simulation.MILLISECOND) + " ms");
    }

    final var prog = engine.compile(jsCode);

    /* Setup script control */
    semaphoreScript = new Semaphore(1);
    semaphoreSim = new Semaphore(1);
    engine.put("TIMEOUT", false);
    engine.put("SHUTDOWN", false);
    engine.put("SEMAPHORE_SCRIPT", semaphoreScript);
    engine.put("SEMAPHORE_SIM", semaphoreSim);

    try {
      semaphoreScript.acquire();
    } catch (InterruptedException e) {
      logger.fatal("Error when creating engine: " + e.getMessage(), e);
      scriptActive = false;
      return false;
    }
    ThreadGroup group = new ThreadGroup("script") {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        while (e.getCause() != null) {
          e = e.getCause();
        }
        if (e.getMessage() != null &&
            e.getMessage().contains("test script killed") ) {
          /* Ignore normal shutdown exceptions */
        } else {
          logger.fatal("Script error:", e);
        }
      }
    };
    scriptThread = new Thread(group, new Runnable() {
      @Override
      public void run() {
        try {
          prog.eval();
        } catch (ScriptException | RuntimeException e) {
          Throwable throwable = e;
          while (throwable.getCause() != null) {
            throwable = throwable.getCause();
          }

          if (throwable.getMessage() != null &&
              throwable.getMessage().contains("test script killed") ) {
            logger.debug("Test script finished");
          } else {
            logger.fatal("Script error:", e);
            if (!Cooja.isVisualized()) {
              logger.fatal("Test script error, terminating Cooja.");
              simulation.getCooja().doQuit(false, 1);
            }

            deactivateScript();
            simulation.stopSimulation();
            Cooja.showErrorDialog(Cooja.getTopParentContainer(), "Script error", e, false);
          }
        }
      }
    }, "script");

    /* Setup simulation observers */
    simulation.getEventCentral().addLogOutputListener(logOutputListener);

    /* Create script output logger */
    engine.put("log", scriptLog);
    engine.put("global", new HashMap<>());
    engine.put("sim", simulation);
    engine.put("gui", simulation.getCooja());
    engine.put("msg", "");
    engine.put("node", new ScriptMote());

    // Script context initialized (engine.put calls), start thread that runs script to the first semaphore.
    scriptThread.start();
    // Wait for script thread to reach barrier in the beginning of the JavaScript run function.
    while (!semaphoreScript.hasQueuedThreads()) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        // FIXME: Something called interrupt() on this thread, stop the computation.
      }
    }
    startRealTime = System.currentTimeMillis();
    startTime = simulation.getSimulationTime();

    timeoutProgressEvent.remove();
    simulation.scheduleEvent(timeoutProgressEvent, startTime + Math.max(1000, timeout / 20));
    timeoutEvent.remove();
    simulation.scheduleEvent(timeoutEvent, startTime + timeout);
    return true;
  }

  private final TimeEvent timeoutEvent = new TimeEvent() {
    @Override
    public void execute(long t) {
      if (!scriptActive) {
        return;
      }
      logger.info("Timeout event @ " + t);
      engine.put("TIMEOUT", true);
      stepScript();
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
      logger.info(String.format("%2.0f%% completed, %2.1f sec remaining", 100*progress, estimatedLeft/1000));
    }
  };

  private final ScriptLog scriptLog = new ScriptLog() {
    @Override
    public void log(String msg) {
      if (scriptLogObserver != null) {
        scriptLogObserver.update(null, msg);
      }
    }
    @Override
    public void append(String filename, String msg) {
      try (var out = Files.newBufferedWriter(Paths.get(filename), UTF_8, CREATE, APPEND)) {
        out.write(msg);
      } catch (Exception e) {
        logger.warn("Test append failed: " + filename + ": " + e.getMessage());
      }
    }
    @Override
    public void writeFile(String filename, String msg) {
      try (var out = Files.newBufferedWriter(Paths.get(filename), UTF_8)) {
        out.write(msg);
      } catch (Exception e) {
        logger.warn("Write file failed: " + filename + ": " + e.getMessage());
      }
    }

    @Override
    public void testOK() {
      logger.info("TEST OK\n");
      log("TEST OK\n");
      deactivate(0);
    }
    @Override
    public void testFailed() {
      logger.warn("TEST FAILED\n");
      log("TEST FAILED\n");
      deactivate(1);
    }
    private void deactivate(final int exitCode) {
      deactivateScript();
      simulation.stopSimulation(false);
      if (!Cooja.isVisualized()) {
        new Thread(() -> simulation.getCooja().doQuit(false, exitCode), "Cooja.doQuit").start();
      }
      throw new RuntimeException("test script killed");
    }

    @Override
    public void generateMessage(final long delay, final String msg) {
      final Mote currentMote = (Mote) engine.get("mote");
      final TimeEvent generateEvent = new TimeEvent() {
        @Override
        public void execute(long t) {
          if (scriptThread == null ||
              !scriptThread.isAlive()) {
            logger.info("script thread not alive. try deactivating script.");
            /*scriptThread.isInterrupted()*/
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
      simulation.invokeSimulationThread(new Runnable() {
        @Override
        public void run() {
          simulation.scheduleEvent(
              generateEvent,
              simulation.getSimulationTime() + delay*Simulation.MILLISECOND);
        }
      });
    }
  };
}
