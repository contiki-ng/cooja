package se.sics.mspsim.core;

import se.sics.mspsim.core.EmulationLogger.WarningType;

public interface LogListener {

    void log(Loggable source, String message);
    void logw(Loggable source, WarningType type, String message) throws EmulationException;

}
