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

package se.sics.mspsim.core;

import se.sics.mspsim.util.Utils;

/**
 * Clock System (CS) module for MSP430FR5969.
 *
 * Register layout (at offset 0x0160):
 *   CSCTL0 (0x0160): Password register - must write CSKEY (0xA500) to unlock
 *   CSCTL1 (0x0162): DCO frequency selection (DCOFSEL, DCORSEL)
 *   CSCTL2 (0x0164): Clock source selection (SELM, SELS, SELA)
 *   CSCTL3 (0x0166): Clock dividers (DIVM, DIVS, DIVA)
 *   CSCTL4 (0x0168): LFXT control
 *   CSCTL5 (0x016A): HFXT control
 *   CSCTL6 (0x016C): Fault flags
 */
public class FR5969ClockSystem extends ClockSystem {

    // Register addresses
    private static final int CSCTL0 = 0x0160;
    private static final int CSCTL1 = 0x0162;
    private static final int CSCTL2 = 0x0164;
    private static final int CSCTL3 = 0x0166;
    private static final int CSCTL4 = 0x0168;
    private static final int CSCTL5 = 0x016A;
    private static final int CSCTL6 = 0x016C;

    // CSCTL0: Password
    private static final int CSKEY = 0xA500;
    private static final int CSKEY_H = 0xA5;

    // CSCTL1: DCO control
    private static final int DCOFSEL_MASK = 0x000E;  // Bits 1-3
    private static final int DCOFSEL_SHIFT = 1;
    private static final int DCORSEL = 0x0040;       // Bit 6: DCO range select

    // CSCTL2: Clock source selection
    private static final int SELM_MASK = 0x0007;     // Bits 0-2: MCLK source
    private static final int SELS_MASK = 0x0070;     // Bits 4-6: SMCLK source
    private static final int SELS_SHIFT = 4;
    private static final int SELA_MASK = 0x0700;     // Bits 8-10: ACLK source
    private static final int SELA_SHIFT = 8;

    // Clock source values
    private static final int SEL_LFXTCLK = 0;
    private static final int SEL_VLOCLK = 1;
    private static final int SEL_LFMODCLK = 2;
    private static final int SEL_DCOCLK = 3;
    private static final int SEL_MODCLK = 4;
    private static final int SEL_HFXTCLK = 5;

    // CSCTL3: Clock dividers
    private static final int DIVM_MASK = 0x0007;     // Bits 0-2: MCLK divider
    private static final int DIVS_MASK = 0x0070;     // Bits 4-6: SMCLK divider
    private static final int DIVS_SHIFT = 4;
    private static final int DIVA_MASK = 0x0700;     // Bits 8-10: ACLK divider
    private static final int DIVA_SHIFT = 8;

    // CSCTL4: LFXT control
    private static final int LFXTOFF = 0x0001;       // LFXT off
    private static final int SMCLKOFF = 0x0002;      // SMCLK off
    private static final int VLOOFF = 0x0008;        // VLO off
    private static final int LFXTBYPASS = 0x0010;    // LFXT bypass
    private static final int LFXTDRIVE_MASK = 0x00C0;

    // CSCTL5: HFXT control
    private static final int HFXTOFF = 0x0001;       // HFXT off
    private static final int HFXTBYPASS = 0x0010;    // HFXT bypass
    private static final int HFXTDRIVE_MASK = 0x00C0;
    private static final int HFFREQ_MASK = 0x0C00;

    // CSCTL6: Fault flags
    private static final int LFXTOFFG = 0x0001;      // LFXT fault flag
    private static final int HFXTOFFG = 0x0002;      // HFXT fault flag

    // DCO frequency tables per SLAS704G Table 5-15 (FR5969 datasheet).
    // Indexed by DCOFSEL.
    private static final int[] DCO_FREQ_LOW = {
        1000000,    // DCOFSEL=0: 1 MHz
        2670000,    // DCOFSEL=1: 2.67 MHz
        3500000,    // DCOFSEL=2: 3.5 MHz
        4000000,    // DCOFSEL=3: 4 MHz
        5330000,    // DCOFSEL=4: 5.33 MHz
        7000000,    // DCOFSEL=5: 7 MHz
        8000000,    // DCOFSEL=6: 8 MHz
        8000000     // DCOFSEL=7: reserved, use 8 MHz
    };

