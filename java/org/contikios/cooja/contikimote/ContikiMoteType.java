/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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
package org.contikios.cooja.contikimote;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.ProjectConfig;
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
import org.contikios.cooja.dialogs.ContikiMoteCompileDialog;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.interfaces.Battery;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mote.BaseContikiMoteType;
import org.contikios.cooja.mote.memory.ArrayMemory;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.mote.memory.MemoryInterface.Symbol;
import org.contikios.cooja.mote.memory.MemoryLayout;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Cooja mote type holds the native library used to communicate with an
 * underlying Contiki system. All communication with that system should always
 * pass through this mote type.
 * <p>
 * This type also contains information about sensors and mote interfaces a mote
 * of this type has.
 * <p>
 * All core communication with the Cooja mote should be via this class. When a
 * mote type is created it allocates a CoreComm to be used with this type, and
 * loads the variable and segments addresses.
 * <p>
 * When a new mote type is created an initialization function is run on the
 * Contiki system in order to create the initial memory. When a new mote is
 * created the createInitialMemory() method should be called to get this initial
 * memory for the mote.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Cooja mote")
@AbstractionLevelDescription("OS level")
// Do not bother end-user with warnings about internal Cooja details.
@SuppressWarnings("preview")
public class ContikiMoteType extends BaseContikiMoteType {
  private static final Logger logger = LoggerFactory.getLogger(ContikiMoteType.class);
  // Shared Arena since MoteTypes are allocated/removed in different threads.
  private final Arena arena = Arena.openShared();
  /**
   * Communication stacks in Contiki.
   */
  public enum NetworkStack {

    DEFAULT, MANUAL;
    public String manualHeader = "netstack-conf-example.h";

    @Override
    public String toString() {
      if (this == DEFAULT) {
        return "Default (from contiki-conf.h)";
      } else if (this == MANUAL) {
        return "Manual netstack header";
      }
      return "[unknown]";
    }

    public String getHeaderFile() {
      return this == MANUAL ? manualHeader : null;
    }

    String getConfig() {
      if (this == DEFAULT) {
        return "DEFAULT";
      } else if (this == MANUAL) {
        return "MANUAL:" + manualHeader;
      }
      return "[unknown]";
    }

    static NetworkStack parseConfig(String config) {
      if (config.startsWith("MANUAL")) {
        NetworkStack st = MANUAL;
        st.manualHeader = config.split(":")[1];
        return st;
      }
      if (!config.equals("DEFAULT")) {
        logger.warn("Bad network stack config: '" + config + "', using default");
      }
      return DEFAULT;
    }
  }

  private NetworkStack netStack = NetworkStack.DEFAULT;

  // Type specific class configuration

  private CoreComm myCoreComm;

  // Initial memory for all motes of this type
  private SectionMoteMemory initialMemory;

  /**
   * Creates a new uninitialized Cooja mote type. This mote type needs to load
   * a library file and parse a map file before it can be used.
   */
  public ContikiMoteType(Cooja gui) {
    myConfig = new ProjectConfig(gui.getProjectConfig());
  }

  @Override
  public String getMoteType() {
    return "cooja";
  }

  @Override
  public String getMoteTypeIdentifierPrefix() {
    // The "mtype" prefix for ContikiMoteType is hardcoded elsewhere, so use that instead of "cooja".
    return "mtype";
  }

  @Override
  public String getMoteName() {
    return "Cooja";
  }

  @Override
  protected String getMoteImage() {
    return null;
  }

  @Override
  public Mote generateMote(Simulation simulation) throws MoteTypeCreationException {
    return new ContikiMote(this, simulation);
  }

