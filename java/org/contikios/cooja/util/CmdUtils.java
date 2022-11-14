/*
 * Copyright (c) 2022, Research Institutes of Sweden. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.dialogs.MessageList;

/**
 * Utility functions for command execution.
 */
public class CmdUtils {
  /**
   * Executes command and returns the output.
   *
   * @param command Command to execute.
   * @param gui Cooja object.
   * @param synchronous Wait for the output readers to terminate before returning.
   * @return Command execution output
   */
  public static String[] run(String command, Cooja gui, boolean synchronous) throws Exception {
    command = gui.resolveConfigDir(Cooja.resolvePathIdentifiers(command));

    ArrayList<String> output = new ArrayList<>();
    final MessageList cmdIO = MessageContainer.createMessageList(Cooja.isVisualized());
    try {
      var pb = new ProcessBuilder("/bin/sh", "-c", command);
      pb.directory(new File(gui.getConfigDir()));
      final Process p = pb.start();
      var stderr = new Thread(() -> {
        try (var errorInput = p.errorReader(UTF_8)) {
          String line;
          while ((line = errorInput.readLine()) != null) {
            cmdIO.addMessage(line, MessageList.ERROR);
          }
        } catch (IOException e) {
          cmdIO.addMessage("Error reading from stderr: " + e.getMessage(), MessageList.ERROR);
        }
      }, "CmdUtils.stderr");
      stderr.setDaemon(true);
      stderr.start();
      var stdout = new Thread(() -> {
        try (var input = p.inputReader(UTF_8)) {
          String line;
          while ((line = input.readLine()) != null) {
            cmdIO.addMessage(line, MessageList.NORMAL);
            output.add(line);
          }
        } catch (IOException e) {
          cmdIO.addMessage("Error reading from stdout: " + e.getMessage(), MessageList.ERROR);
        }
      }, "CmdUtils.stdout");
      stdout.setDaemon(true);
      stdout.start();

      int ret = p.waitFor();
      if (synchronous) {
        stdout.join();
        stderr.join();
      }
      if (ret != 0) {
        throw createException("Command failed with error: " + ret, null, command, cmdIO);
      }
      return output.toArray(new String[0]);
    } catch (InterruptedException | IOException err) {
      throw createException("Command error: " + err.getMessage(), err, command, cmdIO);
    }
  }

  private static Exception createException(String message, Throwable err,
                                           String command, MessageList outputList) {
    outputList.addMessage("Failed to run command: " + command, MessageList.ERROR);
    return new Exception(message, err);
  }
}
