/*
 * Copyright (c) 2007-2012, Swedish Institute of Computer Science.
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
 */

package org.contikios.cooja.mspmote;

import java.awt.Container;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.mote.BaseContikiMoteType;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.mspmote.interfaces.MspSerial;
import se.sics.mspsim.util.DebugInfo;
import se.sics.mspsim.util.ELF;

/**
 * MSP430-based mote types emulated in MSPSim.
 *
 * @see SkyMoteType
 *
 * @author Fredrik Osterlind, Joakim Eriksson, Niclas Finne
 */
@ClassDescription("Msp Mote Type")
public abstract class MspMoteType extends BaseContikiMoteType {
  private static final Logger logger = LogManager.getLogger(MspMoteType.class);

  protected abstract String getMoteImage();

  @Override
  public final Mote generateMote(Simulation simulation) throws MoteTypeCreationException {
    MspMote mote = createMote(simulation);
    mote.initMote();
    return mote;
  }

  protected abstract MspMote createMote(Simulation simulation) throws MoteTypeCreationException;

  @Override
  public boolean configureAndInit(Container parentContainer, Simulation simulation, boolean visAvailable)
          throws MoteTypeCreationException {
    // If visualized, show compile dialog and let user configure.
    if (visAvailable && !simulation.isQuickSetup()) {
      // Create unique identifier.
      if (getIdentifier() == null) {
        int counter = 0;
        boolean identifierOK = false;
        while (!identifierOK) {
          identifierOK = true;
          counter++;
          setIdentifier(getMoteType() + counter);

          for (MoteType existingMoteType : simulation.getMoteTypes()) {
            if (existingMoteType == this) {
              continue;
            }
            if (existingMoteType.getIdentifier().equals(getIdentifier())) {
              identifierOK = false;
              break;
            }
          }
        }
      }

      if (getDescription() == null) {
        setDescription(getMoteName() + " Mote Type #" + getIdentifier());
      }
      return MspCompileDialog.showDialog(parentContainer, simulation, this, getMoteType());
    }

    if (getIdentifier() == null) {
      throw new MoteTypeCreationException("No identifier");
    }
    if (getContikiSourceFile() == null) {
      // Source file is null for firmware-only simulations, so just return true if firmware exists.
      final var firmware = getContikiFirmwareFile();
      if (firmware == null || !firmware.exists()) {
        throw new MoteTypeCreationException("Contiki firmware file does not exist: " + firmware);
      }
      return true;
    }
    if (getCompileCommands() == null) {
      throw new MoteTypeCreationException("No compile commands specified");
    }

    // Not visualized: Compile Contiki immediately.
    return compileMoteType(visAvailable, null);
  }

