/*
 * Copyright (c) 2018, University of Bristol
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
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.SimEventCentral.MoteCountListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.skins.LogisticLossVisualizerSkin;

/**
 * The LogisticLoss radio medium aims to be more realistic as the UDGM radio medium
 * while remaining as easily usable.
 *
 * It takes its name from the fact that the logistic function (shaped as a sigmoid) 
 * is used to model the packet reception probability based on RSSI.
 * 
 * Features:
 * - Models a non-linear relationship between signal level packet reception probability
 * - Models the signal level as a function of distance following a standard formula
 *   from the RF propagation theory
 * - Adds a random level of noise (AWGN) to the signal level of each packet
 * - Multiple Configurable parameters
 * - Visualization similar to UDGM visualization
 *
 * This Cooja plugin uses the logistic function to model the PRR-RSSI relationship:
 *
 *   PRR(rssi) =  1.0 / (1 + exp(-(rssi - rssi_50%))),
 * 
 * where:
 * - `rssi` is the transmit power minus the path loss.
 * - `rssi_50%` is the signal level at which 50% packets are received;
 * 
 * To model the path loss PL_{dBm}(d) this plugin uses the log-distance path loss model:
 *
 *  PL_{dBm}(d) = PL_0 + 10 * \alpha * \log_10 (d / d_0) + NormalDistribution(0, \sigma),
 *
 * where:
 * - `d_0` is the transmission range in meters;
 * - `PL_0` is the loss at `d_0` (i.e. the Rx sensitivity, by default equal
 *    to `-100` dBm as on the TI CC2650 System-on-Chip for IEEE 802.15.4 packets)
 * - `\alpha` is the path loss exponent;
 * - `\sigma` is the standard deviation of the Additive White Gaussian Noise.
 * 
 * The default value of `\alpha` (the path loss exponent) is 3.0 and the default
 * value of `\sigma` is 3.0 as well, both of which approximately correspond to
 * "indoors, 2.4 GHz frequency" according to RF propagation theory.
 *
 * @see UDGM
 * @author Atis Elsts
 */
@ClassDescription("LogisticLoss Medium")
public class LogisticLoss extends AbstractRadioMedium {
    private static Logger logger = Logger.getLogger(LogisticLoss.class);

    /* Success ratio of TX. If this fails, no radios receive the packet */
    public double SUCCESS_RATIO_TX = 1.0;

    /* signal strength in dBm with close to 0% PRR */
    public double RX_SENSITIVITY_DBM = -100.0;

    /*
     * This is the point where the second-order derivative of the logistic loss function becomes negative.
     *  It is also the point where 50% of packets with this signal strength are received.
     */
    public double RSSI_INFLECTION_POINT_DBM = -92.0;

    /* At this distance (in meters), the RSSI is equal to the RX_SENSITIVITY_DBM */
    public double TRANSMITTING_RANGE = 20.0;
    public double INTERFERENCE_RANGE = TRANSMITTING_RANGE;

    /* For the log-distance model, indoors, 2.4 GHz */
    public double PATH_LOSS_EXPONENT = 3.0;

    /* The standard deviation of the AWGN distribution */
    public double AWGN_SIGMA = 3.0;

    /* 
     * This is required to implement the Capture Effect.
     * The co-channel rejection threshold of 802.15.4 radios typically is -3 dB.
     */
    private final double CO_CHANNEL_REJECTION = -3.0;

    /*
     * The transmission power.
     * TODO: figure out how to getCurrentOutputPowerIndicator() to dBm use that.
     */
    public final double DEFAULT_TX_POWER_DBM = 0.0;


    private DirectedGraphMedium dgrm; /* Used only for efficient destination lookup */

    private Random random = null;

    public LogisticLoss(Simulation simulation) {
        super(simulation);
        random = simulation.getRandomGenerator();
        dgrm = new DirectedGraphMedium() {
                protected void analyzeEdges() {
                    /* Create edges according to distances.
                     * XXX May be slow for mobile networks */
                    clearEdges();
                    for (Radio source: LogisticLoss.this.getRegisteredRadios()) {
                        Position sourcePos = source.getPosition();
                        for (Radio dest: LogisticLoss.this.getRegisteredRadios()) {
                            Position destPos = dest.getPosition();
                            /* Ignore ourselves */
                            if (source == dest) {
                                continue;
                            }
                            double distance = sourcePos.getDistanceTo(destPos);
                            if (distance < TRANSMITTING_RANGE) {
                                /* Add potential destination */
                                addEdge(
                                        new DirectedGraphMedium.Edge(source, 
                                                new DGRMDestinationRadio(dest)));
                            }
                        }
                    }
                    super.analyzeEdges();
                }
            };

        /* Register as position observer.
         * If any positions change, re-analyze potential receivers. */
        final Observer positionObserver = new Observer() {
                public void update(Observable o, Object arg) {
                    dgrm.requestEdgeAnalysis();
                }
            };
        /* Re-analyze potential receivers if radios are added/removed. */
        simulation.getEventCentral().addMoteCountListener(new MoteCountListener() {
                public void moteWasAdded(Mote mote) {
                    mote.getInterfaces().getPosition().addObserver(positionObserver);
                    dgrm.requestEdgeAnalysis();
                }
                public void moteWasRemoved(Mote mote) {
                    mote.getInterfaces().getPosition().deleteObserver(positionObserver);
                    dgrm.requestEdgeAnalysis();
                }
            });
        for (Mote mote: simulation.getMotes()) {
            mote.getInterfaces().getPosition().addObserver(positionObserver);
        }
        dgrm.requestEdgeAnalysis();

        /* Register visualizer skin */
        Visualizer.registerVisualizerSkin(LogisticLossVisualizerSkin.class);
    }

