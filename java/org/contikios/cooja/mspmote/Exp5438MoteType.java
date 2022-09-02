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
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.RimeAddress;
import org.contikios.cooja.mspmote.interfaces.CC1101Radio;
import org.contikios.cooja.mspmote.interfaces.CC1120Radio;
import org.contikios.cooja.mspmote.interfaces.Exp5438LED;
import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.mspmote.interfaces.MspClock;
import org.contikios.cooja.mspmote.interfaces.MspDebugOutput;
import org.contikios.cooja.mspmote.interfaces.MspMoteID;
import org.contikios.cooja.mspmote.interfaces.UsciA1Serial;

@ClassDescription("EXP430F5438 mote")
@AbstractionLevelDescription("Emulated level")
public class Exp5438MoteType extends AbstractMspMoteType {

  @Override
  protected MspMote createMote(Simulation simulation) {
    return new Exp5438Mote(this, simulation);
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
    return "images/exp5438.png";
  }

  @Override
  public Class<? extends MoteInterface>[] getDefaultMoteInterfaceClasses() {
    @SuppressWarnings("unchecked")
    Class<? extends MoteInterface>[] list = createMoteInterfaceList(
            Position.class,
            RimeAddress.class,
            IPAddress.class,
            Mote2MoteRelations.class,
            MoteAttributes.class,
            MspClock.class,
            MspMoteID.class,
            Msp802154Radio.class,
            UsciA1Serial.class,
            Exp5438LED.class,
            /*Exp5438LCD.class,*/ /* TODO */
            MspDebugOutput.class);
    return list;
  }

  @Override
  public Class<? extends MoteInterface>[] getAllMoteInterfaceClasses() {
    @SuppressWarnings("unchecked")
    Class<? extends MoteInterface>[] list = createMoteInterfaceList(
        Position.class,
        RimeAddress.class,
        IPAddress.class,
        Mote2MoteRelations.class,
        MoteAttributes.class,
        MspClock.class,
        MspMoteID.class,
        Msp802154Radio.class,
        CC1101Radio.class,
        CC1120Radio.class,
        UsciA1Serial.class,
        Exp5438LED.class,
        /*Exp5438LCD.class,*/ /* TODO */
        MspDebugOutput.class);
    return list;
  }
}
