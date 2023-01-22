package se.sics.mspsim.core;

public interface EmulationLogger {

  /* warning mode for CPU errors such as unaligned word access */
  enum WarningMode {SILENT, PRINT, EXCEPTION}

  /* warning types */
  enum WarningType {
      EMULATION_ERROR, EXECUTION,
      MISALIGNED_READ, MISALIGNED_WRITE,
      ADDRESS_OUT_OF_BOUNDS_READ, ADDRESS_OUT_OF_BOUNDS_WRITE,
      ILLEGAL_IO_WRITE, VOID_IO_READ, VOID_IO_WRITE
  }

  void log(Loggable source, String message);
  void logw(Loggable source, WarningType type, String message) throws EmulationException;

  void addLogListener(LogListener listener);
  void removeLogListener(LogListener listener);
}
