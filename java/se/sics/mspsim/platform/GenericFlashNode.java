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

package se.sics.mspsim.platform;

import se.sics.mspsim.chip.ExternalFlash;
import se.sics.mspsim.chip.FileStorage;
import se.sics.mspsim.core.MSP430;

public abstract class GenericFlashNode<FlashType extends ExternalFlash> extends GenericNode {
  protected final FlashType flash;
  protected String flashFile;

  public GenericFlashNode(String id, MSP430 cpu, FlashType flash) {
    super(id, cpu);
    this.flash = flash;
    registry.registerComponent("xmem", flash);
  }

  public FlashType getFlash() {
    return flash;
  }

  @Override
  public void setupNode() {
    // create a filename for the flash file
    // This should be possible to take from a config file later!
    String fileName = config.getProperty("flashfile");
    if (fileName == null) {
      fileName = firmwareFile;
      if (fileName != null) {
        int ix = fileName.lastIndexOf('.');
        if (ix > 0) {
          fileName = fileName.substring(0, ix);
        }
        fileName = fileName + ".flash";
      }
    }
    if (DEBUG) System.out.println("Using flash file: " + (fileName == null ? "no file" : fileName));
    flashFile = fileName;
    if (flashFile != null) {
      getFlash().setStorage(new FileStorage(flashFile));
    }
  }
}
