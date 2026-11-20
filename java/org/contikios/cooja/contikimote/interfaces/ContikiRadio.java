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
import org.contikios.cooja.COOJARadioPacket;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.mote.memory.VarMemory;
import org.contikios.cooja.radiomediums.UDGM;
import org.contikios.cooja.util.CCITT_CRC;
import org.jdom2.Element;
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

  private static final byte[] SHR = { 0x00, 0x00, 0x00, 0x00, (byte) 0xA7 };

  /**
   * Project default transmission bitrate (kbps).
   */
  private long TICKS_PER_BYTE = 32;
  private final double RADIO_TRANSMISSION_RATE_KBPS;
  private final CCITT_CRC txCrc = new CCITT_CRC();
  private final int CRC_LEN = 2;

  /**
   * Configured transmission bitrate (kbps).
   */
  private double radioTransmissionRateKBPS;

  private RadioPacket packetToMote;

  private RadioPacket packetFromMote;

  private boolean radioOn;

  private boolean isInterfered;

  private long transmissionStartTime = -1;

  private boolean transmissionShrIssued;

  private byte sequencePointer;

  private int alreadyTransmittedBytes;

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;

  private int oldOutputPowerIndicator = -1;

  private int oldRadioChannel = -1;

  private boolean fifopIssued;

  private boolean receptionShrIssued;

  private long receptionStartTime = -1;

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
    return myMoteMemory.getIntValueOf("simTransmitting") == 1;
  }

  @Override
  public boolean isReceiving() {
    return myMoteMemory.getIntValueOf("simReceiving") == 1;
  }

  @Override
  public boolean isInterfered() {
    return isInterfered;
  }

  @Override
  public int getChannel() {
    return myMoteMemory.getIntValueOf("simRadioChannel");
  }

  private boolean hasPendingOutgoingFrame() {
    return myMoteMemory.getIntValueOf("simPendingOutgoingFrame") != 0;
  }

  private boolean isSearchingForShr() {
    return myMoteMemory.getIntValueOf("simShrSearching") != 0;
  }

  private int getFifopThreshold() {
    return myMoteMemory.getIntValueOf("simFifopThreshold");
  }

  private boolean isAutoCrcOn() {
    return myMoteMemory.getIntValueOf("simAutoCrc") != 0;
  }

  private boolean shallEnterRxAfterTx() {
    return myMoteMemory.getIntValueOf("simShallEnterRxAfterTx") != 0;
  }

  private boolean isSequence() {
    return myMoteMemory.getIntValueOf("simIsSequence") != 0;
  }

  private boolean shallStopSequence() {
    return myMoteMemory.getIntValueOf("simStopSequence") != 0;
  }

  @Override
  public void signalReceptionStart() {
    assert packetToMote != null;
    assert radioOn;

    /* check if we can receive right now */
    if (isInterfered() || isReceiving() || isTransmitting() || !isSearchingForShr()) {
      interfereAnyReception();
      return;
    }

    /* update C side */
    myMoteMemory.setIntValueOf("simReceiving", 1); /* TODO delay until SHR */

    /* update internal state */
    fifopIssued = false;
    receptionShrIssued = false;
    receptionStartTime = mote.getSimulation().getSimulationTime();

    /* notify */
    lastEvent = RadioEvent.RECEPTION_STARTED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_STARTED, this);

    mote.scheduleNextWakeup(receptionStartTime + computeTransmissionDuration(SHR.length));
  }

  private void receiveNextByte(int receivedBytes) {
    byte newData[] = packetToMote.getPacketData();
    byte oldData[] = myMoteMemory.getByteArray("simInDataBuffer", 128);

    myMoteMemory.setIntValueOf("simReceivedBytes", receivedBytes);
    oldData[receivedBytes - 1] = isInterfered
        ? (byte) ~newData[receivedBytes - 1]
        : newData[receivedBytes - 1];
    /* TODO handle the case when the previous packet is unread */
    myMoteMemory.setByteArray("simInDataBuffer", oldData);
  }

  @Override
  public void signalReceptionEnd() {
    if (isReceiving()) {
      assert radioOn;

      /* receive final byte */
      receiveNextByte(packetToMote.getPacketData().length);

      /* update C side */
      myMoteMemory.setIntValueOf("simPendingIncomingFrame",
          isInterfered && isAutoCrcOn() ? 0 : 1);
      myMoteMemory.setIntValueOf("simRxdoneInterrupt", 1);
      myMoteMemory.setIntValueOf("simReceiving", 0);
      mote.requestImmediateWakeup();
    }

    /* update internal state */
    isInterfered = false;

    /* notify */
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

  private long computeTransmissionDuration(int bytes) {
    return bytes * TICKS_PER_BYTE;
  }

  private int computeReceivedBytes() {
    long now = mote.getSimulation().getSimulationTime();
    return (int) ((now - receptionStartTime) / TICKS_PER_BYTE);
  }

  private int computeTransmittedBytes() {
    long now = mote.getSimulation().getSimulationTime();
    return (int) ((now - transmissionStartTime) / TICKS_PER_BYTE);
  }

  @Override
  public void doActionsAfterTick() {
    long now = mote.getSimulation().getSimulationTime();

    /* Check if radio hardware status changed */
    if (radioOn != (myMoteMemory.getByteValueOf("simRadioHWOn") == 1)) {
      radioOn = !radioOn;

      if (!radioOn) {
        if (isTransmitting()) {
          /* cause ongoing receptions to fail by invalidating CRC or MIC */
          packetFromMote.getPacketData()[packetFromMote.getPacketData().length - 1] =
              (byte) ~packetFromMote.getPacketData()[packetFromMote.getPacketData().length - 1];
          lastEvent = RadioEvent.TRANSMISSION_FINISHED;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, this);
          /* TODO in the case of a canceled transmission, receivers that received
           * an SHR interrupt should also get FIFOP and RXDONE interrupts */
        }
        myMoteMemory.setIntValueOf("simPendingIncomingFrame", 0);
        myMoteMemory.setIntValueOf("simReceiving", 0);
        myMoteMemory.setIntValueOf("simTransmitting", 0);
        lastEvent = RadioEvent.HW_OFF;
      } else {
        lastEvent = RadioEvent.HW_ON;
      }

      radioEventTriggers.trigger(lastEvent, this);
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
      isInterfered = false;
      lastEvent = RadioEvent.CHANNEL_HOP;
      radioEventTriggers.trigger(RadioEvent.CHANNEL_HOP, this);
    }

    if (isReceiving()) {
      assert !isTransmitting();
      int receivedBytes = computeReceivedBytes();

      /* move received data to mote */
      if (receivedBytes > SHR.length) {
        receiveNextByte(receivedBytes - SHR.length);
      } else {
        myMoteMemory.setIntValueOf("simReceivedBytes", 0);
      }

      if (!receptionShrIssued && receivedBytes >= SHR.length) {
        /* issue SHR interrupt */
        myMoteMemory.setInt64ValueOf("simLastPacketTimestamp", now);
        myMoteMemory.setIntValueOf("simShrInterrupt", 1);
        mote.requestImmediateWakeup();
        receptionShrIssued = true;
      } else if (!fifopIssued && ((receivedBytes - SHR.length) > getFifopThreshold())) {
        /* issue FIFOP interrupt */
        myMoteMemory.setIntValueOf("simFifopInterrupt", 1);
        mote.requestImmediateWakeup();
        fifopIssued = true;
      }

      /* schedule next wake up */
      mote.scheduleNextWakeup(receptionStartTime + computeTransmissionDuration(receivedBytes + 1));
    }

    /* New transmission */
    if (hasPendingOutgoingFrame()) {
      if (isReceiving()) {
        myMoteMemory.setIntValueOf("simReceiving", 0);
      }
      myMoteMemory.setIntValueOf("simPendingOutgoingFrame", 0);
      myMoteMemory.setIntValueOf("simTransmitting", 1);

      /* instantiate COOJARadioPacket */
      int size = myMoteMemory.getByteValueOf("simOutDataBuffer");
      if (size == 0) {
        logger.warn("Skipping zero sized Contiki packet (no buffer)");
        mote.requestImmediateWakeup();
        return;
      }
      if (isAutoCrcOn()) {
        size += CRC_LEN;
      }
      packetFromMote = new COOJARadioPacket(new byte[1 + size]);

      /* update internal state */
      transmissionShrIssued = false;
      transmissionStartTime = now;
      alreadyTransmittedBytes = 0;

      /* notify */
      lastEvent = RadioEvent.TRANSMISSION_STARTED;
      radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, this);
    }

    if (isTransmitting()) {
      int transmittedBytes = computeTransmittedBytes();
      sequencePointer = (byte) ((transmittedBytes - SHR.length) & 0x7F);
      transmittedBytes -= alreadyTransmittedBytes;

      if (!isSequence() && !transmissionShrIssued && (transmittedBytes >= SHR.length)) {
        /* issue SHR interrupt */
        myMoteMemory.setIntValueOf("simShrInterrupt", 1);
        mote.requestImmediateWakeup();
        transmissionShrIssued = true;
      } else if (transmittedBytes == SHR.length + packetFromMote.getPacketData().length) {
        if (!isSequence()) {
          /* issue TXDONE interrupt */
          myMoteMemory.setIntValueOf("simTransmitting", 0);
          mote.requestImmediateWakeup();
          myMoteMemory.setIntValueOf("simTxdoneInterrupt", 1);
        } else if (shallStopSequence()) {
          myMoteMemory.setIntValueOf("simTransmitting", 0);
          mote.requestImmediateWakeup();
        }

        /* notify */
        lastEvent = RadioEvent.TRANSMISSION_FINISHED;
        radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, this);

        if (isSequence() && !shallStopSequence()) {
          alreadyTransmittedBytes += transmittedBytes;
          byte outDataBuffer[] = myMoteMemory.getByteArray("simOutDataBuffer", 128);
          int index = (sequencePointer + SHR.length) & 0x7F;
          packetFromMote = new COOJARadioPacket(new byte[1 + outDataBuffer[index]]);
          lastEvent = RadioEvent.TRANSMISSION_STARTED;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, this);
          mote.scheduleNextWakeup(
              transmissionStartTime
                  + computeTransmissionDuration(alreadyTransmittedBytes + 1));
        } else {
          myMoteMemory.setIntValueOf("simStopSequence", 0);
          /* either enter RX or switch off */
          if (shallEnterRxAfterTx()) {
            myMoteMemory.setIntValueOf("simPendingIncomingFrame", 0);
            myMoteMemory.setIntValueOf("simReceiving", 0);
          } else {
            radioOn = false;
            myMoteMemory.setByteValueOf("simRadioHWOn", (byte) 0);
            lastEvent = RadioEvent.HW_OFF;
            radioEventTriggers.trigger(RadioEvent.HW_OFF, this);
          }
        }
      } else {
        /* update packet contents gradually for supporting async_reprepare */
        byte oldPacketData[] = packetFromMote.getPacketData();
        byte newPacketData[] = myMoteMemory.getByteArray("simOutDataBuffer", 128);
        int size = oldPacketData.length;
        if (transmittedBytes >= SHR.length) {
          if (!isAutoCrcOn() || ((transmittedBytes - SHR.length) < (size - CRC_LEN))) {
            oldPacketData[transmittedBytes - SHR.length] = newPacketData[sequencePointer];
          } else if ((transmittedBytes - SHR.length) == (size - CRC_LEN)) {
            txCrc.setCRC(0);
            for (int i = 1; i < size - CRC_LEN; i++) {
              txCrc.addBitrev(oldPacketData[i]);
            }
            oldPacketData[size - CRC_LEN] = (byte) txCrc.getCRCHi();
          } else {
            oldPacketData[size - 1] = (byte) txCrc.getCRCLow();
          }
        }
        mote.scheduleNextWakeup(
            transmissionStartTime + computeTransmissionDuration(alreadyTransmittedBytes + transmittedBytes + 1));
      }
    }
  }

  public static String print(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    sb.append("[ ");
    for (byte b : bytes) {
      sb.append(String.format("0x%02X ", b));
    }
    sb.append("]");
    return sb.toString();
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
