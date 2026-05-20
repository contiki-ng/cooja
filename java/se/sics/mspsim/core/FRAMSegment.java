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

public class FRAMSegment implements Memory {

    private final MSP430Core core;
    private final int[] memory;
    private final FRAMController framController;

    public FRAMSegment(MSP430Core core, FRAMController framController) {
        this.core = core;
        this.memory = core.memory;
        this.framController = framController;
    }

    @Override
    public int read(int address, AccessMode mode, AccessType type) throws EmulationException {
        int val = memory[address] & 0xff;
        if (mode != AccessMode.BYTE) {
            val |= (memory[address + 1] & 0xff) << 8;
            if ((address & 1) != 0) {
                core.printWarning(WarningType.MISALIGNED_READ, address);
            }
            if (mode == AccessMode.WORD20) {
                val |= (memory[address + 2] & 0xff) << 16;
                val |= (memory[address + 3] & 0xff) << 24;
                val &= 0xfffff;
            } else {
                val &= 0xffff;
            }
        }

        // Notify controller for energy tracking
        if (framController != null) {
            framController.notifyRead(address, mode.bytes);
        }

        return val;
    }

    @Override
    public void write(int dstAddress, int data, AccessMode mode) throws EmulationException {
        // Honour WPROT: when set the write is blocked and WPIFG is raised.
        // get()/set() bypass this so debugger and ELF-loader writes still
        // work after WPROT has been engaged.
        if (framController != null && !framController.attemptWrite(dstAddress)) {
            return;
        }

        memory[dstAddress] = data & 0xff;
        if (mode != AccessMode.BYTE) {
            memory[dstAddress + 1] = (data >> 8) & 0xff;
            if ((dstAddress & 1) != 0) {
                core.printWarning(WarningType.MISALIGNED_WRITE, dstAddress);
            }
            if (mode == AccessMode.WORD20) {
                memory[dstAddress + 2] = (data >> 16) & 0xff;
                memory[dstAddress + 3] = (data >> 24) & 0xff;
            }
        }

        // Notify controller for energy tracking
        if (framController != null) {
            framController.notifyWrite(dstAddress, mode.bytes);
        }
    }

    @Override
    public int get(int address, AccessMode mode) {
        int val = memory[address] & 0xff;
        if (mode != AccessMode.BYTE) {
            val |= (memory[address + 1] & 0xff) << 8;
            if (mode == AccessMode.WORD20) {
                val |= (memory[address + 2] & 0xff) << 16;
                val |= (memory[address + 3] & 0xff) << 24;
                val &= 0xfffff;
            } else {
                val &= 0xffff;
            }
        }
        return val;
    }

    @Override
    public void set(int address, int data, AccessMode mode) {
        memory[address] = data & 0xff;
        if (mode != AccessMode.BYTE) {
            memory[address + 1] = (data >> 8) & 0xff;
            if (mode == AccessMode.WORD20) {
                memory[address + 2] = (data >> 16) & 0xff;
                memory[address + 3] = (data >> 24) & 0xff;
            }
        }
    }

}
