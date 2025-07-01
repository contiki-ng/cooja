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

import java.util.ArrayList;
import java.util.Collection;
import org.contikios.cooja.interfaces.Battery;
import org.contikios.cooja.interfaces.Beeper;
import org.contikios.cooja.interfaces.Button;
import org.contikios.cooja.interfaces.Clock;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.interfaces.MoteID;
import org.contikios.cooja.interfaces.PIR;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.interfaces.SerialPort;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mote interface handler holds all interfaces for a specific mote.
 *
 * @author Fredrik Osterlind
 */
public class MoteInterfaceHandler {
  private static final Logger logger = LoggerFactory.getLogger(MoteInterfaceHandler.class);

  private final ArrayList<MoteInterface> moteInterfaces = new ArrayList<>();

  /* Cached interfaces */
  private Battery myBattery;
  private Beeper myBeeper;
  private Button myButton;
  private Clock myClock;
  private IPAddress myIPAddress;
  private LED myLED;
  private Log myLog;
  private MoteID<?> myMoteID;
  private PIR myPIR;
  private Position myPosition;
  private Radio myRadio;
  private SerialPort mySerialPort;

  /**
   * Initializes mote interface handler. All given interfaces are created.
   *
   * @param mote Mote
   */
  public void init(Mote mote) throws MoteType.MoteTypeCreationException {
    for (var interfaceClass : mote.getType().getMoteInterfaceClasses()) {
      try {
        moteInterfaces.add(interfaceClass.getConstructor(Mote.class).newInstance(mote));
      } catch (Exception e) {
        logger.error("Exception when calling constructor of " + interfaceClass, e);
        throw new MoteType.MoteTypeCreationException("Exception when calling constructor of " + interfaceClass, e);
      }
    }
  }

  /**
   * Returns interface of given type. Returns the first interface found that
   * is either of the given class or of a subclass.
   * <p>
   * Usage: getInterfaceOfType(Radio.class)
   *
   * @param <N>
   * @param interfaceType Class of interface to return
   * @return Mote interface, or null if no interface exists of given type
   */
  public <N extends MoteInterface> N getInterfaceOfType(Class<N> interfaceType) {
    for (MoteInterface intf : moteInterfaces) {
      if (interfaceType.isInstance(intf)) {
        return interfaceType.cast(intf);
      }
    }

    return null;
  }

  /**
   * Returns the first interface with a class name that ends with the given arguments.
   * Example: mote.getInterfaces().get("Temperature");
   * 
   * @param classname
   * @return
   */
  public MoteInterface get(String classname) {
    for (MoteInterface intf : moteInterfaces) {
      if (intf.getClass().getName().endsWith(classname)) {
        return intf;
      }
    }
    return null;
  }

  /**
   * Returns the battery interface (if any).
   *
   * @return Battery interface
   */
  public Battery getBattery() {
    if (myBattery == null) {
      myBattery = getInterfaceOfType(Battery.class);
    }
    return myBattery;
  }

  /**
   * Returns the beeper interface (if any).
   *
   * @return Beeper interface
   */
  public Beeper getBeeper() {
    if (myBeeper == null) {
      myBeeper = getInterfaceOfType(Beeper.class);
    }
    return myBeeper;
  }

  /**
   * Returns the button interface (if any).
   *
   * @return Button interface
   */
  public Button getButton() {
    if (myButton == null) {
      myButton = getInterfaceOfType(Button.class);
    }
    return myButton;
  }

  /**
   * Returns the clock interface (if any).
   *
   * @return Clock interface
   */
  public Clock getClock() {
    if (myClock == null) {
      myClock = getInterfaceOfType(Clock.class);
    }
    return myClock;
  }

  /**
   * Returns the IP address interface (if any).
   *
   * @return IP Address interface
   */
  public IPAddress getIPAddress() {
    if (myIPAddress == null) {
      myIPAddress = getInterfaceOfType(IPAddress.class);
    }
    return myIPAddress;
  }

