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
 */

package org.contikios.cooja;

import java.awt.Container;
import java.io.File;
import java.util.Collection;
import java.util.List;
import javax.swing.JComponent;

import org.jdom2.Element;

import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.dialogs.MessageList;

/**
 * The mote type defines properties common for several motes. These properties
 * may differ between different implementations, but typically includes how a
 * mote is initialized, which hardware peripherals each mote has
 * etc. All simulated motes belongs to one mote type.
 * <p>
 * A mote type may also hold the connection to an underlying simulation
 * framework, such as a compiled Contiki system.
 *
 * @see ContikiMoteType
 * @author Fredrik Osterlind
 */
public interface MoteType {

  /**
   * Returns the mote type description.
   *
   * @return Description
   */
  String getDescription();

  /**
   * Sets the mote type description.
   *
   * @param description
   *          New description
   */
  void setDescription(String description);

  /**
   * Returns the mote type identifier.
   *
   * @return Mote type identifier
   */
  String getIdentifier();

  /**
   * @return Mote interface classes of mote type.
   */
  List<Class<? extends MoteInterface>> getMoteInterfaceClasses();

  /**
   * Returns a panel with mote type specific data.
   * May be null.
   *
   * @return Mote type visualizer
   */
  JComponent getTypeVisualizer();

  /**
   * Returns this mote type's project configuration.
   *
   * @return Project configuration
   */
  ProjectConfig getConfig();

  /**
   * Generates a mote of this mote type.
   *
   * @param simulation
   *          Simulation that will contain mote
   * @return New mote
   */
  Mote generateMote(Simulation simulation) throws MoteTypeCreationException;

  /**
   * This method configures and initializes a mote type ready to be used. It is
   * called from the simulator when a new mote type is created. It may simply
   * confirm that all settings are valid and return true, or display a dialog
   * allowing a user to manually configure the mote type.
   * <p>
   * This method need normally only be run once per mote type!
   *
   * @param parentContainer
   *          Parent container. May be null if not visualized.
   * @param simulation
   *          Simulation holding (or that should hold) mote type
   * @param visAvailable
   *          True if this method is allowed to show a visualizer
   * @return True if mote type has valid settings and is ready to be used
   */
  boolean configureAndInit(Container parentContainer, Simulation simulation,
      boolean visAvailable) throws MoteTypeCreationException;

  /**
   * Returns XML elements representing the current config of this mote type.
   * This is fetched by the simulator for example when saving a simulation
   * configuration file. For example a Contiki base directory may be saved.
   *
   * @see #setConfigXML(Simulation, Collection, boolean)
   * @param simulation
   *          Current simulation
   * @return XML elements representing the current mote type's config
   */
  Collection<Element> getConfigXML(Simulation simulation);

  /**
   * Sets the current mote type config depending on the given XML elements.
   * Observe that this method is responsible for restoring the configuration
   * depending on the given arguments. This may include recompiling and loading
   * libraries.
   *
   * @see #getConfigXML(Simulation)
   * @param simulation
   *          Simulation that will hold the mote type
   * @param configXML
   *          Config XML elements
   * @param visAvailable
   *          True if this method is allowed to show a visualizer
   * @return True if config was set successfully, false otherwise
   */
  boolean setConfigXML(
      Simulation simulation, Collection<Element> configXML, boolean visAvailable)
  throws MoteTypeCreationException;

  default long getExecutableAddressOf(File file, int lineNr) {
    return -1;
  }

  /** Called when the mote type is added to the simulation. */
  default void added() { }

  /** Called when the mote type is removed from the simulation. */
  default void removed() { }

  class MoteTypeCreationException extends Exception {
    private MessageList compilationOutput;

    public MoteTypeCreationException(String message) {
      super(message);
    }
    public MoteTypeCreationException(String message, MessageList output) {
      super(message);
      compilationOutput = output;
    }
    public MoteTypeCreationException(String message, Throwable cause) {
      super(message, cause);
    }
    public MoteTypeCreationException(String message, Throwable cause, MessageList output) {
      super(message, cause);
      compilationOutput = output;
    }
    public MessageList getCompilationOutput() {
      return compilationOutput;
    }
  }

}
