package org.contikios.cooja.serialsocket;

/*
 * Copyright (c) 2014, TU Braunschweig.
 * Copyright (c) 2010, Swedish Institute of Computer Science.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import javax.swing.text.NumberFormatter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jdom2.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MotePlugin;
import org.contikios.cooja.Plugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.interfaces.SerialPort;
import org.contikios.cooja.util.CmdUtils;

/**
 * Socket to simulated serial port forwarder. Server version.
 * 
 * @author Fredrik Osterlind
 * @author Enrico Jorns
 */
@ClassDescription("Serial Socket (SERVER)")
@PluginType(PluginType.PType.MOTE_PLUGIN)
public class SerialSocketServer implements Plugin, MotePlugin {
  private static final Logger logger = LogManager.getLogger(SerialSocketServer.class);

  private final static int STATUSBAR_WIDTH = 350;

  private static final Color COLOR_NEUTRAL = Color.DARK_GRAY;
  private static final Color COLOR_POSITIVE = new Color(0, 161, 83);
  private static final Color COLOR_NEGATIVE = Color.RED;

  private final SerialPort serialPort;
  private Observer serialDataObserver;

  private JLabel socketToMoteLabel;
  private JLabel moteToSocketLabel;
  private JLabel socketStatusLabel;
  private JFormattedTextField listenPortField;
  private JButton serverStartButton;

  private int inBytes = 0, outBytes = 0;

  private ServerSocket serverSocket;
  private Socket clientSocket;

  private String commands = null;

  private final Mote mote;
  private final Simulation simulation;

  private final VisPlugin frame;