    public void removed() {
        super.removed();

        Visualizer.unregisterVisualizerSkin(LogisticLossVisualizerSkin.class);
    }
  
    public RadioConnection createConnections(Radio sender) {
        RadioConnection newConnection = new RadioConnection(sender);

        /* Fail radio transmission randomly - no radios will hear this transmission */
        if (getTxSuccessProbability(sender) < 1.0 && random.nextDouble() > getTxSuccessProbability(sender)) {
            return newConnection;
        }

        /* Get all potential destination radios */
        DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(sender);
        if (potentialDestinations == null) {
            return newConnection;
        }

        /* Loop through all potential destinations */
        Position senderPos = sender.getPosition();
        for (DestinationRadio dest: potentialDestinations) {
            Radio recv = dest.radio;

            /* Fail if radios are on different (but configured) channels */ 
            if (sender.getChannel() >= 0 &&
                    recv.getChannel() >= 0 &&
                    sender.getChannel() != recv.getChannel()) {

                /* Add the connection in a dormant state;
                   it will be activated later when the radio will be
                   turned on and switched to the right channel. This behavior
                   is consistent with the case when receiver is turned off. */
                newConnection.addInterfered(recv);

                continue;
            }
            Position recvPos = recv.getPosition();

            double distance = senderPos.getDistanceTo(recvPos);
            if (distance <= TRANSMITTING_RANGE) {
                /* Within transmission range */

                if (!recv.isRadioOn()) {
                    newConnection.addInterfered(recv);
                    recv.interfereAnyReception();
                } else if (recv.isInterfered()) {
                    /* Was interfered: keep interfering */
                    newConnection.addInterfered(recv);
                } else if (recv.isTransmitting()) {
                    newConnection.addInterfered(recv);
                } else {
                    boolean receiveNewOk = random.nextDouble() < getRxSuccessProbability(sender, recv);

                    if (recv.isReceiving()) {
                        /*
                         * Compare new and old and decide whether to interfere.
                         * XXX: this is a simplifiedcheck. Rather than looking at all N potential senders,
                         * it looks at just this and the strongest one of the previous transmissions
                         * (since updateSignalStrengths() updates the signal strength iff the previous one is weaker)
                        */

                        double oldSignal = recv.getCurrentSignalStrength();
                        double newSignal = getRSSI(sender, recv);

                        boolean doInterfereOld;

                        if(oldSignal + CO_CHANNEL_REJECTION > newSignal) {
                            /* keep the old transmission */
                            doInterfereOld = false;
                            receiveNewOk = false;
                            /* logger.info(sender + ": keep old " + recv); */
                        } else if (newSignal + CO_CHANNEL_REJECTION > oldSignal) {
                            /* keep the new transmission */
                            doInterfereOld = true;
                            /* logger.info(sender + ": keep new " + recv); */
                        } else {
                            /* too equal strengths; none gets through */
                            doInterfereOld = true;
                            receiveNewOk = false;

                            /* logger.info(sender + ": interfere both " + recv); */

                            /* XXX: this will interfere even if later a stronger connections
                             * comes ahead that could override all existing weaker connections! */
                            recv.interfereAnyReception();
                        }

                        if(doInterfereOld) {
                            /* Find all existing connections and interfere them */
                            for (RadioConnection conn : getActiveConnections()) {
                                if (conn.isDestination(recv)) {
                                    conn.addInterfered(recv);
                                }
                            }

                            recv.interfereAnyReception();
                        }
                    }

                    if(receiveNewOk) {
                        /* Success: radio starts receiving */
                        newConnection.addDestination(recv);
                        /* logger.info(sender + ": tx to " + recv); */
                    } else {
                        newConnection.addInterfered(recv);
                        /* logger.info(sender + ": interfere to " + recv); */
                    }
                }
            }
        }

        return newConnection;
    }
  
    public double getSuccessProbability(Radio source, Radio dest) {
        return getTxSuccessProbability(source) * getRxSuccessProbability(source, dest);
    }
    public double getTxSuccessProbability(Radio source) {
        return SUCCESS_RATIO_TX;
    }

