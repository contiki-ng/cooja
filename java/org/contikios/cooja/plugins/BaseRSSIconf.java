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
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;


/**
 * Simple user interface for configuring BaseRSSI of Motes
 * 
 * @see DirectedGraphMedium
 * @author Sebastian Schinabeck
 */
@ClassDescription("Base RSSI")
@PluginType(PluginType.PType.SIM_PLUGIN)
@SupportedArguments(radioMediums = { AbstractRadioMedium.class })

public class BaseRSSIconf extends VisPlugin {
	private final static int IDX_Mote = 0;
	private final static int IDX_BaseRSSI = 1;

	private final static String[] COLUMN_NAMES = { "Mote",
			"BaseRSSI (-45!)" }; // TODO maybe include offset of -45 directly

	private final AbstractRadioMedium radioMedium;
	private final Simulation sim;
	
	
	
	public BaseRSSIconf(Simulation sim, Cooja gui) {
		super("Base RSSI Configurator", gui);
		this.sim = sim;
		radioMedium = (AbstractRadioMedium) sim.getRadioMedium();
    final var model = new AbstractTableModel() {
      @Override
      public String getColumnName(int column) {
        if (column < 0 || column >= COLUMN_NAMES.length) {
          return "";
        }
        return COLUMN_NAMES[column];
      }

      @Override
      public int getRowCount() {
        return radioMedium.getRegisteredRadios().length;
      }

      @Override
      public int getColumnCount() {
        return COLUMN_NAMES.length;
      }

      @Override
      public Object getValueAt(int row, int column) {
        if (row < 0 || row >= radioMedium.getRegisteredRadios().length ||
                column < 0 || column >= COLUMN_NAMES.length) {
          return "";
        }
        Radio radio = radioMedium.getRegisteredRadios()[row];
        return column == IDX_Mote ? radio.getMote() : radioMedium.getBaseRssi(radio);
      }

      @Override
      public void setValueAt(Object value, int row, int column) {
        if (row < 0 || row >= radioMedium.getRegisteredRadios().length ||
                column < 0 || column >= COLUMN_NAMES.length) {
          return;
        }
        if (column == IDX_BaseRSSI) {
          if (value instanceof Number num) {
            var radio = radioMedium.getRegisteredRadios()[row];
            radioMedium.setBaseRssi(radio, num.doubleValue());
          }
        } else {
          super.setValueAt(value, row, column);
        }
      }

      @Override
      public boolean isCellEditable(int row, int column) {
        if (row < 0 || row >= radioMedium.getRegisteredRadios().length) {
          return false;
        }

        if (column == IDX_Mote) {
          gui.signalMoteHighlight(radioMedium.getRegisteredRadios()[row].getMote());
          return false;
        }
        if (column == IDX_BaseRSSI) {
          gui.signalMoteHighlight(radioMedium.getRegisteredRadios()[row].getMote());
          return true;
        }
        return false;
      }

      @Override
      public Class<?> getColumnClass(int c) {
        return getValueAt(0, c).getClass();
      }
    };
    radioMedium.getRadioMediumTriggers().addTrigger(this, (obs, obj) -> model.fireTableDataChanged());
    sim.getMoteTriggers().addTrigger(this, (o, m) -> model.fireTableDataChanged());
		/* Represent motes and RSSI by table */
    final var combo = new JComboBox<Number>();
		var motesTable = new JTable(model) {
			@Override
			public TableCellEditor getCellEditor(int row, int column) {
				combo.removeAllItems();
				if (column == IDX_Mote) {
					for (double d = 1.0; d <= radioMedium.getRegisteredRadios().length; d += 1.0) {
						combo.addItem(d);
					}
				} else if (column == IDX_BaseRSSI) {
					for (double d = AbstractRadioMedium.SS_STRONG; d >= AbstractRadioMedium.SS_NOTHING; d -= 1) {
						combo.addItem((int) d);
					}
				}

				return super.getCellEditor(row, column);
			}
		};
		motesTable.setFillsViewportHeight(true);
		combo.setEditable(true);

		motesTable.getColumnModel().getColumn(IDX_Mote)
				.setCellRenderer(new DefaultTableCellRenderer() { // TODO ????
							@Override
							public void setValue(Object value) {
                if (!(value instanceof Double num)) {
									setText(value.toString());
									return;
								}
                setText(String.format("%1.1f", num));
							}
						});
		motesTable.getColumnModel().getColumn(IDX_BaseRSSI)
				.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public void setValue(Object value) {
            if (!(value instanceof Double num)) {
							setText(value.toString());
							return;
						}
            setText(String.format("%1.1f dBm", num));
					}
				});
		motesTable.getColumnModel().getColumn(IDX_Mote)
				.setCellEditor(new DefaultCellEditor(combo));
		motesTable.getColumnModel().getColumn(IDX_BaseRSSI)
				.setCellEditor(new DefaultCellEditor(combo));

		motesTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		motesTable.getSelectionModel().setSelectionMode(
				ListSelectionModel.SINGLE_SELECTION);

		
		add(BorderLayout.CENTER, new JScrollPane(motesTable));

		model.fireTableDataChanged();
		setSize(400, 300);
	}

	@Override
	public void closePlugin() {
    radioMedium.getRadioMediumTriggers().deleteTriggers(this);
    sim.getMoteTriggers().deleteTriggers(this);
  }
}
