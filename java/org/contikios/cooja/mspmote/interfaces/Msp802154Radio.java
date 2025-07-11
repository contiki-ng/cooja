/*
 * Copyright (c) 2008-2012, Swedish Institute of Computer Science.
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

package org.contikios.cooja.mspmote.interfaces;


import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.mspmote.MspMoteTimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.mspsim.chip.CC2420;
import se.sics.mspsim.chip.RFListener;
import se.sics.mspsim.chip.Radio802154;

/**
 * MSPSim 802.15.4 radio to COOJA wrapper.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("IEEE 802.15.4 Radio")
public class Msp802154Radio extends Radio implements CustomDataRadio {
  private static final Logger logger = LoggerFactory.getLogger(Msp802154Radio.class);

  /**
   * Cross-level:
   * Inter-byte delay for delivering cross-level packet bytes.
   */
  public static final long DELAY_BETWEEN_BYTES =
    (long) (1000.0*Simulation.MILLISECOND/(250000.0/8.0)); /* us. Corresponds to 250kbit/s */

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;

  final MspMote mote;
  final Radio802154 radio;

  private boolean isInterfered;
  private boolean isTransmitting;
  private boolean isReceiving;
  private boolean isSynchronized;

  private byte lastOutgoingByte;
  byte lastIncomingByte;

  private RadioPacket lastOutgoingPacket;
  private RadioPacket lastIncomingPacket;

  public Msp802154Radio(Mote m) {
    this.mote = (MspMote)m;
    this.radio = this.mote.getCPU().getChip(Radio802154.class);
    if (radio == null) {
      throw new IllegalStateException("Mote is not equipped with an IEEE 802.15.4 radio");
    }

    radio.addRFListener(new RFListener() {
      int len;
      int expMpduLen;
      final byte[] buffer = new byte[127 + 6];
      final private byte[] syncSeq = {0,0,0,0,0x7A};
      
      @Override
      public void receivedByte(byte data) {
        if (!isTransmitting()) {
          lastEvent = RadioEvent.TRANSMISSION_STARTED;
          lastOutgoingPacket = null;
          isTransmitting = true;
          len = 0;
          expMpduLen = 0;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, Msp802154Radio.this);
        }

        /* send this byte to all nodes */
        lastOutgoingByte = data;
        lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        radioEventTriggers.trigger(RadioEvent.CUSTOM_DATA_TRANSMITTED, Msp802154Radio.this);

        if (len < buffer.length)
          buffer[len] = data;

        len ++;

        if (len == 5) {
          isSynchronized = true;
          for (int i=0; i<5; i++) {
            if (buffer[i] != syncSeq[i]) {
              // this should never happen, but it happens
              logger.error(String.format("Bad outgoing sync sequence %x %x %x %x %x", buffer[0], buffer[1], buffer[2], buffer[3], buffer[4]));
              isSynchronized = false;
              break;
            }
          }
        }
        else if (len == 6) {
          expMpduLen = data & 0xFF;
          if ((expMpduLen & 0x80) != 0) {
            logger.error("Outgoing length field is larger than 127: " + expMpduLen);
          }
        }

        if (((expMpduLen & 0x80) == 0) && len == expMpduLen + 6 && isSynchronized) {
          lastOutgoingPacket = CC2420RadioPacketConverter.fromCC2420ToCooja(buffer);
          if (lastOutgoingPacket != null) {
            lastEvent = RadioEvent.PACKET_TRANSMITTED;
            radioEventTriggers.trigger(RadioEvent.PACKET_TRANSMITTED, Msp802154Radio.this);
          }
          finishTransmission();
        }
      }
    }); /* addRFListener */

    radio.addOperatingModeListener((source, mode) -> {
      if (radio.isReadyToReceive()) {
        lastEvent = RadioEvent.HW_ON;
        radioEventTriggers.trigger(RadioEvent.HW_ON, this);
      } else {
        radioOff(); // actually it is a state change, not necessarily to OFF
      }
    });

    radio.addChannelListener(channel -> {
      /* XXX Currently assumes zero channel switch time */
      lastEvent = RadioEvent.UNKNOWN;
      radioEventTriggers.trigger(RadioEvent.UNKNOWN, this);
    });
  }


  private void finishTransmission()
  {
    if (isTransmitting()) {
      isTransmitting = false;
      isSynchronized = false;
      lastEvent = RadioEvent.TRANSMISSION_FINISHED;
      radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, this);
    }
  }

  private void radioOff() {
    if (isSynchronized)
      logger.warn("Turning off radio while transmitting a packet");
    finishTransmission();
    lastEvent = RadioEvent.HW_OFF;
    radioEventTriggers.trigger(RadioEvent.HW_OFF, this);
  }

  /* Packet radio support */
  @Override
  public RadioPacket getLastPacketTransmitted() {
    return lastOutgoingPacket;
  }

  @Override
  public RadioPacket getLastPacketReceived() {
    return lastIncomingPacket;
  }

  @Override
  public void setReceivedPacket(RadioPacket packet) {
    /* Note:
     * Only nodes at other abstraction levels deliver full packets.
     * MSPSim motes with 802.15.4 radios would instead directly deliver bytes. */

    lastIncomingPacket = packet;
    /* TODO Check isReceiverOn() instead? */
    if (!radio.isReadyToReceive()) {
      logger.warn("Radio receiver not ready, dropping packet data");
      return;
    }

    /* Delivering packet bytes with delays */
    byte[] packetData = CC2420RadioPacketConverter.fromCoojaToCC2420(packet);
    long deliveryTime = getMote().getSimulation().getSimulationTime();
    for (byte b: packetData) {
      if (isInterfered()) {
        b = (byte) 0xFF;
      }

      final byte byteToDeliver = b;
      getMote().getSimulation().scheduleEvent(new MspMoteTimeEvent(mote) {
        @Override
        public void execute(long t) {
          super.execute(t);
          radio.receivedByte(byteToDeliver);
          mote.requestImmediateWakeup();
        }
      }, deliveryTime);
      deliveryTime += DELAY_BETWEEN_BYTES;
    }
  }

  /* Custom data radio support */
  @Override
  public Object getLastCustomDataTransmitted() {
    return lastOutgoingByte;
  }

  @Override
  public Object getLastCustomDataReceived() {
    return lastIncomingByte;
  }

  @Override
  public void receiveCustomData(Object data) {
    if (!(data instanceof Byte lastIncomingByte)) {
      logger.error("Bad custom data: " + data);
      return;
    }

    final byte inputByte;
    if (isInterfered()) {
      inputByte = (byte)0xFF;
    } else {
      inputByte = lastIncomingByte;
    }
    mote.getSimulation().scheduleEvent(new MspMoteTimeEvent(mote) {
      @Override
      public void execute(long t) {
        super.execute(t);
        radio.receivedByte(inputByte);
        mote.requestImmediateWakeup();
      }
    }, mote.getSimulation().getSimulationTime());

  }

  /* General radio support */
  @Override
  public boolean isTransmitting() {
    return isTransmitting;
  }

  @Override
  public boolean isReceiving() {
    return isReceiving;
  }

  @Override
  public boolean isInterfered() {
    return isInterfered;
  }

  @Override
  public int getChannel() {
    return radio.getActiveChannel();
  }

  public int getFrequency() {
    return radio.getActiveFrequency();
  }

  @Override
  public void signalReceptionStart() {
    isReceiving = true;

    lastEvent = RadioEvent.RECEPTION_STARTED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_STARTED, this);
  }

  @Override
  public void signalReceptionEnd() {
    /* Deliver packet data */
    isReceiving = false;
    isInterfered = false;

    lastEvent = RadioEvent.RECEPTION_FINISHED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_FINISHED, this);
  }

  @Override
  public RadioEvent getLastEvent() {
    return lastEvent;
  }

  @Override
  public void interfereAnyReception() {
    isInterfered = true;
    isReceiving = false;
    lastIncomingPacket = null;

    lastEvent = RadioEvent.RECEPTION_INTERFERED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_INTERFERED, this);
  }

  @Override
  public double getCurrentOutputPower() {
    return radio.getOutputPower();
  }

  @Override
  public int getCurrentOutputPowerIndicator() {
    return radio.getOutputPowerIndicator();
  }

  @Override
  public int getOutputPowerIndicatorMax() {
    return radio.getOutputPowerIndicatorMax();
  }

  /**
   * Current received signal strength.
   * May differ from CC2420's internal value which is an average of the last 8 symbols.
   */
  double currentSignalStrength;

  /**
   * Last 8 received signal strengths
   */
  private final double[] rssiLast = new double[8];
  private int rssiLastCounter;

  @Override
  public double getCurrentSignalStrength() {
    return currentSignalStrength;
  }

  @Override
  public void setCurrentSignalStrength(final double signalStrength) {
    if (signalStrength == currentSignalStrength) {
      return; /* ignored */
    }
    currentSignalStrength = signalStrength;
    if (rssiLastCounter == 0) {
      getMote().getSimulation().scheduleEvent(new MspMoteTimeEvent(mote) {
        @Override
        public void execute(long t) {
          super.execute(t);

          /* Update average */
          System.arraycopy(rssiLast, 1, rssiLast, 0, 7);
          rssiLast[7] = currentSignalStrength;
          double avg = 0;
          for (double v: rssiLast) {
            avg += v;
          }
          avg /= rssiLast.length;

          radio.setRSSI((int) avg);

          rssiLastCounter--;
          if (rssiLastCounter > 0) {
            mote.getSimulation().scheduleEvent(this, t+DELAY_BETWEEN_BYTES/2);
          }
        }
      }, mote.getSimulation().getSimulationTime());
    }
    rssiLastCounter = 8;
  }
  
  
  /**
   * This will set the CORR-value of the CC2420
   * 
   * @see org.contikios.cooja.interfaces.Radio#setLQI(int)
   */
  @Override
  public void setLQI(int lqi){
	  radio.setLQI(lqi);
  }

  @Override
  public int getLQI(){
	  return radio.getLQI();
  }
  
  
  @Override
  public Mote getMote() {
    return mote;
  }

  @Override
  public Position getPosition() {
    return mote.getInterfaces().getPosition();
  }

  @Override
  public boolean isRadioOn() {
    if (radio.isReadyToReceive()) {
      return true;
    }
    if (radio.getMode() == CC2420.MODE_POWER_OFF) {
      return false;
    }
    return radio.getMode() != CC2420.MODE_TXRX_OFF;
  }
  
  @Override
  public boolean canReceiveFrom(CustomDataRadio radio) {
    return radio instanceof Msp802154Radio;
  }
}
