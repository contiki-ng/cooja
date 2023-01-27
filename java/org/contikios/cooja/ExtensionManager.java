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

package org.contikios.cooja;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.motes.DisturberMoteType;
import org.contikios.cooja.motes.ImportAppMoteType;
import org.contikios.cooja.mspmote.SkyMoteType;
import org.contikios.cooja.mspmote.Z1MoteType;
import org.contikios.cooja.mspmote.plugins.MspCLI;
import org.contikios.cooja.mspmote.plugins.MspCodeWatcher;
import org.contikios.cooja.mspmote.plugins.MspCycleWatcher;
import org.contikios.cooja.mspmote.plugins.MspStackWatcher;
import org.contikios.cooja.plugins.BaseRSSIconf;
import org.contikios.cooja.plugins.BufferListener;
import org.contikios.cooja.plugins.DGRMConfigurator;
import org.contikios.cooja.plugins.EventListener;
import org.contikios.cooja.plugins.LogListener;
import org.contikios.cooja.plugins.Mobility;
import org.contikios.cooja.plugins.MoteInformation;
import org.contikios.cooja.plugins.MoteInterfaceViewer;
import org.contikios.cooja.plugins.Notes;
import org.contikios.cooja.plugins.PowerTracker;
import org.contikios.cooja.plugins.RadioLogger;
import org.contikios.cooja.plugins.ScriptRunner;
import org.contikios.cooja.plugins.TimeLine;
import org.contikios.cooja.plugins.VariableWatcher;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;
import org.contikios.cooja.radiomediums.LogisticLoss;
import org.contikios.cooja.radiomediums.SilentRadioMedium;
import org.contikios.cooja.radiomediums.UDGM;
import org.contikios.cooja.radiomediums.UDGMConstantLoss;
import org.contikios.cooja.serialsocket.SerialSocketClient;
import org.contikios.cooja.serialsocket.SerialSocketServer;
import org.contikios.mrm.MRM;

/**
 * Class for loading and querying dynamic extensions.
 */
public class ExtensionManager {
  static final LinkedHashMap<String, Class<? extends Plugin>> builtinPlugins = new LinkedHashMap<>();
  static final LinkedHashMap<String, Class<? extends RadioMedium>> builtinRadioMediums = new LinkedHashMap<>();
  static {
    registerBuiltinPlugin(Visualizer.class);
    registerBuiltinPlugin(LogListener.class);
    registerBuiltinPlugin(TimeLine.class);
    registerBuiltinPlugin(Mobility.class);
    registerBuiltinPlugin(MoteInformation.class);
    registerBuiltinPlugin(MoteInterfaceViewer.class);
    registerBuiltinPlugin(VariableWatcher.class);
    registerBuiltinPlugin(EventListener.class);
    registerBuiltinPlugin(RadioLogger.class);
    registerBuiltinPlugin(ScriptRunner.class);
    registerBuiltinPlugin(Notes.class);
    registerBuiltinPlugin(BufferListener.class);
    registerBuiltinPlugin(DGRMConfigurator.class);
    registerBuiltinPlugin(BaseRSSIconf.class);
    registerBuiltinPlugin(PowerTracker.class);
    registerBuiltinPlugin(SerialSocketClient.class);
    registerBuiltinPlugin(SerialSocketServer.class);
    registerBuiltinPlugin(MspCLI.class);
    registerBuiltinPlugin(MspCodeWatcher.class);
    registerBuiltinPlugin(MspStackWatcher.class);
    registerBuiltinPlugin(MspCycleWatcher.class);

    registerBuiltinRadioMedium(UDGM.class);
    registerBuiltinRadioMedium(UDGMConstantLoss.class);
    registerBuiltinRadioMedium(DirectedGraphMedium.class);
    registerBuiltinRadioMedium(SilentRadioMedium.class);
    registerBuiltinRadioMedium(LogisticLoss.class);
    registerBuiltinRadioMedium(MRM.class);

  }
  private static void registerBuiltinPlugin(final Class<? extends Plugin> pluginClass) {
    builtinPlugins.put(pluginClass.getName(), pluginClass);
  }

