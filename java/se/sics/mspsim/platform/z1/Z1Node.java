package se.sics.mspsim.platform.z1;

import se.sics.mspsim.chip.Button;
import se.sics.mspsim.chip.CC2420;
import se.sics.mspsim.chip.Leds;
import se.sics.mspsim.chip.M25P80;
import se.sics.mspsim.config.MSP430f2617Config;
import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.MSP430;
import se.sics.mspsim.core.MSP430Config;
import se.sics.mspsim.core.PortListener;
import se.sics.mspsim.core.USARTListener;
import se.sics.mspsim.core.USARTSource;
import se.sics.mspsim.core.USCI;
import se.sics.mspsim.platform.GenericFlashNode;
import se.sics.mspsim.ui.SerialMon;

public class Z1Node extends GenericFlashNode<M25P80> implements PortListener, USARTListener {

    public static final int MODE_LEDS_OFF = 0;
    public static final int MODE_LEDS_1 = 1;
    public static final int MODE_LEDS_2 = 2;
    public static final int MODE_LEDS_3 = 3;
    public static final int MODE_MAX = MODE_LEDS_3;

    // Port 2.5 - Button
    public static final int BUTTON_PIN = 5;

    /* P1.2 - Input: FIFOP from CC2420 */
    /* P1.3 - Input: FIFO from CC2420 */
    /* P1.4 - Input: CCA from CC2420 */
    public static final int CC2420_FIFOP = 2;
    public static final int CC2420_FIFO = 3;
    public static final int CC2420_CCA = 4;

    /* P4.1 - Input: SFD from CC2420 */
    /* P4.5 - Output: VREG_EN to CC2420 */
    /* P3.0 - Output: SPI Chip Select (CS_N) */
    public static final int CC2420_SFD = 1;
    public static final int CC2420_VREG = (1 << 5);
    public static final int CC2420_CHIP_SELECT = 0x01;

    public static final int TMP102_PWR_PIN = 0;
    public static final int TMP102_PWR = 1 << TMP102_PWR_PIN;

    public static final int I2C_DATA_PIN = 1;
    public static final int I2C_DATA = 1 << I2C_DATA_PIN;
    public static final int I2C_CLK_PIN = 2;
    public static final int I2C_CLK = 1 << I2C_CLK_PIN;

    public static final int LEDS_CONF_RED    = 0x10;
    public static final int LEDS_CONF_GREEN  = 0x40;
    public static final int LEDS_CONF_BLUE   = 0x20;
    private static final int[] LEDS = { 0xff2020, 0x40ff40, 0x4040ff };

    boolean redLed;
    boolean blueLed;
    boolean greenLed;

    private final Leds leds;
    private final Button button;
    private Z1Gui gui;

    private final CC2420 radio;
//    private TMP102 tmp102;

    public static MSP430Config makeChipConfig() {
        return new MSP430f2617Config();
    }

    // FIXME: replace flash with M25P16.
    // The Z1 platform has a M25P16 chip with 2MB compared to the M25P80 with 1MB, but the chips are compatible.
    public Z1Node(MSP430 cpu, M25P80 flash) {
        super("Z1", cpu, flash);
        setMode(MODE_LEDS_OFF);
        var port1 = cpu.getIOUnit(IOPort.class, "P1");
//        port1.addPortListener(this);
        var port2 = cpu.getIOUnit(IOPort.class, "P2");
//        port2.addPortListener(this);
        var port3 = cpu.getIOUnit(IOPort.class, "P3");
        port3.addPortListener(this);
        var port4 = cpu.getIOUnit(IOPort.class, "P4");
        port4.addPortListener(this);
        var port5 = cpu.getIOUnit(IOPort.class, "P5");
        port5.addPortListener(this);

//        tmp102 = new TMP102(cpu);

        USCI usart0 = cpu.getIOUnit(USCI.class, "USCI B0");
        if (usart0 != null) {
            radio = new CC2420(cpu);
            radio.setCCAPort(port1, CC2420_CCA);
            radio.setFIFOPPort(port1, CC2420_FIFOP);
            radio.setFIFOPort(port1, CC2420_FIFO);
            usart0.addUSARTListener(this);
            radio.setSFDPort(port4, CC2420_SFD);
        } else {
            throw new EmulationException("Could not setup mote - missing USCI B0");
        }

        leds = new Leds(cpu, LEDS);
        button = new Button("Button", cpu, port2, BUTTON_PIN, true);

        var usart = cpu.getIOUnit("USCI A0");
        if (usart instanceof USARTSource) {
            registry.registerComponent("serialio", usart);
        }
    }

    public Leds getLeds() {
        return leds;
    }

    public Button getButton() {
        return button;
    }

    @Override
    public void dataReceived(USARTSource source, int data) {
        // USCI s = (USCI) source;
        radio.dataReceived(source, data);
        flash.dataReceived(source, data);
        /* if nothing selected, just write back a random byte to these devs */
        if (!radio.getChipSelect() && !flash.getChipSelect()) {
            source.byteReceived(0);
        }
    }

    private int lastPort5;
    @Override
    public void portWrite(IOPort source, int data) {
        switch (source.getPort()) {
            case 3 -> radio.setChipSelect((data & CC2420_CHIP_SELECT) == 0); // Chip select = active low...
            case 4 -> {
                radio.setVRegOn((data & CC2420_VREG) != 0);
                //radio.portWrite(source, data);
                flash.portWrite(source, data);
            }
            case 5 -> {
                if ((data & (LEDS_CONF_RED | LEDS_CONF_BLUE | LEDS_CONF_GREEN)) !=
                        (lastPort5 & (LEDS_CONF_RED | LEDS_CONF_BLUE | LEDS_CONF_GREEN))) {
                    redLed = (data & LEDS_CONF_RED) == 0;
                    blueLed = (data & LEDS_CONF_BLUE) == 0;
                    greenLed = (data & LEDS_CONF_GREEN) == 0;
                    leds.setLeds((redLed ? 1 : 0) + (greenLed ? 2 : 0) + (blueLed ? 4 : 0));
                    int newMode = (redLed ? 1 : 0) + (greenLed ? 1 : 0) + (blueLed ? 1 : 0);
                    setMode(newMode);
                }
                if ((data & TMP102_PWR) != (lastPort5 & TMP102_PWR)) {
//                tmp102.setPowerPin((data & TMP102_PWR) != 0);
                }
                if ((data & (I2C_CLK | I2C_DATA)) != (lastPort5 & (I2C_CLK | I2C_DATA))) {
//                tmp102.setI2CPins((data & I2C_CLK) != 0, (data & I2C_DATA) != 0);
                }
                lastPort5 = data;
            }
        }
    }

    @Override
    public void setupNode() {
        super.setupNode();
        if (!config.getPropertyAsBoolean("nogui", true)) {
            setupGUI();
        }
    }

    public void setupGUI() {
        if (gui == null) {
            gui = new Z1Gui(this);
            registry.registerComponent("nodegui", gui);
            // Add some windows for listening to serial output.
            if (cpu.getIOUnit("USCI A0") instanceof USARTSource usart) {
                registry.registerComponent("serialgui", new SerialMon(usart, "USCI A0 Port Output"));
            }
        }
    }

    @Override
    public int getModeMax() {
        return MODE_MAX;
    }
}
