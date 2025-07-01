/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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

package org.contikios.mrm;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.mrm.ChannelModel.Parameter;
import org.jdom2.Element;

/**
 * This plugin allows a user to reconfigure current radio channel parameters.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("MRM Settings")
@PluginType(PluginType.PType.SIM_PLUGIN)
@SupportedArguments(radioMediums = {MRM.class})
public class FormulaViewer extends org.contikios.cooja.VisPlugin {
  private final ChannelModel channelModel;

  private static final Dimension labelDimension = new Dimension(240, 20);
  private static final NumberFormat doubleFormat = NumberFormat.getNumberInstance();
  private static final NumberFormat integerFormat = NumberFormat.getIntegerInstance();

  private final ArrayList<JFormattedTextField> allIntegerParameters = new ArrayList<>();
  private final ArrayList<JFormattedTextField> allDoubleParameters = new ArrayList<>();
  private final ArrayList<JCheckBox> allBooleanParameters = new ArrayList<>();

  private final JPanel areaGeneral;
  private final JPanel areaTransmitter;
  private final JPanel areaReceiver;
  private final JPanel areaRayTracer;
  private final JPanel areaShadowing;

  /**
   * Creates a new formula viewer.
   *
   * @param simulationToVisualize Simulation which holds the MRM channel model.
   */
  public FormulaViewer(Simulation simulationToVisualize, Cooja gui) {
    super("MRM Settings", gui);

    var radioMedium = (MRM) simulationToVisualize.getRadioMedium();
    channelModel = radioMedium.getChannelModel();

    // -- Create and add GUI components --
    JPanel allComponents = new JPanel();
    allComponents.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    allComponents.setLayout(new BoxLayout(allComponents, BoxLayout.Y_AXIS));

    JScrollPane scrollPane = new JScrollPane(allComponents);
    scrollPane.setPreferredSize(new Dimension(500,400));
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    setContentPane(scrollPane);

    JPanel collapsableArea;

    // General parameters
    collapsableArea = createCollapsableArea("General parameters", allComponents);
    areaGeneral = collapsableArea;

    addBooleanParameter(
        Parameter.apply_random,
        Parameter.getDescription(Parameter.apply_random),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.apply_random)
    );

    addDoubleParameter(
        Parameter.snr_threshold,
        Parameter.getDescription(Parameter.snr_threshold),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.snr_threshold)
    );

    addDoubleParameter(
        Parameter.bg_noise_mean,
        Parameter.getDescription(Parameter.bg_noise_mean),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.bg_noise_mean)
    );

    addDoubleParameter(
        Parameter.bg_noise_var,
        Parameter.getDescription(Parameter.bg_noise_var),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.bg_noise_var)
    );

    addDoubleParameter(
        Parameter.system_gain_mean,
        Parameter.getDescription(Parameter.system_gain_mean),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.system_gain_mean)
    );

    addDoubleParameter(
        Parameter.system_gain_var,
        Parameter.getDescription(Parameter.system_gain_var),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.system_gain_var)
    );

    addDoubleParameter(
        Parameter.frequency,
        Parameter.getDescription(Parameter.frequency),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.frequency)
    );

    addBooleanParameter(
        Parameter.captureEffect,
        Parameter.getDescription(Parameter.captureEffect),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.captureEffect)
    );

    addDoubleParameter(
        Parameter.captureEffectPreambleDuration,
        Parameter.getDescription(Parameter.captureEffectPreambleDuration),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.captureEffectPreambleDuration)
    );

    addDoubleParameter(
        Parameter.captureEffectSignalTreshold,
        Parameter.getDescription(Parameter.captureEffectSignalTreshold),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.captureEffectSignalTreshold)
    );

    // Transmitter parameters
    collapsableArea = createCollapsableArea("Transmitter parameters", allComponents);
    areaTransmitter = collapsableArea;

    addDoubleParameter(
        Parameter.tx_power,
        Parameter.getDescription(Parameter.tx_power),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.tx_power)
    );

    addBooleanParameter(
        Parameter.tx_with_gain,
        Parameter.getDescription(Parameter.tx_with_gain),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.tx_with_gain)
    );

    // Receiver parameters
    collapsableArea = createCollapsableArea("Receiver parameters", allComponents);
    areaReceiver = collapsableArea;

    addDoubleParameter(
        Parameter.rx_sensitivity,
        Parameter.getDescription(Parameter.rx_sensitivity),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.rx_sensitivity)
    );

    addBooleanParameter(
        Parameter.rx_with_gain,
        Parameter.getDescription(Parameter.rx_with_gain),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.rx_with_gain)
    );

    // Ray Tracer parameters
    collapsableArea = createCollapsableArea("Ray Tracer parameters", allComponents);
    areaRayTracer = collapsableArea;

    addBooleanParameter(
        Parameter.rt_disallow_direct_path,
        Parameter.getDescription(Parameter.rt_disallow_direct_path),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.rt_disallow_direct_path)
    );

    addBooleanParameter(
        Parameter.rt_ignore_non_direct,
        Parameter.getDescription(Parameter.rt_ignore_non_direct),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.rt_ignore_non_direct)
    );

    addBooleanParameter(
        Parameter.rt_fspl_on_total_length,
        Parameter.getDescription(Parameter.rt_fspl_on_total_length),
        collapsableArea,
        channelModel.getParameterBooleanValue(Parameter.rt_fspl_on_total_length)
    );

    addIntegerParameter(
        Parameter.rt_max_rays,
        Parameter.getDescription(Parameter.rt_max_rays),
        collapsableArea,
        channelModel.getParameterIntegerValue(Parameter.rt_max_rays)
    );

    addIntegerParameter(
        Parameter.rt_max_refractions,
        Parameter.getDescription(Parameter.rt_max_refractions),
        collapsableArea,
        channelModel.getParameterIntegerValue(Parameter.rt_max_refractions)
    );

    addIntegerParameter(
        Parameter.rt_max_reflections,
        Parameter.getDescription(Parameter.rt_max_reflections),
        collapsableArea,
        channelModel.getParameterIntegerValue(Parameter.rt_max_reflections)
    );

    addIntegerParameter(
        Parameter.rt_max_diffractions,
        Parameter.getDescription(Parameter.rt_max_diffractions),
        collapsableArea,
        channelModel.getParameterIntegerValue(Parameter.rt_max_diffractions)
    );

