/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;


import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application radio.
 * <p>
 * May be used by Java-based mote to implement radio functionality.
 * Supports radio channels and output power functionality.
 * The mote itself should observe the radio for incoming radio packet data.
 *
 * @author Fredrik Osterlind
 */
public class ApplicationRadio extends Radio implements NoiseSourceRadio, DirectionalAntennaRadio {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRadio.class);

  private final Simulation simulation;
  private final Mote mote;

  private RadioPacket packetFromMote;
  private RadioPacket packetToMote;

  private boolean isTransmitting;
  private boolean isReceiving;
  private boolean isInterfered;

  private static final long transmissionEndTime = 0;

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;
  private long lastEventTime;

  private double signalStrength = -100;
  private int radioChannel = -1;
  private double outputPower; /* typical cc2420 values: -25 <-> 0 dBm */
  private int outputPowerIndicator = 100;

  private int interfered;

  public ApplicationRadio(Mote mote) {
    this.mote = mote;
    this.simulation = mote.getSimulation();
  }

  /* Packet radio support */
  @Override
  public RadioPacket getLastPacketTransmitted() {
    return packetFromMote;
  }

  @Override
  public RadioPacket getLastPacketReceived() {
    return packetToMote;
  }

  @Override
  public void setReceivedPacket(RadioPacket packet) {
    packetToMote = packet;
  }

  /* General radio support */
  @Override
  public void signalReceptionStart() {
    packetToMote = null;
    if (isInterfered() || isReceiving() || isTransmitting()) {
      interfereAnyReception();
      return;
    }

    isReceiving = true;
    lastEventTime = simulation.getSimulationTime();
    lastEvent = RadioEvent.RECEPTION_STARTED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_STARTED, this);
  }

  @Override
  public void signalReceptionEnd() {
    //System.out.println("SignalReceptionEnded for node: " + mote.getID() + " intf:" + interfered);
    if (isInterfered() || packetToMote == null) {
      interfered--;
      if (interfered == 0) isInterfered = false;
      if (interfered < 0) {
        isInterfered = false;
        //logger.warn("Interfered got lower than 0!!!");
        interfered = 0;
      }
      packetToMote = null;
      if (interfered > 0) return;
    }

    isReceiving = false;
    lastEventTime = simulation.getSimulationTime();
    lastEvent = RadioEvent.RECEPTION_FINISHED;
    radioEventTriggers.trigger(RadioEvent.RECEPTION_FINISHED, this);
  }

  @Override
  public boolean isTransmitting() {
    return isTransmitting;
  }

  public long getTransmissionEndTime() {
    return transmissionEndTime;
  }

  @Override
  public boolean isReceiving() {
    return isReceiving;
  }

  @Override
  public int getChannel() {
    return radioChannel;
  }

  @Override
  public Position getPosition() {
    return mote.getInterfaces().getPosition();
  }

  @Override
  public RadioEvent getLastEvent() {
    return lastEvent;
  }

  /* Note: this must be called exactly as many times as the reception ended */
  @Override
  public void interfereAnyReception() {
    interfered++;
    if (!isInterfered()) {
      isInterfered = true;

      lastEvent = RadioEvent.RECEPTION_INTERFERED;
      lastEventTime = simulation.getSimulationTime();
      radioEventTriggers.trigger(RadioEvent.RECEPTION_INTERFERED, this);
    }
  }

  @Override
  public boolean isInterfered() {
    return isInterfered;
  }

  @Override
  public double getCurrentOutputPower() {
    return outputPower;
  }

  @Override
  public int getOutputPowerIndicatorMax() {
    return outputPowerIndicator;
  }

  @Override
  public int getCurrentOutputPowerIndicator() {
    return outputPowerIndicator;
  }

  @Override
  public double getCurrentSignalStrength() {
    return signalStrength;
  }

  @Override
  public void setCurrentSignalStrength(double signalStrength) {
    this.signalStrength = signalStrength;
  }

  /* Application radio support */

  /**
   * Start transmitting given packet.
   *
   * @param packet Packet data
   * @param duration Duration to transmit
   */
  public void startTransmittingPacket(final RadioPacket packet, final long duration) {
    assert simulation.isSimulationThread() : "Method must be called from the simulation thread";
    if (isTransmitting) {
      logger.warn("Already transmitting, aborting new transmission");
      return;
    }

    // Start transmission.
    isTransmitting = true;
    lastEvent = RadioEvent.TRANSMISSION_STARTED;
    lastEventTime = simulation.getSimulationTime();
    radioEventTriggers.trigger(RadioEvent.TRANSMISSION_STARTED, this);

    // Deliver data.
    packetFromMote = packet;
    lastEvent = RadioEvent.PACKET_TRANSMITTED;
    radioEventTriggers.trigger(RadioEvent.PACKET_TRANSMITTED, this);

    // Finish transmission.
    simulation.scheduleEvent(new MoteTimeEvent(mote) {
      @Override
      public void execute(long t) {
        isTransmitting = false;
        lastEvent = RadioEvent.TRANSMISSION_FINISHED;
        lastEventTime = t;
        radioEventTriggers.trigger(RadioEvent.TRANSMISSION_FINISHED, ApplicationRadio.this);
      }
    }, simulation.getSimulationTime() + duration);
  }

  /**
   * @param i New output power indicator
   */
  public void setOutputPowerIndicator(int i) {
    outputPowerIndicator = i;
  }

  /**
   * @param p New output power
   */
  public void setOutputPower(double p) {
    outputPower = p;
  }

  /**
   * @param channel New radio channel
   */
  public void setChannel(int channel) {
    radioChannel = channel;
    lastEvent = RadioEvent.UNKNOWN;
    lastEventTime = simulation.getSimulationTime();
    radioEventTriggers.trigger(RadioEvent.UNKNOWN, this);
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel(new BorderLayout());
    Box box = Box.createVerticalBox();

    final JLabel statusLabel = new JLabel("");
    final JLabel lastEventLabel = new JLabel("");
    final JLabel channelLabel = new JLabel("");
    final JLabel powerLabel = new JLabel("Output power (dBm):");
    final JLabel ssLabel = new JLabel("");
    final JButton updateButton = new JButton("Update SS");

    JComboBox<String> channelMenu = new JComboBox<>(new String[] {
        "ALL",
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
        "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
        "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"
    });
    channelMenu.addActionListener(e -> {
      var m = (JComboBox<?>) e.getSource();
      String s = (String) m.getSelectedItem();
      if (s == null || s.equals("ALL")) {
        setChannel(-1);
      } else {
        setChannel(Integer.parseInt(s));
      }
    });
    var currentChannel = getChannel();
    channelMenu.setSelectedIndex(currentChannel == -1 ? 0 : currentChannel);
    final JFormattedTextField outputPower = new JFormattedTextField(getCurrentOutputPower());
    outputPower.addPropertyChangeListener("value", evt -> setOutputPower(((Number)outputPower.getValue()).doubleValue()));

    box.add(statusLabel);
    box.add(lastEventLabel);
    box.add(ssLabel);
    box.add(updateButton);
    box.add(channelLabel);
    box.add(channelMenu);
    box.add(powerLabel);
    box.add(outputPower);

    updateButton.addActionListener(e -> ssLabel.setText("Signal strength (not auto-updated): "
        + String.format("%1.1f", getCurrentSignalStrength()) + " dBm"));

    final BiConsumer<RadioEvent, Radio> observer = (event, radio) -> {
      if (isTransmitting()) {
        statusLabel.setText("Transmitting");
      } else if (isReceiving()) {
        statusLabel.setText("Receiving");
      } else {
        statusLabel.setText("Listening");
      }

      lastEventLabel.setText("Last event (time=" + lastEventTime + "): " + lastEvent);
      ssLabel.setText("Signal strength (not auto-updated): "
          + String.format("%1.1f", getCurrentSignalStrength()) + " dBm");
      var infoChannel = getChannel();
      channelLabel.setText("Current channel: " + (infoChannel == -1 ? "ALL" : String.valueOf(infoChannel)));
    };
    radioEventTriggers.addTrigger(this, observer);
    observer.accept(null, null);
    panel.add(BorderLayout.NORTH, box);
    return panel;
  }

  @Override
  public Mote getMote() {
    return mote;
  }

  private boolean radioOn = true;
  public void setReceiverOn(boolean radioOn) {
    if (this.radioOn == radioOn) {
      return;
    }

    this.radioOn = radioOn;
    lastEvent = radioOn?RadioEvent.HW_ON:RadioEvent.HW_OFF;
    lastEventTime = simulation.getSimulationTime();
    radioEventTriggers.trigger(radioOn ? RadioEvent.HW_ON : RadioEvent.HW_OFF, this);
  }
  @Override
  public boolean isRadioOn() {
    return radioOn;
  }

  /* Noise source radio support */
  @Override
  public int getNoiseLevel() {
    return noiseSignal;
  }
  @Override
  public void addNoiseLevelListener(NoiseLevelListener l) {
    noiseListeners.add(l);
  }
  @Override
  public void removeNoiseLevelListener(NoiseLevelListener l) {
    noiseListeners.remove(l);
  }

  /* Noise source radio support (app mote API) */
  private int noiseSignal = Integer.MIN_VALUE;
  private final ArrayList<NoiseLevelListener> noiseListeners = new ArrayList<>();
  public void setNoiseLevel(int signal) {
    this.noiseSignal = signal;
    for (NoiseLevelListener l: noiseListeners) {
      l.noiseLevelChanged(this, signal);
    }
  }

  @Override
  public double getDirection() {
    return 0;
  }
  @Override
  public double getRelativeGain(double radians, double distance) {
    /* Simple sinus-based gain */
    return 5.0*Math.sin(5.0*radians)/(0.01*distance);
  }
  @Override
  public void addDirectionChangeListener(DirectionChangeListener l) {
  }
  @Override
  public void removeDirectionChangeListener(DirectionChangeListener l) {
  }

}
