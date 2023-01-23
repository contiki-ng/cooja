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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Plugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.util.StringUtils;
import org.jdom2.Element;


@ClassDescription("Mobility")
@PluginType(PluginType.PType.SIM_PLUGIN)
public class Mobility implements Plugin {
  private static final boolean WRAP_MOVES = true; /* Wrap around loaded moves forever */

  private final VisPlugin frame;

  private Move[] entries; /* All mote moves */
  private final Simulation simulation;
  private long periodStart; /* us */
  private int currentMove;

  private File filePositions;

  private final MessageListUI log;

  public Mobility(Simulation simulation, final Cooja gui) {
    this.simulation = simulation;
    if (!Cooja.isVisualized()) {
      frame = null;
      log = null;
      return;
    }
    frame = new VisPlugin("Mobility", gui);
    log = new MessageListUI();
    log.addPopupMenuItem(null, true); /* Create message list popup */
    frame.add(new JScrollPane(log));

    if (Cooja.isVisualized()) {
      log.addMessage("Mobility plugin started at (ms): " + simulation.getSimulationTimeMillis());
    }
    frame.setSize(500,200);
  }

  @Override
  public JInternalFrame getCooja() {
    return frame;
  }

  @Override
  public void startPlugin() {
    if (filePositions != null) {
      /* Positions were already loaded */
      return;
    }

    JFileChooser fileChooser = new JFileChooser();
    File suggest = new File(Cooja.getExternalToolsSetting("MOBILITY_LAST", "positions.dat"));
    fileChooser.setSelectedFile(suggest);
    fileChooser.setDialogTitle("Select positions file");
    int reply = fileChooser.showOpenDialog(Cooja.getTopParentContainer());
    if (reply == JFileChooser.APPROVE_OPTION) {
      filePositions = fileChooser.getSelectedFile();
      Cooja.setExternalToolsSetting("MOBILITY_LAST", filePositions.getAbsolutePath());
    }
    if (filePositions == null) {
      throw new RuntimeException("No positions file");
    }
    loadPositions();
  }

  private void loadPositions() {
    if (Cooja.isVisualized()) {
      log.addMessage("Parsing position file: " + filePositions);
    }
    String data = StringUtils.loadFromFile(filePositions);
    if (data == null) return;
    // Load move by move.
    ArrayList<Move> entriesList = new ArrayList<>();
    for (String line : data.split("\n")) {
      if (line.trim().isEmpty() || line.startsWith("#")) { // Skip header/metadata.
        continue;
      }

      String[] args = line.split(" ");
      entriesList.add(new Move((long) (Double.parseDouble(args[1]) * 1000.0 * Simulation.MILLISECOND),
              Integer.parseInt(args[0]), // XXX Mote index, not ID.
              Double.parseDouble(args[2]), Double.parseDouble(args[3])));
    }
    entries = entriesList.toArray(new Move[0]);
    if (Cooja.isVisualized()) {
      log.addMessage("Loaded " + entries.length + " positions");
      frame.setTitle("Mobility: " + filePositions.getName());
    }

    // Execute first event - it will reschedule itself.
    simulation.invokeSimulationThread(() -> {
      currentMove = 0;
      periodStart = simulation.getSimulationTime();
      moveNextMoteEvent.execute(Mobility.this.simulation.getSimulationTime());
    });
  }

  private final TimeEvent moveNextMoteEvent = new TimeEvent() {
    @Override
    public void execute(long t) {

      /* Detect early events: reschedule for later */
      if (simulation.getSimulationTime() < entries[currentMove].time + periodStart) {
        simulation.scheduleEvent(this, entries[currentMove].time + periodStart);
        return;
      }

      /* Perform a single move */
      Move move = entries[currentMove];
      if (move.moteIndex < simulation.getMotesCount()) {
        Mote mote = simulation.getMote(move.moteIndex);
        Position pos = mote.getInterfaces().getPosition();
        pos.setCoordinates(move.posX, move.posY, pos.getZCoordinate());
      }

      currentMove++;
      if (currentMove >= entries.length) {
        if (!WRAP_MOVES) {
          return;
        }
        periodStart = simulation.getSimulationTime();
        currentMove = 0;
      }

      /* Reschedule future events */
      simulation.scheduleEvent(this, entries[currentMove].time + periodStart);
    }
  };

  @Override
  public void closePlugin() {
    moveNextMoteEvent.remove();
  }

  record Move(long time, int moteIndex, double posX, double posY) {
    public String toString() {
      return "MOVE: mote " + moteIndex + " -> [" + posX + "," + posY + "] @ " + time/Simulation.MILLISECOND;
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    if (filePositions != null) {
      var element = new Element("positions");
      File file = simulation.getCooja().createPortablePath(filePositions);
      element.setText(file.getPath().replaceAll("\\\\", "/"));
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      String name = element.getName();

      if (name.equals("positions")) {
        filePositions = simulation.getCooja().restorePortablePath(new File(element.getText()));
        loadPositions();
      }
    }

    return true;
  }
}
