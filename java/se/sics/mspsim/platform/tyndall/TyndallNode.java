package se.sics.mspsim.platform.tyndall;

import se.sics.mspsim.chip.CC2420;
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

public class TyndallNode extends GenericNode implements PortListener, USARTListener {

    /* P8.2 - Input: FIFOP from CC2420 */
    /* P8.1 - Input: FIFO from CC2420 */
    /* P8.5 - Input: CCA from CC2420 */
    public static final int CC2420_FIFOP = 2;
    public static final int CC2420_FIFO = 1;
    public static final int CC2420_CCA = 5;

    /* P8.6 - Input: SFD from CC2420 */
    /* P8.3 - Output: VREG_EN to CC2420 */
    /* P3.0 - Output: SPI Chip Select (CS_N) */
    public static final int CC2420_SFD = 6;
    public static final int CC2420_VREG = (1 << 3);
    public static final int CC2420_CHIP_SELECT = 0x01;


    private final IOPort port1;
    private final IOPort port3;
    private final IOPort port4;
    private final IOPort port5;
    private final IOPort port7;
    private final IOPort port8;

    public static final int LEDS_CONF_RED    = 0x08;
    public static final int LEDS_CONF_GREEN  = 0x01;
    public static final int LEDS_CONF_YELLOW = 0x01;

    private final CC2420 radio;

    public static MSP430Config makeChipConfig() {
        return new MSP430f5437Config();
    }

    public TyndallNode(MSP430 cpu) {
        super("Tyndall", cpu);
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
            radio = new CC2420(cpu);
            radio.setCCAPort(port8, CC2420_CCA);
            radio.setFIFOPPort(port8, CC2420_FIFOP);
            radio.setFIFOPort(port8, CC2420_FIFO);

            usart0.addUSARTListener(this);
            radio.setSFDPort(port8, CC2420_SFD);
        } else {
            throw new EmulationException("Could not setup tyndall mote - missing USCI B0");
        }

        var usart = cpu.getIOUnit("USCI A0");
        if (usart instanceof USARTSource) {
            registry.registerComponent("serialio", usart);
        }
    }

    @Override
    public void dataReceived(USARTSource source, int data) {
        radio.dataReceived(source, data);
        //flash.dataReceived(source, data);
        /* if nothing selected, just write back a random byte to these devs */
        if (!radio.getChipSelect() /*&& !flash.getChipSelect()*/) {
            source.byteReceived(0);
        }
    }

    @Override
    public void portWrite(IOPort source, int data) {
        if (source == port7) {
            //System.out.println("LEDS GREEN = " + ((data & LEDS_CONF_GREEN) > 0));
            System.out.println("LEDS RED = " + ((data & LEDS_CONF_RED) > 0));
            //System.out.println("LEDS YELLOW = " + ((data & LEDS_CONF_YELLOW) > 0));
        }
        if (source == port3) {
            // Chip select = active low...
            radio.setChipSelect((data & CC2420_CHIP_SELECT) == 0);
        }
        if (source == port4) {
            //radio.portWrite(source, data);
            //flash.portWrite(source, data);
        }
        if (source == port8) {
            radio.setVRegOn((data & CC2420_VREG) != 0);
        }
    }

    @Override
    public void setupNode() {
        // To add flash support: super.setupNode();
        if (!config.getPropertyAsBoolean("nogui", true)) {
            setupGUI();
        }
    }

    public void setupGUI() {
        System.out.println("No gui for Tyndall yet...");
        // Add some windows for listening to serial output.
        if (cpu.getIOUnit("USCI A0") instanceof USARTSource usart) {
            registry.registerComponent("serialgui", new SerialMon(usart, "USCI A0 Port Output"));
        }
    }

    @Override
    public int getModeMax() {
        return 0;
    }
}