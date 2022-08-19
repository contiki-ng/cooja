package se.sics.mspsim.core;

public interface USARTSource {

    void addUSARTListener(USARTListener listener);
    void removeUSARTListener(USARTListener listener);

    void addStateChangeListener(StateChangeListener listener);
    void removeStateChangeListener(StateChangeListener listener);

    /* for input into this UART */
    boolean isReceiveFlagCleared();
    void byteReceived(int b);

}
