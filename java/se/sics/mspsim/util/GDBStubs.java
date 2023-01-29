/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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
 * $Id: $
 *
 * -----------------------------------------------------------------
 *
 * GDBStubs
 *
 * Author  : Joakim Eriksson
 * Created : 31 mar 2008
 * Updated : $Date:$
 *           $Revision:$
 */
package se.sics.mspsim.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import se.sics.mspsim.core.EmulationException;
import se.sics.mspsim.core.MSP430Core;
import se.sics.mspsim.core.Memory;

public final class GDBStubs implements Runnable {

    private final static String OK = "OK";

    private ServerSocket serverSocket;
    private OutputStream output;
    private final MSP430Core cpu;

    public GDBStubs(MSP430Core cpu, int port) {
        this.cpu = cpu;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("GDBStubs open server socket port: " + port);
            new Thread(this, "GDBStubs.setupServer").start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final int[] buffer = new int[256];
    private int len;

    @Override
    public void run() {
        while (true) {
            try {
                Socket s = serverSocket.accept();

                DataInputStream input = new DataInputStream(s.getInputStream());
                output = s.getOutputStream();

                StringBuilder cmd = new StringBuilder();
                boolean readCmd = false;
                int c;
                while ((c = input.read()) != -1) {
                    System.out.println("GDBStubs: Read  " + c + " => "
                            + (char) c);
                    if (c == '#') {
                        readCmd = false;
                        /* ack the message */
                        output.write('+');
                        handleCmd(cmd.toString(), buffer);
                        cmd = new StringBuilder();
                        len = 0;
                    }
                    if (readCmd) {
                        cmd.append((char) c);
                        buffer[len++] = (c & 0xff);
                    }
                    if (c == '$') {
                        readCmd = true;
                    }
                }
            } catch (IOException | EmulationException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleCmd(String cmd, int[] cmdBytes)
    throws IOException, EmulationException {
        System.out.println("cmd: " + cmd);
        char c = cmd.charAt(0);
        switch (c) {
        case 'H':
            sendResponse(OK);
            break;
        case 'q':
            switch (cmd) {
                case "qC" -> sendResponse("QC1");
                case "qOffsets" -> sendResponse("Text=0;Data=0;Bss=0");
                case "qfThreadInfo" -> sendResponse("m 01");
                case "qsThreadInfo" -> sendResponse("l");
                case "qSymbol::" -> sendResponse(OK);

                //} else if ("qThreadExtraInfo,1".equals(cmd)){
                //              sendResponse(stringToHex("Stoped"));
                default -> {
                    System.out.println("Command unknown");
                    sendResponse("");
                }
            }

            break;
        case '?':
            sendResponse("S01");
            break;
        case 'g':
            readRegisters();
            break;
        case 'k': // kill
            sendResponse(OK);
            break;
        case 'm':
        case 'M':
        case 'X':
            String cmd2 = cmd.substring(1);
            String[] wdata = cmd2.split(":");
            int cPos = cmd.indexOf(':');
            if (cPos > 0) {
                /* only until length in first part */
                cmd2 = wdata[0];
            }
            String[] parts = cmd2.split(",");
            int addr = Integer.decode("0x" + parts[0]);
            int len = Integer.decode("0x" + parts[1]);
            StringBuilder data = new StringBuilder();
            Memory mem = cpu.getMemory();
            if (c == 'm') {
                System.out.println("Returning memory from: " + addr + " len = "
                        + len);
                /* This might be wrong - which is the correct byte order? */
                for (int i = 0; i < len; i++) {
                    data.append(Utils.hex8(mem.get(addr++, Memory.AccessMode.BYTE)));
                }
                sendResponse(data.toString());
            } else {
                System.out.println("Writing to memory at: " + addr + " len = "
                        + len + " with: "
                        + ((wdata.length > 1) ? wdata[1] : ""));
                cPos++;
                for (int i = 0; i < len; i++) {
                    System.out.println("Writing: " + cmdBytes[cPos] + " to "
                            + addr + " cpos=" + cPos);
                    mem.set(addr++, cmdBytes[cPos++], Memory.AccessMode.BYTE);
                }
                sendResponse(OK);
            }
            break;
        case 'C':
            sendResponse("S01");
            break;
        default:
            System.out.println("Command unknown");
            sendResponse("");
        }
    }

    private void readRegisters() throws IOException {
        StringBuilder regs = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            regs.append(Utils.hex8(cpu.reg[i] & 0xff)).append(Utils.hex8(cpu.reg[i] >> 8));
        }
        sendResponse(regs.toString());
    }

    public void sendResponse(String resp) throws IOException {
        output.write('$');
        int cs = 0;
        if (resp != null) {
            for (int i = 0; i < resp.length(); i++) {
                output.write(resp.charAt(i));
                System.out.print(resp.charAt(i));
                cs += resp.charAt(i);
            }
        }
        output.write('#');
        System.out.print('#');
        int c = (cs & 0xff) >> 4;
            if (c < 10) {
                c = c + '0';
            } else {
                c = c - 10 + 'a';
            }
            output.write((char) c);
            System.out.print((char) c);
            c = cs & 15;
            if (c < 10) {
                c = c + '0';
            } else {
                c = c - 10 + 'a';
            }
            output.write((char) c);
            System.out.println((char) c);
    }
}
