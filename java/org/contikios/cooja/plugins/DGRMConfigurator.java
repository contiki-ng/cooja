/*
 * Copyright (c) 2010, Swedish Institute of Computer Science.
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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;


import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.cooja.radiomediums.DGRMDestinationRadio;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;
import org.contikios.cooja.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple user interface for configuring edges for the Directed Graph
 * Radio Medium (DGRM).
 *
 * @see DirectedGraphMedium
 * @author Fredrik Osterlind
 */
@ClassDescription("DGRM Links")
@PluginType(PluginType.PType.SIM_PLUGIN)
@SupportedArguments(radioMediums = {DirectedGraphMedium.class})
public class DGRMConfigurator extends VisPlugin {
	private static final Logger logger = LoggerFactory.getLogger(DGRMConfigurator.class);

  private final static int IDX_SRC = 0;
  private final static int IDX_DST = 1;
  private final static int IDX_RATIO = 2;
  private final static int IDX_SIGNAL = 3;
  private final static int IDX_LQI = 4;
  private final static int IDX_DELAY = 5;

  private final static String[] COLUMN_NAMES = {
    "Source", "Destination", "RX Ratio", "RSSI","LQI", "Delay"
  };

  private final Cooja gui;
  private final DirectedGraphMedium radioMedium;
  private final JTable graphTable;
  private final JComboBox<Number> combo = new JComboBox<>();
	private final JButton removeButton;

