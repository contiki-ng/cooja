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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.jdom2.Element;
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
 * - `rssi_50%` is the signal level at which 50% packets are received. It is
 *   `ln(99)` dB above the receiver's sensitivity
 *   (`Radio.getRxSensitivity()`), which is how the datasheets define
 *   sensitivity: the level at which 1% of the packets are lost.
 * <p>
 * The received signal strength follows the log-distance path loss model:
 * <p>
 *  RSSI_{dBm}(d) = P_tx - PL_1m - PL_t - 10 * \alpha * \log_10 (d) + NormalDistribution(0, \sigma),
 * <p>
 * where:
 * - `P_tx` is read from the radio in dBm (`Radio.getCurrentOutputPower()`), so
 *   this medium needs a radio that reports a real output power.
 * - `PL_1m` is the free-space path loss at one meter, computed from the
 *   transmitter's carrier frequency (`Radio.getFrequency()`) with the Friis
 *   formula.
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

    /*
     * Effective slope over the distances of the scenario: 2 is free space,
     * above that obstructed, below that guided (corridors, tunnels).
     */
    public double PATH_LOSS_EXPONENT = 3.0;

    /* The standard deviation of the AWGN distribution */
    public double AWGN_SIGMA = 3.0;

    /* 
     * This is required to implement the Capture Effect.
     * The co-channel rejection threshold of 802.15.4 radios typically is -3 dB.
     */
    private static final double CO_CHANNEL_REJECTION = -3.0;

    /* Speed of light, m/s */
    private static final double C = 299792458;

    /* Signal in dB above the sensitivity at which the logistic reaches 50%,
       given that it reaches 1% at the sensitivity */
    private static final double PRR_1_PERCENT_MARGIN_DB = Math.log(99.0);

    /* Enable the time-varying component? */
    public boolean ENABLE_TIME_VARIATION;

    /* Bounds for the time-varying component */
    public double TIME_VARIATION_MIN_PL_DB = -10;
    public double TIME_VARIATION_MAX_PL_DB = 10;

    /* How often to update the time-varying path loss value (in simulation time)? */
    private static final double TIME_VARIATION_STEP_SEC = 10.0;

    private long lastTimeVariationUpdatePeriod;

    private final Random random;

    private final HashMap<Index, TimeVaryingEdge> edgesTable = new HashMap<>();

    public LogisticLoss(Simulation simulation) {
        super(simulation);
        random = simulation.getRandomGenerator();
    }

    @Override
    public List<Radio> getNeighbors(Radio radio) {
        List<Radio> neighbors = new ArrayList<>();
        for (Radio dest: getRegisteredRadios()) {
            if (dest != radio
                    && radio.getPosition().getDistanceTo(dest.getPosition()) <= getRange(radio, dest)) {
                neighbors.add(dest);
            }
        }
        return neighbors;
    }

    @Override
    protected RadioConnection createConnections(Radio sender) {
        RadioConnection newConnection = new RadioConnection(sender);

        Position senderPos = sender.getPosition();
        for (Radio recv: getRegisteredRadios()) {
            if (recv == sender) {
                continue;
            }

            /* Fail if radios are on different (but configured) channels */
            var srcChannel = sender.getChannel();
            var dstChannel = recv.getChannel();
            if (channelsDiffer(srcChannel, dstChannel)) {
                /* Add the connection in a dormant state;
                   it will be activated later when the radio will be
                   turned on and switched to the right channel. This behavior
                   is consistent with the case when receiver is turned off. */
                newConnection.addInterfered(recv);

                continue;
            }
            Position recvPos = recv.getPosition();

            double distance = senderPos.getDistanceTo(recvPos);
            if (distance <= getRange(sender, recv)) {
                /* Within transmission range */
                if (ENABLE_TIME_VARIATION) {
                    Index key = new Index(sender.getMote().getID(), recv.getMote().getID());
                    edgesTable.computeIfAbsent(key, k -> new TimeVaryingEdge());
                }

                if (!recv.isRadioOn()) {
                    newConnection.addInterfered(recv);
                    recv.interfereAnyReception();
                } else if (recv.isInterfered()) {
                    /* Was interfered: keep interfering */
                    newConnection.addInterfered(recv);
                } else if (recv.isTransmitting()) {
                    newConnection.addInterfered(recv);
                    recv.interfereAnyReception();
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
        return getRxSuccessProbability(source, dest);
    }

    public double getRxSuccessProbability(Radio source, Radio dest) {
        return receptionProbability(getRSSI(source, dest), dest.getRxSensitivity());
    }

    /*
     * Reception probability at a signal level, for a given receiver
     * sensitivity: 1% at the sensitivity and 50% `PRR_1_PERCENT_MARGIN_DB`
     * above it.
     */
    static double receptionProbability(double rssiDbm, double sensitivityDbm) {
        double x = rssiDbm - sensitivityDbm - PRR_1_PERCENT_MARGIN_DB;
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /* Additive White Gaussian Noise, sampled from the distribution N(0.0, AWGN_SIGMA) */
    private double getAWGN() {
        return random.nextGaussian() * AWGN_SIGMA;
    }

    /*
     * Distance at which the log-distance link budget reaches `sensitivityDbm`.
     */
    static double linkBudgetRange(double pTxDbm, double pathLoss1mDb,
                                  double sensitivityDbm, double pathLossExponent) {
        return Math.pow(10.0, (pTxDbm - pathLoss1mDb - sensitivityDbm)
                              / (10.0 * pathLossExponent));
    }

    /* Free-space path loss at 1 m, from the Friis formula */
    static double pathLoss1mDb(double frequencyMhz) {
        return 20.0 * Math.log10(4.0 * Math.PI * frequencyMhz * 1e6 / C);
    }

    /** @return Reception range from source to dest, in meters */
    private double getRange(Radio source, Radio dest) {
        return getRange(source, dest.getRxSensitivity());
    }

    /** @return Distance a receiver at sensitivityDbm still receives source, in meters */
    public double getRange(Radio source, double sensitivityDbm) {
        double frequency = source.getFrequency();
        if (frequency <= 0) {
            return 0;
        }
        return linkBudgetRange(source.getCurrentOutputPower(), pathLoss1mDb(frequency),
            sensitivityDbm, PATH_LOSS_EXPONENT);
    }

    /** @return Reception probability at a distance, for a receiver at sensitivityDbm */
    public double getReceptionProbability(Radio source, double distance, double sensitivityDbm) {
        double frequency = source.getFrequency();
        if (frequency <= 0) {
            return 0;
        }
        double pathLossDb = pathLoss1mDb(frequency)
            + 10 * PATH_LOSS_EXPONENT * Math.log10(Math.max(distance, 0.01));
        return receptionProbability(source.getCurrentOutputPower() - pathLossDb, sensitivityDbm);
    }

    private double getRSSI(Radio source, Radio dst) {
        double d = source.getPosition().getDistanceTo(dst.getPosition());
        if (d <= 0) {
            /* Do not allow the distance to be zero */
            d = 0.01;
        }

        double frequency = source.getFrequency();
        if (frequency <= 0) {
            return Double.NEGATIVE_INFINITY;
        }

        double pathLossDb = pathLoss1mDb(frequency)
            + 10 * PATH_LOSS_EXPONENT * Math.log10(d);

        /* Add the time-varying component if enabled */
        if (ENABLE_TIME_VARIATION) {
            TimeVaryingEdge edge = edgesTable.get(
                new Index(source.getMote().getID(), dst.getMote().getID()));
            if (edge != null) {
                pathLossDb += edge.getPL();
            }
        }

        return source.getCurrentOutputPower() - pathLossDb + getAWGN();
    }

    private void updateTimeVariationComponent() {
        long period = (long)(simulation.getSimulationTimeMillis() / (1000.0 * TIME_VARIATION_STEP_SEC));

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
                if (channelsDiffer(srcChannel, dstChannel)) {
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
                if (channelsDiffer(srcChannel, intfChannel)) {
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
            if (element.getName().equals("success_ratio_tx")) {
                logger.warn("LogisticLoss parameter \"success_ratio_tx\" was removed: "
                        + "the link budget models the loss");
                continue;
            }

            if (element.getName().equals("transmitting_range")
                    || element.getName().equals("tx_power_reference_dbm")
                    || element.getName().equals("rx_sensitivity")
                    || element.getName().equals("rssi_inflection_point")
                    || element.getName().equals("frequency_mhz")) {
                logger.warn("LogisticLoss parameter \"" + element.getName()
                        + "\" was removed: the range now follows from the link budget");
                continue;
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