  /**
   * Returns the LED interface (if any).
   *
   * @return LED interface
   */
  public LED getLED() {
    if (myLED == null) {
      myLED = getInterfaceOfType(LED.class);
    }
    return myLED;
  }

  /**
   * Returns the log interface (if any).
   *
   * @return Log interface
   */
  public Log getLog() {
    if (myLog == null) {
      myLog = getInterfaceOfType(Log.class);
    }
    return myLog;
  }

  /**
   * Returns the mote ID interface (if any).
   *
   * @return Mote ID interface
   */
  public MoteID<?> getMoteID() {
    if (myMoteID == null) {
      myMoteID = getInterfaceOfType(MoteID.class);
    }
    return myMoteID;
  }

  /**
   * Returns the PIR interface (if any).
   *
   * @return PIR interface
   */
  public PIR getPIR() {
    if (myPIR == null) {
      myPIR = getInterfaceOfType(PIR.class);
    }
    return myPIR;
  }

  /**
   * Returns the position interface (if any).
   *
   * @return Position interface
   */
  public Position getPosition() {
    if (myPosition == null) {
      myPosition = getInterfaceOfType(Position.class);
    }
    return myPosition;
  }

  /**
   * Returns the radio interface (if any).
   *
   * @return Radio interface
   */
  public Radio getRadio() {
    if (myRadio == null) {
      myRadio = getInterfaceOfType(Radio.class);
    }
    return myRadio;
  }

  /**
   * Returns the first serial port interface (if any).
   *
   * @return Serial port interface
   */
  public SerialPort getSerialPort() {
    if (mySerialPort == null) {
      for (var moteInterface : moteInterfaces) {
        if (moteInterface instanceof SerialPort port) {
          mySerialPort = port;
          break;
        }
      }
    }
    return mySerialPort;
  }

  /**
   * @return Mote interfaces
   */
  public Collection<MoteInterface> getInterfaces() {
    return moteInterfaces;
  }

  public Collection<Element> getConfigXML() {
    var config = new ArrayList<Element>();
    for (var moteInterface: moteInterfaces) {
      var element = new Element("interface_config");
      element.setText(moteInterface.getClass().getName());
      var interfaceXML = moteInterface.getConfigXML();
      if (interfaceXML != null) {
        element.addContent(interfaceXML);
        config.add(element);
      }
    }
    return config;
  }

  public boolean setConfigXML(Mote mote, Element element, boolean ignoreFailure) {
    var name = element.getText().trim();
    if (name.startsWith("se.sics")) {
      name = name.replaceFirst("^se\\.sics", "org.contikios");
    }
    MoteInterface moteInterface = null;
    for (var candidateInterface : getInterfaces()) {
      if (name.equals(candidateInterface.getClass().getName())) {
        moteInterface = candidateInterface;
        break;
      }
    }
    if (moteInterface == null) {
      // Check for compatible interfaces, for example, when reconfiguring mote types.
      // Start with a name match, so native-image works for the builtin mote types.
      var moteInterfaceClass = name.endsWith("MoteID")
              ? MoteID.class : mote.getSimulation().getCooja().tryLoadClass(mote, MoteInterface.class, name);
      if (moteInterfaceClass == null) {
        logger.warn("Cannot find mote interface class: " + name);
        return ignoreFailure;
      }
      moteInterface = getInterfaceOfType(moteInterfaceClass);
      if (moteInterface == null) {
        logger.warn("Cannot find mote interface of class: " + moteInterfaceClass);
        return ignoreFailure;
      }
    }
    moteInterface.setConfigXML(element.getChildren(), Cooja.isVisualized());
    return true;
  }


  @Override
  public String toString() {
    return "Mote interfaces handler (" + moteInterfaces.size() + " mote interfaces)";
  }

  /** Called when the mote is added to the simulation. */
  public void added() {
    for (var i : moteInterfaces) {
      i.added();
    }
  }

  /** Called when the mote is removed from the simulation. */
  public void removed() {
    for (var i : moteInterfaces) {
      i.removed();
    }
  }
}
