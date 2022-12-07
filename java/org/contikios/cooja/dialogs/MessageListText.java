package org.contikios.cooja.dialogs;

import java.io.OutputStream;

public class MessageListText implements MessageList {

    public MessageListText() {
    }
    
    @Override
    public MessageContainer[] getMessages() {
        // TODO Auto-generated method stub
        return new MessageContainer[0];
    }

    @Override
    public void clearMessages() {
        // TODO Auto-generated method stub
    }

    @Override
    public void addMessage(String string) {
        System.out.println(string);
    }

    @Override
    public void addMessage(String string, int type) {
        System.out.println(string);
    }

    @Override
    public void addMessage(Throwable throwable, int type) {
        throwable.printStackTrace(System.out);
    }

    @Override
    public OutputStream getInputStream(int type) {
        // TODO Auto-generated method stub
        return System.out;
    }

    
    
}
