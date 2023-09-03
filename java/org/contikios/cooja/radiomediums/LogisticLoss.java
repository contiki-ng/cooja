/*
 * Copyright (c) 2018-2019, University of Bristol
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Map;
import org.jdom2.Element;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LogisticLoss radio medium aims to be more realistic as the UDGM radio medium
 * while remaining as easily usable.
 * <p>
 * It takes its name from the fact that the logistic function (shaped as a sigmoid) 
 * is used to model the packet reception probability based on RSSI.
 * <p>
 * Features:
 * - Models a non-linear relationship between signal level packet reception probability
 * - Models the signal level as a function of distance following a standard formula
 *   from the RF propagation theory
 * - Adds a random level of noise (AWGN) to the signal level of each packet
 * - Multiple Configurable parameters
 * - Visualization similar to UDGM visualization
 * <p>
 * This Cooja plugin uses the logistic function to model the PRR-RSSI relationship:
 * <p>
 *   PRR(rssi) =  1.0 / (1 + exp(-(rssi - rssi_50%))),
 * <p>
 * where:
 * - `rssi` is the transmit-power minus the path loss.
 * - `rssi_50%` is the signal level at which 50% packets are received;
 * <p>
 * To model the path loss PL_{dBm}(d) this plugin uses the log-distance path loss model:
 * <p>
 *  PL_{dBm}(d) = PL_0 + PL_t + 10 * \alpha * \log_10 (d / d_0) + NormalDistribution(0, \sigma),
 * <p>
 * where:
 * - `d_0` is the transmission range in meters;
 * - `PL_0` is the loss at `d_0` (i.e. the Rx sensitivity, by default equal
 *    to `-100` dBm as on the TI CC2650 System-on-Chip for IEEE 802.15.4 packets)
 * - `PL_t` is the time-varying component of the path loss (by default, zero)
 * - `\alpha` is the path loss exponent;
 * - `\sigma` is the standard deviation of the Additive White Gaussian Noise.
 * <p>
 * The default value of `\alpha` (the path loss exponent) is 3.0 and the default
 * value of `\sigma` is 3.0 as well, both of which approximately correspond to
 * "indoors, 2.4 GHz frequency" according to RF propagation theory.
 * <p>
 * If the time-varying behavior is enabled, the value of `PL_t` is changing over time.
 * The change is within bounds `[TVPL_{min}, TVPL_{max}]`. The evolution is done in discrete steps.
 * At the time `t`, the `PL_t` is updated as:
 * <p>
 *  PL_t = bound(PL_{t-1} + r),
 * <p>
 * where `r` is a small random value, and `bound(pl) = min(MAX_PL, max(MIN_PL, pl))`,
 * and `MIN_PL` and `MAX_PL` are time minimum and maximum values of the time-varying path loss.
 *
 * @see UDGM
 * @author Atis Elsts
 */
@ClassDescription("LogisticLoss Medium")
public class LogisticLoss extends AbstractRadioMedium {
    private static final Logger logger = LoggerFactory.getLogger(LogisticLoss.class);

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
    private static final double CO_CHANNEL_REJECTION = -3.0;

    /*
     * The transmission power.
     * TODO: figure out how to getCurrentOutputPowerIndicator() to dBm use that.
     */
    public static final double DEFAULT_TX_POWER_DBM = 0.0;

    /* Enable the time-varying component? */
    public boolean ENABLE_TIME_VARIATION;

    /* Bounds for the time-varying component */
    public double TIME_VARIATION_MIN_PL_DB = -10;
    public double TIME_VARIATION_MAX_PL_DB = 10;

    /* How often to update the time-varying path loss value (in simulation time)? */
    private static final double TIME_VARIATION_STEP_SEC = 10.0;

    private long lastTimeVariationUpdatePeriod;

    private final DirectedGraphMedium dgrm; /* Used only for efficient destination lookup */

    private final Random random;

    private final HashMap<Index, TimeVaryingEdge> edgesTable = new HashMap<>();

