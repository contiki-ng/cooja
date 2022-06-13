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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Contains the command line parameters and is the main entry point for Cooja.
 */
@Command(version = {
        "Cooja " + Cooja.VERSION,
        "JVM: ${java.version} (${java.vendor} ${java.vm.name} ${java.vm.version})",
        "OS: ${os.name} ${os.version} ${os.arch}"})
class Main {
  /**
   * Option for specifying log4j2 config file.
   */
  @Option(names = "-log4j2", paramLabel = "FILE", description = "the log4j2 config file")
  String logConfigFile;

  /**
   * Option for specifying log directory.
   */
  @Option(names = "-logdir", paramLabel = "DIR", description = "the log directory use")
  String logDir = ".";

  /**
   * Option for specifying basename of logs.
   */
  @Option(names = "-logname", paramLabel = "NAME", description = "the filename for the log")
  String logName = "COOJA.log";

  /**
   * Option for specifying Contiki-NG path.
   */
  @Option(names = "-contiki", paramLabel = "DIR", description = "the Contiki-NG directory")
  String contikiPath;

  /**
   * Option for specifying Cooja path.
   */
  @Option(names = "-cooja", paramLabel = "DIR", description = "the Cooja directory")
  String coojaPath;

  /**
   * Option for specifying external config file of tools.
   */
  @Option(names = "-external_tools_config", paramLabel = "FILE", description = "the filename for external config")
  String externalToolsConfig;

  /**
   * Option for specifying seed used for simulation.
   */
  @Option(names = "-random-seed", paramLabel = "SEED", description = "the random seed")
  Long randomSeed;

  /**
   * The action to take after starting. No action means start GUI.
   */
  @ArgGroup(exclusive = true)
  ExclusiveAction action;

  /**
   * Helper class to encode mutual exclusion between -nogui and -quickstart.
   */
  static class ExclusiveAction {
    /**
     * Option for specifying file to start the simulation with.
     */
    @Option(names = "-quickstart", paramLabel = "FILE", description = "start simulation with file")
    String quickstart;

    /**
     * Option for specifying file to start the simulation with.
     */
    @Option(names = "-nogui", paramLabel = "FILE", description = "start simulation with file")
    String nogui;
  }

  @Option(names = "--version", versionHelp = true,
          description = "print version information and exit")
  boolean versionRequested;

  @Option(names = {"-h", "--help"}, usageHelp = true, description = "display a help message")
  private boolean helpRequested;

  public static void main(String[] args) {
    Main options = new Main();
    CommandLine commandLine = new CommandLine(options);
    try {
      commandLine.parseArgs(args);
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    if (commandLine.isUsageHelpRequested()) {
      commandLine.usage(System.out);
      return;
    }
    if (commandLine.isVersionHelpRequested()) {
      commandLine.printVersionHelp(System.out);
      return;
    }

    if (options.action != null && options.action.quickstart == null &&
        options.action.nogui == null && GraphicsEnvironment.isHeadless()) {
      System.err.println("Trying to start GUI in headless environment, aborting");
      System.exit(1);
    }

    if (options.logConfigFile != null && !Files.exists(Path.of(options.logConfigFile))) {
      System.err.println("Configuration file '" + options.logConfigFile + "' does not exist");
      System.exit(1);
    }

    if (options.externalToolsConfig != null && !Files.exists(Path.of(options.externalToolsConfig))) {
      System.err.println("Specified external tools configuration '" + options.externalToolsConfig + "' not found");
      System.exit(1);
    }

    if (options.contikiPath != null && !Files.exists(Path.of(options.contikiPath))) {
      System.err.println("Contiki-NG path '" + options.contikiPath + "' does not exist");
      System.exit(1);
    }

    if (options.coojaPath == null) {
      try {
        /* Find path to Cooja installation directory from code base */
        URI domain_uri = Cooja.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path path = Paths.get(domain_uri).toAbsolutePath();
        File fp = path.toFile();
        if (fp.isFile()) {
          // Get the directory where the JAR file is placed
          path = path.getParent();
        }
        // Cooja JAR/classes are either in the dist or build directories and we want the installation directory
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

    if (!options.logName.endsWith(".log")) {
      options.logName += ".log";
    }

    // Configure logger
    if (options.logConfigFile == null) {
      ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
      builder.setStatusLevel(Level.INFO);
      builder.setConfigurationName("DefaultConfig");
      builder.add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.NEUTRAL)
              .addAttribute("level", Level.INFO));
      // Configure console appender.
      AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE")
              .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
      appenderBuilder.add(builder.newLayout("PatternLayout")
              .addAttribute("pattern", "%5p [%t] (%F:%L) - %m%n"));
      appenderBuilder.add(builder.newFilter("MarkerFilter", Filter.Result.DENY, Filter.Result.NEUTRAL)
              .addAttribute("marker", "FLOW"));
      builder.add(appenderBuilder);
      builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG)
              .add(builder.newAppenderRef("Stdout")).addAttribute("additivity", false));
      // Configure logfile file appender.
      appenderBuilder = builder.newAppender("File", "FILE")
              .addAttribute("fileName", options.logDir + "/" + options.logName)
              .addAttribute("Append", "false");
      appenderBuilder.add(builder.newLayout("PatternLayout")
              .addAttribute("pattern", "[%d{HH:mm:ss} - %t] [%F:%L] [%p] - %m%n"));
      appenderBuilder.add(builder.newFilter("MarkerFilter", Filter.Result.DENY, Filter.Result.NEUTRAL)
              .addAttribute("marker", "FLOW"));
      builder.add(appenderBuilder);
      builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG)
              .add(builder.newAppenderRef("File")).addAttribute("additivity", false));
      // Construct the root logger and initialize the configurator
      builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("Stdout"))
              .add(builder.newAppenderRef("File")));
      // FIXME: This should be try (LoggerContext cxt = Configurator.initialize(..)),
      //        but go immediately returns which causes the log file to be closed
      //        while the simulation is still running.
      Configurator.initialize(builder.build());
      Cooja.go(options);
    } else {
      Configurator.initialize("ConfigFile", options.logConfigFile);
      Cooja.go(options);
    }
  }
}
