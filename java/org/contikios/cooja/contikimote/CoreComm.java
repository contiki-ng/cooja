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
  /**
   * Loads library libFile with a scope.
   *
   * @param scope Scope to load the library file in
   * @param libFile Library file
   */
  CoreComm(SegmentScope scope, File libFile) {
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
}
