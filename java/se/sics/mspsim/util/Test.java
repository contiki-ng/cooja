/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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
 * Utils
 *
 * Author  : Joakim Eriksson, Niclas Finne
 * Created : Sun Oct 21 22:00:00 2007
 * Updated : $Date$
 *           $Revision$
 */
package se.sics.mspsim.util;
import java.io.IOException;

import se.sics.mspsim.core.IOUnit;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.USART;
import se.sics.mspsim.core.USARTListener;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.platform.sky.CC2420Node;

/**
 * Test - tests a firmware file and exits when reporting "FAIL:" first
 * on a line...
 */
public class Test implements USARTListener {

  private final StringBuilder lineBuffer = new StringBuilder();
  private final MSP430 cpu;

  private Test(MSP430 cpu) {
    this.cpu = cpu;
    IOUnit usart = cpu.getIOUnit("USART 1");
    if (usart instanceof USART) {
      ((USART) usart).addUSARTListener(this);
    }
  }

  @Override
  public void dataReceived(USARTSource source, int data) {
    if (data == '\n') {
      String line = lineBuffer.toString();
      lineBuffer.setLength(0);
      System.out.println("#|" + line);
      if (line.startsWith("FAIL:")) {
        System.err.println("Tests failed!");
        System.exit(1);
      } else if (line.startsWith("EXIT")) {
        System.out.println("Tests succeded!");
        System.exit(0);
      } else if (line.startsWith("DEBUG")) {
        cpu.setDebug(true);
      } else if (line.startsWith("PROFILE")) {
        cpu.getProfiler().printProfile(System.out);
      } else if (line.startsWith("CLEARPROFILE")) {
        cpu.getProfiler().clearProfile();
      }
    } else {
      lineBuffer.append((char) data);
    }
  }

  public static void main(String[] args) {
    int index = 0;
    boolean debug = false;
    if (args[index].startsWith("-")) {
      // Flag
      if ("-debug".equalsIgnoreCase(args[index])) {
        debug = true;
      } else {
        System.err.println("Unknown flag: " + args[index]);
        System.exit(1);
      }
      index++;
    }
    try {
      var cpu = CC2420Node.makeCPU(CC2420Node.makeChipConfig(), args[index]);
      cpu.setDebug(debug);
      cpu.reset();

      // Create the "tester"
      new Test(cpu);
      try {
        cpu.cpuloop();
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }

  }
}
