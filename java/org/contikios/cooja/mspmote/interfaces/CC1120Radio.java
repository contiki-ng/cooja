/*
 * Copyright (c) 2012, Thingsquare.
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
import se.sics.mspsim.chip.CC1120;
import se.sics.mspsim.chip.RFListener;
import se.sics.mspsim.chip.Radio802154;

/**
 * @author Fredrik Osterlind
 */
@ClassDescription("TI CC1120")
public class CC1120Radio extends Radio implements CustomDataRadio {
	private static final Logger logger = LoggerFactory.getLogger(CC1120Radio.class);

	/**
	 * Cross-level:
	 * Inter-byte delay for delivering cross-level packet bytes.
	 */
	/* TODO XXX Fix me as well as symbol duration in CC1120.java */
	public static final long DELAY_BETWEEN_BYTES =
			(long) (1000.0*Simulation.MILLISECOND/(200000.0/8.0)); /* us. Corresponds to 200kbit/s */

	private RadioEvent lastEvent = RadioEvent.UNKNOWN;

	private final MspMote mote;
	private final CC1120 cc1120;

	private boolean isInterfered;
	private boolean isTransmitting;
	private boolean isReceiving;

	private byte lastOutgoingByte;
	private byte lastIncomingByte;

	private RadioPacket lastOutgoingPacket;
	private RadioPacket lastIncomingPacket;

	public CC1120Radio(Mote m) {
		this.mote = (MspMote)m;
		Radio802154 r = this.mote.getCPU().getChip(Radio802154.class);
		if (!(r instanceof CC1120)) {
			throw new IllegalStateException("Mote is not equipped with an CC1120 radio");
		}
		this.cc1120 = (CC1120) r;

		cc1120.addRFListener(new RFListener() {
			int len;
			int expLen;
			final byte[] buffer = new byte[256 + 15];
			private boolean gotSynchbyte;
			@Override
			public void receivedByte(byte data) {
				if (!isTransmitting()) {
					/* Start transmission */
					lastEvent = RadioEvent.TRANSMISSION_STARTED;
					isTransmitting = true;
					len = 0;
					gotSynchbyte = false;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, CC1120Radio.this);
				}
				if (len >= buffer.length) {
					/* Bad size packet, too large */
					logger.debug("Error: bad size: " + len + ", dropping outgoing byte: " + data);
					return;
				}

				/* send this byte to all nodes */
				lastOutgoingByte = data;
				lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        radioEventTriggers.trigger(RadioEvent.CUSTOM_DATA_TRANSMITTED, CC1120Radio.this);

				/* Await synch byte */
				if (!gotSynchbyte) {
					if (lastOutgoingByte == CC1120.SYNCH_BYTE_LAST) {
						gotSynchbyte = true;
					}
					return;
				}

				final int HEADERLEN = 1; /* 1x Length byte */
				final int FOOTERLEN = 2; /* TODO Fix CRC in Mspsim's CCC1120.java */
				if (len == 0) {
					expLen = (0xff&data) + HEADERLEN + FOOTERLEN;
				}
				buffer[len++] = data;

				if (len == expLen) {
					final byte[] buf = new byte[expLen];
					System.arraycopy(buffer, 0, buf, 0, expLen);
          lastOutgoingPacket = () -> buf;

					lastEvent = RadioEvent.PACKET_TRANSMITTED;
          radioEventTriggers.trigger(RadioEvent.PACKET_TRANSMITTED, CC1120Radio.this);

					isTransmitting = false;
					lastEvent = RadioEvent.TRANSMISSION_FINISHED;
          radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, CC1120Radio.this);
					len = 0;
				}
			}
		});

    cc1120.setReceiverListener(on -> {
      if (cc1120.isReadyToReceive()) {
        lastEvent = RadioEvent.HW_ON;
        radioEventTriggers.trigger(RadioEvent.HW_ON, this);
      } else {
        radioOff();
      }
    });

    cc1120.addChannelListener(channel -> {
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
		lastIncomingPacket = packet;

		/* TODO XXX Need support in CCC1120.java */
		/*if (!radio.isReadyToReceive()) {
			logger.warn("Radio receiver not ready, dropping packet data");
			return;
		}*/

		/* Delivering packet bytes with delays */
		byte[] packetData = packet.getPacketData();
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
					cc1120.receivedByte(byteToDeliver);
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
				cc1120.receivedByte(inputByte);
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
		return cc1120.getActiveChannel()+1000;
	}

	public int getFrequency() {
		return cc1120.getActiveFrequency();
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
		/* TODO XXX Need support in CCC1120.java */
		return 1;
	}
	@Override
	public int getCurrentOutputPowerIndicator() {
		/* TODO XXX Need support in CCC1120.java */
		return 10;
	}
	@Override
	public int getOutputPowerIndicatorMax() {
		/* TODO XXX Need support in CCC1120.java */
		return 10;
	}


	/**
	 * Last 8 received signal strengths
	 */
	private double currentSignalStrength;
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

					cc1120.setRSSI((int) avg);

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
	public Mote getMote() {
		return mote;
	}

	@Override
	public Position getPosition() {
		return mote.getInterfaces().getPosition();
	}

	@Override
	public boolean isRadioOn() {
	    return cc1120.isReadyToReceive();
	}

  @Override
  public boolean canReceiveFrom(CustomDataRadio radio) {
    return radio.getClass().equals(this.getClass());
  }

}
