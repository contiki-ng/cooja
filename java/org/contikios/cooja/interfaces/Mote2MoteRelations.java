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

package org.contikios.cooja.interfaces;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.ui.ColorUtils;
import org.contikios.cooja.util.EventTriggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mote2Mote Relations is used to show mote relations in simulated
 * applications, typically for debugging or visualization purposes.
 * <p>
 * The interface is write-only: the simulated Contiki has no knowledge of current relations
 * with other motes. The Contiki application can, however, add and remove relations.
 * <p>
 * A Contiki application adds/removes a relation by outputting a simple messages on its log interface,
 * typically via printf()'s of the serial port.
 * <p>
 * Syntax:
 * "&lt;relation identifier #L&gt; &lt;destination mote ID&gt; &lt;add/remove&gt;"
 * <p>
 * Example, add relation between this mote and mote with ID 1
 * "#L 1 1"
 * <p>
 * Example, remove relation between this mote and mote with ID 1
 * "#L 1 0"
 * <p>
 * Example, remove relation between this mote and mote with ID 2
 * "#L 2 0"
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Mote2Mote Relations")
public class Mote2MoteRelations implements MoteInterface {
  private static final Logger logger = LoggerFactory.getLogger(Mote2MoteRelations.class);
  private final EventTriggers<EventTriggers.AddRemove, Integer> relationTriggers = new EventTriggers<>();
  private final Mote mote;

  private final ArrayList<Mote> relations = new ArrayList<>();
  private BiConsumer<EventTriggers.Update, Log.LogDataInfo> logOutputTrigger;

  public Mote2MoteRelations(Mote mote) {
    this.mote = mote;
  }

  @Override
  public void added() {
    logOutputTrigger = (event, data) -> {
      var msg = data.msg();
      if (msg == null) {
        return;
      }

      if (msg.startsWith("DEBUG: ")) {
        msg = msg.substring("DEBUG: ".length());
      }

      if (!msg.startsWith("#L ")) {
        return;
      }

      String colorName = null;
      int colorIndex = msg.indexOf(';');
      if (colorIndex > 0) {
        colorName = msg.substring(colorIndex + 1).trim();
        msg = msg.substring(0, colorIndex).trim();
      }
      var args = msg.split(" ");
      if (args.length != 3) {
        return;
      }

      int destID;
      try {
        destID = Integer.parseInt(args[1]);
      } catch (Exception e) {
        return; // Not a mote id.
      }
      String state = args[2];

      // Locate destination mote.
      // TODO: Use Rime address interface instead of mote ID?
      var destinationMote = mote.getSimulation().getMoteWithID(destID);
      if (destinationMote == null) {
        logger.warn("No destination mote with ID: " + destID);
        return;
      }
      if (destinationMote == mote) {
        return;
      }

      // Change line state.
      var isAdd = state.equals("1");
      if (isAdd) {
        if (relations.contains(destinationMote)) {
          return;
        }
        relations.add(destinationMote);
        mote.getSimulation().addMoteRelation(mote, destinationMote, ColorUtils.decodeColor(colorName));
      } else {
        relations.remove(destinationMote);
        mote.getSimulation().removeMoteRelation(mote, destinationMote);
      }
      relationTriggers.trigger(isAdd ? EventTriggers.AddRemove.ADD : EventTriggers.AddRemove.REMOVE, relations.size());
    };
    /* Observe log interfaces */
    for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
      if (mi instanceof Log log) {
        log.getLogDataTriggers().addTrigger(this, logOutputTrigger);
      }
    }
    // Trigger to remove relations with motes that are removed.
    mote.getSimulation().getMoteTriggers().addTrigger(this, (event, m) -> {
      if (event == EventTriggers.AddRemove.REMOVE && mote != m) {
        if (relations.remove(m)) {
          mote.getSimulation().removeMoteRelation(mote, m);
        }
      }
    });
  }
  
  @Override
  public void removed() {
    /* Stop observing log interfaces */
    for (MoteInterface mi: mote.getInterfaces().getInterfaces()) {
      if (mi instanceof Log log) {
        log.getLogDataTriggers().removeTrigger(this, logOutputTrigger);
      }
    }
    logOutputTrigger = null;

    /* Remove all relations to other motes */
    Mote[] relationsArr = relations.toArray(new Mote[0]);
    for (Mote m: relationsArr) {
      mote.getSimulation().removeMoteRelation(Mote2MoteRelations.this.mote, m);
    }
    relations.clear();
    mote.getSimulation().getMoteTriggers().deleteTriggers(this);
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    final JLabel countLabel = new JLabel();
    countLabel.setText("Mote has " + relations.size() + " mote relations");
    panel.add(countLabel);
    relationTriggers.addTrigger(this, (obs, sz) -> countLabel.setText("Mote has " + sz + " mote relations"));
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    relationTriggers.deleteTriggers(this);
  }
}
