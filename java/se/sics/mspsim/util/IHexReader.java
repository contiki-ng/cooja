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
 * IHexReader
 *
 * Author  : Joakim Eriksson
 * Created : Sun Oct 21 22:00:00 2007
 * Updated : $Date$
 *           $Revision$
 */

package se.sics.mspsim.util;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.platform.sky.CC2420Node;

public class IHexReader {
  private static final Logger logger = LoggerFactory.getLogger(IHexReader.class);
  /**
   * Utility class, should not be constructed.
   */
  private IHexReader() {}

  public static int[] readFile(String file, int memSize) {
    var memory = new int[memSize];
    for (int i = 0, n = memory.length; i < n; i++) {
      memory[i] = -1;
    }
    try (var bInput = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
      String line;
      boolean terminate = false;
      while ((line = bInput.readLine()) != null && !terminate) {
        if (line.charAt(0) != ':') {
          logger.error("{} not an IHex file? Line starting with '{}'", file, line.charAt(0));
          return null;
        }
        int size = hexToInt(line.charAt(1)) * 0x10 + hexToInt(line.charAt(2));
        int adr =  hexToInt(line.charAt(3)) * 0x1000 +
          hexToInt(line.charAt(4)) * 0x100 + hexToInt(line.charAt(5)) * 0x10 +
          hexToInt(line.charAt(6));
        int type = hexToInt(line.charAt(7)) * 0x10 + hexToInt(line.charAt(8));

        // Termination !!!
        if (type == 0x01) {
          terminate = true;
        } else {
          int index = 9;
          for (int i = 0; i < size; i++) {
            memory[adr + i] = (hexToInt(line.charAt(index++)) * 0x10 +
                                  hexToInt(line.charAt(index++)));
          }
        }
      }
    } catch (IOException ioe) {
      logger.error("IO exception when reading {}", file, ioe);
      return null;
    }
    return memory;
  }

  private static String hex(int data) {
    return Integer.toString(data, 16);
  }

  private static int hexToInt(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    } else {
      return c - 'A' + 10;
    }
  }

  public static void main(String[] args) throws IOException {
    int data = 0x84;
    System.out.println("RRA: " + hex((data & 0x80) + (data >> 1)));

    MSP430 cpu = CC2420Node.makeCPU(CC2420Node.makeChipConfig(), args[0]);
    cpu.reset();
    cpu.cpuloop();
  }


}
