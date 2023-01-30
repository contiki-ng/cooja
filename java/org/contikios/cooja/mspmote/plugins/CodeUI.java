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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.HighlightPainter;

import de.sciss.syntaxpane.DefaultSyntaxKit;
import de.sciss.syntaxpane.components.Markers;

import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.WatchpointMote;
import org.contikios.cooja.util.JSyntaxAddBreakpoint;
import org.contikios.cooja.util.JSyntaxRemoveBreakpoint;
import org.contikios.cooja.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Displays source code and allows a user to add and remove breakpoints.
 *
 * @author Fredrik Osterlind
 */
public class CodeUI extends JPanel {
  private static final Logger logger = LoggerFactory.getLogger(CodeUI.class);

  static {
    DefaultSyntaxKit.initKit();
  }

  private final JEditorPane codeEditor;
  private final HashMap<Integer, Integer> codeEditorLines = new HashMap<>();
  private File displayedFile;

  private static final HighlightPainter CURRENT_LINE_MARKER = new Markers.SimpleMarker(Color.ORANGE);
  private static final HighlightPainter SELECTED_LINE_MARKER = new Markers.SimpleMarker(Color.GREEN);
  private static final HighlightPainter BREAKPOINTS_MARKER = new Markers.SimpleMarker(Color.LIGHT_GRAY);
  private final Object currentLineTag;
  private final Object selectedLineTag;
  private final ArrayList<Object> breakpointsLineTags = new ArrayList<>();

  private JSyntaxAddBreakpoint actionAddBreakpoint;
  private JSyntaxRemoveBreakpoint actionRemoveBreakpoint;

  private final WatchpointMote mote;

