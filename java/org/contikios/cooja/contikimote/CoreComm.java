/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
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

package org.contikios.cooja.contikimote;

import java.io.File;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * CoreComm loads a library file and keeps track of the method handles
 * to the tick and getReferenceAddress methods.
 */
// Do not bother end-user with warnings about internal Cooja details.
@SuppressWarnings("preview")
class CoreComm {
  private final SymbolLookup symbols;
  private final MethodHandle coojaTick;

  private final long dataStart;
  private final int dataSize;
  private final long bssStart;
  private final int bssSize;
  private final long commonStart;
  private final int commonSize;
  /**
   * Loads library libFile with a scope.
   *
   * @param scope Scope to load the library file in
   * @param libFile Library file
   * @param queryContiki Call helper functions in Contiki-NG (macOS)
   */
  CoreComm(SegmentScope scope, File libFile, boolean queryContiki) {
    symbols = SymbolLookup.libraryLookup(libFile.getAbsolutePath(), scope);
    var linker = Linker.nativeLinker();
    coojaTick = linker.downcallHandle(symbols.find("cooja_tick").get(),
            FunctionDescriptor.ofVoid());
    // Call cooja_init() in Contiki-NG.
    var coojaInit = linker.downcallHandle(symbols.find("cooja_init").get(),
            FunctionDescriptor.ofVoid());
    try {
      coojaInit.invokeExact();
    } catch (Throwable e) {
      throw new RuntimeException("Calling cooja_init failed: " + e.getMessage(), e);
    }
    if (queryContiki) {
      var getBssStart = linker.downcallHandle(symbols.find("cooja_bss_start").get(),
              FunctionDescriptor.of(ValueLayout.JAVA_LONG));
      try {
        bssStart = (long)getBssStart.invokeExact();
      } catch (Throwable e) {
        throw new RuntimeException("Calling cooja_bss_start failed: " + e.getMessage(), e);
      }
      var getBssSize = linker.downcallHandle(symbols.find("cooja_bss_size").get(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT));
      try {
        bssSize = (int)getBssSize.invokeExact();
      } catch (Throwable e) {
        throw new RuntimeException("Calling cooja_bss_size failed: " + e.getMessage(), e);
      }
      var getDataStart = linker.downcallHandle(symbols.find("cooja_data_start").get(),
              FunctionDescriptor.of(ValueLayout.JAVA_LONG));
      try {
        dataStart = (long)getDataStart.invokeExact();
      } catch (Throwable e) {
        throw new RuntimeException("Calling cooja_data_start failed: " + e.getMessage(), e);
      }
      var getDataSize = linker.downcallHandle(symbols.find("cooja_data_size").get(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT));
      try {
        dataSize = (int)getDataSize.invokeExact();
      } catch (Throwable e) {
        throw new RuntimeException("Calling cooja_data_size failed: " + e.getMessage(), e);
      }
      var getCommonStart = linker.downcallHandle(symbols.find("cooja_common_start").get(),
              FunctionDescriptor.of(ValueLayout.JAVA_LONG));
      try {
        commonStart = (long)getCommonStart.invokeExact();
      } catch (Throwable e) {
        throw new RuntimeException("Calling cooja_common_start failed: " + e.getMessage(), e);
      }
      var getCommonSize = linker.downcallHandle(symbols.find("cooja_common_size").get(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT));
      try {
        commonSize = (int)getCommonSize.invokeExact();
      } catch (Throwable e) {
        throw new RuntimeException("Calling cooja_common_size failed: " + e.getMessage(), e);
      }
    } else {
      dataStart = symbols.find("cooja_dataStart").get().address();
      dataSize = (int)symbols.find("cooja_dataSize").get().address();
      bssStart = symbols.find("cooja_bssStart").get().address();
      bssSize = (int)symbols.find("cooja_bssSize").get().address();
      commonStart = commonSize = 0;
    }
  }

  /**
   * Ticks a mote once.
   */
  void tick() {
    try {
      coojaTick.invokeExact();
    } catch (Throwable e) {
      throw new RuntimeException("Calling cooja_tick failed: " + e.getMessage(), e);
    }
  }

  /**
   * Returns the absolute memory address of the reference variable.
   */
  long getReferenceAddress() {
    return symbols.find("referenceVar").get().address();
  }

  /**
   * Returns the absolute address of the start of the data section.
   */
  long getDataStartAddress() {
    return dataStart;
  }

  /**
   * Returns the size of the data section.
   */
  int getDataSize() {
    return dataSize;
  }

  /**
   * Returns the absolute address of the start of the bss section.
   */
  long getBssStartAddress() {
    return bssStart;
  }

  /**
   * Returns the size of the bss section.
   */
  int getBssSize() {
    return bssSize;
  }

  /**
   * Returns the absolute address of the start of the common section.
   */
  long getCommonStartAddress() {
    return commonStart;
  }

  /**
   * Returns the size of the common section.
   */
  int getCommonSize() {
    return commonSize;
  }
}
