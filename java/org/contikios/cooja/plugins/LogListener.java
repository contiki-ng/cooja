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
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.SimEventCentral.LogOutputEvent;
import org.contikios.cooja.SimEventCentral.LogOutputListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.dialogs.TableColumnAdjuster;
import org.contikios.cooja.dialogs.UpdateAggregator;
import org.contikios.cooja.util.ArrayQueue;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple mote log listener.
 * Listens to all motes' log interfaces.
 *
 * @author Fredrik Osterlind, Niclas Finne
 */
@ClassDescription("Mote output")
@PluginType(PluginType.PType.SIM_STANDARD_PLUGIN)
public class LogListener extends VisPlugin implements HasQuickHelp {
  private static final Logger logger = LoggerFactory.getLogger(LogListener.class);

  private final Color[] BG_COLORS = {
      new Color(200, 200, 200),
      new Color(200, 200, 255),
      new Color(200, 255, 200),
      new Color(200, 255, 255),
      new Color(255, 200, 200),
      new Color(255, 255, 200),
      new Color(255, 255, 255),
      new Color(255, 220, 200),
      new Color(220, 255, 220),
      new Color(255, 200, 255),
  };

  private final static int COLUMN_TIME = 0;
  private final static int COLUMN_FROM = 1;
  private final static int COLUMN_DATA = 2;
  private final static int COLUMN_CONCAT = 3;
  private final static String[] COLUMN_NAMES = {
    "Time ms",
    "Mote",
    "Message",
    "#"
  };

  public static final long TIME_SECOND = 1000*Simulation.MILLISECOND;
  public static final long TIME_MINUTE = 60*TIME_SECOND;
  public static final long TIME_HOUR = 60*TIME_MINUTE;

  private boolean formatTimeString = true;
  private boolean hasHours;

  private final JTable logTable;
  private final TableRowSorter<TableModel> logFilter;
  private final ArrayQueue<LogData> logs = new ArrayQueue<>();

  private final Simulation simulation;

  private final JTextField filterTextField;
  private final JLabel filterLabel = new JLabel("Filter: ");
  private final Color filterTextFieldBackground;

  private final AbstractTableModel model;

  private final LogOutputListener logOutputListener;

  private boolean backgroundColors = true;
  private final JCheckBoxMenuItem colorCheckbox;

  private boolean inverseFilter;
  private final JCheckBoxMenuItem inverseFilterCheckbox;

  private boolean hideDebug;
  private final JCheckBoxMenuItem hideDebugCheckbox;

  private final JCheckBoxMenuItem appendCheckBox;

  private static final int UPDATE_INTERVAL = 250;
  private final UpdateAggregator<LogData> logUpdateAggregator = new UpdateAggregator<>(UPDATE_INTERVAL) {
    private final Runnable scroll = new Runnable() {
      @Override
      public void run() {
        logTable.scrollRectToVisible(
            new Rectangle(0, logTable.getHeight() - 2, 1, logTable.getHeight()));
      }
    };
    @Override
    protected void handle(List<LogData> ls) {
      boolean isVisible = true;
      if (logTable.getRowCount() > 0) {
        Rectangle visible = logTable.getVisibleRect();
        if (visible.y + visible.height < logTable.getHeight()) {
          isVisible = false;
        }
      }

      /* Add */
      int index = logs.size();
      logs.addAll(ls);
      model.fireTableRowsInserted(index, logs.size()-1);

      /* Remove old */
      int removed = 0;
      int log_limit = simulation.getEventCentral().getLogOutputBufferSize();
      while (logs.size() > log_limit ) {
        logs.remove(0);
        removed++;
      }
      if (removed > 0) {
        model.fireTableRowsDeleted(0, removed-1);
      }

      if (isVisible) {
        SwingUtilities.invokeLater(scroll);
      }
    }
  };

