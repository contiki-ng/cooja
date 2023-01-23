package se.sics.mspsim.platform.ti;

import se.sics.mspsim.config.CC430f5137Config;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.IOUnit;
import se.sics.mspsim.core.MSP430Config;
import se.sics.mspsim.core.PortListener;
import se.sics.mspsim.core.USARTListener;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.ui.SerialMon;

public class CC430Node extends GenericNode implements PortListener, USARTListener {

    final IOPort port1;
    final IOPort port3;
    final IOPort port4;
    final IOPort port5;
    final IOPort port7;
    final IOPort port8;

    public static MSP430Config makeChipConfig() {
        // TODO: this should be a config for MSP430F5438.
        return new CC430f5137Config();
    }

    public CC430Node() {
        super("CC430", makeCPU(makeChipConfig()));
        port1 = cpu.getIOUnit(IOPort.class, "P1");
        port1.addPortListener(this);
        port3 = cpu.getIOUnit(IOPort.class, "P3");
        port3.addPortListener(this);
        port4 = cpu.getIOUnit(IOPort.class, "P4");
        port4.addPortListener(this);
        port5 = cpu.getIOUnit(IOPort.class, "P5");
        port5.addPortListener(this);
        port7 = cpu.getIOUnit(IOPort.class, "P7");
        port7.addPortListener(this);
        port8 = cpu.getIOUnit(IOPort.class, "P8");
        port8.addPortListener(this);
        if (cpu.getIOUnit("USCI B0") instanceof USARTSource usart0) {
            registry.registerComponent("serialio0", usart0);
        }

        var usart = cpu.getIOUnit("USCI A0");
        if (usart instanceof USARTSource) {
            registry.registerComponent("serialio", usart);
        }
    }

    @Override
    public void dataReceived(USARTSource source, int data) {
    }

    @Override
    public void portWrite(IOPort source, int data) {

    }

    @Override
    public void setupNode() {
        if (!config.getPropertyAsBoolean("nogui", true)) {
            // Add some windows for listening to serial output
            IOUnit usart = cpu.getIOUnit("USCI A0");
            if (usart instanceof USARTSource) {
                SerialMon serial = new SerialMon((USARTSource)usart, "USCI A0 Port Output");
                registry.registerComponent("serialgui", serial);
            }
        }
    }

    @Override
    public int getModeMax() {
        return 0;
    }
}
