package org.contikios.cooja.dialogs;
import java.awt.GraphicsEnvironment;

import org.contikios.cooja.Cooja;

public class MessageContainer 
{

    public final int type;

    public MessageContainer(int t) {
        type = t;
    }

    /* This will select UI based or not UI based depending on withUI in combination with
     * headless info.
     */
    public static MessageList createMessageList(boolean withUI) {
        if (withUI && !GraphicsEnvironment.isHeadless() && Cooja.isVisualized()) {
            return new MessageListUI();
        } else {
            return new MessageListText();
        }
    }
}
