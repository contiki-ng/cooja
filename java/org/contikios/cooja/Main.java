/*
 * Copyright (c) 2022, Research Institutes of Sweden. All rights
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
 *
 */

package org.contikios.cooja;

import static se.sics.mspsim.Main.createNode;
import static se.sics.mspsim.Main.getNodeTypeByPlatform;

import ch.qos.logback.core.pattern.color.ANSIConstants;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.contikios.cooja.Cooja.Config;
import org.contikios.cooja.Cooja.LogbackColors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.util.ArgumentManager;

/**
 * Contains the command line parameters and is the main entry point for Cooja.
 */
@Command(version = {
        "Cooja " + Cooja.VERSION + ", Contiki-NG build interface version " + Cooja.CONTIKI_NG_BUILD_VERSION,
        "JVM: ${java.version} (${java.vendor} ${java.vm.name} ${java.vm.version})",
        "OS: ${os.name} ${os.version} ${os.arch}"}, sortOptions = false, sortSynopsis = false)
class Main {
  /**
   * Option for specifying if a GUI should be used.
   */
  @Option(names = "--gui", description = "use graphical mode", negatable = true)
  Boolean gui;

  /**
   * Option for specifying if the console log should be in color.
   */
  @Option(names = "--log-color", description = "use color in console log",
          defaultValue = "true", fallbackValue = "true", negatable = true)
  boolean logColor;

  /**
   * Option for specifying log directory.
   */
  @Option(names = "--logdir", paramLabel = "DIR", description = "the log directory use")
  String logDir = ".";

  /**
   * Option for specifying Nashorn arguments.
   */
  @Option(names = "--nashorn-args", paramLabel = "ARGS", description = "the Nashorn arguments")
  String nashornArgs = "--language=es6";

  /**
   * Option for specifying Contiki-NG path.
   */
  @Option(names = "--contiki", paramLabel = "DIR", description = "the Contiki-NG directory")
  String contikiPath;

  /**
   * Option for specifying Cooja path.
   */
  @Option(names = "--cooja", paramLabel = "DIR", description = "the Cooja directory")
  String coojaPath;

  /**
   * Option for specifying external user config file.
   */
  @Option(names = "--config", paramLabel = "FILE", description = "the filename for external user config")
  String externalUserConfig;

  /**
   * Option for specifying seed used for simulation.
   */
  @Option(names = "--random-seed", paramLabel = "SEED", description = "the random seed")
  Long randomSeed;

  /**
   * Automatically start simulations.
   */
  @Option(names = "--autostart", description = "automatically start simulations")
  boolean autoStart;

  /**
   * Option for specifying simulation files to load.
   */
  @Parameters(paramLabel = "FILE", description = "one or more simulation files")
  final List<String> simulationFiles = new ArrayList<>();

  /**
   * Option for instructing Cooja to update the simulation file (.csc).
   */
  @Option(names = "--update-simulation", description = "write an updated simulation file (.csc) and exit")
  boolean updateSimulation;

  /**
   * Option for instructing Cooja to print the expected Contiki-NG build version.
   */
  @Option(names = "--print-contiki-ng-build-version", description = "print Contiki-NG build version")
  boolean contikiNgBuildVersion;

  /**
   * Option for instructing Cooja to print the simulation config version.
   */
  @Option(names = "--print-simulation-config-version", description = "print simulation config version")
  boolean simulationConfigVersion;

  /**
   * Option for starting MSPSim with a platform.
   */
  @Option(names = "--platform", paramLabel = "ARCH", description = "MSPSim platform")
  String mspSimPlatform;

  @Option(names = "--version", versionHelp = true,
          description = "print version information and exit")
  boolean versionRequested;

  @Option(names = "--help", usageHelp = true, description = "display a help message")
  boolean helpRequested;

