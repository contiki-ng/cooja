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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;

import org.jdom.Element;

import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.util.ArrayUtils;

/**
 * Simulation event central. Simplifies implementations of plugins that observe
 * motes and mote interfaces by keeping track of added and removed motes. For a
 * selected set of interfaces, the event central also maintains an event
 * history.
 * 
 * @see LogOutputEvent
 * @author Fredrik Osterlind
 */
public class SimEventCentral {
  private final Simulation simulation;

  public SimEventCentral(Simulation simulation) {
    this.simulation = simulation;
  }

  /* GENERIC */
  private static class MoteEvent {
    public static int _ID_COUNTER = 0; /* Debugging */
    public final int ID; /* Debugging */

    private final Mote mote;
    private final long time;

    public MoteEvent(Mote mote, long time) {
      ID = _ID_COUNTER++;

      this.mote = mote;
      this.time = time;
    }
    public Mote getMote() {
      return mote;
    }
    public long getTime() {
      return time;
    }

    @Override
    public String toString() {
      return String.valueOf(ID);
    }
  }
  /** Help class for maintaining mote-specific observations */
  private record MoteObservation(Mote mote, Observable observable, Observer observer) {
    public MoteObservation {
      observable.addObserver(observer);
    }
    public void disconnect() {
      observable.deleteObserver(observer);
    }
  }
  private final ArrayList<MoteObservation> moteObservations = new ArrayList<>();

  
  /* ADDED/REMOVED MOTES */
  public interface MoteCountListener {
    void moteWasAdded(Mote mote);
    void moteWasRemoved(Mote mote);
  }
  /** Mote count notifications. */
  private MoteCountListener[] moteCountListeners = new MoteCountListener[0];
  private final Observer moteCountObserver = new Observer() {
    @Override
    public void update(Observable obs, Object obj) {
      if (!(obj instanceof Mote)) {
        return;
      }
      Mote evMote = (Mote) obj;

      /* Check whether mote was added or removed */
      Mote[] allMotes = simulation.getMotes();
      boolean exists = false;
      for (Mote m: allMotes) {
        if (m == evMote) {
          exists = true;
          break;
        }
      }

      if (exists) {
        /* Mote was added */
        if (logOutputListeners.length > 0) {
          // Add another log output observation (supports multiple log interfaces per mote).
          for (MoteInterface mi: evMote.getInterfaces().getInterfaces()) {
            if (mi instanceof Log) {
              moteObservations.add(new MoteObservation(evMote, mi, logOutputObserver));
            }
          }
        }
        /* Notify external listeners */
        for (MoteCountListener l: moteCountListeners) {
          l.moteWasAdded(evMote);
        }
      } else {
        /* Mote was removed */
        // Disconnect and remove mote observations.
        MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
        for (MoteObservation o: observations) {
          if (o.mote() == evMote) {
            o.disconnect();
            moteObservations.remove(o);
          }
        }
        /* Notify external listeners */
        for (MoteCountListener l: moteCountListeners) {
          l.moteWasRemoved(evMote);
        }
      }
    }
  };
  public void addMoteCountListener(MoteCountListener listener) {
    if (moteCountListeners.length == 0) {
      /* Observe simulation for added/removed motes */
      simulation.addObserver(moteCountObserver);
    }

    moteCountListeners = ArrayUtils.add(moteCountListeners, listener);
  }
  public void removeMoteCountListener(MoteCountListener listener) {
    moteCountListeners = ArrayUtils.remove(moteCountListeners, listener);

    if (moteCountListeners.length == 0) {
      /* Stop observing simulation for added/removed motes */
      simulation.deleteObserver(moteCountObserver);
    }
  }


