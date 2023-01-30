package se.sics.mspsim.core;

import se.sics.mspsim.core.EmulationLogger.WarningType;
import se.sics.mspsim.util.Utils;

public class DMA extends IOUnit {

    /* global DMA configuration */
    public static final int DMACTL0 = 0x122;
    public static final int DMACTL1 = 0x124;

    /* per channel configuration */
    public static final int DMAxCTL = 0x1e0;
    public static final int DMAxSA = 0x1e2;
    public static final int DMAxDA = 0x1e4;
    public static final int DMAxSZ = 0x1e6;

    /* DMA TSELx - from msp430x1xxx devices */
    /* new devices has more channels and more triggers */
    public static final int DMAREQ = 0;
    public static final int TACCR2 = 1;
    public static final int TBCCR2 = 2;
    public static final int URXIFG0 = 3; /* UART 0 */
    public static final int UTXIFG0 = 4; /* UART 0 */
    public static final int DAC12_0 = 5;
    public static final int ADC12_0 = 6;
    public static final int TACCR0 = 7;
    public static final int TBCCR0 = 8;
    public static final int URXIFG1 = 9; /* UART 1 */
    public static final int UTXIFG1 = 10; /* UART 1 */
    public static final int MULTIPLIER = 11;

    public static final int IFG_MASK = 0x08;

    private static final int[] INCR = {0,0,-1,1};

    private InterruptMultiplexer interruptMultiplexer;

    class Channel implements InterruptHandler {
        final int channelNo;
        /* public registers */

        int ctl;
        int sourceAddress;
        int destinationAddress;
        int size;

        /* internal registers */
        int currentSourceAddress;
        int currentDestinationAddress;
        int storedSize;

        int srcIncr;
        int dstIncr;
        boolean dstByteMode;
        boolean srcByteMode;

        DMATrigger trigger;
        int triggerIndex;
        int transferMode;

        boolean enable;
        boolean dmaLevel; /* edge or level sensitive trigger */
        boolean dmaIE;
        boolean dmaIFG;

        Channel(int i) {
            channelNo = i;
        }

        void setTrigger(DMATrigger t, int index) {
            if (DEBUG) log("Setting trigger to " + t);
            trigger = t;
            triggerIndex = index;
        }

        void write(int address, int data) {
            switch (address) {
                case 0 -> {
                    ctl = data;
                    transferMode = (data >> 12) & 7;
                    dstIncr = INCR[(data >> 10) & 3];
                    srcIncr = INCR[(data >> 8) & 3];
                    dstByteMode = (data & 0x80) > 0; /* bit 7 */
                    srcByteMode = (data & 0x40) > 0; /* bit 6 */
                    dmaLevel = (data & 0x20) > 0; /* bit 5 */
                    boolean enabling = !enable && (data & 0x10) > 0;
                    enable = (data & 0x10) > 0; /* bit 4 */
                    dmaIFG = (data & IFG_MASK) > 0; /* bit 3 */
                    dmaIE = (data & 0x04) > 0; /* bit 2 */
                    if (DEBUG) log("DMA Ch." + channelNo + ": config srcIncr: " + srcIncr + " dstIncr:" + dstIncr
                            + " en: " + enable + " srcB:" + srcByteMode + " dstB:" + dstByteMode + " level: " + dmaLevel +
                            " transferMode: " + transferMode + " ie:" + dmaIE);
                    /* this might be wrong ? */
                    /*if (enabling) trigger(trigger, triggerIndex);*/
                    interruptMultiplexer.updateInterrupt(dmaIFG & dmaIE, channelNo);
                }
                case 2 -> {
                    sourceAddress = data;
                    currentSourceAddress = data;
                }
                case 4 -> {
                    destinationAddress = data;
                    currentDestinationAddress = data;
                }
                case 6 -> {
                    size = data;
                    storedSize = data;
                }
            }
        }

        int read(int address) {
            switch (address) {
                case 0 -> { // set the IFG.
                    ctl = (ctl & ~IFG_MASK) | (dmaIFG ? IFG_MASK : 0);
                    return ctl;
                }
                case 2 -> {
                    return sourceAddress;
                }
                case 4 -> {
                    return destinationAddress;
                }
                case 6 -> {
                    return size;
                }
            }
            logw(WarningType.EXECUTION, "Illegal read of DMA Channel register");
            return 0;
        }

