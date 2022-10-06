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

import java.awt.Container;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.ProjectConfig;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.dialogs.AbstractCompileDialog;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.util.StringUtils;
import org.jdom.Element;

/**
 * The common parts of mote types based on compiled Contiki-NG targets.
 */
public abstract class BaseContikiMoteType implements MoteType {
  private static final Logger logger = LogManager.getLogger(BaseContikiMoteType.class);
  /** Description of the mote type. */
  protected String description = null;
  /** Identifier of the mote type. */
  protected String identifier = null;

  /** Project configuration of the mote type. */
  protected ProjectConfig projectConfig = null;
  // FIXME: combine fileSource and fileFirmware so only one can be active.
  /** Source file of the mote type. */
  protected File fileSource = null;
  /** Commands to compile the source into the firmware. */
  protected String compileCommands = null;
  /** Firmware of the mote type. */
  protected File fileFirmware = null;

  /** MoteInterface classes used by the mote type. */
  protected final ArrayList<Class<? extends MoteInterface>> moteInterfaceClasses = new ArrayList<>();

  /** Returns file name extension for firmware. */
  public abstract String getMoteType();

  /** Returns human-readable name for mote type. */
  public abstract String getMoteName();

  protected abstract String getMoteImage();

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  @Override
  public ProjectConfig getConfig() {
    return projectConfig;
  }

  @Override
  public File getContikiSourceFile() {
    return fileSource;
  }

  @Override
  public void setContikiSourceFile(File file) {
    fileSource = file;
  }

  @Override
  public String getCompileCommands() {
    return compileCommands;
  }

  @Override
  public void setCompileCommands(String commands) {
    this.compileCommands = commands;
  }

  @Override
  public File getContikiFirmwareFile() {
    return fileFirmware;
  }

  @Override
  public void setContikiFirmwareFile(File file) {
    fileFirmware = file;
  }

  public File getExpectedFirmwareFile(String name) {
    String sourceNoExtension = new File(name).getName();
    if (sourceNoExtension.endsWith(".c")) {
      sourceNoExtension = sourceNoExtension.substring(0, sourceNoExtension.length() - 2);
    }
    return new File(new File(name).getParentFile(),
            "/build/" + getMoteType() + "/" + sourceNoExtension + '.' + getMoteType());
  }

  /**
   * Returns make target based on source file name.
   *
   * @param name Name of source file.
   * @return Make target based on source file
   */
  public String getMakeTargetName(String name) {
    String sourceNoExtension = new File(name.substring(0, name.length() - 2)).getName();
    return sourceNoExtension + "." + getMoteType();
  }

  @Override
  public Class<? extends MoteInterface>[] getMoteInterfaceClasses() {
    if (moteInterfaceClasses.isEmpty()) {
      return null;
    }
    Class<? extends MoteInterface>[] arr = new Class[moteInterfaceClasses.size()];
    moteInterfaceClasses.toArray(arr);
    return arr;
  }

  @Override
  public void setMoteInterfaceClasses(Class<? extends MoteInterface>[] moteInterfaces) {
    moteInterfaceClasses.clear();
    moteInterfaceClasses.addAll(Arrays.asList(moteInterfaces));
  }

  public abstract Class<? extends MoteInterface>[] getAllMoteInterfaceClasses();
  public abstract Class<? extends MoteInterface>[] getDefaultMoteInterfaceClasses();

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

  protected boolean setBaseConfigXML(Simulation sim, Collection<Element> configXML)  {
    for (Element element : configXML) {
      switch (element.getName()) {
        case "identifier":
          identifier = element.getText();
          break;
        case "description":
          description = element.getText();
          break;
        case "contikiapp":
        case "source":
          fileSource = sim.getCooja().restorePortablePath(new File(element.getText()));
          fileFirmware = getExpectedFirmwareFile(fileSource.getName());
          break;
        case "elf":
        case "firmware":
          fileFirmware = sim.getCooja().restorePortablePath(new File(element.getText()));
          break;
        case "command":
        case "commands":
          compileCommands = element.getText();
          break;
        case "moteinterface":
          var name = element.getText().trim();
          var clazz = MoteInterfaceHandler.getInterfaceClass(sim.getCooja(), this, name);
          if (clazz == null) {
            logger.warn("Can't find mote interface class: " + name);
            return false;
          }
          moteInterfaceClasses.add(clazz);
          break;
        case "contikibasedir":
        case "contikicoredir":
        case "projectdir":
        case "compilefile":
        case "process":
        case "sensor":
        case "coreinterface":
          logger.fatal("Old Cooja mote type detected, aborting..");
          return false;
      }
    }
    return true;
  }

  @Override
  public boolean configureAndInit(Container top, Simulation sim, boolean vis) throws MoteTypeCreationException {
    if (vis && !sim.isQuickSetup()) {
      if (getDescription() == null) {
        setDescription(getMoteName() + " Mote Type #" + (sim.getMoteTypes().length + 1));
      }

      final var source = getContikiSourceFile();
      final var firmware = getContikiFirmwareFile();
      String file = source != null ? source.getAbsolutePath() : firmware != null ? firmware.getAbsolutePath() : null;
      var moteClasses = getMoteInterfaceClasses();
      var interfaces = moteClasses == null ? getDefaultMoteInterfaceClasses() : moteClasses;
      if (!showCompilationDialog(sim, new MoteTypeConfig(getDescription(), file, getCompileCommands(), interfaces))) {
        return false;
      }
    } else {
      // Handle multiple compilation commands one by one.
      final var output = MessageContainer.createMessageList(vis);
      final var env = getCompilationEnvironment();
      for (String cmd : StringUtils.splitOnNewline(getCompileCommands())) {
        try {
          compile(cmd, env, fileSource.getParentFile(), null, null, output, true);
        } catch (MoteTypeCreationException e) {
          return false;
        }
      }
    }
    return loadMoteFirmware(vis);
  }

  /** Load the mote firmware into memory. */
  public boolean loadMoteFirmware(boolean vis) throws MoteTypeCreationException {
    return true;
  }

  /** Compilation-relevant parts of mote type configuration. */
  public record MoteTypeConfig(String desc, String file, String commands, Class<? extends MoteInterface>[] interfaces) {}

  /** Create a compilation dialog for this mote type. */
  protected abstract AbstractCompileDialog createCompilationDialog(Simulation sim, MoteTypeConfig cfg);

  /** Show a compilation dialog for this mote type. */
  protected boolean showCompilationDialog(Simulation sim, MoteTypeConfig cfg) {
    final var dialog = createCompilationDialog(sim, cfg);
    dialog.setVisible(true); // Blocks.
    return dialog.createdOK();
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
    String cpus = Integer.toString(Runtime.getRuntime().availableProcessors());
    Matcher m = p.matcher(commandIn.replace("$(CPUS)", cpus));
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
      try (var stdout = new BufferedReader(new InputStreamReader(compileProcess.getInputStream(), UTF_8))) {
        String readLine;
        while ((readLine = stdout.readLine()) != null) {
          messageDialog.addMessage(readLine, MessageList.NORMAL);
        }
      } catch (IOException e) {
        logger.warn("Error while reading from process");
      }
    }, "read input stream thread").start();

    new Thread(() -> {
      try (var stderr = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream(), UTF_8))) {
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
      // Errors are already printed to messageDialog, so just throw a non-descriptive exception on error.
      if (compileProcess.exitValue() != 0) {
        throw new MoteTypeCreationException("Compilation failed");
      }
    } else {
      new Thread(compile, "handle compilation results").start();
    }
    return compileProcess;
  }
}
