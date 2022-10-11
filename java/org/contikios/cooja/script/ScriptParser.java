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

    code = fixNewlines(code);

    code = stripMultiLineComments(code);

    code = stripSingleLineComments(code);

    code = parseTimeout(code);

    code = parseTimeoutWithAction(code);

    code = replaceYieldThenWaitUntils(code);

    code = replaceYields(code);

    code = replaceWaitUntils(code);

    code = replaceTestStatus(code);

    this.code = code;
  }

  private static String fixNewlines(String code) {
    code = code.replaceAll("\r\n", "\n");
    code = "\n" + code + "\n";
    return code;
  }

  private static String stripSingleLineComments(String code) {
    /* TODO Handle strings */
    Pattern pattern = Pattern.compile("//.*\n");
    Matcher matcher = pattern.matcher(code);
    code = matcher.replaceAll("\n");
    return code;
  }

  private static String stripMultiLineComments(String code) {
    /* TODO Handle strings */
    Pattern pattern =
      Pattern.compile("/\\*([^*]|\n|(\\*+([^*/]|\n)))*\\*+/");
    Matcher matcher = pattern.matcher(code);

    while (matcher.find()) {
      String match = matcher.group();
      int newLines = match.split("\n").length;
      code = matcher.replaceFirst("\n".repeat(newLines));
      matcher.reset(code);
    }
    return code;
  }

  private String parseTimeout(String code) throws ScriptSyntaxErrorException {
    Pattern pattern = Pattern.compile(
        "TIMEOUT\\(" +
        "(\\d+)" /* timeout */ +
        "\\)"
    );
    Matcher matcher = pattern.matcher(code);

    if (!matcher.find()) {
      return code;
    }

    if (timeoutTime > 0) {
      throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
    }

    timeoutTime = Long.parseLong(matcher.group(1))*Simulation.MILLISECOND;
    timeoutCode = ";";

    matcher.reset(code);
    code = matcher.replaceFirst(";");

    matcher.reset(code);
    if (matcher.find()) {
      throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
    }
    return code;
  }

  private String parseTimeoutWithAction(String code) throws ScriptSyntaxErrorException {
    Pattern pattern = Pattern.compile(
        "TIMEOUT\\(" +
        "(\\d+)" /* timeout */ +
        "\\s*,\\s*" +
        "(.*)" /* code */ +
        "\\)"
    );
    Matcher matcher = pattern.matcher(code);

    if (!matcher.find()) {
      return code;
    }

    if (timeoutTime > 0) {
      throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
    }

    timeoutTime = Long.parseLong(matcher.group(1))*Simulation.MILLISECOND;
    timeoutCode = replaceTestStatus(matcher.group(2));

    matcher.reset(code);
    code = matcher.replaceFirst(";");

    matcher.reset(code);
    if (matcher.find()) {
      throw new ScriptSyntaxErrorException("Only one timeout handler allowed");
    }
    return code;
  }

  private static String replaceYields(String code) {
    Pattern pattern = Pattern.compile(
        "YIELD\\(\\)"
    );
    return pattern.matcher(code).replaceAll("SCRIPT_SWITCH()");
  }

  private static String replaceYieldThenWaitUntils(String code) {
    Pattern pattern = Pattern.compile(
        "YIELD_THEN_WAIT_UNTIL\\(" +
        "(.*)" /* expression */ +
        "\\)"
    );
    Matcher matcher = pattern.matcher(code);

    while (matcher.find()) {
      code = matcher.replaceFirst(
          "YIELD(); WAIT_UNTIL(" + matcher.group(1) + ")");
      matcher.reset(code);
    }

    return code;
  }

  private static String replaceWaitUntils(String code) {
    Pattern pattern = Pattern.compile(
        "WAIT_UNTIL\\(" +
        "(.*)" /* expression */ +
        "\\)"
    );
    Matcher matcher = pattern.matcher(code);

    while (matcher.find()) {
      code = matcher.replaceFirst(
          "while (!(" + matcher.group(1) + ")) { " +
          " SCRIPT_SWITCH(); " +
      "}");
      matcher.reset(code);
    }

    return code;
  }

  private static String replaceTestStatus(String code) {
    code = Pattern.compile("log\\.testOK\\(\\)").matcher(code).replaceAll("throw new TestOK()");
    return Pattern.compile("log\\.testFailed\\(\\)").matcher(code).replaceAll("throw new TestFailed()");
  }

  public String getJSCode() {
    return getJSCode(code, timeoutCode);
  }
    
  public static String getJSCode(String code, String timeoutCode) {
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
    """ +
    "timeout_function = null; " +
    "function run() { try {" +
    "SEMAPHORE_SIM.acquire(); " +
    "SEMAPHORE_SCRIPT.acquire(); " + /* STARTUP BLOCKS HERE! */
    "if (TIMEOUT) { SCRIPT_TIMEOUT(); } " +
    "if (SHUTDOWN) { throw new Shutdown(); } " +
    "msg = new java.lang.String(msg); " +
    "node.setMoteMsg(mote, msg); " +
    code + 
    "\n" +
    "\n" +
    "while (true) { SCRIPT_SWITCH(); } " /* SCRIPT ENDED */+
    "} catch (error) { " +
    "if (error instanceof TestOK) return 0; " +
    "if (error instanceof TestFailed) return 1; " +
    "if (error instanceof Shutdown) return -1; " +
    "throw(error); }" +
    "};" +
    "\n" +
    "function GENERATE_MSG(time, msg) { " +
    " log.generateMessage(time, msg); " +
    "};\n" +
    "\n" +
    "function SCRIPT_TIMEOUT() { " +
    timeoutCode + "; " +
    " if (timeout_function != null) { timeout_function(); } " +
    " log.log('TEST TIMEOUT\\n'); " +
    " throw new TestFailed(); " +
    "};\n" +
    "\n" +
    "function SCRIPT_SWITCH() { " +
    " SEMAPHORE_SIM.release(); " +
    " SEMAPHORE_SCRIPT.acquire(); " /* SWITCH BLOCKS HERE! */ +
    " if (TIMEOUT) { SCRIPT_TIMEOUT(); } " +
    " if (SHUTDOWN) { throw new Shutdown(); } " +
    " msg = new java.lang.String(msg); " +
    " node.setMoteMsg(mote, msg); " +
    "};\n" +
    "\n" +
    "function write(mote,msg) { " +
    " mote.getInterfaces().getLog().writeString(msg); " +
    "};\n" +
    "run();\n";
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
