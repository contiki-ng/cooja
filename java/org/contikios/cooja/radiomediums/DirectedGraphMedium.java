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

package org.contikios.cooja.radiomediums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Radio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Directed Graph Radio Medium.
 * <p>
 * Can be used both stand-alone as a radio medium, and 
 * as a basis for other radio medium implementations.
 * <p>
 * The stand-alone radio medium supports propagation delays
 * and per-link transmission success ratio/RSSI.
 * 
 * @see AbstractRadioMedium
 * @author Fredrik Osterlind
 */
@ClassDescription("Directed Graph Radio Medium (DGRM)")
public class DirectedGraphMedium extends AbstractRadioMedium {
  private static final Logger logger = LoggerFactory.getLogger(DirectedGraphMedium.class);

  private final Random random;

  private final ArrayList<Edge> edges = new ArrayList<>();
  private boolean edgesDirty = true;

  /* Used for optimizing lookup time */
  private HashMap<Radio,DGRMDestinationRadio[]> edgesTable = new HashMap<>();

  public DirectedGraphMedium(Simulation simulation) {
    super(simulation);
    random = simulation.getRandomGenerator();

    requestEdgeAnalysis();
  }

  @Override
  public List<Radio> getNeighbors(Radio radio) {
    var destinations = getPotentialDestinations(radio);
    if (destinations != null) {
      return Arrays.stream(destinations).map(d -> d.radio).toList();
    }
    return Collections.emptyList();
  }

  public void addEdge(Edge e) {
    edges.add(e);
    requestEdgeAnalysis();
    radioTransmissionTriggers.trigger(Radio.RadioEvent.UNKNOWN, e);
  }

  public void removeEdge(Edge edge) {
    if (!edges.contains(edge)) {
      logger.error("Cannot remove edge: " + edge);
      return;
    }
    edges.remove(edge);
    requestEdgeAnalysis();
    radioTransmissionTriggers.trigger(Radio.RadioEvent.UNKNOWN, edge);
  }

  public void clearEdges() {
    edges.clear();
    requestEdgeAnalysis();
    radioTransmissionTriggers.trigger(Radio.RadioEvent.UNKNOWN, null);
  }

  public Edge[] getEdges() {
    return edges.toArray(new Edge[0]);
  }

  /**
   * Signal that the configuration changed, and needs to be re-analyzed
   * before used.
   */
  public void requestEdgeAnalysis() {
    edgesDirty = true;
  }

  public boolean needsEdgeAnalysis() {
    return edgesDirty;
  }

  @Override
  public void unregisterRadioInterface(Radio radio, Simulation sim) {
    super.unregisterRadioInterface(radio, sim);

    for (Edge edge: getEdges()) {
      if (edge.source == radio || edge.superDest.radio == radio) {
        removeEdge(edge);
        requestEdgeAnalysis();
      }
    }
  }


  
  @Override
  protected void updateSignalStrengths() {

    /* Reset signal strengths (Default: SS_NOTHING) */
    for (Radio radio : getRegisteredRadios()) {
      radio.setCurrentSignalStrength(getBaseRssi(radio));
    }

    /* Set signal strengths */
    RadioConnection[] conns = getActiveConnections();
    for (RadioConnection conn : conns) {
      /*
       * Set sending RSSI. (Default: SS_STRONG)
       */
      if (conn.getSource().getCurrentSignalStrength() < getSendRssi(conn.getSource())) {
        conn.getSource().setCurrentSignalStrength(getSendRssi(conn.getSource()));
      }
      //Maximum reception signal of all possible radios received
      DGRMDestinationRadio[] dstRadios =  getPotentialDestinations(conn.getSource());
      if (dstRadios == null) continue;
      var activeSourceChannel = conn.getSource().getChannel();
      for (DGRMDestinationRadio dstRadio : dstRadios) {
        int edgeChannel = dstRadio.channel;
        int activeDstChannel = dstRadio.radio.getChannel();
        if (activeSourceChannel != -1) {
          if (edgeChannel != -1 && activeSourceChannel != edgeChannel) {
            continue;
          }
          if (activeDstChannel != -1 && activeSourceChannel != activeDstChannel) {
            continue;
          }
        }

        if (dstRadio.radio.getCurrentSignalStrength() < dstRadio.signal) {
          dstRadio.radio.setCurrentSignalStrength(dstRadio.signal);
        }
        /* We can set this without further checks, as it will only be read
         * if a packet is actually received. In that case it is set to the
         * correct value */
        dstRadio.radio.setLQI(dstRadio.lqi);
      }
    
      
    } 
  }


