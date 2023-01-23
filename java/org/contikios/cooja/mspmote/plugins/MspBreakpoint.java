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

package org.contikios.cooja.mspmote.plugins;

import java.io.File;
import org.contikios.cooja.Breakpoint;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.util.StringUtils;
import se.sics.mspsim.core.Memory;
import se.sics.mspsim.core.MemoryMonitor;

/**
 * Mspsim watchpoint.
 *
 * @author Fredrik Osterlind
 */
public class MspBreakpoint extends Breakpoint {
  private final MspMote mspMote;
  private MemoryMonitor memoryMonitor;
  public MspBreakpoint(MspMote mote) {
    super(mote);
    mspMote = mote;
    /* expects setConfigXML(..) */
  }

  public MspBreakpoint(MspMote mote, long address, File codeFile, int lineNr) {
    super(mote, address, codeFile, lineNr);
    mspMote = mote;
    createMonitor();
  }

  @Override
  protected void createMonitor() {
    memoryMonitor = new MemoryMonitor.Adapter() {
      @Override
      public void notifyReadBefore(int addr, Memory.AccessMode mode, Memory.AccessType type) {
        if (type != Memory.AccessType.EXECUTE) {
          return;
        }

        mspMote.signalBreakpointTrigger(MspBreakpoint.this);
      }
    };
    mspMote.getCPU().addWatchPoint((int) address, memoryMonitor);


    // Remember code, to verify it when reloaded.
    if (sourceCode == null) {
      final String code = StringUtils.loadFromFile(codeFile);
      if (code != null) {
        String[] lines = code.split("\n");
        if (lineNr-1 < lines.length) {
          sourceCode = lines[lineNr-1].trim();
        }
      }
    }

  }

  @Override
  public void removed() {
    mspMote.getCPU().removeWatchPoint((int) address, memoryMonitor);
  }
}
