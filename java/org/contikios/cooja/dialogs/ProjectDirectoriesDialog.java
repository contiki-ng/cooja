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

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.contikios.cooja.COOJAProject;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This dialog allows a user to manage Cooja extensions: extensions to COOJA that 
 * provide new functionality such as radio mediums, plugins, and mote types.
 *
 * @author Fredrik Osterlind
 */
public class ProjectDirectoriesDialog extends JDialog {
	private static final Logger logger = LoggerFactory.getLogger(ProjectDirectoriesDialog.class);

  private final Cooja gui;

  private final JTable table;
	private final JTextArea projectInfo = new JTextArea("Extension information:");

  private final ArrayList<COOJAProject> currentProjects = new ArrayList<>();
	private COOJAProject[] returnedProjects;

	/**
	 * Shows a blocking configuration dialog.
	 * Returns a list of new COOJA project directories, or null if canceled by the user. 
	 *  
	 * @param gui COOJA
	 * @param currentProjects Current projects
	 * @return New COOJA projects, or null
	 */
  public static COOJAProject[] showDialog(Cooja gui, COOJAProject[] currentProjects) {
    var dialog = new ProjectDirectoriesDialog(gui, currentProjects);
		dialog.setVisible(true);
		return dialog.returnedProjects;
	}

  private ProjectDirectoriesDialog(Cooja cooja, COOJAProject[] projects) {
    super(Cooja.getTopParentContainer(), "Cooja extensions", ModalityType.APPLICATION_MODAL);
    gui = cooja;
		table = new JTable(new AbstractTableModel() {
			@Override
			public int getColumnCount() {
				return 2;
			}
			@Override
			public int getRowCount() {
				return currentProjects.size();
			}
			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return rowIndex+1;
				}

				COOJAProject p = currentProjects.get(rowIndex);
				if (!p.directoryExists()) {
					return p + "  (not found)";
				}
				if (!p.configExists()) {
					return p + "  (no config)";
				}
				if (!p.configRead()) {
					return p + "  (config error)";
				}
				return p;
			}
		});
    table.setFillsViewportHeight(true);
		table.setTableHeader(null);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(e -> {
      if (table.getSelectedRow() < 0) {
        return;
      }
      showProjectInfo(currentProjects.get(table.getSelectedRow()));
    });
		table.getColumnModel().getColumn(0).setPreferredWidth(30);
		table.getColumnModel().getColumn(0).setMaxWidth(30);
		table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table,
					Object value, boolean isSelected, boolean hasFocus, int row,
					int column) {
				if (currentProjects.get(row).hasError()) {
					setBackground(Color.RED);
				} else {
					setBackground(table.getBackground());
				}
				return super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
						row, column);
			}
		});

		/* Add current extensions */
		for (COOJAProject project : projects) {
      currentProjects.add(project);
      ((AbstractTableModel)table.getModel()).fireTableDataChanged();
    }

		Box mainPane = Box.createVerticalBox();
		Box buttonPane = Box.createHorizontalBox();
		/* Lower buttons */
		{
			buttonPane.setBorder(BorderFactory.createEmptyBorder(0,3,3,3));
			buttonPane.add(Box.createHorizontalGlue());

      var button = new JButton("View config");
      button.addActionListener(e -> {
        try {
          /* Default config */
          ProjectConfig config = new ProjectConfig(true);

          /* Merge configs */
          for (COOJAProject project : getProjects()) {
            config.appendConfig(project.config);
          }
          var myDialog = new ConfigViewer(ProjectDirectoriesDialog.this, config);
          myDialog.setVisible(true);
        } catch (Exception ex) {
          logger.error("Error when merging config: " + ex.getMessage(), ex);
        }
      });
			buttonPane.add(button);
			buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));

			button = new JButton("Cancel");
      button.addActionListener(e -> {
        ProjectDirectoriesDialog.this.returnedProjects = null;
        dispose();
      });
			buttonPane.add(button);

			buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));

			button = new JButton("Apply for session");
      button.addActionListener(e -> {
        ProjectDirectoriesDialog.this.returnedProjects = currentProjects.toArray(new COOJAProject[0]);
        dispose();
      });
			buttonPane.add(button);

			buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
			
			button = new JButton("Save");
      button.addActionListener(e -> {
        var newDefaultProjectDirs = new StringBuilder();
        for (COOJAProject p : currentProjects) {
          if (!newDefaultProjectDirs.isEmpty()) {
            newDefaultProjectDirs.append(";");
          }
          var portablePath = Cooja.createContikiRelativePath(p.dir);
          if (portablePath == null) {
            portablePath = p.dir;
          }
          newDefaultProjectDirs.append(portablePath.getPath());
        }
        newDefaultProjectDirs = new StringBuilder(newDefaultProjectDirs.toString().replace('\\', '/'));
        Object[] options = {"Ok", "Cancel"};
        if (JOptionPane.showOptionDialog(ProjectDirectoriesDialog.this,
                "External tools setting DEFAULT_PROJECTDIRS will change from:\n"
                        + Cooja.getExternalToolsSetting("DEFAULT_PROJECTDIRS", "").replace(';', '\n')
                        + "\n\n to:\n\n"
                        + newDefaultProjectDirs.toString().replace(';', '\n'),
                "Change external tools settings?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                options, options[0]) != JOptionPane.YES_OPTION) {
          return;
        }
        Cooja.setExternalToolsSetting("DEFAULT_PROJECTDIRS", newDefaultProjectDirs.toString());
        dispose();
      });
			buttonPane.add(button);

			this.getRootPane().setDefaultButton(button);
		}

		/* Center: Tree and list*/
		{
      var sortPane = new JPanel(new BorderLayout());
      var button = new JButton("Move up");
      button.addActionListener(e -> {
        int selectedIndex = table.getSelectedRow();
        if (selectedIndex <= 0) {
          return;
        }
        COOJAProject project = currentProjects.get(selectedIndex);
        removeProjectDir(project);
        addProjectDir(project, selectedIndex - 1);
        table.getSelectionModel().setSelectionInterval(selectedIndex - 1, selectedIndex - 1);
      });
			sortPane.add(BorderLayout.NORTH, button);
			
			button = new JButton("Move down");
      button.addActionListener(e -> {
        int selectedIndex = table.getSelectedRow();
        if (selectedIndex < 0 || selectedIndex >= currentProjects.size() - 1) {
          return;
        }
        COOJAProject project = currentProjects.get(selectedIndex);
        removeProjectDir(project);
        addProjectDir(project, selectedIndex + 1);
        table.getSelectionModel().setSelectionInterval(selectedIndex + 1, selectedIndex + 1);
      });
			sortPane.add(BorderLayout.SOUTH, button);

			{
        button = new JButton("Add");
        button.addActionListener(e -> {
          var chooser = new JFileChooser();
          chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
              return file.isDirectory() || "cooja.config".equals(file.getName());
            }

            @Override
            public String getDescription() {
              return "Cooja extension config files (cooja.config)";
            }
          });
          chooser.setCurrentDirectory(new File(Cooja.getExternalToolsSetting("PATH_COOJA")));
          if (chooser.showOpenDialog(Cooja.getTopParentContainer()) != JFileChooser.APPROVE_OPTION) {
            return;
          }
          try {
            currentProjects.add(new COOJAProject(chooser.getSelectedFile().getParentFile()));
            ((AbstractTableModel)table.getModel()).fireTableDataChanged();
          } catch (IOException ex) {
            logger.error("Failed to parse Cooja project: {}", chooser.getSelectedFile(), ex);
          }
        });
        var p = new JPanel(new BorderLayout());
        p.add(BorderLayout.NORTH, button);
        button = new JButton("Remove");
        button.addActionListener(e -> {
          int selectedIndex = table.getSelectedRow();
          if (selectedIndex < 0 || selectedIndex >= currentProjects.size()) {
            return;
          }
          COOJAProject project = currentProjects.get(selectedIndex);
          Object[] options = {"Remove", "Cancel"};
          if (JOptionPane.showOptionDialog(Cooja.getTopParentContainer(),
                  "Remove Cooja project?\n" + project,
                  "Remove Cooja project?", JOptionPane.YES_NO_OPTION,
                  JOptionPane.WARNING_MESSAGE, null, options, options[0]) != JOptionPane.YES_OPTION) {
            return;
          }
          removeProjectDir(project);
        });
				p.add(BorderLayout.SOUTH, button);
				sortPane.add(BorderLayout.CENTER, p);
			}

			JPanel tableAndSort = new JPanel(new BorderLayout());
			JScrollPane scroll = new JScrollPane(table);
			tableAndSort.add(BorderLayout.CENTER, scroll);
			tableAndSort.add(BorderLayout.EAST, sortPane);

			final JSplitPane projectPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			projectPane.setTopComponent(tableAndSort);
			projectInfo.setEditable(false);
			projectPane.setBottomComponent(new JScrollPane(projectInfo));
      mainPane.add(projectPane);
		}

		JPanel topPanel = new JPanel(new BorderLayout());
		getContentPane().add(BorderLayout.NORTH, topPanel);
		getContentPane().add(BorderLayout.CENTER, mainPane);
		getContentPane().add(BorderLayout.SOUTH, buttonPane);
		setSize(700, 500);
    setLocationRelativeTo(Cooja.getTopParentContainer());
	}

	private void showProjectInfo(COOJAProject project) {
		projectInfo.setText("");
		if (project.getDescription() != null) {
			projectInfo.append("-- " + project.getDescription() + " --\n\n");
		}
		
		projectInfo.append("Directory: " + project.dir.getAbsolutePath() + 
				(project.directoryExists()?"":": NOT FOUND") + "\n");
		if (!project.directoryExists()) {
			return;
		}
		projectInfo.append("Configuration: " + project.configFile.getAbsolutePath() + 
				(project.configExists()?"":": NOT FOUND") + "\n");
		if (!project.configExists()) {
			return;
		}
		if (!project.configRead()) {
		        projectInfo.append("Parsing: " +
					   (project.configRead()?"OK":"FAILED") + "\n\n");
			return;
		}
    var sb = new StringBuilder();
    sb.append("Plugins:");
    for (var plugin : gui.getRegisteredPlugins()) {
      sb.append(' ').append(plugin);
    }
    projectInfo.append(sb.append("\n").toString());
    sb.setLength(0);
		if (project.getConfigJARs() != null) {
			String[] jars = project.getConfigJARs();
			projectInfo.append("JARs: " + Arrays.toString(jars) + "\n");
			for (String jar: jars) {
				File jarFile = Cooja.findJarFile(project.dir, jar);
				if (jarFile == null) {
					projectInfo.append("\tError: " + jar + " could not be found.\n");
				} else if (!jarFile.exists()) {
					projectInfo.append("\tError: " + jarFile.getAbsolutePath() + " could not be found.\n");
				} else {
					projectInfo.append("\t" + jarFile.getAbsolutePath() + " found\n");
				}
			}
		}
    var moteTypes = gui.getRegisteredMoteTypes();
    if (moteTypes != null) {
      sb.append("Mote types:");
      for (var moteType : moteTypes) {
        sb.append(' ').append(moteType.toString());
      }
      projectInfo.append(sb.append("\n").toString());
      sb.setLength(0);
    }
    var radioMediums = gui.getRegisteredRadioMediums();
    if (radioMediums != null) {
      sb.append("Radio mediums:");
      for (var medium : radioMediums) {
        sb.append(' ').append(medium.toString());
      }
      projectInfo.append(sb.append("\n").toString());
      sb.setLength(0);
    }
	}

	private COOJAProject[] getProjects() {
		return currentProjects.toArray(new COOJAProject[0]);
	}

  private void addProjectDir(COOJAProject project, int index) {
		currentProjects.add(index, project);
		((AbstractTableModel)table.getModel()).fireTableDataChanged();
	}

  private void removeProjectDir(COOJAProject project) {
		currentProjects.remove(project);
		((AbstractTableModel)table.getModel()).fireTableDataChanged();
		repaint();
	}
}

