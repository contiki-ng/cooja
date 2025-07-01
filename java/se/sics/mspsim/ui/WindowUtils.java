/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * This file is part of MSPSim.
 *
 * -----------------------------------------------------------------
 *
 * WindowUtils
 *
 * Author  : Joakim Eriksson, Niclas Finne, Fredrik Osterlind
 * Created : Sun Oct 21 22:00:00 2007
 */

package se.sics.mspsim.ui;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.prefs.Preferences;

public class WindowUtils {

  private static final Preferences prefs =
    Preferences.userNodeForPackage(WindowUtils.class);

  private static HashMap<Window,CloseListener> closeTable;
  private static HashMap<Window,String> exitTable;

  private WindowUtils() {
  }

  public static void saveWindowBounds(String key, Window window) {
    putWindowBounds(key, window);
    try {
      prefs.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void putWindowBounds(String key, Window window) {
    prefs.put("window." + key + ".bounds", getBounds(window));
  }

  public static void restoreWindowBounds(String key, Window window) {
    setBounds(window, prefs.get("window." + key + ".bounds", null));
  }

  private static String getBounds(Window window) {
    return String.valueOf(window.getX()) + ',' + window.getY() + ',' + window.getWidth() + ',' + window.getHeight();
  }

  private static void setBounds(Window window, String bounds) {
    String[] b;
    if ((bounds != null)
        && ((b = bounds.split(",")) != null)
        && b.length == 4) {
      try {
        window.setBounds(Integer.parseInt(b[0]),
                         Integer.parseInt(b[1]),
                         Integer.parseInt(b[2]),
                         Integer.parseInt(b[3]));
      } catch (Exception e) {
        e.printStackTrace();
        window.pack();
      }
    } else {
      window.setLocationByPlatform(true);
      window.pack();
    }
  }

  public static void clearState() {
    try {
      prefs.clear();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public synchronized static void addSaveOnClose(String key, Window window) {
    if (closeTable == null) {
      closeTable = new HashMap<>();
    }
    if (closeTable.get(window) == null) {
      CloseListener c = new CloseListener(key);
      window.addWindowListener(c);
      closeTable.put(window, c);
    }
  }

  public synchronized static void removeSaveOnClose(Window window) {
    if (closeTable != null) {
      CloseListener c = closeTable.remove(window);
      if (c != null) {
        window.removeWindowListener(c);
      }
    }
  }

  public synchronized static void addSaveOnShutdown(String key, Window window) {
    if (exitTable == null) {
      exitTable = new HashMap<>();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        var table = exitTable;
        if (table != null && !table.isEmpty()) {
          exitTable = null;
          for (var entry : table.entrySet()) {
            putWindowBounds(entry.getValue(), entry.getKey());
          }
          try {
            prefs.flush();
          } catch (Exception e2) {
            e2.printStackTrace();
          }
        }
      }));
    }
    exitTable.put(window, key);
  }

  public synchronized static void removeSaveOnShutdown(Window window) {
    if (exitTable != null) {
      exitTable.remove(window);
    }
  }



  // -------------------------------------------------------------------
  // Main
  // -------------------------------------------------------------------

  public static void main(String[] args) {
    if (args.length != 1 || !args[0].equals("-clearState")) {
      System.err.println("Usage: WindowUtils -clearState");
      System.exit(1);
    }
    clearState();
  }


  // -------------------------------------------------------------------
  // CloseListener
  // -------------------------------------------------------------------

  private static class CloseListener extends WindowAdapter {

    private final String key;

    CloseListener(String key) {
      this.key = key;
    }

    @Override
    public void windowClosing(WindowEvent e) {
      Window source = (Window) e.getSource();
      saveWindowBounds(key, source);
      source.removeWindowListener(this);
      closeTable.remove(source);
    }
  }
} // WindowUtils
