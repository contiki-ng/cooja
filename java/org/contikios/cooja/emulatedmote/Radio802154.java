/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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
package org.contikios.cooja.emulatedmote;

import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 802.15.4 radio class for COOJA.
 *
 * @author Joakim Eriksson
 */

public abstract class Radio802154 extends Radio implements CustomDataRadio {

    private final static boolean DEBUG = false;
    
    private static final Logger logger = LoggerFactory.getLogger(Radio802154.class);

    protected long lastEventTime;

    protected RadioEvent lastEvent = RadioEvent.UNKNOWN;

    protected boolean isInterfered;

    protected boolean isReceiving;

    private RadioByte lastOutgoingByte;

    private RadioByte lastIncomingByte;

    private RadioPacket lastOutgoingPacket;

    private RadioPacket lastIncomingPacket;

    //    private int mode;
    protected final Mote mote;
     
    public Radio802154(Mote mote) {
        this.mote = mote;
    }

    int len;
    int expLen;
    final byte[] buffer = new byte[127 + 15];
    protected void handleTransmit(byte val) {
        if (len == 0) {
            lastEventTime = mote.getSimulation().getSimulationTime();
            lastEvent = RadioEvent.TRANSMISSION_STARTED;
            radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, this);
        }
        /* send this byte to all nodes */
        lastOutgoingByte = new RadioByte(val);
        lastEventTime = mote.getSimulation().getSimulationTime();
        lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        radioEventTriggers.trigger(RadioEvent.CUSTOM_DATA_TRANSMITTED, this);

        buffer[len++] = val;

        logger.debug("802.15.4: " + (val & 0xff) + " transmitted...");

        if (len == 6) {
            expLen = val + 6;
        }

        if (len == expLen) {
            lastOutgoingPacket = Radio802154PacketConverter.fromCC2420ToCooja(buffer);
            lastEventTime = mote.getSimulation().getSimulationTime();
            lastEvent = RadioEvent.PACKET_TRANSMITTED;
            radioEventTriggers.trigger(RadioEvent.PACKET_TRANSMITTED, this);

            lastEventTime = mote.getSimulation().getSimulationTime();
            lastEvent = RadioEvent.TRANSMISSION_FINISHED;
            radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, this);
            len = 0;
        }
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
        if (data instanceof RadioByte) {
            lastIncomingByte = (RadioByte) data;
            handleReceive(lastIncomingByte.getPacketData()[0]);
        }
    }

    /* General radio support */
    @Override
    public boolean isReceiving() {
        return isReceiving;
    }

    @Override
    public boolean isInterfered() {
        return isInterfered;
    }

    protected abstract void handleReceive(byte b);

    protected abstract void handleEndOfReception();

    @Override
    public abstract int getChannel();

    public abstract int getFrequency();

    @Override
    public abstract boolean isRadioOn();

    @Override
    public abstract double getCurrentOutputPower();
    
    @Override
    public abstract int getCurrentOutputPowerIndicator();

    @Override
    public abstract int getOutputPowerIndicatorMax();

    @Override
    public abstract double getCurrentSignalStrength();

    @Override
    public abstract void setCurrentSignalStrength(double signalStrength);

    /* need to add a few more methods later??? */
    @Override
    public void signalReceptionStart() {
        isReceiving = true;

        //      cc2420.setCCA(true);
        //      hasFailedReception = mode == CC2420.MODE_TXRX_OFF;
        /* TODO cc2420.setSFD(true); */

        lastEventTime = mote.getSimulation().getSimulationTime();
        lastEvent = RadioEvent.RECEPTION_STARTED;
        radioEventTriggers.trigger(RadioEvent.RECEPTION_STARTED, this);
    }

    @Override
    public void signalReceptionEnd() {
        /* Deliver packet data */
        isReceiving = false;
        //      hasFailedReception = false;
        isInterfered = false;
        //      cc2420.setCCA(false);

        /* tell the receiver that the packet is ended */
        handleEndOfReception();

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
        isInterfered = true;
        isReceiving = false;
        //      hasFailedReception = false;
        lastIncomingPacket = null;

        //cc2420.setCCA(true);

        /* is this ok ?? */
        handleEndOfReception();
        //recv.nextByte(false, (byte)0);

        lastEventTime = mote.getSimulation().getSimulationTime();
        lastEvent = RadioEvent.RECEPTION_INTERFERED;
        radioEventTriggers.trigger(RadioEvent.RECEPTION_INTERFERED, this);
    }

    @Override
    public Mote getMote() {
        return mote;
    }

    @Override
    public Position getPosition() {
        return mote.getInterfaces().getPosition();
    }

}