  private static void registerBuiltinRadioMedium(Class<? extends RadioMedium> radioMediumClass) {
    builtinRadioMediums.put(radioMediumClass.getName(), radioMediumClass);
  }

  /** Get the class for a named plugin, returns null if not found. */
  public static Class<? extends Plugin> getPluginClass(Cooja cooja, String name) {
    var clazz = builtinPlugins.get(name);
    if (clazz != null) {
      return clazz;
    }
    for (var candidate : cooja.getRegisteredPlugins()) {
      if (name.equals(candidate.getName())) {
        clazz = candidate;
        break;
      }
    }
    return clazz;
  }

  /** Get the class for a named radio medium, returns null if not found. */
  public static Class<? extends RadioMedium> getRadioMediumClass(Cooja cooja, String name) {
    var clazz = builtinRadioMediums.get(name);
    if (clazz != null) {
      return clazz;
    }
    for (var candidate : cooja.getRegisteredRadioMediums()) {
      if (name.equals(candidate.getName())) {
        clazz = candidate;
        break;
      }
    }
    return clazz;
  }

  /** Create a radio medium of a certain class, returns null on failure. */
  public static RadioMedium createRadioMedium(Cooja cooja, Simulation sim, String name)
          throws Cooja.SimulationCreationException {
    if (name.startsWith("se.sics")) {
      name = name.replaceFirst("^se\\.sics", "org.contikios");
    }
    return switch (name) {
      case "org.contikios.cooja.radiomediums.UDGM" -> new UDGM(sim);
      case "org.contikios.cooja.radiomediums.UDGMConstantLoss" -> new UDGMConstantLoss(sim);
      case "org.contikios.cooja.radiomediums.DirectedGraphMedium" -> new DirectedGraphMedium(sim);
      case "org.contikios.cooja.radiomediums.SilentRadioMedium" -> new SilentRadioMedium(sim);
      case "org.contikios.cooja.radiomediums.LogisticLoss" -> new LogisticLoss(sim);
      case "org.contikios.mrm.MRM" -> new MRM(sim);
      default -> {
        var clazz = getRadioMediumClass(cooja, name);
        if (clazz == null) {
          throw new Cooja.SimulationCreationException("Could not load " + name, null);
        }
        try {
          yield clazz.getConstructor(Simulation.class).newInstance(sim);
        } catch (Exception e) {
          throw new Cooja.SimulationCreationException("Could not construct " + name, e);
        }
      }
    };
  }

  /** Create a mote of a certain class, returns null on failure. */
  public static MoteType createMoteType(Cooja cooja, String name) throws MoteType.MoteTypeCreationException {
    if (name.startsWith("se.sics")) {
      name = name.replaceFirst("^se\\.sics", "org.contikios");
    }
    return switch (name) {
      case "org.contikios.cooja.motes.ImportAppMoteType" -> new ImportAppMoteType();
      case "org.contikios.cooja.motes.DisturberMoteType" -> new DisturberMoteType();
      case "org.contikios.cooja.contikimote.ContikiMoteType" -> new ContikiMoteType(cooja);
      case "org.contikios.cooja.mspmote.SkyMoteType" -> new SkyMoteType();
      case "org.contikios.cooja.mspmote.Z1MoteType" -> new Z1MoteType();
      default -> {
        Class<? extends MoteType> moteType = null;
        for (var clazz : cooja.getRegisteredMoteTypes()) {
          if (name.equals(clazz.getName())) {
            moteType = clazz;
            break;
          }
        }
        if (moteType == null) {
          throw new MoteType.MoteTypeCreationException("MoteType " + name + " not registered");
        }
        try {
          yield moteType.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
          throw new MoteType.MoteTypeCreationException("Could not create " + name, e);
        }
      }
    };
  }
}
