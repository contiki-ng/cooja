
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
import se.sics.mspsim.chip.CC2520;
import se.sics.mspsim.chip.RFListener;

/**
 * MSPSim CC2520 radio to COOJA wrapper.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("IEEE CC2520 Radio")
public class CC2520Radio extends Radio implements CustomDataRadio {
  private static final Logger logger = LoggerFactory.getLogger(CC2520Radio.class);

  /**
   * Cross-level:
   * Inter-byte delay for delivering cross-level packet bytes.
   */
  public static final long DELAY_BETWEEN_BYTES =
    (long) (1000.0*Simulation.MILLISECOND/(250000.0/8.0)); /* us. Corresponds to 250kbit/s */

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;

  private final MspMote mote;
  private final CC2520 radio;

  private boolean isInterfered;
  private boolean isTransmitting;
  private boolean isReceiving;

  private byte lastOutgoingByte;
  private byte lastIncomingByte;

  private RadioPacket lastOutgoingPacket;
  private RadioPacket lastIncomingPacket;

  public CC2520Radio(Mote m) {
    this.mote = (MspMote)m;
    this.radio = this.mote.getCPU().getChip(CC2520.class);
    if (radio == null) {
      throw new IllegalStateException("Mote is not equipped with an IEEE CC2520 radio");
    }

    radio.addRFListener(new RFListener() {
      int len;
      int expLen;
      final byte[] buffer = new byte[127 + 15];
      @Override
      public void receivedByte(byte data) {
        if (!isTransmitting()) {
          lastEvent = RadioEvent.TRANSMISSION_STARTED;
          isTransmitting = true;
          len = 0;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, CC2520Radio.this);
        }

        if (len >= buffer.length) {
          /* Bad size packet, too large */
          logger.debug("Error: bad size: " + len + ", dropping outgoing byte: " + data);
          return;
        }

        /* send this byte to all nodes */
        lastOutgoingByte = data;
        lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        radioEventTriggers.trigger(RadioEvent.CUSTOM_DATA_TRANSMITTED, CC2520Radio.this);

        buffer[len++] = data;

        if (len == 6) {
//          System.out.println("## CC2520 Packet of length: " + data + " expected...");
          expLen = data + 6;
        }

        if (len == expLen) {
		len -= 4; /* preamble */
		len -= 1; /* synch */
		len -= radio.getFooterLength(); /* footer */
		final byte[] packetdata = new byte[len];
		System.arraycopy(buffer, 4+1, packetdata, 0, len);
          lastOutgoingPacket = () -> packetdata;
          // TODO: no lastEvent set by observers code, verify that this is correct.
          radioEventTriggers.trigger(RadioEvent.CUSTOM_DATA_TRANSMITTED, CC2520Radio.this);

          isTransmitting = false;
          lastEvent = RadioEvent.TRANSMISSION_FINISHED;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, CC2520Radio.this);
          len = 0;
        }
      }
    });

    radio.addOperatingModeListener((source, mode) -> {
      if (radio.isReadyToReceive()) {
        lastEvent = RadioEvent.HW_ON;
        radioEventTriggers.trigger(RadioEvent.HW_ON, this);
      } else {
        radioOff();
      }
    });

    radio.addChannelListener(channel -> {
      /* XXX Currently assumes zero channel switch time */
      lastEvent = RadioEvent.UNKNOWN;
      radioEventTriggers.trigger(RadioEvent.UNKNOWN, this);
    });
  }

  private void radioOff() {
    /* Radio was turned off during transmission.
     * May for example happen if watchdog triggers */
    if (isTransmitting()) {
      logger.warn("Turning off radio while transmitting, ending packet prematurely");

      /* Simulate end of packet */
      lastOutgoingPacket = () -> new byte[0];

      lastEvent = RadioEvent.PACKET_TRANSMITTED;
      radioEventTriggers.trigger(RadioEvent.PACKET_TRANSMITTED, this);

      /* Register that transmission ended in radio medium */
      isTransmitting = false;
      lastEvent = RadioEvent.TRANSMISSION_FINISHED;
      radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, this);
    }

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
    logger.error("TODO Implement me!");
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
    if (!(data instanceof Byte)) {
      logger.error("Bad custom data: " + data);
      return;
    }
    lastIncomingByte = (Byte) data;

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
	  return 100;
//    return radio.getOutputPowerIndicator();
  }

  @Override
  public int getOutputPowerIndicatorMax() {
	  return 100;
//    return 31;
  }

  private double currentSignalStrength;

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
    if (radio.getMode() == CC2520.MODE_POWER_OFF) {
      return false;
    }
    return radio.getMode() != CC2520.MODE_TXRX_OFF;
  }

  @Override
  public boolean canReceiveFrom(CustomDataRadio radio) {
    return radio.getClass().equals(this.getClass());
  }
}
