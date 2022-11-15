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

package org.contikios.cooja;


/**
 * The purpose of CoreComm is to communicate with a compiled Contiki system
 * using Java Native Interface (JNI). Each implemented class (named
 * Lib[number]), loads a shared library which belongs to one mote type. The
 * reason for this somewhat strange design is that once loaded, a native library
 * cannot be unloaded in Java (in the current versions available). Therefore, if
 * we wish to load several libraries, the names and associated native functions
 * must have unique names. And those names are defined via the calling class in
 * JNI. For example, the corresponding function for a native tick method in
 * class Lib1 will be named Java_org_contikios_cooja_corecomm_Lib1_tick. When creating
 * a new mote type, the main Contiki source file is built with function
 * names compatible with the next available corecomm class. This also implies
 * that even if a mote type is deleted, a new one cannot be created using the
 * same corecomm class without restarting the JVM and thus the entire
 * simulation.
 * <p>
 * Each implemented CoreComm class needs read-access to the following core
 * variables:
 * <ul>
 * <li>referenceVar
 * </ul>
 * and the following native functions:
 * <ul>
 * <li>tick()
 * <li>init()
 * <li>getReferenceAbsAddr()
 * <li>getMemory(int start, int length, byte[] mem)
 * <li>setMemory(int start, int length, byte[] mem)
 * </ul>
 *
 * @author Fredrik Osterlind
 */
public abstract class CoreComm {

  /**
   * Ticks a mote once.
   */
  public abstract void tick();

  /**
   * Returns the absolute memory address of the reference variable.
   */
  public abstract long getReferenceAddress();
}
