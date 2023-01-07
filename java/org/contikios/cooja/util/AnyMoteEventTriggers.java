/*
 * Copyright (c) 2023, RISE Research Institutes of Sweden AB.
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
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.util;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AnyMoteEventTriggers<K> extends EventTriggers<K, Mote> {
  private final Simulation simulation;
  private final Function<Mote, Optional<EventTriggers<K, Mote>>> getEventTriggers;
  /** Reuse the same trigger instance for all mote subscriptions */
  private final BiConsumer<K, Mote> trigger = this::trigger;

  public AnyMoteEventTriggers(Simulation simulation,
                              Function<Mote, Optional<EventTriggers<K, Mote>>> getEventTriggers) {
    this.simulation = simulation;
    this.getEventTriggers = getEventTriggers;
  }

  @Override
  protected void activate() {
    simulation.getMoteTriggers().addTrigger(this, (operation, mote) ->
            getEventTriggers.apply(mote).ifPresent(e -> {
              if (operation == AddRemove.ADD) {
                e.addTrigger(this, trigger);
              } else {
                e.deleteTriggers(this);
              }
            }));
    for (var mote: simulation.getMotes()) {
      getEventTriggers.apply(mote).ifPresent(e -> e.addTrigger(this, trigger));
    }
  }
}
