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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.platform.ti.Exp1101Node;
import se.sics.mspsim.platform.ti.Exp1120Node;
import se.sics.mspsim.platform.ti.Exp5438Node;
import se.sics.mspsim.platform.ti.Trxeb1120Node;
import se.sics.mspsim.platform.ti.Trxeb2520Node;

/**
 * @author Fredrik Osterlind
 */
public class Exp5438Mote extends MspMote {
  private static final Logger logger = LogManager.getLogger(Exp5438Mote.class);

  public final GenericNode exp5438Node;
  private String desc = "";
  
  public Exp5438Mote(MspMoteType moteType, Simulation sim) throws MoteType.MoteTypeCreationException {
    super(moteType, sim);
    final var fileELF = moteType.getContikiFirmwareFile();
    // Hack: Try to figure out what type of MSPSim-node we should be used by checking file extension.
    String filename = fileELF.getName();
    if (filename.endsWith(".exp1101")) {
      exp5438Node = new Exp1101Node();
      desc = "Exp5438+CC1101";
    } else if (filename.endsWith(".exp1120")) {
      exp5438Node = new Exp1120Node();
      desc = "Exp5438+CC1120";
    } else if (filename.endsWith(".trxeb2520")) {
      exp5438Node = new Trxeb2520Node();
      desc = "Trxeb2520";
    } else if (filename.endsWith(".trxeb1120")) {
      exp5438Node = new Trxeb1120Node(false);
      desc = "Trxeb1120";
    } else if (filename.endsWith(".eth1120")) {
      exp5438Node = new Trxeb1120Node(true);
      desc = "Eth1120";
    } else if (filename.endsWith(".exp2420") || filename.endsWith(".exp5438")) {
      exp5438Node = new Exp5438Node();
      desc = "Exp5438+CC2420";
    } else {
      throw new IllegalStateException("unknown file extension, cannot figure out what MSPSim node type to use: " + filename);
    }

    try {
      registry = exp5438Node.getRegistry();
      prepareMote(fileELF, exp5438Node);
    } catch (Exception e) {
      logger.fatal("Error when creating Exp5438 mote: ", e);
      throw new MoteType.MoteTypeCreationException("Error creating Exp5438 mote: " + e.getMessage());
    }
  }

  @Override
  public void idUpdated(int newID) {
  }

  @Override
  public String toString() {
    return desc + " " + getID();
  }

}
