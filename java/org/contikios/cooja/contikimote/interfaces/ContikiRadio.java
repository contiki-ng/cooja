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

import java.util.ArrayList;
import java.util.Collection;

import org.jdom2.Element;

import org.contikios.cooja.COOJARadioPacket;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.mote.memory.VarMemory;
import org.contikios.cooja.radiomediums.UDGM;
import org.contikios.cooja.util.CCITT_CRC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packet radio transceiver mote interface.
 * <p>
 * To simulate transmission rates, the underlying Contiki system is
 * locked in TX or RX states using multi-threading library.
 * <p>
 * Contiki variables:
 * <ul>
 * <li>char simReceiving (1=mote radio is receiving)
 * <li>char simInPolled
 * <p>
 * <li>int simInSize (size of received data packet)
 * <li>byte[] simInDataBuffer (data of received data packet)
 * <li>int64_t simLastPacketTimestamp (timestamp of the last received data packet)
 * <p>
 * <li>int simOutSize (size of transmitted data packet)
 * <li>byte[] simOutDataBuffer (data of transmitted data packet)
 * <p>
 * <li>char simRadioHWOn (radio hardware status (on/off))
 * <li>int simSignalStrength (heard radio signal strength)
 * <li>int simLastSignalStrength
 * <li>char simPower (number indicating power output)
 * <li>int simRadioChannel (number indicating current channel)
 * </ul>
 * <p>
 *
 * This observable notifies at radio state changes during RX and TX.
 *
 * @see #getLastEvent()
 * @see UDGM
 *
 * @author Fredrik Osterlind
 */
public class ContikiRadio extends Radio implements PolledAfterActiveTicks {
  private final ContikiMote mote;

  private final VarMemory myMoteMemory;

  private static final Logger logger = LoggerFactory.getLogger(ContikiRadio.class);

  /**
   * Project default transmission bitrate (kbps).
   */
  private final double RADIO_TRANSMISSION_RATE_KBPS;
  private final CCITT_CRC txCrc = new CCITT_CRC();

  /**
   * Configured transmission bitrate (kbps).
   */
  private double radioTransmissionRateKBPS;

  private RadioPacket packetToMote;

  private RadioPacket packetFromMote;

  private boolean radioOn;

  private boolean isTransmitting;

  private boolean isInterfered;

  private long transmissionEndTime = -1;

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;

  private long lastEventTime;

  private int oldOutputPowerIndicator = -1;

  private int oldRadioChannel = -1;

  /**
   * Creates an interface to the radio at mote.
   *
   * @param mote Mote
   *
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiRadio(Mote mote) {
    // Read class configurations of this mote type
    this.RADIO_TRANSMISSION_RATE_KBPS = mote.getType().getConfig().getDoubleValue(
        ContikiRadio.class, "RADIO_TRANSMISSION_RATE_kbps");
    this.radioTransmissionRateKBPS = this.RADIO_TRANSMISSION_RATE_KBPS;

    this.mote = (ContikiMote) mote;
    this.myMoteMemory = new VarMemory(mote.getMemory());

    radioOn = myMoteMemory.getByteValueOf("simRadioHWOn") == 1;
  }

  /* Packet radio support */
  @Override
  public RadioPacket getLastPacketTransmitted() {
    return packetFromMote;
  }

  @Override
  public RadioPacket getLastPacketReceived() {
    return packetToMote;
  }

  @Override
  public void setReceivedPacket(RadioPacket packet) {
    packetToMote = packet;
  }

  /* General radio support */
  @Override
  public boolean isRadioOn() {
    return radioOn;
  }

  @Override
  public boolean isTransmitting() {
    return isTransmitting;
  }

  @Override
  public boolean isReceiving() {
    return myMoteMemory.getByteValueOf("simReceiving") == 1;
  }

  @Override
  public boolean isInterfered() {
    return isInterfered;
  }

  @Override
  public int getChannel() {
    return myMoteMemory.getIntValueOf("simRadioChannel");
  }

  @Override
  public void signalReceptionStart() {
    packetToMote = null;
    if (isInterfered() || isReceiving() || isTransmitting()) {
      interfereAnyReception();
      return;
    }

    myMoteMemory.setByteValueOf("simReceiving", (byte) 1);
    mote.requestImmediateWakeup();

    lastEventTime = mote.getSimulation().getSimulationTime();
    lastEvent = RadioEvent.RECEPTION_STARTED;

    myMoteMemory.setInt64ValueOf("simLastPacketTimestamp", lastEventTime);
    radioEventTriggers.trigger(RadioEvent.RECEPTION_STARTED, this);
  }