  public CodeUI(WatchpointMote mote) {
    super(new BorderLayout());

    this.mote = mote;

    codeEditor = new JEditorPane();
    codeEditor.setContentType("text/c");
    codeEditor.setText("");
    codeEditor.setEditable(false);
    add(new JScrollPane(codeEditor), BorderLayout.CENTER);

    Highlighter hl = codeEditor.getHighlighter();
    currentLineTag = addHighlight(hl, 0, 0, CURRENT_LINE_MARKER);
    selectedLineTag = addHighlight(hl, 0, 0, SELECTED_LINE_MARKER);

    JPopupMenu popupMenu = codeEditor.getComponentPopupMenu();
    for (Component c: popupMenu.getComponents()) {
      if (c instanceof JMenuItem item) {
        var action = item.getAction();
        if (action instanceof JSyntaxAddBreakpoint breakpoint) {
          actionAddBreakpoint = breakpoint;
        } else if (action instanceof JSyntaxRemoveBreakpoint breakpoint) {
          actionRemoveBreakpoint = breakpoint;
        }
      }
    }

    popupMenu.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        /* Disable breakpoint actions */
        actionAddBreakpoint.setEnabled(false);
        actionRemoveBreakpoint.setEnabled(false);

        int line = getCodeEditorMouseLine();
        if (line < 1) {
          return;
        }

        /* Configure breakpoint menu options */
        /* XXX TODO We should ask for the file specified in the firmware, not
         * the actual file on disk. */
        var address = CodeUI.this.mote.getType().getExecutableAddressOf(displayedFile, line);
        if (address < 0) {
          return;
        }
        final int start = codeEditorLines.get(line);
        int end = codeEditorLines.get(line+1);
        changeHighlight(codeEditor.getHighlighter(), selectedLineTag, start, end);
        boolean hasBreakpoint = CodeUI.this.mote.breakpointExists(address);
        var breakPoint = hasBreakpoint ? actionRemoveBreakpoint : actionAddBreakpoint;
        breakPoint.setEnabled(true);
        breakPoint.putValue("WatchpointMote", CodeUI.this.mote);
        breakPoint.putValue("WatchpointFile", displayedFile);
        breakPoint.putValue("WatchpointLine", line);
        breakPoint.putValue("WatchpointAddress", address);
      }
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        changeHighlight(codeEditor.getHighlighter(), selectedLineTag, 0, 0);
      }
      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });

    displayNoCode(true);
  }

  private static Object addHighlight(Highlighter hl, int start, int end, HighlightPainter currentLineMarker) {
    try {
      return hl.addHighlight(start, end, currentLineMarker);
    } catch (BadLocationException ignored) {
      return null;
    }
  }

  private static void changeHighlight(Highlighter highlighter, Object selectedLineTag, int start, int end) {
    try {
      highlighter.changeHighlight(selectedLineTag, start, end);
    } catch (BadLocationException ignored) {
    }
  }

  public void updateBreakpoints() {
    Highlighter hl = codeEditor.getHighlighter();

    for (Object breakpointsLineTag: breakpointsLineTags) {
      hl.removeHighlight(breakpointsLineTag);
    }
    breakpointsLineTags.clear();

    for (Watchpoint w: mote.getBreakpoints()) {
      if (!w.getCodeFile().equals(displayedFile)) {
        continue;
      }

      final int start = codeEditorLines.get(w.getLineNumber());
      int end = codeEditorLines.get(w.getLineNumber()+1);
      Object lineTag = addHighlight(hl, start, end, BREAKPOINTS_MARKER);
      if (lineTag != null) {
        breakpointsLineTags.add(lineTag);
      }
    }
  }

  private int getCodeEditorMouseLine() {
    Point mousePos = codeEditor.getMousePosition();
    if (mousePos == null) {
      return -1;
    }
    int modelPos = codeEditor.viewToModel2D(mousePos);
    int line = 1;
    while (codeEditorLines.containsKey(line+1)) {
      int next = codeEditorLines.get(line+1);
      if (modelPos < next) {
        return line;
      }
      line++;
    }
    return -1;
  }

  /**
   * Remove any shown source code.
   */
  public void displayNoCode(final boolean markCurrent) {
    java.awt.EventQueue.invokeLater(() -> {
      displayedFile = null;
      codeEditor.setText("[no source displayed]");
      codeEditor.setEnabled(false);
      codeEditorLines.clear();
      displayLine(-1, markCurrent);
    });
  }

  /**
   * Display given source code and mark given line.
   *
   * @param codeFile Source code file
   * @param lineNr Line number
   */
  public void displayNewCode(final File codeFile, final int lineNr, final boolean markCurrent) {
    if (!codeFile.equals(displayedFile)) {
      /* Read from disk */
      final String data = StringUtils.loadFromFile(codeFile);
      if (data == null || data.isEmpty()) {
        displayNoCode(markCurrent);
        return;
      }
      codeEditor.setEnabled(true);

      String[] lines = data.split("\n");
      logger.info("Opening " + codeFile + " (" + lines.length + " lines)");
      int length = 0;
      codeEditorLines.clear();
      for (int line=1; line-1 < lines.length; line++) {
        codeEditorLines.put(line, length);
        length += lines[line-1].length()+1;
      }
      codeEditor.setText(data);
      displayedFile = codeFile;
      updateBreakpoints();
    }

    java.awt.EventQueue.invokeLater(() -> displayLine(lineNr, markCurrent));
  }

  /**
   * Mark given line number in shown source code.
   * Should be called from AWT thread.
   *
   * @param lineNumber Line number
   */
  private void displayLine(int lineNumber, boolean markCurrent) {
    if (markCurrent) {
      /* remove previous highlight */
      changeHighlight(codeEditor.getHighlighter(), currentLineTag, 0, 0);
    }

    if (lineNumber >= 0) {
      final int start = codeEditorLines.get(lineNumber);
      int end = codeEditorLines.get(lineNumber+1);
      if (markCurrent) {
        /* highlight code */
        changeHighlight(codeEditor.getHighlighter(), currentLineTag, start, end);
      }

      /* ensure visible */
      java.awt.EventQueue.invokeLater(() -> {
        try {
          Rectangle2D r = codeEditor.modelToView2D(start);
          if (r != null) {
            codeEditor.scrollRectToVisible(r.getBounds());
          }
        } catch (BadLocationException ignored) {
        }
      });
    }
  }
}
