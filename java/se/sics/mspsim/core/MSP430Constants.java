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
 * MSP430Constants
 *
 * Author  : Joakim Eriksson
 * Created : Sun Oct 21 22:00:00 2007
 * Updated : $Date$
 *           $Revision$
 */

package se.sics.mspsim.core;

public interface MSP430Constants {

  String VERSION = "0.99";

  int MODE_BYTE = 0;
  int MODE_WORD = 1;
  int MODE_WORD20 = 2;

  int[] MODE_BIT_SIZE = {8, 16, 20};

  int[] MODE_MASK = {0xff, 0xffff, 0xfffff};

  /* memory tags - not used yet*/
  int READ_MONITOR = 0x1000;
  int WRITE_MONITOR = 0x2000;
  int EXEC_MONITOR = 0x4000;
  int MEM_FLASH = 0x100;
  int MEM_IO = 0x200;

  int RESET_PUC = 0;
  int RESET_POR = 1;

  // MODES
  int MODE_ACTIVE = 0;
  int MODE_LPM0 = 1;
  int MODE_LPM1 = 2;
  int MODE_LPM2 = 3;
  int MODE_LPM3 = 4;
  int MODE_LPM4 = 5;
  int MODE_MAX = MODE_LPM4;

  String[] MODE_NAMES = {
    "active", "lpm0", "lpm1", "lpm2", "lpm3", "lpm4"
  };

  int CLK_ACLK = 1;
  int CLK_SMCLK = 2;

  // Instructions (full length)
  int RRC = 0x1000;
  int SWPB = 0x1080;
  int RRA = 0x1100;
  int SXT = 0x1180;
  int PUSH = 0x1200;
  int CALL = 0x1280;
  int RETI = 0x1300;

  // Conditional Jumps [
  int JNE = 0x2000;
  int JEQ = 0x2400;
  int JNC = 0x2800;
  int JC = 0x2C00;

  // Conditional Jumps & jumps...
  int JN = 0x3000;
  int JGE = 0x3400;
  int JL = 0x3800;
  int JMP = 0x3C00;

  // Short ones...
  int MOV = 0x4;
  int ADD = 0x5;
  int ADDC = 0x6;
  int SUBC = 0x7;
  int SUB = 0x8;
  int CMP = 0x9;
  int DADD = 0xa;
  int BIT = 0xb;
  int BIC = 0xc;
  int BIS = 0xd;
  int XOR = 0xe;
  int AND = 0xf;


  // MSP430X instructions
  int MOVA_IND = 0x0000;
  int MOVA_IND_AUTOINC = 0x0010; /* Indirect with increment */
  int MOVA_ABS2REG = 0x0020;
  int MOVA_INDX2REG = 0x0030;
  int MOVA_REG2ABS = 0x0060;
  int MOVA_REG2INDX = 0x0070;
  int MOVA_IMM2REG = 0x0080;
  int CMPA_IMM = 0x0090;
  int ADDA_IMM = 0x00a0;
  int SUBA_IMM = 0x00b0;
  int MOVA_REG = 0x00c0;
  int CMPA_REG = 0x00d0;
  int ADDA_REG = 0x00e0;
  int SUBA_REG = 0x00f0;

  int RRXX_ADDR = 0x0040;
  int RRXX_WORD = 0x0050;

  int RRMASK = 0x0300;
  int RRCM = 0x0000; /* rotate right through carry C -> MSB -> MSB-1 ... -> C */
  int RRAM = 0x0100; /* rotate right arithmetically MSB -> MSB -> MSB-1 ...->C*/
  int RLAM = 0x0200; /* rotate left arithm. C <- MSB-1 ... <- 0 */
  int RRUM = 0x0300; /* rotate right unsigned 0 -> MSB -> MSB -1, ... */


  int CALLA_MASK = 0xfff0;
  int CALLA_REG = 0x1340;
  int CALLA_INDEX = 0x1350;
  int CALLA_IND = 0x1360;
  int CALLA_IND_AUTOINC = 0x1370;
  int CALLA_ABS = 0x1380;
  int CALLA_EDE = 0x1390; /* x(PC) */
  int CALLA_IMM = 0x13b0;

  int PUSHM_A = 0x1400;
  int PUSHM_W = 0x1500;
  int POPM_A = 0x1600;
  int POPM_W = 0x1700;


  int EXTWORD_ZC = 0x100;
  int EXTWORD_REPEAT = 0x80;
  int EXTWORD_AL = 0x40;
  int EXTWORD_SRC = 0x780;
  int EXTWORD_DST = 0x0f;




  String[] TWO_OPS = {
    "-","-","-","-","MOV", "ADD", "ADDC", "SUBC", "SUB",
    "CMP", "DADD", "BIT", "BIC", "BIS", "XOR", "AND"
  };

  String[] REGISTER_NAMES = {
    "PC", "SP", "SR", "CG1", "CG2"
  };

  int PC = 0;
  int SP = 1;
  int SR = 2;
  int CG1 = 2;
  int CG2 = 3;

  int[][] CREG_VALUES = {
    {0, 0, 4, 8}, {0, 1, 2, 0xffff}
  };

  int CARRY_B = 0;
  int ZERO_B = 1;
  int NEGATIVE_B = 2;
  int OVERFLOW_B = 8;
  int GIE_B = 3;

  int CARRY = 1;
  int ZERO = 2;
  int NEGATIVE = 4;
  int OVERFLOW = 1 << OVERFLOW_B;
  int GIE = 1 << GIE_B;

  /* For the LPM management */
  int CPUOFF = 0x0010;
  int OSCOFF = 0x0020;
  int SCG0 = 0x0040;
  int SCG1 = 0x0080;

// #define C                   0x0001
// #define Z                   0x0002
// #define N                   0x0004
// #define V                   0x0100
// #define GIE                 0x0008
// #define CPUOFF              0x0010
// #define OSCOFF              0x0020
// #define SCG0                0x0040
// #define SCG1                0x0080


  int AM_REG = 0;
  int AM_INDEX = 1;
  int AM_IND_REG = 2;
  int AM_IND_AUTOINC = 3;

  int CLKCAPTURE_NONE = 0;
  int CLKCAPTURE_UP = 1;
  int CLKCAPTURE_DWN = 2;
  int CLKCAPTURE_BOTH = 3;


  int DEBUGGING_LEVEL = 0;
}
