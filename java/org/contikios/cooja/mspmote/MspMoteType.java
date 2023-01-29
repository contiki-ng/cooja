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

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.contikios.cooja.dialogs.AbstractCompileDialog;
import org.contikios.cooja.mote.BaseContikiMoteType;
import org.contikios.cooja.mote.memory.MemoryInterface.Symbol;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Simulation;
import org.jdom2.Element;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.util.DebugInfo;
import se.sics.mspsim.util.ELF;
import se.sics.mspsim.util.MapEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MSP430-based mote types emulated in MSPSim.
 *
 * @see SkyMoteType
 *
 * @author Fredrik Osterlind, Joakim Eriksson, Niclas Finne
 */
@ClassDescription("Msp Mote Type")
public abstract class MspMoteType extends BaseContikiMoteType {
  private static final Logger logger = LoggerFactory.getLogger(MspMoteType.class);

  private boolean loadedDebugInfo;
  private HashMap<File, HashMap<Integer, Integer>> debuggingInfo; /* cached */
  private ELF elf; /* cached */

  @Override
  protected AbstractCompileDialog createCompilationDialog(Cooja gui, MoteTypeConfig cfg) {
    return new MspCompileDialog(gui, this, cfg);
  }

  @Override
  protected void appendVisualizerInfo(StringBuilder sb) {
  }

  @Override
  public boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable)
      throws MoteTypeCreationException {
    if (!setBaseConfigXML(simulation, configXML)) {
      return false;
    }

    if (fileFirmware == null && fileSource == null) {
      throw new MoteTypeCreationException("Neither source or firmware specified");
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
    return configureAndInit(Cooja.getTopParentContainer(), simulation, Cooja.isVisualized());
  }

  @Override
  public long getExecutableAddressOf(File file, int lineNr) {
    if (file == null || lineNr < 0) {
      return -1;
    }
    if (!loadedDebugInfo) {
      loadedDebugInfo = true;
      try {
        debuggingInfo = getFirmwareDebugInfo();
      } catch (IOException e) {
        logger.error("Failed reading debug info: {}", e.getMessage(), e);
      }
    }
    if (debuggingInfo == null) {
      return -1;
    }

    // Match file.
    var lineTable = debuggingInfo.get(file);
    if (lineTable == null) {
      for (var entry : debuggingInfo.entrySet()) {
        var f = entry.getKey();
        if (f != null && f.getName().equals(file.getName())) {
          lineTable = entry.getValue();
          break;
        }
      }
    }
    if (lineTable == null) {
      return -1;
    }

    // Match line number.
    if (lineTable.get(lineNr) != null) {
      for (var entry : lineTable.entrySet()) {
        var l = entry.getKey();
        if (l != null && l == lineNr) { // Found line address.
          return entry.getValue();
        }
      }
    }
    return -1;
  }

  Map<String, Symbol> getEntries(GenericNode node) {
    if (Cooja.isVisualized()) {
      EventQueue.invokeLater(() -> Cooja.setProgressMessage("Loading " + getContikiFirmwareFile().getName()));
    }
    var elf = (ELF) node.getRegistry().getComponent("elf");
    var vars = new HashMap<String, Symbol>();
    for (var entry : elf.getMap().getAllEntries()) {
      if (entry.getType() != MapEntry.TYPE.variable) {
        continue;
      }
      vars.put(entry.getName(),new Symbol(Symbol.Type.VARIABLE, entry.getName(), entry.getAddress(), entry.getSize()));
    }
    return vars;
  }

  public ELF getELF() throws IOException {
    if (elf == null) {
      elf = ELF.readELF(getContikiFirmwareFile().getPath());
    }
    return elf;
  }

  private HashMap<File, HashMap<Integer, Integer>> getFirmwareDebugInfo()
  throws IOException {
    if (debuggingInfo == null) {
      debuggingInfo = getFirmwareDebugInfo(getELF());
    }
    return debuggingInfo;
  }

  private static HashMap<File, HashMap<Integer, Integer>> getFirmwareDebugInfo(ELF elf) {
    HashMap<File, HashMap<Integer, Integer>> fileToLineHash = new HashMap<>();

    if (elf.getDebug() == null) {
      // No debug information is available
      return fileToLineHash;
    }

    /* Fetch all executable addresses */
    var addresses = elf.getDebug().getExecutableAddresses();
    if (addresses == null) {
      // No debug information is available
      return fileToLineHash;
    }

    for (var address: addresses) {
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
      fileToLineHash.computeIfAbsent(file, k -> new HashMap<>()).put(info.getLine(), address);
    }
    return fileToLineHash;
  }
}
