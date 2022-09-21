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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.ProjectConfig;

/**
 * The common parts of mote types based on compiled Contiki-NG targets.
 */
public abstract class BaseContikiMoteType implements MoteType {
  /** Description of the mote type. */
  protected String description = null;
  /** Identifier of the mote type. */
  protected String identifier = null;

  /** Project configuration of the mote type. */
  protected ProjectConfig projectConfig = null;
  /** Source file of the mote type. */
  protected File fileSource = null;
  /** Commands to compile the source into the firmware. */
  protected String compileCommands = null;
  /** Firmware of the mote type. */
  protected File fileFirmware = null;

  /** MoteInterface classes used by the mote type. */
  protected final ArrayList<Class<? extends MoteInterface>> moteInterfaceClasses = new ArrayList<>();

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
}
