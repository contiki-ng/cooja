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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
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
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.ConvertedRadioPacket;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.dialogs.TableColumnAdjuster;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.analyzers.FragHeadPacketAnalyzer;
import org.contikios.cooja.plugins.analyzers.ICMPv6Analyzer;
import org.contikios.cooja.plugins.analyzers.IEEE802154Analyzer;
import org.contikios.cooja.plugins.analyzers.IPHCPacketAnalyzer;
import org.contikios.cooja.plugins.analyzers.IPv6PacketAnalyzer;
import org.contikios.cooja.plugins.analyzers.PacketAnalyzer;
import org.contikios.cooja.plugins.analyzers.RadioLoggerAnalyzerSuite;
import org.contikios.cooja.util.StringUtils;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Radio logger listens to the simulation radio medium and lists all transmitted
 * data in a table.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Radio messages")
@PluginType(PluginType.PType.SIM_PLUGIN)
public class RadioLogger extends VisPlugin {

  private static final Logger logger = LoggerFactory.getLogger(RadioLogger.class);

  private final static int COLUMN_NO = 0;
  private final static int COLUMN_TIME = 1;
  private final static int COLUMN_FROM = 2;
  private final static int COLUMN_TO = 3;
  private final static int COLUMN_DATA = 4;

  private final JSplitPane splitPane;
  private JTextPane verboseBox;

  private boolean formatTimeString = true;

  private final static String[] COLUMN_NAMES = {
    "No.        ",
    "Time ms",
    "From",
    "To",
    "Data"
  };

  private final Simulation simulation;
  private final JTable dataTable;
  private final TableRowSorter<TableModel> logFilter;
  private final ArrayList<RadioConnectionLog> connections = new ArrayList<>();
  private final RadioMedium radioMedium;
  private final AbstractTableModel model;

  private final HashMap<String, Action> analyzerMap = new HashMap<>();
  private String analyzerName;
  private ArrayList<PacketAnalyzer> analyzers;
  private final IEEE802154Analyzer analyzerWithPcap;
  private File pcapFile;

  private final JTextField searchField = new JTextField(30);

  public RadioLogger(final Simulation simulationToControl, final Cooja gui) {
    super("Radio messages", gui);
    setLayout(new BorderLayout());

    simulation = simulationToControl;
    radioMedium = simulation.getRadioMedium();

    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    JMenu editMenu = new JMenu("Edit");
    JMenu analyzerMenu = new JMenu("Analyzer");
    JMenu payloadMenu = new JMenu("View");

    menuBar.add(fileMenu);
    menuBar.add(editMenu);
    menuBar.add(analyzerMenu);
    menuBar.add(payloadMenu);

    this.setJMenuBar(menuBar);

    ArrayList<PacketAnalyzer> lowpanAnalyzers = new ArrayList<>();
    lowpanAnalyzers.add(new IEEE802154Analyzer(false));
    lowpanAnalyzers.add(new FragHeadPacketAnalyzer());
    lowpanAnalyzers.add(new IPHCPacketAnalyzer());
    lowpanAnalyzers.add(new IPv6PacketAnalyzer());
    lowpanAnalyzers.add(new ICMPv6Analyzer());

    analyzerWithPcap = new IEEE802154Analyzer(true);
    ArrayList<PacketAnalyzer> lowpanAnalyzersPcap = new ArrayList<>();
    lowpanAnalyzersPcap.add(analyzerWithPcap);
    lowpanAnalyzersPcap.add(new FragHeadPacketAnalyzer());
    lowpanAnalyzersPcap.add(new IPHCPacketAnalyzer());
    lowpanAnalyzersPcap.add(new IPv6PacketAnalyzer());
    lowpanAnalyzersPcap.add(new ICMPv6Analyzer());

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
        return connections.size();
      }

      @Override
      public int getColumnCount() {
        return COLUMN_NAMES.length;
      }

