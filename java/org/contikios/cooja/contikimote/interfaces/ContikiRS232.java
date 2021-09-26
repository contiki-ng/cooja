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

import java.util.Vector;

import org.apache.log4j.Logger;
import org.contikios.cooja.*;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.contikimote.ContikiMoteInterface;
import org.contikios.cooja.dialogs.SerialUI;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;

/**
 * Contiki mote serial port and log interfaces.
 *
 * Contiki variables:
 * <ul>
 * <li>char simSerialReceivingFlag (1=mote has incoming serial data)
 * <li>int simSerialReceivingLength
 * <li>byte[] simSerialReceivingData
 * <li>char simSerialSendFlag (1=mote has outgoing serial data)
 * <li>int simSerialSendLength
 * <li>byte[] simSerialSendData
 * </ul>
 * <p>
 *
 * Core interface:
 * <ul>
 * <li>rs232_interface
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
public class ContikiRS232 extends SerialUI implements ContikiMoteInterface
    , PolledAfterActiveTicks, PolledBeforeActiveTicks 
{
  private static Logger logger = Logger.getLogger(ContikiRS232.class);

  private ContikiMote mote = null;
  private VarMemory moteMem = null;

  static final int SERIAL_BUF_SIZE = 16 * 1024; /* rs232.c:40 */
  static final int SERIAL_BUF_STOP = 0x10000;
  private int      serial_buf_limit = SERIAL_BUF_SIZE;

  // legacy serial prints to simlog
  static final int SERIAL_LEGACY        = 0;
  // mote serial can prints to serial
  static final int SERIAL_SENDING       = 1;
  private int      serial_ver = SERIAL_LEGACY;

  static final String simSendFlag = "simSerialSendFlag"; 

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
    if ( this.moteMem.variableExists(simSendFlag) )
        serial_ver = SERIAL_SENDING;
  }

  public static String[] getCoreInterfaceDependencies() {
    return new String[]{"rs232_interface"};
  }

  public void doActionsBeforeTick() {
      int recv_size = moteMem.getIntValueOf("simSerialReceivingLength");
      if (recv_size > SERIAL_BUF_STOP) {
          //this huge value treat as declaration with receiver buffer size in LSB
          serial_buf_limit = recv_size & (SERIAL_BUF_STOP-1);
          logger.debug("mote"+ getMote().getID() +".ContikiRS232 establish receive buffer size " + serial_buf_limit);
      }
  }

  private boolean isSendigFrame() {
      if (serial_ver >= SERIAL_SENDING) {
          return (moteMem.getByteValueOf("simSerialSendFlag") != 0);
      }
      return false;
  }

  private void haveSentFrame() {
      if (serial_ver >= SERIAL_SENDING)
          moteMem.setIntValueOf("simSerialSendFlag", 0);
      
      moteMem.setIntValueOf("simSerialSendLength", 0);
  }
  
  public void doActionsAfterTick() {
    if ( isSendigFrame() ) {
      int len = moteMem.getIntValueOf("simSerialSendLength");
      byte[] bytes = moteMem.getByteArray("simSerialSendData", len);

      haveSentFrame();

  	  //logger.info("RS232:received:" + bytes.length);
  	  bufReceived(bytes);
    }
  }

  @Override
  public void writeString(String message) {
      // TODO: old code and tests, relye that coja-serial finishes every message
      //     passes to mote autocompletes with \n
      // for compatibility with old test, keep this feature for writen strings
      //  BUT this is not exactly old style - it addsEOL to every message.
      //    
      // to write exact data, use writeArray
      if ((message.length() == 0) || (message.charAt(message.length()-1)!='\n') )
          message += '\n';

    super.writeString(message);

    final byte[] dataToAppend = message.getBytes();

    mote.getSimulation().invokeSimulationThread(new Runnable() {
      public void run() {
        /* Append to existing buffer */
        int oldSize = moteMem.getIntValueOf("simSerialReceivingLength");
        if (oldSize < 0) {
            // drop send, since receiver id down, not allow receive
            return;
        }
        int newSize = oldSize + dataToAppend.length;
        if (newSize > serial_buf_limit) {
        	logger.fatal("ContikiRS232: dropping rs232 data #1, buffer full: " + oldSize + " -> " + newSize);
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
      }
    });
  }

  public Mote getMote() {
    return mote;
  }

  private TimeEvent pendingBytesEvent = null;
  private Vector<Byte> pendingBytes = new Vector<Byte>();
  @Override
  public void writeArray(byte[] s) {
	//logger.info("RS232:writeArray:" + s.length);
	super.writeArray(s);

    for (byte b: s) {
      pendingBytes.add(b);
    }

    if (pendingBytesEvent != null) {
      /* Event is already scheduled, no need to reschedule */
      return;
    }

    pendingBytesEvent = new MoteTimeEvent(mote, 0) {
      public void execute(long t) {
        ContikiRS232.this.pendingBytesEvent = null;
        if (pendingBytes.isEmpty()) {
          return;
        }

        int oldSize = moteMem.getIntValueOf("simSerialReceivingLength");
        if (oldSize < 0) {
            // drop send, since receiver id down, not allow receive
            pendingBytes.clear();
            return;
        }
        
        /* Move bytes from synchronized vector to Contiki buffer */
        int nrBytes = pendingBytes.size();
        byte[] dataToAppend = new byte[nrBytes];
        for (int i=0; i < nrBytes; i++) {
          dataToAppend[i] = pendingBytes.firstElement();
          pendingBytes.remove(0);
        }

        /* Append to existing buffer */
        int newSize = oldSize + dataToAppend.length;
        if (newSize > serial_buf_limit) {
        	logger.fatal("ContikiRS232: dropping rs232 data #2, buffer full: " + oldSize + " -> " + newSize);
            moteMem.setByteValueOf("simSerialReceivingFlag", (byte) 1);
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
    mote.getSimulation().invokeSimulationThread(new Runnable() {
      public void run() {
        mote.getSimulation().scheduleEvent(
            pendingBytesEvent,
            mote.getSimulation().getSimulationTime()
        );
      }
    });
  }

  @Override
  public void writeByte(final byte b) {
	super.writeByte(b);

    pendingBytes.add(b);

    if (pendingBytesEvent != null) {
      /* Event is already scheduled, no need to reschedule */
      return;
    }

    pendingBytesEvent = new MoteTimeEvent(mote, 0) {
      public void execute(long t) {
        ContikiRS232.this.pendingBytesEvent = null;
        if (pendingBytes.isEmpty()) {
          return;
        }

        int oldSize = moteMem.getIntValueOf("simSerialReceivingLength");
        if (oldSize < 0) {
            // drop send, since receiver id down, not allow receive
            pendingBytes.clear();
            return;
        }

        /* Move bytes from synchronized vector to Contiki buffer */
        int nrBytes = pendingBytes.size();
        byte[] dataToAppend = new byte[nrBytes];
        for (int i=0; i < nrBytes; i++) {
          dataToAppend[i] = pendingBytes.firstElement();
          pendingBytes.remove(0);
        }

        /* Append to existing buffer */
        int newSize = oldSize + dataToAppend.length;
        if (newSize > serial_buf_limit) {
        	logger.fatal("ContikiRS232: dropping rs232 data #3, buffer full: " + oldSize + " -> " + newSize);
            moteMem.setByteValueOf("simSerialReceivingFlag", (byte) 1);
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

    mote.getSimulation().invokeSimulationThread(new Runnable() {
    	public void run() {
    		if (pendingBytesEvent.isScheduled()) {
    			return;
    		}
    		mote.getSimulation().scheduleEvent(
    				pendingBytesEvent,
    				mote.getSimulation().getSimulationTime()
    				);
    	}
    });
  }

}