  public static void main(String[] args) {
    Main options = new Main();
    CommandLine commandLine = new CommandLine(options);
    try {
      commandLine.parseArgs(args);
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    if (options.helpRequested) {
      commandLine.usage(System.out);
      return;
    }
    if (options.versionRequested) {
      commandLine.printVersionHelp(System.out);
      return;
    }
    boolean quitEarly = false;
    if (options.contikiNgBuildVersion) {
      System.out.println(Cooja.CONTIKI_NG_BUILD_VERSION);
      quitEarly = true;
    }
    if (options.simulationConfigVersion) {
      System.out.println(Cooja.SIMULATION_CONFIG_VERSION);
      quitEarly = true;
    }
    if (quitEarly) {
      return;
    }
    options.gui = options.gui == null || options.gui;

    if (options.gui && GraphicsEnvironment.isHeadless()) {
      System.err.println("Trying to start GUI in headless environment, aborting");
      System.exit(1);
    }

    if (options.updateSimulation && !options.gui) {
      System.err.println("Can only update simulation with --gui");
      System.exit(1);
    }

    if (!options.logColor) {
      if (System.getProperty("logback.layoutPattern") != null
              || !"logback.xml".equals(System.getProperty("logback.configurationFile", "logback.xml"))) {
        System.err.println("Option for no log color can not be used together with custom logback configuration");
        System.exit(1);
      }
      System.setProperty("logback.layoutPattern", "%-5level [%thread] [%file:%line] - %msg%n");
    }

    if (!options.gui) {
      // Ensure no UI is used by Java
      System.setProperty("java.awt.headless", "true");
      Path logDirPath = Path.of(options.logDir);
      if (!Files.exists(logDirPath)) {
        try {
          Files.createDirectory(logDirPath);
        } catch (IOException e) {
          System.err.println("Could not create log directory '" + options.logDir + "'");
          System.exit(1);
        }
      }
    }

    var mspSim = options.mspSimPlatform != null;
    if (mspSim && options.simulationFiles.isEmpty()) {
      System.err.println("MSPSim: missing firmware name argument");
      System.exit(1);
    }

    // Parse and verify soundness of simulation files argument.
    ArrayList<Simulation.SimConfig> simConfigs = new ArrayList<>();
    for (var arg : options.simulationFiles) {
      // Argument on the form "file.csc[,key1=value1,key2=value2, ..]"
      var map = new HashMap<String, String>();
      String file = null;
      for (var item : arg.split(",", -1)) {
        if (file == null) {
          file = item;
          continue;
        }
        var pair = item.split("=", -1);
        if (pair.length != 2) {
          System.err.println("Faulty key=value specification: " + item);
          System.exit(1);
        }
        map.put(pair[0], pair[1]);
      }
      if (file == null) {
        System.err.println("Failed argument parsing of simulation file " + arg);
        System.exit(1);
      }
      if (!mspSim && !file.endsWith(".csc") && !file.endsWith(".csc.gz")) {
        System.err.println("Cooja expects simulation filenames to have an extension of '.csc' or '.csc.gz");
        System.exit(1);
      }
      if (!Files.exists(Path.of(file))) {
        System.err.println("File '" + file + "' does not exist");
        System.exit(1);
      }
      // MSPSim does not use the SimConfig record, so skip to next validation.
      if (mspSim) continue;
      var randomSeed = map.get("random-seed");
      var autoStart = map.getOrDefault("autostart", Boolean.toString(options.autoStart || !options.gui));
      var updateSim = map.getOrDefault("update-simulation", Boolean.toString(options.updateSimulation));
      var logDir = map.getOrDefault("logdir", options.logDir);
      simConfigs.add(new Simulation.SimConfig(file, randomSeed == null ? options.randomSeed : Long.decode(randomSeed),
              Boolean.parseBoolean(autoStart), Boolean.parseBoolean(updateSim), logDir, map));
    }

    if (options.contikiPath != null && !Files.exists(Path.of(options.contikiPath))) {
      System.err.println("Contiki-NG path '" + options.contikiPath + "' does not exist");
      System.exit(1);
    }

    if (options.coojaPath == null) {
      try {
        /* Find path to Cooja installation directory from code base */
        URI domain_uri = Cooja.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path path = Path.of(domain_uri).toAbsolutePath();
        File fp = path.toFile();
        if (fp.isFile()) {
          // Get the directory where the JAR file is placed
          path = path.getParent();
        }
        // Cooja JAR/classes are either in the dist or build directories, and we want the installation directory
        options.coojaPath = path.getParent().normalize().toString();
      } catch (Exception e) {
        System.err.println("Failed to detect Cooja path: " + e);
        System.err.println("Specify the path to Cooja with -cooja=PATH");
        System.exit(1);
      }
    }

    if (!options.coojaPath.endsWith("/")) {
      options.coojaPath += '/';
    }

    if (!Files.exists(Path.of(options.coojaPath))) {
      System.err.println("Cooja path '" + options.coojaPath + "' does not exist");
      System.exit(1);
    }

    if (options.mspSimPlatform == null) { // Start Cooja.
      // Use colors that are good on a dark background and readable on a white background.
      var colors = new LogbackColors(ANSIConstants.BOLD + "91", "96",
              ANSIConstants.GREEN_FG, ANSIConstants.DEFAULT_FG);
      var cfg = new Config(colors, options.gui, options.externalUserConfig,
                options.nashornArgs,
                options.logDir, options.contikiPath, options.coojaPath);
      Cooja.go(cfg, simConfigs);
    } else { // Start MSPSim.
      var config = new ArgumentManager(options.simulationFiles.toArray(new String[0]));
      GenericNode node = null;
      try {
        node = createNode(Objects.requireNonNullElseGet(config.getProperty("nodeType"), () ->
                getNodeTypeByPlatform(options.mspSimPlatform)), options.simulationFiles.get(0));
      } catch (IOException e) {
        System.err.println("IOException from createNode: " + e.getMessage());
        System.exit(1);
      }
      if (node == null) {
        System.err.println("MSPSim does not currently support the platform '" + options.mspSimPlatform + "'.");
        System.exit(1);
      }
      try {
        node.setupArgs(config);
      } catch (IOException e) {
        System.err.println("IOException from setupArgs: " + e.getMessage());
        System.exit(1);
      }
    }
  }
}
