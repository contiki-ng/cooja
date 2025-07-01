/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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
 *
 */

package org.contikios.cooja.motes;

import java.awt.Container;
import java.util.Random;
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.COOJARadioPacket;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.ApplicationRadio;

/**
 * Simple application-level mote that transmits dummy radio packets
 * on all radio channels (-1), interfering all surrounding radio communication.
 * The transmission can be switched on/off; and the Tx duty cycle can be
 * adjusted by setting the time duration of the packet and the inter-packet
 * latency.
 * <p>
 * This mote type also implements the mote functionality ("mote software"),
 * and can be used as an example of implementing application-level mote.
 *
 * @see DisturberMote
 * @author Fredrik Osterlind, Thiemo Voigt
 */
@ClassDescription("Disturber mote")
@AbstractionLevelDescription("Application level")
public class DisturberMoteType extends AbstractApplicationMoteType {
  public DisturberMoteType() {
    super(true);
  }

  @Override
  public boolean configureAndInit(Container parentContainer,
      Simulation simulation, boolean visAvailable) 
  throws MoteTypeCreationException {
    if (!super.configureAndInit(parentContainer, simulation, Cooja.isVisualized())) {
      return false;
    }
    setDescription("Disturber Mote Type #" + getIdentifier());
    return true;
  }
  
  @Override
  public Mote generateMote(Simulation simulation) throws MoteTypeCreationException {
    return new DisturberMote(this, simulation);
  }

  public static class DisturberMote extends AbstractApplicationMote {
    private ApplicationRadio radio;
    
    private final RadioPacket radioPacket = new COOJARadioPacket(new byte[] {
        0x01, 0x02, 0x03, 0x04, 0x05
    });
    private long avgDelay = 16 * Simulation.MILLISECOND;
    private long duration = 4 * Simulation.MILLISECOND;
    private boolean txOn = false;

    DisturberMote(MoteType moteType, Simulation simulation) throws MoteTypeCreationException {
      super(moteType, simulation);
    }

    /* Schedule next (potential) transmission of dummy packet */
    private void scheduleNext(RadioPacket p) {
      Random rd = getSimulation().getRandomGenerator();
      long delay = avgDelay / 2 + (long)(rd.nextDouble() * avgDelay);
      getSimulation().scheduleEvent(new MoteTimeEvent(this) {
          @Override
          public void execute(long t) {
            if (txOn) {
              radio.startTransmittingPacket(radioPacket, duration);
            }
            scheduleNext(radioPacket);
          }
        }, getSimulation().getSimulationTime() + duration + delay);
    }
    
    @Override
    protected void execute(long time) {
      if (radio == null) {
        radio = (ApplicationRadio) getInterfaces().getRadio();
      }
      
      /* Start sending interfering traffic */
      if (txOn) {
        radio.startTransmittingPacket(radioPacket, duration);
      }
      /* Schedule next Tx */
      scheduleNext(radioPacket);
    }

    @Override
    public void receivedPacket(RadioPacket p) {
      /* Ignore */
    }

    @Override
    public void sentPacket(RadioPacket p) {
    }

    @Override
    public String toString() {
      return "Disturber " + getID();
    }

    @Override
    public void writeArray(byte[] s) {}

    @Override
    public void writeByte(byte b) {}

    @Override
    public void writeString(String s) {}

    /* Get average delay in ms */
    public long getAvgDelay() {
      return avgDelay;
    }

    /* Get duration in ms */
    public long getDuration() {
      return duration;
    }

    /* Set average delay in ms */
    public void setAvgDelay(long delay) {
      avgDelay = delay;
    }

    /* Set duration in ms */
    public void setDuration(long dur) {
      duration = dur;
    }

    /* Enable Tx */
    public void txOn() {
      txOn = true;
    }

    /* Disable Tx */
    public void txOff() {
      txOn = false;
    }
  }
}
