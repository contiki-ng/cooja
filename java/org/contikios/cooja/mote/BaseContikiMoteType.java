/*
 * Copyright (c) 2022, RISE Research Institutes of Sweden AB.
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

package org.contikios.cooja.mote;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;

import java.awt.Container;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.interfaces.ContikiBeeper;
import org.contikios.cooja.contikimote.interfaces.ContikiButton;
import org.contikios.cooja.contikimote.interfaces.ContikiCFS;
import org.contikios.cooja.contikimote.interfaces.ContikiClock;
import org.contikios.cooja.contikimote.interfaces.ContikiEEPROM;
import org.contikios.cooja.contikimote.interfaces.ContikiLED;
import org.contikios.cooja.contikimote.interfaces.ContikiMoteID;
import org.contikios.cooja.contikimote.interfaces.ContikiPIR;
import org.contikios.cooja.contikimote.interfaces.ContikiRS232;
import org.contikios.cooja.contikimote.interfaces.ContikiRadio;
import org.contikios.cooja.contikimote.interfaces.ContikiVib;
import org.contikios.cooja.dialogs.AbstractCompileDialog;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.interfaces.Battery;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.motes.AbstractApplicationMoteType;
import org.contikios.cooja.mspmote.interfaces.Msp802154BitErrorRadio;
import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.mspmote.interfaces.MspClock;
import org.contikios.cooja.mspmote.interfaces.MspDebugOutput;
import org.contikios.cooja.mspmote.interfaces.MspDefaultSerial;
import org.contikios.cooja.mspmote.interfaces.MspLED;
import org.contikios.cooja.mspmote.interfaces.MspMoteID;
import org.contikios.cooja.mspmote.interfaces.MspSerial;
import org.contikios.cooja.mspmote.interfaces.SkyButton;
import org.contikios.cooja.mspmote.interfaces.SkyCoffeeFilesystem;
import org.contikios.cooja.mspmote.interfaces.SkyFlash;
import org.contikios.cooja.mspmote.interfaces.SkyTemperature;
import org.contikios.cooja.util.StringUtils;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The common parts of mote types based on compiled Contiki-NG targets.
 */
public abstract class BaseContikiMoteType extends AbstractApplicationMoteType {
  private static final Logger logger = LoggerFactory.getLogger(BaseContikiMoteType.class);

