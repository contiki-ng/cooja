/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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
 */

package org.contikios.cooja.contikimote.interfaces;

import javax.swing.JPanel;


import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.interfaces.Clock;
import org.contikios.cooja.interfaces.PolledAfterAllTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clock mote interface. Controls Contiki time.
 * <p>
 * Contiki variables:
 * <ul>
 * <li>clock_time_t simCurrentTime
 * <li>rtimer_clock_t simRtimerCurrentTicks
 * <li>clock_time_t simEtimerNextExpirationTime
 * <li>rtimer_clock_t simRtimerNextExpirationTime
 * <li>int simEtimerProcessRunValue
 * <li>int simRtimerProcessRunValue
 * <li>int simEtimerPending
 * </ul>
 *
 * This observable never notifies.
 *
 * @author Fredrik Osterlind
 */
public class ContikiClock extends Clock implements PolledBeforeActiveTicks, PolledAfterAllTicks {
  private static final Logger logger = LoggerFactory.getLogger(ContikiClock.class);

  private final ContikiMote mote;
  private final VarMemory moteMem;

  private long moteTime; /* Microseconds */
  private long timeDrift; /* Microseconds */

  /**
   * @param mote Mote
   *
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiClock(Mote mote) {
    super(mote);
    this.mote = (ContikiMote) mote;
    this.moteMem = new VarMemory(mote.getMemory());
    timeDrift = 0;
    moteTime = 0;
  }

  @Override
  public void setTime(long newTime) {
    moteTime = newTime;
    if (moteTime > 0) {
      moteMem.setInt64ValueOf("simCurrentTime", newTime / 1000);
    }
  }

  @Override
  public void setDrift(long drift) {
    this.timeDrift = drift - (drift % 1000); /* Round to ms */
    setTime(timeDrift);
  }

  @Override
  public long getDrift() {
    return timeDrift;
  }

  @Override
  public long getTime() {
    return moteTime;
  }
  
  @Override
  public void setDeviation(double deviation) {
    logger.error("Can't change deviation");
  }

  @Override
  public double getDeviation() {
  	return 1.0;
  }

  @Override
  public void doActionsBeforeTick() {
    /* Update time */
    long currentSimulationTime = simulation.getSimulationTime();
    setTime(currentSimulationTime + timeDrift);
    moteMem.setInt64ValueOf("simRtimerCurrentTicks", currentSimulationTime);
  }

  @Override
  public void doActionsAfterTick() {
    long currentSimulationTime = mote.getSimulation().getSimulationTime();

    /* Always schedule for Rtimer if anything pending */
    if (moteMem.getIntValueOf("simRtimerPending") != 0) {
      mote.scheduleNextWakeup(moteMem.getInt64ValueOf("simRtimerNextExpirationTime"));
    }

    /* Request next tick for remaining events / timers */
    int processRunValue = moteMem.getIntValueOf("simProcessRunValue");
    if (processRunValue != 0) {
      /* Handle next Contiki event in one millisecond */
      mote.scheduleNextWakeup(currentSimulationTime + Simulation.MILLISECOND);
      return;
    }

    int etimersPending = moteMem.getIntValueOf("simEtimerPending");
    if (etimersPending == 0) {
      /* No timers */
      return;
    }

    /* Request tick next wakeup time for Etimer */
    long etimerNextExpirationTime = moteMem.getInt64ValueOf("simEtimerNextExpirationTime") * Simulation.MILLISECOND;
    long etimerTimeToNextExpiration = etimerNextExpirationTime - moteTime;
    if (etimerTimeToNextExpiration <= 0) {
      /* logger.warn(mote.getID() + ": Event timer already expired, but has been delayed: " + etimerTimeToNextExpiration); */
      /* Wake up in one millisecond to handle a missed Etimer task
       * which may be blocked by busy waiting such as one in
       * radio_send(). Scheduling it in a shorter time than one
       * millisecond, e.g., one microsecond, seems to be worthless, and
       * it would cause unnecessary CPU usage. */
      mote.scheduleNextWakeup(currentSimulationTime + Simulation.MILLISECOND);
    } else {
      mote.scheduleNextWakeup(currentSimulationTime + etimerTimeToNextExpiration);
    }
  }


  @Override
  public JPanel getInterfaceVisualizer() {
    return null;
  }
}
