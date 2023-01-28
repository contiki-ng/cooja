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

package org.contikios.cooja.motes;

import static org.contikios.cooja.WatchpointMote.WatchpointListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.jdom2.Element;

public abstract class AbstractWakeupMote<T extends MoteType, M extends MemoryInterface> implements Mote {
  protected final Simulation simulation;
  protected final T moteType;
  protected final M moteMemory;

  protected final MoteInterfaceHandler moteInterfaces = new MoteInterfaceHandler();
  private long nextWakeupTime = -1;

  protected final ArrayList<WatchpointListener> watchpointListeners = new ArrayList<>();
  protected final ArrayList<Watchpoint> watchpoints = new ArrayList<>();

  protected AbstractWakeupMote(T moteType, M moteMemory, Simulation sim) {
    this.moteType = moteType;
    this.moteMemory = moteMemory;
    this.simulation = sim;
  }

  private final TimeEvent executeMoteEvent = new MoteTimeEvent(this) {
    @Override
    public void execute(long t) {
      AbstractWakeupMote.this.execute(t);
    }
    @Override
    public String toString() {
      return "EXECUTE " + AbstractWakeupMote.this.getClass().getName();
    }
  };

  @Override
  public int getID() {
    return moteInterfaces.getMoteID().getMoteID();
  }

  @Override
  public MoteInterfaceHandler getInterfaces() {
    return moteInterfaces;
  }

  @Override
  public MemoryInterface getMemory() {
    return moteMemory;
  }

  @Override
  public MoteType getType() {
    return moteType;
  }

  @Override
  public Simulation getSimulation() {
      return simulation;
  }

  @Override
  public Collection<Element> getConfigXML() {
    var breakpoints = new ArrayList<Element>();
    for (var breakpoint : watchpoints) {
      var element = new Element("breakpoint");
      element.addContent(breakpoint.getConfigXML());
      breakpoints.add(element);
    }

    var config = new ArrayList<Element>();
    if (!breakpoints.isEmpty()) {
      var element = new Element("breakpoints");
      element.addContent(breakpoints);
      config.add(element);
    }

    // Mote interfaces.
    config.addAll(getInterfaces().getConfigXML());
    return config;
  }

  @Override
  public boolean setConfigXML(Simulation sim, Collection<Element> configXML, boolean vis) throws MoteType.MoteTypeCreationException {
    for (var element : configXML) {
      var name = element.getName();
      if ("breakpoints".equals(name)) {
        for (Element elem : element.getChildren("breakpoint")) {
          var breakpoint = createBreakpoint();
          if (breakpoint != null && breakpoint.setConfigXML(elem.getChildren())) {
            watchpoints.add(breakpoint);
          }
        }
      } else if ("interface_config".equals(name)) {
        if (!getInterfaces().setConfigXML(this, element, !simulation.isQuickSetup())) {
          return false;
        }
      }
    }
    /* Schedule us immediately */
    requestImmediateWakeup();
    return true;
  }

  public void addWatchpointListener(WatchpointListener listener) {
    watchpointListeners.add(listener);
  }

  public void removeWatchpointListener(WatchpointListener listener) {
    watchpointListeners.remove(listener);
  }

  public WatchpointListener[] getWatchpointListeners() {
    return watchpointListeners.toArray(new WatchpointListener[0]);
  }

  protected Watchpoint createBreakpoint() {
    // Implemented by subclasses supporting breakpoints
    return null;
  }

  protected Watchpoint createBreakpoint(long address, File codeFile, int lineNr) {
    return null;
  }

  public Watchpoint addBreakpoint(long address, File codeFile, int lineNr) {
    var bp = createBreakpoint(address, codeFile, lineNr);
    if (bp == null) {
      // Breakpoints not supported by this mote type
      return null;
    }
    watchpoints.add(bp);

    for (WatchpointListener listener: watchpointListeners) {
      listener.watchpointsChanged();
    }
    return bp;
  }

  public void removeBreakpoint(Watchpoint watchpoint) {
    watchpoint.removed();
    watchpoints.remove(watchpoint);
    for (var listener : watchpointListeners) {
      listener.watchpointsChanged();
    }
  }

  public Watchpoint[] getBreakpoints() {
    return watchpoints.toArray(new Watchpoint[0]);
  }

  public boolean breakpointExists(long address) {
    if (address < 0) {
      return false;
    }
    for (var watchpoint : watchpoints) {
      if (watchpoint.getExecutableAddress() == address) {
        return true;
      }
    }
    return false;
  }

  public boolean breakpointExists(File file, int lineNr) {
    for (var watchpoint : watchpoints) {
      if (watchpoint.getCodeFile() == null || watchpoint.getCodeFile().compareTo(file) != 0 ||
          watchpoint.getLineNumber() != lineNr) {
        continue;
      }
      return true;
    }
    return false;
  }

  /**
   * Execute mote software.
   * This method is only called from the simulation thread.
   * 
   * @param time Simulation time.
   */
  protected abstract void execute(long time);

  /**
   * Execute mote software as soon as possible.
   * <p>
   * If this method is called from the simulation thread, 
   * the mote software will execute immediately (at the current simulation time).
   * <p>
   * If this method is called from outside the simulation thread, 
   * the mote software will execute as soon as possible.
   */
  public void requestImmediateWakeup() {
    long t = simulation.getSimulationTime();
    if (simulation.isSimulationThread()) {
      scheduleNextWakeup(t);
    } else {
      simulation.invokeSimulationThread(() -> scheduleNextWakeup(t));
    }
  }

  /**
   * Execute mote software at given time, or earlier.
   * <p>
   * If a wakeup is already scheduled earlier than given argument,
   * this request will be ignored.
   * <p>
   * This method must be called from the simulation thread.
   * 
   * @param time Simulation time
   * @return True iff wakeup request rescheduled the wakeup time.
   */
  public boolean scheduleNextWakeup(long time) {
    assert simulation.isSimulationThread() : "Scheduling event from non-simulation thread (" + Thread.currentThread() + ")";
      
    if (executeMoteEvent.isScheduled() &&
        nextWakeupTime <= time) {
      /* Already scheduled wakeup event precedes given time - ignore wakeup request */
      return false;
    }

    if (executeMoteEvent.isScheduled()) {
      /* Reschedule wakeup mote event */
      executeMoteEvent.remove();
    }

    simulation.scheduleEvent(executeMoteEvent, time);

    nextWakeupTime = time;

    return true;
  }

  private HashMap<String, Object> properties;
  @Override
  public void setProperty(String key, Object obj) {
    if (properties == null) {
      properties = new HashMap<>();
    }
    properties.put(key, obj);
  }
  @Override
  public Object getProperty(String key) {
    if (properties == null) {
      return null;
    }
    return properties.get(key);
  }
}
