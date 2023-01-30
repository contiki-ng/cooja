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

package org.contikios.cooja.plugins;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButtonMenuItem;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.Mote;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.SimEventCentral.LogOutputEvent;
import org.contikios.cooja.SimEventCentral.LogOutputListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.Watchpoint;
import org.contikios.cooja.WatchpointMote;
import org.contikios.cooja.WatchpointMote.WatchpointListener;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.interfaces.Radio.RadioEvent;
import org.contikios.cooja.motes.AbstractEmulatedMote;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows events such as mote logs, LEDs, and radio transmissions, in a timeline.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Timeline")
@PluginType(PluginType.PType.SIM_STANDARD_PLUGIN)
public class TimeLine extends VisPlugin implements HasQuickHelp {
  public static final int LED_PIXEL_HEIGHT = 2;
  public static final int EVENT_PIXEL_HEIGHT = 4;
  public static final int TIME_MARKER_PIXEL_HEIGHT = 6;
  public static final int FIRST_MOTE_PIXEL_OFFSET = TIME_MARKER_PIXEL_HEIGHT + EVENT_PIXEL_HEIGHT;

  private static final Color COLOR_BACKGROUND = Color.WHITE;
  private static final boolean PAINT_ZERO_WIDTH_EVENTS = true;
  private static final int PAINT_MIN_WIDTH_EVENTS = 5;
  private static final int TIMELINE_UPDATE_INTERVAL = 100;

  private double currentPixelDivisor;

  private static final long[] ZOOM_LEVELS = {
  	1, 2, 5, 10,
  	20, 50, 100, 200, 500, 1000,
  	2000, 5000, 10000, 20000, 50000, 100000 };

  private boolean needZoomOut;

  private static final Logger logger = LoggerFactory.getLogger(TimeLine.class);

  private int paintedMoteHeight = EVENT_PIXEL_HEIGHT;
  private int paintEventMinWidth = PAINT_MIN_WIDTH_EVENTS;

  private final Simulation simulation;
  private final LogOutputListener newMotesListener;

  /* Experimental features: Use currently active plugin to filter Timeline Log outputs */
  private LogListener logEventFilterPlugin;
  private String      logEventFilterLast = "";
  private boolean     logEventFilterChanged = true;
  private boolean     logEventColorOfMote;

  private final MoteRuler timelineMoteRuler = new MoteRuler();
  private final JComponent timeline = new Timeline();

  private final ArrayList<Mote> highlightedMotes = new ArrayList<>();
  private final static Color HIGHLIGHT_COLOR = Color.CYAN;

  private final ArrayList<MoteObservation> activeMoteObservers = new ArrayList<>();

  private ArrayList<MoteEvents> allMoteEvents = new ArrayList<>();

  private boolean showRadioRXTX = true;
  private boolean showRadioChannels;
  private boolean showRadioOnoff = true;
  private boolean showLeds = true;
  private boolean showLogOutputs;
  private boolean showWatchpoints;

  private Point popupLocation;

  private final JCheckBox showWatchpointsCheckBox = new JCheckBox("Watchpoints", true);
  private final JCheckBox showLogsCheckBox = new JCheckBox("Log output", true);
  private final JCheckBox showLedsCheckBox = new JCheckBox("LEDs", true);
  private final JCheckBox showRadioOnoffCheckbox = new JCheckBox("Radio on/off", true);
  private final JCheckBox showRadioChannelsCheckbox = new JCheckBox("Radio channel", true);
  private final JCheckBox showRadioTXRXCheckbox = new JCheckBox("Radio traffic", true);

  /**
   * @param simulation Simulation
   * @param gui GUI
   */
  public TimeLine(final Simulation simulation, final Cooja gui) {
    super("Timeline", gui);
    this.simulation = simulation;
    currentPixelDivisor = 500;

    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    JMenu editMenu = new JMenu("Edit");
    JMenu motesMenu = new JMenu("Motes");
    JMenu eventsMenu = new JMenu("Events");
    JMenu viewMenu = new JMenu("View");
    JMenu zoomMenu = new JMenu("Zoom");

    menuBar.add(fileMenu);
    menuBar.add(editMenu);
    menuBar.add(viewMenu);
    menuBar.add(zoomMenu);
    menuBar.add(eventsMenu);
    menuBar.add(motesMenu);

    this.setJMenuBar(menuBar);
    motesMenu.add(new JMenuItem(new AbstractAction("Show motes...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox<Object> source = new JComboBox<>();
        source.addItem("All motes");
        for (Mote m1 : simulation.getMotes()) {
          source.addItem(m1);
        }
        Object[] description = {source};
        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessage(description);
        optionPane.setMessageType(JOptionPane.QUESTION_MESSAGE);
        String[] options = {"Cancel", "Show"};
        optionPane.setOptions(options);
        optionPane.setInitialValue(options[1]);
        JDialog dialog = optionPane.createDialog(Cooja.getTopParentContainer(), "Show mote in timeline");
        dialog.setVisible(true);

        if (optionPane.getValue() == null || !optionPane.getValue().equals("Show")) {
          return;
        }

        if ("All motes".equals(source.getSelectedItem())) {
          for (Mote m1 : simulation.getMotes()) {
            addMote(m1);
          }
        } else {
          addMote((Mote) source.getSelectedItem());
        }
      }
    }));
    var zoomInAction = new AbstractAction("Zoom in (Ctrl+)") {
      @Override
      public void actionPerformed(ActionEvent e) {
        zoomFinishLevel(-1, getCenterPointTime(), 0.5);
      }
    };
    zoomMenu.add(new JMenuItem(zoomInAction));
    var zoomOutAction = new AbstractAction("Zoom out (Ctrl-)") {
      @Override
      public void actionPerformed(ActionEvent e) {
        zoomFinishLevel(1, getCenterPointTime(), 0.5);
      }
    };
    zoomMenu.add(new JMenuItem(zoomOutAction));
    viewMenu.add(new JCheckBoxMenuItem(executionDetailsAction) {
	    @Override
	    public boolean isSelected() {
      		return executionDetails;
	    }
    });
    viewMenu.add(new JCheckBoxMenuItem(new AbstractAction("Use Mote colors") {
      @Override
      public void actionPerformed(ActionEvent e) {
        logEventColorOfMote = !logEventColorOfMote;
        timeline.repaint();
      }}) {
        @Override
        public boolean isSelected() {
            return logEventColorOfMote;
        }
    });

    JMenu widthMenu = new JMenu("Minimal event width");
    ButtonGroup minEvWidthButtonGroup = new ButtonGroup();
    for (final int s : new int[] { 1, 5, 10 }) {
      JRadioButtonMenuItem widthMenuItem = new JRadioButtonMenuItem(s + " px");
      widthMenuItem.setSelected(paintEventMinWidth == s);
      widthMenuItem.addActionListener(e -> {
        if (paintEventMinWidth != s) {
          paintEventMinWidth = s;
          TimeLine.this.timeline.repaint();
        }
      });
      minEvWidthButtonGroup.add(widthMenuItem);
      widthMenu.add(widthMenuItem);
    }
    viewMenu.addSeparator();
    viewMenu.add(widthMenu);