        void trigger(DMATrigger trigger, int index) {
            /* perform memory move and possibly clear triggering flag!!! */
            /* NOTE: show config byte/word also !!! */
            if (enable) {
                int data = cpu.currentSegment.read(currentSourceAddress, Memory.AccessMode.BYTE, Memory.AccessType.READ);
                if (DEBUG) log("DMA Triggered reading from: " +
                        currentSourceAddress + " => " + data + " " + (char) data +
                        " size:" + size + " index:" + index);
                // flag already cleared by the memory read above
//                trigger.clearDMATrigger(index);
                DMA.this.cpu.currentSegment.write(currentDestinationAddress, data, Memory.AccessMode.BYTE);

                currentSourceAddress += srcIncr;
                currentDestinationAddress += dstIncr;
                size--;
                if (size == 0) {
                    currentSourceAddress = sourceAddress;
                    currentDestinationAddress = destinationAddress;
                    size = storedSize;
                    if ((transferMode & 0x04) == 0) {
                        enable = false;
                    }
                    /* flag interrupt and update interrupt vector */
                    dmaIFG = true;
                    interruptMultiplexer.updateInterrupt(dmaIFG & dmaIE, channelNo);
                }
            }
        }

        @Override
        public void interruptServiced(int vector) {
        }

        @Override
        public String getName() {
            return "DMA Channel " + channelNo;
        }

        String info() {
            return getName() + (enable ? " Enabled " : " Disabled")
                    + "  Index: " + triggerIndex + "  Trigger: " + trigger
                    + "\n    current source: 0x"
                    + cpu.getAddressAsString(currentSourceAddress)
                    + " destination: 0x"
                    + cpu.getAddressAsString(currentDestinationAddress)
                    + "  size: " + (storedSize - size) + "/" + storedSize;
        }
    }

    private final Channel[] channels = new Channel[3];
    private int dmactl0;
    private int dmactl1;

    /* MAX 16 triggers ? */
    private final DMATrigger[] dmaTrigger = new DMATrigger[16];
    private final int[] dmaTriggerIndex = new int[16];

    public DMA(String id, MSP430Core cpu, int[] memory, int offset) {
        super(id, cpu, memory, offset);
        channels[0] = new Channel(0);
        channels[1] = new Channel(1);
        channels[2] = new Channel(2);
    }

    public void setInterruptMultiplexer(InterruptMultiplexer interruptMultiplexer) {
        this.interruptMultiplexer = interruptMultiplexer;
    }

    public void setDMATrigger(int totindex, DMATrigger trigger, int tIndex) {
        dmaTrigger[totindex] = trigger;
        dmaTriggerIndex[totindex] = tIndex;
        trigger.setDMA(this);
    }

    public void trigger(DMATrigger trigger, int index) {
        /* could make this a bit and have a bit-pattern if more dma channels but
         * with 3 channels it does not make sense. Optimize later - maybe with
         * flag in DMA triggers so that they now if a channel listens at all.
         */
      for (Channel channel : channels) {
//            System.out.println("DMA Channel:" + i + " " + channels[i].trigger + " = " + trigger);
        if (channel.trigger == trigger &&
                channel.triggerIndex == index) channel.trigger(trigger, index);
      }
    }

    @Override
    public void interruptServiced(int vector) {
    }

    @Override
    public void write(int address, int value, boolean word, long cycles) {
        if (DEBUG) log("DMA write to: " + Utils.hex(address, 4) + ": " + value);
        switch (address) {
            case DMACTL0 -> { // DMA Control 0.
                dmactl0 = value;
                channels[0].setTrigger(dmaTrigger[value & 0xf], dmaTriggerIndex[value & 0xf]);
                channels[1].setTrigger(dmaTrigger[(value >> 4) & 0xf], dmaTriggerIndex[(value >> 4) & 0xf]);
                channels[2].setTrigger(dmaTrigger[(value >> 8) & 0xf], dmaTriggerIndex[(value >> 8) & 0xf]);
            }
            case DMACTL1 -> dmactl1 = value; // DMA Control 1.
            default -> channels[(address - DMAxCTL) / 8].write(address & 0x07, value); // must be word?
        }
    }

    @Override
    public int read(int address, boolean word, long cycles) {
        return switch (address) {
            case DMACTL0 -> dmactl0;
            case DMACTL1 -> dmactl1;
            default -> channels[(address - DMAxCTL) / 8].read(address & 7); // must be word?
        };
    }

    @Override
    public String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("  DMACTL0: 0x").append(Utils.hex16(dmactl0)).append("  DMACTL1: 0x").append(Utils.hex16(dmactl1));
        for (Channel c : channels) {
            sb.append("\n  ").append(c.info());
        }
        return sb.toString();
    }

}
