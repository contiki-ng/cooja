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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.COOJAProject;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.ProjectConfig;

/**
 * This dialog allows a user to manage Cooja extensions: extensions to COOJA that 
 * provide new functionality such as radio mediums, plugins, and mote types.
 *
 * @author Fredrik Osterlind
 */
public class ProjectDirectoriesDialog extends JDialog {
	private static final Logger logger = LogManager.getLogger(ProjectDirectoriesDialog.class);

  private final Cooja gui;

  final JTable table;
	private final JTextArea projectInfo = new JTextArea("Extension information:");

  final ArrayList<COOJAProject> currentProjects = new ArrayList<>();
	private COOJAProject[] returnedProjects = null;

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
    final var treePanel = new DirectoryTreePanel(this);
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
      // Expand view.
      try {
        var projectCanonical = currentProjects.get(table.getSelectedRow()).dir.getCanonicalPath();
        var tp = new TreePath(treePanel.tree.getModel().getRoot());
        tp = DirectoryTreePanel.buildTreePath(projectCanonical, treePanel.treeRoot, tp, treePanel.tree);
        if (tp != null) {
          treePanel.tree.setSelectionPath(tp);
          treePanel.tree.scrollPathToVisible(tp);
        }
      } catch (IOException ex) {
        logger.warn("Error when expanding projects: " + ex.getMessage());
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
          logger.fatal("Error when merging config: " + ex.getMessage(), ex);
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
          if (newDefaultProjectDirs.length() > 0) {
            newDefaultProjectDirs.append(";");
          }
          newDefaultProjectDirs.append(gui.createPortablePath(p.dir, false).getPath());
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
				JPanel p = new JPanel(new BorderLayout());
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

			final JSplitPane listPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
			listPane.setLeftComponent(treePanel);
			listPane.setRightComponent(projectPane);
      projectPane.setDividerLocation(0.6);
      listPane.setDividerLocation(0.5);
			mainPane.add(listPane);
		}

		JPanel topPanel = new JPanel(new BorderLayout());
		getContentPane().add(BorderLayout.NORTH, topPanel);
		getContentPane().add(BorderLayout.CENTER, mainPane);
		getContentPane().add(BorderLayout.SOUTH, buttonPane);
		setSize(700, 500);
    setLocationRelativeTo(Cooja.getTopParentContainer());
	}

	protected void showProjectInfo(COOJAProject project) {
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
		if (project.getConfigMoteInterfaces() != null) {
			projectInfo.append("Cooja mote interfaces: " + Arrays.toString(project.getConfigMoteInterfaces()) + "\n");
		}
		if (project.getConfigCSources() != null) {
			projectInfo.append("Cooja mote C sources: " + Arrays.toString(project.getConfigCSources()) + "\n");
		}
	}

	public COOJAProject[] getProjects() {
		return currentProjects.toArray(new COOJAProject[0]);
	}

  protected void addProjectDir(COOJAProject project, int index) {
		currentProjects.add(index, project);
		((AbstractTableModel)table.getModel()).fireTableDataChanged();
	}

  protected void removeProjectDir(COOJAProject project) {
		currentProjects.remove(project);
		((AbstractTableModel)table.getModel()).fireTableDataChanged();
		repaint();
	}

  public void selectListProject(File dir) {
		/* Check if project exists */
		for (COOJAProject p: currentProjects) {
			if (dir.equals(p.dir)) {
        int i = currentProjects.indexOf(p);
				if (i >= 0) {
					table.getSelectionModel().setSelectionInterval(i, i);
				}
				return;
			}
		}

	}
}

/**
 * Shows a directory tree, and allows for selecting directories with a cooja.config file.  
 * 
 * @author Fredrik Osterlind
 */
class DirectoryTreePanel extends JPanel {
	private static final Logger logger = LogManager.getLogger(DirectoryTreePanel.class);

