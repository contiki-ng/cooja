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

import se.sics.mspsim.core.EmulationLogger.WarningType;

public class FRAMController extends IOUnit {

    // FRAM controller size
    public static final int SIZE = 16;

    // Register offsets (relative to base address 0x140)
    private static final int FRCTL0 = 0x00;   // FRAM Control 0
    private static final int GCCTL0 = 0x04;   // General Control 0
    private static final int GCCTL1 = 0x06;   // General Control 1

    // FRCTL0 bits
    private static final int FRCTLPW = 0xA500;    // Password for write access
    private static final int FRCTLPW_MASK = 0xFF00;
    private static final int NWAITS_MASK = 0x0070; // Wait state configuration
    private static final int NWAITS_SHIFT = 4;

    // GCCTL0 bits
    private static final int UBDRSTEN = 0x0080;   // Uncorrectable bit detect reset enable
    private static final int UBDIE = 0x0040;      // Uncorrectable bit detect interrupt enable
    private static final int CBDIE = 0x0020;      // Correctable bit detect interrupt enable
    private static final int FRPWR = 0x0004;      // FRAM power control
    private static final int FRLPMPWR = 0x0002;   // FRAM LPM power control

    // GCCTL1 bits
    private static final int CBDIFG = 0x0002;     // Correctable bit detect interrupt flag
    private static final int UBDIFG = 0x0001;     // Uncorrectable bit detect interrupt flag

    // Register values
    private int frctl0;
    private int gcctl0;
    private int gcctl1;

    // Energy tracking listener
    private FRAMEnergyListener energyListener;

    // Statistics
    private long totalReads;
    private long totalWrites;
    private long totalBytesRead;
    private long totalBytesWritten;

    public FRAMController(MSP430Core cpu, int[] memory, int offset) {
        super("FRAM", "FRAM Controller", cpu, memory, offset);
        reset(MSP430.RESET_POR);
    }

    @Override
    public void reset(int type) {
        // Default values after reset
        frctl0 = 0x9600;  // Password + default wait states
        gcctl0 = FRPWR;   // FRAM powered on
        gcctl1 = 0x0000;  // No flags set

        // Reset statistics
        totalReads = 0;
        totalWrites = 0;
        totalBytesRead = 0;
        totalBytesWritten = 0;
    }

    @Override
    public void write(int address, int value, boolean word, long cycles) {
        int reg = address - offset;

        switch (reg) {
            case FRCTL0:
            case FRCTL0 + 1:
                if (word) {
                    // Check password
                    if ((value & FRCTLPW_MASK) == FRCTLPW) {
                        frctl0 = value;
                        if (DEBUG) {
                            log("FRCTL0 written: 0x" + Integer.toHexString(value) +
                                " (wait states: " + ((value & NWAITS_MASK) >> NWAITS_SHIFT) + ")");
                        }
                    } else {
                        // Password violation
                        logw(WarningType.EMULATION_ERROR,
                             "FRAM password violation on FRCTL0 write");
                    }
                }
                break;

            case GCCTL0:
            case GCCTL0 + 1:
                if (word) {
                    gcctl0 = value & 0x00FF;  // Only lower byte writable
                    if (DEBUG) {
                        log("GCCTL0 written: 0x" + Integer.toHexString(gcctl0));
                    }
                } else {
                    if (reg == GCCTL0) {
                        gcctl0 = (gcctl0 & 0xFF00) | (value & 0x00FF);
                    }
                }
                break;

            case GCCTL1:
            case GCCTL1 + 1:
                if (word) {
                    // Writing 1 clears interrupt flags
                    gcctl1 &= ~(value & (CBDIFG | UBDIFG));
                    if (DEBUG) {
                        log("GCCTL1 written: 0x" + Integer.toHexString(value) +
                            " (flags cleared)");
                    }
                } else {
                    if (reg == GCCTL1) {
                        gcctl1 &= ~(value & (CBDIFG | UBDIFG));
                    }
                }
                break;

            default:
                logw(WarningType.EMULATION_ERROR,
                     "FRAM: write to unimplemented register 0x" + Integer.toHexString(reg));
                break;
        }
    }

    @Override
    public int read(int address, boolean word, long cycles) {
        int reg = address - offset;

        switch (reg) {
            case FRCTL0:
            case FRCTL0 + 1:
                if (word) {
                    // Return with read password
                    return 0x9600 | (frctl0 & 0x00FF);
                }
                return (reg == FRCTL0) ? (frctl0 & 0xFF) : ((frctl0 >> 8) & 0xFF);

            case GCCTL0:
            case GCCTL0 + 1:
                if (word) {
                    return gcctl0;
                }
                return (reg == GCCTL0) ? (gcctl0 & 0xFF) : ((gcctl0 >> 8) & 0xFF);

            case GCCTL1:
            case GCCTL1 + 1:
                if (word) {
                    return gcctl1;
                }
                return (reg == GCCTL1) ? (gcctl1 & 0xFF) : ((gcctl1 >> 8) & 0xFF);

            default:
                logw(WarningType.EMULATION_ERROR,
                     "FRAM: read from unimplemented register 0x" + Integer.toHexString(reg));
                return 0;
        }
    }

    @Override
    public void interruptServiced(int vector) {
        // Clear appropriate interrupt flag when interrupt is serviced
    }

    /**
     * Set the energy listener for FRAM access tracking.
     */
    public void setEnergyListener(FRAMEnergyListener listener) {
        this.energyListener = listener;
    }

    /**
     * Called by FRAMSegment to notify of a write operation.
     * Used for energy tracking.
     */
    public void notifyWrite(int address, int bytes) {
        totalWrites++;
        totalBytesWritten += bytes;

        if (energyListener != null) {
            energyListener.onFRAMWrite(address, bytes);
        }
    }

    /**
     * Called by FRAMSegment to notify of a read operation.
     * Used for energy tracking.
     */
    public void notifyRead(int address, int bytes) {
        totalReads++;
        totalBytesRead += bytes;

        if (energyListener != null) {
            energyListener.onFRAMRead(address, bytes);
        }
    }

    /**
     * Get the configured number of wait states.
     */
    public int getWaitStates() {
        return (frctl0 & NWAITS_MASK) >> NWAITS_SHIFT;
    }

    /**
     * Check if FRAM is powered on.
     */
    public boolean isPowered() {
        return (gcctl0 & FRPWR) != 0;
    }

    /**
     * Check if FRAM controller is blocking the CPU.
     * FRAM never blocks the CPU (unlike Flash which blocks during erase/write).
     */
    public boolean blocksCPU() {
        return false;
    }

    // Statistics getters
    public long getTotalReads() { return totalReads; }
    public long getTotalWrites() { return totalWrites; }
    public long getTotalBytesRead() { return totalBytesRead; }
    public long getTotalBytesWritten() { return totalBytesWritten; }

    @Override
    public String info() {
        return "FRAM Controller\n" +
               "  FRCTL0: 0x" + Integer.toHexString(frctl0) +
               " (wait states: " + getWaitStates() + ")\n" +
               "  GCCTL0: 0x" + Integer.toHexString(gcctl0) +
               " (powered: " + isPowered() + ")\n" +
               "  GCCTL1: 0x" + Integer.toHexString(gcctl1) + "\n" +
               "  Total reads: " + totalReads + " (" + totalBytesRead + " bytes)\n" +
               "  Total writes: " + totalWrites + " (" + totalBytesWritten + " bytes)";
    }

    /**
     * Interface for FRAM energy tracking.
     */
    public interface FRAMEnergyListener {
        void onFRAMWrite(int address, int bytes);
        void onFRAMRead(int address, int bytes);
    }
}
