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
  private String timeoutCode = null;

  private final String code;

  public ScriptParser(String code) throws ScriptSyntaxErrorException {
    String code1 = code;
    code1 = code1.replaceAll("\r\n", "\n");
    code1 = "\n" + code1 + "\n";
    code = code1;

    String code2 = code;
    /* TODO Handle strings */
    Pattern pattern = Pattern.compile("/\\*([^*]|\n|(\\*+([^*/]|\n)))*\\*+/");
    Matcher matcher = pattern.matcher(code2);

    while (matcher.find()) {
      String match = matcher.group();
      int newLines = match.split("\n").length;
      code2 = matcher.replaceFirst("\n".repeat(newLines));
      matcher.reset(code2);
    }
    code = code2;

    String code3 = code;
    /* TODO Handle strings */
    Pattern pattern1 = Pattern.compile("//.*\n");
    Matcher matcher1 = pattern1.matcher(code3);
    code3 = matcher1.replaceAll("\n");
    code = code3;

    String result;
    String code4 = code;
    Pattern pattern2 = Pattern.compile("TIMEOUT\\(" + "(\\d+)" + "\\)");
    Matcher matcher2 = pattern2.matcher(code4);

    if (!matcher2.find()) {
      result = code4;
    } else {
      if (timeoutTime > 0) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
      timeoutTime = Long.parseLong(matcher2.group(1)) * Simulation.MILLISECOND;
      timeoutCode = ";";
      matcher2.reset(code4);
      code4 = matcher2.replaceFirst(";");
      matcher2.reset(code4);
      if (matcher2.find()) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
      result = code4;
    }

    code = result;

    String result1;
    String code5 = code;
    Pattern pattern3 = Pattern.compile("TIMEOUT\\(" + "(\\d+)" + "\\s*,\\s*" + "(.*)" + "\\)");
    Matcher matcher3 = pattern3.matcher(code5);

    if (!matcher3.find()) {
      result1 = code5;
    } else {
      if (timeoutTime > 0) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
      timeoutTime = Long.parseLong(matcher3.group(1)) * Simulation.MILLISECOND;
      String code6 = matcher3.group(2);
      code6 = Pattern.compile("log\\.testOK\\(\\)").matcher(code6).replaceAll("throw new TestOK()");
      timeoutCode = Pattern.compile("log\\.testFailed\\(\\)").matcher(code6).replaceAll("throw new TestFailed()");
      matcher3.reset(code5);
      code5 = matcher3.replaceFirst(";");
      matcher3.reset(code5);
      if (matcher3.find()) {
        throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
      }
      result1 = code5;
    }

    code = result1;

    String code6 = code;
    Pattern pattern4 = Pattern.compile("YIELD_THEN_WAIT_UNTIL\\(" + "(.*)" + "\\)");
    Matcher matcher4 = pattern4.matcher(code6);
    while (matcher4.find()) {
      code6 = matcher4.replaceFirst("YIELD(); WAIT_UNTIL(" + matcher4.group(1) + ")");
      matcher4.reset(code6);
    }

    code = code6;
    String code7 = code;
    Pattern pattern5 = Pattern.compile("WAIT_UNTIL\\(" + "(.*)" + "\\)");
    Matcher matcher5 = pattern5.matcher(code7);
    while (matcher5.find()) {
      code7 = matcher5.replaceFirst("while (!(" + matcher5.group(1) + ")) { " + " YIELD(); " + "}");
      matcher5.reset(code7);
    }
    code = code7;
    String code8 = code;
    code8 = Pattern.compile("log\\.testOK\\(\\)").matcher(code8).replaceAll("throw new TestOK()");
    code = Pattern.compile("log\\.testFailed\\(\\)").matcher(code8).replaceAll("throw new TestFailed()");
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

     function GENERATE_MSG(time, msg) {
       log.generateMsg(mote, time, msg);
     };

     function SCRIPT_TIMEOUT() {
     """ +
       timeoutCode + ";\n" +
     """
       if (timeout_function != null) { timeout_function(); }
       log.log('TEST TIMEOUT\\n');
       throw new TestFailed();
     };

     function YIELD() {
       SEMAPHORE_SIM.release();
       SEMAPHORE_SCRIPT.acquire(); // Wait for simulation here.
       if (TIMEOUT) { SCRIPT_TIMEOUT(); }
       if (SHUTDOWN) { throw new Shutdown(); }
       msg = new java.lang.String(msg);
       node.setMoteMsg(mote, msg);
     };

     function write(mote, msg) {
       mote.getInterfaces().getLog().writeString(msg);
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
