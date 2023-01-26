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
 * This file is part of MSPSim.
 *
 * -----------------------------------------------------------------
 *
 * Main
 *
 * Authors : Joakim Eriksson, Niclas Finne
 * Created : 6 nov 2008
 */

package se.sics.mspsim;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import java.nio.file.Files;
import java.nio.file.Path;
import se.sics.mspsim.chip.AT45DB;
import se.sics.mspsim.chip.M25P80;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.platform.esb.ESBNode;
import se.sics.mspsim.platform.jcreate.JCreateNode;
import se.sics.mspsim.platform.sentillausb.SentillaUSBNode;
import se.sics.mspsim.platform.sky.SkyNode;
import se.sics.mspsim.platform.sky.TelosNode;
import se.sics.mspsim.platform.ti.CC430Node;
import se.sics.mspsim.platform.ti.Exp1101Node;
import se.sics.mspsim.platform.ti.Exp1120Node;
import se.sics.mspsim.platform.ti.Exp5438Node;
import se.sics.mspsim.platform.ti.Trxeb1120Node;
import se.sics.mspsim.platform.ti.Trxeb2520Node;
import se.sics.mspsim.platform.tyndall.TyndallNode;
import se.sics.mspsim.platform.wismote.WismoteNode;
import se.sics.mspsim.platform.z1.Z1Node;
import se.sics.mspsim.util.ArgumentManager;

/**
 *
 */
public class Main {

  public static GenericNode createNode(String className, String firmwareFile) throws IOException {
    return switch (className) { // Sorted alphabetically.
      case "se.sics.mspsim.platform.esb.ESBNode" -> {
        var cpu = ESBNode.makeCPU(ESBNode.makeChipConfig(), firmwareFile);
        yield new ESBNode(cpu);
      }
      case "se.sics.mspsim.platform.jcreate.JCreateNode" -> {
        var cpu = JCreateNode.makeCPU(JCreateNode.makeChipConfig(), firmwareFile);
        yield new JCreateNode(cpu, new M25P80(cpu));
      }
      case "se.sics.mspsim.platform.sentillausb.SentillaUSBNode" -> {
        var cpu = SentillaUSBNode.makeCPU(SentillaUSBNode.makeChipConfig(), firmwareFile);
        yield new SentillaUSBNode(cpu, new M25P80(cpu));
      }
      case "se.sics.mspsim.platform.ti.CC430Node" -> {
        var cpu = CC430Node.makeCPU(CC430Node.makeChipConfig(), firmwareFile);
        yield new CC430Node(cpu);
      }
      case "se.sics.mspsim.platform.ti.Exp1101Node" -> {
        var cpu = Exp1101Node.makeCPU(Exp1101Node.makeChipConfig(), firmwareFile);
        yield new Exp1101Node(cpu);
      }
      case "se.sics.mspsim.platform.ti.Exp1120Node" -> {
        var cpu = Exp1120Node.makeCPU(Exp1120Node.makeChipConfig(), firmwareFile);
        yield new Exp1120Node(cpu);
      }
      case "se.sics.mspsim.platform.ti.Exp5438Node" -> {
        var cpu = Exp5438Node.makeCPU(Exp5438Node.makeChipConfig(), firmwareFile);
        yield new Exp5438Node(cpu);
      }
      // Default to the Trxeb1120 node without ethernet.
      case "se.sics.mspsim.platform.ti.Trxeb1120Node" -> {
        var cpu = Trxeb1120Node.makeCPU(Trxeb1120Node.makeChipConfig(), firmwareFile);
        yield new Trxeb1120Node(false, cpu);
      }
      case "se.sics.mspsim.platform.ti.Trxeb2520Node" -> {
        var cpu = Trxeb2520Node.makeCPU(Trxeb2520Node.makeChipConfig(), firmwareFile);
        yield new Trxeb2520Node(cpu);
      }
      case "se.sics.mspsim.platform.sky.SkyNode" -> {
        var cpu = SkyNode.makeCPU(SkyNode.makeChipConfig(), firmwareFile);
        yield new SkyNode(cpu, new M25P80(cpu));
      }
      case "se.sics.mspsim.platform.sky.TelosNode" -> {
        var cpu = TelosNode.makeCPU(TelosNode.makeChipConfig(), firmwareFile);
        yield new TelosNode(cpu, new AT45DB(cpu));
      }
      case "se.sics.mspsim.platform.tyndall.TyndallNode" -> {
        var cpu = TyndallNode.makeCPU(TyndallNode.makeChipConfig(), firmwareFile);
        yield new TyndallNode(cpu);
      }
      case "se.sics.mspsim.platform.wismote.WismoteNode" -> {
        var cpu = WismoteNode.makeCPU(WismoteNode.makeChipConfig(), firmwareFile);
        yield new WismoteNode(cpu);
      }
      case "se.sics.mspsim.platform.z1.Z1Node" -> {
        var cpu = Z1Node.makeCPU(Z1Node.makeChipConfig(), firmwareFile);
        yield new Z1Node(cpu, new M25P80(cpu));
      }
      default -> {
        try {
          yield Class.forName(className).asSubclass(GenericNode.class).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | ClassCastException | InstantiationException | IllegalAccessException e) {
          // Can not find specified class, or wrong class type, or failed to instantiate
        } catch (InvocationTargetException | NoSuchMethodException e) {
          System.err.println("Could not construct node type: " + e.getMessage());
        }
        yield null;
      }
    };
  }