    public double getRxSuccessProbability(Radio source, Radio dest) {
        double rssi = getRSSI(source, dest);
        double x = rssi - RSSI_INFLECTION_POINT_DBM;
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /* Additive White Gaussian Noise, sampled from the distribution N(0.0, AWGN_SIGMA) */
    private double getAWGN() {
        return random.nextGaussian() * AWGN_SIGMA;
    }

    private double getRSSI(Radio source, Radio dst) {
        double d = source.getPosition().getDistanceTo(dst.getPosition());
        if (d <= 0) {
            /* Do not allow the distance to be zero */
            d = 0.01;
        }

        /* Using the log-distance formula */
        double path_loss_dbm = -RX_SENSITIVITY_DBM + 10 * PATH_LOSS_EXPONENT * Math.log10(d / TRANSMITTING_RANGE);

        return DEFAULT_TX_POWER_DBM - path_loss_dbm + getAWGN();
    }

    public void updateSignalStrengths() {
        /* Override: uses distance as signal strength factor */
    
        /* Reset signal strengths */
        for (Radio radio : getRegisteredRadios()) {
            radio.setCurrentSignalStrength(getBaseRssi(radio));
        }

        /* Set signal strength to below strong on destinations */
        RadioConnection[] conns = getActiveConnections();
        for (RadioConnection conn : conns) {
            if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
                conn.getSource().setCurrentSignalStrength(SS_STRONG);
            }
            for (Radio dstRadio : conn.getDestinations()) {

                if (conn.getSource().getChannel() >= 0 &&
                        dstRadio.getChannel() >= 0 &&
                        conn.getSource().getChannel() != dstRadio.getChannel()) {
                    continue;
                }

                double rssi = getRSSI(conn.getSource(), dstRadio);
                if (dstRadio.getCurrentSignalStrength() < rssi) {
                    dstRadio.setCurrentSignalStrength(rssi);
                }
            }
        }

        /* Set signal strength to below weak on interfered */
        for (RadioConnection conn : conns) {
            for (Radio intfRadio : conn.getInterfered()) {
                if (conn.getSource().getChannel() >= 0 &&
                        intfRadio.getChannel() >= 0 &&
                        conn.getSource().getChannel() != intfRadio.getChannel()) {
                    continue;
                }

                double rssi = getRSSI(conn.getSource(), intfRadio);
                if (intfRadio.getCurrentSignalStrength() < rssi) {
                    intfRadio.setCurrentSignalStrength(rssi);
                }

                /*
                 * XXX: this should be uncommented if there is a desire to see broken packets
                 * false wakeups in all cases, not just in the case of collision,
                 * as happens at the moment
                 */

                /* 
                if (!intfRadio.isInterfered()) {
                    logger.warn("Radio was not interfered: " + intfRadio);
                    intfRadio.interfereAnyReception();
                }
                */
            }
        }
    }

    public Collection<Element> getConfigXML() {
        Collection<Element> config = super.getConfigXML();
        Element element;

        /* Transmitting range */
        element = new Element("transmitting_range");
        element.setText(Double.toString(TRANSMITTING_RANGE));
        config.add(element);

        /* Transmission success probability */
        element = new Element("success_ratio_tx");
        element.setText("" + SUCCESS_RATIO_TX);
        config.add(element);

        /* Rx sensitivity */
        element = new Element("rx_sensitivity");
        element.setText("" + RX_SENSITIVITY_DBM);
        config.add(element);

        /* RSSI inflection point */
        element = new Element("rssi_inflection_point");
        element.setText("" + RSSI_INFLECTION_POINT_DBM);
        config.add(element);

        /* Path loss exponent */
        element = new Element("path_loss_exponent");
        element.setText("" + PATH_LOSS_EXPONENT);
        config.add(element);

        /* AWGN sigma */
        element = new Element("awgn_sigma");
        element.setText("" + AWGN_SIGMA);
        config.add(element);

        return config;
    }

    public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
        super.setConfigXML(configXML, visAvailable);
        for (Element element : configXML) {
            if (element.getName().equals("transmitting_range")) {
                TRANSMITTING_RANGE = Double.parseDouble(element.getText());
                INTERFERENCE_RANGE = TRANSMITTING_RANGE;
            }

            if (element.getName().equals("success_ratio_tx")) {
                SUCCESS_RATIO_TX = Double.parseDouble(element.getText());
            }

            if (element.getName().equals("rx_sensitivity")) {
                RX_SENSITIVITY_DBM = Double.parseDouble(element.getText());
            }

            if (element.getName().equals("rssi_inflection_point")) {
                RSSI_INFLECTION_POINT_DBM = Double.parseDouble(element.getText());
            }

            if (element.getName().equals("path_loss_exponent")) {
                PATH_LOSS_EXPONENT = Double.parseDouble(element.getText());
            }

            if (element.getName().equals("awgn_sigma")) {
                 AWGN_SIGMA = Double.parseDouble(element.getText());
            }
        }
        return true;
    }
}
