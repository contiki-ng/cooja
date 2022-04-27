/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
 * Copyright (c) 2020,  alexrayne <alexraynepe196@gmail.com>
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
/**
 * @author alexrayne <alexraynepe196@gmail.com>
 */

package org.contikios.cooja.interfaces;

import org.contikios.cooja.*;

@ClassDescription("Timeline selectable plugin")
public interface TimeSelect {

    // action on select time request
    public void trySelectTime(final long toTime);

    default
    public void performTimePlugins( Simulation sim, Long time ) {
        performTimePluginsExcept(sim, time, this);
    }

    default
    public <N extends Plugin & TimeSelect> 
    void performTimePlugins( Simulation sim, Long time, Class<N> pluginClass) 
    {
        performTimePluginsExcept(sim, time, this, pluginClass);
    }

    static
    public void performTimePluginsExcept( Simulation sim, Long time, TimeSelect exception ) 
    {
        Plugin[] plugins = sim.getCooja().getStartedPlugins();
        for (Plugin p: plugins) {
          if (p == exception)
              continue;
          if (!(p instanceof TimeSelect)) {
            continue;
          }

          /* Select simulation time */
          TimeSelect plugin = (TimeSelect) p;
          plugin.trySelectTime(time);
        }
    }

    static
    public <N extends Plugin & TimeSelect> 
    void performTimePluginsExcept( Simulation sim, Long time, TimeSelect exception
              , Class<N> pluginClass
              ) 
    {
        Plugin[] plugins = sim.getCooja().getStartedPlugins();
        for (Plugin p: plugins) {
          if (p == exception)
              continue;
          if (!(pluginClass.isInstance(p))) {
            continue;
          }

          /* Select simulation time */
          TimeSelect plugin = (TimeSelect) p;
          plugin.trySelectTime(time);
        }
    }
}