  public static String getNodeTypeByPlatform(String platform) {
    return switch (platform) { // Sorted alphabetically, on return value.
      case "esb" -> "se.sics.mspsim.platform.esb.ESBNode";
      case "jcreate" -> "se.sics.mspsim.platform.jcreate.JCreateNode";
      case "sentilla-usb" -> "se.sics.mspsim.platform.sentillausb.SentillaUSBNode";
      case "cc430" -> "se.sics.mspsim.platform.ti.CC430Node";
      case "exp1101" -> "se.sics.mspsim.platform.ti.Exp1101Node";
      case "exp1120" -> "se.sics.mspsim.platform.ti.Exp1120Node";
      case "exp5438" -> "se.sics.mspsim.platform.ti.Exp5438Node";
      case "trxeb1120" -> "se.sics.mspsim.platform.ti.Trxeb1120Node";
      case "trxeb2520" -> "se.sics.mspsim.platform.ti.Trxeb2520Node";
      case "sky" -> "se.sics.mspsim.platform.sky.SkyNode";
      case "telos" -> "se.sics.mspsim.platform.sky.TelosNode";
      case "tyndall" -> "se.sics.mspsim.platform.tyndall.TyndallNode";
      case "wismote" -> "se.sics.mspsim.platform.wismote.WismoteNode";
      case "z1" -> "se.sics.mspsim.platform.z1.Z1Node";
      // Try to guess the node type.
      default -> "se.sics.mspsim.platform." + platform + '.'
              + Character.toUpperCase(platform.charAt(0))
              + platform.substring(1).toLowerCase() + "Node";
    };
  }

  public static void main(String[] args) throws IOException {
    var config = new ArgumentManager(args);
    var processedArgs = config.getArguments();
    if (processedArgs.length == 0) {
      System.err.println("Usage: -platform=name <firmware>");
      System.exit(1);
    }
    if (!Files.exists(Path.of(processedArgs[0]))) {
      System.err.println("Could not find the firmware file '" + processedArgs[0] + "'.");
      System.exit(1);
    }
    String nodeType = config.getProperty("nodeType");
    if (nodeType == null) {
      var platform = config.getProperty("platform");
      if (platform == null) {
          // Default platform
          platform = "sky";

          // Guess platform based on firmware filename suffix.
          // TinyOS's firmware files are often named 'main.exe'.
          String[] a = config.getArguments();
          if (a.length > 0 && !"main.exe".equals(a[0])) {
              int index = a[0].lastIndexOf('.');
              if (index > 0) {
                  platform = a[0].substring(index + 1);
              }
          }
      }
      nodeType = getNodeTypeByPlatform(platform);
    }
    var node = createNode(nodeType, config.getArguments()[0]);
    if (node == null) {
      System.err.println("MSPSim does not currently support the platform '" + nodeType + "'.");
      System.exit(1);
    }
    node.setupArgs(config);
  }

}
