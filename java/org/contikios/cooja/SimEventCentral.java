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
import java.util.Optional;
import java.util.function.BiConsumer;
import org.contikios.cooja.util.AnyMoteEventTriggers;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;
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
  private final AnyMoteEventTriggers<EventTriggers.Update> positionTriggers;
  private final BiConsumer<EventTriggers.Update, Log.LogDataInfo> logOutputTrigger;

  public SimEventCentral(Simulation simulation) {
    this.simulation = simulation;
    positionTriggers = new AnyMoteEventTriggers<>(simulation, mote ->
            Optional.of(mote.getInterfaces().getPosition().getPositionTriggers()));
    logOutputTrigger = (event, data) -> {
      var msg = data.msg();
      if (msg == null) {
        return;
      }
      if (!msg.isEmpty() && msg.charAt(msg.length() - 1) == '\n') {
        msg = msg.substring(0, msg.length() - 1);
      }

      // Evict the oldest event if the buffer will get full by the current message.
      if (logOutputEvents.size() > logOutputBufferSize - 1) {
        synchronized (logOutputEvents) {
          // Use pollFirst since it does not throw exceptions like removeFirst when the queue is empty.
          logOutputEvents.pollFirst();
        }
      }

      // Store log output, and notify listeners.
      var ev = new LogOutputEvent(data.mote(), simulation.getSimulationTime(), msg);
      synchronized (logOutputEvents) {
        logOutputEvents.add(ev);
      }
      for (var l : logOutputListeners) {
        l.newLogOutput(ev);
      }
    };
  }

  public EventTriggers<EventTriggers.Update, Mote> getPositionTriggers() {
    return positionTriggers;
  }

  /* GENERIC */
  private static class MoteEvent {
    static int _ID_COUNTER; /* Debugging */
    final int ID; /* Debugging */

    private final Mote mote;
    private final long time;

    MoteEvent(Mote mote, long time) {
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
  private record MoteObservation(Mote mote, Log log, BiConsumer<EventTriggers.Update, Log.LogDataInfo> trigger) {
    MoteObservation {
      log.getLogDataTriggers().addTrigger(this, trigger);
    }
    void disconnect() {
      log.getLogDataTriggers().removeTrigger(this, trigger);
    }
  }
  private final ArrayList<MoteObservation> moteObservations = new ArrayList<>();

  
  /* ADDED/REMOVED MOTES */
  void addMote(Mote mote) {
    if (logOutputListeners.length > 0) {
      // Add another log output observation (supports multiple log interfaces per mote).
      for (var mi : mote.getInterfaces().getInterfaces()) {
        if (mi instanceof Log log) {
          moteObservations.add(new MoteObservation(mote, log, logOutputTrigger));
        }
      }
    }
  }

  void removeMote(Mote mote) {
    // Disconnect and remove mote observations.
    for (var o : moteObservations.toArray(new MoteObservation[0])) {
      if (o.mote() == mote) {
        o.disconnect();
        moteObservations.remove(o);
      }
    }
  }

  /* LOG OUTPUT */
  public static class LogOutputEvent extends MoteEvent {
    public final String msg;
    LogOutputEvent(Mote mote, long time, String msg) {
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
  public interface LogOutputListener {
    void newLogOutput(LogOutputEvent ev);
  }
  /** Log output: notifications and history */
  private LogOutputListener[] logOutputListeners = new LogOutputListener[0];

  public void addLogOutputListener(LogOutputListener listener) {
    if (logOutputListeners.length == 0) {
      /* Start observing all log interfaces */
      Mote[] motes = simulation.getMotes();
      for (Mote m: motes) {
        for (MoteInterface mi: m.getInterfaces().getInterfaces()) {
          if (mi instanceof Log log) {
            moteObservations.add(new MoteObservation(m, log, logOutputTrigger));
          }
        }
      }
    }

    logOutputListeners = ArrayUtils.add(logOutputListeners, listener);
  }
  public void removeLogOutputListener(LogOutputListener listener) {
    logOutputListeners = ArrayUtils.remove(logOutputListeners, listener);
    if (logOutputListeners.length == 0) {
      /* Stop observing all log interfaces */
      MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
      for (MoteObservation o: observations) {
        if (o.trigger() == logOutputTrigger) {
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
    }
  }
  public int getLogOutputObservationsCount() {
    int count=0;
    MoteObservation[] observations = moteObservations.toArray(new MoteObservation[0]);
    for (MoteObservation o: observations) {
      if (o.trigger() == logOutputTrigger) {
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
