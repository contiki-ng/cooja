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

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.CoreComm;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.ProjectConfig;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.contikimote.ContikiMoteType.NetworkStack;

/**
 * Contiki Mote Type compile dialog.
 *
 * @author Fredrik Osterlind
 */
public class ContikiMoteCompileDialog extends AbstractCompileDialog {
  private static final long serialVersionUID = -2596048833554777606L;
  private static final Logger logger = LogManager.getLogger(ContikiMoteCompileDialog.class);

  private JComboBox netStackComboBox = new JComboBox(NetworkStack.values());

  public static boolean showDialog(
      Container parent,
      Simulation simulation,
      MoteType moteType) {

    final ContikiMoteCompileDialog dialog = new ContikiMoteCompileDialog(parent, simulation, moteType);

    /* Show dialog and wait for user */
    dialog.setVisible(true); /* BLOCKS */
    if (!dialog.createdOK()) {
      return false;
    }

    /* Assume that if a firmware exists, compilation was ok */
    return true;
  }

  private ContikiMoteCompileDialog(Container parent, Simulation simulation, MoteType moteType) {
    super(parent, simulation, moteType);

    if (contikiSource != null) {
      /* Make sure compilation variables are updated */
      getDefaultCompileCommands(contikiSource);
    }

    /* Add Contiki mote type specifics */
    addAdvancedTab(tabbedPane);
  }

  private void updateForSource(File source) {
    if (moteType.getIdentifier() == null) {
      /* Generate mote type identifier */
      moteType.setIdentifier(
          ContikiMoteType.generateUniqueMoteTypeID(simulation.getMoteTypes(), null));
    }
    
    String output_dir = Cooja.getExternalToolsSetting("PATH_CONTIKI_NG_BUILD_DIR", "build/cooja");
    
    /* Create variables used for compiling Contiki */
    ((ContikiMoteType)moteType).setContikiSourceFile(source);
    ((ContikiMoteType)moteType).libSource = new File(
        source.getParentFile(),
        output_dir + "/" + moteType.getIdentifier() + ".c"
    );
    ((ContikiMoteType)moteType).libFile = new File(
        source.getParentFile(),
        output_dir + "/" + moteType.getIdentifier() + ContikiMoteType.librarySuffix
    );
    ((ContikiMoteType)moteType).archiveFile = new File(
        source.getParentFile(),
        output_dir + "/" + moteType.getIdentifier() + ContikiMoteType.dependSuffix
    );
    ((ContikiMoteType)moteType).mapFile = new File(
        source.getParentFile(),
        output_dir + "/" + moteType.getIdentifier() + ContikiMoteType.mapSuffix);
    ((ContikiMoteType)moteType).javaClassName = CoreComm.getAvailableClassName();

    if (((ContikiMoteType)moteType).javaClassName == null) {
      logger.fatal("Could not allocate a core communicator.");
      return;
    }

    /* Prepare compiler environment */
    String[][] env = null;
    try {
      env = CompileContiki.createCompilationEnvironment(
          moteType.getIdentifier(),
          source,
          ((ContikiMoteType)moteType).mapFile,
          ((ContikiMoteType)moteType).libFile,
          ((ContikiMoteType)moteType).archiveFile,
          ((ContikiMoteType)moteType).javaClassName
      );
      CompileContiki.redefineCOOJASources(
          moteType,
          env
      );

      String[] envOneDimension = new String[env.length];
      for (int i=0; i < env.length; i++) {
        envOneDimension[i] = env[i][0] + "=" + env[i][1];
      }
      createEnvironmentTab(tabbedPane, env);

      /* Prepare compiler with environment variables */
      this.compilationEnvironment = envOneDimension;
    } catch (Exception e) {
      logger.warn("Error when creating environment: " + e.getMessage());
      e.printStackTrace();
      env = null;
    }
  }
  
  public boolean canLoadFirmware(File file) {
    /* Disallow loading firmwares without compilation */
    /*
    if (file.getName().endsWith(ContikiMoteType.librarySuffix)) {
      return true;
    }
    */

    return false;
  }

