/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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
 * $Id: $
 *
 * -----------------------------------------------------------------
 *
 * Watchdog
 *
 * Author  : Joakim Eriksson
 * Created : 22 apr 2008
 * Updated : $Date:$
 *           $Revision:$
 */
package se.sics.mspsim.core;

import se.sics.mspsim.core.EmulationLogger.WarningType;
import se.sics.mspsim.util.Utils;

/**
 * @author joakim
 *
 */
public class Watchdog extends IOUnit implements SFRModule {

  private static final int WDTHOLD = 0x80;
  private static final int WDTCNTCL = 0x08;
  private static final int WDTMSEL = 0x10;

  private static final int WATCHDOG_VECTOR = 10;
  private static final int WATCHDOG_INTERRUPT_BIT = 0;
  private static final int WATCHDOG_INTERRUPT_VALUE = 1 << WATCHDOG_INTERRUPT_BIT;

  // Nominal internal very-low-power oscillator frequency. The CS modules in
  // current devices document VLO at 9-10 kHz typ.; 10 kHz is used here so
  // WDT timeouts driven by VLOCLK have plausible duration.
  private static final int VLOCLK_FREQ_HZ = 10000;

  private final int[] delayTable;
  private final int wdtISxMask;
  private final int wdtSSELMask;
  private final int wdtSSEL_ACLK;
  private final int wdtSSEL_VLOCLK;

  private final int resetVector;

  private int wdtctl = 0x4;

  public boolean wdtOn = true;

  // The current "delay" when started/cleared (or hold).
  private int delay;
  // The target time for this timer
  private long targetTime;

  private enum ClockSource { SMCLK, ACLK, VLOCLK }
  private ClockSource clockSource = ClockSource.SMCLK;

  // Timer or WDT mode
  private boolean timerMode;

  private final TimeEvent wdtTrigger = new TimeEvent(0, "Watchdog") {
    @Override
    public void execute(long t) {
      // Here the WDT triggered!!!
      if (timerMode) {
          SFR sfr = cpu.getSFR();
          sfr.setBitIFG(0, WATCHDOG_INTERRUPT_VALUE);
          scheduleTimer();
          System.out.println("WDT trigger - will set interrupt flag (no reset)");
          cpu.generateTrace(System.out);
      } else {
          System.out.println("WDT trigger - will reset node!");
          cpu.generateTrace(System.out);
          cpu.flagInterrupt(resetVector, Watchdog.this, true);
      }
    }
  };

  public Watchdog(MSP430Core cpu, int address) {
    this(cpu, address, null, 0x03, 0x04, 0x04, -1);
  }

  public Watchdog(MSP430Core cpu, int address, int[] delayTable, int wdtISxMask) {
    this(cpu, address, delayTable, wdtISxMask, 0x04, 0x04, -1);
  }

  public Watchdog(MSP430Core cpu, int address, int[] delayTable, int wdtISxMask,
                  int wdtSSELMask, int wdtSSEL_ACLK, int wdtSSEL_VLOCLK) {
    super("Watchdog", cpu, cpu.memory, address);

    this.delayTable = delayTable != null ? delayTable : new int[] { 32768, 8192, 512, 64 };
    this.wdtISxMask = wdtISxMask;
    this.wdtSSELMask = wdtSSELMask;
    this.wdtSSEL_ACLK = wdtSSEL_ACLK;
    this.wdtSSEL_VLOCLK = wdtSSEL_VLOCLK;

    resetVector = cpu.MAX_INTERRUPT;

    cpu.getSFR().registerSFDModule(0, WATCHDOG_INTERRUPT_BIT, this, WATCHDOG_VECTOR);
  }

  @Override
  public void interruptServiced(int vector) {
    cpu.flagInterrupt(vector, this, false);
  }

  @Override
  public void reset(int type) {
      super.reset(type);
      wdtctl = 0x4;
  }

  @Override
  public int read(int address, boolean word, long cycles) {
          return wdtctl | 0x6900;
  }

  @Override
  public void write(int address, int value, boolean word, long cycles) {
    if (address == offset) {
      if ((value >> 8) == 0x5a) {
        wdtctl = value & 0xff;
        if (DEBUG) log("Wrote to WDTCTL: " + Utils.hex8(wdtctl) + " from $" + Utils.hex(cpu.getPC(), 4));

        // Is it on?
        wdtOn = (value & 0x80) == 0;
        int ssel = value & wdtSSELMask;
        if (ssel == wdtSSEL_ACLK) {
          clockSource = ClockSource.ACLK;
        } else if (wdtSSEL_VLOCLK >= 0 && ssel == wdtSSEL_VLOCLK) {
          clockSource = ClockSource.VLOCLK;
        } else {
          // SMCLK selected, or an unsupported source (e.g. X_CLK) -> fall back
          // to the cycle-counter path so behaviour stays defined.
          clockSource = ClockSource.SMCLK;
        }
        if ((value & WDTCNTCL) != 0) {
          // Clear timer => reset the delay
          delay = delayTable[value & wdtISxMask];
        }
        timerMode = (value & WDTMSEL) != 0;
        // Start it if it should be started!
        if (wdtOn) {
          if (DEBUG) log("Setting WDTCNT to count: " + delay);
          scheduleTimer();
        } else {
          // Stop it and remember current "delay" left!
          wdtTrigger.remove();
        }
      } else {
        // Trigger reset!!
        logw(WarningType.EXECUTION, "illegal write to WDTCTL (" + value + ") from $" + Utils.hex(cpu.getPC(), 4)
            + " - reset!!!!");
        cpu.flagInterrupt(resetVector, this, true);
      }
    }
  }

  private void scheduleTimer() {
      switch (clockSource) {
      case ACLK -> {
          if (DEBUG) log("setting delay in ms (ACLK): " + 1000.0 * delay / cpu.aclkFrq);
          targetTime = cpu.scheduleTimeEventMillis(wdtTrigger, 1000.0 * delay / cpu.aclkFrq);
      }
      case VLOCLK -> {
          if (DEBUG) log("setting delay in ms (VLOCLK): " + 1000.0 * delay / VLOCLK_FREQ_HZ);
          targetTime = cpu.scheduleTimeEventMillis(wdtTrigger, 1000.0 * delay / VLOCLK_FREQ_HZ);
      }
      case SMCLK -> {
          if (DEBUG) log("setting delay in cycles");
          targetTime = cpu.cycles + delay;
          cpu.scheduleCycleEvent(wdtTrigger, targetTime);
      }
      }
  }

  @Override
  public void enableChanged(int reg, int bit, boolean enabled) {
      if (DEBUG) log("*** Watchdog module enabled: " + enabled);
  }
}