  /**
   * @param simulation Simulation
   * @param gui GUI
   */
  public LogListener(final Simulation simulation, final Cooja gui) {
    super("Mote output", gui);
    this.simulation = simulation;

    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    JMenu editMenu = new JMenu("Edit");
    JMenu showMenu = new JMenu("View");

    menuBar.add(fileMenu);
    menuBar.add(editMenu);
    menuBar.add(showMenu);
    this.setJMenuBar(menuBar);

    Action copyAllAction = new AbstractAction("Copy all data") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        StringBuilder sb = new StringBuilder();
        for (var data : logs) {
          sb.append(data.getTime()).append("\t");
          sb.append(data.getID()).append("\t");
          sb.append(data.ev.getMessage()).append("\n");
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
      }
    };
    editMenu.add(new JMenuItem(copyAllAction));
    Action copyAllMessagesAction = new AbstractAction("Copy all messages") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        StringBuilder sb = new StringBuilder();
        for (var data : logs) {
          sb.append(data.ev.getMessage()).append("\n");
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
      }
    };
    editMenu.add(new JMenuItem(copyAllMessagesAction));
    Action copyAction = new AbstractAction("Copy selected") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        int[] selectedRows = logTable.getSelectedRows();

        StringBuilder sb = new StringBuilder();
        for (int i : selectedRows) {
          sb.append(logTable.getValueAt(i, COLUMN_TIME)).append("\t");
          sb.append(logTable.getValueAt(i, COLUMN_FROM)).append("\t");
          sb.append(logTable.getValueAt(i, COLUMN_DATA)).append("\n");
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
      }
    };
    editMenu.add(new JMenuItem(copyAction));
    editMenu.addSeparator();
    Action clearAction = new AbstractAction("Clear all messages") {
      @Override
      public void actionPerformed(ActionEvent e) {
        clear();
      }
    };
    editMenu.add(new JMenuItem(clearAction));


    Action saveAction = new AbstractAction("Save to file") {
      @Override
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        File suggest = new File(Cooja.getExternalToolsSetting("LOG_LISTENER_SAVEFILE", "loglistener.txt"));
        fc.setSelectedFile(suggest);
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

        Cooja.setExternalToolsSetting("LOG_LISTENER_SAVEFILE", saveFile.getPath());
        if (saveFile.exists() && !saveFile.canWrite()) {
          logger.error("No write access to file: " + saveFile);
          return;
        }

        try (var outStream = new PrintWriter(Files.newBufferedWriter(saveFile.toPath(), UTF_8))) {
          for (LogData data : logs) {
            outStream.println(
                    data.getTime() + "\t" +
                            data.getID() + "\t" +
                            data.ev.getMessage());
          }
        } catch (Exception ex) {
          logger.error("Could not write to file: " + saveFile);
        }
      }
    };
    fileMenu.add(new JMenuItem(saveAction));
    Action appendAction = new AbstractAction("Append to file") {
      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBoxMenuItem cb = (JCheckBoxMenuItem) e.getSource();
        appendToFile = cb.isSelected();
        if (!appendToFile) {
          appendToFile(null, null);
          appendStreamFile = null;
          return;
        }

        JFileChooser fc = new JFileChooser();
        File suggest = new File(Cooja.getExternalToolsSetting("LOG_LISTENER_APPENDFILE", "loglistener_append.txt"));
        fc.setSelectedFile(suggest);
        int returnVal = fc.showSaveDialog(Cooja.getTopParentContainer());
        if (returnVal != JFileChooser.APPROVE_OPTION) {
          appendToFile = false;
          cb.setSelected(false);
          return;
        }

        File saveFile = fc.getSelectedFile();
        Cooja.setExternalToolsSetting("LOG_LISTENER_APPENDFILE", saveFile.getPath());
        if (saveFile.exists() && !saveFile.canWrite()) {
          logger.error("No write access to file: " + saveFile);
          appendToFile = false;
          cb.setSelected(false);
          return;
        }
        appendToFile = true;
        appendStreamFile = saveFile;
        if (!appendStreamFile.exists()) {
          try {
            appendStreamFile.createNewFile();
          } catch (IOException ex) {
          }
        }
      }
    };
    appendCheckBox = new JCheckBoxMenuItem(appendAction);
    fileMenu.add(appendCheckBox);

    colorCheckbox = new JCheckBoxMenuItem("Mote-specific coloring", backgroundColors);
    showMenu.add(colorCheckbox);
    colorCheckbox.addActionListener(e -> {
      backgroundColors = colorCheckbox.isSelected();
      repaint();
    });
    hideDebugCheckbox = new JCheckBoxMenuItem("Hide \"DEBUG: \" messages");
    showMenu.add(hideDebugCheckbox);
    hideDebugCheckbox.addActionListener(e -> {
      hideDebug = hideDebugCheckbox.isSelected();
      setFilter(getFilter());
      repaint();
    });
    inverseFilterCheckbox = new JCheckBoxMenuItem("Inverse filter");
    showMenu.add(inverseFilterCheckbox);
    inverseFilterCheckbox.addActionListener(e -> {
      inverseFilter = inverseFilterCheckbox.isSelected();
      if (inverseFilter) {
        filterLabel.setText("Exclude:");
      } else {
        filterLabel.setText("Filter:");
      }
      setFilter(getFilter());
      repaint();
    });


    model = new AbstractTableModel() {
      @Override
      public String getColumnName(int col) {
      	if (col == COLUMN_TIME && formatTimeString) {
    			return "Time";
      	}
        return COLUMN_NAMES[col];
      }
      @Override
      public int getRowCount() {
        return logs.size();
      }
      @Override
      public int getColumnCount() {
        return COLUMN_NAMES.length;
      }
      @Override
      public Object getValueAt(int row, int col) {
        LogData log = logs.get(row);
        if (col == COLUMN_TIME) {
          return log.getTime();
        } else if (col == COLUMN_FROM) {
          return log.getID();
        } else if (col == COLUMN_DATA) {
          return log.ev.getMessage();
        } else if (col == COLUMN_CONCAT) {
          return log.getID() + ' ' + log.ev.getMessage();
        }
        return null;
      }
    };

    logTable = new JTable(model) {
      @Override
      public String getToolTipText(MouseEvent e) {
        java.awt.Point p = e.getPoint();
        int rowIndex = rowAtPoint(p);
        int colIndex = columnAtPoint(p);
        int columnIndex = convertColumnIndexToModel(colIndex);
        if (rowIndex < 0 || columnIndex < 0) {
          return super.getToolTipText(e);
        }
        Object v = getValueAt(rowIndex, columnIndex);
        if (v != null) {
          String t = v.toString();
          if (t.length() > 60) {
            StringBuilder sb = new StringBuilder();
            sb.append("<html>");
            do {
              sb.append(t, 0, 60).append("<br>");
              t = t.substring(60);
            } while (t.length() > 60);
            return sb.append(t).append("</html>").toString();
          }
        }
        return super.getToolTipText(e);
      }
    };
    DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
          Object value, boolean isSelected, boolean hasFocus, int row,
          int column) {
      	if (row >= logTable.getRowCount()) {
          return super.getTableCellRendererComponent(
              table, value, isSelected, hasFocus, row, column);
      	}

      	if (backgroundColors) {
          LogData d = logs.get(logTable.getRowSorter().convertRowIndexToModel(row));
          int color = (10+d.ev.getMote().getID())%10;
          setBackground(BG_COLORS[color]);
        } else {
          setBackground(null);
        }

        return super.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, column);
      }
    };
    logTable.getColumnModel().getColumn(COLUMN_TIME).setCellRenderer(cellRenderer);
    logTable.getColumnModel().getColumn(COLUMN_FROM).setCellRenderer(cellRenderer);
    logTable.getColumnModel().getColumn(COLUMN_DATA).setCellRenderer(cellRenderer);
    logTable.getColumnModel().removeColumn(logTable.getColumnModel().getColumn(COLUMN_CONCAT));
    logTable.setFillsViewportHeight(true);
    logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    logTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
    logTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          showInAllAction.actionPerformed(null);
        }
      }
    });
    logFilter = new TableRowSorter<>(model);
    for (int i = 0, n = model.getColumnCount(); i < n; i++) {
      logFilter.setSortable(i, false);
    }
    logTable.setRowSorter(logFilter);

    /* Toggle time format */
    logTable.getTableHeader().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int colIndex = logTable.columnAtPoint(e.getPoint());
        int columnIndex = logTable.convertColumnIndexToModel(colIndex);
        if (columnIndex != COLUMN_TIME) {
        	return;
        }
    		formatTimeString = !formatTimeString;
    		repaintTimeColumn();
    	}
		});
    logTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int colIndex = logTable.columnAtPoint(e.getPoint());
        int columnIndex = logTable.convertColumnIndexToModel(colIndex);
        if (columnIndex != COLUMN_FROM) {
        	return;
        }

        int rowIndex = logTable.rowAtPoint(e.getPoint());
        if (rowIndex == -1) {
          return;
        }
        LogData d = logs.get(logTable.getRowSorter().convertRowIndexToModel(rowIndex));
        if (d == null) {
        	return;
        }
        gui.signalMoteHighlight(d.ev.getMote());
    	}
		});

    /* Automatically update column widths */
    final TableColumnAdjuster adjuster = new TableColumnAdjuster(logTable);
    adjuster.packColumns();

    /* Popup menu */

    JPopupMenu popupMenu = new JPopupMenu();
    JMenu focusMenu = new JMenu("Show in");
    focusMenu.add(new JMenuItem(showInAllAction));
    focusMenu.addSeparator();
    focusMenu.add(new JMenuItem(timeLineAction));
    focusMenu.add(new JMenuItem(radioLoggerAction));
    popupMenu.add(focusMenu);
    /* Fetch log output history */
    LogOutputEvent[] history = simulation.getEventCentral().getLogOutputHistory();
    if (history.length > 0) {
      for (LogOutputEvent historyEv: history) {
      	if (!hasHours && historyEv.getTime() > TIME_HOUR) {
      		hasHours = true;
      		repaintTimeColumn();
      	}
        LogData data = new LogData(historyEv);
        logs.add(data);
      }
      java.awt.EventQueue.invokeLater(() -> {
        model.fireTableDataChanged();
        logTable.scrollRectToVisible(new Rectangle(0, logTable.getHeight() - 2, 1, logTable.getHeight()));
      });
    }

    /* Column width adjustment */
    java.awt.EventQueue.invokeLater(() -> {
      /* Make sure this happens *after* adding history */
      adjuster.setDynamicAdjustment(true);
    });

    /* Start observing motes for new log output */
    logUpdateAggregator.start();
    simulation.getEventCentral().addLogOutputListener(logOutputListener = ev -> {
      if (!hasHours && ev.getTime() > TIME_HOUR) {
        hasHours = true;
        repaintTimeColumn();
      }
      var data = new LogData(ev);
      logUpdateAggregator.add(data);
      if (appendToFile) {
        appendToFile(appendStreamFile, data.getTime() + "\t" + data.getID() + "\t" + data.ev.getMessage() + "\n");
      }
    });

    /* UI components */
    JPanel filterPanel = new JPanel();
    filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.X_AXIS));
    filterTextField = new JTextField("");
    filterTextFieldBackground = filterTextField.getBackground();
    filterPanel.add(Box.createHorizontalStrut(2));
    filterPanel.add(filterLabel);
    filterPanel.add(filterTextField);
    filterTextField.addActionListener(e -> {
      String str = filterTextField.getText();
      setFilter(str);
      // Autoscroll.
      int s = logTable.getSelectedRow();
      if (s < 0) {
        return;
      }
      s = logTable.getRowSorter().convertRowIndexToView(s);
      if (s < 0) {
        return;
      }
      int v = logTable.getRowHeight() * s;
      logTable.scrollRectToVisible(new Rectangle(0, v - 5, 1, v + 5));
    });
    filterPanel.add(Box.createHorizontalStrut(2));

    getContentPane().add(BorderLayout.CENTER, new JScrollPane(logTable));
    getContentPane().add(BorderLayout.SOUTH, filterPanel);

    pack();

    /* XXX HACK: here we set the position and size of the window when it appears on a blank simulation screen. */
    this.setLocation(400, 160);
    this.setSize(Cooja.getDesktopPane().getWidth() - 400, 240);
  }

  private void repaintTimeColumn() {
  	logTable.getColumnModel().getColumn(COLUMN_TIME).setHeaderValue(
  			logTable.getModel().getColumnName(COLUMN_TIME));
  	repaint();
	}

  @Override
  public void closePlugin() {
    /* Stop observing motes */
    appendToFile(null, null);
    logUpdateAggregator.stop();
    simulation.getEventCentral().removeLogOutputListener(logOutputListener);
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    element = new Element("filter");
    element.setText(filterTextField.getText());
    config.add(element);

    if (formatTimeString) {
    	element = new Element("formatted_time");
    	config.add(element);
    }
    if (backgroundColors) {
      element = new Element("coloring");
      config.add(element);
    }
    if (hideDebug) {
      element = new Element("hidedebug");
      config.add(element);
    }
    if (inverseFilter) {
    	element = new Element("inversefilter");
    	config.add(element);
    }
    if (appendToFile) {
      element = new Element("append");
      element.setText(simulation.getCooja().createPortablePath(appendStreamFile).getPath());
      config.add(element);
    }
    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      String name = element.getName();
      if ("filter".equals(name)) {
        setFilter(element.getText());
      } else if ("coloring".equals(name)) {
        backgroundColors = true;
        colorCheckbox.setSelected(true);
      } else if ("hidedebug".equals(name)) {
        hideDebug = true;
        hideDebugCheckbox.setSelected(true);
      } else if ("inversefilter".equals(name)) {
      	inverseFilter = true;
      	inverseFilterCheckbox.setSelected(true);
      } else if ("formatted_time".equals(name)) {
      	formatTimeString = true;
      	repaintTimeColumn();
      } else if ("append".equals(name)) {
        appendToFile = true;
        appendStreamFile = simulation.getCooja().restorePortablePath(new File(element.getText()));
        appendCheckBox.setSelected(true);
        if (!appendStreamFile.exists()) {
          try {
            appendStreamFile.createNewFile();
          } catch (IOException e) {
          }
        }
      }
    }

    return true;
  }

  public String getFilter() {
    return filterTextField.getText();
  }

  public void setFilter(String str) {
    filterTextField.setText(str);
    resetFiltered();

    try {
    	final RowFilter<Object,Integer> regexp;
      if (str != null && !str.isEmpty()) {
      	regexp = RowFilter.regexFilter(str, COLUMN_FROM, COLUMN_DATA, COLUMN_CONCAT);
      } else {
      	regexp = null;
      }
    	RowFilter<Object, Integer> wrapped = new RowFilter<>() {
        @Override
        public boolean include(RowFilter.Entry<?, ? extends Integer> entry) {
          if (regexp != null) {
            boolean pass;
            if (entry.getIdentifier() != null) {
              // entry alredy in logs, so can check is it filetred?
              int row = entry.getIdentifier().intValue();
              LogData log = logs.get(row);
              pass = (log.filtered == FilterState.PASS);
              if (log.filtered == FilterState.NONE) {
                pass = regexp.include(entry);
                log.setFiltered(pass);
              }
            } else {
              pass = regexp.include(entry);
            }
            if (inverseFilter && pass) {
              return false;
            } else if (!inverseFilter && !pass) {
              return false;
            }
          }
          if (hideDebug) {
            return !entry.getStringValue(COLUMN_DATA).startsWith("DEBUG: ");
          }
          return true;
        }
      };
      logFilter.setRowFilter(wrapped);
      filterTextField.setBackground(filterTextFieldBackground);
      filterTextField.setToolTipText(null);
    } catch (PatternSyntaxException e) {
      logFilter.setRowFilter(null);
      filterTextField.setBackground(Color.red);
      filterTextField.setToolTipText("Syntax error in regular expression: " + e.getMessage());
    }
    Cooja.getDesktopPane().repaint();
  }

  public void trySelectTime(final long time) {
    for (int i = 0; i < logs.size(); i++) {
      if (logs.get(i).ev.getTime() < time) {
        continue;
      }
      int view = logTable.convertRowIndexToView(i);
      if (view < 0) {
        continue;
      }
      logTable.scrollRectToVisible(logTable.getCellRect(view, 0, true));
      logTable.setRowSelectionInterval(view, view);
      return;
    }
  }

  private enum FilterState { NONE, PASS, REJECTED }

  private class LogData {
    final LogOutputEvent ev;
    FilterState    filtered;

    LogData(LogOutputEvent ev) {
      this.ev = ev;
      this.filtered = FilterState.NONE;
    }

    String getID() {
      return "ID:" + ev.getMote().getID();
    }

    String getTime() {
      if (formatTimeString) {
        return getFormattedTime(ev.getTime());
      } else {
        return String.valueOf(ev.getTime() / Simulation.MILLISECOND);
      }
    }

    void setFiltered(boolean pass) {
        if (pass)
            filtered = FilterState.PASS;
        else
            filtered = FilterState.REJECTED;
    }
  }
  
  private void resetFiltered() {
      for( LogData x: logs) {
          x.filtered = FilterState.NONE;
      }
  }

  private boolean appendToFile;
  private File appendStreamFile;
  private boolean appendToFileWroteHeader;
  private PrintWriter appendStream;
  public boolean appendToFile(File file, String text) {
    /* Close stream */
    if (file == null) {
      if (appendStream != null) {
        appendStream.close();
        appendStream = null;
      }
      return false;
    }

    /* Open stream */
    if (appendStream == null || file != appendStreamFile) {
      try {
        if (appendStream != null) {
          appendStream.close();
          appendStream = null;
        }
        appendStream = new PrintWriter(Files.newBufferedWriter(file.toPath(), UTF_8, CREATE, APPEND));
        appendStreamFile = file;
        appendToFileWroteHeader = false;
      } catch (Exception ex) {
        logger.error("Append file failed: " + ex.getMessage(), ex);
        return false;
      }
    }

    /* Append to file */
    if (!appendToFileWroteHeader) {
      appendStream.println("-- Log Listener [" + simulation.getTitle() + "]: Started at " +
              (ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)));
      appendToFileWroteHeader = true;
    }
    appendStream.print(text);
    appendStream.flush();
    return true;
  }

  private final Action timeLineAction = new AbstractAction("Timeline") {
    @Override
    public void actionPerformed(ActionEvent e) {
      int view = logTable.getSelectedRow();
      if (view < 0) {
        return;
      }
      int model = logTable.convertRowIndexToModel(view);
      long time = logs.get(model).ev.getTime();
      simulation.getCooja().getPlugins(TimeLine.class).forEach(p -> p.trySelectTime(time));
    }
  };

  private final Action radioLoggerAction = new AbstractAction("Radio Logger") {
    @Override
    public void actionPerformed(ActionEvent e) {
      int view = logTable.getSelectedRow();
      if (view < 0) {
        return;
      }
      int model = logTable.convertRowIndexToModel(view);
      long time = logs.get(model).ev.getTime();
      simulation.getCooja().getPlugins(RadioLogger.class).forEach(p -> p.trySelectTime(time));
    }
  };

  private final Action showInAllAction = new AbstractAction("All") {
    {
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      timeLineAction.actionPerformed(null);
      radioLoggerAction.actionPerformed(null);
    }
  };

  public void clear() {
    int size = logs.size();
    if (size > 0) {
      logs.clear();
      model.fireTableRowsDeleted(0, size - 1);
    }
  }

  @Override
  public String getQuickHelp() {
    return
        "<b>Log Listener</b>" +
        "<p>Listens to log output from all simulated motes. " +
        "Right-click the main area for a popup menu with more options. " +
        "<p>You may filter shown logs by entering regular expressions in the bottom text field. " +
        "Filtering is performed on both the Mote and the Data columns." +
        "<p><b>Filter examples:</b> " +
        "<br><br>Hello<br><i>logs containing the string 'Hello'</i>" +
        "<br><br>^Contiki<br><i>logs starting with 'Contiki'</i>" +
        "<br><br>^[CR]<br><i>logs starting either a C or an R</i>" +
        "<br><br>Hello$<br><i>logs ending with 'Hello'</i>" +
        "<br><br>^ID:[2-5]$<br><i>logs from motes 2 to 5</i>" +
        "<br><br>^ID:[2-5] Contiki<br><i>logs from motes 2 to 5 starting with 'Contiki'</i>";
  }

  /* Experimental feature: let other plugins learn if a log output would be filtered or not */
  public boolean filterWouldAccept(LogOutputEvent ev) {
    RowFilter<? super TableModel, ? super Integer> rowFilter = logFilter.getRowFilter();
    if (rowFilter == null) {
      /* No filter */
      return true;
    }

    final LogData ld = new LogData(ev);
    RowFilter.Entry<? extends TableModel, ? extends Integer> entry = new RowFilter.Entry<>() {
      @Override
      public TableModel getModel() {
        return model;
      }
      @Override
      public int getValueCount() {
        return model.getColumnCount();
      }
      @Override
      public Object getValue(int index) {
        if (index == COLUMN_TIME) {
          return ld.getTime();
        } else if (index == COLUMN_FROM) {
          return ld.getID();
        } else if (index == COLUMN_DATA) {
          return ld.ev.getMessage();
        } else if (index == COLUMN_CONCAT) {
          return ld.getID() + ' ' + ld.ev.getMessage();
        }
        return null;
      }
      @Override
      public Integer getIdentifier() {
        return null;
      }
    };
    boolean show;
    show = rowFilter.include(entry);
    return show;
  }
  public Color getColorOfEntry(LogOutputEvent logEvent) {
    int color = (10+logEvent.getMote().getID())%10;
    return BG_COLORS[color];
  }

  public static String getFormattedTime(long t) {
    long h = (t / LogListener.TIME_HOUR);
    t -= (t / TIME_HOUR)*TIME_HOUR;
    long m = (t / TIME_MINUTE);
    t -= (t / TIME_MINUTE)*TIME_MINUTE;
    long s = (t / TIME_SECOND);
    t -= (t / TIME_SECOND)*TIME_SECOND;
    long ms = t / Simulation.MILLISECOND;
    if (h > 0) {
      return String.format("%d:%02d:%02d.%03d", h,m,s,ms);
    } else {
      return String.format("%02d:%02d.%03d", m,s,ms);
    }
  }

}
