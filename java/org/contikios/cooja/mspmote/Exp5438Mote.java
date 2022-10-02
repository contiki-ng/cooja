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

import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import se.sics.mspsim.platform.GenericNode;

/**
 * @author Fredrik Osterlind
 */
public class Exp5438Mote extends MspMote {
  private static final Logger logger = LogManager.getLogger(Exp5438Mote.class);

  public final GenericNode exp5438Node;
  private final String description;
  
  public Exp5438Mote(MspMoteType moteType, Simulation sim, GenericNode node, String desc)
          throws MoteType.MoteTypeCreationException {
    super(moteType, sim);
    exp5438Node = node;
    description = desc;
    registry = exp5438Node.getRegistry();
    try {
      prepareMote(exp5438Node);
    } catch (Exception e) {
      logger.fatal("Error when creating Exp5438 mote: ", e);
      throw new MoteType.MoteTypeCreationException("Error creating Exp5438 mote: " + e.getMessage());
    }
    myMoteInterfaceHandler = new MoteInterfaceHandler(this, moteType.getMoteInterfaceClasses());
  }

  @Override
  public void idUpdated(int newID) {
  }

  @Override
  public String toString() {
    return description + " " + getID();
  }

}
