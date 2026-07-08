/*
 * Copyright (c) 2026, RISE Research Institutes of Sweden AB
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
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package se.sics.mspsim.platform.fr5969;

import se.sics.mspsim.chip.Button;
import se.sics.mspsim.config.MSP430FR5969Config;
import se.sics.mspsim.core.FRAMController;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.IOUnit;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.ui.SerialMon;

public class FR5969Node extends GenericNode {

    // LED pin masks (active high on the LaunchPad), consumed by MspExp430Fr5969LED.
    public static final int LED1_RED = (1 << 0);    // P1.0
    public static final int LED2_GREEN = (1 << 6);  // P4.6

    // S1 button on P4.5, active-low. Matches button-sensor.c sensor 0 in the
    // Contiki-NG msp430fr5969 platform.
    public static final int BUTTON_S1_PIN = 5;
    private final Button button;

    public static MSP430FR5969Config makeChipConfig() {
        return new MSP430FR5969Config();
    }

    public FR5969Node(MSP430 cpu) {
        super("FR5969", cpu);
        // Register the Button chip in the constructor, not in setupNode():
        // Cooja's MspMote runs MoteInterfaceHandler.init() (which builds
        // MspButton via cpu.getChip(Button.class)) inside its super-constructor,
        // before node.setup() is called.
        IOPort port4 = cpu.getIOUnit(IOPort.class, "P4");
        button = port4 != null ? new Button("Button", cpu, port4, BUTTON_S1_PIN, false) : null;
    }

    @Override
    public void setupNode() {
        FRAMController fram = cpu.getFRAMController();
        if (fram != null) {
            registry.registerComponent("fram", fram);
        }

        IOUnit usart = cpu.getIOUnit("USCI A0");
        if (usart instanceof USARTSource) {
            registry.registerComponent("serialio", usart);
        }

        if (!config.getPropertyAsBoolean("nogui", true)) {
            setupGUI();
        }
    }

    private void setupGUI() {
        IOUnit usart = cpu.getIOUnit("USCI A0");
        if (usart instanceof USARTSource usartSource) {
            SerialMon serial = new SerialMon(usartSource, "FR5969 Serial Output");
            registry.registerComponent("serialgui", serial);
        }
    }

    @Override
    public int getModeMax() {
        return 0;
    }
}