/**
 * Modal frame that shows all keys with their respective values of a given class
 * configuration.
 *
 * @author Fredrik Osterlind
 */
class ConfigViewer extends JDialog {
  public ConfigViewer(Dialog dialog, ProjectConfig config) {
		super(dialog, "Merged project configuration", true);
		JPanel configPane = new JPanel(new BorderLayout());

		/* Control */
		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.X_AXIS));
		buttonPane.add(Box.createHorizontalGlue());

		var button = new JButton("Close");
		button.addActionListener(e -> dispose());
		buttonPane.add(button);

		/* Config */
		JPanel keyPane = new JPanel();
		keyPane.setBackground(Color.WHITE);
		keyPane.setLayout(new BoxLayout(keyPane, BoxLayout.Y_AXIS));
		keyPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
		configPane.add(keyPane, BorderLayout.WEST);

		JPanel valuePane = new JPanel();
		valuePane.setBackground(Color.WHITE);
		valuePane.setLayout(new BoxLayout(valuePane, BoxLayout.Y_AXIS));
		configPane.add(valuePane, BorderLayout.EAST);

		var label = new JLabel("KEY");
		label.setForeground(Color.RED);
		keyPane.add(label);
		label = new JLabel("VALUE");
		label.setForeground(Color.RED);
		valuePane.add(label);

    for (var entry : config.getEntrySet()) {
      if (!(entry.getKey() instanceof String propertyName && entry.getValue() instanceof String val)) continue;
			keyPane.add(new JLabel(propertyName));
      // Add artificial space so the valuePane contains something. Otherwise, the next
      // row will have its values displayed on this row.
      valuePane.add(new JLabel(val.isEmpty() ? " " : val));
		}

		Container contentPane = getContentPane();
		configPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		configPane.setBackground(Color.WHITE);
		contentPane.add(new JScrollPane(configPane), BorderLayout.CENTER);
		contentPane.add(buttonPane, BorderLayout.SOUTH);
		pack();
		setAlwaysOnTop(true);
		setSize(700, 300);
		setLocationRelativeTo(dialog);
	}
}
