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
import java.util.Collections;
import java.util.List;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;

/**
 * The RadioMedium interface should be implemented by all COOJA radio
 * mediums. Radios registered in this medium can both send and receive radio
 * data. Depending on the implementation of this interface, more or less
 * accurate radio behaviour imitation is acquired.
 *
 * @author Fredrik Osterlind
 */
public interface RadioMedium {
  /**
   * Register a radio to this radio medium.
   * <p>
   * Concerning radio data, this radio will be treated the same way as a mote's
   * radio. This method can be used to add non-mote radio devices, such as a
   * packet generator or a sniffer.
   *
   * @param radio
   *          Radio
   * @param sim
   *          Simulation holding radio
   */
  void registerRadioInterface(Radio radio, Simulation sim);

  /**
   * Unregister given radio interface from this medium.
   *
   * @param radio
   *          Radio interface to unregister
   * @param sim
   *          Simulation holding radio
   */
  void unregisterRadioInterface(Radio radio, Simulation sim);

  /**
   * Get the list of radios within transmission range of specified radio interface.
   *
   * @param radio A radio interface whose neighbors are requested.
   * @return A list of radios within transmission range.
   */
  default List<Radio> getNeighbors(Radio radio) {
    return Collections.emptyList();
  }

  /**
   * Triggers that are notified of radio events.
   *
   * @see #getLastConnection()
   */
  EventTriggers<Radio.RadioEvent, Object> getRadioTransmissionTriggers();

  /**
   * @return Last radio connection finished in the radio medium
   */
  RadioConnection getLastConnection();

  /**
   * Returns XML elements representing the current config of this radio medium.
   * This is fetched by the simulator for example when saving a simulation
   * configuration file. For example a radio medium may return user altered
   * range parameters. This method should however not return state specific
   * information such as a current radio status. (All nodes are restarted when
   * loading a simulation.)
   *
   * @see #setConfigXML(Collection, boolean)
   * @return XML elements representing the current radio medium config
   */
  Collection<Element> getConfigXML();

  /**
   * Sets the current radio medium config depending on the given XML elements.
   * <p>
   * This method is called after the simulation has finished loading.
   *
   * @see #getConfigXML()
   * @param configXML
   *          Config XML elements
   * @return True if config was set successfully, false otherwise
   */
  boolean setConfigXML(Collection<Element> configXML, boolean visAvailable);

  /**
   * Called when radio medium is removed. 
   */
  default void removed() {}
}