  @Override
  public void signalReceptionEnd() {
    if (isInterfered || packetToMote == null) {
      isInterfered = false;
      packetToMote = null;
      myMoteMemory.setIntValueOf("simInSize", 0);
    } else {
      myMoteMemory.setIntValueOf("simInSize", packetToMote.getPacketData().length - 2);
      myMoteMemory.setByteArray("simInDataBuffer", packetToMote.getPacketData());
    }

    myMoteMemory.setByteValueOf("simReceiving", (byte) 0);
    mote.requestImmediateWakeup();
    lastEventTime = mote.getSimulation().getSimulationTime();
    lastEvent = RadioEvent.RECEPTION_FINISHED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_FINISHED, this);
  }

  @Override
  public RadioEvent getLastEvent() {
    return lastEvent;
  }

  @Override
  public void interfereAnyReception() {
    if (isInterfered()) {
      return;
    }
 
    isInterfered = true;

    lastEvent = RadioEvent.RECEPTION_INTERFERED;
    lastEventTime = mote.getSimulation().getSimulationTime();
    radioEventTriggers.trigger(RadioEvent.RECEPTION_INTERFERED, this);
  }

  @Override
  public double getCurrentOutputPower() {
    /* TODO Implement method */
    logger.warn("Not implemented, always returning 0 dBm");
    return 0;
  }

  @Override
  public int getOutputPowerIndicatorMax() {
    return 100;
  }

  @Override
  public int getCurrentOutputPowerIndicator() {
    return myMoteMemory.getByteValueOf("simPower");
  }

  @Override
  public double getCurrentSignalStrength() {
    return myMoteMemory.getIntValueOf("simSignalStrength");
  }

  @Override
  public void setCurrentSignalStrength(double signalStrength) {
    myMoteMemory.setIntValueOf("simSignalStrength", (int) signalStrength);
  }

  /** Set LQI to a value between 0 and 255.
   * 
   * @see org.contikios.cooja.interfaces.Radio#setLQI(int)
   */
  @Override
  public void setLQI(int lqi){
    if(lqi<0) {
      lqi=0;
    }
    else if(lqi>0xff) {
      lqi=0xff;
    }
    myMoteMemory.setIntValueOf("simLQI", lqi);
  }

  @Override
  public int getLQI(){
    return myMoteMemory.getIntValueOf("simLQI");
  }

  @Override
  public Position getPosition() {
    return mote.getInterfaces().getPosition();
  }

  @Override
  public void doActionsAfterTick() {
    long now = mote.getSimulation().getSimulationTime();

    /* Check if radio hardware status changed */
    if (radioOn != (myMoteMemory.getByteValueOf("simRadioHWOn") == 1)) {
      radioOn = !radioOn;

      if (!radioOn) {
        myMoteMemory.setByteValueOf("simReceiving", (byte) 0);
        myMoteMemory.setIntValueOf("simInSize", 0);
        myMoteMemory.setIntValueOf("simOutSize", 0);
        isTransmitting = false;
        lastEvent = RadioEvent.HW_OFF;
      } else {
        lastEvent = RadioEvent.HW_ON;
      }

      lastEventTime = now;
      radioEventTriggers.trigger(radioOn ? RadioEvent.HW_ON : RadioEvent.HW_OFF, this);
    }
    if (!radioOn) {
      return;
    }

    /* Check if radio output power changed */
    var currPower = myMoteMemory.getByteValueOf("simPower");
    if (currPower != oldOutputPowerIndicator) {
      oldOutputPowerIndicator = currPower;
      lastEvent = RadioEvent.UNKNOWN;
      radioEventTriggers.trigger(RadioEvent.UNKNOWN, this);
    }

    /* Check if radio channel changed */
    var currentChannel = getChannel();
    if (currentChannel != oldRadioChannel) {
      oldRadioChannel = currentChannel;
      lastEvent = RadioEvent.UNKNOWN;
      radioEventTriggers.trigger(RadioEvent.UNKNOWN, this);
    }

    /* Ongoing transmission */
    if (isTransmitting && now >= transmissionEndTime) {
      myMoteMemory.setIntValueOf("simOutSize", 0);
      isTransmitting = false;
      mote.requestImmediateWakeup();

      lastEventTime = now;
      lastEvent = RadioEvent.TRANSMISSION_FINISHED;
      radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, this);
    }

    /* New transmission */
    int size;
    if (!isTransmitting && (size = myMoteMemory.getIntValueOf("simOutSize")) > 0) {
      packetFromMote = new COOJARadioPacket(myMoteMemory.getByteArray("simOutDataBuffer", size + 2));

      if (packetFromMote.getPacketData() == null || packetFromMote.getPacketData().length == 0) {
        logger.warn("Skipping zero sized Contiki packet (no buffer)");
        myMoteMemory.setIntValueOf("simOutSize", 0);
        mote.requestImmediateWakeup();
        return;
      }

      byte[] data = packetFromMote.getPacketData();
      txCrc.setCRC(0);
      for (int i = 0; i < size; i++) {
        txCrc.addBitrev(data[i]);
      }
      data[size] = (byte)txCrc.getCRCHi();
      data[size + 1] = (byte)txCrc.getCRCLow();

      isTransmitting = true;

      /* Calculate transmission duration (us) */
      /* XXX Currently floored due to millisecond scheduling! */
      long duration = (int) (Simulation.MILLISECOND*((8 * size /*bits*/) / radioTransmissionRateKBPS));
      transmissionEndTime = now + Math.max(1, duration);

      lastEventTime = now;
      lastEvent = RadioEvent.TRANSMISSION_STARTED;
      radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, this);

      // Deliver packet right away
      lastEvent = RadioEvent.PACKET_TRANSMITTED;
      radioEventTriggers.trigger(RadioEvent.PACKET_TRANSMITTED, this);
    }

    if (isTransmitting && transmissionEndTime > now) {
      mote.scheduleNextWakeup(transmissionEndTime);
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    // Only save radio transmission rate in configuration if different from project default
    if (this.radioTransmissionRateKBPS == this.RADIO_TRANSMISSION_RATE_KBPS) {
      return null;
    }

           ArrayList<Element> config = new ArrayList<>();

           Element element;

           /* Radio bitrate */
           element = new Element("bitrate");
           element.setText(String.valueOf(radioTransmissionRateKBPS));
           config.add(element);

           return config;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML,
                 boolean visAvailable) {
         for (Element element : configXML) {
                 if (element.getName().equals("bitrate")) {
                         radioTransmissionRateKBPS = Double.parseDouble(element.getText());
                 }
         }
  }

  @Override
  public Mote getMote() {
    return mote;
  }

  @Override
  public String toString() {
    return "Radio at " + mote;
  }
}
