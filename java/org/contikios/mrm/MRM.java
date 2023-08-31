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

import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.DirectionalAntennaRadio;
import org.contikios.cooja.interfaces.NoiseSourceRadio;
import org.contikios.cooja.interfaces.NoiseSourceRadio.NoiseLevelListener;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.mrm.ChannelModel.Parameter;
import org.contikios.mrm.ChannelModel.RadioPair;
import org.contikios.mrm.ChannelModel.TxPair;

/**
 * Multi-path Ray-tracing radio medium (MRM).
 * <p>
 * MRM is an alternative to the simpler radio mediums available in
 * COOJA. It is packet based and uses a 2D ray-tracing approach to approximate
 * signal strength attenuation between simulated radios. Currently, the
 * ray-tracing only supports reflections and refractions through homogeneous
 * obstacles.
 * <p>
 * MRM provides two plugins: one for visualizing the radio environment,
 * and one for configuring the radio medium parameters.
 * <p>
 * Future work includes adding support for diffraction and scattering.
 * <p>
 * MRM supports both noise source radios and directional antenna radios.
 * 
 * @see DirectionalAntennaRadio
 * @see NoiseSourceRadio
 * @author Fredrik Osterlind
 */
@ClassDescription("Multi-path Ray-tracer Medium (MRM)")
public class MRM extends AbstractRadioMedium {
  public final static boolean WITH_NOISE = true; /* NoiseSourceRadio */
  public final static boolean WITH_DIRECTIONAL = true; /* DirectionalAntennaRadio */

  private boolean WITH_CAPTURE_EFFECT;
  private double CAPTURE_EFFECT_THRESHOLD;
  private double CAPTURE_EFFECT_PREAMBLE_DURATION;
  
  private final Random random;
  private final ChannelModel currentChannelModel;

  /**
   * Creates a new Multi-path Ray-tracing Medium (MRM).
   */
  public MRM(Simulation simulation) {
    super(simulation);

    random = simulation.getRandomGenerator();
    currentChannelModel = new ChannelModel(simulation);
    
    WITH_CAPTURE_EFFECT = currentChannelModel.getParameterBooleanValue(ChannelModel.Parameter.captureEffect);
    CAPTURE_EFFECT_THRESHOLD = currentChannelModel.getParameterDoubleValue(ChannelModel.Parameter.captureEffectSignalTreshold);
    CAPTURE_EFFECT_PREAMBLE_DURATION = currentChannelModel.getParameterDoubleValue(ChannelModel.Parameter.captureEffectPreambleDuration);
   
    currentChannelModel.getSettingsTriggers().addTrigger(this, (event, arg) -> {
      WITH_CAPTURE_EFFECT = currentChannelModel.getParameterBooleanValue(Parameter.captureEffect);
      CAPTURE_EFFECT_THRESHOLD = currentChannelModel.getParameterDoubleValue(Parameter.captureEffectSignalTreshold);
      CAPTURE_EFFECT_PREAMBLE_DURATION = currentChannelModel.getParameterDoubleValue(Parameter.captureEffectPreambleDuration);
      // Radio Medium changed here, so notify.
      radioMediumTriggers.trigger(EventTriggers.AddRemove.ADD, null);
    });
    
    if (Cooja.isVisualized()) {
      simulation.getCooja().registerPlugin(AreaViewer.class);
      simulation.getCooja().registerPlugin(FormulaViewer.class);
    }
  }

  @Override
  public void removed() {
    super.removed();

    if (Cooja.isVisualized()) {
      simulation.getCooja().unregisterPlugin(AreaViewer.class);
      simulation.getCooja().unregisterPlugin(FormulaViewer.class);
    }
    currentChannelModel.getSettingsTriggers().deleteTriggers(this);
  }
  
  private final NoiseLevelListener noiseListener = (radio, signal) -> updateSignalStrengths();
  @Override
  public void registerRadioInterface(Radio radio, Simulation sim) {
        super.registerRadioInterface(radio, sim);
        
        /* Radio Medium changed here so notify Observers */
    radioMediumTriggers.trigger(EventTriggers.AddRemove.ADD, radio);
    if (WITH_NOISE && radio instanceof NoiseSourceRadio noiseRadio) {
      noiseRadio.addNoiseLevelListener(noiseListener);
    }
  }
  @Override
  public void unregisterRadioInterface(Radio radio, Simulation sim) {
        super.unregisterRadioInterface(radio, sim);

        /* Radio Medium changed here so notify Observers */
    radioMediumTriggers.trigger(EventTriggers.AddRemove.REMOVE, radio);
    if (WITH_NOISE && radio instanceof NoiseSourceRadio noiseRadio) {
      noiseRadio.removeNoiseLevelListener(noiseListener);
    }
  }
  
