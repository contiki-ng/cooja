/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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
 *
 * $Id$
 *
 * -----------------------------------------------------------------
 *
 * ConfigManager
 *
 * Author  : Joakim Eriksson, Niclas Finne
 * Created : Fri Oct 11 15:24:14 2002
 * Updated : $Date$
 *           $Revision$
 */
package se.sics.mspsim.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

public class ConfigManager {

  private Properties properties;


  // -------------------------------------------------------------------
  // Config file handling
  // -------------------------------------------------------------------

  boolean loadConfiguration(String configFile) {
    try (var input = Files.newBufferedReader(new File(configFile).toPath(), StandardCharsets.UTF_8)) {
      var p = new Properties();
      p.load(input);
      this.properties = p;
      return true;
    } catch (FileNotFoundException e) {
      return false;
    } catch (IOException e) {
      throw new IllegalArgumentException("could not read config file '" + configFile + "': " + e);
    }
  }

  // -------------------------------------------------------------------
  // Properties handling
  // -------------------------------------------------------------------

  public String getProperty(String name) {
    return getProperty(name, null);
  }

  public String getProperty(String name, String defaultValue) {
    String value = (properties != null)
    ? properties.getProperty(name)
        : null;

    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    return value;
  }

  public void setProperty(String name, String value) {
    if (properties == null) {
      synchronized (this) {
        if (properties == null) {
          properties = new Properties();
        }
      }
    }

    if (value == null) {
      properties.remove(name);
    } else {
      properties.put(name, value);
    }
  }

  public boolean getPropertyAsBoolean(String name, boolean defaultValue) {
    String value = getProperty(name, null);
    return value == null ? defaultValue : "true".equals(value) || "yes".equals(value) || "1".equals(value);
  }

  public void print(PrintStream out) {
      properties.list(out);
  }
} // ConfigManager
