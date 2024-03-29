/*
 * Copyright (c) 2012, Swedish Institute of Computer Science.
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

package org.contikios.cooja.util;

import de.sciss.syntaxpane.actions.DefaultSyntaxAction;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.Action;
import javax.swing.JMenuItem;
import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.WatchpointMote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSyntaxRemoveBreakpoint extends DefaultSyntaxAction {
  private static final Logger logger = LoggerFactory.getLogger(JSyntaxRemoveBreakpoint.class);

  public JSyntaxRemoveBreakpoint() {
    super("removebreakpoint");
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    JMenuItem menuItem = (JMenuItem) e.getSource();
    Action action = menuItem.getAction();
    WatchpointMote watchpointMote = (WatchpointMote) action.getValue("WatchpointMote");
    if (watchpointMote == null) {
      logger.warn("Error: No source, cannot configure breakpoint");
      return;
    }

    File file = (File) action.getValue("WatchpointFile");
    Integer line = (Integer) action.getValue("WatchpointLine");
    var address = (Long) action.getValue("WatchpointAddress");
    if (file == null || line == null || address == null) {
      logger.warn("Error: Bad breakpoint info, cannot remove breakpoint");
      return;
    }
    for (Watchpoint w: watchpointMote.getBreakpoints()) {
      if (file.equals(w.getCodeFile()) && line.equals(w.getLineNumber()) && address.equals(w.getExecutableAddress())) {
        watchpointMote.removeBreakpoint(w);
      }
    }

  }
}