/*    addBooleanParameter(
        Parameters.rt_use_scattering,
        Parameter.getDescription(Parameters.rt_use_scattering),
        collapsableArea,
        currentChannelModel.getParameterBooleanValue(Parameters.rt_use_scattering)
    );
*/
    addDoubleParameter(
        Parameter.rt_refrac_coefficient,
        Parameter.getDescription(Parameter.rt_refrac_coefficient),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.rt_refrac_coefficient)
    );

    addDoubleParameter(
        Parameter.rt_reflec_coefficient,
        Parameter.getDescription(Parameter.rt_reflec_coefficient),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.rt_reflec_coefficient)
    );

    addDoubleParameter(
        Parameter.rt_diffr_coefficient,
        Parameter.getDescription(Parameter.rt_diffr_coefficient),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.rt_diffr_coefficient)
    );

/*    addDoubleParameter(
        Parameters.rt_scatt_coefficient,
        Parameter.getDescription(Parameters.rt_scatt_coefficient),
        collapsableArea,
        currentChannelModel.getParameterDoubleValue(Parameters.rt_scatt_coefficient)
    );
*/
    // Shadowing parameters
    collapsableArea = createCollapsableArea("Shadowing parameters", allComponents);
    areaShadowing = collapsableArea;

    addDoubleParameter(
        Parameter.obstacle_attenuation,
        Parameter.getDescription(Parameter.obstacle_attenuation),
        collapsableArea,
        channelModel.getParameterDoubleValue(Parameter.obstacle_attenuation)
    );

    // Add trigger to keep all GUI components synchronized.
    // TODO: param is non-null when a single setting has changed.
    channelModel.getSettingsTriggers().addTrigger(this, (event, param) -> {
      for (var textField : allIntegerParameters) {
        Parameter id = (Parameter) textField.getClientProperty("id");
        textField.setValue(channelModel.getParameterValue(id));
      }
      for (var textField : allDoubleParameters) {
        Parameter id = (Parameter) textField.getClientProperty("id");
        textField.setValue(channelModel.getParameterValue(id));
      }
      for (var checkBox : allBooleanParameters) {
        Parameter id = (Parameter) checkBox.getClientProperty("id");
        checkBox.setSelected(channelModel.getParameterBooleanValue(id));
      }
      repaint();
    });

    // Set initial size etc.
    pack();
    setVisible(true);
  }

  /**
   * Creates a new collapsable area which may be used for holding model parameters.
   * @param title Title of area
   * @param contentPane Where this area should be added
   * @return New empty collapsable area
   */
  private JPanel createCollapsableArea(String title, Container contentPane) {
    // Create panels
    JPanel holdingPanel = new JPanel() {
      @Override
      public Dimension getMaximumSize() {
        return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
      }
    };
    holdingPanel.setLayout(new BoxLayout(holdingPanel, BoxLayout.Y_AXIS));

    final JPanel collapsableArea = new JPanel() {
      @Override
      public Dimension getMaximumSize() {
        return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
      }
    };
    collapsableArea.setLayout(new BoxLayout(collapsableArea, BoxLayout.Y_AXIS));
    collapsableArea.setVisible(false);

    JPanel titlePanel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getMaximumSize() {
        return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
      }
    };

    titlePanel.add(BorderLayout.WEST, new JLabel(title));
    JCheckBox collapseCheckBox = new JCheckBox("show settings", false);
    collapseCheckBox.addActionListener(e -> collapsableArea.setVisible(((JCheckBox) e.getSource()).isSelected()));
    collapsableArea.putClientProperty("my_checkbox", collapseCheckBox);

    titlePanel.add(BorderLayout.EAST, collapseCheckBox);

    collapsableArea.setBorder(
        BorderFactory.createLineBorder(Color.LIGHT_GRAY)
    );
    collapsableArea.setAlignmentY(Component.TOP_ALIGNMENT);

    holdingPanel.add(titlePanel);
    holdingPanel.add(collapsableArea);

    contentPane.add(holdingPanel);
    return collapsableArea;
  }

  /**
   * Creates and adds a panel with a label and a
   * text field which accepts doubles.
   *
   * @param id Identifier of new parameter
   * @param description Description of new parameter
   * @param contentPane Where to add created panel
   * @param initialValue Initial value
   * @return Text field in created panel
   */
  private JFormattedTextField addDoubleParameter(Parameter id, String description, Container contentPane, double initialValue) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setAlignmentY(Component.TOP_ALIGNMENT);
    panel.add(Box.createHorizontalStrut(10));
    JLabel label = new JLabel(description);
    label.setPreferredSize(labelDimension);
    panel.add(label);
    panel.add(Box.createHorizontalGlue());
    JFormattedTextField textField = new JFormattedTextField(doubleFormat);
    panel.add(textField);
    textField.setValue(initialValue);
    textField.setColumns(4);
    textField.putClientProperty("id", id);
    textField.addPropertyChangeListener("value", e -> {
      JFormattedTextField textField1 = (JFormattedTextField) e.getSource();
      Parameter id1 = (Parameter) textField1.getClientProperty("id");
      Double val = ((Number) e.getNewValue()).doubleValue();
      channelModel.setParameterValue(id1, val);
      if (!Parameter.getDefaultValue(id1).equals(val)) {
        textField1.setBackground(Color.LIGHT_GRAY);
        textField1.setToolTipText("Default value: " + Parameter.getDefaultValue(id1));
      } else {
        textField1.setBackground(null);
        textField1.setToolTipText(null);
      }
    });
    if (!Parameter.getDefaultValue(id).equals(initialValue)) {
      textField.setBackground(Color.LIGHT_GRAY);
      textField.setToolTipText("Default value: " + Parameter.getDefaultValue(id));
    } else {
      textField.setBackground(null);
      textField.setToolTipText(null);
    }

    allDoubleParameters.add(textField);

    contentPane.add(panel);

    return textField;
  }

  /**
   * Creates and adds a panel with a label and a
   * text field which accepts integers.
   *
   * @param id Identifier of new parameter
   * @param description Description of new parameter
   * @param contentPane Where to add created panel
   * @param initialValue Initial value
   * @return Text field in created panel
   */
  private JFormattedTextField addIntegerParameter(Parameter id, String description, Container contentPane, int initialValue) {
    JPanel panel = new JPanel();
    JLabel label;
    JFormattedTextField textField;

    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setAlignmentY(Component.TOP_ALIGNMENT);
    panel.add(Box.createHorizontalStrut(10));
    label = new JLabel(description);
    panel.add(label);
    label.setPreferredSize(labelDimension);
    panel.add(Box.createHorizontalGlue());
    textField = new JFormattedTextField(integerFormat);
    panel.add(textField);
    textField.setValue((double) initialValue);
    textField.setColumns(4);
    textField.putClientProperty("id", id);
    textField.addPropertyChangeListener("value", e -> {
      JFormattedTextField textField1 = (JFormattedTextField) e.getSource();
      Parameter id1 = (Parameter) textField1.getClientProperty("id");
      Integer val = ((Number) e.getNewValue()).intValue();
      channelModel.setParameterValue(id1, val);
      if (!Parameter.getDefaultValue(id1).equals(val)) {
        textField1.setBackground(Color.LIGHT_GRAY);
        textField1.setToolTipText("Default value: " + Parameter.getDefaultValue(id1));
      } else {
        textField1.setBackground(null);
        textField1.setToolTipText(null);
      }
    });
    if (!Parameter.getDefaultValue(id).equals(initialValue)) {
      textField.setBackground(Color.LIGHT_GRAY);
      textField.setToolTipText("Default value: " + Parameter.getDefaultValue(id));
    } else {
      textField.setBackground(null);
      textField.setToolTipText(null);
    }

    allIntegerParameters.add(textField);

    contentPane.add(panel);

    return textField;
  }

  /**
   * Creates and adds a panel with a label and a
   * boolean checkbox.
   *
   * @param id Identifier of new parameter
   * @param description Description of new parameter
   * @param contentPane Where to add created panel
   * @param initialValue Initial value
   * @return Checkbox in created panel
   */
  private JCheckBox addBooleanParameter(Parameter id, String description, Container contentPane, boolean initialValue) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setAlignmentY(Component.TOP_ALIGNMENT);
    panel.add(Box.createHorizontalStrut(10));
    JLabel label = new JLabel(description);
    panel.add(label);
    label.setPreferredSize(labelDimension);
    panel.add(Box.createHorizontalGlue());
    JCheckBox checkBox = new JCheckBox();
    panel.add(checkBox);
    checkBox.setSelected(initialValue);
    checkBox.putClientProperty("id", id);
    checkBox.addActionListener(e -> {
      JCheckBox checkBox1 = (JCheckBox) e.getSource();
      Parameter id1 = (Parameter) checkBox1.getClientProperty("id");
      Object val = checkBox1.isSelected();
      channelModel.setParameterValue(id1, val);
      if (!Parameter.getDefaultValue(id1).equals(val)) {
        checkBox1.setText("<");
      } else {
        checkBox1.setText("");
      }
    });
    if (!Parameter.getDefaultValue(id).equals(initialValue)) {
      checkBox.setText("<");
    } else {
      checkBox.setText("");
    }

    allBooleanParameters.add(checkBox);

    contentPane.add(panel);

    return checkBox;
  }

  @Override
  public void closePlugin() {
    channelModel.getSettingsTriggers().deleteTriggers(this);
  }

  /**
   * Returns XML elements representing the current configuration.
   *
   * @see #setConfigXML(Collection)
   * @return XML element collection
   */
  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    element = new Element("show_general");
    element.setText(Boolean.toString(areaGeneral.isVisible()));
    config.add(element);
    element = new Element("show_transmitter");
    element.setText(Boolean.toString(areaTransmitter.isVisible()));
    config.add(element);
    element = new Element("show_receiver");
    element.setText(Boolean.toString(areaReceiver.isVisible()));
    config.add(element);
    element = new Element("show_raytracer");
    element.setText(Boolean.toString(areaRayTracer.isVisible()));
    config.add(element);
    element = new Element("show_shadowing");
    element.setText(Boolean.toString(areaShadowing.isVisible()));
    config.add(element);
    return config;
  }

  /**
   * Sets the configuration depending on the given XML elements.
   *
   * @see #getConfigXML()
   * @param configXML
   *          Config XML elements
   * @return True if config was set successfully, false otherwise
   */
  public boolean setConfigXML(Collection<Element> configXML) {
    for (Element element : configXML) {
      if (element.getName().equals("show_general")) {
        JCheckBox checkBox = (JCheckBox) areaGeneral.getClientProperty("my_checkbox");
        checkBox.setSelected(Boolean.parseBoolean(element.getText()));
        checkBox.getActionListeners()[0].actionPerformed(new ActionEvent(checkBox,
            ActionEvent.ACTION_PERFORMED, ""));
      } else if (element.getName().equals("show_transmitter")) {
        JCheckBox checkBox = (JCheckBox) areaTransmitter.getClientProperty("my_checkbox");
        checkBox.setSelected(Boolean.parseBoolean(element.getText()));
        checkBox.getActionListeners()[0].actionPerformed(new ActionEvent(checkBox,
            ActionEvent.ACTION_PERFORMED, ""));
      } else if (element.getName().equals("show_receiver")) {
        JCheckBox checkBox = (JCheckBox) areaReceiver.getClientProperty("my_checkbox");
        checkBox.setSelected(Boolean.parseBoolean(element.getText()));
        checkBox.getActionListeners()[0].actionPerformed(new ActionEvent(checkBox,
            ActionEvent.ACTION_PERFORMED, ""));
      } else if (element.getName().equals("show_raytracer")) {
        JCheckBox checkBox = (JCheckBox) areaRayTracer.getClientProperty("my_checkbox");
        checkBox.setSelected(Boolean.parseBoolean(element.getText()));
        checkBox.getActionListeners()[0].actionPerformed(new ActionEvent(checkBox,
            ActionEvent.ACTION_PERFORMED, ""));
      } else if (element.getName().equals("show_shadowing")) {
        JCheckBox checkBox = (JCheckBox) areaShadowing.getClientProperty("my_checkbox");
        checkBox.setSelected(Boolean.parseBoolean(element.getText()));
        checkBox.getActionListeners()[0].actionPerformed(new ActionEvent(checkBox,
            ActionEvent.ACTION_PERFORMED, ""));
      }
    }
    return true;
  }

}
