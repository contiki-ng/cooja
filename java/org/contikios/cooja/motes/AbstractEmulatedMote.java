/*
 * Copyright (c) 2009, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja.motes;

import org.contikios.cooja.Breakpoint;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.plugins.BufferListener;
import org.contikios.cooja.plugins.TimeLine;
import se.sics.mspsim.core.Chip;

public abstract class AbstractEmulatedMote<T extends MoteType, C extends Chip, M extends MemoryInterface> extends AbstractWakeupMote<T, M> {
  protected final C myCpu;
  protected long lastBreakpointCycles = -1;

  protected AbstractEmulatedMote(T moteType, C cpu, M moteMemory, Simulation sim) throws MoteType.MoteTypeCreationException {
    super(moteType, moteMemory, sim);
    myCpu = cpu;
    moteInterfaces.init(this);
  }

  /**
   * Returns the CPU.
   *
   * @return CPU
   */
  public C getCPU() {
    return myCpu;
  }

  /**
   * @return CPU frequency (Hz)
   */
  public int getCPUFrequency() {
    return -1;
  }
  
  /**
   * @return Execution details, for instance a stack trace
   * @see TimeLine
   */
  public String getExecutionDetails() {
    return null;
  }

  /**
   * @return One-liner describing current PC, for instance source file and line.
   * May return null.
   * 
   * @see BufferListener
   */
  public String getPCString() {
    return null;
  }

  public String getStackTrace() {
    return null;
  }

  public abstract long getCPUCycles();

  /**
   * Stop execution immediately.
   * May for example be called by a breakpoint handler.
   */
  public abstract void stopNextInstruction();

  public void signalBreakpointTrigger(Breakpoint b) {
    var cycles = getCPUCycles();
    if (lastBreakpointCycles == cycles) {
      return;
    }
    lastBreakpointCycles = cycles;
    if (b.stopsSimulation() && getSimulation().isRunning()) {
      stopNextInstruction();
    }
    for (var listener : getWatchpointListeners()) {
      listener.watchpointTriggered(b);
    }
  }
}
