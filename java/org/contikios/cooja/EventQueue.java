/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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

package org.contikios.cooja;

import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * @author Joakim Eriksson (ported to COOJA by Fredrik Osterlind)
 */
final class EventQueue {

  private long count;

  public static final class Pair implements Comparable<Pair> {
    public final TimeEvent event;
    public final long time;

    private final long uuid;

    Pair(TimeEvent event, long time, long uuid) {
      this.event = event;
      this.time = time;
      this.uuid = uuid;
    }

    @Override
    public int compareTo(Pair other) {
      if (time < other.time)
      {
        return -1;
      }
      else if (time > other.time)
      {
        return +1;
      }
      else
      {
        // Tiebreaker, to prioritise events based on insertion order
        if (uuid < other.uuid)
        {
          return -1;
        }
        else if (uuid > other.uuid)
        {
          return +1;
        }
        else
        {
          throw new RuntimeException("Bad compare");
        }
      }
    }
  }

  private final PriorityQueue<Pair> queue = new PriorityQueue<>();

  /**
   * Should only be called from simulation thread!
   *
   * @param event Event
   * @param time Time
   */
  public void addEvent(TimeEvent event, long time) {
    if (event.isQueued()) {
      if (event.isScheduled()) {
        throw new IllegalStateException("Event is already scheduled: " + event);
      }
      removeFromQueue(event);
    }

    // Each event is given a monotonically increasing unique id.
    // This is used in a tiebreaker in the queue, so events that are
    // inserted earlier are executed first.
    queue.add(new Pair(event, time, count++));

    event.setScheduled(true);
  }

  /**
   * Should only be called from simulation thread!
   *
   * @param event Event
   * @return True if event was removed
   */
  private boolean removeFromQueue(TimeEvent event) {
    boolean removed = queue.removeIf((Pair p) -> p.event == event);

    assert removed == event.isQueued();

    if (removed)
    {
      event.setScheduled(false);
    }

    return removed;
  }

  public void clear() {
    queue.clear();
  }

  /**
   * Should only be called from simulation thread!
   *
   * @return Event
   */
  public Pair popFirst() {
    Pair tmp;

    while (true)
    {
      tmp = queue.poll();

      if (tmp == null) {
        return null;
      }

      boolean scheduled = tmp.event.isScheduled();

      // No longer scheduled or queued
      tmp.event.setScheduled(false);

      if (scheduled)
      {
        break;
      }

      // If not scheduled, then find the next scheduled event
    }

    return tmp;
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public boolean removeIf(final Predicate<TimeEvent> pred) {
    return queue.removeIf((Pair p) -> pred.test(p.event));
  }

  @Override
  public String toString() {
    return "EventQueue with " + queue.size() + " events";
  }
}