  /** Static translation map from name -> class for builtin interfaces. */
  protected static final Map<String, Class<? extends MoteInterface>> builtinInterfaces = Map.ofEntries(
          entry("org.contikios.cooja.interfaces.IPAddress", IPAddress.class),
          entry("org.contikios.cooja.interfaces.Position", Position.class),
          entry("org.contikios.cooja.interfaces.Battery", Battery.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiVib", ContikiVib.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiMoteID", ContikiMoteID.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiRS232", ContikiRS232.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiBeeper", ContikiBeeper.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiIPAddress", IPAddress.class), // Compatibility.
          entry("org.contikios.cooja.contikimote.interfaces.ContikiRadio", ContikiRadio.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiButton", ContikiButton.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiPIR", ContikiPIR.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiClock", ContikiClock.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiLED", ContikiLED.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiCFS", ContikiCFS.class),
          entry("org.contikios.cooja.contikimote.interfaces.ContikiEEPROM", ContikiEEPROM.class),
          entry("org.contikios.cooja.interfaces.Mote2MoteRelations", Mote2MoteRelations.class),
          entry("org.contikios.cooja.interfaces.MoteAttributes", MoteAttributes.class),
          entry("org.contikios.cooja.mspmote.interfaces.ESBLog", MspSerial.class), // Compatibility.
          entry("org.contikios.cooja.mspmote.interfaces.MspClock", MspClock.class),
          entry("org.contikios.cooja.mspmote.interfaces.MspDebugOutput", MspDebugOutput.class),
          entry("org.contikios.cooja.mspmote.interfaces.MspDefaultSerial", MspDefaultSerial.class),
          entry("org.contikios.cooja.mspmote.interfaces.MspIPAddress", IPAddress.class), // Compatibility.
          entry("org.contikios.cooja.mspmote.interfaces.MspLED", MspLED.class),
          entry("org.contikios.cooja.mspmote.interfaces.MspMoteID", MspMoteID.class),
          entry("org.contikios.cooja.mspmote.interfaces.MspSerial", MspSerial.class),
          entry("org.contikios.cooja.mspmote.interfaces.Msp802154Radio", Msp802154Radio.class),
          entry("org.contikios.cooja.mspmote.interfaces.Msp802154BitErrorRadio", Msp802154BitErrorRadio.class),
          entry("org.contikios.cooja.mspmote.interfaces.SkyButton", SkyButton.class),
          entry("org.contikios.cooja.mspmote.interfaces.SkyByteRadio", Msp802154Radio.class), // Compatibility.
          entry("org.contikios.cooja.mspmote.interfaces.SkyCoffeeFilesystem", SkyCoffeeFilesystem.class),
          entry("org.contikios.cooja.mspmote.interfaces.SkyFlash", SkyFlash.class),
          entry("org.contikios.cooja.mspmote.interfaces.SkyLED", MspLED.class), // Compatibility
          entry("org.contikios.cooja.mspmote.interfaces.SkySerial", MspSerial.class), // Compatibility.
          entry("org.contikios.cooja.mspmote.interfaces.SkyTemperature", SkyTemperature.class));

  // FIXME: combine fileSource and fileFirmware so only one can be active.
  /** Source file of the mote type. */
  protected File fileSource;
  /** Commands to compile the source into the firmware. */
  protected String compileCommands;
  /** Firmware of the mote type. */
  protected File fileFirmware;

  protected BaseContikiMoteType() {
    super(false);
  }

  /** Returns file name extension for firmware. */
  public abstract String getMoteType();

  /** Returns the mote type identifier prefix. Should not be overridden, special case for ContikiMoteType. */
  @Override
  public String getMoteTypeIdentifierPrefix() {
    return getMoteType();
  }

  /** Returns human-readable name for mote type. */
  public abstract String getMoteName();

  protected abstract String getMoteImage();

  public File getContikiSourceFile() {
    return fileSource;
  }

  public String getCompileCommands() {
    return compileCommands;
  }

  public File getContikiFirmwareFile() {
    return fileFirmware;
  }

  public File getExpectedFirmwareFile(String name) {
    String sourceNoExtension = new File(name).getName();
    if (sourceNoExtension.endsWith(".c")) {
      sourceNoExtension = sourceNoExtension.substring(0, sourceNoExtension.length() - 2);
    }
    return new File(new File(name).getParentFile(),
            "/build/" + getMoteType() + "/" + sourceNoExtension + '.' + getMoteType());
  }

  public abstract List<Class<? extends MoteInterface>> getAllMoteInterfaceClasses();
  public abstract List<Class<? extends MoteInterface>> getDefaultMoteInterfaceClasses();

  /** Target hook for adding additional information to view. */
  protected abstract void appendVisualizerInfo(StringBuilder sb);

  @Override
  public JComponent getTypeVisualizer() {
    StringBuilder sb = new StringBuilder();
    // Identifier.
    sb.append("<html><table><tr><td>Identifier</td><td>").append(getIdentifier()).append("</td></tr>");

    // Description.
    sb.append("<tr><td>Description</td><td>").append(getDescription()).append("</td></tr>");

    // Source.
    sb.append("<tr><td>Contiki source</td><td>");
    final var source = getContikiSourceFile();
    sb.append(source == null ? "[not specified]" : source.getAbsolutePath());
    sb.append("</td></tr>");

    // Firmware.
    sb.append("<tr><td>Contiki firmware</td><td>").append(getContikiFirmwareFile().getAbsolutePath()).append("</td></tr>");

    // Compile commands.
    String compileCommands = getCompileCommands();
    if (compileCommands == null) {
      compileCommands = "";
    }
    sb.append("<tr><td valign=\"top\">Compile commands</td><td>")
            .append(compileCommands.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")).append("</td></tr>");
    // Add target-specific information.
    appendVisualizerInfo(sb);
    // Construct the label and set its icon.
    JLabel label = new JLabel(sb.append("</table></html>").toString());
    label.setVerticalTextPosition(JLabel.TOP);
    Icon moteTypeIcon = getMoteTypeIcon();
    if (moteTypeIcon != null) {
      label.setIcon(moteTypeIcon);
    }
    return label;
  }

  private Icon getMoteTypeIcon() {
    String imageName = getMoteImage();
    if (imageName == null) {
      return null;
    }
    URL imageURL = getClass().getResource(imageName);
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

  protected ArrayList<Element> getBaseConfigXML(Simulation sim, boolean saveFirmware) {
    var config = new ArrayList<Element>();
    // Description.
    var element = new Element("description");
    element.setText(getDescription());
    config.add(element);

    // Source file.
    if (fileSource != null) {
      element = new Element("source");
      File file = sim.getCooja().createPortablePath(fileSource);
      element.setText(file.getPath().replaceAll("\\\\", "/"));
      config.add(element);
      element = new Element("commands");
      element.setText(compileCommands);
      config.add(element);
    }

    // Firmware file.
    if (saveFirmware) {
      element = new Element("firmware");
      File file = sim.getCooja().createPortablePath(fileFirmware);
      element.setText(file.getPath().replaceAll("\\\\", "/"));
      config.add(element);
    }

    // Mote interfaces.
    for (var moteInterface : moteInterfaceClasses) {
      element = new Element("moteinterface");
      element.setText(moteInterface.getName());
      config.add(element);
    }
    return config;
  }

  @Override
  public Collection<Element> getConfigXML(Simulation sim) {
    return getBaseConfigXML(sim, true);
  }

  protected boolean setBaseConfigXML(Simulation sim, Collection<Element> configXML) {
    for (Element element : configXML) {
      switch (element.getName()) {
        case "description" -> description = element.getText();
        case "contikiapp", "source" -> {
          fileSource = sim.getCooja().restorePortablePath(new File(element.getText()));
          fileFirmware = getExpectedFirmwareFile(fileSource.getName());
        }
        case "elf", "firmware" -> fileFirmware = sim.getCooja().restorePortablePath(new File(element.getText()));
        case "command", "commands" -> compileCommands = element.getText();
        case "moteinterface" -> {
          var name = element.getText().trim();
          if (name.startsWith("se.sics")) {
            name = name.replaceFirst("^se\\.sics", "org.contikios");
          }
          // Skip interfaces that have been removed or merged into other classes.
          if ("org.contikios.cooja.interfaces.RimeAddress".equals(name)) {
            continue;
          }
          var clazz = builtinInterfaces.get(name);
          if (clazz == null) {
            for (var moteInterfaceClass : getAllMoteInterfaceClasses()) {
              if (name.equals(moteInterfaceClass.getName())) {
                clazz = moteInterfaceClass;
                break;
              }
            }
          }
          if (clazz == null) {
            logger.error("Mote interface {} not available for {} mote", name, getMoteName());
            return false;
          }
          moteInterfaceClasses.add(clazz);
        }
        case "contikibasedir", "contikicoredir", "projectdir", "compilefile", "process", "sensor", "coreinterface" -> {
          logger.error("Old Cooja mote type detected, aborting..");
          return false;
        }
      }
    }
    if (moteInterfaceClasses.isEmpty()) { // Old MspMote simulation, or reconfigured simulation.
      moteInterfaceClasses.addAll(getDefaultMoteInterfaceClasses());
    }
    return true;
  }

  @Override
  public boolean configureAndInit(Container top, Simulation sim, boolean vis) throws MoteTypeCreationException {
    if (Cooja.isVisualized() && !sim.isQuickSetup()) {
      var currDesc = getDescription();
      var desc = currDesc == null ? getMoteName() + " Mote Type #" + (sim.getMoteTypes().length + 1) : currDesc;
      final var source = getContikiSourceFile();
      final var firmware = getContikiFirmwareFile();
      String file = source != null ? source.getAbsolutePath() : firmware != null ? firmware.getAbsolutePath() : null;
      var moteClasses = getMoteInterfaceClasses();
      var defaultClasses = getDefaultMoteInterfaceClasses();
      var cfg = showCompilationDialog(sim.getCooja(), new MoteTypeConfig(desc, getMoteType(), file,
              getCompileCommands(), moteClasses.isEmpty() ? defaultClasses : moteClasses, defaultClasses,
              getAllMoteInterfaceClasses()));
      if (cfg == null) {
        return false;
      }
      setDescription(cfg.desc);
      if (cfg.file.endsWith(".c")) {
        fileSource = new File(cfg.file);
        fileFirmware = getExpectedFirmwareFile(fileSource.getAbsolutePath());
      } else {
        fileFirmware = new File(cfg.file);
      }
      compileCommands = cfg.commands;
      moteInterfaceClasses.clear();
      moteInterfaceClasses.addAll(cfg.interfaces);
    } else {
      // Handle multiple compilation commands one by one.
      final var output = MessageContainer.createMessageList(vis);
      final var env = getCompilationEnvironment();
      for (String cmd : StringUtils.splitOnNewline(getCompileCommands())) {
        compile(cmd, env, fileSource.getParentFile(), null, null, output, true);
      }
    }
    return loadMoteFirmware(vis);
  }

  /** Load the mote firmware into memory. */
  public boolean loadMoteFirmware(boolean vis) throws MoteTypeCreationException {
    return true;
  }

  /** Compilation-relevant parts of mote type configuration. */
  public record MoteTypeConfig(String desc, String targetName, String file, String commands,
                               List<Class<? extends MoteInterface>> interfaces,
                               List<Class<? extends MoteInterface>> defaultInterfaces,
                               List<Class<? extends MoteInterface>> allInterfaces) {}

  /** Create a compilation dialog for this mote type. */
  protected abstract AbstractCompileDialog createCompilationDialog(Cooja gui, MoteTypeConfig cfg);

  /** Show a compilation dialog for this mote type. */
  protected MoteTypeConfig showCompilationDialog(Cooja gui, MoteTypeConfig cfg) {
    return new Cooja.RunnableInEDT<MoteTypeConfig>() {
      @Override
      public MoteTypeConfig work() {
        final var dialog = createCompilationDialog(gui, cfg);
        dialog.setVisible(true); // Blocks.
        return dialog.results();
      }
    }.invokeAndWait();
  }

  /** Return a compilation environment. */
  public LinkedHashMap<String, String> getCompilationEnvironment() {
    return null;
  }

  /**
   * Executes a Contiki compilation command.
   *
   * @param commandIn Command
   * @param env (Optional) Environment. May be null.
   * @param directory Directory in which to execute command
   * @param onSuccess Action called if compilation succeeds
   * @param onFailure Action called if compilation fails
   * @param messageDialog Is written both std and err process output
   * @param synchronous If true, method blocks until process completes
   * @return Sub-process if called asynchronously
   * @throws MoteTypeCreationException If process returns error, or outputFile does not exist
   */
  public static Process compile(
          final String commandIn,
          final Map<String, String> env,
          final File directory,
          final Action onSuccess,
          final Action onFailure,
          final MessageList messageDialog,
          boolean synchronous)
          throws MoteTypeCreationException {
    Pattern p = Pattern.compile("([^\\s\"']+|\"[^\"]*\"|'[^']*')");
    // Perform compile command variable expansions.
    String make = Cooja.getExternalToolsSetting("PATH_MAKE");
    String cpus = Integer.toString(Runtime.getRuntime().availableProcessors());
    Matcher m = p.matcher(commandIn.replace("$(MAKE)", make).replace("$(CPUS)", cpus));
    ArrayList<String> commandList = new ArrayList<>();
    while (m.find()) {
      String arg = m.group();
      if (arg.length() > 1 && (arg.charAt(0) == '"' || arg.charAt(0) == '\'')) {
        arg = arg.substring(1, arg.length() - 1);
      }
      commandList.add(arg);
    }
    messageDialog.addMessage("> " + String.join(" ", commandList), MessageList.NORMAL);
    final var pb = new ProcessBuilder(commandList).directory(directory);
    if (env != null) {
      var environment = pb.environment();
      environment.clear();
      environment.putAll(env);
    }
    final Process compileProcess;
    try {
      compileProcess = pb.start();
    } catch (IOException ex) {
      if (onFailure != null) {
        onFailure.actionPerformed(null);
      }
      throw new MoteTypeCreationException("Compilation error: " + ex.getMessage(), ex);
    }
    new Thread(() -> {
      try (var stdout = compileProcess.inputReader(UTF_8)) {
        String readLine;
        while ((readLine = stdout.readLine()) != null) {
          messageDialog.addMessage(readLine, MessageList.NORMAL);
        }
      } catch (IOException e) {
        logger.warn("Error while reading from process");
      }
    }, "read input stream thread").start();

    new Thread(() -> {
      try (var stderr = compileProcess.errorReader(UTF_8)) {
        String readLine;
        while ((readLine = stderr.readLine()) != null) {
          messageDialog.addMessage(readLine, MessageList.ERROR);
        }
      } catch (IOException e) {
        logger.warn("Error while reading from process");
      }
    }, "read error stream thread").start();

    final var compile = new Runnable() {
      @Override
      public void run() {
        try {
          compileProcess.waitFor();
        } catch (Exception e) {
          messageDialog.addMessage(e.getMessage(), MessageList.ERROR);
          return;
        }

        if (compileProcess.exitValue() != 0) {
          messageDialog.addMessage("Compilation process returned error code " + compileProcess.exitValue(), MessageList.ERROR);
          if (onFailure != null) {
            java.awt.EventQueue.invokeLater(() -> onFailure.actionPerformed(null));
          }
        } else if (onSuccess != null) {
          java.awt.EventQueue.invokeLater(() -> onSuccess.actionPerformed(null));
        }
      }
    };
    if (synchronous) {
      compile.run();
      if (compileProcess.exitValue() != 0) {
        throw new MoteTypeCreationException("Compilation failed", messageDialog);
      }
    } else {
      new Thread(compile, "handle compilation results").start();
    }
    return compileProcess;
  }
}
