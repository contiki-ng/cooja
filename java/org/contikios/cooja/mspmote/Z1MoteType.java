/*
 * Copyright (c) 2012, Swedish Institute of Computer Science.
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

package org.contikios.cooja.mspmote;
import java.io.IOException;
import java.util.List;
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mspmote.interfaces.CoojaM25P80;
import org.contikios.cooja.mspmote.interfaces.Msp802154BitErrorRadio;
import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.mspmote.interfaces.MspButton;
import org.contikios.cooja.mspmote.interfaces.MspClock;
import org.contikios.cooja.mspmote.interfaces.MspDebugOutput;
import org.contikios.cooja.mspmote.interfaces.MspDefaultSerial;
import org.contikios.cooja.mspmote.interfaces.MspLED;
import org.contikios.cooja.mspmote.interfaces.MspMoteID;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.platform.z1.Z1Node;

@ClassDescription("Z1 mote")
@AbstractionLevelDescription("Emulated level")
public class Z1MoteType extends MspMoteType {

    @Override
    public String getMoteType() {
        return "z1";
    }

    @Override
    public String getMoteName() {
        return "Z1";
    }

    @Override
    protected String getMoteImage() {
        return "/images/z1.jpg";
    }

    @Override
    public MspMote generateMote(Simulation simulation) throws MoteTypeCreationException {
        MSP430 cpu;
        try {
            cpu = Z1Node.makeCPU(Z1Node.makeChipConfig(), fileFirmware.getAbsolutePath());
        } catch (IOException e) {
            throw new MoteTypeCreationException("Failed to create CPU", e);
        }
        return new Z1Mote(this, simulation, new Z1Node(cpu, new CoojaM25P80(cpu)));
    }

    @Override
    public List<Class<? extends MoteInterface>> getDefaultMoteInterfaceClasses() {
        return List.of(
                Position.class,
                IPAddress.class,
                Mote2MoteRelations.class,
                MoteAttributes.class,
                MspClock.class,
                MspMoteID.class,
                MspButton.class,
//                SkyFlash.class,
                Msp802154Radio.class,
                MspDefaultSerial.class,
                MspLED.class,
                MspDebugOutput.class);
    }

    @Override
    public List<Class<? extends MoteInterface>> getAllMoteInterfaceClasses() {
        return List.of(
                Position.class,
                IPAddress.class,
                Mote2MoteRelations.class,
                MoteAttributes.class,
                MspClock.class,
                MspMoteID.class,
                MspButton.class,
//                SkyFlash.class,
                Msp802154Radio.class,
                Msp802154BitErrorRadio.class,
                MspDefaultSerial.class,
                MspLED.class,
                MspDebugOutput.class);
    }
}
