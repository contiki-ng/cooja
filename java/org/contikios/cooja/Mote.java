/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
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
 */

package org.contikios.cooja;

import java.util.Collection;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.jdom2.Element;

/**
 * A simulated mote.
 * <p>
 * All motes have an interface handler, a mote type and a mote memory.
 *
 * @see org.contikios.cooja.MoteInterfaceHandler
 * @see org.contikios.cooja.mote.memory.SectionMoteMemory
 * @see org.contikios.cooja.MoteType
 *
 * @author Fredrik Osterlind
 */
public interface Mote {

  /**
   * @return Unique mote ID
   */
  int getID();

  /**
   * Returns the interface handler of this mote.
   *
   * @return Mote interface handler
   */
  MoteInterfaceHandler getInterfaces();

  /**
   * Returns the memory of this mote.
   *
   * @return Mote memory
   */
  MemoryInterface getMemory();

  /**
   * Returns mote type.
   *
   * @return Mote type
   */
  MoteType getType();

  /**
   * Returns simulation which holds this mote.
   *
   * @return Simulation
   */
  Simulation getSimulation();

  /**
   * Returns XML elements representing the current config of this mote. This is
   * fetched by the simulator for example when saving a simulation configuration
   * file. For example a mote may return the configs of all its interfaces. This
   * method should however not return state specific information.
   * (All nodes are restarted when loading a simulation.)
   *
   * @see #setConfigXML(Simulation, Collection, boolean)
   * @return XML elements representing the current mote config
   */
  Collection<Element> getConfigXML();

  /**
   * Sets the current mote config depending on the given XML elements.
   *
   * @param simulation
   *          Simulation holding this mote
   * @param configXML
   *          Config XML elements
   * @param visAvailable
   *          True if simulation is allowed to show visualizers
   *
   * @see #getConfigXML()
   * @return True if the mote was successfully configured and False otherwise.
   * @throws org.contikios.cooja.MoteType.MoteTypeCreationException if the mote failed to process the configuration
   */
  boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable) throws MoteType.MoteTypeCreationException;

  /**
   * Called when mote is added to simulation, so interfaces are added.
   */
  default void added() {
    getInterfaces().added();
  }

  /**
   * Called when mote is removed from simulation, so interfaces are removed.
   */
  default void removed() {
    getInterfaces().removed();
  }

  void setProperty(String key, Object obj);
  Object getProperty(String key);
}