  @Override
  public LinkedHashMap<String, String> getCompilationEnvironment() {
    // Create the compilation environment.
    var env = new LinkedHashMap<String, String>();
    env.put("LIBNAME", "$(BUILD_DIR_BOARD)/" + getIdentifier() + ".cooja");
    env.put("COOJA_VERSION",  Cooja.CONTIKI_NG_BUILD_VERSION);
    env.put("CC", Cooja.getExternalToolsSetting("PATH_C_COMPILER"));
    var ccFlags = Cooja.getExternalToolsSetting("COMPILER_ARGS");
    if (ccFlags != null) {
      env.put("EXTRA_CC_ARGS", ccFlags);
    }
    env.put("PATH", System.getenv("PATH"));
    // Pass through environment variables for the Contiki-NG CI.
    String ci = System.getenv("CI");
    if (ci != null) {
      env.put("CI", ci);
    }
    String relstr = System.getenv("RELSTR");
    if (relstr != null) {
      env.put("RELSTR", relstr);
    }
    String quiet = System.getenv("QUIET");
    if (quiet != null) {
      env.put("QUIET", quiet);
    }
    return env;
  }

  @Override
  protected AbstractCompileDialog createCompilationDialog(Cooja gui, MoteTypeConfig cfg) {
    return new ContikiMoteCompileDialog(gui, this, cfg);
  }

  private static MemoryInterface getMemory(long addr, int size, Map<String, Symbol> variables) {
    return new ArrayMemory(addr, MemoryLayout.getNative(), new byte[size], variables);
  }

  /** Load LibN.java and the corresponding .cooja file into memory. */
  @Override
  public boolean loadMoteFirmware(boolean vis) throws MoteTypeCreationException {
    if (myCoreComm != null) {
      throw new MoteTypeCreationException("Core communicator already used: " + myCoreComm.getClass().getName());
    }
    /* Parse addresses using map file
     * or output of command specified in external tools settings (e.g. nm -a )
     */
    boolean useCommand = Boolean.parseBoolean(Cooja.getExternalToolsSetting("PARSE_WITH_COMMAND", "false"));
    // Allocate core communicator class
    final var firmwareFile = getContikiFirmwareFile();
    myCoreComm = new CoreComm(arena.scope(), firmwareFile, useCommand);

    var command = Cooja.getExternalToolsSetting(useCommand ? "PARSE_COMMAND" : "READELF_COMMAND");
    if (command != null) {
      command = Cooja.resolvePathIdentifiers(command);
    }
    if (command == null) {
      throw new MoteTypeCreationException("No " + (useCommand ? "parse" : "readelf") + " command configured!");
    }
    command = command.replace("$(LIBFILE)", firmwareFile.getName().replace(File.separatorChar, '/'));

    SectionParser dataSecParser;
    SectionParser bssSecParser = null;
    SectionParser commonSecParser = null;

    if (useCommand) {
      String[] output = loadCommandData(command, firmwareFile, vis);
      // FIXME: COMMAND_VAR_SEC_DATA & friends cannot be configured by
      //         the user, hardcode the values here instead.
      dataSecParser = new CommandSectionParser(output,
              Cooja.getExternalToolsSetting("COMMAND_VAR_SEC_DATA"));
      bssSecParser = new CommandSectionParser(output,
              Cooja.getExternalToolsSetting("COMMAND_VAR_SEC_BSS"));
      commonSecParser = new CommandSectionParser(output,
              Cooja.getExternalToolsSetting("COMMAND_VAR_SEC_COMMON"));
    } else {
      var symbols = String.join("\n", loadCommandData(command, firmwareFile, vis));
      dataSecParser = new MapSectionParser(symbols);
    }

    /* We first need the value of Contiki's referenceVar, which tells us the
     * memory offset between Contiki's variable and the relative addresses that
     * were calculated directly from the library file.
     *
     * This offset will be used in Cooja in the memory abstraction to match
     * Contiki's and Cooja's address spaces */
    Map<String, Symbol> variables = dataSecParser.parseSymbols(null);
    if (bssSecParser != null) {
      variables = bssSecParser.parseSymbols(variables);
    }
    if (commonSecParser != null) {
      variables = commonSecParser.parseSymbols(variables);
    }
    long offset;
    try {
      offset = myCoreComm.getReferenceAddress() - variables.get("referenceVar").addr;
    } catch (Exception e) {
      throw new MoteTypeCreationException("Error setting reference variable: " + e.getMessage(), e);
    }
    logger.debug(firmwareFile.getName() + ": offsetting Cooja mote address space: 0x" + Long.toHexString(offset));

    // Create initial memory: data+bss+optional common.
    var offsetVariables = new HashMap<String, Symbol>();
    for (var entry : variables.entrySet()) {
      var old = entry.getValue();
      offsetVariables.put(entry.getKey(), new Symbol(old.type, old.name, old.addr + offset, old.size));
    }
    initialMemory = new SectionMoteMemory(offsetVariables);
    initialMemory.addMemorySection("data",
            getMemory(myCoreComm.getDataStartAddress(), myCoreComm.getDataSize(), offsetVariables));
    initialMemory.addMemorySection("bss",
            getMemory(myCoreComm.getBssStartAddress(), myCoreComm.getBssSize(), offsetVariables));
    if (commonSecParser != null) {
      initialMemory.addMemorySection("common",
              getMemory(myCoreComm.getCommonStartAddress(), myCoreComm.getCommonSize(), offsetVariables));
    }
    getCoreMemory(initialMemory);
    return true;
  }

