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

package se.sics.mspsim.core;

import java.util.ArrayDeque;

/**
 * UART-mode implementation of the eUSCI_A peripheral on FR5xxx parts.
 */
public class EUsciA extends IOUnit implements DMATrigger, USARTSource {

    private static final boolean DEBUG = false;

    // FR5xxx eUSCI_A register offsets
    public static final int CTLW0 = 0x00;   // Control Word 0
    public static final int CTLW1 = 0x02;   // Control Word 1 (not used in UART mode)
    public static final int BRW = 0x06;     // Baud Rate Word
    public static final int MCTLW = 0x08;   // Modulation Control Word
    public static final int STATW = 0x0a;   // Status Word
    public static final int RXBUF = 0x0c;   // Receive Buffer
    public static final int TXBUF = 0x0e;   // Transmit Buffer
    public static final int ABCTL = 0x10;   // Auto Baud Rate Control
    public static final int IRCTL = 0x12;   // IrDA Control
    public static final int IE = 0x1a;      // Interrupt Enable
    public static final int IFG = 0x1c;     // Interrupt Flag
    public static final int IV = 0x1e;      // Interrupt Vector

    // CTLW0 bits
    public static final int UCSWRST = 0x0001;   // Software reset
    public static final int UCTXBRK = 0x0002;   // Transmit break
    public static final int UCTXADDR = 0x0004;  // Transmit address
    public static final int UCDORM = 0x0008;    // Dormant mode
    public static final int UCBRKIE = 0x0010;   // Break interrupt enable
    public static final int UCRXEIE = 0x0020;   // RX error interrupt enable
    public static final int UCSSEL__UCLK = 0x0000;   // UCLK
    public static final int UCSSEL__ACLK = 0x0040;   // ACLK
    public static final int UCSSEL__SMCLK = 0x0080;  // SMCLK
    public static final int UCSSEL_MASK = 0x00C0;

    // MCTLW bits
    public static final int UCOS16 = 0x0001;    // Oversampling mode
    public static final int UCBRF_MASK = 0x00F0; // First stage modulation
    public static final int UCBRS_MASK = 0xFF00; // Second stage modulation

    // STATW bits
    public static final int UCBUSY = 0x0001;    // USCI busy
    public static final int UCADDR = 0x0002;    // Address received
    public static final int UCRXERR = 0x0004;   // RX error
    public static final int UCBRK = 0x0008;     // Break detected
    public static final int UCPE = 0x0010;      // Parity error
    public static final int UCOE = 0x0020;      // Overrun error
    public static final int UCFE = 0x0040;      // Framing error
    public static final int UCLISTEN = 0x0080;  // Listen enable

    // IFG bits
    public static final int UCRXIFG = 0x0001;   // RX interrupt flag
    public static final int UCTXIFG = 0x0002;   // TX interrupt flag
    public static final int UCSTTIFG = 0x0004;  // Start bit interrupt flag
    public static final int UCTXCPTIFG = 0x0008; // TX complete interrupt flag

    // Registers
    private int ctlw0;
    private int ctlw1;
    private int brw;
    private int mctlw;
    private int statw;
    private int rxbuf;
    private int txbuf;
    private int ie;
    private int ifg;
    private int iv;

    // State
    private boolean moduleEnabled;
    private boolean transmitting;
    private int clockSource;
    private int baudRate;
    private int tickPerByte;
    private long nextTXReady;

    private final int uartIndex;
    private final int vector;
    private final ArrayDeque<Integer> txBuffer = new ArrayDeque<>(100);
    private USARTListener usartListener;

    // TX trigger event
    private final TimeEvent txTrigger = new TimeEvent(0) {
        @Override
        public void execute(long t) {
            handleTransmit(t);
        }
    };

    public EUsciA(MSP430Core cpu, int uartIndex, int[] memory, MSP430Config config) {
        super(config.uartConfig[uartIndex].name, cpu, memory, config.uartConfig[uartIndex].offset);
        this.uartIndex = uartIndex;
        this.vector = config.uartConfig[uartIndex].rxVector;
        reset(0);
    }

    @Override
    public void reset(int type) {
        ctlw0 = UCSWRST;  // Start in reset state
        ctlw1 = 0;
        brw = 0;
        mctlw = 0;
        statw = 0;
        rxbuf = 0;
        txbuf = 0;
        ie = 0;
        ifg = UCTXIFG;  // TX buffer empty at start
        iv = 0;

        moduleEnabled = false;
        transmitting = false;
        clockSource = MSP430Constants.CLK_SMCLK;
        tickPerByte = 1000;
        nextTXReady = -1;
        txBuffer.clear();

        updateIV();

        if (DEBUG) {
            log("eUSCI_A reset, ifg=" + Integer.toHexString(ifg));
        }
    }