  @Override
  protected MRMRadioConnection createConnections(final Radio sender) {
    MRMRadioConnection newConnection = new MRMRadioConnection(sender);

    /* TODO Cache potential destination in DGRM */
    /* Loop through all potential destinations */
    for (final var recv: getRegisteredRadios()) {
      if (sender == recv) {
        continue;
      }

      /* Fail if radios are on different (but configured) channels */
      var srcChannel = sender.getChannel();
      var dstChannel = recv.getChannel();
      if (srcChannel >= 0 && dstChannel >= 0 && srcChannel != dstChannel) {
        newConnection.addInterfered(recv);
        continue;
      }
      /* Calculate receive probability */
      TxPair txPair = new RadioPair() {
        @Override
        public Radio getFromRadio() {
          return sender;
        }
        @Override
        public Radio getToRadio() {
          return recv;
        }
      };
      double[] probData = currentChannelModel.getProbability(
          txPair,
          -Double.MAX_VALUE /* TODO Include interference */
      );

      double recvProb = probData[0];
      double recvSignalStrength = probData[1];
      if (recvProb == 1.0 || random.nextDouble() < recvProb) {
        /* Yes, the receiver *may* receive this packet (it's strong enough) */
        if (!recv.isRadioOn()) {
          newConnection.addInterfered(recv);
          recv.interfereAnyReception();
        } else if (recv.isInterfered()) {
          if (WITH_CAPTURE_EFFECT) {
            /* XXX TODO Implement me:
             * If the new transmission is both stronger and the SFD has not
             * been received by the weaker transmission, then this new
             * transmission should be received.
             *
             * When this is implemented, also implement
             * RadioConnection.java:getReceptionStartTime()
             */

            /* Was interfered: keep interfering */
            newConnection.addInterfered(recv, recvSignalStrength);
          } else {
            /* Was interfered: keep interfering */
            newConnection.addInterfered(recv, recvSignalStrength);
          }
        } else if (recv.isTransmitting()) {
          newConnection.addInterfered(recv, recvSignalStrength);
        } else if (recv.isReceiving()) {
          /* Was already receiving: start interfering.
           * Assuming no continuous preambles checking */

          if (!WITH_CAPTURE_EFFECT) {
            newConnection.addInterfered(recv, recvSignalStrength);
            recv.interfereAnyReception();

            /* Interfere receiver in all other active radio connections */
            for (RadioConnection conn : getActiveConnections()) {
              if (conn.isDestination(recv)) {
                conn.addInterfered(recv);
              }
            }
          } else {
            /* CAPTURE EFFECT */
            double currSignal = recv.getCurrentSignalStrength();
            /* Capture effect: recv-radio is already receiving.
             * Are we strong enough to interfere? */

            if (recvSignalStrength >= currSignal - CAPTURE_EFFECT_THRESHOLD /* config */) {
              /* New signal is strong enough to either interfere with ongoing transmission,
               * or to be received/captured */
              long startTime = newConnection.getReceptionStartTime();
              boolean interfering = (simulation.getSimulationTime()-startTime) >= CAPTURE_EFFECT_PREAMBLE_DURATION; /* us */
              if (interfering) {
                newConnection.addInterfered(recv, recvSignalStrength);
                recv.interfereAnyReception();

                /* Interfere receiver in all other active radio connections */
                for (RadioConnection conn : getActiveConnections()) {
                  if (conn.isDestination(recv)) {
                    conn.addInterfered(recv);
                  }
                }
              } else {
                /* XXX Warning: removing destination from other connections */
                for (RadioConnection conn : getActiveConnections()) {
                  if (conn.isDestination(recv)) {
                    conn.removeDestination(recv);
                  }
                }

                /* Success: radio starts receiving */
                newConnection.addDestination(recv, recvSignalStrength);
              }
            }
          }

        } else {
          /* Success: radio starts receiving */
          newConnection.addDestination(recv, recvSignalStrength);
        }
      } else if (recvSignalStrength > currentChannelModel.getParameterDoubleValue(Parameter.bg_noise_mean)) {
        /* The incoming signal is strong, but strong enough to interfere? */

        if (!WITH_CAPTURE_EFFECT) {
                newConnection.addInterfered(recv, recvSignalStrength);
                recv.interfereAnyReception();
        }
        // TODO: add else-branch and implement new type: newConnection.addNoise()?
        // Currently, this connection will never disturb this radio.
      }

    }

    return newConnection;
  }

