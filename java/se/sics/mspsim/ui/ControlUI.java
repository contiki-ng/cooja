/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * This file is part of MSPSim.
 *
 * $Id$
 *
 * -----------------------------------------------------------------
 *
 * ControlUI
 *
 * Author  : Joakim Eriksson
 * Created : Sun Oct 21 22:00:00 2007
 * Updated : $Date$
 *           $Revision$
 */

package se.sics.mspsim.ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.SimEvent;
import se.sics.mspsim.core.SimEventListener;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.util.ComponentRegistry;
import se.sics.mspsim.util.DebugInfo;
import se.sics.mspsim.util.ELF;
import se.sics.mspsim.util.ServiceComponent;

public class ControlUI extends JPanel implements ActionListener, SimEventListener, ServiceComponent {
  private static final String TITLE = "MSPSim monitor";

  private ManagedWindow window;
  private JButton controlButton;
  private MSP430 cpu;
  private GenericNode node;
  private DebugUI dui;

  private ELF elfData;
  private SourceViewer sourceViewer;

  private Action stepAction;
  private ComponentRegistry registry;

  private Status status = Status.STOPPED;

  private String name;

  public ControlUI() {
    super(new GridLayout(0, 1));
  }

  private void setup() {
    if (window != null) return;
    this.cpu = (MSP430) registry.getComponent("cpu");
    this.node = (GenericNode) registry.getComponent("node");
    elfData = (ELF) registry.getComponent("elf");

    WindowManager wm = (WindowManager) registry.getComponent("windowManager");
    window = wm.createWindow("ControlUI");
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());

    jp.add(this, BorderLayout.WEST);
    dui = new DebugUI(cpu);
    jp.add(dui, BorderLayout.CENTER);
    window.add(jp);

    createButton("Debug On");
    controlButton = createButton(cpu.isRunning() ? "Stop" : "Run");

    JButton stepButton = new JButton();
    // Allow the button to get the same height as the other buttons.
    stepButton.setMaximumSize(new Dimension(60, Short.MAX_VALUE));

    SpinnerNumberModel stepsModel = new SpinnerNumberModel();
    stepsModel.setValue(1);
    stepsModel.setMinimum(1);
    JSpinner stepsSpinner = new JSpinner(stepsModel);

    JPanel stepsPanel = new JPanel();
    stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.LINE_AXIS));
    stepsPanel.add(stepButton);
    stepsPanel.add(stepsSpinner);
    add(stepsPanel);

    stepAction = new AbstractAction("Step") {
      @Override
      public void actionPerformed(ActionEvent e) {
        stepButton.setEnabled(false);
        int steps = (int)stepsSpinner.getValue();
        for (int i = 0; i < steps; i++) {
          try {
            ControlUI.this.node.step();
          } catch (Exception e2) {
            e2.printStackTrace();
          }
        }
          dui.updateRegs();
          dui.repaint();
          if (elfData != null && sourceViewer != null
              && sourceViewer.isVisible()) {
            int pc = ControlUI.this.cpu.getPC();
            DebugInfo dbg = elfData.getDebugInfo(pc);
            if (dbg != null) {
              if (ControlUI.this.cpu.getDebug()) {
                System.out.println("looking up $" + Integer.toString(pc, 16) +
                                   " => " + dbg.getFile() + ':' +
                                   dbg.getLine());
              }
              sourceViewer.viewFile(dbg.getPath(), dbg.getFile());
              sourceViewer.viewLine(dbg.getLine());
            }
          }
        stepButton.setEnabled(true);
        }
      };
    stepAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_S);
    stepAction.setEnabled(!cpu.isRunning());
    stepButton.setAction(stepAction);

    createButton("Stack Trace");

    if (elfData != null) {
      createButton("Show Source");
    }
    createButton("Profile Dump");

    // Setup standard actions
    stepButton.registerKeyboardAction(stepAction,
                                      KeyStroke.getKeyStroke(KeyEvent.VK_S,
                                                             InputEvent.CTRL_DOWN_MASK),
                                      JComponent.WHEN_IN_FOCUSED_WINDOW);

    cpu.addSimEventListener(this);

    window.setVisible(true);
  }

  public void setSourceViewer(SourceViewer viewer) {
    sourceViewer = viewer;
  }

  private JButton createButton(String text) {
    JButton jb = new JButton(text);
    jb.addActionListener(this);
    add(jb);
    return jb;
  }

  private void updateCPUPercent() {
    window.setTitle(TITLE + "  CPU On: " + cpu.getCPUPercent() + "%");
  }

  @Override
  public void actionPerformed(ActionEvent ae) {
    String cmd = ae.getActionCommand();
    updateCPUPercent();
    if ("Debug On".equals(cmd)) {
      cpu.setDebug(true);
      ((JButton) ae.getSource()).setText("Debug Off");

    } else if ("Debug Off".equals(cmd)) {
      cpu.setDebug(false);
      ((JButton) ae.getSource()).setText("Debug On");

    } else if ("Run".equals(cmd)) {
      node.start();

    } else if ("Stop".equals(cmd)) {
      node.stop();

    } else if ("Profile Dump".equals(cmd)) {
      if (cpu.getProfiler() != null) {
        cpu.getProfiler().printProfile(System.out);
      } else {
        System.out.println("*** No profiler available");
      }
      //     } else if ("Single Step".equals(cmd)) {
      //       cpu.step();
//       dui.repaint();
    } else if ("Show Source".equals(cmd)) {
      int pc = cpu.getPC();
      if (elfData != null) {
        DebugInfo dbg = elfData.getDebugInfo(pc);
        if (dbg != null) {
          if (cpu.getDebug()) {
            System.out.println("looking up $" + Integer.toString(pc, 16) +
                               " => " + dbg.getFile() + ':' + dbg.getLine());
          }
          if (sourceViewer != null) {
            sourceViewer.viewFile(dbg.getPath(), dbg.getFile());
            sourceViewer.viewLine(dbg.getLine());
          } else {
            System.out.println("File: " + dbg.getFile());
            System.out.println("LineNr: " + dbg.getLine());
          }
        }
      }
    } else if ("Stack Trace".equals(cmd)) {
      cpu.getProfiler().printStackTrace(System.out);
    }
    dui.updateRegs();
  }

  @Override
  public void simChanged(SimEvent event) {
    switch (event.type()) {
      case START, STOP -> java.awt.EventQueue.invokeLater(() -> {
        if (cpu.isRunning()) {
          controlButton.setText("Stop");
          stepAction.setEnabled(false);
        } else {
          controlButton.setText("Run");
          stepAction.setEnabled(true);
        }
      });
    }
  }

  @Override
  public Status getStatus() {
      return status;
  }

  @Override
  public String getName() {
      return name;
  }

  @Override
  public void init(String name, ComponentRegistry registry) {
      this.name = name;
      this.registry = registry;
  }

  @Override
  public void start() {
      setup();
      status = Status.STARTED;
      window.setVisible(true);
  }

  @Override
  public void stop() {
      status = Status.STOPPED;
      window.setVisible(false);
  }
}
