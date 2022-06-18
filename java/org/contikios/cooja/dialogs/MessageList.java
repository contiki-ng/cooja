package org.contikios.cooja.dialogs;

import java.io.OutputStream;

public interface MessageList {

    int NORMAL = 0;
    int WARNING = 1;
    int ERROR = 2;
    
    void addMessage(String string, int normal);

    MessageContainer[] getMessages();

    void clearMessages();

    void addMessage(String string);

    OutputStream getInputStream(int type);

}
