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

package org.contikios.cooja;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.contikios.cooja.util.StringUtils;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic parts of a breakpoint. Based on MspBreakpoint.
 */
public abstract class Breakpoint implements Watchpoint {
  private static final Logger logger = LoggerFactory.getLogger(Breakpoint.class);

  protected final WatchpointMote mote;

  protected long address = -1; /* Binary address */
  protected File codeFile; /* Source code, may be null*/
  protected int lineNr = -1; /* Source code line number, may be null */

  protected boolean stopsSimulation = true;

  protected String msg;
  protected Color color = Color.BLACK;

  protected String sourceCode;

  public Breakpoint(WatchpointMote mote) {
    this.mote = mote;
    // expects setConfigXML(..).
  }

  public Breakpoint(WatchpointMote mote, long address, File codeFile, int lineNr) {
    this(mote);
    this.address = address;
    this.codeFile = codeFile;
    this.lineNr = lineNr;
  }

  @Override
  public WatchpointMote getMote() {
    return mote;
  }

  @Override
  public Color getColor() {
    return color;
  }

  @Override
  public void setColor(Color color) {
    this.color = color;
  }

  @Override
  public String getDescription() {
    String desc = "";
    if (codeFile != null) {
      desc += codeFile.getPath() + ":" + lineNr + " (0x" + Long.toHexString(address) + ")";
    } else if (address >= 0) {
      desc += "0x" + Long.toHexString(address);
    }
    if (msg != null) {
      desc += "\n\n" + msg;
    }
    return desc;
  }
  @Override
  public void setUserMessage(String msg) {
    this.msg = msg;
  }

  @Override
  public String getUserMessage() {
    return msg;
  }

  @Override
  public File getCodeFile() {
    return codeFile;
  }

  @Override
  public int getLineNumber() {
    return lineNr;
  }

  @Override
  public long getExecutableAddress() {
    return address;
  }

  @Override
  public void setStopsSimulation(boolean stops) {
    stopsSimulation = stops;
  }

  @Override
  public boolean stopsSimulation() {
    return stopsSimulation;
  }

  protected abstract void createMonitor();

  @Override
  public Collection<Element> getConfigXML() {
    var config = new ArrayList<Element>();
    var element = new Element("stops");
    element.setText(String.valueOf(stopsSimulation));
    config.add(element);

    element = new Element("codefile");
    File file = mote.getSimulation().getCooja().createPortablePath(codeFile);
    element.setText(file.getPath().replaceAll("\\\\", "/"));
    config.add(element);

    if (lineNr >= 0) {
      element = new Element("line");
      element.setText(String.valueOf(lineNr));
      config.add(element);
    }

    if (sourceCode != null) {
      element = new Element("sourcecode");
      element.setText(sourceCode);
      config.add(element);
    }

    if (msg != null) {
      element = new Element("msg");
      element.setText(msg);
      config.add(element);
    }

    if (color != null) {
      element = new Element("color");
      element.setText(String.valueOf(color.getRGB()));
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML) {
    // Already knows mote and breakpoints.
    for (var element : configXML) {
      switch (element.getName()) {
        case "codefile" -> {
          codeFile = mote.getSimulation().getCooja().restorePortablePath(new File(element.getText()));
          try {
            codeFile = codeFile.getCanonicalFile();
          } catch (IOException e) {
            codeFile = null;
          }
          if (codeFile == null || !codeFile.exists()) {
            logger.error("Could not find file: {}", element.getText());
            return false;
          }
        }
        case "line" -> lineNr = Integer.parseInt(element.getText());
        case "sourcecode", "contikicode" -> {
          // Verify that code did not change.
          final String code = StringUtils.loadFromFile(codeFile);
          if (code != null && lineNr > 0) {
            String[] lines = code.split("\n");
            if (lineNr - 1 < lines.length) {
              sourceCode = lines[lineNr - 1].trim();
            }
          }
          var lastSourceCode = element.getText().trim();
          if (!lastSourceCode.equals(sourceCode)) {
            logger.warn("Detected modified code at breakpoint: " + codeFile.getPath() + ":" + lineNr + ".");
            logger.warn("From: '" + lastSourceCode + "'");
            logger.warn("  To: '" + sourceCode + "'");
          }
        }
        case "msg" -> msg = element.getText();
        case "color" -> color = new Color(Integer.parseInt(element.getText()));
        case "stops" -> stopsSimulation = Boolean.parseBoolean(element.getText());
      }
    }

    // Update executable address.
    address = mote.getType().getExecutableAddressOf(codeFile, lineNr);
    if (address < 0) {
      logger.error("Could not restore breakpoint, did source code change?");
      return false;
    }
    createMonitor();
    return true;
  }

  @Override
  public String toString() {
    return getMote() + ": " + getDescription();
  }
}