	private final ProjectDirectoriesDialog parent;
	final JTree tree;
	final DefaultMutableTreeNode treeRoot;
	public DirectoryTreePanel(ProjectDirectoriesDialog parent) {
		super(new BorderLayout());
		this.parent = parent;

		/* Build directory tree */
		treeRoot = new DefaultMutableTreeNode("My Computer");
		tree = new JTree(treeRoot);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.expandRow(0);
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private final Icon unselectedIcon = new CheckboxIcon(null);
			private final Icon selectedIcon = new CheckboxIcon(new Color(0, 255, 0, 128));
			private final Icon errorIcon = new CheckboxIcon(new Color(255, 0, 0, 128));
			private Font boldFont = null;
			private Font normalFont = null;
			@Override
			public Component getTreeCellRendererComponent(JTree tree,
					Object value, boolean sel, boolean expanded, boolean leaf,
					int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,
						row, hasFocus);
				if (value instanceof DefaultMutableTreeNode) {
					value = ((DefaultMutableTreeNode) value).getUserObject();
				}
				if (!(value instanceof TreeDirectory)) {
					return this;
				}
				TreeDirectory td = (TreeDirectory) value;

				if (boldFont == null) {
					normalFont = getFont();
					boldFont = getFont().deriveFont( Font.BOLD );
				}

				/* Style */
				setFont(normalFont);
				if (td.isProject()) {
					if (td.containsConfig()) {
						setIcon(selectedIcon);
					} else {
						/* Error: no cooja.config */
						setIcon(errorIcon);
						setFont(boldFont);
					}
				} else if (td.containsConfig()) {
					setIcon(unselectedIcon);
				} else if (td.subtreeContainsProject()) {
					setFont(boldFont);
				}

				return this;
			}
			class CheckboxIcon implements Icon {
				Icon icon;
				final Color color;
				public CheckboxIcon(Color color) {
					this.icon = (Icon) UIManager.get("CheckBox.icon");
					this.color = color;
				}
				@Override
				public int getIconHeight() {
					if (icon == null) {
						return 18;
					}
					return icon.getIconHeight();
				}
				@Override
				public int getIconWidth() {
					if (icon == null) {
						return 18;
					}
					return icon.getIconWidth();
				}
				@Override
				public void paintIcon(Component c, Graphics g, int x, int y) {
					if (icon != null) {
						try {
							icon.paintIcon(c, g, x, y);
						} catch (Exception e) {
							icon = null;
						}
					}
					if (icon == null) {
						g.setColor(Color.WHITE);
						g.fillRect(x+1, y+1, 16, 16);
						g.setColor(Color.BLACK);
						g.drawRect(x+1, y+1, 16, 16);
					}
					if (color != null) {
						g.setColor(color);
						g.fillRect(x, y, getIconWidth(), getIconHeight());
					}
				}
			}
		});
		tree.setModel(new COOJAProjectTreeModel(treeRoot));
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
				if (selPath == null) {
					return;
				}
				if (e.getClickCount() != 1) {
					return;
				}
				Object o = selPath.getLastPathComponent();
				if (!(o instanceof DefaultMutableTreeNode)) {
					return;
				}
				if (!(((DefaultMutableTreeNode) o).getUserObject() instanceof TreeDirectory)) {
					return;
				}
				TreeDirectory pd = (TreeDirectory) ((DefaultMutableTreeNode) o).getUserObject();
				Rectangle r = tree.getPathBounds(selPath);
				int delta = e.getX() - r.x;
				if (delta > 18 /* XXX Icon width */) {
					return;
				}

				if (pd.isProject()) {
          for (var p : DirectoryTreePanel.this.parent.getProjects()) {
            if (p.dir.equals(pd.dir)) {
              DirectoryTreePanel.this.parent.removeProjectDir(p);
            }
          }
        } else if (pd.containsConfig()) {
          try {
            DirectoryTreePanel.this.parent.currentProjects.add(new COOJAProject(pd.dir));
            ((AbstractTableModel) DirectoryTreePanel.this.parent.table.getModel()).fireTableDataChanged();
          } catch (IOException e1) {
            logger.error("Failed to parse Cooja project: {}", pd.dir, e1);
          }
        }
        DirectoryTreePanel.this.parent.repaint();
			}
		});
    tree.addTreeSelectionListener(e -> {
      TreePath selPath = e.getPath();
      if (selPath == null) {
        return;
      }
      Object o = selPath.getLastPathComponent();
      if (!(o instanceof DefaultMutableTreeNode)) {
        return;
      }
      if (!(((DefaultMutableTreeNode) o).getUserObject() instanceof TreeDirectory)) {
        return;
      }
      TreeDirectory pd = (TreeDirectory) ((DefaultMutableTreeNode) o).getUserObject();
      if (pd.isProject()) {
        DirectoryTreePanel.this.parent.selectListProject(pd.dir);
      }
    });

		/* Try to expand current COOJA projects */
		for (COOJAProject project: parent.getProjects()) {
			if (!project.dir.exists()) {
				logger.fatal("Project directory not found: " + project.dir);
				continue;
			}
			try {
				String projectCanonical = project.dir.getCanonicalPath();
				TreePath tp = new TreePath(tree.getModel().getRoot());
				tp = buildTreePath(projectCanonical, treeRoot, tp, tree);
				if (tp != null) {
					tree.expandPath(tp.getParentPath());
				}
			} catch (IOException ex) {
				logger.warn("Error when expanding projects: " + ex.getMessage());
			}
		}
		add(BorderLayout.CENTER, new JScrollPane(tree));
	}

  static TreePath buildTreePath(String projectCanonical, DefaultMutableTreeNode parent, TreePath tp, JTree tree)
	throws IOException {
		/* Force filesystem listing */
		tree.getModel().getChildCount(parent);

		for (int i=0; i < tree.getModel().getChildCount(parent); i++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) tree.getModel().getChild(parent, i);
			Object userObject = child.getUserObject();
			if (!(userObject instanceof TreeDirectory)) {
				logger.fatal("Bad tree element: " + userObject.getClass());
				continue;
			}
			TreeDirectory td = (TreeDirectory) userObject;
			String treeCanonical = td.dir.getCanonicalPath();

			projectCanonical = projectCanonical.replace('\\', '/');
			if (!projectCanonical.endsWith("/")) {
				projectCanonical += "/";
			}
			treeCanonical = treeCanonical.replace('\\', '/');
			if (!treeCanonical.endsWith("/")) {
				treeCanonical += "/";
			}

			if (projectCanonical.startsWith(treeCanonical)) {
				tp = tp.pathByAddingChild(child);
				if (projectCanonical.equals(treeCanonical)) {
					return tp;
				}

				return buildTreePath(projectCanonical, child, tp, tree);
			}
		}
		return null;
	}

	private class TreeDirectory {
		final File dir;
		File[] subdirs = null;

		public TreeDirectory(File file) {
			this.dir = file;
		}

		boolean isProject() {
			for (COOJAProject project: parent.getProjects()) {
				if (project.dir.equals(dir)) {
					return true;
				}
			}
			return false;
		}
		boolean containsConfig() {
			return new File(dir, ProjectConfig.PROJECT_CONFIG_FILENAME).exists();
		}
		boolean subtreeContainsProject() {
			try {
				String dirCanonical = dir.getCanonicalPath();
				for (COOJAProject project: parent.getProjects()) {
					if (!project.dir.exists()) {
						continue;
					}
					String projectCanonical = project.dir.getCanonicalPath();
					if (projectCanonical.startsWith(dirCanonical)) {
						return true;
					}
				}
			} catch (IOException ex) {
			}
			return false;
		}
		@Override
		public String toString() {
			if (dir.getName().isEmpty()) {
				return dir.getAbsolutePath();
			}
			return dir.getName();
		}
	}
	private class COOJAProjectTreeModel extends DefaultTreeModel {
		private final DefaultMutableTreeNode computerNode;

		public COOJAProjectTreeModel(DefaultMutableTreeNode computerNode) {
			super(computerNode);
			this.computerNode = computerNode;

			/* List roots */
			File[] devices = File.listRoots();
			if (devices == null) {
				logger.fatal("Could not list filesystem");
				return;
			}
			for (File device: devices) {
				DefaultMutableTreeNode deviceNode = new DefaultMutableTreeNode(new TreeDirectory(device));
				computerNode.add(deviceNode);
			}
		}
		@Override
		public Object getRoot() {
			return computerNode.getUserObject();
		}
		@Override
		public boolean isLeaf(Object node) {
			if ((node instanceof DefaultMutableTreeNode)) {
				node = ((DefaultMutableTreeNode)node).getUserObject();
			}
			if (!(node instanceof TreeDirectory)) {
				/* Computer node */
				return false;
			}
			TreeDirectory td = ((TreeDirectory)node);

			return td.dir.isFile();
		}
		@Override
		public int getChildCount(Object parent) {
      if (parent instanceof DefaultMutableTreeNode node) {
        parent = node.getUserObject();
      }
			if (!(parent instanceof TreeDirectory)) {
				/* Computer node */
				return computerNode.getChildCount();
			}
			TreeDirectory td = ((TreeDirectory)parent);

			File[] children;
			if (td.subdirs != null) {
				children = td.subdirs;
			} else {
				children = getDirectoryList(td.dir);
				td.subdirs = children;
			}
			if (children == null) {
				return 0;
			}
			return children.length;
		}
		@Override
		public Object getChild(Object parent, int index) {
      if (parent instanceof DefaultMutableTreeNode node) {
        parent = node.getUserObject();
      }
			if (!(parent instanceof TreeDirectory)) {
				/* Computer node */
				return computerNode.getChildAt(index);
			}
			TreeDirectory td = ((TreeDirectory)parent);

			File[] children;
			if (td.subdirs != null) {
				children = td.subdirs;
			} else {
				children = getDirectoryList(td.dir);
				td.subdirs = children;
			}
			if ((children == null) || (index >= children.length)) {
				return null;
			}
			return new DefaultMutableTreeNode(new TreeDirectory(children[index]));
		}
		@Override
		public int getIndexOfChild(Object parent, Object child) {
      if (parent instanceof DefaultMutableTreeNode node) {
        parent = node.getUserObject();
      }
			if (!(parent instanceof TreeDirectory)) {
				/* Computer node */
				for(int i=0; i < computerNode.getChildCount(); i++) {
					if (computerNode.getChildAt(i).equals(child)) {
						return i;
					}
				}
			}
			TreeDirectory td = ((TreeDirectory)parent);

			File[] children;
			if (td.subdirs != null) {
				children = td.subdirs;
			} else {
				children = getDirectoryList(td.dir);
				td.subdirs = children;
			}
			if (children == null) {
				return -1;
			}
			if (child instanceof DefaultMutableTreeNode) {
				child = ((DefaultMutableTreeNode)child).getUserObject();
			}
			File subDir = ((TreeDirectory)child).dir;
			for(int i = 0; i < children.length; i++) {
				if (subDir.equals(children[i])) {
					return i;
				}
			}
			return -1;
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newvalue) {}
		@Override
		public void addTreeModelListener(TreeModelListener l) {}
		@Override
		public void removeTreeModelListener(TreeModelListener l) {}

    private File[] getDirectoryList(File parent) {
      var dirs = parent.listFiles(file -> file.isDirectory() && !file.getName().startsWith("."));
			Arrays.sort(dirs);
			return dirs;
		}
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

		Enumeration<String> allPropertyNames = config.getPropertyNames();
		while (allPropertyNames.hasMoreElements()) {
			String propertyName = allPropertyNames.nextElement();
			keyPane.add(new JLabel(propertyName));
      valuePane.add(new JLabel(config.getStringValue(propertyName).equals("") ? "" : config.getStringValue(propertyName)));
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
