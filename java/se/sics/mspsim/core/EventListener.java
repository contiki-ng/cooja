package se.sics.mspsim.core;
import se.sics.mspsim.util.ProxySupport;

public interface EventListener {

    void event(EventSource source, String event, Object data);

    class Proxy extends ProxySupport<EventListener> implements EventListener {
        public static final Proxy INSTANCE = new Proxy();

        @Override
        public void event(EventSource source, String event, Object data) {
            EventListener[] listeners = this.listeners;
            for(EventListener listener : listeners) {
                listener.event(source, event, data);
            }
        }

    }

}
