/*
 * Copyright (c) 2007-2012, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * This file is part of MSPSim.
 *
 * -----------------------------------------------------------------
 *
 * NetworkConnection
 *
 * Author  : Joakim Eriksson
 * Created : 31 mar 2008
 */
package se.sics.mspsim.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import se.sics.mspsim.chip.PacketListener;

/**
 * @author joakim
 *
 */
public class NetworkConnection implements Runnable {

  private final static boolean DEBUG = false;
  private final static int DEFAULT_PORT = 4711;

  private ServerSocket serverSocket;
  private final SendThread sendThread;
  private ConnectionThread[] connections;
  private PacketListener packetListener;

  public NetworkConnection() {
    if (connect(DEFAULT_PORT)) {
      System.out.println("NetworkConnection: Connected to network...");
    } else {
      setupServer(DEFAULT_PORT);
      System.out.println("NetworkConnection: Setup network server...");
    }
    sendThread = new SendThread();
  }

  public synchronized void addPacketListener(PacketListener listener) {
      packetListener = PacketListener.Proxy.INSTANCE.add(packetListener, listener);
  }

  public synchronized void removePacketListener(PacketListener listener) {
      packetListener = PacketListener.Proxy.INSTANCE.remove(packetListener, listener);
  }

  private void setupServer(int port) {
    try {
      serverSocket = new ServerSocket(port);
      if (DEBUG) System.out.println("NetworkConnection: setup of server socket finished... ");
      new Thread(this, "NetworkConnection.setupServer").start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    System.out.println("NetworkConnection: Accepting new connections...");
    while (true) {
      try {
        Socket s = serverSocket.accept();
        if (DEBUG) System.out.println("NetworkConnection: New connection from " + s.getRemoteSocketAddress());
        connections = ArrayUtils.add(ConnectionThread.class, connections, new ConnectionThread(s));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  // Data incoming from the network!!! - forward to radio and if server, to
  // all other nodes
  private void dataReceived(byte[] data, ConnectionThread source) {
    PacketListener listener = this.packetListener;
    if (listener != null) {
      // Send this data to the transmitter in this node!
      listener.transmissionStarted();
      listener.transmissionEnded(data);
    }

    // And if this is the server, propagate to the others
    if (serverSocket != null) {
      dataSent(data, source);
    }
  }

  // Data was sent from the radio in the node (or other node) and should
  // be sent out to other nodes!!!
  public void dataSent(byte[] receivedData) {
    dataSent(receivedData, null);
  }

  // Data was sent either from radio, or came from another "radio" -
  // and if so it should be propagated to all others.
  public void dataSent(byte[] receivedData, ConnectionThread source) {
    if (connections != null && sendThread != null) {
      sendThread.send(receivedData, source);
    }
  }

  private static void printPacket(String prefix, byte[] data) {
    System.out.print("NetworkConnection: " + prefix);
    for (byte datum : data) {
      System.out.print(' ' + Utils.hex8(datum));
    }
    System.out.println();
  }

  private boolean connect(int port) {
    try {
      Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
      connections = ArrayUtils.add(ConnectionThread.class, connections, new ConnectionThread(socket));
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  // Records in Java should normally not contain arrays, but the code uses SendEvent solely as an internal container
  // without comparing it to other objects.
  @SuppressWarnings("ArrayRecordComponent")
  private record SendEvent(byte[] data, ConnectionThread source) {}

  final class SendThread implements Runnable {

    private final ArrayList<SendEvent> queue = new ArrayList<>();

    SendThread() {
      new Thread(this, "NetworkConnection.SendThread").start();
    }

    synchronized void send(byte[] receivedData, ConnectionThread source) {
      queue.add(new SendEvent(receivedData, source));
      notifyAll();
    }

    synchronized SendEvent getNext() throws InterruptedException {
      while (queue.isEmpty()) {
        wait();
      }
      return queue.remove(0);
    }

    private void sendPacket(SendEvent event) {
      ConnectionThread[] cthr = connections;
      if (cthr != null) {
        for (ConnectionThread connectionThread : cthr) {
          if (connectionThread.isClosed()) {
            connections = ArrayUtils.remove(connections, connectionThread);
            // Do not write back to the source
          } else if (connectionThread != event.source) {
            try {
              connectionThread.output.write(event.data, 0, event.data.length);
              connectionThread.output.flush();
            } catch (IOException e) {
              e.printStackTrace();
              connectionThread.close();
            }
          }
        }
        if (DEBUG) {
//              System.out.println("NetworkConnection: wrote " + receivedData.length + " bytes");
          printPacket("sent", event.data);
        }
      }
    }

    @Override
    public void run() {
      try {
        SendEvent event;
        do {
          event = getNext();
          if (event != null) {
            sendPacket(event);
          }
        } while (event != null);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  final class ConnectionThread implements Runnable {
    Socket socket;
    DataInputStream input;
    OutputStream output;

    ConnectionThread(Socket socket) throws IOException {
      this.socket = socket;
      input = new DataInputStream(socket.getInputStream());
      output = socket.getOutputStream();
      new Thread(this, "NetworkConnection.ConnectionThread").start();
    }

    void close() {
      try {
        input.close();
        socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      socket = null;
    }

    boolean isClosed() {
      return socket == null;
    }

    @Override
    public void run() {
      if (DEBUG) System.out.println("NetworkConnection: Started connection thread...");
      try {
        while (socket != null) {
          int len = input.read();
          if (len > 0) {
            byte[] buffer = new byte[len + 1];
            buffer[0] = (byte) (len & 0xff);
            input.readFully(buffer, 1, len);
            if (DEBUG) {
//              System.out.println("NetworkConnection: Read packet with " + len + " bytes");
              printPacket("read", buffer);
            }
            dataReceived(buffer, this);
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        close();
      }
    }
  }
}
