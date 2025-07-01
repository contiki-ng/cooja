/*
 * Copyright (c) 2011, Swedish Institute of Computer Science.
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

package org.contikios.cooja.mspmote.interfaces;

import java.util.ArrayList;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.dialogs.SerialUI;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.mspmote.MspMoteTimeEvent;
import se.sics.mspsim.core.USARTSource;

/**
 * @author Fredrik Osterlind
 */
@ClassDescription("Serial port")
public class MspSerial extends SerialUI {
  private static final long DELAY_INCOMING_DATA = 69; /* 115200 bit/s */
  
  private final Simulation simulation;
  private final MspMote mote;
  private final USARTSource usart;
  
  private final ArrayList<Byte> incomingData = new ArrayList<>();
 
  private final TimeEvent writeDataEvent;

  public String ioConfigString() {
	  return "USART 1";
  }
  public MspSerial(Mote mote) {
    this.mote = (MspMote) mote;
    this.simulation = mote.getSimulation();
    /* Listen to port writes */
    usart = getUSARTSource(this.mote);
    if (usart != null) {
      usart.addUSARTListener((source, data) -> MspSerial.this.dataReceived(data));
      writeDataEvent = new MspMoteTimeEvent(this.mote) {
        @Override
        public void execute(long t) {
          super.execute(t);
          boolean finished = false;
          byte b = 0;
          synchronized (incomingData) {
            if (incomingData.isEmpty() || !usart.isReceiveFlagCleared()) {
              finished = true;
            } else { // Write byte to serial port.
              b = incomingData.remove(0);
            }
          }
          if (!finished) {
            usart.byteReceived(b);
            mote.requestImmediateWakeup();
          }
          if (!incomingData.isEmpty()) {
            simulation.scheduleEvent(this, t + DELAY_INCOMING_DATA);
          }
        }
      };
    } else {
      writeDataEvent = null;
    }
  }

  protected USARTSource getUSARTSource(MspMote mote) {
      return mote.getCPU().getIOUnit(USARTSource.class, ioConfigString());
  }

  @Override
  public void writeByte(byte b) {
    if (writeDataEvent == null) return;
    incomingData.add(b);
    if (writeDataEvent.isScheduled()) {
      return;
    }
    if (simulation.isSimulationThread()) {
      simulation.scheduleEvent(writeDataEvent, simulation.getSimulationTime());
    } else {
      simulation.invokeSimulationThread(() -> {
        if (!writeDataEvent.isScheduled()) {
          simulation.scheduleEvent(writeDataEvent, simulation.getSimulationTime());
        }
      });
    }
  }

  @Override
  public void writeString(String s) {
    for (int i=0; i < s.length(); i++) {
      writeByte((byte) s.charAt(i));
    }
    writeByte((byte) 10);
  }

  @Override
  public void writeArray(byte[] s) {
    for (byte element : s) {
      writeByte(element);
    }
  }

  @Override
  public Mote getMote() {
    return mote;
  }
}
