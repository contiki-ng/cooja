/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * -----------------------------------------------------------------
 *
 * GenericNode
 *
 * Author  : Joakim Eriksson
 */

package se.sics.mspsim.platform;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import javax.swing.JFrame;
import se.sics.mspsim.cli.CommandHandler;
import se.sics.mspsim.cli.DebugCommands;
import se.sics.mspsim.cli.FileCommands;
import se.sics.mspsim.cli.MiscCommands;
import se.sics.mspsim.cli.NetCommands;
import se.sics.mspsim.cli.ProfilerCommands;
import se.sics.mspsim.cli.StreamCommandHandler;
import se.sics.mspsim.cli.WindowCommands;
import se.sics.mspsim.core.Chip;
import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.MSP430Config;
import se.sics.mspsim.core.MSP430Constants;
import se.sics.mspsim.extutil.highlight.HighlightSourceViewer;
import se.sics.mspsim.ui.ConsoleUI;
import se.sics.mspsim.ui.ControlUI;
import se.sics.mspsim.ui.JFrameWindowManager;
import se.sics.mspsim.ui.StackUI;
import se.sics.mspsim.ui.WindowUtils;
import se.sics.mspsim.util.ArgumentManager;
import se.sics.mspsim.util.ComponentRegistry;
import se.sics.mspsim.util.ConfigManager;
import se.sics.mspsim.util.ELF;
import se.sics.mspsim.util.IHexReader;
import se.sics.mspsim.util.MapTable;
import se.sics.mspsim.util.OperatingModeStatistics;
import se.sics.mspsim.util.PluginRepository;
import se.sics.mspsim.util.StatCommands;

public abstract class GenericNode extends Chip implements Runnable {

  private static final String PROMPT = "MSPSim>";

  protected final MSP430 cpu;
  protected final ComponentRegistry registry;
  protected ConfigManager config;

  protected String firmwareFile;
  protected final OperatingModeStatistics stats;

  public static MSP430 makeCPU(MSP430Config config, String firmwareFile) throws IOException {
    ELF elf = null;
    int[] memory;
    if (firmwareFile.endsWith("ihex")) { // IHEX Reading.
      memory = IHexReader.readFile(firmwareFile, config.maxMem);
    } else {
      elf = ELF.readELF(firmwareFile);
      memory = elf.loadPrograms(config.maxMem);
    }
    return new MSP430(config, memory, elf);
  }

  public GenericNode(String id, MSP430 cpu) {
    super(id, cpu);
    this.cpu = cpu;
    stats = new OperatingModeStatistics(cpu);
    this.registry = cpu.getRegistry();
    registry.registerComponent("node", this);
  }

  public ComponentRegistry getRegistry() {
    return registry;
  }

  public MSP430 getCPU() {
    return cpu;
  }

  public abstract void setupNode();

  public void setCommandHandler(CommandHandler handler) {
    registry.registerComponent("commandHandler", handler);
  }

