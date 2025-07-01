/*
 * Copyright (c) 2007, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.motes;

import java.awt.Container;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.ProjectConfig;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.ApplicationLED;
import org.contikios.cooja.interfaces.ApplicationRadio;
import org.contikios.cooja.interfaces.ApplicationSerialPort;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.MoteID;
import org.contikios.cooja.interfaces.Position;
import org.jdom2.Element;

@ClassDescription("Application Mote Type")
public abstract class AbstractApplicationMoteType implements MoteType {
  /** Description of the mote type. */
  protected String description;
  /** Identifier of the mote type. */
  protected String identifier;

  /** Project configuration of the mote type. */
  protected ProjectConfig myConfig;

  /** MoteInterface classes used by the mote type. */
  protected final ArrayList<Class<? extends MoteInterface>> moteInterfaceClasses = new ArrayList<>();

  /** Random generator for generating a unique mote ID. */
  private static final Random rnd = new Random();

  public AbstractApplicationMoteType(boolean useDefaultMoteInterfaceClasses) {
    super();
    String testID = "";
    boolean available = false;
    while (!available) {
      testID = getMoteTypeIdentifierPrefix() + rnd.nextInt(1000000000);
      available = !Cooja.usedMoteTypeIDs.contains(testID);
      // FIXME: add check that the library name is not already used.
    }
    identifier = testID;
    if (useDefaultMoteInterfaceClasses) {
      moteInterfaceClasses.addAll(List.of(SimpleMoteID.class, Position.class, ApplicationSerialPort.class,
              ApplicationRadio.class, ApplicationLED.class, Mote2MoteRelations.class, MoteAttributes.class));
    }
  }

  /** Returns the mote type identifier prefix. */
  public String getMoteTypeIdentifierPrefix() {
    return "apptype";
  }

  @Override
  public boolean configureAndInit(Container parentContainer, Simulation simulation, boolean visAvailable)
  throws MoteTypeCreationException {
    if (description == null) {
      description = "Application Mote Type #" + identifier;
    }
    return true;
  }

  @Override
  public String getIdentifier() {
    return identifier;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public List<Class<? extends MoteInterface>> getMoteInterfaceClasses() {
    return moteInterfaceClasses;
  }

  @Override
  public JComponent getTypeVisualizer() {
    StringBuilder sb = new StringBuilder();
    // Identifier
    sb.append("<html><table><tr><td>Identifier</td><td>")
    .append(getIdentifier()).append("</td></tr>");

    // Description
    sb.append("<tr><td>Description</td><td>")
    .append(getDescription()).append("</td></tr>");

    sb.append("<tr><td valign=\"top\">Mote interface</td><td>");
    for (Class<? extends MoteInterface> moteInterface : moteInterfaceClasses) {
      sb.append(moteInterface.getSimpleName()).append("<br>");
    }
    sb.append("</td></tr>");

    JLabel label = new JLabel(sb.append("</table></html>").toString());
    label.setVerticalTextPosition(JLabel.TOP);
    return label;
  }

  @Override
  public ProjectConfig getConfig() {
    return myConfig;
  }

  @Override
  public Collection<Element> getConfigXML(Simulation simulation) {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    element = new Element("identifier");
    element.setText(getIdentifier());
    config.add(element);

    element = new Element("description");
    element.setText(getDescription());
    config.add(element);

    return config;
  }

  @Override
  public boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable)
  throws MoteTypeCreationException {
    for (Element element : configXML) {
      String name = element.getName();
      if (name.equals("identifier")) {
        identifier = element.getText();
      } else if (name.equals("description")) {
        description = element.getText();
      }
    }
    return configureAndInit(Cooja.getTopParentContainer(), simulation, Cooja.isVisualized());
  }

  public static class SimpleMoteID extends MoteID<Mote> {
    public SimpleMoteID(Mote mote) {
      super(mote);
    }
  }
}