  public SerialSocketServer(Mote mote, Simulation simulation, final Cooja gui) {
    this.mote = mote;
    this.simulation = simulation;

    int SERVER_DEFAULT_PORT = 60000 + mote.getID();

    serialPort = (SerialPort) mote.getInterfaces().getLog();
    if (serialPort == null) {
      throw new RuntimeException("No mote serial port");
    }

    if (!Cooja.isVisualized()) {
      frame = null;
      return;
    }
    frame = new VisPlugin("Serial Socket (SERVER) (" + mote + ")", gui, this);
    updateTimer.start();
    frame.setResizable(false);
    frame.setLayout(new BorderLayout());

    // --- Server Port setup

    GridBagConstraints c = new GridBagConstraints();
    JPanel socketPanel = new JPanel(new GridBagLayout());
    socketPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    JLabel label = new JLabel("Listen port: ");
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.1;
    c.anchor = GridBagConstraints.EAST;
    socketPanel.add(label, c);

    NumberFormat nf = NumberFormat.getIntegerInstance();
    nf.setGroupingUsed(false);
    listenPortField = new JFormattedTextField(new NumberFormatter(nf));
    listenPortField.setColumns(5);
    listenPortField.setText(String.valueOf(SERVER_DEFAULT_PORT));
    c.gridx++;
    c.weightx = 0.0;
    socketPanel.add(listenPortField, c);

    serverStartButton = new JButton("Start") { // Button for label toggling
      @Override
      public Dimension getPreferredSize() {
        String origText = getText();
        Dimension origDim = super.getPreferredSize();
        setText("Stop");
        Dimension altDim = super.getPreferredSize();
        setText(origText);
        return new Dimension(Math.max(origDim.width, altDim.width), origDim.height);
      }
    };
    c.gridx++;
    c.weightx = 0.1;
    c.anchor = GridBagConstraints.EAST;
    socketPanel.add(serverStartButton, c);

    c.gridx = 0;
    c.gridy++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.HORIZONTAL;
    socketPanel.add(new JSeparator(JSeparator.HORIZONTAL), c);

    frame.add(BorderLayout.NORTH, socketPanel);

    // --- Incoming / outgoing info

    JPanel connectionInfoPanel = new JPanel(new GridLayout(0, 2));
    connectionInfoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    c = new GridBagConstraints();

    label = new JLabel("socket -> mote: ");
    label.setHorizontalAlignment(JLabel.RIGHT);
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.EAST;
    connectionInfoPanel.add(label);

    socketToMoteLabel = new JLabel("0 bytes");
    c.gridx++;
    c.anchor = GridBagConstraints.WEST;
    connectionInfoPanel.add(socketToMoteLabel);

    label = new JLabel("mote -> socket: ");
    label.setHorizontalAlignment(JLabel.RIGHT);
    c.gridx = 0;
    c.gridy++;
    c.anchor = GridBagConstraints.EAST;
    connectionInfoPanel.add(label);

    moteToSocketLabel = new JLabel("0 bytes");
    c.gridx++;
    c.anchor = GridBagConstraints.WEST;
    connectionInfoPanel.add(moteToSocketLabel);

    frame.add(BorderLayout.CENTER, connectionInfoPanel);

    // --- Status bar

    JPanel statusBarPanel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(STATUSBAR_WIDTH, d.height);
      }
    };
    statusBarPanel.setLayout(new BoxLayout(statusBarPanel, BoxLayout.LINE_AXIS));
    statusBarPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
    label = new JLabel("Status: ");
    statusBarPanel.add(label);

    socketStatusLabel = new JLabel("Idle");
    socketStatusLabel.setForeground(Color.DARK_GRAY);
    statusBarPanel.add(socketStatusLabel);

    frame.add(BorderLayout.SOUTH, statusBarPanel);

    serverStartButton.addActionListener(e -> {
      if (e.getActionCommand().equals("Start")) {
        try {
          listenPortField.commitEdit();
        } catch (ParseException ex) {
          logger.error(ex);
        }
        startServer(((Long) listenPortField.getValue()).intValue());
      } else {
        stopServer();
      }
    });

    frame.pack();

    // gui updates for server status updates
    addServerListener(new ServerListener() {

      @Override
      public void onServerStarted(final int port) {
        SwingUtilities.invokeLater(() -> {
          System.out.println("onServerStarted");
          socketStatusLabel.setForeground(COLOR_NEUTRAL);
          socketStatusLabel.setText("Listening on port " + port);
          listenPortField.setEnabled(false);
          serverStartButton.setText("Stop");
        });
      }

      @Override
      public void onClientConnected(final Socket client) {
        SwingUtilities.invokeLater(() -> {
          socketStatusLabel.setForeground(COLOR_POSITIVE);
          socketStatusLabel.setText("Client "
                  + client.getInetAddress() + ":" + client.getPort() + " connected.");
        });
      }

      @Override
      public void onClientDisconnected() {
        SwingUtilities.invokeLater(() -> {
          // XXX check why needed
          if (serverSocket != null) {
            socketStatusLabel.setForeground(COLOR_NEUTRAL);
            socketStatusLabel.setText("Listening on port " + serverSocket.getLocalPort());
          }
        });
      }

      @Override
      public void onServerStopped() {
        SwingUtilities.invokeLater(() -> {
          listenPortField.setEnabled(true);
          serverStartButton.setText("Start");
          socketStatusLabel.setForeground(COLOR_NEUTRAL);
          socketStatusLabel.setText("Idle");
        });
      }

      @Override
      public void onServerError(final String msg) {
        SwingUtilities.invokeLater(() -> {
          socketStatusLabel.setForeground(COLOR_NEGATIVE);
          socketStatusLabel.setText(msg);
        });
      }

    });
  }

  private final List<ServerListener> listeners = new LinkedList<>();
  
  public interface ServerListener {
    void onServerStarted(int port);
    void onClientConnected(Socket client);
    void onClientDisconnected();
    void onServerStopped();
    void onServerError(String msg);
  }
  
  private void addServerListener(ServerListener listener) {
    listeners.add(listener);
  }
  
  public void notifyServerStarted(int port) {
    for (ServerListener listener : listeners) {
      listener.onServerStarted(port);
    }
  }
  
  public void notifyClientConnected(Socket client) {
    for (ServerListener listener : listeners) {
      listener.onClientConnected(client);
    }
  }

  public void notifyClientDisconnected() {
    for (ServerListener listener : listeners) {
      listener.onClientDisconnected();
    }
  }
  
  public void notifyServerStopped() {
    for (ServerListener listener : listeners) {
      listener.onServerStopped();
    }
  }
  
  public void notifyServerError(String msg) {
    for (ServerListener listener : listeners) {
      listener.onServerError(msg);
    }
  }
  
  /**
   * Start listening with server
   *
   * @param port Port to listen on.
   * @return Returns true on success.
   */
  public boolean startServer(int port) {
    try {
      serverSocket = new ServerSocket(port);
      logger.info("Listening on port: " + port);
      notifyServerStarted(port);
    } catch (IOException ex) {
      logger.error(ex.getMessage());
      notifyServerError(ex.getMessage());
      return false;
    }
    new Thread(() -> {
      Thread incomingDataHandler = null;
      while (!serverSocket.isClosed()) {
        try {
          // wait for next client
          Socket candidateSocket = serverSocket.accept();

          // reject connection if already one client connected
          if (clientSocket != null && !clientSocket.isClosed()) {
            logger.info("Refused connection of client " + candidateSocket.getInetAddress());
            candidateSocket.close();
            continue;
          }

          clientSocket = candidateSocket;

          /* Start handler for data input from socket */
          incomingDataHandler = new Thread(new IncomingDataHandler(), "incomingDataHandler");
          incomingDataHandler.start();

          /* Observe serial port for outgoing data */
          serialDataObserver = new SerialDataObserver();
          serialPort.addSerialDataObserver(serialDataObserver);

          inBytes = outBytes = 0;

          logger.info("Client connected: " + clientSocket.getInetAddress());
          notifyClientConnected(clientSocket);
        } catch (IOException e) {
          logger.info("Listening thread shut down: " + e.getMessage());
          try {
            serverSocket.close();
          } catch (IOException ex) {
            logger.error(ex);
          }
        }
      }
      cleanupClient();
      if (incomingDataHandler != null) {
        // Wait for reader thread to terminate
        try {
          incomingDataHandler.join(500);
        } catch (InterruptedException ex) {
          logger.warn(ex);
        }
      }
      notifyServerStopped();
    }, "SerialSocketServer").start();

    if (commands != null && !simulation.getCfg().updateSim()) {
      // Run commands in a separate thread since Cooja cannot start the simulation before this method returns.
      // The simulation is required to run before tunslip6 can be started.
      new Thread(() -> {
        int rv = 0;
        for (var cmd : commands.split("\n")) {
          if (cmd.trim().isEmpty()) {
            continue;
          }
          try {
            // Must not be synchronous, tunslip6 hangs in 17-tun-rpl-br because SerialSocket does not disconnect.
            CmdUtils.run(cmd, simulation.getCooja(), false);
          } catch (Exception e) {
            rv = 1;
            break;
          }
        }
        simulation.stopSimulation(rv > 0 ? rv : null);
        stopServer();
      }, "SerialSocketServer commands").start();
    }
    return true;
  }

  /**
   * Stops server by closing server listen socket.
   */
  public void stopServer() {
    try {
      serverSocket.close();
    } catch (IOException ex) {
      logger.error(ex);
    }
  }

  /* Forward data: virtual port -> mote */
  private class IncomingDataHandler implements Runnable {

    DataInputStream in;

    @Override
    public void run() {
      int numRead = 0;
      byte[] data = new byte[16*1024];
      try {
        in = new DataInputStream(clientSocket.getInputStream());
      } catch (IOException ex) {
        logger.error(ex);
        return;
      }

      logger.info("Forwarder: socket -> serial port");
      while (numRead >= 0) {
        final int finalNumRead = numRead;
        final byte[] finalData = data;
        /* We are not on the simulation thread */
        simulation.invokeSimulationThread(() -> {
          for (int i = 0; i < finalNumRead; i++) {
            serialPort.writeByte(finalData[i]);
          }
          inBytes += finalNumRead;
        });

        try {
          numRead = in.read(data);
        } catch (IOException e) {
          logger.info(e.getMessage());
          numRead = -1;
        }
      }
      logger.info("End of Stream");
      cleanupClient();
    }
  }

  private class SerialDataObserver implements Observer {
    
    DataOutputStream out;

    public SerialDataObserver() {
      try {
        out = new DataOutputStream(clientSocket.getOutputStream());
      } catch (IOException ex) {
        logger.error(ex);
        // FIXME: this should fail, not continue and produce invisible updates.
        out = null;
      }
    }

    @Override
    public void update(Observable obs, Object obj) {
      if (out == null) {
        return;
      }
      try {
        out.write(serialPort.getLastSerialData());
        out.flush();
        outBytes++;
      } catch (IOException ex) {
        logger.error(ex);
        cleanupClient();
      }
    }

  }

  @Override
  public Collection<Element> getConfigXML() {
    List<Element> config = new ArrayList<>();

    // XXX isVisualized guards?

    var element = new Element("port");
    if (serverSocket == null || !serverSocket.isBound()) {
      try {
        listenPortField.commitEdit();
        element.setText(String.valueOf(listenPortField.getValue()));
      } catch (ParseException ex) {
        logger.error(ex.getMessage());
        listenPortField.setText("null");
      }
    } else {
      element.setText(String.valueOf(serverSocket.getLocalPort()));
    }
    config.add(element);

    element = new Element("bound");
    if (serverSocket == null) {
      element.setText(String.valueOf(false));
    } else {
      element.setText(String.valueOf(!serverSocket.isClosed()));
    }
    config.add(element);

    if (commands != null) {
      element = new Element("commands");
      element.setText(commands);
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Simulation sim, Collection<Element> configXML) {
    Integer port = null;
    boolean bound = false;
    
    for (Element element : configXML) {
      switch (element.getName()) {
        case "port" -> port = Integer.parseInt(element.getText());
        case "bound" -> bound = Boolean.parseBoolean(element.getText());
        case "commands" -> commands = element.getText();
        default -> logger.warn("Unknown config element: " + element.getName());
      }
    }
    if (Cooja.isVisualized()) {
      if (port != null) {
        listenPortField.setText(String.valueOf(port));
      }
      if (bound) {
        serverStartButton.doClick();
      }
    } else {
      // if bound and all set up, start client
      if (port == null) {
        logger.error("Server not started due to incomplete configuration");
        return false;
      }
      return startServer(port);
    }

    return true;
  }

  private void cleanupClient() {
    try {
      if (clientSocket != null) {
        clientSocket.close();
        clientSocket = null;
      }
    } catch (IOException e1) {
      logger.error(e1.getMessage());
    }

    serialPort.deleteSerialDataObserver(serialDataObserver);

    notifyClientDisconnected();
  }

  private boolean closed = false;

  @Override
  public JInternalFrame getCooja() {
    return frame;
  }

  @Override
  public void startPlugin() {
  }

  @Override
  public void closePlugin() {
    closed = true;
    cleanupClient();
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException ex) {
      logger.error(ex);
    }
  }

  @Override
  public Mote getMote() {
    return mote;
  }

  private static final int UPDATE_INTERVAL = 150;
  private final Timer updateTimer = new Timer(UPDATE_INTERVAL, new ActionListener() {
    @Override
	  public void actionPerformed(ActionEvent e) {
      if (closed) {
        updateTimer.stop();
        return;
      }
      socketToMoteLabel.setText(inBytes + " bytes");
      moteToSocketLabel.setText(outBytes + " bytes");
    }
  });
}