  public void setupArgs(ArgumentManager config) throws IOException {
    String[] args = config.getArguments();
    firmwareFile = args[0];
    if (config.getProperty("nogui") == null) {
      config.setProperty("nogui", "false");
    }
    /* Ensure auto-run of a start script */
    if (config.getProperty("autorun") == null) {
      File fp = new File("config/scripts/autorun.sc");
      if (!fp.exists()) {
        File parent;
        try {
          parent = new File(GenericNode.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getParentFile();
        } catch (URISyntaxException e) {
          parent = null;
        }
        if (parent != null) {
          var autoRunScript = "resources/main/scripts/autorun.sc";
          fp = new File(parent, autoRunScript);
          if (!fp.exists()) { // Running from gradle, outside project dir.
            fp = new File(parent.getParentFile(), autoRunScript);
          }
        }
      }
      if (fp.exists()) {
        config.setProperty("autorun", fp.getAbsolutePath());
      }
    }
    config.setProperty("firmwareFile", firmwareFile);

    String mapFile = config.getProperty("map");
    if (mapFile != null) {
      MapTable map = new MapTable(mapFile);
      cpu.getDisAsm().setMap(map);
      cpu.setMap(map);
      registry.registerComponent("mapTable", map);
    }

    setup(config);


    if (!config.getPropertyAsBoolean("nogui", false)) {
      // Setup control and other UI components
      ControlUI control = new ControlUI();
      registry.registerComponent("controlgui", control);
      registry.registerComponent("stackchart", new StackUI(cpu));
      HighlightSourceViewer sourceViewer = new HighlightSourceViewer();
      // Add the firmware location to the search path
      File fp = new File(firmwareFile).getParentFile();
      if (fp != null) {
          try {
              // Get absolute path
              fp = fp.getCanonicalFile();
          } catch (Exception e) {
              // Ignore
          }
          sourceViewer.addSearchPath(fp);
      }
      control.setSourceViewer(sourceViewer);
    }

    String script = config.getProperty("autorun");
    if (script != null) {
      File fp = new File(script);
      if (fp.canRead()) {
        CommandHandler ch = registry.getComponent(CommandHandler.class, "commandHandler");
        script = script.replace('\\', '/');
        System.out.println("Autoloading script: " + script);
        config.setProperty("autoloadScript", script);
        if (ch != null) {
          ch.lineRead("source \"" + script + '"');
        }
      }
    }

    if (args.length > 1) {
        // Run the following arguments as commands
        CommandHandler ch = registry.getComponent(CommandHandler.class, "commandHandler");
        if (ch != null) {
            for (int i = 1; i < args.length; i++) {
                System.out.println("calling '" + args[i] + "'");
                ch.lineRead(args[i]);
            }
        }
    }
    System.out.println("-----------------------------------------------");
    System.out.println("MSPSim " + MSP430Constants.VERSION + " starting firmware: " + firmwareFile);
    System.out.println("-----------------------------------------------");
    System.out.print(PROMPT);
    System.out.flush();
  }

  public void setup(ConfigManager config) {
    this.config = config;
    registry.registerComponent("config", config);

    CommandHandler ch = registry.getComponent(CommandHandler.class, "commandHandler");

    if (ch == null && config.getPropertyAsBoolean("cli", true)) {
        if (config.getPropertyAsBoolean("jconsole", false)) {
            ConsoleUI console = new ConsoleUI();
            PrintStream consoleStream = new PrintStream(console.getOutputStream());
            ch = new CommandHandler(consoleStream, consoleStream);
            JFrame w = new JFrame("ConsoleUI");
            w.add(console);
            w.setBounds(20, 20, 520, 400);
            w.setLocationByPlatform(true);
            String key = "console";
            WindowUtils.restoreWindowBounds(key, w);
            WindowUtils.addSaveOnShutdown(key, w);
            w.setVisible(true);
            console.setCommandHandler(ch);
        } else {
            ch = new StreamCommandHandler(System.in, System.out, System.err, PROMPT);
        }
        registry.registerComponent("commandHandler", ch);
    }

    registry.registerComponent("pluginRepository", new PluginRepository());
    registry.registerComponent("debugcmd", new DebugCommands());
    registry.registerComponent("misccmd", new MiscCommands());
    registry.registerComponent("filecmd", new FileCommands());
    registry.registerComponent("statcmd", new StatCommands(cpu, stats));
    registry.registerComponent("wincmd", new WindowCommands());
    registry.registerComponent("profilecmd", new ProfilerCommands());
    registry.registerComponent("netcmd", new NetCommands());
    registry.registerComponent("windowManager", new JFrameWindowManager());

    // Monitor execution
    cpu.setMonitorExec(true);

    setupNode();

    registry.start();

    cpu.reset();
  }


  @Override
  public void run() {
    if (!cpu.isRunning()) {
      try {
        cpu.cpuloop();
      } catch (Exception e) {
        System.err.println("Exception in cpu loop: " + e.getMessage());
      }
    }
  }

  public void start() {
    if (!cpu.isRunning()) {
      Thread thread = new Thread(this, "GenericNode.start");
      // Set this thread to normal priority in case the start method was called
      // from the higher priority AWT thread.
      thread.setPriority(Thread.NORM_PRIORITY);
      thread.start();
    }
  }

  public void stop() {
    cpu.stop();
  }

  public void step() throws EmulationException {
    step(1);
  }

  // A step that will break out of breakpoints!
  public void step(int nr) throws EmulationException {
    if (!cpu.isRunning()) {
      cpu.stepInstructions(nr);
    }
  }
}
