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

package se.sics.mspsim.config;

import java.util.ArrayList;
import se.sics.mspsim.core.CRC16;
import se.sics.mspsim.core.ClockSystem;
import se.sics.mspsim.core.FR5969ClockSystem;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.IOUnit;
import se.sics.mspsim.core.MSP430Config;
import se.sics.mspsim.core.MSP430Core;
import se.sics.mspsim.core.Multiplier32;
import se.sics.mspsim.core.PMM;
import se.sics.mspsim.core.SysReg;
import se.sics.mspsim.core.Timer;
import se.sics.mspsim.core.eUSCI_A;
import se.sics.mspsim.util.Utils;

public class MSP430FR5969Config extends MSP430Config {

    // FRAM memory regions (from MSP430FR5969 datasheet)
    public static final int MAIN_FRAM_START = 0x4400;
    public static final int MAIN_FRAM_SIZE = 64 * 1024;  // 64KB main FRAM
    public static final int INFO_FRAM_START = 0x1800;
    // 0x1800-0x19FF: 512 B user info FRAM, 0x1A00-0x1AFF: device descriptor (TLV).
    // Cover both as one non-volatile region so msp430-elf startup code that
    // reads TLV calibration records (DEVICE_ID, ADC offsets, ...) does not hit
    // an unmapped page.
    public static final int INFO_FRAM_SIZE = 0x300;      // 768 B (info + TLV)
    public static final int SRAM_START = 0x1C00;
    public static final int SRAM_SIZE = 2 * 1024;        // 2KB SRAM

    // Port configuration for FR5969
    // FR5969 has P1, P2, P3, P4, and PJ
    private static final String[] portConfig = {
        "P1=200,IN 00,OUT 02,DIR 04,REN 06,SEL0 0A,SEL1 0C,IV_L 0E,IV_H 0F,SELC 16,IES 18,IE 1A,IFG 1C",
        "P2=200,IN 01,OUT 03,DIR 05,REN 07,SEL0 0B,SEL1 0D,IV_L 1E,IV_H 1F,SELC 17,IES 19,IE 1B,IFG 1D",
        "P3=220,IN 00,OUT 02,DIR 04,REN 06,SEL0 0A,SEL1 0C,SELC 16",
        "P4=220,IN 01,OUT 03,DIR 05,REN 07,SEL0 0B,SEL1 0D,SELC 17",
        "PJ=320,IN 00,OUT 02,DIR 04,REN 06,SEL0 0A,SEL1 0C"
    };

    public MSP430FR5969Config() {
        // FR5969 has 64 interrupt vectors (0-63), highest is RESET at 0x007E
        maxInterruptVector = 63;
        MSP430XArch = true;
        hasFRAM = true;
        framControllerOffset = 0x140;
        sfrOffset = 0x100;
        watchdogOffset = 0x15C;

        // FR5xxx WDT_A: WDTISx is bits 0..2 with longer intervals than the
        // 2-bit F1xxx table. See SLAU367P table 11-2.
        wdtDelayTable = new int[] {
            1 << 31, 1 << 27, 1 << 23, 1 << 19,
            1 << 15, 1 << 13, 1 << 9,  1 << 6,
        };
        wdtISxMask = 0x07;
        // FR5xxx WDTSSEL is bits 5..6, encoding SMCLK/ACLK/VLOCLK/X_CLK.
        wdtSSELMask = 0x60;
        wdtSSEL_ACLK = 0x20;
        wdtSSEL_VLOCLK = 0x40;

        // Timer configuration for FR5969 (vector = header offset / 2)
        // Timer_A0 (Timer0_A5): 5 CCR, CCR0=53 (0x006A), CCR1-4=52 (0x0068)
        // Timer_A1 (Timer1_A3): 3 CCR, CCR0=49 (0x0062), CCR1-2=48 (0x0060)
        // Timer_A2 (Timer3_A2): 2 CCR, CCR0=43 (0x0056), CCR1=42 (0x0054)
        // Timer_B0 (Timer0_B3): 7 CCR, CCR0=59 (0x0076), CCR1-6=58 (0x0074)
        TimerConfig timerA0 = new TimerConfig(53, 52, 5, 0x340, Timer.TIMER_Ax149, "Timer_A0", 0x36E);
        TimerConfig timerA1 = new TimerConfig(49, 48, 3, 0x380, Timer.TIMER_Ax149, "Timer_A1", 0x3AE);
        TimerConfig timerA2 = new TimerConfig(43, 42, 2, 0x400, Timer.TIMER_Ax149, "Timer_A2", 0x42E);
        TimerConfig timerB0 = new TimerConfig(59, 58, 7, 0x3C0, Timer.TIMER_Bx149, "Timer_B0", 0x3EE);
        timerConfig = new TimerConfig[] {timerA0, timerA1, timerA2, timerB0};

        // UART configuration for FR5969 (vector = header offset / 2)
        // eUSCI_A0: vector 56 (0x0070), offset 0x5C0
        // eUSCI_A1: vector 51 (0x0066), offset 0x5E0
        // eUSCI_B0: vector 55 (0x006E), offset 0x640
        uartConfig = new UARTConfig[] {
            new UARTConfig("USCI A0", 56, 0x5C0),
            new UARTConfig("USCI A1", 51, 0x5E0),
            new UARTConfig("USCI B0", 55, 0x640)
        };

        // mainFlashConfig() is reused to register the main FRAM region; the
        // MSP430Core branches on hasFRAM to decide segment + controller type.
        infoMemConfig(INFO_FRAM_START, INFO_FRAM_SIZE);
        mainFlashConfig(MAIN_FRAM_START, MAIN_FRAM_SIZE);
        ramConfig(SRAM_START, SRAM_SIZE);
        ioMemSize(0x1000);  // 4KB IO memory space for FR5xxx series
    }