  /* LOG OUTPUT */
  public static class LogOutputEvent extends MoteEvent {
    public final String msg;
    public LogOutputEvent(Mote mote, long time, String msg) {
      super(mote, time);
      this.msg = msg;
    }
    public String getMessage() {
      return msg;
    }
  }
  /** Default buffer sizes. */
  private int logOutputBufferSize = Integer.parseInt(Cooja.getExternalToolsSetting("BUFFERSIZE_LOGOUTPUT", "" + 40000));
  private final ArrayDeque<LogOutputEvent> logOutputEvents = new ArrayDeque<>();
  public interface LogOutputListener extends MoteCountListener {
    void removedLogOutput(LogOutputEvent ev);
    void newLogOutput(LogOutputEvent ev);
  }
  /** Log output: notifications and history */
  private LogOutputListener[] logOutputListeners = new LogOutputListener[0];
  private final Observer logOutputObserver = new Observer() {
    @Override
    public void update(Observable obs, Object obj) {
      Mote mote = (Mote) obj;
      String msg = ((Log) obs).getLastLogMessage();
      if (msg == null) {
        return;
      }
      if (msg.length() > 0 && msg.charAt(msg.length() - 1) == '\n') {
        msg = msg.substring(0, msg.length() - 1);
      }

      /* We may have to remove some events now */
      while (logOutputEvents.size() > logOutputBufferSize-1) {
        LogOutputEvent removed;
        synchronized (logOutputEvents) {
          removed = logOutputEvents.pollFirst();
        }
        if (removed == null) {
          break;
        }
        for (LogOutputListener l: logOutputListeners) {
          l.removedLogOutput(removed);
        }
      }

      /* Store log output, and notify listeners */
      LogOutputEvent ev = new LogOutputEvent(mote, simulation.getSimulationTime(), msg);
      synchronized (logOutputEvents) {
        logOutputEvents.add(ev);
      }
      for (LogOutputListener l: logOutputListeners) {
        l.newLogOutput(ev);
      }
    }
  };
  public void addLogOutputListener(LogOutputListener listener) {
    if (logOutputListeners.length == 0) {
      /* Start observing all log interfaces */
      Mote[] motes = simulation.getMotes();
      for (Mote m: motes) {
        for (MoteInterface mi: m.getInterfaces().getInterfaces()) {
          if (mi instanceof Log) {
            moteObservations.add(new MoteObservation(m, mi, logOutputObserver));
          }
        }
      }
    }

    logOutputListeners = ArrayUtils.add(logOutputListeners, listener);
    addMoteCountListener(listener);
  }
  public void removeLogOutputListener(LogOutputListener listener) {
    logOutputListeners = ArrayUtils.remove(logOutputListeners, listener);
    removeMoteCountListener(listener);

    if (logOutputListeners.length == 0) {
      /* Stop observing all log interfaces */
      MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
      for (MoteObservation o: observations) {
        if (o.observer() == logOutputObserver) {
          o.disconnect();
          moteObservations.remove(o);
        }
      }

      /* Clear logs (TODO config) */
      logOutputEvents.clear();
    }
  }

  public LogOutputEvent[] getLogOutputHistory() {
    synchronized (logOutputEvents) {
      return logOutputEvents.toArray(new LogOutputEvent[0]);
    }
  }
  public int getLogOutputBufferSize() {
    return logOutputBufferSize;
  }
  public void setLogOutputBufferSize(int size) {
    logOutputBufferSize = size;
    
    /* We may have to remove some events now */
    while (logOutputEvents.size() > logOutputBufferSize) {
      LogOutputEvent removed = logOutputEvents.pollFirst();
      if (removed == null) {
        break;
      }
      for (LogOutputListener l: logOutputListeners) {
        l.removedLogOutput(removed);
      }
    }
  }
  public int getLogOutputObservationsCount() {
    int count=0;
    MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
    for (MoteObservation o: observations) {
      if (o.observer() == logOutputObserver) {
        count++;
      }
    }
    return count;
  }

  @Override
  public String toString() {
    return 
    "\nActive mote observations: " + moteObservations.size() +
    "\n" +
    "\nMote count listeners: " + moteCountListeners.length +
    "\n" +
    "\nLog output listeners: " + logOutputListeners.length +
    "\nLog output history: " + logOutputEvents.size()
    ;
  }
  

  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    /* Log output buffer size */
    var element = new Element("logoutput");
    element.setText(String.valueOf(logOutputBufferSize));
    config.add(element);

    return config;
  }

  public void setConfigXML(Collection<Element> configXML) {
    for (Element element : configXML) {
      String name = element.getName();
      if (name.equals("logoutput")) {
        logOutputBufferSize = Integer.parseInt(element.getText());
      }
    }
  }
  
}