      @Override
      public Object getValueAt(int row, int col) {
        if (row < 0 || row >= connections.size()) {
          return "";
        }
        RadioConnectionLog conn = connections.get(row);
        if (col == COLUMN_NO) {
          if (!showDuplicates && conn.hides > 0) {
            return (row + 1) + "+" + conn.hides;
          }
          return String.valueOf(row + 1);
        } else if (col == COLUMN_TIME) {
          if (formatTimeString) {
            return LogListener.getFormattedTime(conn.startTime);
          }
          return Long.toString(conn.startTime / Simulation.MILLISECOND);
        } else if (col == COLUMN_FROM) {
          return String.valueOf(conn.connection.getSource().getMote().getID());
        } else if (col == COLUMN_TO) {
          Radio[] dests = conn.connection.getDestinations();
          if (dests.length == 0) {
            return "-";
          }
          if (dests.length == 1) {
            return String.valueOf(dests[0].getMote().getID());
          }
          if (dests.length == 2) {
            return String.valueOf(dests[0].getMote().getID()) + ',' + dests[1].getMote().getID();
          }
          return "[" + dests.length + " d]";
        } else if (col == COLUMN_DATA) {
          if (conn.data == null) {
            prepareDataString(connections.get(row));
          }
          if (aliases != null) {
            /* Check if alias exists */
            String alias = aliases.getProperty(conn.data);
            if (alias != null) {
              return alias;
            }
          }
          return conn.data;
        }
        return null;
      }

      @Override
      public boolean isCellEditable(int row, int col) {
        if (col == COLUMN_FROM) {
          /* Highlight source */
          gui.signalMoteHighlight(connections.get(row).connection.getSource().getMote());
          return false;
        }

        if (col == COLUMN_TO) {
          /* Highlight all destinations */
          Radio[] dests = connections.get(row).connection.getDestinations();
          for (Radio dest: dests) {
            gui.signalMoteHighlight(dest.getMote());
          }
          return false;
        }
        return false;
      }

