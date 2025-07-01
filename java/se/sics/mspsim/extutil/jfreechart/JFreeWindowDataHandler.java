/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * This file is part of MSPSim.
 *
 * $Id: $
 *
 * -----------------------------------------------------------------
 *
 * JFreeWindowDataHandler
 *
 * Author  : Joakim Eriksson
 * Created : 18 apr 2008
 * Updated : $Date:$
 *           $Revision:$
 */
package se.sics.mspsim.extutil.jfreechart;

import java.util.ArrayList;
import java.util.HashMap;
import org.jfree.data.general.Series;
import se.sics.mspsim.cli.AbstractWindowDataHandler;

/**
 * @author joakim
 *
 */
public abstract class JFreeWindowDataHandler extends AbstractWindowDataHandler {

  private HashMap<Integer,String> labelMap = new HashMap<>();

  protected String getDataSeriesLabel(int index) {
    return labelMap.get(index);
  }

  protected String getDataSeriesLabel(int index, String defaultLabel) {
    var label = getDataSeriesLabel(index);
    return label != null ? label : defaultLabel;
  }

  public abstract int getDataSeriesCount();
  public abstract Series getDataSeries(int index);

  /**
   * Sets a property for a specific data series at the given index.
   * Currently supports setting "label" property to assign a custom label to a data series.
   *
   * @param index The index of the data series
   * @param param The property name to set ("label" is currently supported)
   * @param args Array of arguments for the property, where args[0] contains the property value
   * @see se.sics.mspsim.cli.AbstractWindowDataHandler#setProperty(int, java.lang.String, java.lang.String[])
   */
  @Override
  public void setProperty(int index, String param, String[] args) {
    if ("label".equals(param)) {
      labelMap.put(index, args[0]);
    }
  }

}
