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

package org.contikios.cooja.radiomediums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/*
 * The medium has to place a transmitter at the distance the link budget gives
 * it, for the sensitivity of the receiver that is actually listening.
 */
class LogisticLossTest {
  /* Link budget constants, deliberately independent of the medium's fields. */
  private static final double PATH_LOSS_1M_DB = 40.19; /* Friis at 1 m, 2.44 GHz */
  private static final double PATH_LOSS_EXPONENT = 3.0;
  private static final double CC2420_SENSITIVITY_DBM = -95.0;

  @Test
  void testPathLossAtOneMetre() {
    assertEquals(40.19, LogisticLoss.pathLoss1mDb(2440.0), 0.01);
    assertEquals(31.22, LogisticLoss.pathLoss1mDb(868.0), 0.01);
  }

  @Test
  void testRangeAtFullPower() {
    assertEquals(67.1, range(0.0, PATH_LOSS_1M_DB, CC2420_SENSITIVITY_DBM), 0.1);
  }

  @Test
  void testOnePercentReceptionAtSensitivity() {
    assertEquals(0.01, LogisticLoss.receptionProbability(CC2420_SENSITIVITY_DBM,
        CC2420_SENSITIVITY_DBM), 1e-6);
  }

  @Test
  void testHalfReceptionAboveSensitivity() {
    assertEquals(0.5, LogisticLoss.receptionProbability(-90.4, CC2420_SENSITIVITY_DBM), 0.01);
  }

  @Test
  void testNoReceptionBelowSensitivity() {
    assertEquals(0.0, LogisticLoss.receptionProbability(-105.0, CC2420_SENSITIVITY_DBM), 0.001);
  }

  @Test
  void testReceptionOnlyDependsOnTheMarginOverSensitivity() {
    /* Raising the signal and the sensitivity by the same amount must not
       change anything: the model is parameterized by margin, not by level. */
    assertEquals(LogisticLoss.receptionProbability(-90.0, -95.0),
        LogisticLoss.receptionProbability(-80.0, -85.0), 1e-12);
    assertEquals(LogisticLoss.receptionProbability(-99.0, -95.0),
        LogisticLoss.receptionProbability(-109.0, -105.0), 1e-12);
  }

  @Test
  void testRangeBoundaryIsTheOnePercentPoint() {
    /* The cutoff and the curve have to agree, or links die inside the range. */
    double distance = range(0.0, PATH_LOSS_1M_DB, CC2420_SENSITIVITY_DBM);
    double rssi = -PATH_LOSS_1M_DB - 10 * PATH_LOSS_EXPONENT * Math.log10(distance);
    assertEquals(0.01, LogisticLoss.receptionProbability(rssi, CC2420_SENSITIVITY_DBM), 1e-6);
  }

  private static double range(double pTxDbm, double pathLoss1mDb, double sensitivityDbm) {
    return LogisticLoss.linkBudgetRange(pTxDbm, pathLoss1mDb, sensitivityDbm, PATH_LOSS_EXPONENT);
  }
}
