/*
 * Copyright (c) 2011, Swedish Institute of Computer Science.
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
import org.contikios.cooja.mspmote.interfaces.CC1101Radio;
import org.contikios.cooja.mspmote.interfaces.CC1120Radio;
import org.contikios.cooja.mspmote.interfaces.Exp5438LED;
import org.contikios.cooja.mspmote.interfaces.Msp802154BitErrorRadio;
import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.mspmote.interfaces.MspClock;
import org.contikios.cooja.mspmote.interfaces.MspDebugOutput;
import org.contikios.cooja.mspmote.interfaces.MspMoteID;
import org.contikios.cooja.mspmote.interfaces.UsciA1Serial;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.platform.ti.Exp1101Node;
import se.sics.mspsim.platform.ti.Exp1120Node;
import se.sics.mspsim.platform.ti.Exp5438Node;
import se.sics.mspsim.platform.ti.Trxeb1120Node;
import se.sics.mspsim.platform.ti.Trxeb2520Node;

@ClassDescription("EXP430F5438 mote")
@AbstractionLevelDescription("Emulated level")
public class Exp5438MoteType extends MspMoteType {

  @Override
  public MspMote generateMote(Simulation simulation) throws MoteTypeCreationException {
    final var fileELF = getContikiFirmwareFile();
    // Hack: Try to figure out what type of MSPSim-node we should be used by checking file extension.
    String filename = fileELF.getName();
    var firmwareFile = fileELF.getAbsolutePath();
    final GenericNode exp5438Node;
    final String desc;
    final MSP430 cpu;
    try {
      if (filename.endsWith(".exp1101")) {
        cpu = Exp1101Node.makeCPU(Exp1101Node.makeChipConfig(), firmwareFile);
        exp5438Node = new Exp1101Node(cpu);
        desc = "Exp5438+CC1101";
      } else if (filename.endsWith(".exp1120")) {
        cpu = Exp1120Node.makeCPU(Exp1120Node.makeChipConfig(), firmwareFile);
        exp5438Node = new Exp1120Node(cpu);
        desc = "Exp5438+CC1120";
      } else if (filename.endsWith(".trxeb2520")) {
        cpu = Trxeb2520Node.makeCPU(Trxeb2520Node.makeChipConfig(), firmwareFile);
        exp5438Node = new Trxeb2520Node(cpu);
        desc = "Trxeb2520";
      } else if (filename.endsWith(".trxeb1120")) {
        cpu = Trxeb1120Node.makeCPU(Trxeb1120Node.makeChipConfig(), firmwareFile);
        exp5438Node = new Trxeb1120Node(false, cpu);
        desc = "Trxeb1120";
      } else if (filename.endsWith(".eth1120")) {
        cpu = Trxeb1120Node.makeCPU(Trxeb1120Node.makeChipConfig(), firmwareFile);
        exp5438Node = new Trxeb1120Node(true, cpu);
        desc = "Eth1120";
      } else if (filename.endsWith(".exp2420") || filename.endsWith(".exp5438")) {
        cpu = Exp5438Node.makeCPU(Exp5438Node.makeChipConfig(), firmwareFile);
        exp5438Node = new Exp5438Node(cpu);
        desc = "Exp5438+CC2420";
      } else {
        throw new IllegalStateException("Unknown file extension, cannot figure out what MSPSim node type to use: " + filename);
      }
    } catch (IOException e) {
      throw new MoteTypeCreationException("Failed to create CPU", e);
    }
    return new Exp5438Mote(this, simulation, exp5438Node, desc);
  }

  @Override
  public String getMoteType() {
    return "exp5438";
  }

  @Override
  public String getMoteName() {
    return "Exp5438";
  }

  @Override
  protected String getMoteImage() {
    return "/images/exp5438.png";
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
            Msp802154Radio.class,
            UsciA1Serial.class,
            Exp5438LED.class,
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
        Msp802154Radio.class,
        Msp802154BitErrorRadio.class,
        CC1101Radio.class,
        CC1120Radio.class,
        UsciA1Serial.class,
        Exp5438LED.class,
        MspDebugOutput.class);
  }
}
