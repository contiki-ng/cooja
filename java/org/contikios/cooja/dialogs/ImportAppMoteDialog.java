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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.motes.ImportAppMoteType;

/**
 * Java Application Mote Type compile dialog.
 *
 * @author Niclas Finne
 */
public class ImportAppMoteDialog extends JDialog {
  private final static Dimension LABEL_DIMENSION = new Dimension(170, 25);

  private static String lastPath;
  private static String lastFile;

  private final JTextField pathField;
  private final JTextField classField;
  private boolean hasSelected;

  public ImportAppMoteDialog(final Cooja cooja, final ImportAppMoteType moteType) {
    super(Cooja.getTopParentContainer(), "Create Mote Type: Application Mote", ModalityType.APPLICATION_MODAL);
    JPanel topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
    topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    var descriptionField = new JTextField(40);
    if (moteType.getDescription() != null) {
      descriptionField.setText(moteType.getDescription());
    } else {
      descriptionField.setText("[enter mote type description]");
    }
    topPanel.add(createPanel("Description", descriptionField));

    pathField = new JTextField(40);
    File moteClassPath = moteType.getMoteClassPath();
    if (moteClassPath != null) {
      pathField.setText(moteClassPath.getPath());
    } else if (lastPath != null) {
      pathField.setText(lastPath);
    }
    topPanel.add(createPanel("Java Class Path:", pathField));

    classField = new JTextField(40);
    String moteClass = moteType.getMoteClassName();
    if (moteClass != null && !moteClass.isEmpty()) {
      classField.setText(moteClass);
    } else if (lastFile != null) {
      classField.setText(lastFile);
    }
    JButton browseButton = new JButton("Browse");
    browseButton.addActionListener(e -> {
      JFileChooser fc = new JFileChooser();
      String path = pathField.getText();
      String name = classField.getText();
      if (name.indexOf('/') >= 0 || name.indexOf(File.separatorChar) >= 0) {
        // Already full path
        fc.setSelectedFile(new File(name));
      } else if (!name.isEmpty()) {
        fc.setSelectedFile(new File(new File(path), name.replace(".", "/") + ".class"));
      } else {
        var fp = cooja.restorePortablePath(new File(Cooja.getExternalToolsSetting("IMPORT_APP_LAST", "mymote.class")));
        if (!path.isEmpty() && !fp.getAbsolutePath().startsWith(path)) {
          fc.setCurrentDirectory(new File(path));
        } else {
          fc.setSelectedFile(fp);
        }
      }
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setFileFilter(new FileNameExtensionFilter("Application Mote Java Class", "class"));
      fc.setDialogTitle("Select Application Mote Java Class");
      if (fc.showOpenDialog(ImportAppMoteDialog.this) == JFileChooser.APPROVE_OPTION) {
        File fp = fc.getSelectedFile();
        if (fp != null) {
          trySetClass(cooja, fp);
        }
      }
    });
    topPanel.add(createPanel("Application Mote Java Class:", classField, browseButton));

    ActionListener cancelAction = e -> {
      setVisible(false);
      dispose();
    };
    var cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(cancelAction);

    KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    getRootPane().registerKeyboardAction(cancelAction, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);

    var createButton = new JButton("Create");
    createButton.addActionListener(e -> {
      String className = classField.getText();
      if (className.isEmpty()) {
        JOptionPane.showMessageDialog(ImportAppMoteDialog.this,
            "No class specified", "Failed to load class", JOptionPane.ERROR_MESSAGE);
        return;
      }
      File classFile;
      if (className.indexOf('/') >= 0 || className.indexOf(File.separatorChar) >= 0) {
        classFile = new File(className);
      } else {
        classFile = new File(new File(pathField.getText()), className.replace(".", "/") + ".class");
      }
      if (trySetClass(cooja, classFile)) {
        moteType.setDescription(descriptionField.getText());
        String path = pathField.getText();
        if (!path.isEmpty()) {
          moteType.setMoteClassPath(new File(path));
          lastPath = path;
        } else {
          moteType.setMoteClassPath(null);
          lastPath = null;
        }
        moteType.setMoteClassName(classField.getText());
        lastFile = classField.getText();
        hasSelected = true;
        ImportAppMoteDialog.this.setVisible(false);
        ImportAppMoteDialog.this.dispose();
      }
    });
    getRootPane().setDefaultButton(createButton);

    JPanel buttonPanel = new JPanel();
    buttonPanel.add(createButton);
    buttonPanel.add(cancelButton);

    /* Build panel */
    var mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(BorderLayout.NORTH, topPanel);
    mainPanel.add(buttonPanel, BorderLayout.SOUTH);
    setContentPane(mainPanel);

    setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cancelButton.doClick();
      }
    });

    descriptionField.requestFocus();
    descriptionField.selectAll();

    pack();
    setLocationRelativeTo(Cooja.getTopParentContainer());
  }

  private boolean trySetClass(Cooja cooja, File classFile) {
    try (var loader = ImportAppMoteType.createTestLoader(cooja, classFile)) {
      if (!loader.isTestSubclass(Mote.class)) {
        JOptionPane.showMessageDialog(ImportAppMoteDialog.this,
            "Class '" + classFile + "'\n is not of type Mote", "Failed to load class",
            JOptionPane.ERROR_MESSAGE);
      } else {
        pathField.setText(loader.getTestClassPath().getPath());
        classField.setText(loader.getTestClassName());
        Cooja.setExternalToolsSetting("IMPORT_APP_LAST", cooja.createPortablePath(classFile).getPath());
        return true;
      }
    } catch (FileNotFoundException e1) {
      JOptionPane.showMessageDialog(ImportAppMoteDialog.this,
          "Could not find class '" + classFile + "'", "Failed to load class",
          JOptionPane.ERROR_MESSAGE);
    } catch (Exception | LinkageError e1) {
      JOptionPane.showMessageDialog(ImportAppMoteDialog.this,
          "Could not load class '" + classFile + "':\n" + e1, "Failed to load class",
          JOptionPane.ERROR_MESSAGE);
    }
    return false;
  }

  private static Component createPanel(String title, JComponent field1) {
    return createPanel(title, field1, null);
  }

  private static Component createPanel(String title, JComponent field1, JComponent field2) {
    Box panel = Box.createHorizontalBox();
    JLabel label = new JLabel(title);
    label.setPreferredSize(LABEL_DIMENSION);
    panel.add(label);

    panel.add(field1);
    if (field2 != null) {
      panel.add(field2);
    }
    return panel;
  }

  public boolean waitForUserResponse() {
    setVisible(true);
    return hasSelected;
  }

}