    private static final int[] DCO_FREQ_HIGH = {
        1000000,    // DCOFSEL=0: 1 MHz (same as low range)
        5330000,    // DCOFSEL=1: 5.33 MHz
        7000000,    // DCOFSEL=2: 7 MHz
        8000000,    // DCOFSEL=3: 8 MHz
        16000000,   // DCOFSEL=4: 16 MHz
        21000000,   // DCOFSEL=5: 21 MHz
        24000000,   // DCOFSEL=6: 24 MHz
        24000000    // DCOFSEL=7: reserved, use 24 MHz
    };

    // Fixed clock frequencies
    private static final int VLOCLK_FREQ = 10000;       // ~10 kHz
    private static final int LFXTCLK_FREQ = 32768;      // 32.768 kHz crystal
    private static final int MODCLK_FREQ = 5000000;     // ~5 MHz
    private static final int LFMODCLK_FREQ = 39062;     // MODCLK/128

    private static final int MAX_DCO_FREQ = 24000000;

    private final Timer[] timers;

    // Current state
    private boolean unlocked;
    private int currentDcoFrequency;
    private int currentSmclkFrequency;
    private int currentAclkFrequency;

    public FR5969ClockSystem(MSP430Core cpu, int[] memory, int offset, Timer[] timers) {
        super("FR5969ClockSystem", cpu, memory, offset);
        this.timers = timers;
    }

    @Override
    public int getMaxDCOFrequency() {
        return MAX_DCO_FREQ;
    }

    @Override
    public int getAddressRangeMin() {
        return CSCTL0;
    }

    @Override
    public int getAddressRangeMax() {
        return CSCTL6;
    }

    @Override
    public void reset(int type) {
        // Reset values per SLAU367P §3.3 (FR58xx/FR59xx Family User's Guide).
        // After POR: DCO = 8 MHz, MCLK = SMCLK = DCO/8 = 1 MHz, ACLK = LFXT (VLO when LFXTOFF).
        unlocked = false;

        // CSCTL0: Locked (status reads 0x9600)
        memory[CSCTL0] = 0x00;
        memory[CSCTL0 + 1] = 0x96;

        // CSCTL1: DCORSEL=0, DCOFSEL=6 -> 8 MHz
        memory[CSCTL1] = 0x0C;
        memory[CSCTL1 + 1] = 0x00;

        // CSCTL2: SELA=0 (LFXT), SELS=3 (DCO), SELM=3 (DCO)
        memory[CSCTL2] = 0x33;
        memory[CSCTL2 + 1] = 0x00;

        // CSCTL3: DIVM=3 (/8), DIVS=3 (/8), DIVA=0 (/1) -> 1 MHz MCLK/SMCLK after reset
        memory[CSCTL3] = 0x33;
        memory[CSCTL3 + 1] = 0x00;

        // CSCTL4: LFXTOFF=1, HFXTOFF=1, default drive bits
        memory[CSCTL4] = 0xC9;
        memory[CSCTL4 + 1] = 0xCD;

        // CSCTL5: ENSTFCNT1/2=1, default fault flags
        memory[CSCTL5] = 0xC5;
        memory[CSCTL5 + 1] = 0x00;

        // CSCTL6: clear (matches cooja-ng)
        memory[CSCTL6] = 0x00;
        memory[CSCTL6 + 1] = 0x00;

        // Apply initial configuration
        setConfiguration(cpu.cycles);
    }

    @Override
    public int read(int address, boolean word, long cycles) {
        int val = memory[address];
        if (word) {
            val |= memory[(address + 1) & 0xffff] << 8;
        }

        // CSCTL0 reads as 0x96xx when locked, 0xA5xx when unlocked
        if (address == CSCTL0 && word) {
            val = (val & 0x00FF) | (unlocked ? 0xA500 : 0x9600);
        } else if (address == CSCTL0 + 1 && !word) {
            val = unlocked ? 0xA5 : 0x96;
        }

        return val;
    }