  @Override
  public List<Class<? extends MoteInterface>> getAllMoteInterfaceClasses() {
    return List.of(Position.class,
            Battery.class,
            ContikiVib.class,
            ContikiMoteID.class,
            ContikiRS232.class,
            ContikiBeeper.class,
            IPAddress.class,
            ContikiRadio.class,
            ContikiButton.class,
            ContikiPIR.class,
            ContikiClock.class,
            ContikiLED.class,
            ContikiCFS.class,
            ContikiEEPROM.class,
            Mote2MoteRelations.class,
            MoteAttributes.class);
  }

  @Override
  public List<Class<? extends MoteInterface>> getDefaultMoteInterfaceClasses() {
    return getAllMoteInterfaceClasses();
  }

  @Override
  public File getExpectedFirmwareFile(String name) {
    return new File(new File(name).getParentFile(), "build/cooja/" + identifier + "." + getMoteType());
  }

  /**
   * Abstract base class for concrete section parser class.
   */
  interface SectionParser {
    Map<String, Symbol> parseSymbols(Map<String, Symbol> inVars);
  }

  /**
   * Parses Map file for section data.
   */
  private static class MapSectionParser implements SectionParser {
    private final String readelfData;

    MapSectionParser(String readelfData) {
      this.readelfData = readelfData;
    }

    @Override
    public Map<String, Symbol> parseSymbols(Map<String, Symbol> inVars) {
      Map<String, Symbol> varNames = new HashMap<>();
      try (var s = new Scanner(readelfData)) {
        s.nextLine(); // Skip first blank line.
        while (s.hasNext()) {
          var symbolNum = s.next();
          if (!symbolNum.endsWith(":") || "Num:".equals(symbolNum)) {
            s.nextLine(); // Skip until line starts with "1:" token.
            continue;
          }
          // Scanner.nextLong() is really slow, get the next token and parse it.
          var addr = Long.parseLong(s.next(), 16);
          // Size is output in decimal if below 100000, hex otherwise. The command line option --sym-base=10 gives
          // a decimal output, but readelf 2.34 does not have the option.
          var sizeString = s.next();
          var hex = sizeString.startsWith("0x");
          var size = Integer.parseInt(hex ? sizeString.substring(2) : sizeString, hex ? 16 : 10);
          var type = s.next();
          if (!"OBJECT".equals(type)) {
            s.nextLine(); // Skip lines that do not define variables.
            continue;
          }
          // Skip 3 tokens that are not required.
          s.next();
          s.next();
          s.next();
          var name = s.next();
          varNames.put(name, new Symbol(Symbol.Type.VARIABLE, name, addr, size));
        }
      }
      return varNames;
    }

  }

