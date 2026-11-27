/*
 * Copyright (c) 2026, Valter Sanches Martins Neto
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
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.mrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.contikios.mrm.ChannelModel.Parameter;
import org.contikios.mrm.ChannelModel.TxPair;
import org.junit.jupiter.api.Test;

/* Reception is decided by the margin over the receiver's sensitivity, which
   the receiver radio supplies; the channel model has no sensitivity of its own. */
class ChannelModelTest {
  private static final double FREQUENCY_MHZ = 2400.0;
  private static final double CC2420_SENSITIVITY_DBM = -95.0;
  private static final double NO_INTERFERENCE = -Double.MAX_VALUE;

  private static TxPair pair(double distanceM, double powerDbm, double sensitivityDbm) {
    return new TxPair() {
      @Override
      public double getFromX() {
        return 0;
      }

      @Override
      public double getFromY() {
        return 0;
      }

      @Override
      public double getToX() {
        return distanceM;
      }

      @Override
      public double getToY() {
        return 0;
      }

      @Override
      public double getTxPower() {
        return powerDbm;
      }

      @Override
      public double getTxGain() {
        return 0;
      }

      @Override
      public double getRxGain() {
        return 0;
      }

      @Override
      public double getRxSensitivity() {
        return sensitivityDbm;
      }

      @Override
      public double getFrequency() {
        return FREQUENCY_MHZ;
      }
    };
  }

  private static double probability(ChannelModel channel, TxPair tx) {
    return channel.getProbability(tx, NO_INTERFERENCE)[0];
  }

  @Test
  void testPathLossAtOneMetre() {
    assertEquals(-40.04, new ChannelModel(null).getFSPL(1.0, FREQUENCY_MHZ), 0.01);
  }

  @Test
  void testPathLossAtOneKilometre() {
    assertEquals(-100.04, new ChannelModel(null).getFSPL(1000.0, FREQUENCY_MHZ), 0.01);
  }

  @Test
  void testNoSignalWithoutCarrier() {
    assertTrue(Double.isInfinite(new ChannelModel(null).getFSPL(100.0, 0.0)));
  }

  @Test
  void testReceptionSaturatesAboveSensitivity() {
    assertEquals(1.0, probability(new ChannelModel(null),
        pair(10.0, 0.0, CC2420_SENSITIVITY_DBM)), 0.01);
  }

  @Test
  void testNoReceptionWellBelowSensitivity() {
    assertEquals(0.0, probability(new ChannelModel(null),
        pair(10_000.0, 0.0, CC2420_SENSITIVITY_DBM)), 0.01);
  }

  @Test
  void testPowerAndSensitivityTradeOneForOne() {
    /* 15 dB more power and 15 dB less sensitive receiver: same margin, same
       probability. */
    ChannelModel channel = new ChannelModel(null);
    assertEquals(probability(channel, pair(1000.0, 0.0, CC2420_SENSITIVITY_DBM)),
        probability(channel, pair(1000.0, -15.0, -110.0)), 1e-9);
  }

  @Test
  void testNoiseFloorCancels() {
    /* The threshold is derived from the sensitivity, so the absolute noise
       floor only matters against it, and it drops out of the difference. */
    ChannelModel channel = new ChannelModel(null);
    TxPair tx = pair(1000.0, 0.0, CC2420_SENSITIVITY_DBM);
    double quiet = probability(channel, tx);
    channel.setParameterValue(Parameter.bg_noise_mean, -80.0);
    assertEquals(quiet, probability(channel, tx), 1e-12);
  }
}
