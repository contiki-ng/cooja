package se.sics.mspsim.platform.ti;

import se.sics.mspsim.chip.CC2520;
import se.sics.mspsim.config.MSP430f5437Config;
import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.MSP430Config;
import se.sics.mspsim.core.PortListener;
import se.sics.mspsim.core.USARTListener;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.platform.GenericNode;

public class Trxeb2520Node extends GenericNode implements PortListener, USARTListener {

        /* TrxEB + CC2520EM 2.1.
         * Assuming non-default GPIO configuration (see trxeb2520 platform) */
    public static final int CC2520_FIFOP = 4; /* GPIO0 - 1.4 */
    public static final int CC2520_FIFO = 4; /* GPIO1 - 3.4 */
    public static final int CC2520_CCA = 3; /* GPIO3 - 1.3 */
    public static final int CC2520_SFD = 2; /* GPIO4 - 1.2 */

    /* Output: VREG_EN to CC2520 */
    public static final int CC2520_VREG = (1 << 7); /* 1.7 */
    /* Output: SPI Chip Select (CS_N) */
    public static final int CC2520_CHIP_SELECT = (1); /* 3.0 */
    public static final int CC2520_RESET = (1); /* 8.0 */

        private final IOPort port1;
        private final IOPort port3;
        private final IOPort port4;
        private final IOPort port5;
        private final IOPort port7;
        private final IOPort port8;

        private final CC2520 radio;

    public static MSP430Config makeChipConfig() {
        return new MSP430f5437Config();
    }

  public Trxeb2520Node(MSP430 cpu) {
    super("Trxeb2520", cpu);
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
      radio = new CC2520(cpu);
      radio.setGPIO(0, port1, CC2520_FIFOP);
      radio.setGPIO(1, port3, CC2520_FIFO);
      radio.setGPIO(3, port1, CC2520_CCA);
      radio.setGPIO(4, port1, CC2520_SFD);
      usart0.addUSARTListener(this);
    } else {
      throw new EmulationException("Error creating Trxeb2520Node: no USCI B0");
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
                if (source == port1) {
                        radio.setVRegOn((data & CC2520_VREG) != 0);
                } else if (source == port3) {
                        radio.setChipSelect((data & CC2520_CHIP_SELECT) == 0);
                } else if (source == port8) {
//			if ((data & CC2520_RESET) != 0) {
//				radio.notifyReset();
//			}
                }
        }

        @Override
        public void setupNode() {}

        @Override
        public int getModeMax() {
                return 0;
        }
}
