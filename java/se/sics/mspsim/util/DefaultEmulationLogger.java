package se.sics.mspsim.util;

import java.io.PrintStream;

import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.EmulationLogger;
import se.sics.mspsim.core.LogListener;
import se.sics.mspsim.core.Loggable;
import se.sics.mspsim.core.MSP430Core;

public class DefaultEmulationLogger implements EmulationLogger {

  private final MSP430Core cpu;
  private final WarningMode defaultMode = WarningMode.PRINT;
  private final PrintStream out;
  private LogListener[] logListeners;

  public DefaultEmulationLogger(MSP430Core cpu, PrintStream out) {
    this.cpu = cpu;
    this.out = out;
  }

  protected WarningMode getMode(WarningType type) {
      return defaultMode;
  }

  @Override
  public void log(Loggable source, String message) {
      LogListener[] listeners = this.logListeners;
      if (listeners != null) {
          for (LogListener l : listeners) {
              l.log(source, message);
          }
      }
  }

  @Override
  public void logw(Loggable source, WarningType type, String message)
          throws EmulationException {
      switch (getMode(type)) {
      case SILENT:
          break;
      case PRINT:
          out.println(source.getID() + ": " + message);
          cpu.generateTrace(out);
          break;
      case EXCEPTION:
          out.println(source.getID() + ": " + message);
          cpu.generateTrace(out);
          throw new EmulationException(message);
      }

      LogListener[] listeners = this.logListeners;
      if (listeners != null) {
          for (LogListener l : listeners) {
              l.logw(source, type, message);
          }
      }
  }

  @Override
  public synchronized void addLogListener(LogListener listener) {
      logListeners = ArrayUtils.add(LogListener.class, logListeners, listener);
  }

  @Override
  public synchronized void removeLogListener(LogListener listener) {
      logListeners = ArrayUtils.remove(logListeners, listener);
  }
}
