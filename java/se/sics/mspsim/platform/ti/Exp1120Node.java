package se.sics.mspsim.platform.ti;

import se.sics.mspsim.chip.CC1120;
import se.sics.mspsim.config.MSP430f5437Config;
import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.MSP430Config;
import se.sics.mspsim.core.PortListener;
import se.sics.mspsim.core.USARTListener;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.platform.GenericNode;
import se.sics.mspsim.ui.SerialMon;

public class Exp1120Node extends GenericNode implements PortListener, USARTListener {

        public static final int CC1120_GDO0 = 7; /* 1.7 */
        public static final int CC1120_GDO2 = 3; /* 1.3 */

        public static final int CC1120_CHIP_SELECT = (1); // 3.0

        private final IOPort port1;
        private final IOPort port3;
        private final IOPort port4;
        private final IOPort port5;
        private final IOPort port7;
        private final IOPort port8;

        public static final int LEDS_CONF_RED    = (1); // 1.0
        public static final int LEDS_CONF_YELLOW = (1 << 1); // 1.1

        private final CC1120 radio;

        public static MSP430Config makeChipConfig() {
                return new MSP430f5437Config();
        }

        public Exp1120Node(MSP430 cpu) {
          super("Exp1120", cpu);
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
            radio = new CC1120(cpu);
            radio.setGDO0(port1, CC1120_GDO0);
            radio.setGDO2(port1, CC1120_GDO2);
            usart0.addUSARTListener(this);
          } else {
            throw new EmulationException("Error creating Exp1120Node: no USCI B0");
          }

          var usart = cpu.getIOUnit("USCI A1");
          if (usart instanceof USARTSource) {
            registry.registerComponent("serialio", usart);
          }
        }

        @Override
        public void dataReceived(USARTSource source, int data) {
                radio.dataReceived(source, data);

                /* if nothing selected, just write back a random byte to these devs */
                if (!radio.getChipSelect()) {
                        source.byteReceived(0);
                }
        }

        @Override
        public void portWrite(IOPort source, int data) {
                if (source == port3) {
                        // Chip select = active low...
                        radio.setChipSelect((data & CC1120_CHIP_SELECT) == 0);
                }
        }

        @Override
        public void setupNode() {
                if (!config.getPropertyAsBoolean("nogui", true)) {
                  setupGUI();
                }
        }

  public void setupGUI() {
    // Add some windows for listening to serial output.
    if (cpu.getIOUnit("USCI A1") instanceof USARTSource usart) {
      registry.registerComponent("serialgui", new SerialMon(usart, "USCI A1 Port Output"));
    }
  }

    @Override
        public int getModeMax() {
                return 0;
        }
}
