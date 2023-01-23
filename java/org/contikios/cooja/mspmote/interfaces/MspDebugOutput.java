/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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

package org.contikios.cooja.mspmote.interfaces;

import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.mote.memory.VarMemory;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.util.EventTriggers;
import se.sics.mspsim.core.Memory;
import se.sics.mspsim.core.MemoryMonitor;

/**
 * Observes writes to a special (hardcoded) Contiki variable: cooja_debug_ptr.
 * When the pointer is changed, the string that the pointer points to 
 * is outputted as log output from this mote interface.
 * <p>
 * Contiki code example:
 *  cooja_debug_ptr = "Almost non-intrusive debug output";
 * or simply:
 *  COOJA_DEBUG("Almost non-intrusive debug output");
 *  
 * @author Fredrik Osterlind
 */
@ClassDescription("Debugging output")
public class MspDebugOutput extends Log {
  private final static String CONTIKI_POINTER = "cooja_debug_ptr";
  
  private final MspMote mote;
  private final VarMemory mem;
  
  private String lastLog;
  private MemoryMonitor memoryMonitor;
  
  public MspDebugOutput(Mote mote) {
    this.mote = (MspMote) mote;
    this.mem = new VarMemory(this.mote.getMemory());

    if (!mem.variableExists(CONTIKI_POINTER)) {
      /* Disabled */
      return;
    }
    this.mote.getCPU().addWatchPoint((int) mem.getVariableAddress(CONTIKI_POINTER),
        memoryMonitor = new MemoryMonitor.Adapter() {
        @Override
        public void notifyWriteAfter(int adr, int data, Memory.AccessMode mode) {
          String msg = extractString(MspDebugOutput.this.mote.getMemory(), data);
          if (!msg.isEmpty()) {
            lastLog = "DEBUG: " + msg;
            getLogDataTriggers().trigger(EventTriggers.Update.UPDATE, new LogDataInfo(mote, lastLog));
          }
      }
    });
  }

  private static String extractString(MemoryInterface mem, int address) {
    StringBuilder sb = new StringBuilder();
    while (true) {
      byte[] data = mem.getMemorySegment(address, 8);
      address += 8;
      for (byte b: data) {
        if (b == 0) {
          return sb.toString();
        }
        sb.append((char)b);
        if (sb.length() > 128) {
          /* Maximum size */
          return sb + "...";
        }
      }
    }
  }

  @Override
  public String getLastLogMessage() {
    return lastLog;
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    return null;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
  }

  @Override
  public void removed() {
    super.removed();

    if (memoryMonitor != null) {
      mote.getCPU().removeWatchPoint((int) mem.getVariableAddress(CONTIKI_POINTER), memoryMonitor);
    }
  }
}