  public String getDefaultCompileCommands(final File source) {
      String save_command = getCompileCommands();

    if (moteType == null) {
      /* Not ready to compile yet */
      return save_command;
    }

    if (source == null || !source.exists()) {
      /* Not ready to compile yet */
      return save_command;
    }

    if (SwingUtilities.isEventDispatchThread()) {
      updateForSource(source);
    } else {
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            updateForSource(source);
          }
        });
      } catch (InvocationTargetException e) {
        logger.fatal("Error when updating for source " + source + ": " + e.getMessage(), e);
      } catch (InterruptedException e) {
        logger.fatal("Error when updating for source " + source + ": " + e.getMessage(), e);
      }
    }

    /*"make clean TARGET=cooja\n" + */
    final String target = getExpectedFirmwareFile(source).getName();
    String command = setCommandTarget(save_command, target);

    final String newstack = ((ContikiMoteType) moteType).getNetworkStack().getHeaderFile(); 
    command = setNetStack(command, newstack);

    return command;
  }

  private
  String setNetStack(String command, String newstack) {
      final String netstack_patern = "NETSTACK_CONF_H=([^\\s,]*)";

      if (newstack == null) {
          //remove netstack
          command = command.replaceAll(netstack_patern, "");
          return command;
      }

      final String netstack_def = "NETSTACK_CONF_H=" + newstack; 
      final String netstack_cmd = " DEFINES=" + netstack_def;  

      // update netstack header
      if ( command.indexOf("NETSTACK_CONF_H=") > 0) {
          command = command.replaceAll(netstack_patern, netstack_def);
      }
      else
          command += netstack_cmd;
      return command;
  }

  private
  String setCommandTarget(String command, String target) {

      String target_cmd = "TARGET="+getTargetName();
      
      int cmd_finish = command.lastIndexOf("make ");
      if (cmd_finish > 0) {
          // update old command with new source, and netstack
          final String target_patern = " ((\\S)*."+getTargetName()+") "+target_cmd;
          String make = command.substring(cmd_finish);
          make = make.replaceAll(target_patern, " " + target + " "+target_cmd );

          return command.substring(0, cmd_finish) + make;
      }
      else {
          String new_command = Cooja.getExternalToolsSetting("PATH_MAKE") + " " 
                  + target + " "+target_cmd
                  ;
          return command + "\n"+new_command;
      }
  }

  private final static String[][] PATH_IDENTIFIER = {
          {"[CONTIKI_DIR]","PATH_CONTIKI",""},
          //{"[COOJA_DIR]","PATH_COOJA",""},
          //{"[APPS_DIR]","PATH_APPS","apps"}
      };

  public File getExpectedFirmwareFile(File source) {
    return ContikiMoteType.getExpectedFirmwareFile(source);
  }

  public Class<? extends MoteInterface>[] getAllMoteInterfaces() {
	  ProjectConfig projectConfig = moteType.getConfig();
	  String[] intfNames = projectConfig.getStringArrayValue(ContikiMoteType.class, "MOTE_INTERFACES");
	  ArrayList<Class<? extends MoteInterface>> classes = new ArrayList<Class<? extends MoteInterface>>();

	  /* Load mote interface classes */
	  for (String intfName : intfNames) {
		  Class<? extends MoteInterface> intfClass =
				  gui.tryLoadClass(this, MoteInterface.class, intfName);

		  if (intfClass == null) {
			  logger.warn("Failed to load mote interface class: " + intfName);
			  continue;
		  }

		  classes.add(intfClass);
	  }
	  return classes.toArray(new Class[0]);
  }
  public Class<? extends MoteInterface>[] getDefaultMoteInterfaces() {
	  return getAllMoteInterfaces();
  }



  final JTextField netstack_headerTextField = new JTextField();
  private void updateForNetstack() {
      final String content = netstack_headerTextField.getText();
      ((ContikiMoteType)moteType).getNetworkStack().manualHeader = content;
      setDialogState(DialogState.SELECTED_SOURCE);
  };

  private void addAdvancedTab(JTabbedPane parent) {

    /* TODO System symbols */
    /*JCheckBox symbolsCheckBox = new JCheckBox("With system symbols", false);
    symbolsCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    symbolsCheckBox.setEnabled(false);
    symbolsCheckBox.setToolTipText("Not implemented");*/

    /* Communication stack */
    JLabel label = new JLabel("Default network stack header");
    label.setPreferredSize(LABEL_DIMENSION);
    final JTextField headerTextField = netstack_headerTextField;

    headerTextField.setText(((ContikiMoteType)moteType).getNetworkStack().manualHeader);
    headerTextField.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e){
            updateForNetstack();
        }
    });
    headerTextField.addFocusListener(new FocusListener() {
        public void focusLost(FocusEvent e) {
          if (!e.isTemporary()) {
              updateForNetstack();
          }
        }
        public void focusGained(FocusEvent e) { };
      });

    final Box netStackHeaderBox = Box.createHorizontalBox();
    netStackHeaderBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    netStackHeaderBox.add(label);
    netStackHeaderBox.add(Box.createHorizontalStrut(20));
    netStackHeaderBox.add(headerTextField);

    label = new JLabel("Default network stack");
    label.setPreferredSize(LABEL_DIMENSION);
    netStackComboBox.setSelectedItem(((ContikiMoteType)moteType).getNetworkStack());
    netStackComboBox.setEnabled(true);
    netStackComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ((ContikiMoteType)moteType).setNetworkStack((NetworkStack)netStackComboBox.getSelectedItem());
        netStackHeaderBox.setVisible((NetworkStack)netStackComboBox.getSelectedItem() == NetworkStack.MANUAL);
        setDialogState(DialogState.SELECTED_SOURCE);
      }
    });
    Box netStackBox = Box.createHorizontalBox();
    netStackBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    netStackBox.add(label);
    netStackBox.add(Box.createHorizontalStrut(20));
    netStackBox.add(netStackComboBox);
    netStackHeaderBox.setVisible((NetworkStack)netStackComboBox.getSelectedItem() == NetworkStack.MANUAL);


    /* Advanced tab */
    Box box = Box.createVerticalBox();
    box.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    /*box.add(symbolsCheckBox);*/
    box.add(netStackBox);
    box.add(netStackHeaderBox);
    box.add(Box.createVerticalGlue());
    JPanel container = new JPanel(new BorderLayout());
    container.add(BorderLayout.NORTH, box);
    parent.addTab("Advanced", null, new JScrollPane(container), "Advanced Contiki Mote Type settings");
  }

  private void createEnvironmentTab(JTabbedPane parent, Object[][] env) {
    /* Remove any existing environment tabs */
    for (int i=0; i < tabbedPane.getTabCount(); i++) {
      if (tabbedPane.getTitleAt(i).equals("Environment")) {
        tabbedPane.removeTabAt(i--);
      }
    }

    /* Create new tab, fill with current environment data */
    String[] columnNames = { "Variable", "Value" };
    JTable table = new JTable(env, columnNames) {
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };

    JPanel panel = new JPanel(new BorderLayout());
    JButton button = new JButton("Change environment variables: Open external tools dialog");
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        /* Show external tools dialog */
        ExternalToolsDialog.showDialog(Cooja.getTopParentContainer());

        /* Update and select environment tab */
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            getDefaultCompileCommands(((ContikiMoteType)moteType).getContikiSourceFile());
            for (int i=0; i < tabbedPane.getTabCount(); i++) {
              if (tabbedPane.getTitleAt(i).equals("Environment")) {
                tabbedPane.setSelectedIndex(i);
                break;
              }
            }
            setDialogState(DialogState.AWAITING_COMPILATION);
          }
        });

      }
    });
    panel.add(BorderLayout.NORTH, button);
    panel.add(BorderLayout.CENTER, new JScrollPane(table));

    parent.addTab("Environment", null, panel, "Environment variables");
  }

  public void writeSettingsToMoteType() {
    /* XXX Do not load the generated firmware.
     * Instead, load the copy in output_dir */
    String output_dir = Cooja.getExternalToolsSetting("PATH_CONTIKI_NG_BUILD_DIR", "build/cooja"); 
    File contikiFirmware = new File(
        moteType.getContikiSourceFile().getParentFile(),
        output_dir + "/" + moteType.getIdentifier() + ContikiMoteType.librarySuffix
    );
    moteType.setContikiFirmwareFile(contikiFirmware);

    /* TODO System symbols */
    ((ContikiMoteType)moteType).setHasSystemSymbols(false);
  }

  public void compileContiki()
  throws Exception {
    if (((ContikiMoteType)moteType).libSource == null ||
        ((ContikiMoteType)moteType).libFile == null ||
        ((ContikiMoteType)moteType).archiveFile == null ||
        ((ContikiMoteType)moteType).mapFile == null ||
        ((ContikiMoteType)moteType).javaClassName == null) {
      throw new Exception("Library variables not defined");
    }

    /* Delete output files before compiling */
    ((ContikiMoteType)moteType).libSource.delete();
    ((ContikiMoteType)moteType).libFile.delete();
    ((ContikiMoteType)moteType).archiveFile.delete();
    ((ContikiMoteType)moteType).mapFile.delete();

    /* Extract Contiki dependencies from currently selected mote interfaces */
    String[] coreInterfaces =
      ContikiMoteType.getRequiredCoreInterfaces(getSelectedMoteInterfaceClasses());
    ((ContikiMoteType)moteType).setCoreInterfaces(coreInterfaces);

    /* Generate Contiki main source */
    /*try {
      CompileContiki.generateSourceFile(
          ((ContikiMoteType)moteType).libSource,
          ((ContikiMoteType)moteType).javaClassName,
          ((ContikiMoteType)moteType).getSensors(),
          ((ContikiMoteType)moteType).getCoreInterfaces()
      );
    } catch (Exception e) {
      throw (Exception) new Exception("Error when generating Contiki main source").initCause(e);
    }*/

    /* Start compiling */
    super.compileContiki();
  }

  protected String getTargetName() {
  	return "cooja";
  }

}
