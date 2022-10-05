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

package org.contikios.cooja.mspmote;

import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.dialogs.AbstractCompileDialog;

public class MspCompileDialog extends AbstractCompileDialog {
  public static boolean showDialog(Simulation sim, MspMoteType moteType) {
    final AbstractCompileDialog dialog = new MspCompileDialog(sim, moteType);
    /* Show dialog and wait for user */
    dialog.setVisible(true); /* BLOCKS */
    return dialog.createdOK();
  }

  private MspCompileDialog(Simulation sim, MspMoteType moteType) {
    super(sim, moteType);
    setTitle("Create Mote Type: Compile Contiki for " + moteType.getMoteType());
    addCompilationTipsTab(tabbedPane);
  }

  @Override
  public Class<? extends MoteInterface>[] getAllMoteInterfaces() {
	  return ((MspMoteType)moteType).getAllMoteInterfaceClasses();
  }
  @Override
  public Class<? extends MoteInterface>[] getDefaultMoteInterfaces() {
	  return ((MspMoteType)moteType).getDefaultMoteInterfaceClasses();
  }

  private static void addCompilationTipsTab(JTabbedPane parent) {
    JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.append("# Without low-power radio:\n" +
    		"DEFINES=NETSTACK_MAC=nullmac_driver,NETSTACK_RDC=nullrdc_noframer_driver,CC2420_CONF_AUTOACK=0\n" +
    		"# (remember to \"make clean\" after changing compilation flags)"
    );

    parent.addTab("Tips", null, new JScrollPane(textArea), "Compilation tips");
  }

  @Override
  public boolean canLoadFirmware(String name) {
    return name.endsWith("." + moteType.getMoteType()) || name.equals("main.exe");
  }
}
