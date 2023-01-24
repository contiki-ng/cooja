package se.sics.mspsim.platform.sky;
import se.sics.mspsim.chip.Button;
import se.sics.mspsim.chip.ExternalFlash;
import se.sics.mspsim.chip.Leds;
import se.sics.mspsim.chip.SHT11;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.MSP430;

public abstract class MoteIVNode<FlashType extends ExternalFlash> extends CC2420Node<FlashType> {

  public static final int MODE_LEDS_OFF = 0;
  public static final int MODE_LEDS_1 = 1;
  public static final int MODE_LEDS_2 = 2;
  public static final int MODE_LEDS_3 = 3;
  public static final int MODE_MAX = MODE_LEDS_3;
  // Port 2.
  public static final int BUTTON_PIN = 7;

  public static final int SHT11_CLK_PIN = 6;
  public static final int SHT11_DATA_PIN = 5;

  public static final int SHT11_CLK = 1 << SHT11_CLK_PIN;
  public static final int SHT11_DATA = 1 << SHT11_DATA_PIN;

  private static final int[] LEDS = { 0xff2020, 0x40ff40, 0x4040ff };
  public static final int BLUE_LED = 0x40;
  public static final int GREEN_LED = 0x20;
  public static final int RED_LED = 0x10;

  public boolean redLed;
  public boolean blueLed;
  public boolean greenLed;

  private final Leds leds;
  private final Button button;
  public final SHT11 sht11;

  public SkyGui gui;

  public MoteIVNode(String id, MSP430 cpu, FlashType flash) {
    super(id, cpu, flash);
    setMode(MODE_LEDS_OFF);
    leds = new Leds(cpu, LEDS);
    button = new Button("Button", cpu, port2, BUTTON_PIN, true);
    sht11 = new SHT11(cpu);
    sht11.setDataPort(port1, SHT11_DATA_PIN);
  }

  public Leds getLeds() {
      return leds;
  }

  public Button getButton() {
      return button;
  }

  @Deprecated
  public void setButton(boolean buttonPressed) {
      button.setPressed(buttonPressed);
  }

  @Override
  public void setupGUI() {
    super.setupGUI();
    if (gui == null) {
      gui = new SkyGui(this);
      registry.registerComponent("nodegui", gui);
    }
  }

  @Override
  public void portWrite(IOPort source, int data) {
    super.portWrite(source, data);

    if (source == port5) {
      redLed = (data & RED_LED) == 0;
      blueLed = (data & BLUE_LED) == 0;
      greenLed = (data & GREEN_LED) == 0;
      leds.setLeds((redLed ? 1 : 0) + (greenLed ? 2 : 0) + (blueLed ? 4 : 0));
      int newMode = (redLed ? 1 : 0) + (greenLed ? 1 : 0) + (blueLed ? 1 : 0);
      setMode(newMode);
    } else if (source == port1) {
      sht11.clockPin((data & SHT11_CLK) != 0);
      sht11.dataPin((data & SHT11_DATA) != 0);
    }
  }

  @Override
  public int getModeMax() {
    return MODE_MAX;
  }

}