  /**
   * Generates hash table using current edges for efficient lookup.
   */
  protected void analyzeEdges() {
    HashMap<Radio,ArrayList<DGRMDestinationRadio>> listTable = new HashMap<>();

    /* Fill edge hash table with all edges */
    for (Edge edge: getEdges()) {
      ArrayList<DGRMDestinationRadio> destRadios;
      if (!listTable.containsKey(edge.source)) {
        destRadios = new ArrayList<>();
      } else {
        destRadios = listTable.get(edge.source);
      }

      destRadios.add(edge.superDest);
      listTable.put(edge.source, destRadios);
    }

    /* Convert to arrays */
    HashMap<Radio,DGRMDestinationRadio[]> arrTable = new HashMap<>();
    for (var entry : listTable.entrySet()) {
      arrTable.put(entry.getKey(), entry.getValue().toArray(new DGRMDestinationRadio[0]));
    }

    this.edgesTable = arrTable;
    edgesDirty = false;
    
    /* Radio Medium changed here so notify Observers */
    radioMediumTriggers.trigger(EventTriggers.AddRemove.ADD, null);
  }

  /**
   * Returns all potential destination radios, i.e. all radios "within reach".
   * Does not consider radio channels, transmission success ratios etc.
   *  
   * @param source Source radio
   * @return All potential destination radios
   */
  public DGRMDestinationRadio[] getPotentialDestinations(Radio source) {
    if (edgesDirty) {
      analyzeEdges();
    }
    return edgesTable.get(source);
  }

  @Override
  protected RadioConnection createConnections(Radio source) {
    if (edgesDirty) {
      analyzeEdges();
    }
    if (edgesDirty) {
      logger.error("Error when analyzing edges, aborting new radio connection");
      return new RadioConnection(source);
    }

    /* Create new radio connection using edge hash table */
    RadioConnection newConn = new RadioConnection(source);
    DGRMDestinationRadio[] destinations = getPotentialDestinations(source);
    if (destinations == null) {
      /* No destinations */
      /*logger.info(sendingRadio + ": No dest");*/
      return newConn;
    }

    /*logger.info(source + ": " + destinations.length + " potential destinations");*/
    for (DGRMDestinationRadio dest: destinations) {
    
      if (dest.radio == source) {
        /* Fail: cannot receive our own transmission */
        /*logger.info(source + ": Fail, receiver is sender");*/
        continue;
      }

      int srcc = source.getChannel();
      int dstc = dest.radio.getChannel();
      int edgeChannel = dest.getChannel();

      if (edgeChannel >= 0 && dstc >= 0 && edgeChannel != dstc) {
      	/* Fail: the edge is configured for a different radio channel */
        continue;
      }

      if (srcc >= 0 && dstc >= 0 && srcc != dstc) {
        /* Fail: radios are on different (but configured) channels */
        newConn.addInterfered(dest.radio);
        continue;
      }

      if (!dest.radio.isRadioOn()) {
        /* Fail: radio is off */
        /*logger.info(source + ": Fail, off");*/
        newConn.addInterfered(dest.radio);
        continue;
      }
      
      if (dest.radio.isInterfered()) {
        /* Fail: radio is interfered in another connection */
        /*logger.info(source + ": Fail, interfered");*/
        newConn.addInterfered(dest.radio);
        continue;
      }
     
      if (dest.radio.isReceiving()) {
         /* Fail: radio is already actively receiving */
         /*logger.info(source + ": Fail, receiving");*/
         newConn.addInterfered(dest.radio);

         /* We will also interfere with the other connection */
         dest.radio.interfereAnyReception();
         
         // Find connection, that is sending to that radio
         // and mark the destination as interfered
         for (RadioConnection conn : getActiveConnections()) {
           for (Radio dstRadio : conn.getDestinations()) {
             if (dstRadio == dest.radio) {
               conn.addInterfered(dest.radio);
               break;
             }
           }
         }        
         continue;
      }
            
      if (dest.ratio < 1.0 && random.nextDouble() > dest.ratio) {
    	/* Fail: Reception ratio */
        /*logger.info(source + ": Fail, randomly");*/
        newConn.addInterfered(dest.radio);
        continue;
      }

      /* Success: radio starts receiving */
      /*logger.info(source + ": OK: " + dest.radio);*/
      newConn.addDestination(dest.radio, dest.delay);
    }

    return newConn;
  }

