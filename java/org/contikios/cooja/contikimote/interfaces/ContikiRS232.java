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
 *
 */

package org.contikios.cooja.contikimote.interfaces;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayDeque;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.dialogs.SerialUI;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contiki mote serial port and log interfaces.
 * <p>
 * Contiki variables:
 * <ul>
 * <li>char simSerialReceivingFlag (1=mote has incoming serial data)
 * <li>int simSerialReceivingLength
 * <li>byte[] simSerialReceivingData
 * </ul>
 * <p>
 *
 * This observable notifies observers when a serial message is sent from the mote.
 *
 * @see #getLastLogMessage()
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Serial port")
public class ContikiRS232 extends SerialUI implements PolledAfterActiveTicks {
  private static final Logger logger = LoggerFactory.getLogger(ContikiRS232.class);

  private final ContikiMote mote;
  private final VarMemory moteMem;

  static final int SERIAL_BUF_SIZE = 16 * 1024; /* rs232.c:40 */

  /**
   * Creates an interface to the RS232 at mote.
   *
   * @param mote
   *          RS232's mote.
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiRS232(Mote mote) {
    this.mote = (ContikiMote) mote;
    this.moteMem = new VarMemory(mote.getMemory());
  }

  @Override
  public void doActionsAfterTick() {
    if (moteMem.getByteValueOf("simLoggedFlag") == 1) {
      int len = moteMem.getIntValueOf("simLoggedLength");
      byte[] bytes = moteMem.getByteArray("simLoggedData", len);

      moteMem.setByteValueOf("simLoggedFlag", (byte) 0);
      moteMem.setIntValueOf("simLoggedLength", 0);

      for (byte b: bytes) {
        dataReceived(b);
      }
    }
  }

  @Override
  public void writeString(String message) {
    final byte[] dataToAppend = message.getBytes(UTF_8);

    mote.getSimulation().invokeSimulationThread(() -> {
      /* Append to existing buffer */
      int oldSize = moteMem.getIntValueOf("simSerialReceivingLength");
      int newSize = oldSize + dataToAppend.length;
      if (newSize > SERIAL_BUF_SIZE) {
        logger.error("ContikiRS232: dropping rs232 data #1, buffer full: " + oldSize + " -> " + newSize);
        mote.requestImmediateWakeup();
        return;
      }
      moteMem.setIntValueOf("simSerialReceivingLength", newSize);

      byte[] oldData = moteMem.getByteArray("simSerialReceivingData", oldSize);
      byte[] newData = new byte[newSize];

      System.arraycopy(oldData, 0, newData, 0, oldData.length);
      System.arraycopy(dataToAppend, 0, newData, oldSize, dataToAppend.length);

      moteMem.setByteArray("simSerialReceivingData", newData);

      moteMem.setByteValueOf("simSerialReceivingFlag", (byte) 1);
      mote.requestImmediateWakeup();
    });
  }

  @Override
  public Mote getMote() {
    return mote;
  }

  private TimeEvent pendingBytesEvent;
  private final ArrayDeque<Byte> pendingBytes = new ArrayDeque<>();
  @Override
  public void writeArray(byte[] s) {
    for (byte b: s) {
      pendingBytes.add(b);
    }

    if (pendingBytesEvent != null) {
      /* Event is already scheduled, no need to reschedule */
      return;
    }

    pendingBytesEvent = new MoteTimeEvent(mote) {
      @Override
      public void execute(long t) {
        ContikiRS232.this.pendingBytesEvent = null;
        if (pendingBytes.isEmpty()) {
          return;
        }

        // Move bytes from Cooja buffer into Contiki-NG buffer.
        int nrBytes = pendingBytes.size();
        byte[] dataToAppend = new byte[nrBytes];
        for (int i=0; i < nrBytes; i++) {
          dataToAppend[i] = pendingBytes.pop();
        }

        /* Append to existing buffer */
        int oldSize = moteMem.getIntValueOf("simSerialReceivingLength");
        int newSize = oldSize + dataToAppend.length;
        if (newSize > SERIAL_BUF_SIZE) {
        	logger.error("ContikiRS232: dropping rs232 data #2, buffer full: " + oldSize + " -> " + newSize);
        	mote.requestImmediateWakeup();
        	return;
        }
        moteMem.setIntValueOf("simSerialReceivingLength", newSize);

        byte[] oldData = moteMem.getByteArray("simSerialReceivingData", oldSize);
        byte[] newData = new byte[newSize];

        System.arraycopy(oldData, 0, newData, 0, oldData.length);
        System.arraycopy(dataToAppend, 0, newData, oldSize, dataToAppend.length);

        moteMem.setByteArray("simSerialReceivingData", newData);

        moteMem.setByteValueOf("simSerialReceivingFlag", (byte) 1);

        /* Reschedule us if more bytes are available */
        mote.getSimulation().scheduleEvent(this, t);
        mote.requestImmediateWakeup();
      }
    };
    mote.getSimulation().invokeSimulationThread(() ->
            mote.getSimulation().scheduleEvent(pendingBytesEvent, mote.getSimulation().getSimulationTime()));
  }

  @Override
  public void writeByte(final byte b) {
    pendingBytes.add(b);

    if (pendingBytesEvent != null) {
      /* Event is already scheduled, no need to reschedule */
      return;
    }

    pendingBytesEvent = new MoteTimeEvent(mote) {
      @Override
      public void execute(long t) {
        ContikiRS232.this.pendingBytesEvent = null;
        if (pendingBytes.isEmpty()) {
          return;
        }

        // Move bytes from Cooja buffer to Contiki-NG buffer.
        int nrBytes = pendingBytes.size();
        byte[] dataToAppend = new byte[nrBytes];
        for (int i=0; i < nrBytes; i++) {
          dataToAppend[i] = pendingBytes.pop();
        }

        /* Append to existing buffer */
        int oldSize = moteMem.getIntValueOf("simSerialReceivingLength");
        int newSize = oldSize + dataToAppend.length;
        if (newSize > SERIAL_BUF_SIZE) {
        	logger.error("ContikiRS232: dropping rs232 data #3, buffer full: " + oldSize + " -> " + newSize);
        	mote.requestImmediateWakeup();
        	return;
        }
        moteMem.setIntValueOf("simSerialReceivingLength", newSize);

        byte[] oldData = moteMem.getByteArray("simSerialReceivingData", oldSize);
        byte[] newData = new byte[newSize];

        System.arraycopy(oldData, 0, newData, 0, oldData.length);
        System.arraycopy(dataToAppend, 0, newData, oldSize, dataToAppend.length);

        moteMem.setByteArray("simSerialReceivingData", newData);

        moteMem.setByteValueOf("simSerialReceivingFlag", (byte) 1);

        /* Reschedule us if more bytes are available */
        mote.getSimulation().scheduleEvent(this, t);
        mote.requestImmediateWakeup();
      }
    };

    /* Simulation thread: schedule immediately */
    if (mote.getSimulation().isSimulationThread()) {
    	mote.getSimulation().scheduleEvent(
    			pendingBytesEvent,
    			mote.getSimulation().getSimulationTime()
    			);
    	return;
    }

    mote.getSimulation().invokeSimulationThread(() -> {
      if (pendingBytesEvent.isScheduled()) {
        return;
      }
      mote.getSimulation().scheduleEvent(pendingBytesEvent, mote.getSimulation().getSimulationTime());
    });
  }

}