    public LogisticLoss(Simulation simulation) {
        super(simulation);
        random = simulation.getRandomGenerator();
        dgrm = new DirectedGraphMedium(simulation) {
                @Override
                protected void analyzeEdges() {
                    /* Create edges according to distances.
                     * XXX May be slow for mobile networks */
                    clearEdges();
                    /* XXX: do not remove the time-varying edges to preserve their evolution */

                    for (Radio source: LogisticLoss.this.getRegisteredRadios()) {
                        Position sourcePos = source.getPosition();
                        int sourceID = source.getMote().getID();
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

                                if (ENABLE_TIME_VARIATION) {
                                    int destID = dest.getMote().getID();
                                    if (sourceID < destID) {
                                        Index key = new Index(sourceID, destID);
                                        if (!edgesTable.containsKey(key)) {
                                            edgesTable.put(key, new TimeVaryingEdge());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    super.analyzeEdges();
                }
            };

        /* Register as position observer.
         * If any positions change, re-analyze potential receivers. */
        simulation.getEventCentral().getPositionTriggers().addTrigger(this, (o, m) -> dgrm.requestEdgeAnalysis());
        /* Re-analyze potential receivers if radios are added/removed. */
        simulation.getMoteTriggers().addTrigger(this, (o, m) -> dgrm.requestEdgeAnalysis());

        dgrm.requestEdgeAnalysis();
    }

    @Override
    public List<Radio> getNeighbors(Radio radio) {
        return dgrm.getNeighbors(radio);
    }

    @Override
    protected RadioConnection createConnections(Radio sender) {
        RadioConnection newConnection = new RadioConnection(sender);

        /* Fail radio transmission randomly - no radios will hear this transmission */
        if (getTxSuccessProbability() < 1.0 && random.nextDouble() > getTxSuccessProbability()) {
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
            var srcChannel = sender.getChannel();
            var dstChannel = recv.getChannel();
            if (srcChannel >= 0 && dstChannel >= 0 && srcChannel != dstChannel) {
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
                         * XXX: this is a simplified check. Rather than looking at all N potential senders,
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
        return getTxSuccessProbability() * getRxSuccessProbability(source, dest);
    }
    public double getTxSuccessProbability() {
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

        /* Add the time-varying component if enabled */
        if (ENABLE_TIME_VARIATION) {
            Index key = new Index(source.getMote().getID(), dst.getMote().getID());
            TimeVaryingEdge e = edgesTable.get(key);
            if (e != null) {
                path_loss_dbm += e.getPL();
            } else {
                logger.warn("No edge between " + source.getMote().getID() + " and " + dst.getMote().getID());
            }
        }

        return DEFAULT_TX_POWER_DBM - path_loss_dbm + getAWGN();
    }

    private void updateTimeVariationComponent() {
        long period = (long)(simulation.getSimulationTimeMillis() / (1000.0 * TIME_VARIATION_STEP_SEC));

        if (dgrm.needsEdgeAnalysis()) {
            dgrm.analyzeEdges();
        }

        while (period > lastTimeVariationUpdatePeriod) {
            for (Map.Entry<Index, TimeVaryingEdge> entry : edgesTable.entrySet()) {
                entry.getValue().evolve();
            }
            /* update the time state */
            lastTimeVariationUpdatePeriod += 1;
        }
    }

    @Override
    protected void updateSignalStrengths() {
        /* Override: uses distance as signal strength factor */

        if(ENABLE_TIME_VARIATION) {
            updateTimeVariationComponent();
        }
    
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
            var srcChannel = conn.getSource().getChannel();
            for (Radio dstRadio : conn.getDestinations()) {
                var dstChannel = dstRadio.getChannel();
                if (srcChannel >= 0 && dstChannel >= 0 && srcChannel != dstChannel) {
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
            var srcChannel = conn.getSource().getChannel();
            for (Radio intfRadio : conn.getInterfered()) {
                var intfChannel = intfRadio.getChannel();
                if (srcChannel >= 0 && intfChannel >= 0 && srcChannel != intfChannel) {
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

    @Override
    public Collection<Element> getConfigXML() {
        Collection<Element> config = super.getConfigXML();
        Element element;

        /* Transmitting range */
        element = new Element("transmitting_range");
        element.setText(Double.toString(TRANSMITTING_RANGE));
        config.add(element);

        /* Transmission success probability */
        element = new Element("success_ratio_tx");
        element.setText(String.valueOf(SUCCESS_RATIO_TX));
        config.add(element);

        /* Rx sensitivity */
        element = new Element("rx_sensitivity");
        element.setText(String.valueOf(RX_SENSITIVITY_DBM));
        config.add(element);

        /* RSSI inflection point */
        element = new Element("rssi_inflection_point");
        element.setText(String.valueOf(RSSI_INFLECTION_POINT_DBM));
        config.add(element);

        /* Path loss exponent */
        element = new Element("path_loss_exponent");
        element.setText(String.valueOf(PATH_LOSS_EXPONENT));
        config.add(element);

        /* AWGN sigma */
        element = new Element("awgn_sigma");
        element.setText(String.valueOf(AWGN_SIGMA));
        config.add(element);

        /* Time variation enabled? */
        element = new Element("enable_time_variation");
        element.setText(String.valueOf(ENABLE_TIME_VARIATION));
        config.add(element);

        if(ENABLE_TIME_VARIATION) {
            /* Time-variable path loss bounds */
            element = new Element("time_variation_min_pl_db");
            element.setText(String.valueOf(TIME_VARIATION_MIN_PL_DB));
            config.add(element);
            element = new Element("time_variation_max_pl_db");
            element.setText(String.valueOf(TIME_VARIATION_MAX_PL_DB));
            config.add(element);
        }

        return config;
    }

    @Override
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

            if (element.getName().equals("enable_time_variation")) {
                 ENABLE_TIME_VARIATION = Boolean.parseBoolean(element.getText());
            }

            if (element.getName().equals("time_variation_min_pl_db")) {
                 TIME_VARIATION_MIN_PL_DB = Double.parseDouble(element.getText());
            }

            if (element.getName().equals("time_variation_max_pl_db")) {
                 TIME_VARIATION_MAX_PL_DB = Double.parseDouble(element.getText());
            }
        }
        return true;
    }

    // Invariant: x <= y
    private static class Index {
        private final int x;
        private final int y;

        Index(int a, int b) {
            if(a <= b) {
                this.x = a;
                this.y = b;
            } else {
                this.x = b;
                this.y = a;
            }
        }

        @Override
        public int hashCode() {
            return this.x ^ this.y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Index other = (Index) obj;
            if (x != other.x)
                return false;
          return y == other.y;
        }
    }

    private class TimeVaryingEdge {
        /* The current value of the time-varying */
        private double timeVariationPlDb;

        TimeVaryingEdge() {
            timeVariationPlDb = 0.0;
        }

        void evolve() {
            /* evolve the value */
            timeVariationPlDb += random.nextDouble() - 0.5;
            /* bound the value */
            if (timeVariationPlDb < TIME_VARIATION_MIN_PL_DB) {
                timeVariationPlDb = TIME_VARIATION_MIN_PL_DB;
            } else if (timeVariationPlDb > TIME_VARIATION_MAX_PL_DB) {
                timeVariationPlDb = TIME_VARIATION_MAX_PL_DB;
            }
        }

        double getPL() {
            return timeVariationPlDb;
        }
    }
}
