package org.contikios.cooja.dialogs;
import java.awt.GraphicsEnvironment;

import org.contikios.cooja.Cooja;

public class MessageContainer extends MessageRanged 
{

    public final String message;

    public MessageContainer(String message, int type) {
        super(type);
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
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
