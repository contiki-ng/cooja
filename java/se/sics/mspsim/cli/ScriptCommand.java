/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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
 * This file is part of MSPSim.
 *
 * $Id$
 *
 * -----------------------------------------------------------------
 *
 * ScriptCommand
 *
 * Authors : Joakim Eriksson, Niclas Finne
 * Created : 21 apr 2008
 * Updated : $Date$
 *           $Revision$
 */

package se.sics.mspsim.cli;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

/**
 *
 */
public class ScriptCommand extends Command {

  private final File scriptFile;

  public ScriptCommand(File scriptFile) {
    this.scriptFile = scriptFile;
  }

  @Override
  public String getArgumentHelp(String commandName) {
    return null;
  }

  @Override
  public String getCommandHelp(String commandName) {
    return "(implemented as " + scriptFile.getAbsolutePath() + ')';
  }

  @Override
  public int executeCommand(CommandContext context) {
    try {
      try (var in = Files.newBufferedReader(scriptFile.toPath(), UTF_8)) {
        String line;
        while ((line = in.readLine()) != null) {
          line = line.trim();
          if (!line.isEmpty() && (line.charAt(0) != '#' || line.startsWith("#!"))) {
            if (context.executeCommand(line) != 0) {
              break;
            }
          }
        }
      }
      return 0;
    } catch (FileNotFoundException e) {
      context.err.println("could not read the script '" + scriptFile + '\'');
      return 1;
    } catch (IOException e) {
      context.err.println("could not read the script '" + scriptFile + '\'');
      e.printStackTrace(context.err);
      return 1;
    }
  }
}
