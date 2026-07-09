/*
 * Copyright (c) 2026, RISE Research Institutes of Sweden AB
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
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.mspmote;

import java.io.IOException;
import java.util.List;
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mspmote.interfaces.MspButton;
import org.contikios.cooja.mspmote.interfaces.MspClock;
import org.contikios.cooja.mspmote.interfaces.MspDebugOutput;
import org.contikios.cooja.mspmote.interfaces.MspDefaultSerial;
import org.contikios.cooja.mspmote.interfaces.MspExp430Fr5969LED;
import org.contikios.cooja.mspmote.interfaces.MspMoteID;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.platform.fr5969.FR5969Node;

/**
 * MSP-EXP430FR5969 LaunchPad mote type. The board has no radio, so the mote
 * exposes only serial / button / LED interfaces.
 */
@ClassDescription("MSP-EXP430FR5969 mote")
@AbstractionLevelDescription("Emulated level")
public class MspExp430Fr5969MoteType extends MspMoteType {

    @Override
    public String getMoteType() {
        return "msp-exp430fr5969";
    }

    @Override
    public String getMoteName() {
        return "MSP-EXP430FR5969";
    }

    @Override
    protected String getMoteImage() {
        return null;  // No image available yet
    }

    @Override
    public MspMote generateMote(Simulation simulation) throws MoteTypeCreationException {
        MSP430 cpu;
        try {
            cpu = FR5969Node.makeCPU(FR5969Node.makeChipConfig(),
                    fileFirmware.getAbsolutePath());
        } catch (IOException e) {
            throw new MoteTypeCreationException("Failed to create FR5969 CPU", e);
        }
        return new MspExp430Fr5969Mote(this, simulation, new FR5969Node(cpu));
    }

    @Override
    public List<Class<? extends MoteInterface>> getDefaultMoteInterfaceClasses() {
        return List.of(
                Position.class,
                MoteAttributes.class,
                MspClock.class,
                MspMoteID.class,
                MspButton.class,
                MspDefaultSerial.class,
                MspExp430Fr5969LED.class,
                MspDebugOutput.class);
    }

    @Override
    public List<Class<? extends MoteInterface>> getAllMoteInterfaceClasses() {
        return List.of(
                Position.class,
                Mote2MoteRelations.class,
                MoteAttributes.class,
                MspClock.class,
                MspMoteID.class,
                MspButton.class,
                MspDefaultSerial.class,
                MspExp430Fr5969LED.class,
                MspDebugOutput.class);
    }
}