  public DGRMConfigurator(Simulation sim, Cooja gui) {
    super("DGRM Configurator", gui);
    this.gui = gui;
    radioMedium = (DirectedGraphMedium) sim.getRadioMedium();

    /* Listen for graph updates */
    radioMedium.getRadioTransmissionTriggers().addTrigger(this, (event, obj) -> model.fireTableDataChanged());

    /* Represent directed graph by table */
    graphTable = new JTable(model) {
      @Override
      public TableCellEditor getCellEditor(int row, int column) {
				combo.removeAllItems();
        if (column == IDX_RATIO) {
          for (double d=1.0; d >= 0.0; d -= 0.1) {
            combo.addItem(d);
          }
        } else if (column == IDX_SIGNAL) {
          for (double d=AbstractRadioMedium.SS_STRONG; d >= AbstractRadioMedium.SS_WEAK; d -= 1) {
            combo.addItem((int) d);
          }
        } else if (column == IDX_LQI) {
            for (int d = 110; d > 50; d -= 5) {
              combo.addItem(d);
            }
        } else if (column == IDX_DELAY) {
          for (double d=0; d <= 5; d++) {
            combo.addItem(d);
          }
        }
        return super.getCellEditor(row, column);
      }
    };
    graphTable.setFillsViewportHeight(true);
    combo.setEditable(true);

    graphTable.getColumnModel().getColumn(IDX_RATIO).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public void setValue(Object value) {
        if (!(value instanceof Double)) {
          setText(value.toString());
          return;
        }
        setText(String.format("%1.1f%%", 100* (Double) value));
      }
    });
    graphTable.getColumnModel().getColumn(IDX_SIGNAL).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public void setValue(Object value) {
        if (value instanceof Number number) {
          setText(String.format("%1.1f dBm", number.doubleValue()));
          return;
        }
        setText(value.toString());
      }
    });
    graphTable.getColumnModel().getColumn(IDX_LQI).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public void setValue(Object value) {
	    if (!(value instanceof Long)) {
	      setText(value.toString());
	      return;
	    }
        setText(String.valueOf(value));
		}
    });
    graphTable.getColumnModel().getColumn(IDX_DELAY).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public void setValue(Object value) {
        if (!(value instanceof Long)) {
          setText(value.toString());
          return;
        }
        setText(value + " ms");
      }
    });
    graphTable.getColumnModel().getColumn(IDX_RATIO).setCellEditor(new DefaultCellEditor(combo));
    graphTable.getColumnModel().getColumn(IDX_SIGNAL).setCellEditor(new DefaultCellEditor(combo));
    graphTable.getColumnModel().getColumn(IDX_LQI).setCellEditor(new DefaultCellEditor(combo));
    graphTable.getColumnModel().getColumn(IDX_DELAY).setCellEditor(new DefaultCellEditor(combo));

    graphTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    graphTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    JPanel southPanel = new JPanel(new GridLayout(1, 3));
    JButton button = new JButton("Add");
    button.addActionListener(e -> doAddLink());
    southPanel.add(button);
    button = new JButton("Remove");
    button.addActionListener(e -> doRemoveSelectedLink());
    removeButton = button;
    removeButton.setEnabled(false);
    southPanel.add(button);
    button = new JButton("Import");
    button.addActionListener(e -> doImportFromFile());
    southPanel.add(button);

    getContentPane().setLayout(new BorderLayout());
    add(BorderLayout.CENTER, new JScrollPane(graphTable));
    add(BorderLayout.SOUTH, southPanel);

    graphTable.getSelectionModel().addListSelectionListener(e -> {
      ListSelectionModel lsm = (ListSelectionModel)e.getSource();
      if (e.getValueIsAdjusting()) {
        return;
      }
      removeButton.setEnabled(!lsm.isSelectionEmpty());
    });

    model.fireTableDataChanged();
    setSize(400, 300);
  }

  private void doAddLink() {
    var source = new JComboBox<Mote>();
    var dest = new JComboBox<Mote>();
    for (Mote m: gui.getSimulation().getMotes()) {
      source.addItem(m);
      dest.addItem(m);
    }

    /* User input */
    Object[] description = {
        COLUMN_NAMES[0],
        source,
        COLUMN_NAMES[1],
        dest
    };
    JOptionPane optionPane = new JOptionPane();
    optionPane.setMessage(description);
    optionPane.setMessageType(JOptionPane.QUESTION_MESSAGE);
    String[] options = {"Cancel", "Add"};
    optionPane.setOptions(options);
    optionPane.setInitialValue(options[1]);
    JDialog dialog = optionPane.createDialog(this, title);
    dialog.setTitle("Add graph edge");
    dialog.setVisible(true);
    if (optionPane.getValue() == null || !optionPane.getValue().equals("Add")) {
      return;
    }

    /* Register new edge with radio medium */
    DirectedGraphMedium.Edge newEdge = new DirectedGraphMedium.Edge(
    		((Mote) source.getSelectedItem()).getInterfaces().getRadio(),
    		new DGRMDestinationRadio(
    				((Mote) dest.getSelectedItem()).getInterfaces().getRadio()
    		)
    );
    radioMedium.addEdge(newEdge);
    model.fireTableDataChanged();
  }

  private void doRemoveLink(DirectedGraphMedium.Edge edge) {
    radioMedium.removeEdge(edge);
    model.fireTableDataChanged();
  }
	private void doRemoveSelectedLink() {
    int firstIndex = graphTable.getSelectedRow();
		if (firstIndex < 0) {
			return;
		}

		doRemoveLink(radioMedium.getEdges()[firstIndex]);
	}
	private void doImportFromFile() {
		/* Delete existing edges */
    if (radioMedium.getEdges().length > 0) {
      String[] options = { "Remove", "Cancel" };
      int n = JOptionPane.showOptionDialog(
          Cooja.getTopParentContainer(),
          "Importing edges will remove all your existing edges.",
          "Clear edge table?", JOptionPane.YES_NO_OPTION,
          JOptionPane.WARNING_MESSAGE, null, options, options[0]);
      if (n != JOptionPane.YES_OPTION) {
        return;
      }
      for (DirectedGraphMedium.Edge e: radioMedium.getEdges()) {
      	radioMedium.removeEdge(e);
      }
    }

		/* Select file to import edges from */
    JFileChooser fc = new JFileChooser();
    File suggest = new File(Cooja.getExternalToolsSetting("DGRM_IMPORT_LINKS_FILE", "cooja_dgrm_links.dat"));
    fc.setSelectedFile(suggest);
    int returnVal = fc.showOpenDialog(Cooja.getTopParentContainer());
    if (returnVal != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = fc.getSelectedFile();
    if (file == null || !file.exists() || !file.canRead()) {
      logger.error("No read access to file: " + file);
      return;
    }
    Cooja.setExternalToolsSetting("DGRM_IMPORT_LINKS_FILE", file.getPath());

    /* Parse and import edges */
    try {
      var edges = parseDGRMLinksFile(file, gui.getSimulation());
      Arrays.sort(edges, Comparator.comparingInt(o -> o.source.getMote().getID()));
      for (var e : edges) {
        radioMedium.addEdge(e);
      }
      logger.info("Imported " + edges.length + " DGRM edges");
    } catch (Exception e) {
      Cooja.showErrorDialog("Error when importing DGRM links from " + file.getName(), e, false);
    }
	}

	static final int INDEX_SRC = 0;
	static final int INDEX_DST = 1;
	static final int INDEX_PRR = 2;
	static final int INDEX_PRR_CI = 3;
	static final int INDEX_NUM_TX = 4;
	static final int INDEX_NUM_RX = 5;
	static final int INDEX_RSSI_MEDIAN = 6;
	static final int INDEX_RSSI_MIN = 7;
	static final int INDEX_RSSI_MAX = 8;
	private static DirectedGraphMedium.Edge[] parseDGRMLinksFile(File file, Simulation simulation) {
		String fileContents = StringUtils.loadFromFile(file);
		ArrayList<DirectedGraphMedium.Edge> edges = new ArrayList<>();

		/* format: # [src] [dst] [prr] [prr_ci] [num_tx] [num_rx] [rssi] [rssi_min] [rssi_max] */
		for (String l: fileContents.split("\n")) {
			l = l.trim();
			if (l.startsWith("#")) {
				continue;
			}

			Mote m;
			String[] arr = l.split(" ");
			int source = Integer.parseInt(arr[INDEX_SRC]);
			m = simulation.getMoteWithID(source);
			if (m == null) {
				throw new RuntimeException("No simulation mote with ID " + source);
			}
			Radio sourceRadio = m.getInterfaces().getRadio();
			int dst = Integer.parseInt(arr[INDEX_DST]);
			m = simulation.getMoteWithID(dst);
			if (m == null) {
				throw new RuntimeException("No simulation mote with ID " + dst);
			}
			DGRMDestinationRadio destRadio = new DGRMDestinationRadio(m.getInterfaces().getRadio());
			double prr = Double.parseDouble(arr[INDEX_PRR]);
			/*double prrConfidence = Double.parseDouble(arr[INDEX_PRR_CI]);*/
			/*int numTX <- INDEX_NUM_TX;*/
			/*int numRX <- INDEX_NUM_RX;*/
			double rssi = Double.parseDouble(arr[INDEX_RSSI_MEDIAN]);
			/*int rssiMin <- INDEX_RSSI_MIN;*/
			/*int rssiMax <- INDEX_RSSI_MAX;*/

			DirectedGraphMedium.Edge edge = new DirectedGraphMedium.Edge(sourceRadio, destRadio);
			destRadio.delay = 0;
			destRadio.ratio = prr;
			/*destRadio.prrConfidence = prrConfidence;*/
			destRadio.signal = rssi;
			edges.add(edge);
		}
		return edges.toArray(new DirectedGraphMedium.Edge[0]);
	}

  private final AbstractTableModel model = new AbstractTableModel() {
    @Override
    public String getColumnName(int column) {
      if (column < 0 || column >= COLUMN_NAMES.length) {
        return "";
      }
      return COLUMN_NAMES[column];
    }
    @Override
    public int getRowCount() {
      return radioMedium.getEdges().length;
    }
    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }
    @Override
    public Object getValueAt(int row, int column) {
      if (row < 0 || row >= radioMedium.getEdges().length) {
        return "";
      }
      if (column < 0 || column >= COLUMN_NAMES.length) {
        return "";
      }
      DirectedGraphMedium.Edge edge = radioMedium.getEdges()[row];
      if (column == IDX_SRC) {
        return edge.source.getMote();
      }
      if (column == IDX_DST) {
        return edge.superDest.radio.getMote();
      }
      if (column == IDX_RATIO) {
        return edge.superDest.ratio;
      }
      if (column == IDX_SIGNAL) {
        return edge.superDest.signal;
      }
      if (column == IDX_LQI) {
          return edge.superDest.lqi;
        }
      if (column == IDX_DELAY) {
        return edge.superDest.delay / Simulation.MILLISECOND;
      }
      return "";
    }
    @Override
    public void setValueAt(Object value, int row, int column) {
      if (row < 0 || row >= radioMedium.getEdges().length) {
        return;
      }
      if (column < 0 || column >= COLUMN_NAMES.length) {
        return;
      }

      DirectedGraphMedium.Edge edge = radioMedium.getEdges()[row];
      try {
      	if (column == IDX_RATIO) {
          edge.superDest.ratio = ((Number) value).doubleValue();
      	} else if (column == IDX_SIGNAL) {
          edge.superDest.signal = ((Number) value).doubleValue();
      	} else if (column == IDX_DELAY) {
          edge.superDest.delay =
      			((Number)value).longValue() * Simulation.MILLISECOND;
      	} else if (column == IDX_LQI) {
          edge.superDest.lqi = ((Number) value).intValue();
      	} 
      	else {
          super.setValueAt(value, row, column);
      	}
      	radioMedium.requestEdgeAnalysis();
      } catch (ClassCastException e) {
      }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      if (row < 0 || row >= radioMedium.getEdges().length) {
        return false;
      }

      Mote sourceMote = radioMedium.getEdges()[row].source.getMote();
      if (column == IDX_SRC) {
        gui.signalMoteHighlight(sourceMote);
        return false;
      }
      if (column == IDX_DST) {
        gui.signalMoteHighlight(radioMedium.getEdges()[row].superDest.radio.getMote());
        return false;
      }
      if (column == IDX_RATIO) {
        return true;
      }
      if (column == IDX_SIGNAL) {
        return true;
      }
      if (column == IDX_LQI) {
        return true;
      }
      return column == IDX_DELAY;
    }

    @Override
    public Class<?> getColumnClass(int c) {
      return getValueAt(0, c).getClass();
    }
  };

  @Override
  public void closePlugin() {
    radioMedium.getRadioTransmissionTriggers().deleteTriggers(this);
  }

}
