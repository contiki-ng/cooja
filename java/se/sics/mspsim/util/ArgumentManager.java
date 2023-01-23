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
 * -----------------------------------------------------------------
 *
 * ArgumentManager
 *
 * Author  : Joakim Eriksson, Niclas Finne
 * Created : Tue Apr 08 22:08:32 2003
 */
package se.sics.mspsim.util;
import java.util.ArrayList;

/**
 */
public class ArgumentManager extends ConfigManager {

  private final String[] arguments;

  public ArgumentManager(String[] args) {
    ArrayList<String> list = new ArrayList<>();
    ArrayList<String> config = new ArrayList<>();
    for (int i = 0, n = args.length; i < n; i++) {
      if ("-".equals(args[i])) {
        // The rest should be considered arguments
        for(++i; i < args.length; i++) {
          list.add(args[i]);
        }
        break;
      }
      if (args[i].startsWith("-")) {
        String param = args[i].substring(1);
        String value = "";
        int index = param.indexOf('=');
        if (index >= 0) {
          value = param.substring(index + 1);
          param = param.substring(0, index);
        }
        if (param.isEmpty()) {
          throw new IllegalArgumentException("illegal argument: " + args[i]);
        }
        if ("config".equals(param)) {
          if (value.isEmpty()) {
            throw new IllegalArgumentException("no config file name specified");
          }
          if (!loadConfiguration(value)) {
            throw new IllegalArgumentException("failed to load configuration " + value);
          }
        }
        config.add(param);
        config.add(!value.isEmpty() ? value : "true");
      } else {
        // Normal argument.
        list.add(args[i]);
      }
    }
    arguments = list.toArray(new String[0]);
    for (int i = 0, n = config.size(); i < n; i += 2) {
      setProperty(config.get(i), config.get(i + 1));
    }
  }

  public String[] getArguments() {
    return arguments;
  }
} // ArgumentManager