  @Override
  public JComponent getTypeVisualizer() {
    StringBuilder sb = new StringBuilder();
    // Identifier
    sb.append("<html><table><tr><td>Identifier</td><td>")
    .append(getIdentifier()).append("</td></tr>");

    // Description
    sb.append("<tr><td>Description</td><td>")
    .append(getDescription()).append("</td></tr>");

    /* Contiki source */
    sb.append("<tr><td>Contiki source</td><td>");
    final var source = getContikiSourceFile();
    if (source != null) {
      sb.append(source.getAbsolutePath());
    } else {
      sb.append("[not specified]");
    }
    sb.append("</td></tr>");

    /* Contiki firmware */
    sb.append("<tr><td>Contiki firmware</td><td>")
    .append(getContikiFirmwareFile().getAbsolutePath()).append("</td></tr>");

    /* Compile commands */
    String compileCommands = getCompileCommands();
    if (compileCommands == null) {
        compileCommands = "";
    }
    sb.append("<tr><td valign=top>Compile commands</td><td>")
    .append(compileCommands.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")).append("</td></tr>");

    JLabel label = new JLabel(sb.append("</table></html>").toString());
    label.setVerticalTextPosition(JLabel.TOP);
    /* Icon */
    Icon moteTypeIcon = getMoteTypeIcon();
    if (moteTypeIcon != null) {
      label.setIcon(moteTypeIcon);
    }
    return label;
  }

  public Icon getMoteTypeIcon() {
    String imageName = getMoteImage();
    if (imageName == null) {
      return null;
    }
    URL imageURL = this.getClass().getClassLoader().getResource(imageName);
    if (imageURL == null) {
      return null;
    }
    ImageIcon icon = new ImageIcon(imageURL);
    if (icon.getIconHeight() > 0 && icon.getIconWidth() > 0) {
      Image image = icon.getImage().getScaledInstance(
              (200 * icon.getIconWidth() / icon.getIconHeight()), 200,
              Image.SCALE_DEFAULT);
      return new ImageIcon(image);
    }
    return null;
  }

  @Override
  public Collection<Element> getConfigXML(Simulation simulation) {
    ArrayList<Element> config = new ArrayList<>();

    // Identifier
    var element = new Element("identifier");
    element.setText(getIdentifier());
    config.add(element);

    // Description
    element = new Element("description");
    element.setText(getDescription());
    config.add(element);

    // Source file
    if (fileSource != null) {
      element = new Element("source");
      File file = simulation.getCooja().createPortablePath(fileSource);
      element.setText(file.getPath().replaceAll("\\\\", "/"));
      element.setAttribute("EXPORT", "discard");
      config.add(element);
      element = new Element("commands");
      element.setText(compileCommands);
      element.setAttribute("EXPORT", "discard");
      config.add(element);
    }

    // Firmware file
    element = new Element("firmware");
    File file = simulation.getCooja().createPortablePath(fileFirmware);
    element.setText(file.getPath().replaceAll("\\\\", "/"));
    element.setAttribute("EXPORT", "copy");
    config.add(element);

    // Mote interfaces
    for (var moteInterface : moteInterfaceClasses) {
      element = new Element("moteinterface");
      element.setText(moteInterface.getName());
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable)
      throws MoteTypeCreationException {

    ArrayList<Class<? extends MoteInterface>> intfClassList = new ArrayList<>();
    for (Element element : configXML) {
      String name = element.getName();

      if (name.equals("identifier")) {
        identifier = element.getText();
      } else if (name.equals("description")) {
        description = element.getText();
      } else if (name.equals("source")) {
        fileSource = new File(element.getText());
        if (!fileSource.exists()) {
          fileSource = simulation.getCooja().restorePortablePath(fileSource);
        }
      } else if (name.equals("command")) {
        /* Backwards compatibility: command is now commands */
        logger.warn("Old simulation config detected: old version only supports a single compile command");
        compileCommands = element.getText();
      } else if (name.equals("commands")) {
        compileCommands = element.getText();
      } else if (name.equals("firmware")) {
        fileFirmware = new File(element.getText());
        if (!fileFirmware.exists()) {
          fileFirmware = simulation.getCooja().restorePortablePath(fileFirmware);
        }
      } else if (name.equals("elf")) {
        /* Backwards compatibility: elf is now firmware */
        logger.warn("Old simulation config detected: firmware specified as elf");
        fileFirmware = new File(element.getText());
      } else if (name.equals("moteinterface")) {
        String intfClass = element.getText().trim();

        /* Backwards compatibility: se.sics -> org.contikios */
        if (intfClass.startsWith("se.sics")) {
        	intfClass = intfClass.replaceFirst("se\\.sics", "org.contikios");
        }

        /* Backwards compatibility: MspIPAddress -> IPAddress */
        if (intfClass.equals("org.contikios.cooja.mspmote.interfaces.MspIPAddress")) {
        	logger.warn("Old simulation config detected: IP address interface was moved");
        	intfClass = IPAddress.class.getName();
        }
        if (intfClass.equals("org.contikios.cooja.mspmote.interfaces.ESBLog")) {
        	logger.warn("Old simulation config detected: ESBLog was replaced by MspSerial");
        	intfClass = MspSerial.class.getName();
        }
        if (intfClass.equals("org.contikios.cooja.mspmote.interfaces.SkyByteRadio")) {
        	logger.warn("Old simulation config detected: SkyByteRadio was replaced by Msp802154Radio");
        	intfClass = Msp802154Radio.class.getName();
        }
        if (intfClass.equals("org.contikios.cooja.mspmote.interfaces.SkySerial")) {
        	logger.warn("Old simulation config detected: SkySerial was replaced by MspSerial");
        	intfClass = MspSerial.class.getName();
        }

        var moteInterfaceClass = MoteInterfaceHandler.getInterfaceClass(simulation.getCooja(), this, intfClass);

        if (moteInterfaceClass == null) {
          logger.warn("Can't find mote interface class: " + intfClass);
        } else {
          intfClassList.add(moteInterfaceClass);
        }
      } else {
        logger.fatal("Unrecognized entry in loaded configuration: " + name);
        throw new MoteTypeCreationException(
            "Unrecognized entry in loaded configuration: " + name);
      }
    }

    Class<? extends MoteInterface>[] intfClasses = intfClassList.toArray(new Class[0]);

    if (intfClasses.length == 0) {
      /* Backwards compatibility: No interfaces specified */
      logger.warn("Old simulation config detected: no mote interfaces specified, assuming all.");
      intfClasses = getAllMoteInterfaceClasses();
    }
    setMoteInterfaceClasses(intfClasses);

    if (fileFirmware == null) {
      if (fileSource == null) {
        throw new MoteTypeCreationException("Neither source or firmware specified");
      }

      /* Backwards compatibility: Generate expected firmware file name from source */
      fileFirmware = getExpectedFirmwareFile(fileSource);
      logger.warn("Old simulation config detected: no firmware file specified, using '{}'", fileFirmware);
    }

    return configureAndInit(Cooja.getTopParentContainer(), simulation, visAvailable);
  }

  public abstract Class<? extends MoteInterface>[] getAllMoteInterfaceClasses();
  public abstract Class<? extends MoteInterface>[] getDefaultMoteInterfaceClasses();

  public File getExpectedFirmwareFile(File source) {
    File parentDir = source.getParentFile();
    String sourceNoExtension = source.getName();
    if (sourceNoExtension.endsWith(".c")) {
      sourceNoExtension = sourceNoExtension.substring(0, source.getName().length() - 2);
    }
    return new File(parentDir, "/build/" + getMoteType() + "/" + sourceNoExtension + '.' + getMoteType());
  }

  private ELF elf; /* cached */
  public ELF getELF() throws IOException {
    if (elf == null) {
      elf = ELF.readELF(getContikiFirmwareFile().getPath());
    }
    return elf;
  }

  private HashMap<File, HashMap<Integer, Integer>> debuggingInfo = null; /* cached */
  public HashMap<File, HashMap<Integer, Integer>> getFirmwareDebugInfo()
  throws IOException {
    if (debuggingInfo == null) {
      debuggingInfo = getFirmwareDebugInfo(getELF());
    }
    return debuggingInfo;
  }

  public static HashMap<File, HashMap<Integer, Integer>> getFirmwareDebugInfo(ELF elf) {
    HashMap<File, HashMap<Integer, Integer>> fileToLineHash = new HashMap<>();

    if (elf.getDebug() == null) {
      // No debug information is available
      return fileToLineHash;
    }

    /* Fetch all executable addresses */
    ArrayList<Integer> addresses = elf.getDebug().getExecutableAddresses();
    if (addresses == null) {
      // No debug information is available
      return fileToLineHash;
    }

    for (int address: addresses) {
      DebugInfo info = elf.getDebugInfo(address);
      if (info == null) {
        continue;
      }
      if (info.getPath() == null && info.getFile() == null) {
        continue;
      }
      if (info.getLine() < 0) {
        continue;
      }

      File file;
      if (info.getPath() != null) {
        file = new File(info.getPath(), info.getFile());
      } else {
        file = new File(info.getFile());
      }
      try {
        file = file.getCanonicalFile();
      } catch (IOException e) {
      }

      HashMap<Integer, Integer> lineToAddrHash = fileToLineHash.get(file);
      if (lineToAddrHash == null) {
        lineToAddrHash = new HashMap<>();
        fileToLineHash.put(file, lineToAddrHash);
      }

      lineToAddrHash.put(info.getLine(), address);
    }

    return fileToLineHash;
  }

}
