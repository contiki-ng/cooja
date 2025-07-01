/*
 * Copyright (c) 2010, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * This file is part of MSPSim.
 *
 * $Id$
 *
 * -----------------------------------------------------------------
 *
 * JCreateNode
 *
 * Author  : Joakim Eriksson, Niclas Finne
 * Created : 7 jul 2010
 * Updated : $Date$
 *           $Revision$
 */

package se.sics.mspsim.platform.sentillausb;

import se.sics.mspsim.chip.Leds;
import se.sics.mspsim.chip.M25P80;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.platform.sky.CC2420Node;

/**
 * Emulation of Sentilla Gateway USB Mote
 */
public class SentillaUSBNode extends CC2420Node<M25P80> {

    public static final int MODE_LEDS_OFF = 0;
    public static final int MODE_LEDS_1 = 1;
    public static final int MODE_LEDS_2 = 2;
    public static final int MODE_MAX = MODE_LEDS_2;

    private static final int[] LEDS = { 0xff2020, 0x40ff40 };
    public static final int GREEN_LED = 0x20;
    public static final int RED_LED = 0x10;

    private SentillaUSBGui gui;

    private final Leds leds;
    boolean redLed;
    boolean greenLed;

    public SentillaUSBNode(MSP430 cpu, M25P80 flash) {
        super("Sentilla USB", cpu, flash);
        setMode(MODE_LEDS_OFF);
        leds = new Leds(cpu, LEDS);
    }

    public Leds getLeds() {
        return leds;
    }

    // USART Listener
    @Override
    public void dataReceived(USARTSource source, int data) {
        radio.dataReceived(source, data);
        flash.dataReceived(source, data);
        /* if nothing selected, just write back a random byte to this device */
        if (!radio.getChipSelect() && !flash.getChipSelect()) {
            source.byteReceived(0);
        }
    }

    @Override
    protected void flashWrite(IOPort source, int data) {
        flash.portWrite(source, data);
    }

    @Override
    public void setupGUI() {
        super.setupGUI();
        if (gui == null) {
            gui = new SentillaUSBGui(this);
            registry.registerComponent("nodegui", gui);
        }
    }

    @Override
    public void portWrite(IOPort source, int data) {
        super.portWrite(source, data);

        if (source == port5) {
            redLed = (data & RED_LED) == 0;
            greenLed = (data & GREEN_LED) == 0;
            leds.setLeds((redLed ? 1 : 0) + (greenLed ? 2 : 0));
            int newMode = (redLed ? 1 : 0) + (greenLed ? 1 : 0);
            setMode(newMode);
        }
    }

    @Override
    public int getModeMax() {
        return MODE_MAX;
    }
}
