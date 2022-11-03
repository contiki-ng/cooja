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
 */

package org.contikios.cooja.script;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;

import org.contikios.cooja.Simulation;

public class ScriptParser {
  private long timeoutTime = -1;
  private String timeoutCode = "";

  private final String code;

  public ScriptParser(String code) throws ScriptSyntaxErrorException {
    code = "\n" + code.replaceAll("\r\n", "\n") + "\n";
    Matcher matcher = Pattern.compile("/\\*([^*]|\n|(\\*+([^*/]|\n)))*\\*+/").matcher(code);
    while (matcher.find()) {
      String match = matcher.group();
      int newLines = match.split("\n").length;
      code = matcher.replaceFirst("\n".repeat(newLines));
      matcher.reset(code);
    }
    code = Pattern.compile("//.*\n").matcher(code).replaceAll("\n");

    Matcher matcher2 = Pattern.compile("TIMEOUT\\(" + "(\\d+)" + "\\)").matcher(code);
    if (matcher2.find()) {
      timeoutTime = Long.parseLong(matcher2.group(1)) * Simulation.MILLISECOND;
      matcher2.reset(code);
      code = matcher2.replaceFirst(";");
      matcher2.reset(code);
      if (matcher2.find()) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
    }

    Matcher matcher3 = Pattern.compile("TIMEOUT\\(" + "(\\d+)" + "\\s*,\\s*" + "(.*)" + "\\)").matcher(code);
    if (matcher3.find()) {
      if (timeoutTime > 0) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
      timeoutTime = Long.parseLong(matcher3.group(1)) * Simulation.MILLISECOND;
      // FIXME: code6 should not be required, handled by last lines in constructor.
      String code6 = matcher3.group(2);
      code6 = Pattern.compile("log\\.testOK\\(\\)").matcher(code6).replaceAll("throw new TestOK()");
      timeoutCode = Pattern.compile("log\\.testFailed\\(\\)").matcher(code6).replaceAll("throw new TestFailed()");
      matcher3.reset(code);
      code = matcher3.replaceFirst(";");
      matcher3.reset(code);
      if (matcher3.find()) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
    }

    Matcher matcher4 = Pattern.compile("YIELD_THEN_WAIT_UNTIL\\(" + "(.*)" + "\\)").matcher(code);
    while (matcher4.find()) {
      code = matcher4.replaceFirst("YIELD(); WAIT_UNTIL(" + matcher4.group(1) + ")");
      matcher4.reset(code);
    }

    Matcher matcher5 = Pattern.compile("WAIT_UNTIL\\(" + "(.*)" + "\\)").matcher(code);
    while (matcher5.find()) {
      code = matcher5.replaceFirst("while (!(" + matcher5.group(1) + ")) { " + " YIELD(); " + "}");
      matcher5.reset(code);
    }
    code = Pattern.compile("log\\.testOK\\(\\)").matcher(code).replaceAll("throw new TestOK()");
    code = Pattern.compile("log\\.testFailed\\(\\)").matcher(code).replaceAll("throw new TestFailed()");
    code = Pattern.compile("log\\.generateMessage\\(").matcher(code).replaceAll("log.generateMsg(mote, ");
    this.code = code;
  }

  public String getJSCode() {
    // Nashorn can be created with --language=es6, but "class TestFailed extends Error .." is not supported.
    return
     """
     function TestFailed(msg, name, line) {
       var err = new Error(msg, name, line);
       Object.setPrototypeOf(err, Object.getPrototypeOf(this));
       return err;
     }
     TestFailed.prototype = Object.create(Error.prototype, {
       constructor: { value: Error, enumerable: false, writable: true, configurable: true }
     });
     Object.setPrototypeOf(TestFailed, Error);
     function TestOK(msg, name, line) {
       var err = new Error(msg, name, line);
       Object.setPrototypeOf(err, Object.getPrototypeOf(this));
       return err;
     }
     TestOK.prototype = Object.create(Error.prototype, {
       constructor: { value: Error, enumerable: false, writable: true, configurable: true }
     });
     Object.setPrototypeOf(TestOK, Error);
     function Shutdown(msg, name, line) {
       var err = new Error(msg, name, line);
       Object.setPrototypeOf(err, Object.getPrototypeOf(this));
       return err;
     }
     Shutdown.prototype = Object.create(Error.prototype, {
       constructor: { value: Error, enumerable: false, writable: true, configurable: true }
     });
     Object.setPrototypeOf(Shutdown, Error);

     function GENERATE_MSG(time, msg) {
       log.generateMsg(mote, time, msg);
     };

     function YIELD() {
       SEMAPHORE_SIM.release();
       SEMAPHORE_SCRIPT.acquire(); // Wait for simulation here.
       if (TIMEOUT) {
     """ + timeoutCode + ";\n" +
     """
         if (timeout_function != null) { timeout_function(); }
         log.log('TEST TIMEOUT\\n');
         throw new TestFailed();
       }
       if (SHUTDOWN) { throw new Shutdown(); }
       node.setMoteMsg(mote, msg);
     };

     function write(mote, msg) {
       mote.getInterfaces().getLog().writeString(msg);
     };
     timeout_function = null;
     function run() {
       try {
         YIELD();
         // User script starting.
     """ +
     code +
     """
         // User script end.
         while (true) { YIELD(); }
       } catch (error) {
         SEMAPHORE_SCRIPT.release();
         if (error instanceof TestOK) return 0;
         if (error instanceof TestFailed) return 1;
         if (error instanceof Shutdown) return -1;
         throw(error);
       }
     };
     run();
     """;
  }

  public long getTimeoutTime() {
    return timeoutTime;
  }

  public static class ScriptSyntaxErrorException extends ScriptException {
    public ScriptSyntaxErrorException(String msg) {
      super(msg);
    }
  }

}
