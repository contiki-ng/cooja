package se.sics.mspsim.core;

public interface DMATrigger {
    void setDMA(DMA dma);
    boolean getDMATriggerState(int index);
    void clearDMATrigger(int index);
}
