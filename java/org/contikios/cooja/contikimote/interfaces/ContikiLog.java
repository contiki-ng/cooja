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
import org.contikios.cooja.dialogs.LogUI;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;

/**
 * Contiki mote serial port and log interfaces.
 *
 * Contiki variables:
 * <ul>
 * <li>char simSerialReceivingFlag (1=mote has incoming serial data)
 * <li>int simSerialReceivingLength
 * <li>byte[] simSerialReceivingData
 * </ul>
 * <p>
 *
 * Core interface:
 * <ul>
 * <li>simlog_interface
 * </ul>
 * <p>
 *
 * This observable notifies observers when a serial message is sent from the mote.
 *
 * @see #getLastLogMessage()
 *
 * @author AlexRayne
 * @author Fredrik Osterlind
 */
@ClassDescription("Debug Log")
public class ContikiLog extends LogUI implements ContikiMoteInterface, PolledAfterActiveTicks {
  private static Logger logger = Logger.getLogger(ContikiLog.class);

  private ContikiMote mote = null;
  private VarMemory moteMem = null;

  static final int SERIAL_BUF_SIZE = 16 * 1024; /* rs232.c:40 */

  /**
   * Creates an interface to the RS232 at mote.
   *
   * @param mote
   *          RS232's mote.
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiLog(Mote mote) {
    this.mote = (ContikiMote) mote;
    this.moteMem = new VarMemory(mote.getMemory());
  }

  public static String[] getCoreInterfaceDependencies() {
    return new String[]{ "simlog_interface" };
  }

  public void doActionsAfterTick() {
    if (moteMem.getByteValueOf("simLoggedFlag") == 1) {
      int len = moteMem.getIntValueOf("simLoggedLength");
      byte[] bytes = moteMem.getByteArray("simLoggedData", len);

      moteMem.setByteValueOf("simLoggedFlag", (byte) 0);
      moteMem.setIntValueOf("simLoggedLength", 0);

      dataReceivedBuf(bytes);
    }
  }

  public void writeString(String message) {
  }

  public Mote getMote() {
    return mote;
  }

  private TimeEvent pendingBytesEvent = null;
  private Vector<Byte> pendingBytes = new Vector<Byte>();
  public void writeArray(byte[] s) {
  }

  public void writeByte(final byte b) {
  }

}