  /**
   * Parses command output for section data.
   */
  private static class CommandSectionParser implements SectionParser {
    private final String[] mapFileData;
    private final String sectionRegExp;

    /**
     * Creates SectionParser based on output of configurable command.
     *
     * @param mapFileData Map file lines as array of String
     * @param sectionRegExp Regular expression describing symbol table section identifier (e.g. '[Rr]' for readonly)
     *        Will be used to replaced '<SECTION>'in 'COMMAND_VAR_NAME_ADDRESS_SIZE'
     */
    CommandSectionParser(String[] mapFileData, String sectionRegExp) {
      this.mapFileData = mapFileData;
      this.sectionRegExp = sectionRegExp;
    }

    @Override
    public Map<String, Symbol> parseSymbols(Map<String, Symbol> inVars) {
      if (inVars != null) {
        return inVars;
      }
      HashMap<String, Symbol> addresses = new HashMap<>();
      /* Replace "<SECTION>" in regex by section specific regex */
      Pattern pattern = Pattern.compile(
              Cooja.getExternalToolsSetting("COMMAND_VAR_NAME_ADDRESS_SIZE")
                      .replace("<SECTION>", Pattern.quote(sectionRegExp)));
      for (String line : mapFileData) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
          /* Line matched variable address */
          String symbol = matcher.group("symbol");
          long varAddr = Long.parseUnsignedLong(matcher.group("address"), 16);
          int varSize;

          if (matcher.group(2) != null) {
           varSize = Integer.parseInt(matcher.group("size"), 16);
          } else {
            varSize = -1;
          }

          /* XXX needs to be checked */
          if (!addresses.containsKey(symbol)) {
	    logger.debug("Put symbol " + symbol + " with address " + varAddr + " and size " + varSize);
            addresses.put(symbol, new Symbol(Symbol.Type.VARIABLE, symbol, varAddr, varSize));
          }
        }
      }

