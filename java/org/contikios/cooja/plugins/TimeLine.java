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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
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
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.jdom2.Element;

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

  private boolean needZoomOut = false;

  private static final Logger logger = LogManager.getLogger(TimeLine.class);

  private int paintedMoteHeight = EVENT_PIXEL_HEIGHT;
  private int paintEventMinWidth = PAINT_MIN_WIDTH_EVENTS;

  private final Simulation simulation;
  private final LogOutputListener newMotesListener;
  
  /* Experimental features: Use currently active plugin to filter Timeline Log outputs */
  private LogListener logEventFilterPlugin = null;
  private String      logEventFilterLast = "";
  private boolean     logEventFilterChanged = true;
  private boolean     logEventColorOfMote   = false;

  private final MoteRuler timelineMoteRuler = new MoteRuler();
  private final JComponent timeline = new Timeline();

  private final Observer moteHighlightObserver;
  private final ArrayList<Mote> highlightedMotes = new ArrayList<>();
  private final static Color HIGHLIGHT_COLOR = Color.CYAN;

  private final ArrayList<MoteObservation> activeMoteObservers = new ArrayList<>();

  private ArrayList<MoteEvents> allMoteEvents = new ArrayList<>();

  private boolean showRadioRXTX = true;
  private boolean showRadioChannels = false;
  private boolean showRadioOnoff = true;
  private boolean showLeds = true;
  private boolean showLogOutputs = false;
  private boolean showWatchpoints = false;

  private Point popupLocation = null;

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
        String[] options = new String[]{"Cancel", "Show"};
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
    viewMenu.add(new JSeparator());
    ButtonGroup minEvWidthButtonGroup = new ButtonGroup();
    for ( int s : new int[]{1,5,10} ) {
        JRadioButtonMenuItem evwidthMenuItemN = new JRadioButtonMenuItem(
                new ChangeMinEventWidthAction("min event width "+s, s));
        minEvWidthButtonGroup.add(evwidthMenuItemN);
        viewMenu.add(evwidthMenuItemN);
    }

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
          logger.fatal("No write access to file");
          return;
        }

        try (var outStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveFile), UTF_8))) {
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
          logger.fatal("Could not write to file: " + saveFile);
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
    simulation.getEventCentral().addLogOutputListener(newMotesListener = new LogOutputListener() {
      @Override
      public void moteWasAdded(Mote mote) {
        addMote(mote);
      }
      @Override
      public void moteWasRemoved(Mote mote) {
        removeMote(mote);
      }
      @Override
      public void removedLogOutput(LogOutputEvent ev) {
      }
      @Override
      public void newLogOutput(LogOutputEvent ev) {
        /* Log output */
        Mote mote = ev.getMote();
        LogEvent logEvent = new LogEvent(ev);
        
        /* TODO Optimize */
        for (MoteEvents moteEvents: allMoteEvents) {
          if (moteEvents.mote == mote) {
            moteEvents.addLog(logEvent);
            break;
          }
        }
      }
    });
    for (Mote m: simulation.getMotes()) {
      addMote(m);
    }

    /* Update timeline for the duration of the plugin */
    repaintTimelineTimer.start();

    Cooja.addMoteHighlightObserver(moteHighlightObserver = new Observer() {
      @Override
      public void update(Observable obs, Object obj) {
        if (!(obj instanceof Mote)) {
          return;
        }

        final Timer timer = new Timer(100, null);
        final Mote mote = (Mote) obj;
        timer.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            /* Count down */
            if (timer.getDelay() < 90) {
              timer.stop();
              highlightedMotes.remove(mote);
              repaint();
              return;
            }

            /* Toggle highlight state */
            if (highlightedMotes.contains(mote)) {
              highlightedMotes.remove(mote);
            } else {
              highlightedMotes.add(mote);
            }
            timer.setDelay(timer.getDelay()-1);
            repaint();
          }
        });
        timer.start();
      }
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
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        int w = timeline.getVisibleRect().width;

        /* centerPixel-leftPixel <=> focusCenter*w; */
        int centerPixel = (int) (focusTime/currentPixelDivisor);
        int leftPixel = (int) (focusTime/currentPixelDivisor - focusCenter*w);

        Rectangle r = new Rectangle(
            leftPixel, 0,
            w, 1
        );

        timeline.scrollRectToVisible(r);

        /* Time ruler */
        if (mark) {
          mousePixelPositionX = centerPixel;
          mouseDownPixelPositionX = centerPixel;
          mousePixelPositionY = timeline.getHeight();
        }
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

  private class ChangeMinEventWidthAction extends AbstractAction {
      private final int minWidth;
      public ChangeMinEventWidthAction(String name, int minWidth) {
        super(name);
        this.minWidth = minWidth;
      }
      @Override
      public void actionPerformed(ActionEvent e) {
          paintEventMinWidth = minWidth;
          timeline.repaint();
      }
  }

  public void clear() {
    for (MoteEvents me : allMoteEvents) {
      me.clear();
    }
    repaint();
  }


  private class MoteStatistics {
    Mote mote;
    long onTimeRedLED = 0, onTimeGreenLED = 0, onTimeBlueLED = 0;
    int nrLogs = 0;
    long radioOn = 0;
    long onTimeRX = 0, onTimeTX = 0, onTimeInterfered = 0;

    @Override
    public String toString() {
      return toString(true, true, true, true);
    }
    public String toString(boolean logs, boolean leds, boolean radioHW, boolean radioRXTX) {
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
        for (MoteEvent ev: moteEvents.ledEvents) {
          if (!(ev instanceof LEDEvent)) continue;
          LEDEvent ledEvent = (LEDEvent) ev;

          /* Red */
          if (ledEvent.red) {
            /* LED is on, add time interval */
            if (ledEvent.next == null) {
              stats.onTimeRedLED += (simulation.getSimulationTime() - ledEvent.time);
            } else {
              stats.onTimeRedLED += (ledEvent.next.time - ledEvent.time);
            }
          }

          /* Green */
          if (ledEvent.green) {
            /* LED is on, add time interval */
            if (ledEvent.next == null) {
              stats.onTimeGreenLED += (simulation.getSimulationTime() - ledEvent.time);
            } else {
              stats.onTimeGreenLED += (ledEvent.next.time - ledEvent.time);
            }
          }

          /* Blue */
          if (ledEvent.blue) {
            /* LED is on, add time interval */
            if (ledEvent.next == null) {
              stats.onTimeBlueLED += (simulation.getSimulationTime() - ledEvent.time);
            } else {
              stats.onTimeBlueLED += (ledEvent.next.time - ledEvent.time);
            }
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
        for (MoteEvent ev: moteEvents.radioHWEvents) {
          if (!(ev instanceof RadioHWEvent)) continue;
          RadioHWEvent hwEvent = (RadioHWEvent) ev;
          if (hwEvent.on) {
            /* HW is on */
            if (hwEvent.next == null) {
              stats.radioOn += (simulation.getSimulationTime() - hwEvent.time);
            } else {
              stats.radioOn += (hwEvent.next.time - hwEvent.time);
            }
          }
        }
      }

      if (radioRXTX) {
        for (MoteEvent ev: moteEvents.radioRXTXEvents) {
          if (!(ev instanceof RadioRXTXEvent)) continue;
          RadioRXTXEvent rxtxEvent = (RadioRXTXEvent) ev;
          if (rxtxEvent.state == RXTXRadioEvent.IDLE) {
            continue;
          }

          long diff;
          if (rxtxEvent.next == null) {
            diff = (simulation.getSimulationTime() - rxtxEvent.time);
          } else {
            diff = (rxtxEvent.next.time - rxtxEvent.time);
          }

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

    if (allStats.size() == 0) {
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
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        /* Mark selected time in time ruler */
        final int toPixel = (int) (toTime / currentPixelDivisor);
        mousePixelPositionX = toPixel;
        mouseDownPixelPositionX = toPixel;
        mousePixelPositionY = timeline.getHeight();

        /* Check if time is already visible */
        Rectangle vis = timeline.getVisibleRect();
        if (toPixel >= vis.x && toPixel < vis.x + vis.width) {
          repaint();
          return;
        }

        forceRepaintAndFocus(toTime, 0.5, false);
      }
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

  private boolean executionDetails = false;
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
    private Observer observer;
    private Observable observable;
    private Mote mote;

    private WatchpointMote watchpointMote; /* XXX */
    private WatchpointListener watchpointListener; /* XXX */

    public MoteObservation(Mote mote, Observable observable, Observer observer) {
      this.mote = mote;
      this.observable = observable;
      this.observer = observer;
    }

    /* XXX Special case, should be generalized */
    public MoteObservation(Mote mote, WatchpointMote watchpointMote, WatchpointListener listener) {
      this.mote = mote;
      this.watchpointMote = watchpointMote;
      this.watchpointListener = listener;
    }

    public Mote getMote() {
      return mote;
    }

    /**
     * Disconnect observer from observable (stop observing) and clean up resources (remove pointers).
     */
    public void dispose() {
      if (observable != null) {
        observable.deleteObserver(observer);
        mote = null;
        observable = null;
        observer = null;
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
      Observer observer = new Observer() {
        @Override
        public void update(Observable o, Object arg) {
          LEDEvent ev = new LEDEvent(
              simulation.getSimulationTime(),
              moteLEDs.isRedOn(),
              moteLEDs.isGreenOn(),
              moteLEDs.isYellowOn()
          );

          moteEvents.addLED(ev);
        }
      };

      moteLEDs.addObserver(observer);
      activeMoteObservers.add(new MoteObservation(mote, moteLEDs, observer));
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
      Observer observer = new Observer() {
        int lastChannel = -1;
        @Override
        public void update(Observable o, Object arg) {
          RadioEvent radioEv = moteRadio.getLastEvent();

          String details = null;
          if (executionDetails && mote instanceof AbstractEmulatedMote) {
            details = ((AbstractEmulatedMote) mote).getExecutionDetails();
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

      moteRadio.addObserver(observer);
      activeMoteObservers.add(new MoteObservation(mote, moteRadio, observer));
    }

    /* Watchpoints */
    if (mote instanceof WatchpointMote watchpointMote) {
      WatchpointListener listener = new WatchpointListener() {
        @Override
        public void watchpointTriggered(Watchpoint watchpoint) {
          WatchpointEvent ev = new WatchpointEvent(simulation.getSimulationTime(), watchpoint);

          if (executionDetails && mote instanceof AbstractEmulatedMote) {
            String details = ((AbstractEmulatedMote) mote).getExecutionDetails();
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

    if (moteHighlightObserver != null) {
      Cooja.deleteMoteHighlightObserver(moteHighlightObserver);
    }

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
  public boolean setConfigXML(Simulation sim, Collection<Element> configXML) {
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
    public Timeline() {
      setLayout(null);
      setToolTipText(null);
      setBackground(COLOR_BACKGROUND);
      var mouseAdapter = new MouseAdapter() {
        private Popup popUpToolTip = null;
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
            if (t.getTipText() == null || t.getTipText().equals("")) {
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

      long intervalStart = (long)(bounds.x*currentPixelDivisor);
      long intervalEnd = (long) (intervalStart + bounds.width*currentPixelDivisor);

      if (intervalEnd > simulation.getSimulationTime()) {
        intervalEnd = simulation.getSimulationTime();
      }
      if (bounds.x > Integer.MAX_VALUE - 1000) {
        /* Strange bounds */
        return;
      }

      g.setColor(COLOR_BACKGROUND);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      drawTimeRule(g, intervalStart, intervalEnd);

      // invalidate filter stamp
      if (logEventFilterPlugin != null) {
          if (logEventFilterPlugin.getFilter() != logEventFilterLast) {
              logEventFilterLast = logEventFilterPlugin.getFilter();
              logEventFilterChanged = true;
          }
          else
              logEventFilterChanged = false;
      }
      else
          logEventFilterChanged = false;
      
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
          MoteEvent firstEvent = getFirstIntervalEvent(events.radioRXTXEvents, intervalStart);
          if (firstEvent != null) {
            firstEvent.paintInterval(g, lineHeightOffset, intervalEnd);
          }
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showRadioChannels) {
          MoteEvent firstEvent = getFirstIntervalEvent(events.radioChannelEvents, intervalStart);
          if (firstEvent != null) {
            firstEvent.paintInterval(g, lineHeightOffset, intervalEnd);
          }
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showRadioOnoff) {
          MoteEvent firstEvent = getFirstIntervalEvent(events.radioHWEvents, intervalStart);
          if (firstEvent != null) {
            firstEvent.paintInterval(g, lineHeightOffset, intervalEnd);
          }
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showLeds) {
          MoteEvent firstEvent = getFirstIntervalEvent(events.ledEvents, intervalStart);
          if (firstEvent != null) {
            firstEvent.paintInterval(g, lineHeightOffset, intervalEnd);
          }
          lineHeightOffset += 3*LED_PIXEL_HEIGHT;
        }
        if (showLogOutputs) {
          MoteEvent firstEvent = getFirstIntervalEvent(events.logEvents, intervalStart);
          if (firstEvent != null) {
            firstEvent.paintInterval(g, lineHeightOffset, intervalEnd);
          }
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }
        if (showWatchpoints) {
          MoteEvent firstEvent = getFirstIntervalEvent(events.watchpointEvents, intervalStart);
          if (firstEvent != null) {
            firstEvent.paintInterval(g, lineHeightOffset, intervalEnd);
          }
          lineHeightOffset += EVENT_PIXEL_HEIGHT;
        }

        lineHeightOffset += EVENT_PIXEL_HEIGHT;
      }

      /* Draw vertical time marker (if mouse is dragged) */
      drawMouseTime(g, intervalStart, intervalEnd);
    }

    private <T extends MoteEvent> T getFirstIntervalEvent(ArrayList<T> events, long time) {
      /* TODO IMPLEMENT ME: Binary search */
      int nrEvents = events.size();
      if (nrEvents == 0) {
        return null;
      }
      if (nrEvents == 1) {
        events.get(0);
      }

      int ev = 0;
      while (ev < nrEvents && events.get(ev).time < time) {
        ev++;
      }
      ev--;
      if (ev < 0) {
        ev = 0;
      }

      if (ev >= events.size()) {
        return events.get(events.size()-1);
      }
      return events.get(ev);
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

    public String getMouseToolTipText(MouseEvent event) {
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
        MoteEvent ev = getFirstIntervalEvent(events, time);
        if (ev != null && time >= ev.time) {
          tooltip += ev + "<br>";

        	if (ev.details != null) {
        		tooltip += "Details:<br>" + ev.details;
        	}
        }
      }

      tooltip += "</html>";
      return tooltip;
    }
  }

  class MoteRuler extends JPanel {
    public MoteRuler() {
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
          Cooja.signalMoteHighlight(m);

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
  abstract class MoteEvent {
    MoteEvent prev = null;
    MoteEvent next = null;
    String details = null;
    final long time;
    public MoteEvent(long time) {
      this.time = time;
    }

    /**
     * Used by the default paint method to color events.
     * The event is not painted if the returned color is null.
     *
     * @see #paintInterval(Graphics, int, long)
     * @return Event color or null
     */
    public abstract Color getEventColor();

    /* Default paint method */
    public void paintInterval(Graphics g, int lineHeightOffset, long end) {
      MoteEvent ev = this;
      while (ev != null && ev.time < end) {
        int w; /* Pixel width */

        /* Calculate event width */
        if (ev.next != null) {
          w = (int) ((ev.next.time - ev.time)/currentPixelDivisor);
        } else {
          w = (int) ((end - ev.time)/currentPixelDivisor); /* No more events */
        }

        /* Handle zero pixel width events */
        if (w == 0) {
          if (PAINT_ZERO_WIDTH_EVENTS) {
            w = 1;
          } else {
            ev = ev.next;
            continue;
          }
        }

        if( w < paintEventMinWidth)
            w = paintEventMinWidth;

        Color color = ev.getEventColor();
        if (color == null) {
          /* Skip painting event */
          ev = ev.next;
          continue;
        }
        g.setColor(color);

        g.fillRect(
            (int)(ev.time/currentPixelDivisor), lineHeightOffset,
            w, EVENT_PIXEL_HEIGHT
        );

        ev = ev.next;
      }
    }
  }
  class NoHistoryEvent extends MoteEvent {
    public NoHistoryEvent(long time) {
      super(time);
    }
    @Override
    public Color getEventColor() {
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
  class RadioRXTXEvent extends MoteEvent {
    final RXTXRadioEvent state;
    public RadioRXTXEvent(long time, RXTXRadioEvent ev) {
      super(time);
      this.state = ev;
    }
    @Override
    public Color getEventColor() {
      if (state == RXTXRadioEvent.IDLE) {
        return null;
      } else if (state == RXTXRadioEvent.TRANSMITTING) {
        return Color.BLUE;
      } else if (state == RXTXRadioEvent.RECEIVING) {
        return Color.GREEN;
      } else if (state == RXTXRadioEvent.INTERFERED) {
        return Color.RED;
      } else {
        logger.fatal("Unknown RXTX event");
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

  private final static Color[] CHANNEL_COLORS = new Color[] {
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
  class RadioChannelEvent extends MoteEvent {
    final int channel;
    final boolean radioOn;
    public RadioChannelEvent(long time, int channel, boolean radioOn) {
      super(time);
      this.channel = channel;
      this.radioOn = radioOn;
    }
    @Override
    public Color getEventColor() {
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

  class RadioHWEvent extends MoteEvent {
    final boolean on;
    public RadioHWEvent(long time, boolean on) {
      super(time);
      this.on = on;
    }
    @Override
    public Color getEventColor() {
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
  class LEDEvent extends MoteEvent {
    final boolean red;
    final boolean green;
    final boolean blue;
    final Color color;
    public LEDEvent(long time, boolean red, boolean green, boolean blue) {
      super(time);
      this.red = red;
      this.green = green;
      this.blue = blue;
      this.color = new Color(red?255:0, green?255:0, blue?255:0);
    }
    @Override
    public Color getEventColor() {
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
    public void paintInterval(Graphics g, int lineHeightOffset, long end) {
      MoteEvent ev = this;
      while (ev != null && ev.time < end) {
        int w; /* Pixel width */

        /* Calculate event width */
        if (ev.next != null) {
          w = (int) ((ev.next.time - ev.time)/currentPixelDivisor);
        } else {
          w = (int) ((end - ev.time)/currentPixelDivisor); /* No more events */
        }

        /* Handle zero pixel width events */
        if (w == 0) {
          if (PAINT_ZERO_WIDTH_EVENTS) {
            w = 1;
          } else {
            ev = ev.next;
            continue;
          }
        }

        if( w < paintEventMinWidth)
            w = paintEventMinWidth;

        Color color = ev.getEventColor();
        if (color == null) {
          /* Skip painting event */
          ev = ev.next;
          continue;
        }
        if (color.getRed() > 0) {
          g.setColor(new Color(color.getRed(), 0, 0));
          g.fillRect(
              (int)(ev.time/currentPixelDivisor), lineHeightOffset,
              w, LED_PIXEL_HEIGHT
          );
        }
        if (color.getGreen() > 0) {
          g.setColor(new Color(0, color.getGreen(), 0));
          g.fillRect(
              (int)(ev.time/currentPixelDivisor), lineHeightOffset+LED_PIXEL_HEIGHT,
              w, LED_PIXEL_HEIGHT
          );
        }
        if (color.getBlue() > 0) {
          g.setColor(new Color(0, 0, color.getBlue()));
          g.fillRect(
              (int)(ev.time/currentPixelDivisor), lineHeightOffset+2*LED_PIXEL_HEIGHT,
              w, LED_PIXEL_HEIGHT
          );
        }
        ev = ev.next;
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

  class LogEvent extends MoteEvent {
    final LogOutputEvent logEvent;
    // filter result cache
    private  FilterState     filtered;

    public LogEvent(LogOutputEvent ev) {
      super(ev.getTime());
      this.logEvent = ev;
      this.filtered = FilterState.NONE;
    }

    @Override
    public Color getEventColor() {
      if (logEventColorOfMote && logEventFilterPlugin != null) {
        /* Ask log listener for event color to use */
        return logEventFilterPlugin.getColorOfEntry(logEvent);
      }
      return Color.GREEN;
    }
    /* Default paint method */
    @Override
    public void paintInterval(Graphics g, int lineHeightOffset, long end) {
      LogEvent ev = this;
      long time_start = -1;
      
      while (ev != null && ev.time < end) {
          int position = (int)(ev.time/currentPixelDivisor);
          if (ev.time < time_start ){
              /* Skip painting event over already painted one*/
              ev = (LogEvent) ev.next;
              continue;
          }
          
          /* Ask active log listener whether this should be filtered  */
        
        if (logEventFilterPlugin != null) {
          if (logEventFilterChanged || (filtered == FilterState.NONE) ) {
              boolean show = logEventFilterPlugin.filterWouldAccept(ev.logEvent);
              if (show)
                  filtered = FilterState.PASS;
              else
                  filtered = FilterState.REJECTED;
          }
          
          if (filtered == FilterState.REJECTED) {
            /* Skip painting event */
            ev = (LogEvent) ev.next;
            continue;
          }
        }

        Color color = ev.getEventColor();
        if (color == null) {
          /* Skip painting event */
          ev = (LogEvent) ev.next;
          continue;
        }

        // upper bound of start position
        time_start = (long)((position+1)*currentPixelDivisor);

        g.setColor(color);
        g.fillRect( position, lineHeightOffset, 4, EVENT_PIXEL_HEIGHT );

        g.setColor(Color.BLUE);
        g.fillRect( position, lineHeightOffset, 1, EVENT_PIXEL_HEIGHT );

        ev = (LogEvent) ev.next;
      }
    }
    @Override
    public String toString() {
      return "Mote " + logEvent.getMote() + " says:<br>" + logEvent.getMessage() + "<br>";
    }
  }
  class WatchpointEvent extends MoteEvent {
    final Watchpoint watchpoint;
    public WatchpointEvent(long time, Watchpoint watchpoint) {
      super(time);
      this.watchpoint = watchpoint;
    }
    @Override
    public Color getEventColor() {
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

    /* Default paint method */
    @Override
    public void paintInterval(Graphics g, int lineHeightOffset, long end) {
      MoteEvent ev = this;
      while (ev != null && ev.time < end) {
        int w = 2; /* Watchpoints are always two pixels wide */

        Color color = ev.getEventColor();
        if (color == null) {
          /* Skip painting event */
          ev = ev.next;
          continue;
        }
        g.setColor(color);

        g.fillRect(
            (int)(ev.time/currentPixelDivisor), lineHeightOffset,
            w, EVENT_PIXEL_HEIGHT
        );

        ev = ev.next;
      }
    }
  }
  class MoteEvents {
    final Mote mote;
    final ArrayList<MoteEvent> radioRXTXEvents = new ArrayList<>();
    final ArrayList<MoteEvent> radioChannelEvents = new ArrayList<>();
    final ArrayList<MoteEvent> radioHWEvents = new ArrayList<>();
    final ArrayList<MoteEvent> ledEvents = new ArrayList<>();
    final ArrayList<MoteEvent> logEvents = new ArrayList<>();
    final ArrayList<MoteEvent> watchpointEvents = new ArrayList<>();

    private MoteEvent lastRadioRXTXEvent = null;
    private MoteEvent lastRadioChannelEvent = null;
    private MoteEvent lastRadioHWEvent = null;
    private MoteEvent lastLEDEvent = null;
    private MoteEvent lastLogEvent = null;
    private MoteEvent lastWatchpointEvent = null;

    public MoteEvents(Mote mote) {
      this.mote = mote;
      if (mote.getSimulation().getSimulationTime() > 0) {
        /* Create no history events */
        lastRadioRXTXEvent = new NoHistoryEvent(0);
        lastRadioChannelEvent = new NoHistoryEvent(0);
        lastRadioHWEvent = new NoHistoryEvent(0);
        lastLEDEvent = new NoHistoryEvent(0);
        lastLogEvent = new NoHistoryEvent(0);
        lastWatchpointEvent = new NoHistoryEvent(0);
        radioRXTXEvents.add(lastRadioRXTXEvent);
        radioChannelEvents.add(lastRadioChannelEvent);
        radioHWEvents.add(lastRadioHWEvent);
        ledEvents.add(lastLEDEvent);
        logEvents.add(lastLogEvent);
        watchpointEvents.add(lastWatchpointEvent);
      }
    }

    protected void clear() {
      this.radioRXTXEvents.clear();
      this.radioChannelEvents.clear();
      this.radioHWEvents.clear();
      this.ledEvents.clear();
      this.logEvents.clear();
      this.watchpointEvents.clear();

      if (mote.getSimulation().getSimulationTime() > 0) {
        /* Create no history events */
        lastRadioRXTXEvent = new NoHistoryEvent(0);
        lastRadioChannelEvent = new NoHistoryEvent(0);
        lastRadioHWEvent = new NoHistoryEvent(0);
        lastLEDEvent = new NoHistoryEvent(0);
        lastLogEvent = new NoHistoryEvent(0);
        lastWatchpointEvent = new NoHistoryEvent(0);
        radioRXTXEvents.add(lastRadioRXTXEvent);
        radioChannelEvents.add(lastRadioChannelEvent);
        radioHWEvents.add(lastRadioHWEvent);
        ledEvents.add(lastLEDEvent);
        logEvents.add(lastLogEvent);
        watchpointEvents.add(lastWatchpointEvent);
      }
    }

    public void addRadioRXTX(RadioRXTXEvent ev) {
      /* Link with previous events */
      if (lastRadioRXTXEvent != null) {
        ev.prev = lastRadioRXTXEvent;
        lastRadioRXTXEvent.next = ev;
      }
      lastRadioRXTXEvent = ev;

      radioRXTXEvents.add(ev);
    }
    public void addRadioChannel(RadioChannelEvent ev) {
      /* Link with previous events */
      if (lastRadioChannelEvent != null) {
        ev.prev = lastRadioChannelEvent;
        lastRadioChannelEvent.next = ev;
      }
      lastRadioChannelEvent = ev;

      radioChannelEvents.add(ev);
    }
    public void addRadioHW(RadioHWEvent ev) {
      /* Link with previous events */
      if (lastRadioHWEvent != null) {
        ev.prev = lastRadioHWEvent;
        lastRadioHWEvent.next = ev;
      }
      lastRadioHWEvent = ev;

      radioHWEvents.add(ev);
    }
    public void addLED(LEDEvent ev) {
      /* Link with previous events */
      if (lastLEDEvent != null) {
        ev.prev = lastLEDEvent;
        lastLEDEvent.next = ev;
      }
      lastLEDEvent = ev;

      ledEvents.add(ev);
    }
    public void addLog(LogEvent ev) {
      /* Link with previous events */
      if (lastLogEvent != null) {
        ev.prev = lastLogEvent;
        lastLogEvent.next = ev;
      }
      lastLogEvent = ev;

      logEvents.add(ev);
    }
    public void addWatchpoint(WatchpointEvent ev) {
      /* Link with previous events */
      if (lastWatchpointEvent != null) {
        ev.prev = lastWatchpointEvent;
        lastWatchpointEvent.next = ev;
      }
      lastWatchpointEvent = ev;

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
