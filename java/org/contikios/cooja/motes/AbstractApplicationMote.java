/*
 * Copyright (c) 2007, Swedish Institute of Computer Science. All rights
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
import org.contikios.cooja.MoteType;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.ApplicationSerialPort;
import org.contikios.cooja.interfaces.Radio;

/**
 * Abstract application mote.
 * <p>
 * Simplifies implementation of application level mote types.
 *
 * @author Fredrik Osterlind
 */
public abstract class AbstractApplicationMote extends AbstractWakeupMote<MoteType, SectionMoteMemory> {
  public abstract void receivedPacket(RadioPacket p);
  public abstract void sentPacket(RadioPacket p);
  
  public AbstractApplicationMote(MoteType moteType, Simulation sim) throws MoteType.MoteTypeCreationException {
    super(moteType, new SectionMoteMemory(new HashMap<>()), sim);
    moteInterfaces.init(this);
    // Observe our own radio for incoming radio packets.
    moteInterfaces.getRadio().getRadioEventTriggers().addTrigger(this, (event, radio) -> {
      if (radio.getLastEvent() == Radio.RadioEvent.RECEPTION_FINISHED) {
        if (radio.getLastPacketReceived() != null) // Only send in packets when they exist.
          receivedPacket(radio.getLastPacketReceived());
      } else if (radio.getLastEvent() == Radio.RadioEvent.TRANSMISSION_FINISHED) {
        if (radio.getLastPacketTransmitted() != null)
          sentPacket(radio.getLastPacketTransmitted());
      }
    });
    requestImmediateWakeup();
  }

  public void log(String msg) {
    ((ApplicationSerialPort)moteInterfaces.getLog()).triggerLog(msg);
  }
  
  @Override
  public String toString() {
    return "AppMote " + getID();
  }

  // These methods should be overridden to allow application motes receiving serial data.
  public abstract void writeArray(byte[] s);
  public abstract void writeByte(byte b);
  public abstract void writeString(String s);
}