      @Override
      public Class<?> getColumnClass(int c) {
        return getValueAt(0, c).getClass();
      }
    };

    dataTable = new JTable(model) {
      @Override
      public String getToolTipText(MouseEvent e) {
        java.awt.Point p = e.getPoint();
        int rowIndex = rowAtPoint(p);
        if (rowIndex < 0) {
          return super.getToolTipText(e);
        }
        int modelRowIndex = convertRowIndexToModel(rowIndex);
        int colIndex = columnAtPoint(p);
        int modelColumnIndex = convertColumnIndexToModel(colIndex);
        if (modelRowIndex < 0 || modelColumnIndex < 0) {
          return super.getToolTipText(e);
        }

        /* TODO This entry may represent several hidden connections */
        RadioConnectionLog conn = connections.get(modelRowIndex);
        if (modelColumnIndex == COLUMN_TIME) {
          return "<html>"
                  + "Start time (us): " + conn.startTime
                  + "<br>"
                  + "End time (us): " + conn.endTime
                  + "<br><br>"
                  + "Duration (us): " + (conn.endTime - conn.startTime)
                  + "</html>";
        } else if (modelColumnIndex == COLUMN_FROM) {
          return conn.connection.getSource().getMote().toString();
        } else if (modelColumnIndex == COLUMN_TO) {
          Radio[] dests = conn.connection.getDestinations();
          if (dests.length == 0) {
            return "No destinations";
          }
          StringBuilder tip = new StringBuilder();
          tip.append("<html>");
          if (dests.length == 1) {
            tip.append("One destination:<br>");
          } else {
            tip.append(dests.length).append(" destinations:<br>");
          }
          for (Radio radio: dests) {
            tip.append(radio.getMote()).append("<br>");
          }
          tip.append("</html>");
          return tip.toString();
        } else if (modelColumnIndex == COLUMN_DATA) {
          if (conn.tooltip == null) {
            prepareTooltipString(conn);
          }
          return conn.tooltip;
        }
        return super.getToolTipText(e);
      }
    };

    /* Toggle time format */
    dataTable.getTableHeader().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int colIndex = dataTable.columnAtPoint(e.getPoint());
        int columnIndex = dataTable.convertColumnIndexToModel(colIndex);
        if (columnIndex != COLUMN_TIME) {
          return;
        }
        formatTimeString = !formatTimeString;
        dataTable.getColumnModel().getColumn(COLUMN_TIME).setHeaderValue(
                dataTable.getModel().getColumnName(COLUMN_TIME));
        repaint();
      }
    });

    dataTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          showInAllAction.actionPerformed(null);
        } else if (e.getKeyCode() == KeyEvent.VK_F
                && (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
          searchField.setVisible(true);
          searchField.requestFocus();
          searchField.selectAll();
          revalidate();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          searchField.setVisible(false);
          dataTable.requestFocus();
          revalidate();
        }
      }
    });

    logFilter = new TableRowSorter<>(model);
    for (int i = 0, n = model.getColumnCount(); i < n; i++) {
      logFilter.setSortable(i, false);
    }
    dataTable.setRowSorter(logFilter);

    dataTable.getSelectionModel().addListSelectionListener(e -> {
      int row = dataTable.getSelectedRow();
      if (row < 0) {
        return;
      }
      int modelRowIndex = dataTable.convertRowIndexToModel(row);
      if (modelRowIndex >= 0) {
        RadioConnectionLog conn = connections.get(modelRowIndex);
        if (conn.tooltip == null) {
          prepareTooltipString(conn);
        }
        verboseBox.setText(conn.tooltip);
        verboseBox.setCaretPosition(0);
      }
    });
    // Set data column width greedy
    dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

    dataTable.setFont(new Font("Monospaced", Font.PLAIN, 12));

    Action copyAllAction = new AbstractAction("Copy all") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        StringBuilder sb = new StringBuilder();
        for (RadioConnectionLog connection : connections) {
          sb.append(connection.toString()).append("\n");
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
      }
    };
    editMenu.add(new JMenuItem(copyAllAction));
    Action copyAction = new AbstractAction("Copy selected") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        int[] selectedRows = dataTable.getSelectedRows();

        StringBuilder sb = new StringBuilder();
        for (int i : selectedRows) {
          int iModel = dataTable.convertRowIndexToModel(i);
          sb.append(connections.get(iModel).toString()).append("\n");
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
      }
    };
    editMenu.add(new JMenuItem(copyAction));
    editMenu.add(new JSeparator());
    Action clearAction = new AbstractAction("Clear") {
      @Override
      public void actionPerformed(ActionEvent e) {
        int size = connections.size();
        if (size > 0) {
          connections.clear();
          model.fireTableRowsDeleted(0, size - 1);
          setTitle("Radio messages: showing " + dataTable.getRowCount() + "/" + connections.size() + " packets");
        }
      }
    };
    editMenu.add(new JMenuItem(clearAction));

    Action aliasAction = new AbstractAction("Payload alias...") {
      @Override
      public void actionPerformed(ActionEvent e) {
        int selectedRow = dataTable.getSelectedRow();
        if (selectedRow < 0) return;
        selectedRow = dataTable.convertRowIndexToModel(selectedRow);
        if (selectedRow < 0) return;

        String current = "";
        if (aliases != null && aliases.getProperty(connections.get(selectedRow).data) != null) {
          current = aliases.getProperty(connections.get(selectedRow).data);
        }

        String alias = (String) JOptionPane.showInputDialog(
                Cooja.getTopParentContainer(),
                "Enter alias for all packets with identical payload.\n"
                        + "An empty string removes the current alias.\n\n"
                        + connections.get(selectedRow).data + "\n",
                "Create packet payload alias",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                current);
        if (alias == null) {
          // Cancelled
          return;
        }

        // Should be null if empty
        if (aliases == null) {
          aliases = new Properties();
        }

        // Remove current alias
        if (alias.isEmpty()) {
          aliases.remove(connections.get(selectedRow).data);

          // Should be null if empty
          if (aliases.isEmpty()) {
            aliases = null;
          }
          repaint();
          return;
        }

        // (Re)define alias
        aliases.put(connections.get(selectedRow).data, alias);
        repaint();
      }
    };
    payloadMenu.add(new JMenuItem(aliasAction));
    payloadMenu.add(new JCheckBoxMenuItem(showDuplicatesAction) {
      @Override
      public boolean isSelected() {
        return showDuplicates;
      }
    });
    payloadMenu.add(new JCheckBoxMenuItem(hideNoDestinationAction) {
      @Override
      public boolean isSelected() {
        return hideNoDestinationPackets;
      }
    });

    Action saveAction = new AbstractAction("Save to file...") {
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
          logger.error("No write access to file: " + saveFile);
          return;
        }

        try {
          PrintWriter outStream = new PrintWriter(Files.newBufferedWriter(saveFile.toPath(), UTF_8));
          for (RadioConnectionLog connection : connections) {
            outStream.print(connection.toString() + "\n");
          }
          outStream.close();
        } catch (Exception ex) {
          logger.error("Could not write to file: " + saveFile);
        }
      }
    };
    fileMenu.add(new JMenuItem(saveAction));

    JPopupMenu popupMenu = new JPopupMenu();

    JMenu focusMenu = new JMenu("Show in");
    focusMenu.add(new JMenuItem(showInAllAction));
    focusMenu.addSeparator();
    focusMenu.add(new JMenuItem(timeLineAction));
    focusMenu.add(new JMenuItem(logListenerAction));
    popupMenu.add(focusMenu);

    //a group of radio button menu items
    ButtonGroup group = new ButtonGroup();
    JRadioButtonMenuItem rbMenuItem = new JRadioButtonMenuItem(
            createAnalyzerAction("No Analyzer", "none", null, true));
    group.add(rbMenuItem);
    analyzerMenu.add(rbMenuItem);

    rbMenuItem = new JRadioButtonMenuItem(createAnalyzerAction(
            "6LoWPAN Analyzer", "6lowpan", lowpanAnalyzers, false));
    group.add(rbMenuItem);
    analyzerMenu.add(rbMenuItem);

    rbMenuItem = new JRadioButtonMenuItem(createAnalyzerAction(
            "6LoWPAN Analyzer with PCAP", "6lowpan-pcap", lowpanAnalyzersPcap, false));
    group.add(rbMenuItem);
    analyzerMenu.add(rbMenuItem);

    /* Load additional analyzers specified by projects (cooja.config) */
    String[] projectAnalyzerSuites
            = gui.getProjectConfig().getStringArrayValue(RadioLogger.class, "ANALYZERS");
    if (projectAnalyzerSuites != null) {
      for (String suiteName: projectAnalyzerSuites) {
        if (suiteName == null || suiteName.trim().isEmpty()) {
          continue;
        }
        Class<? extends RadioLoggerAnalyzerSuite> suiteClass
                = gui.tryLoadClass(RadioLogger.this, RadioLoggerAnalyzerSuite.class, suiteName);
        try {
          RadioLoggerAnalyzerSuite suite = suiteClass.getDeclaredConstructor().newInstance();
          ArrayList<PacketAnalyzer> suiteAnalyzers = suite.getAnalyzers();
          rbMenuItem = new JRadioButtonMenuItem(createAnalyzerAction(
                  suite.getDescription(), suiteName, suiteAnalyzers, false));
          group.add(rbMenuItem);
          analyzerMenu.add(rbMenuItem);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e1) {
          logger.warn("Failed to load analyzer suite '" + suiteName + "': " + e1.getMessage());
        }
      }
    }

    dataTable.setComponentPopupMenu(popupMenu);
    dataTable.setFillsViewportHeight(true);

    verboseBox = new JTextPane();
    verboseBox.setContentType("text/html");
    verboseBox.setEditable(false);
    verboseBox.setComponentPopupMenu(popupMenu);

    /* Search text field */
    searchField.setVisible(false);
    searchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          searchSelectNext(
                  searchField.getText(),
                  (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0);
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          searchField.setVisible(false);
          dataTable.requestFocus();
          revalidate();
        }
      }
    });

    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                               new JScrollPane(dataTable), new JScrollPane(verboseBox));
    splitPane.setOneTouchExpandable(true);
    splitPane.setDividerLocation(150);
    add(BorderLayout.NORTH, searchField);
    add(BorderLayout.CENTER, splitPane);

    TableColumnAdjuster adjuster = new TableColumnAdjuster(dataTable);
    adjuster.setDynamicAdjustment(true);
    adjuster.packColumns();

    radioMedium.getRadioTransmissionTriggers().addTrigger(this, (obs, obj) -> {
      RadioConnection conn = radioMedium.getLastConnection();
      if (conn == null) {
        return;
      }
      final RadioConnectionLog loggedConn = new RadioConnectionLog();
      loggedConn.packet = conn.getSource().getLastPacketTransmitted();
      if (loggedConn.packet == null)
        return;
      loggedConn.startTime = conn.getStartTime();
      loggedConn.endTime = simulation.getSimulationTime();
      loggedConn.connection = conn;
      EventQueue.invokeLater(() -> {
        int lastSize = connections.size();
        // Check if the last row is visible.
        boolean isVisible = false;
        int rowCount = dataTable.getRowCount();
        if (rowCount > 0) {
          Rectangle lastRow = dataTable.getCellRect(rowCount - 1, 0, true);
          Rectangle visible = dataTable.getVisibleRect();
          isVisible = visible.y <= lastRow.y && visible.y + visible.height >= lastRow.y + lastRow.height;
        }
        connections.add(loggedConn);
        if (connections.size() > lastSize) {
          model.fireTableRowsInserted(lastSize, connections.size() - 1);
        }
        if (isVisible) {
          dataTable.scrollRectToVisible(dataTable.getCellRect(dataTable.getRowCount() - 1, 0, true));
        }
        setTitle("Radio messages: showing " + dataTable.getRowCount() + "/" + connections.size() + " packets");
      });
    });

    setSize(500, 300);
  }

  @Override
  public void startPlugin() {
    super.startPlugin();
    rebuildAllEntries();
  }

  private void searchSelectNext(String text, boolean reverse) {
    if (text.isEmpty()) {
      return;
    }
    int row = dataTable.getSelectedRow();
    if (row < 0) {
      row = 0;
    }

    if (!reverse) {
      row++;
    } else {
      row--;
    }

    int rows = dataTable.getModel().getRowCount();
    for (int i = 0; i < rows; i++) {
      int r;
      if (!reverse) {
        r = (row + i + rows) % rows;
      } else {
        r = (row - i + rows) % rows;
      }
      String val = (String) dataTable.getModel().getValueAt(r, COLUMN_DATA);
      if (!val.contains(text)) {
        continue;
      }
      dataTable.setRowSelectionInterval(r, r);
      dataTable.scrollRectToVisible(dataTable.getCellRect(r, COLUMN_DATA, true));
      searchField.setBackground(Color.WHITE);
      return;
    }
    searchField.setBackground(Color.RED);
  }

  /**
   * Selects a logged radio packet close to the given time.
   *
   * @param time Start time
   */
  public void trySelectTime(final long time) {
    if (dataTable.getRowCount() == 0) {
      return;
    }
    for (int ai = 0; ai < model.getRowCount(); ai++) {
      int index = dataTable.convertRowIndexToModel(ai);
      if (connections.get(index).endTime < time) {
        continue;
      }
      dataTable.scrollRectToVisible(dataTable.getCellRect(ai, 0, true));
      dataTable.setRowSelectionInterval(ai, ai);
      return;
    }
    dataTable.scrollRectToVisible(dataTable.getCellRect(dataTable.getRowCount() - 1, 0, true));
    dataTable.setRowSelectionInterval(dataTable.getRowCount() - 1, dataTable.getRowCount() - 1);
  }

  private void applyFilter() {
    for (RadioConnectionLog conn: connections) {
      conn.data = null;
      conn.tooltip = null;
      conn.hides = 0;
      conn.hiddenBy = null;
    }

    try {
      logFilter.setRowFilter(null);
      RowFilter<Object, Object> filter = new RowFilter<>() {
        @Override
        public boolean include(RowFilter.Entry<?, ?> entry) {
          int row = (Integer) entry.getIdentifier();
          RadioConnectionLog current = connections.get(row);
          byte[] currentData = current.packet.getPacketData();

          if (!showDuplicates && row > 0) {
            RadioConnectionLog previous = connections.get(row - 1);
            byte[] previousData = previous.packet.getPacketData();
            if (!showDuplicates
                    && Arrays.equals(previousData, currentData)
                    && previous.connection.getSource() == current.connection.getSource()
                    && Arrays.equals(previous.connection.getAllDestinations(), current.connection.getAllDestinations())) {
              if (connections.get(row - 1).hiddenBy == null) {
                connections.get(row - 1).hides++;
                connections.get(row).hiddenBy = connections.get(row - 1);
              } else {
                connections.get(row - 1).hiddenBy.hides++;
                connections.get(row).hiddenBy = connections.get(row - 1).hiddenBy;
              }
              return false;
            }
          }

          if (hideNoDestinationPackets) {
            return current.connection.getDestinations().length != 0;
          }

          return true;
        }
      };
      logFilter.setRowFilter(filter);
    } catch (PatternSyntaxException e) {
      logFilter.setRowFilter(null);
      logger.warn("Error when setting table filter: " + e.getMessage());
    }
  }

  private void prepareDataString(RadioConnectionLog conn) {
    byte[] data;
    if (conn.packet == null) {
      data = null;
    } else if (conn.packet instanceof ConvertedRadioPacket) {
      data = ((ConvertedRadioPacket) conn.packet).getOriginalPacketData();
    } else {
      data = conn.packet.getPacketData();
    }
    if (data == null) {
      conn.data = "[unknown data]";
      return;
    }

    StringBuilder brief = new StringBuilder();
    StringBuilder verbose = new StringBuilder();

    /* default analyzer */
    PacketAnalyzer.Packet packet = new PacketAnalyzer.Packet(data, PacketAnalyzer.MAC_LEVEL,
                                                             simulation.convertSimTimeToActualTime(conn.startTime));
    if (analyzePacket(packet, brief, verbose)) {
      if (packet.hasMoreData()) {
        byte[] payload = packet.getPayload();
        brief.append(StringUtils.toHex(payload, 4));
        if (!verbose.isEmpty()) {
          verbose.append("<p>");
        }
        verbose.append("<b>Payload (")
                .append(payload.length).append(" bytes)</b><br><pre>")
                .append(StringUtils.hexDump(payload))
                .append("</pre>");
      }
      conn.data = (data.length < 100 ? (data.length < 10 ? "  " : " ") : "")
              + data.length + ": " + brief;
      if (!verbose.isEmpty()) {
        conn.tooltip = verbose.toString();
      }
    } else {
      conn.data = data.length + ": 0x" + StringUtils.toHex(data, 4);
    }
  }

  private boolean analyzePacket(PacketAnalyzer.Packet packet, StringBuilder brief, StringBuilder verbose) {
    if (analyzers == null) return false;
    try {
      boolean analyze = true;
      while (analyze) {
        analyze = false;
        for (PacketAnalyzer analyzer : analyzers) {
          if (analyzer.matchPacket(packet)) {
            int res = analyzer.analyzePacket(packet, brief, verbose);
            if (packet.hasMoreData() && !brief.isEmpty()) {
              brief.append('|');
              verbose.append("<br>");
            }
            if (res != PacketAnalyzer.ANALYSIS_OK_CONTINUE) {
              /* this was the final or the analysis failed - no analyzable payload possible here... */
              return !brief.isEmpty();
            }
            /* continue another round if more bytes left */
            analyze = packet.hasMoreData();
            break;
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Error when analyzing packet: " + e.getMessage(), e);
      return false;
    }
    return !brief.isEmpty();
  }

  private void prepareTooltipString(RadioConnectionLog conn) {
    RadioPacket packet = conn.packet;
    if (packet == null) {
      conn.tooltip = "";
      return;
    }

    if (packet instanceof ConvertedRadioPacket && packet.getPacketData().length > 0) {
      byte[] original = ((ConvertedRadioPacket) packet).getOriginalPacketData();
      byte[] converted = packet.getPacketData();
      conn.tooltip = "<html><font face=\"Monospaced\">"
              + "<b>Packet data (" + original.length + " bytes)</b><br>"
              + "<pre>" + StringUtils.hexDump(original) + "</pre>"
              + "</font><font face=\"Monospaced\">"
              + "<b>Cross-level packet data (" + converted.length + " bytes)</b><br>"
              + "<pre>" + StringUtils.hexDump(converted) + "</pre>"
              + "</font></html>";
    } else if (packet instanceof ConvertedRadioPacket) {
      byte[] original = ((ConvertedRadioPacket) packet).getOriginalPacketData();
      conn.tooltip = "<html><font face=\"Monospaced\">"
              + "<b>Packet data (" + original.length + " bytes)</b><br>"
              + "<pre>" + StringUtils.hexDump(original) + "</pre>"
              + "</font><font face=\"Monospaced\">"
              + "<b>No cross-level conversion available</b><br>"
              + "</font></html>";
    } else {
      byte[] data = packet.getPacketData();
      conn.tooltip = "<html><font face=\"Monospaced\">"
              + "<b>Packet data (" + data.length + " bytes)</b><br>"
              + "<pre>" + StringUtils.hexDump(data) + "</pre>"
              + "</font></html>";
    }
  }

  @Override
  public void closePlugin() {
    radioMedium.getRadioTransmissionTriggers().deleteTriggers(this);
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    Element element = new Element("split");
    element.addContent(Integer.toString(splitPane.getDividerLocation()));
    config.add(element);

    if (formatTimeString) {
      element = new Element("formatted_time");
      config.add(element);
    }

    if (showDuplicates) {
      element = new Element("showdups");
      element.addContent(Boolean.toString(showDuplicates));
      config.add(element);
    }

    if (hideNoDestinationPackets) {
      element = new Element("hidenodests");
      element.addContent(Boolean.toString(hideNoDestinationPackets));
      config.add(element);
    }

    if (analyzerName != null && analyzers != null) {
      element = new Element("analyzers");
      element.setAttribute("name", analyzerName);
      config.add(element);
    }

    if (aliases != null) {
      for (Map.Entry<Object, Object> entry : aliases.entrySet()) {
        element = new Element("alias");
        element.setAttribute("payload", (String) entry.getKey());
        element.setAttribute("alias", (String) entry.getValue());
        config.add(element);
      }
    }

    if (pcapFile != null) {
      element = new Element("pcap_file");
      // FIXME: "file" is unused.
      File file = simulation.getCooja().createPortablePath(pcapFile);
      element.setText(pcapFile.getPath().replaceAll("\\\\", "/"));
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      String name = element.getName();
      if ("alias".equals(name)) {
        String payload = element.getAttributeValue("payload");
        String alias = element.getAttributeValue("alias");
        if (aliases == null) {
          aliases = new Properties();
        }
        aliases.put(payload, alias);
      } else if ("split".equals(name)) {
        splitPane.setDividerLocation(Integer.parseInt(element.getText()));
      } else if ("formatted_time".equals(name)) {
        formatTimeString = true;
      } else if ("showdups".equals(name)) {
        showDuplicates = Boolean.parseBoolean(element.getText());
      } else if ("hidenodests".equals(name)) {
        hideNoDestinationPackets = Boolean.parseBoolean(element.getText());
      } else if ("analyzers".equals(name)) {
        String analyzerName = element.getAttributeValue("name");
        final Action action;
        if (analyzerName != null && ((action = analyzerMap.get(analyzerName)) != null)) {
          action.putValue(Action.SELECTED_KEY, Boolean.TRUE);
          action.actionPerformed(null);
        }
      } else if (name.equals("pcap_file")) {
        pcapFile = simulation.getCooja().restorePortablePath(new File(element.getText()));
        analyzerWithPcap.setPcapFile(pcapFile);
      }
    }
    return true;
  }

  private class RadioConnectionLog {

    long startTime;
    long endTime;
    RadioConnection connection;
    RadioPacket packet;

    RadioConnectionLog hiddenBy;
    int hides;

    String data;
    String tooltip;

    @Override
    public String toString() {
      if (data == null) {
        RadioLogger.this.prepareDataString(this);
      }
      return startTime / Simulation.MILLISECOND + "\t"
              + connection.getSource().getMote().getID() + "\t"
              + getDestString(this) + "\t"
              + data;
    }
  }

  private static String getDestString(RadioConnectionLog c) {
    Radio[] dests = c.connection.getDestinations();
    if (dests.length == 0) {
      return "-";
    }
    if (dests.length == 1) {
      return String.valueOf(dests[0].getMote().getID());
    }
    StringBuilder sb = new StringBuilder();
    for (Radio dest: dests) {
      sb.append(dest.getMote().getID()).append(',');
    }
    sb.setLength(sb.length() - 1);
    return sb.toString();
  }

  private void rebuildAllEntries() {
    applyFilter();

    if (!connections.isEmpty()) {
      model.fireTableRowsUpdated(0, connections.size() - 1);
    }
    verboseBox.setText("");

    setTitle("Radio messages: showing " + dataTable.getRowCount() + "/" + connections.size() + " packets");
    Cooja.getDesktopPane().repaint();
  }

  private Action createAnalyzerAction(String name, final String actionName,
                                      final ArrayList<PacketAnalyzer> analyzerList, boolean selected) {
    Action action = new AbstractAction(name) {
      @Override
      public void actionPerformed(ActionEvent event) {
        if (analyzers != analyzerList) {
          analyzers = analyzerList;
          analyzerName = actionName;
          rebuildAllEntries();
        }
      }
    };
    action.putValue(Action.SELECTED_KEY, selected ? Boolean.TRUE : Boolean.FALSE);
    analyzerMap.put(actionName, action);
    return action;
  }

  private final Action timeLineAction = new AbstractAction("Timeline") {
    @Override
    public void actionPerformed(ActionEvent e) {
      int selectedRow = dataTable.getSelectedRow();
      if (selectedRow < 0) return;
      selectedRow = dataTable.convertRowIndexToModel(selectedRow);
      if (selectedRow < 0) return;

      long time = connections.get(selectedRow).startTime;
      simulation.getCooja().getPlugins(TimeLine.class).forEach(p -> p.trySelectTime(time));
    }
  };

  private final Action logListenerAction = new AbstractAction("Mote output") {
    @Override
    public void actionPerformed(ActionEvent e) {
      int selectedRow = dataTable.getSelectedRow();
      if (selectedRow < 0) return;
      selectedRow = dataTable.convertRowIndexToModel(selectedRow);
      if (selectedRow < 0) return;

      long time = connections.get(selectedRow).startTime;

      simulation.getCooja().getPlugins(LogListener.class).forEach(p -> p.trySelectTime(time));
    }
  };

  private final Action showInAllAction = new AbstractAction("Timeline and mote output") {

    {
      putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      timeLineAction.actionPerformed(null);
      logListenerAction.actionPerformed(null);
    }
  };

  private Properties aliases;

  private boolean showDuplicates;
  private final AbstractAction showDuplicatesAction = new AbstractAction("Show duplicates") {
    @Override
    public void actionPerformed(ActionEvent e) {
      showDuplicates = !showDuplicates;
      rebuildAllEntries();
    }
  };

  private boolean hideNoDestinationPackets;
  private final AbstractAction hideNoDestinationAction = new AbstractAction("Hide airshots") {
    @Override
    public void actionPerformed(ActionEvent e) {
      hideNoDestinationPackets = !hideNoDestinationPackets;
      rebuildAllEntries();
    }
  };
}
