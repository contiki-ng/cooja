/*
 * Copyright (c) 2012, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.BorderFactory; 

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.SerialIO;
import org.contikios.cooja.interfaces.SerialPort;
import org.contikios.cooja.dialogs.MessageListUI;
import org.contikios.cooja.dialogs.MessageContainer;

import org.contikios.cooja.util.StringUtils;



public abstract class SerialUI extends SerialIO 
	implements SerialPort 
{
  private static Logger logger = Logger.getLogger(SerialUI.class);

  private final static int MAX_LENGTH = 16*1024;
  private static final int LOG     = 0;
  private static final int SENDING = 1;
  private static final int RECEIVING = 2;

  private boolean is_recv = false; //flag that last activity is receiving
  private byte[] recvBuf = new byte[MAX_LENGTH];
  private int    recvLen = 0;
  private byte lastSerialData = 0; /* SerialPort */
  private String lastLogMessage = ""; /* Log */
  private StringBuilder newMessage = new StringBuilder(); /* Log */

  private byte[] lastSendingData = null;

  /* Command history */
  private final static int HISTORY_SIZE = 15;
  private ArrayList<String> history = new ArrayList<String>();
  private int historyPos = -1;

  /* Log */
  public String getLastLogMessage() {
    return lastLogMessage;
  }

  /* SerialPort */
  private abstract class SerialDataObservable extends Observable {
    public abstract void notifyNewData();
  }
  private SerialDataObservable serialDataObservable = new SerialDataObservable() {
    public void notifyNewData() {
      if (this.countObservers() == 0) {
        return;
      }
      setChanged();
      notifyObservers(SerialUI.this);
    }
  };
  public void addSerialDataObserver(Observer o) {
    serialDataObservable.addObserver(o);
  }
  public void deleteSerialDataObserver(Observer o) {
    serialDataObservable.deleteObserver(o);
  }
  public byte getLastSerialData() {
    return lastSerialData;
  }

  protected void receiveFlush() {
	  if (recvLen <= 0)
		  return;
      /* Notify observers of new log */
      lastLogMessage = newMessage.toString();
      lastLogMessage = lastLogMessage.replaceAll("[^\\p{Print}\\p{Blank}]", "");
      newMessage.setLength(0);
      is_recv = true;
      this.setChanged();
      this.notifyObservers(getMote());
      recvLen = 0;
  }

  // on incoming 1 byte
  // @return - true if recvBufer is flushed 
  public boolean on_recv_byte_flushed(byte x) {
		recvBuf[recvLen] = x;
		++recvLen;

		lastSerialData  = x;
	    serialDataObservable.notifyNewData();

		boolean flushed = false;
		if (x == '\n') {
			this.receiveFlush();
			flushed = true;
	    } else {
	      newMessage.append((char) x);
	      if (newMessage.length() > MAX_LENGTH) {
	        /*logger.warn("Dropping too large log message (>" + MAX_LENGTH + " bytes).");*/
	        lastLogMessage = "# [1024 bytes, no line ending]: " + newMessage.substring(0, 20) + "...";
			this.receiveFlush();
			flushed = true;
	      }
	    }
	    /* Notify observers of new serial character */
	    is_recv = true;
		return flushed;
  }

  public void dataReceived(int data) {
    lastSendingData = null;
	on_recv_byte_flushed((byte)data);
    /* Notify observers of new serial character */
    is_recv = true;
  }

  public void bufReceived(byte[] data) {
    lastSendingData = null;

    boolean flushed = false;
    for (byte x : data)
    	flushed = on_recv_byte_flushed(x);

    if (!flushed)
    	this.receiveFlush();
    is_recv = true;
}

  protected void sendFlush() {
	  is_recv = false;
      this.setChanged();
      this.notifyObservers(getMote());
  };
  
  public void writeString(String message) {
	  if (is_recv)
		  this.receiveFlush();
	  lastSendingData = message.getBytes();
	  this.sendFlush();
  }

  public void writeArray(byte[] s) {
	  if (is_recv)
		  this.receiveFlush();
	  lastSendingData = s;
	  this.sendFlush();
  }

  public void writeByte(final byte b) {
	  if (is_recv)
		  this.receiveFlush();
	  lastSendingData = new byte[1];
	  lastSendingData[0] = b;
	  this.sendFlush();
  }

  private JScrollPane fanoutMessageList(MessageListUI x) {
	  x.setFont(new Font("monospace", Font.PLAIN, 12));
      //x.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
      //x.setFont(new Font("Lucida Sans Typewriter", Font.PLAIN, 10));
      //x.setFont(new Font("Consolas", Font.PLAIN, 10));
      //x.setFont(new Font("Curier New", Font.PLAIN, 10));
      x.setCellRenderer( x.newWrapedMessageRenderer() );
      x.setScrolableVertical();

      x.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      x.addPopupMenuItem(null, true);

      x.setForeground(LOG,       Color.black);
      x.setForeground(RECEIVING, Color.blue);
      x.setForeground(SENDING,   Color.magenta);
      x.setBorder(RECEIVING,BorderFactory.createMatteBorder(1, 5, 1, 1, Color.blue) );
      x.setBorder(SENDING,  BorderFactory.createMatteBorder(1, 1, 1, 5, Color.magenta) );
      x.setBorder(LOG,      BorderFactory.createMatteBorder(1, 1, 1, 1, Color.black) );

      JScrollPane scrollPane = new JScrollPane(x);
      //scrollPane.setViewportView(x);
      scrollPane.setPreferredSize(new Dimension(80, 100));
      return scrollPane;
  }

  /* Mote interface visualizer */
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel(new BorderLayout());

    JTabbedPane tabbedView = new JTabbedPane();
    tabbedView.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
    tabbedView.setTabPlacement(JTabbedPane.TOP); //BOTTOM

    final JTextArea logTextPane = new JTextArea();
    logTextPane.setLineWrap(true);
    logTextPane.setWrapStyleWord(true);
    JScrollPane scrollTextPane = new JScrollPane(logTextPane);
    scrollTextPane.setPreferredSize(new Dimension(80, 100));
    tabbedView.addTab("text", scrollTextPane);

    //final JTextArea logHexPane = new JTextArea();
    final MessageListUI logHexPane = new MessageListUI();
    logHexPane.setFont(new Font("Curier New", Font.PLAIN, 10));
    tabbedView.addTab("hex", fanoutMessageList(logHexPane));

    //final JTextArea logHexPane = new JTextArea();
    final MessageListUI logDumpPane = new MessageListUI();
    logDumpPane.setFont(new Font("Consolas", Font.PLAIN, 10));
    tabbedView.addTab("dump", fanoutMessageList(logDumpPane));

    JPanel commandPane = new JPanel(new BorderLayout());
    final JTextField commandField = new JTextField(5);//15
    JButton sendButton = new JButton("Send data");

    ActionListener sendCommandAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final String command = trim(commandField.getText());
        if (command == null) {
          commandField.getToolkit().beep();
          return;
        }

        try {
          /* Add to history */
          if (history.size() > 0 && command.equals(history.get(0))) {
            /* Ignored */
          } else {
            history.add(0, command);
            while (history.size() > HISTORY_SIZE) {
              history.remove(HISTORY_SIZE-1);
            }
          }
          historyPos = -1;

          appendToTextArea(logTextPane, "> " + command);
          commandField.setText("");
          if (getMote().getSimulation().isRunning()) {
            getMote().getSimulation().invokeSimulationThread(new Runnable() {
              public void run() {
                writeString(command);
              }
            });
          } else {
            writeString(command);
          }
        } catch (Exception ex) {
          logger.error("could not send '" + command + "':", ex);
          JOptionPane.showMessageDialog(
              logTextPane,
              "Could not send '" + command + "':\n" + ex.getMessage(), "Error sending message",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    };
    commandField.addActionListener(sendCommandAction);
    sendButton.addActionListener(sendCommandAction);

    /* History */
    commandField.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
        case KeyEvent.VK_UP: {
          historyPos++;
          if (historyPos >= history.size()) {
            historyPos = history.size() - 1;
            commandField.getToolkit().beep();
          }
          if (historyPos >= 0 && historyPos < history.size()) {
            commandField.setText(history.get(historyPos));
          } else {
            commandField.setText("");
          }
          break;
        }
        case KeyEvent.VK_DOWN: {
          historyPos--;
          if (historyPos < 0) {
            historyPos = -1;
            commandField.setText("");
            commandField.getToolkit().beep();
            break;
          }
          if (historyPos >= 0 && historyPos < history.size()) {
            commandField.setText(history.get(historyPos));
          } else {
            commandField.setText("");
          }
          break;
        }
        }
      }
    });

    commandPane.add(BorderLayout.CENTER, commandField);
    commandPane.add(BorderLayout.EAST, sendButton);

    logTextPane.setOpaque(false);
    logTextPane.setEditable(false);
    logTextPane.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if ((e.getModifiers() & (MouseEvent.SHIFT_MASK|MouseEvent.CTRL_MASK)) != 0) {
          return;
        }
        commandField.requestFocusInWindow();
      }
    });

    /* Mote interface observer */
    Observer observer;
    this.addObserver(observer = new Observer() {
      public void update(Observable obs, Object obj) {
        final byte[] sendData = (lastSendingData != null) 
        				? Arrays.copyOf(lastSendingData, lastSendingData.length)
        				: null;
        final byte[] recvData = Arrays.copyOf(recvBuf, recvLen);
        EventQueue.invokeLater(new Runnable() {
          public void run() {
        	final int recvLen = recvData.length;
            if (recvLen > 0){
        		//logger.info("SUI: logMessage "+recvLen );
                appendToTextArea(logTextPane, recvData );
              	appendToHexArea(logHexPane, recvData, RECEIVING);
              	appendToDumpArea(logDumpPane, recvData, RECEIVING);
            }
            if ( sendData != null) {
        		//logger.info("SUI: lastSendingData "+sendData.length);
            	appendToHexArea(logHexPane, sendData, SENDING);
            	appendToDumpArea(logDumpPane, sendData, SENDING);
                appendToTextArea(logTextPane, sendData );
            }

          }
        });
      }
    });
    panel.putClientProperty("intf_obs", observer);

    panel.add(BorderLayout.CENTER, tabbedView);
    panel.add(BorderLayout.SOUTH, commandPane);
    return panel;
  }

  public void releaseInterfaceVisualizer(JPanel panel) {
    Observer observer = (Observer) panel.getClientProperty("intf_obs");
    if (observer == null) {
      logger.fatal("Error when releasing panel, observer is null");
      return;
    }

    this.deleteObserver(observer);
  }

  private static final String HISTORY_SEPARATOR = "~;";
  public Collection<Element> getConfigXML() {
    StringBuilder sb = new StringBuilder();
    for (String s: history) {
      if (s == null) {
        continue;
      }
      sb.append(s + HISTORY_SEPARATOR);
    }
    if (sb.length() == 0) {
      return null;
    }

    ArrayList<Element> config = new ArrayList<Element>();
    Element element = new Element("history");
    element.setText(sb.toString());
    config.add(element);

    return config;
  }

  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      if (element.getName().equals("history")) {
        String[] history = element.getText().split(HISTORY_SEPARATOR);
        for (String h: history) {
          this.history.add(h);
        }
        historyPos = -1;
      }
    }
  }

  public void close() {
  }

  public void flushInput() {
  }

  public abstract Mote getMote();



  protected static void appendToTextArea(JTextArea textArea, final byte[] data) {
	StringBuilder recvStr = new StringBuilder();
	final Font font = textArea.getFont();
	
	for( byte x: data) {
		char c = (char)x;
		if (isPrintableChar(c) 
				&& font.canDisplay(c)
			)
			recvStr.append(c);
		else
			recvStr.append('_');
	}
	appendToTextArea(textArea, recvStr.toString() );
  }

  protected static void appendToTextArea(JTextArea textArea, String text) {
    String current = textArea.getText();
    int len = current.length();
    if (len > 8192) {
      current = current.substring(len - 8192);
    }
    current = len > 0 ? (current + '\n' + text) : text;
    textArea.setText(current);
    textArea.setCaretPosition(current.length());

    Rectangle visRect = textArea.getVisibleRect();
    if (visRect.x > 0) {
      visRect.x = 0;
      textArea.scrollRectToVisible(visRect);
    }
  }

  public static 
  boolean isPrintableChar( char c ) {
	if (Character.isISOControl(c))
		return false;
	if (c == KeyEvent.CHAR_UNDEFINED)
		return false;
	Character.UnicodeBlock block = Character.UnicodeBlock.of( c );
	if (block != null)
	if (block == Character.UnicodeBlock.SPECIALS)
		return false;
	return true;
  }

  protected static void appendToHexArea(MessageListUI textArea, final byte[] data, int type) {
	  if(data.length > 0) {
		  textArea.addMessage( new HexMessage (data , type) );
	  }
  }

  protected static void appendToDumpArea(MessageListUI textArea, final byte[] data, int type) {
	  if(data.length > 0) {
	      MessageContainer msg = new DumpMessage(data , type);
          textArea.addMessage( msg );
	  }
  }

  public  static 
  class BinMessage extends MessageContainer {
      public final byte[] data;

      public BinMessage(final byte[] x, int type ){
          super(type);
          this.data = x;
      }
  } 

  public  static 
  class HexMessage extends BinMessage {

      public HexMessage(final byte[] x, int type ){
          super(x, type);
      }

      @Override
      public String toString() {
          return StringUtils.toHex(data, 1);
      }
  }

  public  static 
  class DumpMessage extends BinMessage {

      public DumpMessage(final byte[] x, int type ){
          super(x, type);
      }

      @Override
      public String toString() {
          final String line = StringUtils.hexDump(data, 1, 16);
          if (line.charAt(line.length()-1)=='\n')
              return line.substring(0, line.length()-1);
          return line;
      }
  }

  private static String trim(String text) {
    return (text != null) && ((text = text.trim()).length() > 0) ? text : null;
  }
  
}