    private void updateIV() {
        int ie_ifg = ifg & ie;
        if ((ie_ifg & UCRXIFG) != 0) {
            iv = 2;
        } else if ((ie_ifg & UCTXIFG) != 0) {
            iv = 4;
        } else {
            iv = 0;
        }
    }

    private void refreshInterrupt() {
        updateIV();
        cpu.flagInterrupt(vector, this, (ifg & ie) != 0);
    }

    private void setBitIFG(int bits) {
        ifg |= bits;
        refreshInterrupt();
    }

    private void clrBitIFG(int bits) {
        ifg &= ~bits;
        refreshInterrupt();
    }

    private void updateBaudRate() {
        int div = brw;
        if (div == 0) div = 1;

        int clkFrq;
        if (clockSource == MSP430Constants.CLK_ACLK) {
            clkFrq = cpu.aclkFrq;
        } else {
            clkFrq = cpu.smclkFrq;
        }

        // Handle case where clock frequency is not set (default to 8MHz for FR5969)
        if (clkFrq == 0) {
            clkFrq = 8000000;
            if (DEBUG) {
                log("Clock frequency was 0, defaulting to 8MHz");
            }
        }

        // Check for oversampling mode
        if ((mctlw & UCOS16) != 0) {
            // Oversampling mode: effective divider is brw * 16
            baudRate = clkFrq / (div * 16);
        } else {
            baudRate = clkFrq / div;
        }

        if (baudRate == 0) baudRate = 1;

        // Calculate ticks per byte (8 data bits + start + stop = 10 bits)
        tickPerByte = (10 * clkFrq) / baudRate;
        if (tickPerByte == 0) tickPerByte = 1;

        if (DEBUG) {
            log("Baud rate updated: clk=" + clkFrq + ", div=" + div +
                ", os16=" + ((mctlw & UCOS16) != 0) +
                ", baud=" + baudRate + ", tickPerByte=" + tickPerByte);
        }
    }

    private void handleTransmit(long cycles) {
        if (DEBUG) {
            log("handleTransmit: transmitting=" + transmitting +
                ", txBuffer.size=" + txBuffer.size());
        }

        if (transmitting) {
            // Shift out the byte and notify listener
            if (!txBuffer.isEmpty()) {
                int data = txBuffer.remove();
                USARTListener listener = this.usartListener;
                if (listener != null) {
                    listener.dataReceived(this, data);
                }
            }

            // Check if done transmitting
            if (txBuffer.isEmpty()) {
                statw &= ~UCBUSY;
                transmitting = false;
                setBitIFG(UCTXIFG);
                if (DEBUG) {
                    log("TX complete, TXIFG set");
                }
            }
        }

        // Schedule next transmission if buffer not empty
        if (!txBuffer.isEmpty()) {
            clrBitIFG(UCTXIFG);
            transmitting = true;
            nextTXReady = cycles + tickPerByte;
            cpu.scheduleCycleEvent(txTrigger, nextTXReady);
        }
    }

