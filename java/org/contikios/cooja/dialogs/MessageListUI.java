/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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
 *
 */

package org.contikios.cooja.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.border.*;
import javax.swing.BorderFactory; 
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ListCellRenderer;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import org.apache.log4j.Logger;

import org.contikios.cooja.Cooja;

/**
 *
 * @author Adam Dunkels
 * @author Joakim Eriksson
 * @author Niclas Finne
 * @author Fredrik Osterlind
 */
public class MessageListUI extends JList implements MessageList {

  private static final Logger logger = Logger.getLogger(MessageListUI.class);

  private Color[] foregrounds = new Color[] { null, Color.red };
  private Color[] backgrounds = new Color[] { null, null };
  private Border[] borders = new Border[] { null, null };

  private JPopupMenu popup = null;
  private boolean hideNormal = false;
  private boolean isVerticalScrollable = false;

  private int max = -1;
  
  public MessageListUI() {
    if (GraphicsEnvironment.isHeadless()) {
        throw new RuntimeException("Can not use UI version of message list in Headless mode");
    }
    super.setModel(new MessageModel());
    setCellRenderer(new MessageRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  /**
   * @param max Maximum number of messages
   */
  public MessageListUI(int max) {
    this();
    this.max = max;
  }

  public void setScrolableVertical() {
      // https://stackoverflow.com/questions/7306295/swing-jlist-with-multiline-text-and-dynamic-height
      isVerticalScrollable = true;

      ComponentListener l = new ComponentAdapter() {

          @Override
          public void componentResized(ComponentEvent e) {
              // next line possible if list is of type JXList
              // list.invalidateCellSizeCache();
              // for core: force cache invalidation by temporarily setting fixed height
              setFixedCellHeight(10);
              setFixedCellHeight(-1);
          }

      };
      addComponentListener(l);
  }
  
  @Override
  public boolean getScrollableTracksViewportWidth() {
      if (isVerticalScrollable)
          return true;
      else
          return super.getScrollableTracksViewportWidth();
  }

  // specify topmost type, that style is managed by get/set Color/Border
  public void setManagedTypes(int types_num) {
      // first type assigns to a JList
      if (foregrounds.length+1 == types_num)
          return;
      if (types_num <= 1)
          types_num = 1;
      foregrounds   = Arrays.copyOf(foregrounds, types_num);
      backgrounds   = Arrays.copyOf(backgrounds, types_num);
      borders       = Arrays.copyOf(borders, types_num);
  }

  public Color getForeground(int type) {
    Color c = null;
    if ((type > 0) && (type <= foregrounds.length))
      c = foregrounds[type - 1];
    return c == null ? getForeground() : c;
  }

  public void setForeground(int type, Color color) {
    if (type > 0 && type <= foregrounds.length) {
      foregrounds[type - 1] = color;
    } else if (type == NORMAL) {
      setForeground(color);
    } else {
        setManagedTypes(type);
        foregrounds[type - 1] = color;
    }
  }

  public Color getBackground(int type) {
    Color c = type > 0 && type <= backgrounds.length
      ? backgrounds[type - 1] : null;
    return c == null ? getBackground() : c;
  }

  public void setBackground(int type, Color color) {
    if (type > 0 && type <= backgrounds.length) {
      backgrounds[type - 1] = color;
    } else if (type == NORMAL) {
      setBackground(color);
    }else {
        setManagedTypes(type);
        backgrounds[type - 1] = color;
    }
  }


  public Border getBorder(int type) {
    Border c = type > 0 && type <= borders.length
      ? borders[type - 1] : null;
    return c == null ? getBorder() : c;
  }

  public void setBorder(int type, Border x) {
    if (type > 0 && type <= borders.length) {
        borders[type - 1] = x;
    } else if (type == NORMAL) {
      setBorder(x);
    }else {
        setManagedTypes(type);
        borders[type - 1] = x;
    }
  }

  public PrintStream getInputStream(final int type) {
    try {
      PipedInputStream input = new PipedInputStream();
      PipedOutputStream output = new PipedOutputStream(input);
      final BufferedReader stringInput = new BufferedReader(new InputStreamReader(input));

      Thread readThread = new Thread(new Runnable() {
        @Override
        public void run() {
          String readLine;
          try {
            while ((readLine = stringInput.readLine()) != null) {
              addMessage(readLine, type);
            }
          } catch (IOException e) {
            // Occurs when write end closes pipe - die quietly
          }
        }

      });
      readThread.start();

      return new PrintStream(output);
    } catch (IOException e) {
      logger.error(messages);
      return null;
    }
  }

  public void addMessage(String message) {
    addMessage(message, NORMAL);
  }

  private ArrayList<MessageContainer> messages = new ArrayList<MessageContainer>();

  public MessageContainer[] getMessages() {
      return messages.toArray( new MessageContainer[0] );
  }

  public MessageContainer[] getSelectedMessages() 
  {
    MessageContainer[] messages = null;
    if(getSelectedIndex() < 0){
        messages = getMessages();
    }
    else {
        int[] selectedIx = getSelectedIndices();
    	messages = new MessageContainer[selectedIx.length];
        for (int i = 0; i < selectedIx.length; i++) {
        	messages[i] = (MessageContainer)(getModel().getElementAt(selectedIx[i]));
        }
    }
    return messages;
  }

  private void updateModel() {
    boolean scroll = getLastVisibleIndex() >= getModel().getSize() - 2;

    while (messages.size() > getModel().getSize()) {
      ((DefaultListModel<MessageContainer>) getModel()).addElement(messages.get(getModel().getSize()));
    }
    while (max > 0 && getModel().getSize() > max) {
      ((DefaultListModel) getModel()).removeElementAt(0);
      messages.remove(0);
    }

    if (scroll) {
      ensureIndexIsVisible(getModel().getSize() - 1);
    }
  }

  public void addMessage(final String message, final int type) {
      // this is for text messages log/warn/error
      Cooja.setProgressMessage(message, type);
      StringMessage msg = new StringMessage(message, type);
      addMessage(msg);
  } 

  public void addMessage(final MessageContainer msg)
  {
    messages.add(msg);

    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        updateModel();
      }
    });
  }

  public void clearMessages() {
    messages.clear();
    ((DefaultListModel) getModel()).clear();
  }

  @Override
  public void setModel(ListModel model) {
    throw new IllegalArgumentException("changing model not permitted");
  }

  public void addPopupMenuItem(JMenuItem item, boolean withDefaults) {
    if (popup == null) {
      popup = new JPopupMenu();
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popup.show(MessageListUI.this, e.getX(), e.getY());
          }
        }
        @Override
        public void mousePressed(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popup.show(MessageListUI.this, e.getX(), e.getY());
          }
        }
        @Override
        public void mouseReleased(MouseEvent e) {
          if (e.isPopupTrigger()) {
            popup.show(MessageListUI.this, e.getX(), e.getY());
          }
        }
      });

      JMenuItem headerMenuItem = new JMenuItem("Output:");
      headerMenuItem.setEnabled(false);
      popup.add(headerMenuItem);
      popup.add(new JSeparator());

      if (withDefaults) {
        /* Create default menu items */
        final JMenuItem hideNormalMenuItem = new JCheckBoxMenuItem("Hide normal output");
        hideNormalMenuItem.setEnabled(true);
        hideNormalMenuItem.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            MessageListUI.this.hideNormal = hideNormalMenuItem.isSelected();
            ((MessageModel)getModel()).updateList();
          }
        });
        popup.add(hideNormalMenuItem);

        JMenuItem consoleOutputMenuItem = new JMenuItem("Output to console");
        consoleOutputMenuItem.setEnabled(true);
        consoleOutputMenuItem.addActionListener(new ActionListener() {
          
          @Override
          public void actionPerformed(ActionEvent e) {
        	MessageContainer[] messages = getSelectedMessages();
            logger.info("\nCOMPILATION OUTPUT:\n");
            for (MessageContainer msg: messages) {
              if (hideNormal && msg.type == NORMAL) {
                continue;
              }
              logger.info(msg);
            }
            logger.info("\n");
          }
        });
        popup.add(consoleOutputMenuItem);

        JMenuItem clipboardMenuItem = new JMenuItem("Copy to clipboard");
        clipboardMenuItem.setEnabled(true);
        clipboardMenuItem.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            StringBuilder sb = new StringBuilder();
            MessageContainer[] messages = getSelectedMessages();
            for (MessageContainer msg: messages) {
              if (hideNormal && msg.type == NORMAL) {
                continue;
              }
              sb.append(msg + "\n");
            }

            StringSelection stringSelection = new StringSelection(sb.toString());
            clipboard.setContents(stringSelection, null);
          }
        });
        popup.add(clipboardMenuItem);

        popup.add(new JSeparator());
      }
    }

    if (item == null) {
      return;
    }

    popup.add(item);
  }

  // -------------------------------------------------------------------
  // Renderer for messages
  // -------------------------------------------------------------------

  private class MessageModel extends DefaultListModel {
    public void updateList() {
      fireContentsChanged(this, 0, getSize());
    }
  }


  private class MessageRenderer extends DefaultListCellRenderer {
    private final Dimension nullDimension = new Dimension(0,0);
    @Override
    public Component getListCellRendererComponent(
        JList list,
	Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus)
    {
      super.getListCellRendererComponent(list, value, index, isSelected,
					 cellHasFocus);
      MessageContainer msg = (MessageContainer) value;

      if (hideNormal && msg.type == NORMAL && index != MessageListUI.this.getModel().getSize()-1) {
        setPreferredSize(nullDimension);
        return this;
      }

      setPreferredSize(null);
      setForeground(((MessageListUI) list).getForeground(msg.type));
      setBackground(((MessageListUI) list).getBackground(msg.type));
      return this;
    }
  }

  public class WrapedMessageRenderer extends JPanel// JTextArea // 
                                    implements ListCellRenderer  
    {
        private final Dimension nullDimension = new Dimension(0,0);
        private final Dimension lessDimension = new Dimension(50, 32);
        public JTextArea renderer = null;

        public WrapedMessageRenderer(){
            setLayout( new BoxLayout(this, BoxLayout.PAGE_AXIS) );
            setMinimumSize( lessDimension );

            renderer = new JTextArea(1,3);
            renderer.setMinimumSize( lessDimension );
            renderer.setOpaque(false);
            renderer.setEditable(false);
            renderer.setLineWrap(true);
            renderer.setWrapStyleWord(true);

            add(renderer);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension d = super.getMaximumSize();
            d.height = getPreferredSize().height;
            return d;
        }

        @Override
        public Component getListCellRendererComponent(
            JList list, Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus)
        {
          MessageContainer msg = (MessageContainer) value;

          Component container = (Component)this; //new JPanel(new BorderLayout());
          if (hideNormal && msg.type == NORMAL && index != MessageListUI.this.getModel().getSize()-1) {
              container.setMaximumSize(nullDimension);
            return container;
          }
          //after hide, need to show me
          container.setMaximumSize(null);
          renderer.setPreferredSize(null);

          MessageListUI self = ((MessageListUI) list);
          
          //JTextArea renderer = this; //new JTextArea();

          if (cellHasFocus)
              renderer.setForeground(Color.yellow);
          else
              renderer.setForeground(self.getForeground(msg.type));

          if(isSelected)
              renderer.setBackground(Color.gray);
          else
              renderer.setBackground(self.getBackground(msg.type));

  	      renderer.setBorder( self.getBorder(msg.type) );


          renderer.setText(value.toString());
          int width = list.getWidth();
          // this is just to lure the ta's internal sizing mechanism into action
          if (width > 0)
              renderer.setSize(width, Short.MAX_VALUE);
          container.revalidate();

          /*
          Dimension tmp = renderer.getPreferredSize();
          Dimension min = super.getPreferredSize();
          Dimension max = super.getMaximumSize();
          renderer.setText(value.toString() 
                          +"#"+ tmp.getWidth() + ":" + tmp.getHeight()
                          +" @"+ min.getWidth() + ":" + min.getHeight()
                          +" #"+ (int)max.getWidth() + ":" + (int)max.getHeight()
                          );
          */

          // request the focus on this component to be able to edit it
          if (cellHasFocus)
	    	  renderer.requestFocus();

          return container;
        }
     } // end of inner class MessageRenderer
  
  public 
    WrapedMessageRenderer newWrapedMessageRenderer() {
       return new WrapedMessageRenderer();
    }

} // end of MessagList
