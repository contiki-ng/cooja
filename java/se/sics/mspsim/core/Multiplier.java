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
 * Multiplier
 *
 * Author  : Joakim Eriksson
 * Created : Sun Oct 21 22:00:00 2007
 * Updated : $Date$
 *           $Revision$
 */

package se.sics.mspsim.core;
import se.sics.mspsim.core.EmulationLogger.WarningType;
import se.sics.mspsim.util.Utils;

public class Multiplier extends IOUnit {

  public static final int MPY = 0x130;
  public static final int MPYS = 0x132;
  public static final int MAC = 0x134;
  public static final int MACS = 0x136;
  public static final int OP2 = 0x138;
  public static final int RESLO = 0x13a;
  public static final int RESHI = 0x13c;
  public static final int SUMEXT = 0x13e;

  private int mpy;
  private int mpys;
  private int op2;

  private int resLo;
  private int resHi;
  private int mac;
  private int macs;
  private int sumext;

  private int op1;

  private boolean signed;
  private boolean accumulating;
  /**
   * Creates a new <code>Multiplier</code> instance.
   *
   */
  public Multiplier(MSP430Core cpu, int[] memory, int offset) {
    super("Multiplier", "Hardware Multiplier", cpu, memory, offset);
  }

  @Override
  public int read(int address, boolean word, long cycles) {
    return switch (address) {
      case MPY -> mpy;
      case MPYS -> mpys;
      case MAC -> mac;
      case MACS -> macs;
      case OP2 -> op2;
      case RESHI -> {
        if (DEBUG) log("read res hi: " + resHi);
        yield resHi;
      }
      case RESLO -> {
        if (DEBUG) log("read res lo: " + resLo);
        yield resLo;
      }
      case SUMEXT -> {
        if (DEBUG) log("read sumext: " + sumext);
        yield sumext;
      }
      default -> {
        logw(WarningType.EMULATION_ERROR, "read unhandled address: 0x" + Utils.hex(address, 4));
        yield 0;
      }
    };
  }

  @Override
  public void write(int address, int data, boolean word, long cycles) {
    if (DEBUG) {
      log("write to: $" + Utils.hex(address, 4) + " data = " + data + " word = " + word);
    }
    switch (address) {
      case MPY -> {
        op1 = mpy = data;
        if (DEBUG) log("Write to MPY: " + data);
        signed = false;
        accumulating = false;
      }
      case MPYS -> {
        op1 = mpys = data;
        if (DEBUG) log("Write to MPYS: " + data);
        signed = true;
        accumulating = false;
      }
      case MAC -> {
        op1 = mac = data;
        if (DEBUG) log("Write to MAC: " + data);
        signed = false;
        accumulating = true;
      }
      case MACS -> {
        op1 = macs = data;
        if (DEBUG) log("Write to MACS: " + data);
        signed = true;
        accumulating = true;
      }
      case RESLO -> resLo = data;
      case RESHI -> resHi = data;
      case OP2 -> {
        if (DEBUG) log("Write to OP2: " + data);
        sumext = 0;
        op2 = data;
        // Expand to word
        if (signed) {
          if (!word) {
            if (op1 > 0x80) op1 = op1 | 0xff00;
            if (op2 > 0x80) op2 = op2 | 0xff00;
          }
          op1 = op1 > 0x8000 ? op1 - 0x10000 : op1;
          op2 = op2 > 0x8000 ? op2 - 0x10000 : op2;
        }
        long res = (long) op1 * (long) op2;
        if (DEBUG) log("O1:" + op1 + " * " + op2 + " = " + res);
        if (signed) {
          sumext = res < 0 ? 0xffff : 0;
        }
        if (accumulating) {
          res += ((long) resHi << 16) + resLo;
          if (!signed) {
            sumext = res > 0xffffffffL ? 1 : 0;
          }
        } else if (!signed) {
          sumext = 0;
        }
        resHi = (int) ((res >> 16) & 0xffff);
        resLo = (int) (res & 0xffff);
        if (DEBUG) log(" ===> result = " + res);
      }
    }
  }

  @Override
  public void interruptServiced(int vector) {
  }
}
