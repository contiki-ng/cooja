/*
 * Copyright (c) 2022, RISE Research Institutes of Sweden AB.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

/**
 * Helper class that holds triggers. Triggers are called with a key for the kind
 * of event that happened, and the data.
 * @param <K> Key/event type
 * @param <T> Data value
 */
public class EventTriggers<K, T> {
  /** Utility enum that indicates something was added or removed. */
  public enum AddRemove {ADD, REMOVE}
  /** Utility enum that indicates something was added, removed, or updated. */
  public enum AddRemoveUpdate {ADD, REMOVE, UPDATE}
  /** Utility enum that indicates something was started, stopped or removed. */
  public enum Operation {START, STOP, REMOVE}
  /** Utility enum that indicates something was updated. Improves readability, could be represented by Void. */
  public enum Update {UPDATE}
  @SuppressWarnings("unchecked")
  private final BiConsumer<K, T>[] EMPTY_CONSUMER = new BiConsumer[0];

  private final LinkedHashMap<Object, ArrayList<BiConsumer<K, T>>> triggers = new LinkedHashMap<>();
  private BiConsumer<K, T>[] listeners = EMPTY_CONSUMER;
  private boolean hasChanged;
  private boolean isActive;

  /**
   * Add a trigger owned by an object.
   */
  public void addTrigger(Object owner, BiConsumer<K, T> observer) {
    triggers.computeIfAbsent(owner, k -> new ArrayList<>()).add(observer);
    hasChanged = true;
    if (!isActive) {
      isActive = true;
      activate();
    }
  }

  /**
   * Remove a trigger owned by an object.
   */
  public void removeTrigger(Object owner, BiConsumer<K, T> observer) {
    var l = triggers.get(owner);
    if (l == null) return;
    if (l.remove(observer)) {
      if (l.isEmpty()) {
        triggers.remove(owner);
      }
      hasChanged = true;
    }
  }

  /**
   * Delete all triggers belonging to an object.
   */
  public void deleteTriggers(Object owner) {
    if (triggers.remove(owner) != null) {
      hasChanged = true;
    }
  }

  /**
   * Invoke all triggers with the key and value as parameters.
   */
  public void trigger(K key, T value) {
    if (hasChanged) {
      hasChanged = false;
      var observers = new ArrayList<BiConsumer<K,T>>();
      for (var observables : triggers.values()) {
        observers.addAll(observables);
      }
      listeners = observers.toArray(EMPTY_CONSUMER);
    }

    // Remember original list in case it is changed
    var observers = listeners;
    for (var o : observers) {
      o.accept(key, value);
    }
  }

  /**
   * Called when first trigger has been added.
   */
  protected void activate() {
  }
}