  @Override
  protected void updateSignalStrengths() {

    /* Reset: Background noise */
        double background = 
                currentChannelModel.getParameterDoubleValue((Parameter.bg_noise_mean));
    for (Radio radio : getRegisteredRadios()) {
      radio.setCurrentSignalStrength(background);
    }

    /* Active radio connections */
    RadioConnection[] conns = getActiveConnections();
    for (RadioConnection conn : conns) {
      var srcChannel = conn.getSource().getChannel();
      for (Radio dstRadio : conn.getDestinations()) {
        double signalStrength = ((MRMRadioConnection) conn).getDestinationSignalStrength(dstRadio);
        var dstChannel = dstRadio.getChannel();
        if (srcChannel >= 0 && dstChannel >= 0 && srcChannel != dstChannel) {
          continue;
        }
        if (dstRadio.getCurrentSignalStrength() < signalStrength) {
          dstRadio.setCurrentSignalStrength(signalStrength);
        }
      }
    }

    /* Interfering/colliding radio connections */
    for (RadioConnection conn : conns) {
      var srcChannel = conn.getSource().getChannel();
      for (Radio intfRadio : conn.getInterfered()) {
        var intfChannel = intfRadio.getChannel();
        if (srcChannel >= 0 && intfChannel >= 0 && srcChannel != intfChannel) {
          continue;
        }
        double signalStrength = ((MRMRadioConnection) conn).getInterferenceSignalStrength(intfRadio);
        if (intfRadio.getCurrentSignalStrength() < signalStrength) {
                intfRadio.setCurrentSignalStrength(signalStrength);
        }

        if (!intfRadio.isInterfered()) {
                intfRadio.interfereAnyReception();
        }
      }
    }

    /* Check for noise sources */
    if (!WITH_NOISE) return;
    for (final var noiseRadio: getRegisteredRadios()) {
      if (!(noiseRadio instanceof NoiseSourceRadio radio)) {
        continue;
      }
      if (radio.getNoiseLevel() == Integer.MIN_VALUE) {
        continue;
      }

      /* Calculate how noise source affects surrounding radios */
      for (final var affectedRadio : getRegisteredRadios()) {
        if (noiseRadio == affectedRadio) {
          continue;
        }

        /* Update noise levels */
        TxPair txPair = new RadioPair() {
          @Override
          public Radio getFromRadio() {
            return noiseRadio;
          }
          @Override
          public Radio getToRadio() {
            return affectedRadio;
          }
        };
        double[] signalMeanVar = currentChannelModel.getReceivedSignalStrength(txPair);
        double signal = signalMeanVar[0];
        if (signal < background) {
          continue;
        }

        /* TODO Additive signals strengths? */
        /* TODO XXX Consider radio channels */
        /* TODO XXX Potentially interfere even when signal is weaker (~3dB)...
         * (we may alternatively just use the getSINR method...) */
        if (affectedRadio.getCurrentSignalStrength() < signal) {
          affectedRadio.setCurrentSignalStrength(signal);

          /* TODO Interfere with radio connections? */
          if (affectedRadio.isReceiving() && !affectedRadio.isInterfered()) {
            for (RadioConnection conn : conns) {
              if (conn.isDestination(affectedRadio)) {
                /* Intefere with current reception, mark radio as interfered */
                conn.addInterfered(affectedRadio);
                if (!affectedRadio.isInterfered()) {
                  affectedRadio.interfereAnyReception();
                }
              }
            }
          }
        }
      }
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    return currentChannelModel.getConfigXML();
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML,
      boolean visAvailable) {
    return currentChannelModel.setConfigXML(configXML);
  }


  // -- MRM specific methods --

  /**
   * @return Number of registered radios.
   */
  public int getRegisteredRadioCount() {
        /* TODO Expensive operation */
    return getRegisteredRadios().length;
  }

  /**
   * Returns radio at given index.
   *
   * @param index Index of registered radio.
   * @return Radio at given index
   */
  public Radio getRegisteredRadio(int index) {
    return getRegisteredRadios()[index];
  }

  /**
   * Returns the current channel model object, responsible for
   * all probability and transmission calculations.
   *
   * @return Current channel model
   */
  public ChannelModel getChannelModel() {
    return currentChannelModel;
  }

  static class MRMRadioConnection extends RadioConnection {
    private final HashMap<Radio, Double> signalStrengths = new HashMap<>();

    MRMRadioConnection(Radio sourceRadio) {
      super(sourceRadio);
    }

    void addDestination(Radio radio, double signalStrength) {
      signalStrengths.put(radio, signalStrength);
      addDestination(radio);
    }

    void addInterfered(Radio radio, double signalStrength) {
      signalStrengths.put(radio, signalStrength);
      addInterfered(radio);
    }

    double getDestinationSignalStrength(Radio radio) {
        if (signalStrengths.get(radio) == null) {
                return Double.MIN_VALUE;
        }
      return signalStrengths.get(radio);
    }

    double getInterferenceSignalStrength(Radio radio) {
        if (signalStrengths.get(radio) == null) {
                return Double.MIN_VALUE;
        }
      return signalStrengths.get(radio);
    }
  }

}
