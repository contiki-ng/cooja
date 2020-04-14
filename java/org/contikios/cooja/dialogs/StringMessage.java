package org.contikios.cooja.dialogs;
import java.awt.GraphicsEnvironment;

import org.contikios.cooja.Cooja;

// this is a base for messages with RANG/TYPE
public class StringMessage extends MessageContainer {

    public final String message;

    public StringMessage(final String message, int type) {
        super(type);
        this.message = message;
    }

    public StringMessage(final MessageContainer x) {
        super(x.type);
        this.message = x.toString();
    }

    @Override
    public String toString() {
        return message;
    }
    
}
