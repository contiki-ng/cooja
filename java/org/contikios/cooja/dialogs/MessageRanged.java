package org.contikios.cooja.dialogs;
import java.awt.GraphicsEnvironment;

import org.contikios.cooja.Cooja;

// this is a base for messages with RANG/TYPE
public class MessageRanged {

    public final int type;

    public MessageRanged(int t) {
        type = t;
    }

}