      return addresses;
    }
  }

  /**
   * Ticks the currently loaded mote. This should not be used directly, but
   * rather via {@link ContikiMote#execute(long)}.
   */
  void tick() {
    myCoreComm.tick();
  }

  /**
   * Creates and returns a copy of this mote type's initial memory (just after
   * the init function has been run). When a new mote is created it should get
   * its memory from here.
   *
   * @return Initial memory of a mote type
   */
  SectionMoteMemory createInitialMemory() {
    return initialMemory.clone();
  }

  /**
   * Copy core memory to given memory. This should not be used directly, but
   * instead via ContikiMote.getMemory().
   *
   * @param mem
   *          Memory to set
   */
  static void getCoreMemory(SectionMoteMemory mem) {
    for (var sec : mem.getSections().values()) {
      int length = sec.getTotalSize();
      MemorySegment.ofAddress(sec.getStartAddr(), length, SegmentScope.global())
              .asByteBuffer().get(0, sec.getMemory(), 0, length);
    }
  }

  /**
   * Copy given memory to the Contiki system.
   *
   * @param mem
   * New memory
   */
  static void setCoreMemory(SectionMoteMemory mem) {
    for (var sec : mem.getSections().values()) {
      int length = sec.getTotalSize();
      MemorySegment.ofAddress(sec.getStartAddr(), length, SegmentScope.global())
              .asByteBuffer().put(0, sec.getMemory(), 0, length);
    }
  }

  /**
   * @param netStack Contiki network stack
   */
  public void setNetworkStack(NetworkStack netStack) {
    this.netStack = netStack;
  }

  /**
   * @return Contiki network stack
   */
  public NetworkStack getNetworkStack() {
    return netStack;
  }

  /**
   * Executes configured command on given file and returns the result.
   *
   * @param command Command to execute
   * @param libraryFile Contiki library
   * @param withUI Specifies if UI should be used or not for error output
   * @return Command execution output
   * @throws org.contikios.cooja.MoteType.MoteTypeCreationException if any error occurred or command gave no output
   */
  private static String[] loadCommandData(String command, File libraryFile, boolean withUI) throws MoteTypeCreationException {
    ArrayList<String> output = new ArrayList<>();
    final MessageList commandOutput = MessageContainer.createMessageList(withUI);
    try {
      /* Execute command, read response */
      ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
      pb.directory(libraryFile.getParentFile());
      final Process p = pb.start();
      Thread readThread = new Thread(() -> {
        try (var errorInput = p.errorReader(UTF_8)) {
          String line;
          while ((line = errorInput.readLine()) != null) {
            commandOutput.addMessage(line, MessageList.ERROR);
          }
        } catch (IOException e) {
          commandOutput.addMessage("Error reading from command stderr: "
                                           + e.getMessage(), MessageList.ERROR);
        }
      }, "read command output");
      readThread.setDaemon(true);
      readThread.start();

      try (var input = p.inputReader(UTF_8)) {
        String line;
        while ((line = input.readLine()) != null) {
          output.add(line);
        }
      }

      int ret = p.waitFor();

      // wait for read thread to finish processing any error output
      readThread.join();

      if (ret != 0) {
        // Command returned with error
        throw createException("Command failed with error: " + ret, null, command, commandOutput);
      }
      if (output.isEmpty()) {
        throw createException("No output from parse command!", null, command, commandOutput);
      }
      return output.toArray(new String[0]);
    } catch (InterruptedException | IOException err) {
      throw createException("Command error: " + err.getMessage(), err, command, commandOutput);
    }
  }

  private static MoteTypeCreationException createException(String message, Throwable err,
                                                           String command, MessageList outputList) {
    outputList.addMessage("Failed to run command: " + command, MessageList.ERROR);
    return new MoteTypeCreationException(message, err, outputList);
  }

  @Override
  protected void appendVisualizerInfo(StringBuilder sb) {
    /* Mote interfaces */
    sb.append("<tr><td valign=\"top\">Mote interface</td><td>");
    for (var moteInterface : moteInterfaceClasses) {
      sb.append(moteInterface.getSimpleName()).append("<br>");
    }
    sb.append("</td></tr>");
  }

  @Override
  public Collection<Element> getConfigXML(Simulation simulation) {
    var config = getBaseConfigXML(simulation, false);
    if (getNetworkStack() != NetworkStack.DEFAULT) {
      var element = new Element("netstack");
      element.setText(getNetworkStack().getConfig());
      config.add(element);
    }
    return config;
  }

  @Override
  public boolean setConfigXML(Simulation simulation,
                              Collection<Element> configXML, boolean visAvailable)
          throws MoteTypeCreationException {
    if (!setBaseConfigXML(simulation, configXML)) {
      return false;
    }
    for (Element element : configXML) {
      switch (element.getName()) {
        case "commstack" -> {
          logger.warn("The Cooja communication stack config was removed: " + element.getText());
          logger.warn("Instead assuming default network stack.");
        }
        case "netstack" -> netStack = NetworkStack.parseConfig(element.getText());
      }
    }
    final var sourceFile = getContikiSourceFile();
    if (sourceFile == null) {
      throw new MoteTypeCreationException("No Contiki application specified");
    }
    // Compensate for non-standard naming rules.
    fileFirmware = getExpectedFirmwareFile(sourceFile.getAbsolutePath());
    if (getCompileCommands() == null) {
      throw new MoteTypeCreationException("No compile commands specified");
    }
    return configureAndInit(Cooja.getTopParentContainer(), simulation, Cooja.isVisualized());
  }

  @Override
  public void removed() {
    arena.close();
  }
}
