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

import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;

public abstract class AbstractWakeupMote implements Mote {
  private static final Logger logger = LogManager.getLogger(AbstractWakeupMote.class);

  protected Simulation simulation = null;

  private long nextWakeupTime = -1;

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
  public Simulation getSimulation() {
      return simulation;
  }

  public void setSimulation(Simulation simulation) {
      this.simulation = simulation;
  }
  
  /**
   * Execute mote software.
   * This method is only called from the simulation thread.
   * 
   * @param time Simulation time.
   */
  public abstract void execute(long time);

  /**
   * Execute mote software as soon as possible.
   * 
   * If this method is called from the simulation thread, 
   * the mote software will execute immediately (at the current simulation time).
   * 
   * If this method is called from outside the simulation thread, 
   * the mote software will execute as soon as possible.
   */
  public void requestImmediateWakeup() {
    long t = simulation.getSimulationTime();
    
    if (simulation.isSimulationThread()) {
      /* Schedule wakeup immediately */
      scheduleNextWakeup(t);
    }
    else {
      /* Schedule wakeup asap */
      simulation.invokeSimulationThread(new Runnable() {
        @Override
        public void run() {
          scheduleNextWakeup(t);
        }
      });
    }
  }

  /**
   * Returns next wakeup time, or -1 if not scheduled.

   */
  public long getNextWakeupTime() {
    if (!executeMoteEvent.isScheduled()) {
      return -1;
    }
    return nextWakeupTime;
  }
  
  /**
   * Execute mote software at given time, or earlier.
   * 
   * If a wakeup is already scheduled earlier than given argument,
   * this request will be ignored.
   * 
   * This method must be called from the simulation thread.
   * 
   * @param time Simulation time
   * @return True iff wakeup request rescheduled the wakeup time.
   */
  public boolean scheduleNextWakeup(long time) {
    assert simulation.isSimulationThreadOrNull() : "Scheduling event from non-simulation thread (" + Thread.currentThread() + ")";
      
    if (executeMoteEvent.isScheduled() &&
        nextWakeupTime <= time) {
      /* Already scheduled wakeup event precedes given time - ignore wakeup request */
      return false;
    }

    if (executeMoteEvent.isScheduled()) {
      /* Reschedule wakeup mote event */
      /*logger.info("Rescheduled wakeup from " + executeMoteEvent.getTime() + " to " + time);*/
      executeMoteEvent.remove();
    }

    simulation.scheduleEvent(executeMoteEvent, time);

    nextWakeupTime = time;

    return true;
  }

  @Override
  public void removed() {
  }
  
  private HashMap<String, Object> properties = null;
  @Override
  public void setProperty(String key, Object obj) {
    if (properties == null) {
      properties = new HashMap<String, Object>();
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