    @Override
    public void write(int address, int data, boolean word, long cycles) {
        int reg = address - offset;

        if (DEBUG) {
            log("write: reg=0x" + Integer.toHexString(reg) +
                ", data=0x" + Integer.toHexString(data) +
                ", word=" + word);
        }

        switch (reg) {
        case CTLW0:
            // Handle SWRST transition
            boolean wasInReset = (ctlw0 & UCSWRST) != 0;
            boolean nowInReset = (data & UCSWRST) != 0;

            if (wasInReset && !nowInReset) {
                // Coming out of reset - enable module
                moduleEnabled = true;
                setBitIFG(UCTXIFG);  // TX buffer empty
            } else if (!wasInReset && nowInReset) {
                // Going into reset
                moduleEnabled = false;
                if (DEBUG) {
                    log("Module disabled (entering reset)");
                }
            }

            // Update clock source
            int ucssel = data & UCSSEL_MASK;
            if (ucssel == UCSSEL__ACLK) {
                clockSource = MSP430Constants.CLK_ACLK;
            } else {
                clockSource = MSP430Constants.CLK_SMCLK;
            }

            ctlw0 = data;
            updateBaudRate();
            break;

        case CTLW1:
            ctlw1 = data;
            break;

        case BRW:
            brw = data;
            updateBaudRate();
            break;

        case MCTLW:
            mctlw = data;
            updateBaudRate();
            break;

        case STATW:
            // STATW is mostly read-only, only UCLISTEN can be written
            statw = (statw & ~UCLISTEN) | (data & UCLISTEN);
            break;

        case TXBUF:
            txbuf = data & 0xFF;
            if (moduleEnabled) {
                clrBitIFG(UCTXIFG);
                statw |= UCBUSY;
                txBuffer.add(txbuf);

                if (!transmitting) {
                    transmitting = true;
                    nextTXReady = cycles + tickPerByte;
                    cpu.scheduleCycleEvent(txTrigger, nextTXReady);
                    if (DEBUG) {
                        log("Scheduled TX at cycle " + nextTXReady);
                    }
                }
            } else if (DEBUG) {
                log("TXBUF write ignored - module not enabled");
            }
            break;

        case IE:
            // Writing IE may enable a previously-pending IFG bit (must
            // re-flag) or clear all enables (must de-flag).
            ie = data;
            refreshInterrupt();
            break;

        case IFG:
            // Writing IFG can set/clear flags directly; re-evaluate the
            // CPU interrupt line so cleared flags actually deflag.
            ifg = data;
            refreshInterrupt();
            break;

        default:
            if (DEBUG) {
                log("Unhandled write to offset 0x" + Integer.toHexString(reg));
            }
            break;
        }
    }

    @Override
    public int read(int address, boolean word, long cycles) {
        int reg = address - offset;
        int result = 0;

        switch (reg) {
        case CTLW0:
            result = ctlw0;
            break;
        case CTLW1:
            result = ctlw1;
            break;
        case BRW:
            result = brw;
            break;
        case MCTLW:
            result = mctlw;
            break;
        case STATW:
            result = statw;
            break;
        case RXBUF:
            result = rxbuf;
            clrBitIFG(UCRXIFG);
            stateChanged(USARTListener.RXFLAG_CLEARED, true);
            break;
        case TXBUF:
            result = txbuf;
            break;
        case IE:
            result = ie;
            break;
        case IFG:
            result = ifg;
            break;
        case IV:
            result = iv;
            // Reading IV clears highest priority pending interrupt
            if ((ifg & ie & UCRXIFG) != 0) {
                clrBitIFG(UCRXIFG);
            } else if ((ifg & ie & UCTXIFG) != 0) {
                clrBitIFG(UCTXIFG);
            }
            break;
        default:
            if (DEBUG) {
                log("Unhandled read from offset 0x" + Integer.toHexString(reg));
            }
            break;
        }

        if (DEBUG && (reg == IFG || reg == TXBUF || reg == RXBUF)) {
            log("read: reg=0x" + Integer.toHexString(reg) +
                " -> 0x" + Integer.toHexString(result));
        }

        return result;
    }

    @Override
    public void interruptServiced(int vector) {
        // Interrupt acknowledged
    }

    // USARTSource interface
    @Override
    public synchronized void addUSARTListener(USARTListener listener) {
        usartListener = USARTListener.Proxy.INSTANCE.add(usartListener, listener);
        if (DEBUG) {
            log("USART listener added: " + listener);
        }
    }

    @Override
    public synchronized void removeUSARTListener(USARTListener listener) {
        usartListener = USARTListener.Proxy.INSTANCE.remove(usartListener, listener);
    }

    @Override
    public boolean isReceiveFlagCleared() {
        return (ifg & UCRXIFG) == 0;
    }

    @Override
    public void byteReceived(int b) {
        if (DEBUG) {
            log("byteReceived: " + b + " ('" + (char)b + "')");
        }
        rxbuf = b & 0xFF;
        setBitIFG(UCRXIFG);
    }

    // DMATrigger interface
    @Override
    public void setDMA(DMA dma) {
        // TODO: implement DMA
    }

    @Override
    public boolean getDMATriggerState(int index) {
        return false;
    }

    @Override
    public void clearDMATrigger(int index) {
    }

    @Override
    public String info() {
        return "eUSCI_A" + uartIndex +
               " moduleEnabled=" + moduleEnabled +
               " ifg=0x" + Integer.toHexString(ifg) +
               " ie=0x" + Integer.toHexString(ie) +
               " brw=" + brw +
               " baud=" + baudRate;
    }
}
