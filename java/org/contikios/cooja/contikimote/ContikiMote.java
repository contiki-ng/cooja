/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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
 */

package org.contikios.cooja.contikimote;

import java.util.ArrayList;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.PolledAfterAllTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.interfaces.PolledBeforeAllTicks;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.motes.AbstractWakeupMote;

/**
 * A Contiki mote executes an actual Contiki system via
 * a loaded shared library and JNI.
 * It contains a section mote memory, a mote interface handler and a
 * Contiki mote type.
 * <p>
 * The mote type is responsible for the connection to the loaded
 * Contiki system.
 * <p>
 * When ticked a Contiki mote polls all interfaces, copies the mote
 * memory to the core, lets the Contiki system handle one event,
 * fetches the updated memory and finally polls all interfaces again.
 *
 * @author      Fredrik Osterlind
 */
public class ContikiMote extends AbstractWakeupMote<ContikiMoteType, SectionMoteMemory> {
  private final ArrayList<PolledBeforeActiveTicks> polledBeforeActive = new ArrayList<>();
  private final ArrayList<PolledAfterActiveTicks> polledAfterActive = new ArrayList<>();
  private final ArrayList<PolledBeforeAllTicks> polledBeforePassive = new ArrayList<>();
  private final ArrayList<PolledAfterAllTicks> polledAfterPassive = new ArrayList<>();

  /**
   * Creates a new mote of given type.
   * Both the initial mote memory and the interface handler
   * are supplied from the mote type.
   *
   * @param moteType Mote type
   * @param sim Mote's simulation
   */
  ContikiMote(ContikiMoteType moteType, Simulation sim) throws MoteType.MoteTypeCreationException {
    super(moteType, moteType.createInitialMemory(), sim);
    moteInterfaces.init(this);
    for (var intf : moteInterfaces.getInterfaces()) {
      if (intf instanceof PolledBeforeActiveTicks intf2) {
        polledBeforeActive.add(intf2);
      }
      if (intf instanceof PolledAfterActiveTicks intf2) {
        polledAfterActive.add(intf2);
      }
      if (intf instanceof PolledBeforeAllTicks intf2) {
        polledBeforePassive.add(intf2);
      }
      if (intf instanceof PolledAfterAllTicks intf2) {
        polledAfterPassive.add(intf2);
      }
    }
    requestImmediateWakeup();
  }

  /**
   * Ticks mote once. This is done by first polling all interfaces
   * and letting them act on the stored memory before the memory is set. Then
   * the mote is ticked, and the new memory is received.
   * Finally, all interfaces are allowing to act on the new memory in order to
   * discover relevant changes. This method also schedules the next mote tick time
   * depending on Contiki specifics; pending timers and polled processes.
   *
   * @param simTime Current simulation time
   */
  @Override
  protected void execute(long simTime) {
    // (Jan 2023, Java 17/IntelliJ): Keep the interface actions in explicit for-loops,
    // so costs are clearly attributed in performance profiles.
    for (var moteInterface : polledBeforeActive) {
      moteInterface.doActionsBeforeTick();
    }
    for (var moteInterface : polledBeforePassive) {
      moteInterface.doActionsBeforeTick();
    }

    /* Check if pre-boot time */
    var moteTime = moteInterfaces.getClock().getTime();
    if (moteTime < 0) {
      scheduleNextWakeup(simTime + Math.abs(moteTime));
      return;
    }

    /* Copy mote memory to Contiki */
    ContikiMoteType.setCoreMemory(moteMemory);

    /* Handle a single Contiki events */
    moteType.tick();

    /* Copy mote memory from Contiki */
    ContikiMoteType.getCoreMemory(moteMemory);

    moteMemory.pollForMemoryChanges();
    for (var moteInterface : polledAfterActive) {
      moteInterface.doActionsAfterTick();
    }
    for (var moteInterface : polledAfterPassive) {
      moteInterface.doActionsAfterTick();
    }
  }

  @Override
  public String toString() {
    return "Contiki " + getID();
  }
}