    @Override
    public void write(int address, int data, boolean word, long cycles) {
        if (DEBUG) {
            log("Write to FR5969ClockSystem: " + Utils.hex16(address) + " => " + Utils.hex16(data));
        }

        // Handle CSCTL0 (password register)
        if (address == CSCTL0) {
            if (word) {
                // Writing CSKEY unlocks, any other value locks
                unlocked = (data == CSKEY);
                if (DEBUG) {
                    log("CS registers " + (unlocked ? "UNLOCKED" : "LOCKED"));
                }
            }
            // Don't store the password in memory
            return;
        }

        if (address == CSCTL0 + 1) {
            // Writing high byte only
            unlocked = ((data & 0xFF) == CSKEY_H);
            if (DEBUG) {
                log("CS registers " + (unlocked ? "UNLOCKED" : "LOCKED") + " (high byte write)");
            }
            return;
        }

        // All other registers require unlock
        if (!unlocked) {
            if (DEBUG) {
                log("CS write ignored - registers locked");
            }
            return;
        }

        // Store value in memory
        memory[address] = data & 0xff;
        if (word) {
            memory[address + 1] = (data >> 8) & 0xff;
        }

        // Reconfigure clocks
        setConfiguration(cycles);
    }

    @Override
    public void interruptServiced(int vector) {
        // CS module doesn't generate interrupts
    }

    private void setConfiguration(long cycles) {
        // Read CSCTL1 for DCO frequency selection
        int csctl1 = readWord(CSCTL1);
        int dcofsel = (csctl1 & DCOFSEL_MASK) >> DCOFSEL_SHIFT;
        boolean dcorsel = (csctl1 & DCORSEL) != 0;

        // Get DCO frequency from table
        int dcoFreq = dcorsel ? DCO_FREQ_HIGH[dcofsel] : DCO_FREQ_LOW[dcofsel];

        // Read CSCTL2 for clock source selection
        int csctl2 = readWord(CSCTL2);
        int selm = csctl2 & SELM_MASK;
        int sels = (csctl2 & SELS_MASK) >> SELS_SHIFT;
        int sela = (csctl2 & SELA_MASK) >> SELA_SHIFT;

        // Read CSCTL3 for clock dividers
        int csctl3 = readWord(CSCTL3);
        int divm = 1 << (csctl3 & DIVM_MASK);
        int divs = 1 << ((csctl3 & DIVS_MASK) >> DIVS_SHIFT);
        int diva = 1 << ((csctl3 & DIVA_MASK) >> DIVA_SHIFT);

        // Calculate MCLK frequency
        int mclkSource = getSourceFrequency(selm, dcoFreq);
        int mclkFreq = mclkSource / divm;

        // Calculate SMCLK frequency
        int smclkSource = getSourceFrequency(sels, dcoFreq);
        int smclkFreq = smclkSource / divs;

        // Check if SMCLK is disabled
        int csctl4 = readWord(CSCTL4);
        if ((csctl4 & SMCLKOFF) != 0) {
            smclkFreq = 0;
        }

        // Calculate ACLK frequency
        int aclkSource = getSourceFrequency(sela, dcoFreq);
        int aclkFreq = aclkSource / diva;

        if (DEBUG) {
            log("Clock configuration: DCO=" + dcoFreq + "Hz, MCLK=" + mclkFreq +
                "Hz, SMCLK=" + smclkFreq + "Hz, ACLK=" + aclkFreq + "Hz");
        }

        // Update CPU with new frequencies if changed
        if (dcoFreq != currentDcoFrequency || smclkFreq != currentSmclkFrequency ||
            aclkFreq != currentAclkFrequency) {
            currentDcoFrequency = dcoFreq;
            currentSmclkFrequency = smclkFreq;
            currentAclkFrequency = aclkFreq;

            // DCO frequency is used for MCLK (CPU clock)
            // SMCLK is used for peripherals
            cpu.setDCOFrq(mclkFreq, smclkFreq);

            // ACLK is used for timers and low-power peripherals
            cpu.setACLKFrq(aclkFreq);

            // Reset timer counters when clock changes
            if (timers != null) {
                for (Timer timer : timers) {
                    timer.resetCounter(cycles);
                }
            }
        }
    }

    private int getSourceFrequency(int selector, int dcoFreq) {
        return switch (selector) {
            case SEL_LFXTCLK -> LFXTCLK_FREQ;
            case SEL_VLOCLK -> VLOCLK_FREQ;
            case SEL_LFMODCLK -> LFMODCLK_FREQ;
            case SEL_DCOCLK -> dcoFreq;
            case SEL_MODCLK -> MODCLK_FREQ;
            case SEL_HFXTCLK -> LFXTCLK_FREQ;  // Default to LFXT if HFXT not configured
            default -> dcoFreq;
        };
    }

    private int readWord(int address) {
        return memory[address] | (memory[address + 1] << 8);
    }
}
