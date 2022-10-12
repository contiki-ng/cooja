package org.contikios.cooja.dialogs;

import org.contikios.cooja.Cooja;

public record MessageContainer(String message, int type) {
    @Override
    public String toString() {
        return message;
    }

    /* This will select UI based or not UI based depending on withUI in combination with
     * headless info.
     */
    public static MessageList createMessageList(boolean withUI) {
        if (withUI && Cooja.isVisualized()) {
            return new Cooja.RunnableInEDT<MessageList>() {
                @Override
                public MessageList work() {
                    return new MessageListUI();
                }
            }.invokeAndWait();
        } else {
            return new MessageListText();
        }
    }
}
