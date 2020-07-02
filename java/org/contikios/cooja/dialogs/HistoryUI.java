package org.contikios.cooja.dialogs;

import java.util.ArrayList;
import java.util.Collection;

import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JTextField;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.Text;

// this is a base for messages with RANG/TYPE
public class HistoryUI {

    private static Logger logger = Logger.getLogger(HistoryUI.class);

    /* Command history */
    private final static int HISTORY_SIZE = 15;
    private ArrayList<String> history = new ArrayList<String>();
    private int historyPos = -1;

    public void add(String command) {
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
    }

    public void assignOnUI(JTextField commandField) {
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
    }

    private static final String HISTORY_SEPARATOR = "~;";

    public Element getConfigXML(final String name) {
        if (history.size() <= 0)
            return null;

      Element element = new Element(name);

      for (String s: history) {
          if (s == null) {
            continue;
          }
          Element e = new Element("item");
          e.setText(s);
          element.addContent( e );
      }

      /*
      // old style history - as one string
      StringBuilder sb = new StringBuilder();
      for (String s: history) {
        if (s == null) {
          continue;
        }
        sb.append(s + HISTORY_SEPARATOR);
      }

      if (sb.length() > 0) {
          element.setText(sb.toString());
      }
      */

      return element;
    }

    public void setConfigXML(Element element) {
          Collection<Element> hist = element.getChildren();
          if (!hist.isEmpty()) {
              logger.info("history:" + hist );
              for (Element h: hist) {
                  this.history.add(h.getText());
              }
          }
          else {
              logger.info("old style history" + element.getText());
              // old style history - as one string
              String[] history = element.getText().split(HISTORY_SEPARATOR);
              for (String h: history) {
                this.history.add(h);
              }
          }
          historyPos = -1;
    }
}
