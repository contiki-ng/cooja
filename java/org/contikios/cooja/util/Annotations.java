/*
 * Copyright (c) 2023, RISE Research Institutes of Sweden AB.
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
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.util;

import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.SupportedArguments;

/**
 * Utility methods for annotations.
 */
public class Annotations {
  /**
   * Prevent instances of this class
   */
  private Annotations() {}

  /**
   * Return true if a mote is compatible with an annotation.
   */
  public static boolean isCompatible(SupportedArguments annotation, Mote mote) {
    if (annotation == null || mote == null) {
      return true;
    }
    // Check mote interfaces.
    var moteInterfaces = mote.getInterfaces();
    for (var requiredMoteInterface : annotation.moteInterfaces()) {
      if (moteInterfaces.getInterfaceOfType(requiredMoteInterface) == null) {
        return false;
      }
    }
    // Check mote type.
    var clazz = mote.getClass();
    for (var supportedMote : annotation.motes()) {
      if (supportedMote.isAssignableFrom(clazz)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return true if a radio medium is compatible with an annotation.
   */
  public static boolean isCompatible(SupportedArguments annotation, RadioMedium radioMedium) {
    if (annotation == null || radioMedium == null) {
      return true;
    }
    for (var o : annotation.radioMediums()) {
      if (o.isAssignableFrom(radioMedium.getClass())) {
        return true;
      }
    }
    return false;
  }
}
