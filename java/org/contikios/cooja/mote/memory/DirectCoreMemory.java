/*
 * Copyright (c) 2021, alexrayne
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
 */

package org.contikios.cooja.mote.memory;

import java.util.Arrays;
import java.util.Map;
import org.contikios.cooja.CoreComm;

/**
 * A memory that is backed by an array.
 *
 * @author Enrico Joerns
 */
public class DirectCoreMemory implements MemoryInterface {

  private final long startAddress;
  private final long offset;
  private final int  memory_size;
  //private final byte wrthrogh[];
  private final MemoryLayout layout;
  private final boolean readonly;
  private final Map<String, Symbol> symbols;// XXX Allow to set symbols
  private final CoreComm myCore;
  
  public static final boolean READONLY = true;
  public static final boolean WRITABLE = false;
  
  public DirectCoreMemory(CoreComm comm, long offset, long address, int size, MemoryLayout layout, Map<String, Symbol> symbols) {
    this(comm, offset, address, size, layout, WRITABLE, symbols);
  }

  public DirectCoreMemory(CoreComm comm, long offset, long address, int size, MemoryLayout layout, boolean readonly, Map<String, Symbol> symbols) {
    this.myCore	= comm;
    this.startAddress = address;
    this.offset = offset;
    this.layout = layout;
    this.memory_size = size;
    this.readonly = readonly;
    this.symbols = symbols;
  }

  @Override
  public byte[] getMemory() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  final public 
  boolean in_mem(long adr, long len) {
     return (adr >= startAddress) && ((adr + len ) <= (startAddress + memory_size) );
  }

  /**
   * XXX Should addr be the relative or the absolute address of this section?
   * @param addr
   * @param size
   * @return
   * @throws org.contikios.cooja.mote.memory.MemoryInterface.MoteMemoryException 
   */
  @Override
  public byte[] getMemorySegment(long addr, int size) throws MoteMemoryException {
    if(!in_mem(addr, size)) {
        throw new MoteMemoryException("get out of memory");
    }
    
    byte[] ret = new byte[size];
    myCore.getMemory( (int)(addr-offset), size, ret);
    return ret;
  }

  @Override
  public void setMemorySegment(long addr, byte[] data) throws MoteMemoryException {
    if (readonly) {
      throw new MoteMemoryException("Invalid write access for readonly memory");
    }
    
    if(!in_mem(addr, data.length)) {
        throw new MoteMemoryException("set out of memory");
    }
    
    myCore.setMemory((int)(addr-offset), data.length, data);
  }

  @Override
  public void clearMemory() {
    byte[] zero = new byte[memory_size];
    Arrays.fill(zero, (byte)0);
    myCore.setMemory((int)(startAddress), memory_size, zero);
  }

  @Override
  public long getStartAddr() {
    return startAddress;
  }

  @Override
  public int getTotalSize() {
    return memory_size;
  }

  @Override
  public Map<String, Symbol> getSymbolMap() {
    return symbols;
  }

  @Override
  public MemoryLayout getLayout() {
    return layout;
  }

  @Override
  public boolean addSegmentMonitor(SegmentMonitor.EventType flag, long address, int size, SegmentMonitor monitor) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean removeSegmentMonitor(long address, int size, SegmentMonitor monitor) {
    throw new UnsupportedOperationException("Not supported yet.");
  }
  
}
