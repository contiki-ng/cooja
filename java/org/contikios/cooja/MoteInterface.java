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

import java.util.Collection;
import javax.swing.JPanel;
import org.jdom2.Element;

import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.PolledAfterAllTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.interfaces.PolledBeforeAllTicks;

/**
 * A mote interface represents a mote property. Typically, this is a simulated
 * hardware peripheral such as a button or LEDs. This may also be a property
 * that the mote itself is unaware of, for example the current position of
 * the mote.
 * <p>
 * Interfaces are the main way for the simulator to interact with a simulated
 * mote.
 * <p>
 * Each interface can be polled before and after mote ticks.
 * This is controlled by implementing the correct Java interfaces,
 * such as PolledBeforeActiveTicks.
 *
 * @see PolledBeforeActiveTicks
 * @see PolledAfterActiveTicks
 * @see PolledBeforeAllTicks
 * @see PolledAfterAllTicks
 *
 * @author Fredrik Osterlind
 */
public interface MoteInterface {
  /**
   * Returns a panel visualizing this interface. This could for
   * example show last messages sent/received for a radio interface, or logged
   * message for a log interface.
   * <p>
   * All panels returned from this method must later be released for memory
   * reasons.
   * <p>
   * This method may return null.
   *
   * @see #releaseInterfaceVisualizer(JPanel)
   * @return Interface visualizer or null
   */
  JPanel getInterfaceVisualizer();

  /**
   * This method should be called when a visualizer panel is no longer in use.
   * Any resources of that panel, for example registered observers, will be
   * released.
   *
   * @see #getInterfaceVisualizer()
   * @param panel
   *          An interface visualizer panel fetched earlier for this mote
   *          interface.
   */
  void releaseInterfaceVisualizer(JPanel panel);

  /**
   * Returns XML elements representing the current config of this mote
   * interface. This is fetched by the simulator for example when saving a
   * simulation configuration file. For example an IP interface may return one
   * element with the mote IP address. This method should however not return
   * state specific information such as a log history. (All nodes are restarted
   * when loading a simulation.)
   *
   * @see #setConfigXML(Collection, boolean)
   * @return XML elements representing the current interface config
   */
  default Collection<Element> getConfigXML() {
    return null;
  }

  /**
   * Sets the current mote interface config depending on the given XML elements.
   *
   * @see #getConfigXML()
   * @param configXML
   *          Config XML elements
   * @param visAvailable
   *          Is this object allowed to show a visualizer?
   */
  default void setConfigXML(Collection<Element> configXML, boolean visAvailable) {}
  
  /**
   * Called to free resources used by the mote interface.
   * This method is called when the mote is removed from the simulation.
   */
  default void removed() {}
  
  /**
   * Called when all mote interfaces have been added to mote.
   */
  default void added() {}
}