    @Override
    public int setup(MSP430Core cpu, ArrayList<IOUnit> ioUnits) {
        // Setup hardware multiplier (MPY32)
        Multiplier32 mp = new Multiplier32(cpu, cpu.memory, 0x4C0);
        cpu.setIORange(0x4C0, 0x2E, mp);

        // Setup eUSCI units (using eUSCI_A for FR5xxx register layout)
        for (int i = 0, n = uartConfig.length; i < n; i++) {
            eUSCI_A usci = new eUSCI_A(cpu, i, cpu.memory, this);
            cpu.setIORange(uartConfig[i].offset, 0x20, usci);
            ioUnits.add(usci);
        }

        // Setup IO ports
        // P1 has interrupt vector 47 (0x005E), P2 has interrupt vector 44 (0x0058)
        IOPort last = IOPort.parseIOPort(cpu, 47, portConfig[0], null);
        ioUnits.add(last);
        last = IOPort.parseIOPort(cpu, 44, portConfig[1], last);
        ioUnits.add(last);

        // P3, P4, PJ don't have interrupts
        for (int i = 2; i < portConfig.length; i++) {
            last = IOPort.parseIOPort(cpu, 0, portConfig[i], last);
            ioUnits.add(last);
        }

        // Setup system registers
        SysReg sysreg = new SysReg(cpu, cpu.memory);
        cpu.setIORange(SysReg.ADDRESS, SysReg.SIZE, sysreg);
        ioUnits.add(sysreg);

        // Setup power management module
        PMM pmm = new PMM(cpu, cpu.memory, 0x120);
        cpu.setIORange(0x120, PMM.SIZE, pmm);
        ioUnits.add(pmm);

        // Setup CRC16 module (useful for checkpoint validation in intermittent computing)
        CRC16 crc = new CRC16(cpu, CRC16.OFFSET);
        cpu.setIORange(CRC16.OFFSET, CRC16.SIZE, crc);
        ioUnits.add(crc);

        return portConfig.length + uartConfig.length;
    }

    @Override
    public String getAddressAsString(int addr) {
        return Utils.hex20(addr);
    }

    @Override
    public ClockSystem createClockSystem(MSP430Core cpu, int[] memory, Timer[] timers) {
        // FR5969 uses the CS (Clock System) module, not UCS
        return new FR5969ClockSystem(cpu, memory, 0, timers);
    }

    /**
     * Check if an address is in the FRAM region (non-volatile).
     */
    public boolean isFRAM(int address) {
        return (address >= MAIN_FRAM_START && address < MAIN_FRAM_START + MAIN_FRAM_SIZE) ||
               (address >= INFO_FRAM_START && address < INFO_FRAM_START + INFO_FRAM_SIZE);
    }

    /**
     * Check if an address is in the SRAM region (volatile).
     */
    public boolean isSRAM(int address) {
        return address >= SRAM_START && address < SRAM_START + SRAM_SIZE;
    }
}
