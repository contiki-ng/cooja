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
 */

package org.contikios.cooja.plugins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Plugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks radio events to sum up transmission, reception, and radio on times.
 * This plugin can be run without visualization, i.e. from a Contiki test.
 *
 * @author Fredrik Osterlind, Adam Dunkels
 */
@ClassDescription("Mote radio duty cycle")
@PluginType(PluginType.PType.SIM_PLUGIN)
public class PowerTracker implements Plugin {
  private static final Logger logger = LoggerFactory.getLogger(PowerTracker.class);

  private static final int POWERTRACKER_UPDATE_INTERVAL = 100; /* ms */

  private static final int COLUMN_MOTE = 0;
  private static final int COLUMN_RADIOON = 1;
  private static final int COLUMN_RADIOTX = 2;
  private static final int COLUMN_RADIORX = 3;

  private final Simulation simulation;
  private final ArrayList<MoteTracker> moteTrackers = new ArrayList<>();

  private JTable table;
  private int tableMaxRadioOnIndex = -1;

  private final VisPlugin frame;

  public PowerTracker(final Simulation simulation, final Cooja gui) {
    this.simulation = simulation;

    /* Automatically add/delete motes */
    simulation.getMoteTriggers().addTrigger(this, (event, m) -> {
      if (event == EventTriggers.AddRemove.ADD) {
        addMote(m);
      } else {
        removeMote(m);
      }
      table.invalidate();
      table.repaint();
    });
    frame = Cooja.isVisualized() ? new VisPlugin("PowerTracker", gui, this) : null;
    for (Mote m: simulation.getMotes()) {
      addMote(m);
    }

    if (!Cooja.isVisualized()) {
      return;
    }

    AbstractTableModel model = new AbstractTableModel() {
      @Override
      public int getRowCount() {
        return moteTrackers.size()+1;
      }
      @Override
      public int getColumnCount() {
        return 4;
      }
      @Override
      public String getColumnName(int col) {
        if (col == COLUMN_MOTE) {
          return "Mote";
        }
        if (col == COLUMN_RADIOON) {
          return "Radio on (%)";
        }
        if (col == COLUMN_RADIOTX) {
          return "Radio TX (%)";
        }
        if (col == COLUMN_RADIORX) {
          return "Radio RX (%)";
        }
        return null;
      }
      @Override
      public Object getValueAt(int rowIndex, int col) {
        if (rowIndex < 0 || rowIndex >= moteTrackers.size()+1) {
          return null;
        }

        if (rowIndex == moteTrackers.size()) {
          /* Average */
          long radioOn = 0;
          long radioTx = 0;
          long radioRx = 0;
          long duration = 0;
          for (MoteTracker mt: moteTrackers) {
            radioOn += mt.radioOn;
            radioTx += mt.radioTx;
            radioRx += mt.radioRx;

            duration += mt.duration;
          }

          if (col == COLUMN_MOTE) {
            return "AVERAGE";
          }
          if (col == COLUMN_RADIOON) {
            return String.format("%2.2f%%", 100.0*radioOn/duration);
          }
          if (col == COLUMN_RADIOTX) {
            return String.format("%2.2f%%", 100.0*radioTx/duration);
          }
          if (col == COLUMN_RADIORX) {
            return String.format("%2.2f%%", 100.0*radioRx/duration);
          }
          return null;
        }

        MoteTracker rule = moteTrackers.get(rowIndex);
        if (col == COLUMN_MOTE) {
          return rule.mote.toString();
        }
        if (col == COLUMN_RADIOON) {
          return String.format("%2.2f%%", 100.0*rule.getRadioOnRatio());
        }
        if (col == COLUMN_RADIOTX) {
          return String.format("%2.2f%%", 100.0*rule.getRadioTxRatio());
        }
        if (col == COLUMN_RADIORX) {
          return String.format("%2.2f%%", 100.0*rule.getRadioRxRatio());
        }
        return null;
      }
    };
    table = new JTable(model) {
      @Override
      public String getToolTipText(MouseEvent e) {
        java.awt.Point p = e.getPoint();
        int rowIndex = table.rowAtPoint(p);
        if (rowIndex < 0 || rowIndex >= moteTrackers.size()) {
          return null;
        }
        MoteTracker mt = moteTrackers.get(rowIndex);
        return "<html><pre>" + mt.toString() + "</html>";
      }
    };
    table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (row == tableMaxRadioOnIndex) {
          setBackground(isSelected ? table.getSelectionBackground():Color.RED);
          setForeground(isSelected ? table.getSelectionForeground():Color.WHITE);
        } else {
          setBackground(isSelected ? table.getSelectionBackground():table.getBackground());
          setForeground(isSelected ? table.getSelectionForeground():table.getForeground());
        }
        return c;
      }
    });

    Box control = Box.createHorizontalBox();
    control.add(Box.createHorizontalGlue());
    control.add(new JButton(new AbstractAction("Print to console/Copy to clipboard") {
      @Override
      public void actionPerformed(ActionEvent e) {
        String output = radioStatistics(true, true, false);
        logger.info("PowerTracker output:\n\n" + output);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(output), null);
      }
    }));
    control.add(new JButton(new AbstractAction("Reset") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Runnable r = PowerTracker.this::reset;
        if (simulation.isRunning()) {
          simulation.invokeSimulationThread(r);
        } else {
          r.run();
        }
      }
    }));

    frame.getContentPane().add(BorderLayout.CENTER, new JScrollPane(table));
    frame.getContentPane().add(BorderLayout.SOUTH, control);
    frame.setSize(400, 400);

    repaintTimer.start();
  }

  public MoteTracker getMoteTrackerOf(Mote mote) {
	  for (MoteTracker mt : moteTrackers) {
		  if (mt.mote == mote) {
			  return mt;
		  }
	  }
	  return null;
  }

  public String radioStatistics(boolean radioHW, boolean radioRXTX, boolean onlyAverage) {
    StringBuilder sb = new StringBuilder();

    /* Average */
    long radioOn = 0;
    long radioTx = 0;
    long radioRx = 0;
    long radioInterfered = 0;
    long duration = 0;
    for (MoteTracker mt: moteTrackers) {
      radioOn += mt.radioOn;
      radioTx += mt.radioTx;
      radioRx += mt.radioRx;
      radioInterfered += mt.radioInterfered;

      duration += mt.duration;
    }
    if (radioHW) {
      sb.append(String.format("AVG" + " ON " + (radioOn + " us ") + "%2.2f %%", 100.0 * radioOn / duration)).append("\n");
    }
    if (radioRXTX) {
      sb.append(String.format("AVG" + " TX " + (radioTx + " us ") + "%2.2f %%", 100.0 * radioTx / duration)).append("\n");
      sb.append(String.format("AVG" + " RX " + (radioRx + " us ") + "%2.2f %%", 100.0 * radioRx / duration)).append("\n");
      sb.append(String.format("AVG" + " INT " + (radioInterfered + " us ") + "%2.2f %%", 100.0 * radioInterfered / duration)).append("\n");
    }

    if (onlyAverage) {
      return sb.toString();
    }

    for (MoteTracker mt: moteTrackers) {
      sb.append(mt.toString(radioHW, radioRXTX));
    }
    return sb.toString();
  }

  public static class MoteTracker {
    /* last radio state */
    private boolean radioWasOn;
    private RadioState lastRadioState;
    private long lastUpdateTime;

    /* accumulating radio state durations */
    long duration;
    long radioOn;
    long radioTx;
    long radioRx;
    long radioInterfered;

    private final Simulation simulation;
    private Mote mote;
    private Radio radio;

    MoteTracker(Mote mote) {
      this.simulation = mote.getSimulation();
      this.mote = mote;
      this.radio = mote.getInterfaces().getRadio();

      radioWasOn = radio.isRadioOn();
      if (radio.isTransmitting()) {
        lastRadioState = RadioState.TRANSMITTING;
      } else if (radio.isReceiving()) {
        lastRadioState = RadioState.RECEIVING;
      } else if (radio.isInterfered()){
        lastRadioState = RadioState.INTERFERED;
      } else {
        lastRadioState = RadioState.IDLE;
      }
      lastUpdateTime = simulation.getSimulationTime();
      radio.getRadioEventTriggers().addTrigger(this, this::trigger);
    }

    private void trigger(Radio.RadioEvent event, Radio radio) {
      update();
    }

    void update() {
      long now = simulation.getSimulationTime();

      accumulateDuration(now - lastUpdateTime);

      /* Radio on/off */
      if (radioWasOn) {
        accumulateRadioOn(now - lastUpdateTime);
      }

      /* Radio tx/rx */
      if (lastRadioState == RadioState.TRANSMITTING) {
        accumulateRadioTx(now - lastUpdateTime);
      } else if (lastRadioState == RadioState.RECEIVING) {
        accumulateRadioRx(now - lastUpdateTime);
      } else if (lastRadioState == RadioState.INTERFERED) {
        accumulateRadioIntefered(now - lastUpdateTime);
      }

      /* Await next radio event */
      if (radio.isTransmitting()) {
        lastRadioState = RadioState.TRANSMITTING;
      } else if (!radio.isRadioOn()) {
        lastRadioState = RadioState.IDLE;
      } else if (radio.isInterfered()) {
        lastRadioState = RadioState.INTERFERED;
      } else if (radio.isReceiving()) {
        lastRadioState = RadioState.RECEIVING;
      } else {
        lastRadioState = RadioState.IDLE;
      }
      radioWasOn = radio.isRadioOn();
      lastUpdateTime = now;
    }

    void accumulateDuration(long t) {
      duration += t;
    }
    void accumulateRadioOn(long t) {
      radioOn += t;
    }
    void accumulateRadioTx(long t) {
      radioTx += t;
    }
    void accumulateRadioRx(long t) {
      radioRx += t;
    }
    void accumulateRadioIntefered(long t) {
      radioInterfered += t;
    }

    double getRadioOnRatio() {
      return 1.0*radioOn/duration;
    }

    double getRadioTxRatio() {
      return 1.0*radioTx/duration;
    }

    double getRadioInterferedRatio() {
      return 1.0*radioInterfered/duration;
    }

    double getRadioRxRatio() {
      return 1.0*radioRx/duration;
    }

    Mote getMote() {
      return mote;
    }

    void dispose() {
      radio.getRadioEventTriggers().removeTrigger(this, this::trigger);
      radio = null;
      mote = null;
    }

    @Override
    public String toString() {
      return toString(true, true);
    }
    String toString(boolean radioHW, boolean radioRXTX) {
      StringBuilder sb = new StringBuilder();
      String moteString = mote.toString().replace(' ', '_');

      sb.append(moteString).append(" MONITORED ").append(duration).append(" us\n");
      if (radioHW) {
        sb.append(String.format(moteString + " ON " + (radioOn + " us ") + "%2.2f %%", 100.0 * getRadioOnRatio())).append("\n");
      }
      if (radioRXTX) {
        sb.append(String.format(moteString + " TX " + (radioTx + " us ") + "%2.2f %%", 100.0 * getRadioTxRatio())).append("\n");
        sb.append(String.format(moteString + " RX " + (radioRx + " us ") + "%2.2f %%", 100.0 * getRadioRxRatio())).append("\n");
        sb.append(String.format(moteString + " INT " + (radioInterfered + " us ") + "%2.2f %%", 100.0 * getRadioInterferedRatio())).append("\n");
      }
      return sb.toString();
    }
  }

  public void reset() {
    while (!moteTrackers.isEmpty()) {
      removeMote(moteTrackers.get(0).mote);
    }
    for (Mote m: simulation.getMotes()) {
      addMote(m);
    }
  }

  private void addMote(Mote mote) {
    if (mote == null) {
      return;
    }

    var moteRadio = mote.getInterfaces().getRadio();
    if (moteRadio != null) {
      var tracker = new MoteTracker(mote);
      tracker.update();
      moteTrackers.add(tracker);
    }

    if (!Cooja.isVisualized()) {
      return;
    }
    frame.setTitle("PowerTracker: " + moteTrackers.size() + " motes");
  }

  private void removeMote(Mote mote) {
    /* Remove mote tracker(s) */
    MoteTracker[] trackers = moteTrackers.toArray(new MoteTracker[0]);
    for (MoteTracker t: trackers) {
      if (t.getMote() == mote) {
        t.dispose();
        moteTrackers.remove(t);
      }
    }

    if (!Cooja.isVisualized()) {
      return;
    }
    frame.setTitle("PowerTracker: " + moteTrackers.size() + " motes");
  }

  @Override
  public JInternalFrame getCooja() {
    return frame;
  }

  @Override
  public void startPlugin() {
  }

  @Override
  public void closePlugin() {
    /* Remove repaint timer */
    repaintTimer.stop();
    simulation.getMoteTriggers().deleteTriggers(this);
    /* Remove mote trackers */
    for (Mote m: simulation.getMotes()) {
      removeMote(m);
    }
    if (!moteTrackers.isEmpty()) {
      logger.error("Mote observers not cleaned up correctly");
      for (MoteTracker t: moteTrackers.toArray(new MoteTracker[0])) {
        t.dispose();
      }
    }
  }

  public enum RadioState {
    IDLE, RECEIVING, TRANSMITTING, INTERFERED
  }

  private final Timer repaintTimer = new Timer(POWERTRACKER_UPDATE_INTERVAL, new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      /* Identify max radio on */
      double maxRadioOn = 0;
      int maxRadioOnIndex = -1;
      for (int i=0; i < moteTrackers.size(); i++) {
        MoteTracker mt = moteTrackers.get(i);
        if (mt.getRadioOnRatio() > maxRadioOn) {
          maxRadioOn = mt.getRadioOnRatio();
          maxRadioOnIndex = i;
        }
      }
      if (maxRadioOnIndex >= 0) {
        tableMaxRadioOnIndex = maxRadioOnIndex;
      }

      table.repaint();
    }
  });

  @Override
  public Collection<Element> getConfigXML() {
    return null;
  }
  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    return true;
  }

}
