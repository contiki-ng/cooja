/*
 * Copyright (c) 2023, RISE Research Institutes of Sweden AB.
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
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;
import org.contikios.cooja.Cooja;

/**
 * Used to alter the highlight colors in the console logs. Uses the configured
 * log colors of Cooja.
 */
public class LogbackColor extends ForegroundCompositeConverterBase<ILoggingEvent> {
  private static boolean configured;
  private static String ERROR_COLOR;
  private static String WARN_COLOR;
  private static String INFO_COLOR;
  private static String DEFAULT_COLOR;

  @Override
  protected String getForegroundColorCode(ILoggingEvent event) {
    if (Cooja.configuration == null) { // Logging before Cooja initialized.
      return ANSIConstants.DEFAULT_FG;
    }
    if (!configured) {
      ERROR_COLOR = Cooja.configuration.logColors().error();
      WARN_COLOR = Cooja.configuration.logColors().warn();
      INFO_COLOR = Cooja.configuration.logColors().info();
      DEFAULT_COLOR = Cooja.configuration.logColors().fallback();
      configured = true;
    }
    return switch (event.getLevel().toInt()) {
      case Level.ERROR_INT -> ERROR_COLOR;
      case Level.WARN_INT -> WARN_COLOR;
      case Level.INFO_INT -> INFO_COLOR;
      default -> DEFAULT_COLOR;
    };
  }
}
