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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.CoreComm;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteInterfaceHandler;
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
import org.contikios.cooja.dialogs.ContikiMoteCompileDialog;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.interfaces.Battery;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.RimeAddress;
import org.contikios.cooja.mote.BaseContikiMoteType;
import org.contikios.cooja.mote.memory.ArrayMemory;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.mote.memory.MemoryInterface.Symbol;
import org.contikios.cooja.mote.memory.MemoryLayout;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.contikios.cooja.mote.memory.VarMemory;
import org.jdom.Element;

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
public class ContikiMoteType extends BaseContikiMoteType {

  private static final Logger logger = LogManager.getLogger(ContikiMoteType.class);

  /**
   * Library file suffix
   */
  private static final String librarySuffix = ".cooja";

  /**
   * Random generator for generating a unique mote ID.
   */
  private static final Random rnd = new Random();

  private final Cooja gui;

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
      if (this == DEFAULT) {
        return null;
      } else if (this == MANUAL) {
        return manualHeader;
      }
      return null;
    }

    public String getConfig() {
      if (this == DEFAULT) {
        return "DEFAULT";
      } else if (this == MANUAL) {
        return "MANUAL:" + manualHeader;
      }
      return "[unknown]";
    }

    public static NetworkStack parseConfig(String config) {
      if (config.equals("DEFAULT")) {
        return DEFAULT;
      } else if (config.startsWith("MANUAL")) {
        NetworkStack st = MANUAL;
        st.manualHeader = config.split(":")[1];
        return st;
      }

      /* TODO Backwards compatibility */
      logger.warn("Bad network stack config: '" + config + "', using default");
      return DEFAULT;
    }
  }

  private final String javaClassName; // Loading Java class name: Lib1.java.

  private NetworkStack netStack = NetworkStack.DEFAULT;

  // Type specific class configuration

  private CoreComm myCoreComm = null;

  // Initial memory for all motes of this type
  private SectionMoteMemory initialMemory = null;

  /** Offset between native (cooja) and contiki address space */
  long offset;

  /**
   * Creates a new uninitialized Cooja mote type. This mote type needs to load
   * a library file and parse a map file before it can be used.
   */
  public ContikiMoteType(Cooja gui) {
    this.gui = gui;
    javaClassName = CoreComm.getAvailableClassName();
    projectConfig = gui.getProjectConfig().clone();
  }

  @Override
  public String getMoteType() {
    return "cooja";
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

  /**
   * Get the mote file for the extension.
   *
   * @param extension File extension (.map)
   * @return The mote file for the extension
   */
  private File getMoteFile(String extension) {
    return new File(fileSource.getParentFile(), "build/cooja/" + identifier + extension);
  }

  @Override
  public Map<String, String> getCompilationEnvironment() {
    var sources = new StringBuilder();
    var dirs = new StringBuilder();
    // Check whether Cooja projects include additional sources.
    String[] coojaSources = getConfig().getStringArrayValue(ContikiMoteType.class, "C_SOURCES");
    if (coojaSources != null) {
      for (String s : coojaSources) {
        if (s.trim().isEmpty()) {
          continue;
        }
        File p = getConfig().getUserProjectDefining(ContikiMoteType.class, "C_SOURCES", s);
        if (p == null) {
          logger.warn("Project defining C_SOURCES$" + s + " not found");
          continue;
        }
        sources.append(s).append(" ");
        dirs.append(p.getPath()).append(" ");
      }
    }

    // Create the compilation environment.
    String ccFlags = Cooja.getExternalToolsSetting("COMPILER_ARGS", "");
    var env = new LinkedHashMap<String, String>();
    env.put("LIBNAME", "$(BUILD_DIR_BOARD)/" + getIdentifier() + ".cooja");
    env.put("COOJA_VERSION",  Cooja.CONTIKI_NG_BUILD_VERSION);
    env.put("CLASSNAME", javaClassName);
    env.put("COOJA_SOURCEDIRS", dirs.toString().replace("\\", "/"));
    env.put("COOJA_SOURCEFILES", sources.toString());
    env.put("CC", Cooja.getExternalToolsSetting("PATH_C_COMPILER"));
    env.put("EXTRA_CC_ARGS", ccFlags);
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
  protected boolean showCompilationDialog(Simulation sim) {
    return ContikiMoteCompileDialog.showDialog(sim, this);
  }

  /** Load LibN.java and the corresponding .cooja file into memory. */
  @Override
  public boolean loadMoteFirmware(boolean vis) throws MoteTypeCreationException {
    if (myCoreComm != null) {
      throw new MoteTypeCreationException("Core communicator already used: " + myCoreComm.getClass().getName());
    }
    Path tmpDir;
    try {
      tmpDir = Files.createTempDirectory("cooja");
    } catch (IOException e) {
      logger.warn("Failed to create temp directory:" + e);
      return false;
    }
    tmpDir.toFile().deleteOnExit();

    // Create, compile, and load the Java wrapper that loads the C library.

    // Allocate core communicator class
    final var firmwareFile = getContikiFirmwareFile();
    logger.debug("Creating core communicator between Java class " + javaClassName + " and Contiki library '" + firmwareFile.getPath() + "'");
    myCoreComm = CoreComm.createCoreComm(tmpDir, javaClassName, firmwareFile);

    /* Parse addresses using map file
     * or output of command specified in external tools settings (e.g. nm -a )
     */
    boolean useCommand = Boolean.parseBoolean(Cooja.getExternalToolsSetting("PARSE_WITH_COMMAND", "false"));

    SectionParser dataSecParser;
    SectionParser bssSecParser;
    SectionParser commonSecParser = null;

    if (useCommand) {
      /* Parse command output */
      String[] output = loadCommandData(firmwareFile, vis);

      dataSecParser = new CommandSectionParser(
              output,
              Cooja.getExternalToolsSetting("COMMAND_DATA_START"),
              Cooja.getExternalToolsSetting("COMMAND_DATA_END"),
              Cooja.getExternalToolsSetting("COMMAND_VAR_SEC_DATA"));
      bssSecParser = new CommandSectionParser(
              output,
              Cooja.getExternalToolsSetting("COMMAND_BSS_START"),
              Cooja.getExternalToolsSetting("COMMAND_BSS_END"),
              Cooja.getExternalToolsSetting("COMMAND_VAR_SEC_BSS"));
      commonSecParser = new CommandSectionParser(
              output,
              Cooja.getExternalToolsSetting("COMMAND_COMMON_START"),
              Cooja.getExternalToolsSetting("COMMAND_COMMON_END"),
              Cooja.getExternalToolsSetting("COMMAND_VAR_SEC_COMMON"));
    } else {
      // Parse map file (build/cooja/mtype1.map).
      var mapFile = getMoteFile(".map");
      if (!mapFile.exists()) {
        throw new MoteTypeCreationException("Map file " + mapFile + " could not be found");
      }
      // The map file for 02-ringbufindex.csc is 2779 lines long, add some margin beyond that.
      var lines = new ArrayList<String>(4000);
      try (var reader = Files.newBufferedReader(mapFile.toPath(), UTF_8)) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      } catch (IOException e) {
        logger.fatal("No map data could be loaded");
        lines.clear();
      }

      if (lines.isEmpty()) {
        throw new MoteTypeCreationException("No map data could be loaded: " + mapFile);
      }
      String[] mapData = lines.toArray(new String[0]);
      dataSecParser = new MapSectionParser(
              mapData,
              Cooja.getExternalToolsSetting("MAPFILE_DATA_START"),
              Cooja.getExternalToolsSetting("MAPFILE_DATA_SIZE"));
      bssSecParser = new MapSectionParser(
              mapData,
              Cooja.getExternalToolsSetting("MAPFILE_BSS_START"),
              Cooja.getExternalToolsSetting("MAPFILE_BSS_SIZE"));
    }

    /* We first need the value of Contiki's referenceVar, which tells us the
     * memory offset between Contiki's variable and the relative addresses that
     * were calculated directly from the library file.
     *
     * This offset will be used in Cooja in the memory abstraction to match
     * Contiki's and Cooja's address spaces */
    HashMap<String, Symbol> variables = new HashMap<>();
    {
      SectionMoteMemory tmp = new SectionMoteMemory(variables);
      VarMemory varMem = new VarMemory(tmp);
      tmp.addMemorySection("tmp.data", dataSecParser.parse(0));
      tmp.addMemorySection("tmp.bss", bssSecParser.parse(0));
      if (commonSecParser != null) {
        tmp.addMemorySection("tmp.common", commonSecParser.parse(0));
      }

      try {
        long referenceVar = varMem.getVariable("referenceVar").addr;
        myCoreComm.setReferenceAddress(referenceVar);
      } catch (RuntimeException e) {
        throw new MoteTypeCreationException("Error setting reference variable: " + e.getMessage(), e);
      }

      getCoreMemory(tmp);

      offset = varMem.getAddrValueOf("referenceVar");
      logger.debug(firmwareFile.getName()
              + ": offsetting Cooja mote address space: 0x" + Long.toHexString(offset));
    }

    /* Create initial memory: data+bss+optional common */
    initialMemory = new SectionMoteMemory(variables);

    initialMemory.addMemorySection("data", dataSecParser.parse(offset));

    initialMemory.addMemorySection("bss", bssSecParser.parse(offset));

    if (commonSecParser != null) {
      initialMemory.addMemorySection("common", commonSecParser.parse(offset));
    }

    getCoreMemory(initialMemory);
    return true;
  }

  @Override
  public Class<? extends MoteInterface>[] getAllMoteInterfaceClasses() {
    String[] ifNames = getConfig().getStringArrayValue(ContikiMoteType.class, "MOTE_INTERFACES");
    ArrayList<Class<? extends MoteInterface>> classes = new ArrayList<>();

    classes.add(Position.class);
    classes.add(Battery.class);
    classes.add(ContikiVib.class);
    classes.add(ContikiMoteID.class);
    classes.add(ContikiRS232.class);
    classes.add(ContikiBeeper.class);
    classes.add(RimeAddress.class);
    classes.add(IPAddress.class);
    classes.add(ContikiRadio.class);
    classes.add(ContikiButton.class);
    classes.add(ContikiPIR.class);
    classes.add(ContikiClock.class);
    classes.add(ContikiLED.class);
    classes.add(ContikiCFS.class);
    classes.add(ContikiEEPROM.class);
    classes.add(Mote2MoteRelations.class);
    classes.add(MoteAttributes.class);
    // Load mote interface classes.
    for (String ifName : ifNames) {
      var ifClass = MoteInterfaceHandler.getInterfaceClass(gui, this, ifName);
      if (ifClass == null) {
        logger.warn("Failed to load mote interface class: " + ifName);
        continue;
      }
      classes.add(ifClass);
    }
    return classes.toArray(new Class[0]);
  }

  @Override
  public Class<? extends MoteInterface>[] getDefaultMoteInterfaceClasses() {
    return getAllMoteInterfaceClasses();
  }

  @Override
  public File getExpectedFirmwareFile(String name) {
    return new File(new File(name).getParentFile(), "build/cooja/" + identifier + ContikiMoteType.librarySuffix);
  }

  /**
   * Abstract base class for concrete section parser class.
   */
  static abstract class SectionParser {

    private final String[] mapFileData;
    protected long startAddr;
    protected int size;
    protected Map<String, Symbol> variables;

    public SectionParser(String[] mapFileData) {
      this.mapFileData = mapFileData;
    }

    public String[] getData() {
      return mapFileData;
    }

    public long getStartAddr() {
      return startAddr;
    }

    public int getSize() {
      return size;
    }

    public Map<String, Symbol> getVariables(){
      return variables;
    }

    protected abstract boolean parseStartAddrAndSize();

    abstract Map<String, Symbol> parseSymbols(long offset);

    public MemoryInterface parse(long offset) {
      if (!parseStartAddrAndSize()) {
        return null;
      }

      variables = parseSymbols(offset);

      if (logger.isDebugEnabled()) {
        logger.debug(String.format("Parsed section at 0x%x ( %d == 0x%x bytes)",
                                 getStartAddr() + offset,
                                 getSize(),
                                 getSize()));
        for (Map.Entry<String, Symbol> entry : variables.entrySet()) {
          logger.debug(String.format("Found Symbol: %s, 0x%x, %d",
                  entry.getKey(),
                  entry.getValue().addr,
                  entry.getValue().size));
        }
      }

      return new ArrayMemory(
              getStartAddr() + offset,
              getSize(),
              MemoryLayout.getNative(),
              variables);
    }

  }

  /**
   * Parses Map file for section data.
   */
  static class MapSectionParser extends SectionParser {

    private final String startRegExp;
    private final String sizeRegExp;

    public MapSectionParser(String[] mapFileData, String startRegExp, String sizeRegExp) {
      super(mapFileData);
      assert startRegExp != null : "Section start regexp must be specified";
      assert !startRegExp.isEmpty() : "Section start regexp must contain characters";
      assert sizeRegExp != null : "Section size regexp must be specified";
      assert !sizeRegExp.isEmpty() : "Section size regexp must contain characters";
      this.startRegExp = startRegExp;
      this.sizeRegExp = sizeRegExp;
    }

    @Override
    protected boolean parseStartAddrAndSize() {
      startAddr = -1;
      size = -1;
      Pattern varPattern = Pattern.compile(startRegExp);
      for (var line : getData()) {
        Matcher varMatcher = varPattern.matcher(line);
        if (varMatcher.find()) {
          startAddr = Long.parseUnsignedLong(varMatcher.group(1).trim(), 16);
          Pattern sizePattern = Pattern.compile(sizeRegExp);
          Matcher sizeMatcher = sizePattern.matcher(line);
          if (sizeMatcher.find()) {
            size = (int) Long.parseUnsignedLong(sizeMatcher.group(1).trim(), 16);
          }
          break;
        }
      }
      return startAddr >= 0 && size > 0;
    }

    @Override
    public Map<String, Symbol> parseSymbols(long offset) {
      Map<String, Symbol> varNames = new HashMap<>();

      Pattern pattern = Pattern.compile(Cooja.getExternalToolsSetting("MAPFILE_VAR_NAME"));
      final var secStart = getStartAddr();
      final var secSize = getSize();
      final var varAddrRegexp1 = Cooja.getExternalToolsSetting("MAPFILE_VAR_ADDRESS_1");
      final var varAddrRegexp2 = Cooja.getExternalToolsSetting("MAPFILE_VAR_ADDRESS_2");
      final var varSizeRegexp1 = Cooja.getExternalToolsSetting("MAPFILE_VAR_SIZE_1");
      final var varSizeRegexp2 = Cooja.getExternalToolsSetting("MAPFILE_VAR_SIZE_2");
      final String[] mapFileData = getData();
      for (String line : mapFileData) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
          final long varAddr = Long.decode(matcher.group(1));
          if (varAddr >= secStart && varAddr <= secStart + secSize) {
            String varName = matcher.group(2);
            String regExp = varAddrRegexp1 + varName + varAddrRegexp2;
            String retString = null;
            Pattern pattern2 = Pattern.compile(regExp);
            for (String line1 : mapFileData) {
              Matcher matcher2 = pattern2.matcher(line1);
              if (matcher2.find()) {
                retString = matcher2.group(1);
                break;
              }
            }
            long mapFileVarAddress = retString == null ? -1 : Long.parseUnsignedLong(retString.trim(), 16);
            int mapFileVarSize = -1;
            Pattern pattern1 = Pattern.compile(varSizeRegexp1 + varName + varSizeRegexp2);
            for (int idx = 0; idx < mapFileData.length; idx++) {
              String parseString = mapFileData[idx];
              Matcher matcher1 = pattern1.matcher(parseString);
              if (matcher1.find()) {
                mapFileVarSize = Integer.decode(matcher1.group(1));
                break;
              }
              // second approach with lines joined
              if (idx < mapFileData.length - 1) {
                parseString += mapFileData[idx + 1];
              }
              matcher1 = pattern1.matcher(parseString);
              if (matcher1.find()) {
                mapFileVarSize = Integer.decode(matcher1.group(1));
                break;
              }
            }
            varNames.put(varName, new Symbol(
                    Symbol.Type.VARIABLE,
                    varName,
                    mapFileVarAddress + offset,
                    mapFileVarSize));
          }
        }
      }
      return varNames;
    }

  }

  /**
   * Parses command output for section data.
   */
  static class CommandSectionParser extends SectionParser {

    private final String startRegExp;
    private final String endRegExp;
    private final String sectionRegExp;

    /**
     * Creates SectionParser based on output of configurable command.
     * 
     * @param mapFileData Map file lines as array of String
     * @param startRegExp Regular expression for parsing start of section
     * @param endRegExp Regular expression for parsing end of section
     * @param sectionRegExp Regular expression describing symbol table section identifier (e.g. '[Rr]' for readonly)
     *        Will be used to replaced '<SECTION>'in 'COMMAND_VAR_NAME_ADDRESS_SIZE'
     */
    public CommandSectionParser(String[] mapFileData, String startRegExp, String endRegExp, String sectionRegExp) {
      super(mapFileData);
      this.startRegExp = startRegExp;
      this.endRegExp = endRegExp;
      this.sectionRegExp = sectionRegExp;
    }

    @Override
    protected boolean parseStartAddrAndSize() {
      // FIXME: Adjust this code to mirror the optimized method in MapSectionParser.
      if (startRegExp == null || startRegExp.equals("")) {
        startAddr = -1;
      } else {
        long result;
        String retString = null;
        Pattern pattern = Pattern.compile(startRegExp);
        for (String line : getData()) {
          Matcher matcher = pattern.matcher(line);
          if (matcher.find()) {
            retString = matcher.group(1);
            break;
          }
        }

        if (retString == null || retString.equals("")) {
          result = -1;
        } else {
          result = Long.parseUnsignedLong(retString.trim(), 16);
        }

        startAddr = result;
      }

      if (startAddr < 0 || endRegExp == null || endRegExp.equals("")) {
        size = -1;
        return false;
      }

      long end;
      String retString = null;
      Pattern pattern = Pattern.compile(endRegExp);
      for (String line : getData()) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
          retString = matcher.group(1);
          break;
        }
      }

      if (retString == null || retString.equals("")) {
        end = -1;
      } else {
        end = Long.parseUnsignedLong(retString.trim(), 16);
      }

      if (end < 0) {
        size = -1;
        return false;
      }
      size = (int) (end - getStartAddr());
      return startAddr >= 0 && size > 0;
    }

    @Override
    public Map<String, Symbol> parseSymbols(long offset) {
      HashMap<String, Symbol> addresses = new HashMap<>();
      /* Replace "<SECTION>" in regex by section specific regex */
      Pattern pattern = Pattern.compile(
              Cooja.getExternalToolsSetting("COMMAND_VAR_NAME_ADDRESS_SIZE")
                      .replace("<SECTION>", Pattern.quote(sectionRegExp)));
      for (String line : getData()) {
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
          /* Line matched variable address */
          String symbol = matcher.group("symbol");
          long varAddr = Long.parseUnsignedLong(matcher.group("address"), 16) + offset;
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
          } else {
            long oldAddress = addresses.get(symbol).addr;
            if (oldAddress != varAddr) {
              /*logger.warn("Warning, command response not matching previous entry of: "
               + varName);*/
            }
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
  protected void tick() {
    myCoreComm.tick();
  }

  /**
   * Creates and returns a copy of this mote type's initial memory (just after
   * the init function has been run). When a new mote is created it should get
   * its memory from here.
   *
   * @return Initial memory of a mote type
   */
  protected SectionMoteMemory createInitialMemory() {
    return initialMemory.clone();
  }

  /**
   * Copy core memory to given memory. This should not be used directly, but
   * instead via ContikiMote.getMemory().
   *
   * @param mem
   *          Memory to set
   */
  protected void getCoreMemory(SectionMoteMemory mem) {
    for (var sec : mem.getSections().values()) {
      myCoreComm.getMemory(sec.getStartAddr() - offset, sec.getTotalSize(), sec.getMemory());
    }
  }

  /**
   * Copy given memory to the Contiki system.
   *
   * @param mem
   * New memory
   */
  protected void setCoreMemory(SectionMoteMemory mem) {
    for (var sec : mem.getSections().values()) {
      myCoreComm.setMemory(sec.getStartAddr() - offset, sec.getTotalSize(), sec.getMemory());
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
   * @param libraryFile Contiki library
   * @param withUI Specifies if UI should be used or not for error output
   * @return Command execution output
   * @throws org.contikios.cooja.MoteType.MoteTypeCreationException if any error occurred or command gave no output
   */
  private static String[] loadCommandData(File libraryFile, boolean withUI) throws MoteTypeCreationException {
    ArrayList<String> output = new ArrayList<>();

    String command = Cooja.getExternalToolsSetting("PARSE_COMMAND");
    if (command != null) {
      command = Cooja.resolvePathIdentifiers(command);
    }
    if (command == null) {
      throw new MoteTypeCreationException("No parse command configured!");
    }

    /* Prepare command */
    command = command.replace("$(LIBFILE)",
                              libraryFile.getName().replace(File.separatorChar, '/'));

    final MessageList commandOutput = MessageContainer.createMessageList(withUI);
    try {
      /* Execute command, read response */
      ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
      pb.directory(libraryFile.getParentFile());
      final Process p = pb.start();
      Thread readThread = new Thread(() -> {
        try (BufferedReader errorInput = new BufferedReader(
                new InputStreamReader(p.getErrorStream(), UTF_8))) {
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

      try (BufferedReader input = new BufferedReader(
              new InputStreamReader(p.getInputStream(), UTF_8))) {
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

    var e = new MoteTypeCreationException(message, err);
    e.setCompilationOutput(outputList);
    return e;
  }

  /**
   * Generates a unique Cooja mote type ID.
   *
   * @param reservedIdentifiers Already reserved identifiers
   * @return Unique mote type ID.
   */
  public static String generateUniqueMoteTypeID(Set<String> reservedIdentifiers) {
    String testID = "";
    boolean available = false;

    while (!available) {
      testID = "mtype" + rnd.nextInt(1000000000);
      available = !reservedIdentifiers.contains(testID);
      // FIXME: add check that the library name is not already used.
    }

    return testID;
  }

  @Override
  protected void appendVisualizerInfo(StringBuilder sb) {
    /* JNI class */
    sb.append("<tr><td>JNI library</td><td>")
            .append(this.javaClassName).append("</td></tr>");

    /* Mote interfaces */
    sb.append("<tr><td valign=\"top\">Mote interface</td><td>");
    for (Class<? extends MoteInterface> moteInterface : moteInterfaceClasses) {
      sb.append(moteInterface.getSimpleName()).append("<br>");
    }
    sb.append("</td></tr>");
  }

  @Override
  public Collection<Element> getConfigXML(Simulation simulation) {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    element = new Element("identifier");
    element.setText(getIdentifier());
    config.add(element);

    element = new Element("description");
    element.setText(getDescription());
    config.add(element);

    element = new Element("source");
    File file = simulation.getCooja().createPortablePath(getContikiSourceFile());
    element.setText(file.getPath().replaceAll("\\\\", "/"));
    config.add(element);

    element = new Element("commands");
    element.setText(compileCommands);
    config.add(element);

    for (var moteInterface : moteInterfaceClasses) {
      element = new Element("moteinterface");
      element.setText(moteInterface.getName());
      config.add(element);
    }

    if (getNetworkStack() != NetworkStack.DEFAULT) {
      element = new Element("netstack");
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
        case "commstack":
          logger.warn("The Cooja communication stack config was removed: " + element.getText());
          logger.warn("Instead assuming default network stack.");
          break;
        case "netstack":
          netStack = NetworkStack.parseConfig(element.getText());
          break;
      }
    }
    final var sourceFile = getContikiSourceFile();
    if (sourceFile != null) { // Compensate for non-standard naming rules.
      fileFirmware = getMoteFile(librarySuffix);
    }
    // FIXME: These checks should always be on.
    if (!visAvailable || simulation.isQuickSetup()) {
      if (getIdentifier() == null) {
        throw new MoteTypeCreationException("No identifier specified");
      }
      if (sourceFile == null) {
        throw new MoteTypeCreationException("No Contiki application specified");
      }
      if (getCompileCommands() == null) {
        throw new MoteTypeCreationException("No compile commands specified");
      }
    }
    return configureAndInit(Cooja.getTopParentContainer(), simulation, visAvailable);
  }
}
