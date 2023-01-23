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
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.jdom2.Element;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.interfaces.SerialPort;
import org.contikios.cooja.util.EventTriggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SerialUI extends Log implements SerialPort {
  private static final Logger logger = LoggerFactory.getLogger(SerialUI.class);
  private final static int MAX_LENGTH = 16*1024;

  protected final EventTriggers<EventTriggers.Update, Byte> serialDataTriggers = new EventTriggers<>();
  private byte lastSerialData; /* SerialPort */
  private String lastLogMessage = ""; /* Log */
  private int charactersReceived;
  private final StringBuilder newMessage = new StringBuilder(); /* Log */

  /* Command history */
  private final static int HISTORY_SIZE = 15;
  private final ArrayList<String> history = new ArrayList<>();
  private int historyPos = -1;

  /* Log */
  @Override
  public String getLastLogMessage() {
    return lastLogMessage;
  }

  /* SerialPort */
  @Override
  public byte getLastSerialData() {
    return lastSerialData;
  }

  /** Returns true for characters in <code>\p{Punct}</code>.
   * Characters of the punctuation class are listed in java.util.regex.Pattern Javadoc. */
  private static boolean isPunctuation(char c) {
    return switch (c) {
      case '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/', ':',
              ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~' -> true;
      default -> false;
    };
  }

  // FIXME: make data a byte instead.
  public void dataReceived(int data) {
    charactersReceived++;
    if (data == '\n' || charactersReceived > MAX_LENGTH) {
      lastLogMessage = data == '\n'
              ? newMessage.toString()
              : "# [1024 bytes, no line ending]: " + newMessage.substring(0, Math.min(20, newMessage.length())) + "...";
      newMessage.setLength(0);
      charactersReceived = 0;
      logDataTriggers.trigger(EventTriggers.Update.UPDATE, new LogDataInfo(getMote(), lastLogMessage));
    } else {
      char ch = (char) data;
      if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch) || isPunctuation(ch)) {
        newMessage.append(ch);
      }
    }

    /* Notify observers of new serial character */
    lastSerialData = (byte) data;
    serialDataTriggers.trigger(EventTriggers.Update.UPDATE, (byte) data);
  }


  /* Mote interface visualizer */
  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel commandPane = new JPanel(new BorderLayout());

    final JTextArea logTextPane = new JTextArea();
    final JTextField commandField = new JTextField(15);
    JButton sendButton = new JButton("Send data");

    ActionListener sendCommandAction = e -> {
      final String command = trim(commandField.getText());
      if (command == null) {
        commandField.getToolkit().beep();
        return;
      }

      try {
        /* Add to history */
        if (history.isEmpty() || !command.equals(history.get(0))) {
          history.add(0, command);
          while (history.size() > HISTORY_SIZE) {
            history.remove(HISTORY_SIZE-1);
          }
        }
        historyPos = -1;

        appendToTextArea(logTextPane, "> " + command);
        commandField.setText("");
        if (getMote().getSimulation().isRunning()) {
          getMote().getSimulation().invokeSimulationThread(() -> writeString(command));
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
    };
    commandField.addActionListener(sendCommandAction);
    sendButton.addActionListener(sendCommandAction);

    /* History */
    commandField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_UP -> {
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
          }
          case KeyEvent.VK_DOWN -> {
            historyPos--;
            if (historyPos < 0) {
              historyPos = -1;
              commandField.setText("");
              commandField.getToolkit().beep();
            } else if (historyPos < history.size()) {
              commandField.setText(history.get(historyPos));
            } else {
              commandField.setText("");
            }
          }
        }
      }
    });

    commandPane.add(BorderLayout.CENTER, commandField);
    commandPane.add(BorderLayout.EAST, sendButton);

    logTextPane.setOpaque(false);
    logTextPane.setEditable(false);
    logTextPane.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if ((e.getModifiersEx() & (MouseEvent.SHIFT_DOWN_MASK|MouseEvent.CTRL_DOWN_MASK)) != 0) {
          return;
        }
        commandField.requestFocusInWindow();
      }
    });

    getLogDataTriggers().addTrigger(panel, (obs, obj) -> {
      final String logMessage = getLastLogMessage();
      EventQueue.invokeLater(() -> appendToTextArea(logTextPane, logMessage));
    });
    JScrollPane scrollPane = new JScrollPane(logTextPane);
    scrollPane.setPreferredSize(new Dimension(100, 100));
    panel.add(BorderLayout.CENTER, scrollPane);
    panel.add(BorderLayout.SOUTH, commandPane);
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    getLogDataTriggers().deleteTriggers(panel);
  }

  private static final String HISTORY_SEPARATOR = "~;";
  @Override
  public Collection<Element> getConfigXML() {
    StringBuilder sb = new StringBuilder();
    for (String s: history) {
      if (s == null) {
        continue;
      }
      sb.append(s).append(HISTORY_SEPARATOR);
    }
    if (sb.isEmpty()) {
      return null;
    }

    ArrayList<Element> config = new ArrayList<>();
    Element element = new Element("history");
    element.setText(sb.toString());
    config.add(element);

    return config;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      if (element.getName().equals("history")) {
        this.history.addAll(Arrays.asList(element.getText().split(HISTORY_SEPARATOR)));
        historyPos = -1;
      }
    }
  }

  @Override
  public void close() {
  }

  @Override
  public void flushInput() {
  }

  public abstract Mote getMote();


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

  private static String trim(String text) {
    return (text != null) && (!(text = text.trim()).isEmpty()) ? text : null;
  }

  @Override
  public EventTriggers<EventTriggers.Update, Byte> getSerialDataTriggers() {
    return serialDataTriggers;
  }
}