    fileMenu.add(new JMenuItem(new AbstractAction("Save to file...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        int returnVal = fc.showSaveDialog(Cooja.getTopParentContainer());
        if (returnVal != JFileChooser.APPROVE_OPTION) {
          return;
        }
        File saveFile = fc.getSelectedFile();
        if (saveFile.exists()) {
          String s1 = "Overwrite";
          String s2 = "Cancel";
          Object[] options = {s1, s2};
          int n = JOptionPane.showOptionDialog(
                  Cooja.getTopParentContainer(),
                  "A file with the same name already exists.\nDo you want to remove it?",
                  "Overwrite existing file?", JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE, null, options, s1);
          if (n != JOptionPane.YES_OPTION) {
            return;
          }
        }

        if (saveFile.exists() && !saveFile.canWrite()) {
          logger.error("No write access to file");
          return;
        }

        try (var outStream = Files.newBufferedWriter(saveFile.toPath(), UTF_8)) {
          // Output all events (sorted per mote).
          for (MoteEvents events : allMoteEvents) {
            for (MoteEvent ev : events.ledEvents) {
              outStream.write(events.mote + "\t" + ev.time + "\t" + ev + "\n");
            }
            for (MoteEvent ev : events.logEvents) {
              outStream.write(events.mote + "\t" + ev.time + "\t" + ev + "\n");
            }
            for (MoteEvent ev : events.radioChannelEvents) {
              outStream.write(events.mote + "\t" + ev.time + "\t" + ev + "\n");
            }
            for (MoteEvent ev : events.radioHWEvents) {
              outStream.write(events.mote + "\t" + ev.time + "\t" + ev + "\n");
            }
            for (MoteEvent ev : events.radioRXTXEvents) {
              outStream.write(events.mote + "\t" + ev.time + "\t" + ev + "\n");
            }
            for (MoteEvent ev : events.watchpointEvents) {
              outStream.write(events.mote + "\t" + ev.time + "\t" + ev + "\n");
            }
          }
        } catch (Exception ex) {
          logger.error("Could not write to file: " + saveFile);
        }
      }
    }));
    fileMenu.add(new JMenuItem(new AbstractAction("Print statistics to console") {
      @Override
      public void actionPerformed(ActionEvent e) {
        TimeLine.this.simulation.stopSimulation();
        logger.info(extractStatistics(true, true, true, true));
      }
    }));
    editMenu.add(new JMenuItem(new AbstractAction("Clear all timeline data") {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (simulation.isRunning()) {
          simulation.invokeSimulationThread(() -> clear());
        } else {
          clear();
        }
      }
    }));

    showRadioTXRXCheckbox.setToolTipText("Show radio transmissions, receptions, and collisions");
    showRadioTXRXCheckbox.setName("showRadioRXTX");
    showRadioTXRXCheckbox.addActionListener(e -> {
      showRadioRXTX = ((JCheckBox) e.getSource()).isSelected();
      recalculateMoteHeight();
    });
    eventsMenu.add(showRadioTXRXCheckbox);
    showRadioOnoffCheckbox.setToolTipText("Show radio hardware state");
    showRadioOnoffCheckbox.setSelected(showRadioOnoff);
    showRadioOnoffCheckbox.setName("showRadioHW");
    showRadioOnoffCheckbox.addActionListener(e -> {
      showRadioOnoff = ((JCheckBox) e.getSource()).isSelected();
      recalculateMoteHeight();
    });
    eventsMenu.add(showRadioOnoffCheckbox);
    showRadioChannelsCheckbox.setToolTipText("Show different radio channels");
    showRadioChannelsCheckbox.setSelected(showRadioChannels);
    showRadioChannelsCheckbox.setName("showRadioChannels");
    showRadioChannelsCheckbox.addActionListener(e -> {
      showRadioChannels = ((JCheckBox) e.getSource()).isSelected();
      recalculateMoteHeight();
    });
    eventsMenu.add(showRadioChannelsCheckbox);
    showLedsCheckBox.setToolTipText("Show LED state");
    showLedsCheckBox.setSelected(showLeds);
    showLedsCheckBox.setName("showLEDs");
    showLedsCheckBox.addActionListener(e -> {
      showLeds = ((JCheckBox) e.getSource()).isSelected();
      recalculateMoteHeight();
    });
    eventsMenu.add(showLedsCheckBox);
    showLogsCheckBox.setToolTipText("Show mote log output, such as printf()'s");
    showLogsCheckBox.setSelected(showLogOutputs);
    showLogsCheckBox.setName("showLogOutput");
    showLogsCheckBox.addActionListener(e -> {
      showLogOutputs = ((JCheckBox) e.getSource()).isSelected();
      // Check whether there is an active log listener that is used to filter logs.
      logEventFilterPlugin = simulation.getCooja().getPlugin(LogListener.class);
      // Invalidate filter stamp.
      logEventFilterLast = "";
      logEventFilterChanged = true;

      if (showLogOutputs) {
        if (logEventFilterPlugin != null) {
          logger.info("Filtering shown log outputs by use of " + Cooja.getDescriptionOf(logEventFilterPlugin) + " plugin");
        } else {
          logger.info("No active " + Cooja.getDescriptionOf(LogListener.class) + " plugin, not filtering log outputs");
        }
      }
      recalculateMoteHeight();
    });
    eventsMenu.add(showLogsCheckBox);
    showWatchpointsCheckBox.setToolTipText("Show code watchpoints (for emulated motes)");
    showWatchpointsCheckBox.setSelected(showWatchpoints);
    showWatchpointsCheckBox.setName("showWatchpoints");
    showWatchpointsCheckBox.addActionListener(e -> {
      showWatchpoints = ((JCheckBox) e.getSource()).isSelected();
      recalculateMoteHeight();
    });
    eventsMenu.add(showWatchpointsCheckBox);

    /* Box: events to observe */

    /* Panel: timeline canvas w. scroll pane and add mote button */
    var timelineScrollPane = new JScrollPane(
        timeline,
        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    timelineScrollPane.getHorizontalScrollBar().setUnitIncrement(50);
    timelineScrollPane.setRowHeaderView(timelineMoteRuler);
    timelineScrollPane.setBackground(Color.WHITE);

    /* Zoom in/out via keyboard*/
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), "zoomIn");
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK), "zoomIn");
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, KeyEvent.CTRL_DOWN_MASK), "zoomIn");
    getActionMap().put("zoomIn", zoomInAction);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), "zoomOut");
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, KeyEvent.CTRL_DOWN_MASK), "zoomOut");
    getActionMap().put("zoomOut", zoomOutAction);
    getContentPane().add(timelineScrollPane);

    recalculateMoteHeight();
    pack();

    numberMotesWasUpdated();

    // Automatically add/delete motes. This listener also observes mote log outputs.
    simulation.getEventCentral().addLogOutputListener(newMotesListener = ev -> {
      var mote = ev.getMote();
      var logEvent = new LogEvent(ev);
      // TODO: Optimize.
      for (var moteEvents: allMoteEvents) {
        if (moteEvents.mote == mote) {
          moteEvents.addLog(logEvent);
          break;
        }
      }
    });
    simulation.getMoteTriggers().addTrigger(this, (event, m) -> {
      if (event == EventTriggers.AddRemove.ADD) {
        addMote(m);
      } else {
        removeMote(m);
      }
    });
    for (Mote m: simulation.getMotes()) {
      addMote(m);
    }

    /* Update timeline for the duration of the plugin */
    repaintTimelineTimer.start();

    simulation.getMoteHighlightTriggers().addTrigger(this, (event, mote) -> {
      final Timer timer = new Timer(100, null);
      timer.addActionListener(e -> {
        // Count down.
        if (timer.getDelay() < 90) {
          timer.stop();
          highlightedMotes.remove(mote);
          repaint();
          return;
        }

        // Toggle highlight state.
        if (highlightedMotes.contains(mote)) {
          highlightedMotes.remove(mote);
        } else {
          highlightedMotes.add(mote);
        }
        timer.setDelay(timer.getDelay()-1);
        repaint();
      });
      timer.start();
    });

    /* XXX HACK: here we set the position and size of the window when it appears on a blank simulation screen. */
    this.setLocation(0, Cooja.getDesktopPane().getHeight() - 166);
    this.setSize(Cooja.getDesktopPane().getWidth(), 166);
  }

  @Override
  public void startPlugin() {
      super.startPlugin();
      
      showWatchpointsCheckBox.setSelected(showWatchpoints);
      showLogsCheckBox.setSelected(showLogOutputs);
      showLedsCheckBox.setSelected(showLeds);
      showRadioOnoffCheckbox.setSelected(showRadioOnoff);
      showRadioChannelsCheckbox.setSelected(showRadioChannels);
      showRadioTXRXCheckbox.setSelected(showRadioRXTX);
  }

  private void forceRepaintAndFocus(final long focusTime, final double focusCenter) {
    forceRepaintAndFocus(focusTime, focusCenter, true);
  }

  private void forceRepaintAndFocus(final long focusTime, final double focusCenter, final boolean mark) {
    lastRepaintSimulationTime = -1; /* Force repaint */
    repaintTimelineTimer.getActionListeners()[0].actionPerformed(null); /* Force size update*/
    EventQueue.invokeLater(() -> {
      int w = timeline.getVisibleRect().width;
      /* centerPixel-leftPixel <=> focusCenter*w; */
      int centerPixel = (int) (focusTime/currentPixelDivisor);
      int leftPixel = (int) (focusTime/currentPixelDivisor - focusCenter*w);
      timeline.scrollRectToVisible(new Rectangle(leftPixel, 0, w, 1));

      // Time ruler.
      if (mark) {
        mousePixelPositionX = centerPixel;
        mouseDownPixelPositionX = centerPixel;
        mousePixelPositionY = timeline.getHeight();
      }
    });
  }

  private void zoomFinish (final double zoomDivisor,
                           final long focusTime,
                           final double focusCenter) {
    currentPixelDivisor = Math.min(100000, Math.max(1, zoomDivisor));
    if (zoomDivisor != currentPixelDivisor) {
      logger.info("Zoom level: adjusted out-of-range " + zoomDivisor + " us/pixel");
    }
    forceRepaintAndFocus(focusTime, focusCenter);
  }

  private void zoomFinishLevel(int zoomOffset, long focusTime, double focusCenter) {
    var zoomLevel = 0;
    while (zoomLevel < ZOOM_LEVELS.length) {
      if (currentPixelDivisor <= ZOOM_LEVELS[zoomLevel]) break;
      zoomLevel++;
    }
    zoomFinish((double) ZOOM_LEVELS[Math.min(15, Math.max(0, zoomLevel + zoomOffset))], focusTime, focusCenter);
  }

  private long getCenterPointTime() {
      Rectangle r = timeline.getVisibleRect();
      int pixelX = r.x + r.width/2;
      if (popupLocation != null) {
        pixelX = popupLocation.x;
        popupLocation = null;
      }
      if (mousePixelPositionX > 0) {
        pixelX = mousePixelPositionX;
      }
      return (long) (pixelX*currentPixelDivisor);
  }

  public void clear() {
    for (MoteEvents me : allMoteEvents) {
      me.clear();
    }
    repaint();
  }


  private class MoteStatistics {
    Mote mote;
    long onTimeRedLED, onTimeGreenLED, onTimeBlueLED;
    int nrLogs;
    long radioOn;
    long onTimeRX, onTimeTX, onTimeInterfered;

    @Override
    public String toString() {
      return toString(true, true, true, true);
    }
    String toString(boolean logs, boolean leds, boolean radioHW, boolean radioRXTX) {
      long duration = simulation.getSimulationTime(); /* XXX */
      StringBuilder sb = new StringBuilder();
      String moteDesc = (mote!=null? String.valueOf(mote.getID()) :"AVERAGE") + " ";
      if (logs) {
        sb.append(moteDesc).append("nr_logs ").append(nrLogs).append("\n");
      }
      if (leds) {
        sb.append(moteDesc).append("led_red ").append(onTimeRedLED).append(" us ")
                .append(100.0 * ((double) onTimeRedLED / duration)).append(" %\n");
        sb.append(moteDesc).append("led_green ").append(onTimeGreenLED).append(" us ")
                .append(100.0 * ((double) onTimeGreenLED / duration)).append(" %\n");
        sb.append(moteDesc).append("led_blue ").append(onTimeBlueLED).append(" us ")
                .append(100.0 * ((double) onTimeBlueLED / duration)).append(" %\n");
      }
      if (radioHW) {
        sb.append(moteDesc).append("radio_on ").append(radioOn).append(" us ")
                .append(100.0 * ((double) radioOn / duration)).append(" %\n");
      }
      if (radioRXTX) {
        sb.append(moteDesc).append("radio_tx ").append(onTimeTX).append(" us ")
                .append(100.0 * ((double) onTimeTX / duration)).append(" %\n");
        sb.append(moteDesc).append("radio_rx ").append(onTimeRX).append(" us ")
                .append(100.0 * ((double) onTimeRX / duration)).append(" %\n");
        sb.append(moteDesc).append("radio_int ").append(onTimeInterfered)
                .append(" us ").append(100.0 * ((double) onTimeInterfered / duration)).append(" %\n");
      }
      return sb.toString();
    }
  }

  public synchronized String extractStatistics(
      boolean logs, boolean leds, boolean radioHW, boolean radioRXTX) {
    StringBuilder output = new StringBuilder();

    /* Process all events (per mote basis) */
    ArrayList<MoteStatistics> allStats = new ArrayList<>();
    for (MoteEvents moteEvents: allMoteEvents) {
      MoteStatistics stats = new MoteStatistics();
      allStats.add(stats);
      stats.mote = moteEvents.mote;

      if (leds) {
        for (int i = 0, n = moteEvents.ledEvents.size(); i < n; i++) {
          if (!(moteEvents.ledEvents.get(i) instanceof LEDEvent ledEvent)) continue;

          MoteEvent nextEvent = i + 1 < n ? moteEvents.ledEvents.get(i + 1) : null;
          long endTime = nextEvent != null ? nextEvent.time : simulation.getSimulationTime();

          /* Red */
          if (ledEvent.red) {
            /* LED is on, add time interval */
            stats.onTimeRedLED += endTime - ledEvent.time;
          }

          /* Green */
          if (ledEvent.green) {
            /* LED is on, add time interval */
            stats.onTimeGreenLED += endTime - ledEvent.time;
          }

          /* Blue */
          if (ledEvent.blue) {
            /* LED is on, add time interval */
            stats.onTimeBlueLED += endTime - ledEvent.time;
          }
        }
      }

      if (logs) {
        for (MoteEvent ev: moteEvents.logEvents) {
          if (!(ev instanceof LogEvent)) continue;
          stats.nrLogs++;
        }
      }

      if (radioHW) {
        for (int i = 0, n = moteEvents.radioHWEvents.size(); i < n; i++) {
          if (!(moteEvents.radioHWEvents.get(i) instanceof RadioHWEvent hwEvent)) continue;

          if (hwEvent.on) {
            MoteEvent nextEvent = i + 1 < n ? moteEvents.radioHWEvents.get(i + 1) : null;
            long endTime = nextEvent != null ? nextEvent.time : simulation.getSimulationTime();

            /* HW is on */
            stats.radioOn += endTime - hwEvent.time;
          }
        }
      }

      if (radioRXTX) {
        for (int i = 0, n = moteEvents.radioRXTXEvents.size(); i < n; i++) {
          if (!(moteEvents.radioRXTXEvents.get(i) instanceof RadioRXTXEvent rxtxEvent)) continue;
          if (rxtxEvent.state == RXTXRadioEvent.IDLE) {
            continue;
          }

          MoteEvent nextEvent = i + 1 < n ? moteEvents.radioRXTXEvents.get(i + 1) : null;
          long endTime = nextEvent != null ? nextEvent.time : simulation.getSimulationTime();
          long diff = endTime - rxtxEvent.time;

          if (rxtxEvent.state == RXTXRadioEvent.TRANSMITTING) {
            stats.onTimeTX += diff;
            continue;
          }
          if (rxtxEvent.state == RXTXRadioEvent.INTERFERED) {
            stats.onTimeInterfered += diff;
            continue;
          }
          if (rxtxEvent.state == RXTXRadioEvent.RECEIVING) {
            stats.onTimeRX += diff;
          }
        }
      }

      output.append(stats.toString(logs, leds, radioHW, radioRXTX));
    }

    if (allStats.isEmpty()) {
      return output.toString();
    }

    /* Average */
    MoteStatistics average = new MoteStatistics();
    for (MoteStatistics stats: allStats) {
      average.onTimeRedLED += stats.onTimeRedLED;
      average.onTimeGreenLED += stats.onTimeGreenLED;
      average.onTimeBlueLED += stats.onTimeBlueLED;
      average.radioOn += stats.radioOn;
      average.onTimeRX += stats.onTimeRX;
      average.onTimeTX += stats.onTimeTX;
      average.onTimeInterfered += stats.onTimeInterfered;
    }
    average.onTimeBlueLED /= allStats.size();
    average.onTimeGreenLED /= allStats.size();
    average.onTimeBlueLED /= allStats.size();
    average.radioOn /= allStats.size();
    average.onTimeRX /= allStats.size();
    average.onTimeTX /= allStats.size();
    average.onTimeInterfered /= allStats.size();

    output.append(average.toString(logs, leds, radioHW, radioRXTX));
    return output.toString();
  }

  public void trySelectTime(final long toTime) {
    EventQueue.invokeLater(() -> {
      // Mark selected time in time ruler.
      final int toPixel = (int) (toTime / currentPixelDivisor);
      mousePixelPositionX = toPixel;
      mouseDownPixelPositionX = toPixel;
      mousePixelPositionY = timeline.getHeight();
      // Check if time is already visible.
      Rectangle vis = timeline.getVisibleRect();
      if (toPixel >= vis.x && toPixel < vis.x + vis.width) {
        repaint();
        return;
      }
      forceRepaintAndFocus(toTime, 0.5, false);
    });
  }

  private final Action radioLoggerAction = new AbstractAction("Show in " + Cooja.getDescriptionOf(RadioLogger.class)) {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (popupLocation == null) {
        return;
      }
      long time = (long) (popupLocation.x*currentPixelDivisor);

      simulation.getCooja().getPlugins(RadioLogger.class).forEach(p -> p.trySelectTime(time));
    }
  };
  private final Action logListenerAction = new AbstractAction("Show in " + Cooja.getDescriptionOf(LogListener.class)) {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (popupLocation == null) {
        return;
      }
      long time = (long) (popupLocation.x*currentPixelDivisor);

      simulation.getCooja().getPlugins(LogListener.class).forEach(p -> p.trySelectTime(time));
    }
  };

  private final Action showInAllAction = new AbstractAction("Show in " + Cooja.getDescriptionOf(LogListener.class) + " and " + Cooja.getDescriptionOf(RadioLogger.class)) {
    @Override
    public void actionPerformed(ActionEvent e) {
      logListenerAction.actionPerformed(null);
      radioLoggerAction.actionPerformed(null);
    }
  };

  private boolean executionDetails;
  private final Action executionDetailsAction = new AbstractAction("Show execution details in tooltips") {
    @Override
    public void actionPerformed(ActionEvent e) {
    	executionDetails = !executionDetails;
    }
  };

  private void numberMotesWasUpdated() {
    java.awt.EventQueue.invokeLater(() -> {
      if (allMoteEvents.isEmpty()) {
        setTitle("Timeline");
      } else {
        setTitle("Timeline showing " + allMoteEvents.size() + " motes");
      }
      timelineMoteRuler.revalidate();
      timelineMoteRuler.repaint();
      timeline.revalidate();
      timeline.repaint();
    });
  }

  /* XXX Keeps track of observed mote interfaces */
  static class MoteObservation {
    private EventTriggers<EventTriggers.Update, Mote> moteEventTriggers;
    private EventTriggers<RadioEvent, Radio> radioEventTriggers;
    private final Mote mote;

    private WatchpointMote watchpointMote; /* XXX */
    private WatchpointListener watchpointListener; /* XXX */

    MoteObservation(Mote mote, Radio radio, EventTriggers<RadioEvent, Radio> triggers) {
      this.mote = mote;
      radioEventTriggers = triggers;
    }

    MoteObservation(Mote mote, EventTriggers<EventTriggers.Update, Mote> triggers) {
      this.mote = mote;
      moteEventTriggers = triggers;
    }

    /* XXX Special case, should be generalized */
    MoteObservation(Mote mote, WatchpointMote watchpointMote, WatchpointListener listener) {
      this.mote = mote;
      this.watchpointMote = watchpointMote;
      this.watchpointListener = listener;
    }

    Mote getMote() {
      return mote;
    }

    /**
     * Disconnect observer from observable (stop observing) and clean up resources (remove pointers).
     */
    void dispose() {
      if (moteEventTriggers != null) {
        moteEventTriggers.deleteTriggers(this);
      }
      if (radioEventTriggers != null) {
        radioEventTriggers.deleteTriggers(this);
      }
      /* XXX */
      if (watchpointMote != null) {
        watchpointMote.removeWatchpointListener(watchpointListener);
        watchpointMote = null;
        watchpointListener = null;
      }
    }
  }

  private void addMoteObservers(final Mote mote, final MoteEvents moteEvents) {
    /* LEDs */
    final LED moteLEDs = mote.getInterfaces().getLED();
    if (moteLEDs != null) {
      LEDEvent startupEv = new LEDEvent(
          simulation.getSimulationTime(),
          moteLEDs.isRedOn(),
          moteLEDs.isGreenOn(),
          moteLEDs.isYellowOn()
      );
      moteEvents.addLED(startupEv);
      var moteObserver = new MoteObservation(mote, moteLEDs.getTriggers());
      moteLEDs.getTriggers().addTrigger(moteObserver, (o, m) ->
              moteEvents.addLED(new LEDEvent(simulation.getSimulationTime(),
                                             moteLEDs.isRedOn(), moteLEDs.isGreenOn(), moteLEDs.isYellowOn())));
      activeMoteObservers.add(moteObserver);
    }

    /* Radio OnOff, RXTX, and channels */
    final Radio moteRadio = mote.getInterfaces().getRadio();
    if (moteRadio != null) {
      RadioChannelEvent startupChannel = new RadioChannelEvent(
          simulation.getSimulationTime(), moteRadio.getChannel(), moteRadio.isRadioOn());
      moteEvents.addRadioChannel(startupChannel);
      RadioHWEvent startupHW = new RadioHWEvent(
          simulation.getSimulationTime(), moteRadio.isRadioOn());
      moteEvents.addRadioHW(startupHW);
      RadioRXTXEvent startupRXTX = new RadioRXTXEvent(
          simulation.getSimulationTime(), RXTXRadioEvent.IDLE);
      moteEvents.addRadioRXTX(startupRXTX);
      var observer = new BiConsumer<RadioEvent, Radio>() {
        int lastChannel = -1;
        @Override
        public void accept(RadioEvent radioEv, Radio radio) {
          String details = null;
          if (executionDetails && mote instanceof AbstractEmulatedMote<?, ?, ?> emulatedMote) {
            details = emulatedMote.getExecutionDetails();
            if (details != null) {
              details = "<br>" + details.replace("\n", "<br>");
            }
          }

          /* Radio channel */
          int nowChannel = moteRadio.getChannel();
          if (nowChannel != lastChannel) {
            lastChannel = nowChannel;
            RadioChannelEvent ev = new RadioChannelEvent(
                simulation.getSimulationTime(), nowChannel, moteRadio.isRadioOn());
            moteEvents.addRadioChannel(ev);

            ev.details = details;
          }
          
          if (radioEv == RadioEvent.HW_ON ||
              radioEv == RadioEvent.HW_OFF) {
            RadioHWEvent ev = new RadioHWEvent(
                simulation.getSimulationTime(), moteRadio.isRadioOn());
            moteEvents.addRadioHW(ev);

            ev.details = details;

            /* Also create another channel event here */
            lastChannel = nowChannel;
            RadioChannelEvent ev2 = new RadioChannelEvent(
                simulation.getSimulationTime(), nowChannel, moteRadio.isRadioOn());
            ev2.details = details;
            moteEvents.addRadioChannel(ev2);
          }

          /* Radio RXTX events */
          if (radioEv == RadioEvent.TRANSMISSION_STARTED ||
              radioEv == RadioEvent.TRANSMISSION_FINISHED ||
              radioEv == RadioEvent.RECEPTION_STARTED ||
              radioEv == RadioEvent.RECEPTION_INTERFERED ||
              radioEv == RadioEvent.RECEPTION_FINISHED) {

            RadioRXTXEvent ev;
            /* Override events, instead show state */
            if (moteRadio.isTransmitting()) {
              ev = new RadioRXTXEvent(
                  simulation.getSimulationTime(), RXTXRadioEvent.TRANSMITTING);
            } else if (!moteRadio.isRadioOn()) {
              ev = new RadioRXTXEvent(
                  simulation.getSimulationTime(), RXTXRadioEvent.IDLE);
            } else if (moteRadio.isInterfered()) {
              ev = new RadioRXTXEvent(
                  simulation.getSimulationTime(), RXTXRadioEvent.INTERFERED);
            } else if (moteRadio.isReceiving()) {
              ev = new RadioRXTXEvent(
                  simulation.getSimulationTime(), RXTXRadioEvent.RECEIVING);
            } else {
              ev = new RadioRXTXEvent(
                  simulation.getSimulationTime(), RXTXRadioEvent.IDLE);
            }

            moteEvents.addRadioRXTX(ev);

            ev.details = details;
          }

        }
      };
      var obs = new MoteObservation(mote, moteRadio, moteRadio.getRadioEventTriggers());
      moteRadio.getRadioEventTriggers().addTrigger(obs, observer);
      activeMoteObservers.add(obs);
    }

    /* Watchpoints */
    if (mote instanceof WatchpointMote watchpointMote) {
      WatchpointListener listener = new WatchpointListener() {
        @Override
        public void watchpointTriggered(Watchpoint watchpoint) {
          WatchpointEvent ev = new WatchpointEvent(simulation.getSimulationTime(), watchpoint);

          if (executionDetails && mote instanceof AbstractEmulatedMote<?, ?, ?> emulatedMote) {
            String details = emulatedMote.getExecutionDetails();
            if (details != null) {
              details = "<br>" + details.replace("\n", "<br>");
              ev.details = details;
            }
          }

          moteEvents.addWatchpoint(ev);
        }
        @Override
        public void watchpointsChanged() {
        }
      };

      watchpointMote.addWatchpointListener(listener);
      activeMoteObservers.add(new MoteObservation(mote, watchpointMote, listener));
    }

  }

  private void addMote(Mote newMote) {
    if (newMote == null) {
      return;
    }
    for (MoteEvents moteEvents: allMoteEvents) {
      if (moteEvents.mote == newMote) {
        return;
      }
    }

    MoteEvents newMoteLog = new MoteEvents(newMote);
    allMoteEvents.add(newMoteLog);
    addMoteObservers(newMote, newMoteLog);

    numberMotesWasUpdated();
  }

  private void removeMote(Mote mote) {
    MoteEvents remove = null;
    for (MoteEvents moteEvents: allMoteEvents) {
      if (moteEvents.mote == mote) {
        remove = moteEvents;
        break;
      }
    }
    if (remove == null) {
      logger.warn("No such observed mote: " + mote);
      return;
    }
    allMoteEvents.remove(remove);

    /* Remove mote observers */
    MoteObservation[] moteObservers = activeMoteObservers.toArray(new MoteObservation[0]);
    for (MoteObservation o: moteObservers) {
      if (o.getMote() == mote) {
        o.dispose();
        activeMoteObservers.remove(o);
      }
    }

    numberMotesWasUpdated();
  }

  private void recalculateMoteHeight() {
    int h = EVENT_PIXEL_HEIGHT;
    if (showRadioRXTX) {
      h += EVENT_PIXEL_HEIGHT;
    }
    if (showRadioChannels) {
      h += EVENT_PIXEL_HEIGHT;
    }
    if (showRadioOnoff) {
      h += EVENT_PIXEL_HEIGHT;
    }
    if (showLeds) {
      h += 3*LED_PIXEL_HEIGHT;
    }
    if (showLogOutputs) {
      h += EVENT_PIXEL_HEIGHT;
    }
    if (showWatchpoints) {
      h += EVENT_PIXEL_HEIGHT;
    }
    if (h != paintedMoteHeight) {
      paintedMoteHeight = h;
      timelineMoteRuler.repaint();
      timeline.repaint();
    }
  }

  @Override
  public void closePlugin() {
    /* Remove repaint timer */
    repaintTimelineTimer.stop();
    simulation.getMoteHighlightTriggers().deleteTriggers(this);
    simulation.getMoteTriggers().deleteTriggers(this);
    simulation.getEventCentral().removeLogOutputListener(newMotesListener);

    /* Remove active mote interface observers */
    for (MoteObservation o: activeMoteObservers) {
      o.dispose();
    }
    activeMoteObservers.clear();
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    /* Remember observed motes */
    Mote[] allMotes = simulation.getMotes();
    for (MoteEvents moteEvents: allMoteEvents) {
      var element = new Element("mote");
      for (int i=0; i < allMotes.length; i++) {
        if (allMotes[i] == moteEvents.mote) {
          element.setText(String.valueOf(i));
          config.add(element);
          break;
        }
      }
    }
    if (showRadioRXTX) {
      config.add(new Element("showRadioRXTX"));
    }
    if (showRadioChannels) {
      config.add(new Element("showRadioChannels"));
    }
    if (showRadioOnoff) {
      config.add(new Element("showRadioHW"));
    }
    if (showLeds) {
      config.add(new Element("showLEDs"));
    }
    if (showLogOutputs) {
      config.add(new Element("showLogOutput"));
    }
    if (showWatchpoints) {
      config.add(new Element("showWatchpoints"));
    }

    if (executionDetails) {
      config.add(new Element("executionDetails"));
    }

    var element = new Element("zoomfactor");
    element.addContent(String.valueOf(currentPixelDivisor));
    config.add(element);

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    showRadioRXTX = false;
    showRadioChannels = false;
    showRadioOnoff = false;
    showLeds = false;
    showLogOutputs = false;
    showWatchpoints = false;

    executionDetails = false;

    /* Remove already registered motes */
    MoteEvents[] allMoteEventsArr = allMoteEvents.toArray(new MoteEvents[0]);
    for (MoteEvents moteEvents: allMoteEventsArr) {
      removeMote(moteEvents.mote);
    }

    for (Element element : configXML) {
      String name = element.getName();
      if ("mote".equals(name)) {
        int index = Integer.parseInt(element.getText());
        addMote(simulation.getMote(index));
      } else if ("showRadioRXTX".equals(name)) {
        showRadioRXTX = true;
      } else if ("showRadioChannels".equals(name)) {
        showRadioChannels = true;
      } else if ("showRadioHW".equals(name)) {
        showRadioOnoff = true;
      } else if ("showLEDs".equals(name)) {
        showLeds = true;
      } else if ("showLogOutput".equals(name)) {
        showLogOutputs = true;
      } else if ("showWatchpoints".equals(name)) {
        showWatchpoints = true;
      } else if ("executionDetails".equals(name)) {
      	executionDetails = true;
      } else if ("zoomfactor".equals(name)) {
        /* NB: Historically no validation on this option */
        zoomFinish(Double.parseDouble(element.getText()), 0, 0);
      }
    }

    recalculateMoteHeight();

    return true;
  }


  private int mousePixelPositionX = -1;
  private int mousePixelPositionY = -1;
  private int mouseDownPixelPositionX = -1;
  class Timeline extends JComponent {
    Timeline() {
      setLayout(null);
      setToolTipText(null);
      setBackground(COLOR_BACKGROUND);
      var mouseAdapter = new MouseAdapter() {
        private Popup popUpToolTip;
        private double zoomInitialPixelDivisor;
        private int zoomInitialMouseY;
        private long zoomCenterTime = -1;
        private double zoomCenter = -1;

        @Override
        public void mouseDragged(MouseEvent e) {
          super.mouseDragged(e);
          if (e.isControlDown()) { // Zoom with mouse.
            if (zoomCenterTime < 0) {
              return;
            }
            var factor = Math.exp(0.01 * (e.getY() - zoomInitialMouseY));
            zoomFinish(zoomInitialPixelDivisor * factor, zoomCenterTime, zoomCenter);
            return;
          }
          if (e.isAltDown()) { // Pan with mouse.
            if (zoomCenterTime < 0) {
              return;
            }
            zoomCenter = (double) (e.getX() - timeline.getVisibleRect().x) / timeline.getVisibleRect().width;
            forceRepaintAndFocus(zoomCenterTime, zoomCenter);
            return;
          }
          if (mousePixelPositionX >= 0) {
            mousePixelPositionX = e.getX();
            mousePixelPositionY = e.getY();
            repaint();
          }
        }

        @Override
        public void mousePressed(MouseEvent e) {
          if (e.isControlDown()) { // Zoom with mouse.
            zoomInitialMouseY = e.getY();
            zoomInitialPixelDivisor = currentPixelDivisor;
            zoomCenterTime = (long) (e.getX() * currentPixelDivisor);
            zoomCenter = (double) (e.getX() - timeline.getVisibleRect().x) / timeline.getVisibleRect().width;
            return;
          }
          if (e.isAltDown()) { // Pan with mouse.
            zoomCenterTime = (long) (e.getX() * currentPixelDivisor);
            return;
          }
          if (popUpToolTip != null) {
            popUpToolTip.hide();
            popUpToolTip = null;
          }
          if (e.getPoint().getY() < FIRST_MOTE_PIXEL_OFFSET) {
            mousePixelPositionX = e.getX();
            mouseDownPixelPositionX = e.getX();
            mousePixelPositionY = e.getY();
            repaint();
          } else { // Trigger tooltip.
            JToolTip t = timeline.createToolTip();
            t.setTipText(Timeline.this.getMouseToolTipText(e));
            if (t.getTipText() == null || t.getTipText().isEmpty()) {
              return;
            }
            t.validate();

            // Check tooltip width.
            var screenBounds = timeline.getGraphicsConfiguration().getBounds();
            int x;
            {
              int tooltip = e.getLocationOnScreen().x + t.getPreferredSize().width;
              int screen = screenBounds.x + screenBounds.width;
              if (tooltip > screen) {
                x = e.getLocationOnScreen().x - (tooltip - screen);
              } else {
                x = e.getLocationOnScreen().x;
              }
            }

            // Check tooltip height.
            int y;
            {
              int tooltip = e.getLocationOnScreen().y + t.getPreferredSize().height;
              int screen = screenBounds.y + screenBounds.height;
              if (tooltip > screen) {
                y = e.getLocationOnScreen().y - (tooltip - screen);
              } else {
                y = e.getLocationOnScreen().y;
              }
            }
            popUpToolTip = PopupFactory.getSharedInstance().getPopup(null, t, x, y);
            popUpToolTip.show();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          zoomCenterTime = -1;
          if (popUpToolTip != null) {
            popUpToolTip.hide();
            popUpToolTip = null;
          }
          super.mouseReleased(e);
          mousePixelPositionX = -1;
          repaint();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          if (e.isControlDown()) {
            final long zct = (long) (e.getX() * currentPixelDivisor);
            final double zc = (double) (e.getX() - timeline.getVisibleRect().x) / timeline.getVisibleRect().width;
            zoomFinishLevel(e.getWheelRotation(), zct, zc);
          }
        }
      };
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
      addMouseWheelListener(mouseAdapter);

      /* Popup menu */
      final JPopupMenu popupMenu = new JPopupMenu();

      popupMenu.add(new JMenuItem(showInAllAction));
      popupMenu.add(new JMenuItem(logListenerAction));
      popupMenu.add(new JMenuItem(radioLoggerAction));

      JMenu advancedMenu = new JMenu("Advanced");
      advancedMenu.add(new JCheckBoxMenuItem(executionDetailsAction) {
        @Override
        public boolean isSelected() {
      		return executionDetails;
      	}
      });

      addMouseListener(new MouseAdapter() {
      	long lastClick = -1;
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popupLocation = new Point(e.getX(), e.getY());
            popupMenu.show(Timeline.this, e.getX(), e.getY());
          }

          /* Focus on double-click */
          if (System.currentTimeMillis() - lastClick < 250) {
            popupLocation = e.getPoint();
            showInAllAction.actionPerformed(null);

            long time = (long) (popupLocation.x*currentPixelDivisor);
            simulation.getCooja().getPlugins(TimeLine.class).forEach(p -> {
              if (p != TimeLine.this) {
                p.trySelectTime(time);
              }
            });
          }
          lastClick = System.currentTimeMillis();
        }
        @Override
        public void mousePressed(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popupLocation = new Point(e.getX(), e.getY());
            popupMenu.show(Timeline.this, e.getX(), e.getY());
          }
        }
        @Override
        public void mouseReleased(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popupLocation = new Point(e.getX(), e.getY());
            popupMenu.show(Timeline.this, e.getX(), e.getY());
          }
        }
      });
    }

    private final Color SEPARATOR_COLOR = new Color(220, 220, 220);
    @Override
    public void paintComponent(Graphics g) {
      Rectangle bounds = g.getClipBounds();
      if (needZoomOut) {
        /* Need zoom out */
        g.setColor(Color.RED);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        Rectangle vis = timeline.getVisibleRect();
        g.setColor(Color.WHITE);
        String msg = "Zoom out";
        FontMetrics fm = g.getFontMetrics();
        int msgWidth = fm.stringWidth(msg);
        int msgHeight = fm.getHeight();
        g.drawString(msg,
            vis.x + vis.width/2 - msgWidth/2,
            vis.y + vis.height/2 + msgHeight/2);
        return;
      }

      long simulationTime = simulation.getSimulationTime();
      long intervalStart = (long)(bounds.x*currentPixelDivisor);
      long intervalEnd = Math.min(simulationTime, (long) (intervalStart + bounds.width*currentPixelDivisor));

      if (bounds.x > Integer.MAX_VALUE - 1000) {
        /* Strange bounds */
        return;
      }

      g.setColor(COLOR_BACKGROUND);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      drawTimeRule(g, intervalStart, intervalEnd);

      if (showLogOutputs) {
        // invalidate filter stamp
        if (logEventFilterPlugin != null &&
                logEventFilterPlugin.getFilter() != logEventFilterLast) {
          logEventFilterLast = logEventFilterPlugin.getFilter();
          logEventFilterChanged = true;
        } else {
          logEventFilterChanged = false;
        }
      }

      /* Paint mote events */
      int lineHeightOffset = FIRST_MOTE_PIXEL_OFFSET;
      boolean dark = true;
      for (MoteEvents events : allMoteEvents) {

        /* Mote separators */
        if (dark) {
          g.setColor(SEPARATOR_COLOR);
          g.fillRect(
              0, lineHeightOffset-2,
              getWidth(), paintedMoteHeight
          );
        }
        dark = !dark;

        if (showRadioRXTX) {
          paintEvents(g, events.radioRXTXEvents, intervalStart, intervalEnd, lineHeightOffset);
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showRadioChannels) {
          paintEvents(g, events.radioChannelEvents, intervalStart, intervalEnd, lineHeightOffset);
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showRadioOnoff) {
          paintEvents(g, events.radioHWEvents, intervalStart, intervalEnd, lineHeightOffset);
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showLeds) {
          paintEvents(g, events.ledEvents, intervalStart, intervalEnd, lineHeightOffset);
          lineHeightOffset += 3*LED_PIXEL_HEIGHT;
        }
        if (showLogOutputs) {
          paintEvents(g, events.logEvents, intervalStart, intervalEnd, lineHeightOffset);
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showWatchpoints) {
          paintEvents(g, events.watchpointEvents, intervalStart, intervalEnd, lineHeightOffset);
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }

        lineHeightOffset += EVENT_PIXEL_HEIGHT;
      }

      /* Draw vertical time marker (if mouse is dragged) */
      drawMouseTime(g, intervalStart, intervalEnd);
    }

    private void paintEvents(Graphics g, ArrayList<MoteEvent> events, long intervalStart, long intervalEnd,
                             int lineHeightOffset) {
      if (events.isEmpty()) {
        return;
      }

      int lastPosition = -1;
      for (int i = getIndexOfFirstIntervalEvent(events, intervalStart), n = events.size(); i < n; i++) {
        MoteEvent event = events.get(i);
        if (event.time >= intervalEnd) {
          break;
        }

        int x = (int) (event.time / currentPixelDivisor);
        if (event.collapseOverlapping && x < lastPosition + 1) {
          continue;
        }

        /* Calculate event width */
        int width;
        if (event.fixedWidth == 0) {
          MoteEvent nextEvent = i + 1 < n ? events.get(i + 1) : null;
          long endTime = (nextEvent != null ? nextEvent.time : intervalEnd) - event.time;
          width = (int) (endTime / currentPixelDivisor);
          /* Handle zero pixel width events */
          if (width == 0) {
            if (PAINT_ZERO_WIDTH_EVENTS) {
              width = 1;
            } else {
              continue;
            }
          }
        } else {
          width = event.fixedWidth;
        }

        /* Always respect the minimum width configuration */
        if (width < paintEventMinWidth) {
          width = paintEventMinWidth;
        }

        Color color = event.getEventColor(TimeLine.this);
        if (color == null) {
          /* Skip painting event */
          continue;
        }
        g.setColor(color);

        lastPosition = x;
        event.paintInterval(TimeLine.this, g, x, lineHeightOffset, width);
      }
    }

    private static <T extends MoteEvent> int getIndexOfFirstIntervalEvent(ArrayList<T> events, long time) {
      /* TODO IMPLEMENT ME: Binary search */
      int nrEvents = events.size();
      if (nrEvents == 0) {
        return -1;
      }

      int ev = 0;
      while (ev < nrEvents && events.get(ev).time < time) {
        ev++;
      }
      return Math.max(ev - 1, 0);
    }

    private void drawTimeRule(Graphics g, long start, long end) {
      long time;

      /* Paint 10ms and 100 ms markers */
      g.setColor(Color.GRAY);

      time = start - (start % (100*Simulation.MILLISECOND));
      while (time <= end) {
        if (time % (100*Simulation.MILLISECOND) == 0) {
          g.drawLine(
              (int) (time/currentPixelDivisor), 0,
              (int) (time/currentPixelDivisor), TIME_MARKER_PIXEL_HEIGHT);
        } else {
          g.drawLine(
              (int) (time/currentPixelDivisor), 0,
              (int) (time/currentPixelDivisor), TIME_MARKER_PIXEL_HEIGHT/2);
        }
        time += (10*Simulation.MILLISECOND);
      }
    }

    private void drawMouseTime(Graphics g, long start, long end) {
      if (mousePixelPositionX >= 0) {
        long time = (long) (mousePixelPositionX*currentPixelDivisor);
        long diff = (long) (Math.abs(mouseDownPixelPositionX-mousePixelPositionX)*currentPixelDivisor);
        String str =
        	"Time (ms): " + (double)time/Simulation.MILLISECOND +
        	" (" + (double)diff/Simulation.MILLISECOND + ")";

        int h = g.getFontMetrics().getHeight();
        int w = g.getFontMetrics().stringWidth(str) + 6;
        int y= mousePixelPositionY<getHeight()/2?0:getHeight()-h;
        int delta = mousePixelPositionX + w > end/currentPixelDivisor?w:0; /* Don't write outside visible area */

        /* Line */
        g.setColor(Color.GRAY);
        g.drawLine(
            mousePixelPositionX, 0,
            mousePixelPositionX, getHeight());

        /* Text box */
        g.setColor(Color.DARK_GRAY);
        g.fillRect(
            mousePixelPositionX-delta, y,
            w, h);
        g.setColor(Color.BLACK);
        g.drawRect(
            mousePixelPositionX-delta, y,
            w, h);
        g.setColor(Color.WHITE);
        g.drawString(str,
            mousePixelPositionX+3-delta,
            y+h-1);
      }
    }

    String getMouseToolTipText(MouseEvent event) {
      if (event.getPoint().y <= TIME_MARKER_PIXEL_HEIGHT) {
        return "<html>Click to display time marker</html>";
      }
      if (event.getPoint().y <= FIRST_MOTE_PIXEL_OFFSET) {
        return null;
      }

      /* Mote */
      int mote = (event.getPoint().y-FIRST_MOTE_PIXEL_OFFSET)/paintedMoteHeight;
      if (mote < 0 || mote >= allMoteEvents.size()) {
        return null;
      }
      String tooltip = "<html>Mote: " + allMoteEvents.get(mote).mote + "<br>";

      /* Time */
      long time = (long) (event.getPoint().x*currentPixelDivisor);
      tooltip += "Time (ms): " + (double)time/Simulation.MILLISECOND + "<br>";

      /* Event */
      ArrayList<? extends MoteEvent> events = null;
      int evMatched = 0;
      int evMouse = ((event.getPoint().y-FIRST_MOTE_PIXEL_OFFSET) % paintedMoteHeight) / EVENT_PIXEL_HEIGHT;
      if (showRadioRXTX) {
        if (evMatched == evMouse) {
          events = allMoteEvents.get(mote).radioRXTXEvents;
        }
        evMatched++;
      }
      if (showRadioChannels) {
        if (evMatched == evMouse) {
          events = allMoteEvents.get(mote).radioChannelEvents;
        }
        evMatched++;
      }
      if (showRadioOnoff) {
        if (evMatched == evMouse) {
          events = allMoteEvents.get(mote).radioHWEvents;
        }
        evMatched++;
      }
      if (showLeds) {
        if (evMatched == evMouse) {
          events = allMoteEvents.get(mote).ledEvents;
        }
        evMatched++;
      }
      if (showLogOutputs) {
        if (evMatched == evMouse) {
          events = allMoteEvents.get(mote).logEvents;
        }
        evMatched++;
      }
      if (showWatchpoints) {
        if (evMatched == evMouse) {
          events = allMoteEvents.get(mote).watchpointEvents;
        }
        evMatched++;
      }
      if (events != null) {
        int index = getIndexOfFirstIntervalEvent(events, time);
        if (index >= 0) {
          MoteEvent ev = events.get(index);
          if (time >= ev.time) {
            tooltip += ev + "<br>";

            if (ev.details != null) {
              tooltip += "Details:<br>" + ev.details;
            }
          }
        }
      }

      tooltip += "</html>";
      return tooltip;
    }
  }

  class MoteRuler extends JPanel {
    MoteRuler() {
      setPreferredSize(new Dimension(35, 1));
      setToolTipText(null);
      setBackground(COLOR_BACKGROUND);

      final JPopupMenu popupMenu = new JPopupMenu();
      final JMenuItem topItem = new JMenuItem(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Mote m = (Mote) ((JComponent) e.getSource()).getClientProperty("mote");
          // Sort by distance.
          MoteEvents mEvent = null;
          for (MoteEvents me : allMoteEvents.toArray(new MoteEvents[0])) {
            if (me.mote == m) {
              mEvent = me;
              break;
            }
          }
          allMoteEvents.remove(mEvent);
          allMoteEvents.add(0, mEvent);
          numberMotesWasUpdated();
        }
      });
      topItem.setText("Move to top");
      popupMenu.add(topItem);
      final JMenuItem sortItem = new JMenuItem(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Mote m = (Mote) ((JComponent) e.getSource()).getClientProperty("mote");
          // Sort by distance.
          ArrayList<MoteEvents> sortedMoteEvents = new ArrayList<>();
          for (MoteEvents me : allMoteEvents.toArray(new MoteEvents[0])) {
            double d = me.mote.getInterfaces().getPosition().getDistanceTo(m);
            int i;
            for (i = 0; i < sortedMoteEvents.size(); i++) {
              double d2 = m.getInterfaces().getPosition().getDistanceTo(sortedMoteEvents.get(i).mote);
              if (d < d2) {
                break;
              }
            }
            sortedMoteEvents.add(i, me);
          }
          allMoteEvents = sortedMoteEvents;
          numberMotesWasUpdated();
        }
      });
      sortItem.setText("Sort by distance");
      popupMenu.add(sortItem);
      final JMenuItem removeItem = new JMenuItem(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          removeMote((Mote) ((JComponent) e.getSource()).getClientProperty("mote"));
        }
      });
      removeItem.setText("Remove from timeline");
      popupMenu.add(removeItem);
      final JMenuItem keepMoteOnlyItem = new JMenuItem(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Mote m = (Mote) ((JComponent) e.getSource()).getClientProperty("mote");
          MoteEvents[] mes = allMoteEvents.toArray(new MoteEvents[0]);
          for (MoteEvents me : mes) {
            if (me.mote == m) {
              continue;
            }
            removeMote(me.mote);
          }
        }
      });
      keepMoteOnlyItem.setText("Remove all motes from timeline but");
      popupMenu.add(keepMoteOnlyItem);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          Mote m = getMote(e.getPoint());
          if (m == null) {
            return;
          }
          gui.signalMoteHighlight(m);

          sortItem.setText("Sort by distance: " + m);
          sortItem.putClientProperty("mote", m);
          topItem.setText("Move to top: " + m);
          topItem.putClientProperty("mote", m);
          removeItem.setText("Remove from timeline: " + m);
          removeItem.putClientProperty("mote", m);
          keepMoteOnlyItem.setText("Remove all motes from timeline but: " + m);
          keepMoteOnlyItem.putClientProperty("mote", m);
          popupMenu.show(MoteRuler.this, e.getX(), e.getY());
        }
      });
    }

    private Mote getMote(Point p) {
      if (p.y < FIRST_MOTE_PIXEL_OFFSET) {
        return null;
      }
      int m = (p.y-FIRST_MOTE_PIXEL_OFFSET)/paintedMoteHeight;
      if (m < allMoteEvents.size()) {
        return allMoteEvents.get(m).mote;
      }
      return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(COLOR_BACKGROUND);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(Color.BLACK);

      g.setFont(new Font("SansSerif", Font.PLAIN, paintedMoteHeight));
      int y = FIRST_MOTE_PIXEL_OFFSET-EVENT_PIXEL_HEIGHT/2+paintedMoteHeight;
      for (MoteEvents moteLog: allMoteEvents) {
        String str = String.valueOf(moteLog.mote.getID());
        int w = g.getFontMetrics().stringWidth(str) + 1;

        if (highlightedMotes.contains(moteLog.mote)) {
        	g.setColor(HIGHLIGHT_COLOR);
          g.fillRect(0, y-paintedMoteHeight, getWidth()-1, paintedMoteHeight);
          g.setColor(Color.BLACK);
        }
        g.drawString(str, getWidth() - w, y);
        y += paintedMoteHeight;
      }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      Point p = event.getPoint();
      Mote m = getMote(p);
      if (m == null)
        return null;

      return "<html>" + m + "<br>Click mote for options</html>";
    }
  }

  /* Event classes */
  static abstract class MoteEvent {
    String details;
    int fixedWidth;
    boolean collapseOverlapping;
    final long time;
    MoteEvent(long time) {
      this.time = time;
    }

    /**
     * Used by the default paint method to color events.
     * The event is not painted if the returned color is null.
     *
     * @see #paintInterval(TimeLine, Graphics, int, int, int)
     * @return Event color or null
     */
    public abstract Color getEventColor(TimeLine timeLine);

    /* Default paint method */
    void paintInterval(TimeLine timeLine, Graphics g, int x, int lineHeightOffset, int width) {
      g.fillRect(x, lineHeightOffset, width, EVENT_PIXEL_HEIGHT);
    }
  }
  static class NoHistoryEvent extends MoteEvent {
    NoHistoryEvent(long time) {
      super(time);
    }
    @Override
    public Color getEventColor(TimeLine timeLine) {
      return Color.CYAN;
    }
    @Override
    public String toString() {
      return "No events has been captured yet";
    }
  }
  public enum RXTXRadioEvent {
    IDLE, RECEIVING, TRANSMITTING, INTERFERED
  }
  static class RadioRXTXEvent extends MoteEvent {
    final RXTXRadioEvent state;
    RadioRXTXEvent(long time, RXTXRadioEvent ev) {
      super(time);
      this.state = ev;
    }
    @Override
    public Color getEventColor(TimeLine timeLine) {
      if (state == RXTXRadioEvent.IDLE) {
        return null;
      } else if (state == RXTXRadioEvent.TRANSMITTING) {
        return Color.BLUE;
      } else if (state == RXTXRadioEvent.RECEIVING) {
        return Color.GREEN;
      } else if (state == RXTXRadioEvent.INTERFERED) {
        return Color.RED;
      } else {
        logger.error("Unknown RXTX event");
        return null;
      }
    }
    @Override
    public String toString() {
      if (state == RXTXRadioEvent.IDLE) {
        return "Radio idle from " + time + "<br>";
      } else if (state == RXTXRadioEvent.TRANSMITTING) {
      	return "Radio transmitting from " + time + "<br>";
      } else if (state == RXTXRadioEvent.RECEIVING) {
        return "Radio receiving from " + time + "<br>";
      } else if (state == RXTXRadioEvent.INTERFERED) {
        return "Radio interfered from " + time + "<br>";
      } else {
        return "Unknown event<br>";
      }
    }
  }

  private final static Color[] CHANNEL_COLORS = {
    Color.decode("0x008080"), Color.decode("0x808080"), Color.decode("0xC00000"),
    Color.decode("0x000020"), Color.decode("0x202000"), Color.decode("0x200020"),
    Color.decode("0x002020"), Color.decode("0x202020"), Color.decode("0x006060"),
    Color.decode("0x606060"), Color.decode("0xA00000"), Color.decode("0x00A000"),
    Color.decode("0x0000A0"), Color.decode("0x400040"), Color.decode("0x004040"),
    Color.decode("0x404040"), Color.decode("0x200000"), Color.decode("0x002000"),
    Color.decode("0xA0A000"), Color.decode("0xA000A0"), Color.decode("0x00A0A0"),
    Color.decode("0xA0A0A0"), Color.decode("0xE00000"), Color.decode("0x600000"),
    Color.decode("0x000040"), Color.decode("0x404000"), Color.decode("0xFF0000"),
    Color.decode("0x00FF00"), Color.decode("0x0000FF"), Color.decode("0xFFFF00"),
    Color.decode("0xFF00FF"), Color.decode("0x808000"), Color.decode("0x800080"),
  };
  static class RadioChannelEvent extends MoteEvent {
    final int channel;
    final boolean radioOn;
    RadioChannelEvent(long time, int channel, boolean radioOn) {
      super(time);
      this.channel = channel;
      this.radioOn = radioOn;
    }
    @Override
    public Color getEventColor(TimeLine timeLine) {
      if (channel >= 0) {
        if (!radioOn) {
          return null;
        }
        return CHANNEL_COLORS[channel % CHANNEL_COLORS.length];
      }
      return null;
    }
    @Override
    public String toString() {
      return "Radio channel " + channel + "<br>";
    }
  }

  static class RadioHWEvent extends MoteEvent {
    final boolean on;
    RadioHWEvent(long time, boolean on) {
      super(time);
      this.on = on;
    }
    @Override
    public Color getEventColor(TimeLine timeLine) {
    	if (on) {
    	    return Color.GRAY;
    	}
    	return null;
    }
    @Override
    public String toString() {
      return "Radio HW was turned " + (on?"on":"off") + "<br>";
    }
  }
  static class LEDEvent extends MoteEvent {
    final boolean red;
    final boolean green;
    final boolean blue;
    final Color color;
    LEDEvent(long time, boolean red, boolean green, boolean blue) {
      super(time);
      this.red = red;
      this.green = green;
      this.blue = blue;
      this.color = new Color(red?255:0, green?255:0, blue?255:0);
    }
    @Override
    public Color getEventColor(TimeLine timeLine) {
      if (!red && !green && !blue) {
        return null;
      } else if (red && green && blue) {
        return Color.LIGHT_GRAY;
      } else {
        return color;
      }
    }
    /* LEDs are painted in three lines */
    @Override
    public void paintInterval(TimeLine timeLine, Graphics g, int x, int lineHeightOffset, int width) {
      if (color.getRed() > 0) {
        g.setColor(new Color(color.getRed(), 0, 0));
        g.fillRect(x, lineHeightOffset, width, LED_PIXEL_HEIGHT);
      }
      if (color.getGreen() > 0) {
        g.setColor(new Color(0, color.getGreen(), 0));
        g.fillRect(x, lineHeightOffset + LED_PIXEL_HEIGHT, width, LED_PIXEL_HEIGHT);
      }
      if (color.getBlue() > 0) {
        g.setColor(new Color(0, 0, color.getBlue()));
        g.fillRect(x, lineHeightOffset + 2 * LED_PIXEL_HEIGHT, width, LED_PIXEL_HEIGHT);
      }
    }
    @Override
    public String toString() {
      return
      "LED state:<br>" +
      "Red = " + (red?"ON":"OFF") + "<br>" +
      "Green = " + (green?"ON":"OFF") + "<br>" +
      "Blue = " + (blue?"ON":"OFF") + "<br>";
    }
  }

  private enum FilterState { NONE, PASS, REJECTED }

  static class LogEvent extends MoteEvent {

    final LogOutputEvent logEvent;
    // filter result cache
    private FilterState filtered;
    private Color logEventColor;

    LogEvent(LogOutputEvent ev) {
      super(ev.getTime());
      this.logEvent = ev;
      this.filtered = FilterState.NONE;
      this.fixedWidth = 4;
      this.collapseOverlapping = true;
    }

    @Override
    public Color getEventColor(TimeLine timeline) {
      /* Ask active log listener whether this should be filtered  */
      if (timeline.logEventFilterPlugin != null) {
        if (timeline.logEventFilterChanged || (filtered == FilterState.NONE)) {
          filtered = timeline.logEventFilterPlugin.filterWouldAccept(logEvent)
                  ? FilterState.PASS
                  : FilterState.REJECTED;
          logEventColor = null;
        }
        if (filtered == FilterState.REJECTED) {
          return null;
        }
        if (timeline.logEventColorOfMote) {
          if (logEventColor != null) {
            return logEventColor;
          }
          /* Ask log listener for event color to use */
          return logEventColor = timeline.logEventFilterPlugin.getColorOfEntry(logEvent);
        }
      }
      return Color.green;
    }
    /* Default paint method */
    public void paintInterval(TimeLine timeLine, Graphics g, int x, int lineHeightOffset, int width) {
      g.fillRect(x, lineHeightOffset, width, EVENT_PIXEL_HEIGHT);
      g.setColor(Color.BLUE);
      g.fillRect(x, lineHeightOffset, 1, EVENT_PIXEL_HEIGHT);
    }
    @Override
    public String toString() {
      return "Mote " + logEvent.getMote() + " says:<br>" + logEvent.getMessage() + "<br>";
    }
  }
  static class WatchpointEvent extends MoteEvent {
    final Watchpoint watchpoint;
    WatchpointEvent(long time, Watchpoint watchpoint) {
      super(time);
      this.watchpoint = watchpoint;
      this.fixedWidth = 2;
      this.collapseOverlapping = true;
    }
    @Override
    public Color getEventColor(TimeLine timeLine) {
      Color c = watchpoint.getColor();
      if (c == null) {
        return Color.BLACK;
      }
      return c;
    }
    @Override
    public String toString() {
      String desc = watchpoint.getDescription();
      desc = desc.replace("\n", "<br>");
      return
      "Watchpoint triggered at time (ms): " +  time/Simulation.MILLISECOND + ".<br>"
      + desc + "<br>";
    }
  }

  static class MoteEvents {
    final Mote mote;
    final ArrayList<MoteEvent> radioRXTXEvents = new ArrayList<>();
    final ArrayList<MoteEvent> radioChannelEvents = new ArrayList<>();
    final ArrayList<MoteEvent> radioHWEvents = new ArrayList<>();
    final ArrayList<MoteEvent> ledEvents = new ArrayList<>();
    final ArrayList<MoteEvent> logEvents = new ArrayList<>();
    final ArrayList<MoteEvent> watchpointEvents = new ArrayList<>();

    MoteEvents(Mote mote) {
      this.mote = mote;
      clear();
    }

    void clear() {
      this.radioRXTXEvents.clear();
      this.radioChannelEvents.clear();
      this.radioHWEvents.clear();
      this.ledEvents.clear();
      this.logEvents.clear();
      this.watchpointEvents.clear();

      if (mote.getSimulation().getSimulationTime() > 0) {
        /* Create no history events */
        radioRXTXEvents.add(new NoHistoryEvent(0));
        radioChannelEvents.add(new NoHistoryEvent(0));
        radioHWEvents.add(new NoHistoryEvent(0));
        ledEvents.add(new NoHistoryEvent(0));
        logEvents.add(new NoHistoryEvent(0));
        watchpointEvents.add(new NoHistoryEvent(0));
      }
    }

    void addRadioRXTX(RadioRXTXEvent ev) {
      radioRXTXEvents.add(ev);
    }
    void addRadioChannel(RadioChannelEvent ev) {
      radioChannelEvents.add(ev);
    }
    void addRadioHW(RadioHWEvent ev) {
      radioHWEvents.add(ev);
    }
    void addLED(LEDEvent ev) {
      ledEvents.add(ev);
    }
    void addLog(LogEvent ev) {
      logEvents.add(ev);
    }
    void addWatchpoint(WatchpointEvent ev) {
      watchpointEvents.add(ev);
    }
  }

  private long lastRepaintSimulationTime = -1;
  private final Timer repaintTimelineTimer = new Timer(TIMELINE_UPDATE_INTERVAL, new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      /* Only set new size if simulation time has changed */
      long now = simulation.getSimulationTime();
      if (now == lastRepaintSimulationTime) {
        return;
      }
      lastRepaintSimulationTime = now;

      /* Update timeline size */
      int newWidth;
      if (now/currentPixelDivisor > Integer.MAX_VALUE) {
        /* Need zoom out */
        newWidth = 1;
        needZoomOut = true;
      } else {
        newWidth = (int) (now/currentPixelDivisor);
        needZoomOut = false;
      }

      Rectangle visibleRectangle = timeline.getVisibleRect();
      boolean isTracking = visibleRectangle.x + visibleRectangle.width >= timeline.getWidth();

      int newHeight = (FIRST_MOTE_PIXEL_OFFSET + paintedMoteHeight * allMoteEvents.size());
      timeline.setPreferredSize(new Dimension(
          newWidth,
          newHeight
      ));
      timelineMoteRuler.setPreferredSize(new Dimension(
          35,
          newHeight
      ));
      timeline.revalidate();
      timeline.repaint();

      /* Update visible rectangle */
      if (isTracking) {
        Rectangle r = new Rectangle(
            newWidth-1, visibleRectangle.y,
            1, 1);
        timeline.scrollRectToVisible(r);
      }
    }
  });

  @Override
  public String getQuickHelp() {
    return
        "<b>Timeline</b>" +
        "<p>The timeline shows simulation events over time. " +
        "The timeline can be used to inspect activities of individual nodes as well as interactions between nodes." +
        "<p>For each mote, simulation events are shown on a colored line. Different colors correspond to different events. For more information about a particular event, mouse click it." +
        "<p>The <i>Events</i> menu control what event types are shown in the timeline. " +
        "Currently, six event types are supported (see below). " +
        "<p>All motes are by default shown in the timeline. Motes can be removed from the timeline by right-clicking the node ID on the left." +
        "<p>To display a vertical time marker on the timeline, press and hold the mouse on the time ruler (top)." +
        "<p>For more options for a given event, right-click the mouse for a popup menu." +
        "<p><b>Radio traffic</b>" +
        "<br>Shows radio traffic events. Transmissions are painted blue, receptions are green, and interfered radios are red." +
        "<p><b>Radio channel</b>" +
        "<br>Shows the current radio channel by colors." +
        "<p><b>Radio on/off</b>" +
        "<br>Shows whether the mote radio is on or off. When gray, the radio is on." +
        "<p><b>LEDs</b>" +
        "<br>Shows LED state: red, green, and blue. (Assumes all mote types have exactly three LEDs.)" +
        "<p><b>Log outputs</b>" +
        "<br>Shows log outputs, as also shown in " + Cooja.getDescriptionOf(LogListener.class) +
        "<p><b>Watchpoints</b>" +
        "<br>Shows triggered watchpoints, currently only supported by emulated motes. To add watchpoints, use the Msp Code Watcher plugin.";
  }
}