  @Override
  public Collection<Element> getConfigXML() {
    Collection<Element> config = super.getConfigXML();

    for (Edge edge: getEdges()) {
      Element element;
      element = new Element("edge");
      element.addContent(edge.getConfigXML());
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    super.setConfigXML(configXML, visAvailable);
    boolean oldConfig = false;
    for (var element : configXML) {
      if (element.getName().equals("edge")) {
        Collection<Element> edgeConfig = element.getChildren();
        Radio source = null;
        DGRMDestinationRadio dest = null;
        for (Element edgeElement : edgeConfig) {
          if (edgeElement.getName().equals("src")) {
            oldConfig = true;

            /* Old config: lookup source mote */
            for (Mote m : simulation.getMotes()) {
              if (m.toString().equals(edgeElement.getText())) {
                logger.info("Old config: mapping '" + edgeElement.getText() + "' to node " + m.getID());
                source = m.getInterfaces().getRadio();
                break;
              }
            }
          } else if (edgeElement.getName().equals("source")) {
            source = simulation.getMoteWithID(
                    Integer.parseInt(edgeElement.getText())).getInterfaces().getRadio();
          } else if (oldConfig && edgeElement.getName().equals("ratio")) {
            /* Old config: parse link ratio */
            dest.ratio = Double.parseDouble(edgeElement.getText());
          } else if (edgeElement.getName().equals("dest")) {
            if (oldConfig) {
              /* Old config: create simple destination link */
              Radio destRadio = null;
              for (Mote m : simulation.getMotes()) {
                if (m.toString().equals(edgeElement.getText())) {
                  logger.info("Old config: mapping '" + edgeElement.getText() + "' to node " + m.getID());
                  destRadio = m.getInterfaces().getRadio();
                  break;
                }
              }
              dest = new DGRMDestinationRadio(destRadio);
            } else {
              String destClassName = edgeElement.getText().trim();
              if (destClassName.isEmpty()) {
                continue;
              }
              /* Backwards compatibility: se.sics -> org.contikios */
              destClassName = destClassName.replaceFirst("^se\\.sics", "org.contikios");

              var destinationRadioID = edgeElement.getChild("radio");
              var destRadioMote = simulation.getMoteWithID(Integer.parseInt(destinationRadioID.getText().trim()));
              if (destRadioMote == null) {
                throw new IllegalStateException("Can not find mote with id " + destinationRadioID);
              }

              if (DGRMDestinationRadio.class.getName().equals(destClassName)) {
                dest = new DGRMDestinationRadio(destRadioMote.getInterfaces().getRadio());
              } else {
                Class<? extends DGRMDestinationRadio> destClass =
                        simulation.getCooja().tryLoadClass(this, DGRMDestinationRadio.class, destClassName);
                if (destClass == null) {
                  throw new RuntimeException("Could not load class: " + destClassName);
                }
                try {
                  dest = destClass.getDeclaredConstructor(Radio.class).newInstance(destRadioMote.getInterfaces().getRadio());
                } catch (Exception e) {
                  throw new RuntimeException("Could not configure radio destination of type: " + destClassName, e);
                }
              }
              dest.setConfigXML(edgeElement.getChildren(), simulation);
            }
          }
        }
        if (source == null || dest == null) {
          logger.error("Failed loading DGRM links, aborting");
          return false;
        } else {
          addEdge(new Edge(source, dest));
        }
      }
    }
    requestEdgeAnalysis();
    return true;
  }

  public static class Edge {
    public final Radio source;
    public final DGRMDestinationRadio superDest;

    public Edge(Radio source, DGRMDestinationRadio dest) {
      this.source = source;
      this.superDest = dest;
    }

    private Collection<Element> getConfigXML() {
      ArrayList<Element> config = new ArrayList<>();
      Element element;

      element = new Element("source");
      element.setText(String.valueOf(source.getMote().getID()));
      config.add(element);

      element = new Element("dest");
      element.setText(superDest.getClass().getName());
      Collection<Element> destConfig = superDest.getConfigXML();
      if (destConfig != null) {
        element.addContent(destConfig);
        config.add(element);
      }

      return config;
    }
  }
}
