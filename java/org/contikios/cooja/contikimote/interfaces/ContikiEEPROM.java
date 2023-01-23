/*
 * Copyright (c) 2014, Swedish Institute of Computer Science.
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

package org.contikios.cooja.contikimote.interfaces;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Formatter;
import java.util.LinkedHashMap;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.jdom2.Element;

import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contiki EEPROM interface
 * <p>
 * Contiki variables:
 * <ul>
 * <li>char[] simEEPROMData
 * <li>char simEEPROMChanged (1=EEPROM has been altered)
 * <li>int simEEPROMRead (bytes read from EEPROM)
 * <li>int simEEPROMWritten (bytes written to EEPROM)
 * </ul>
 * <p>
 *
 * This observable notifies when the eeprom is used (read/write).
 *
 * @author Claes Jakobsson (based on ContikiCFS by Fredrik Osterlind)
 */
@ClassDescription("EEPROM")
public class ContikiEEPROM implements MoteInterface, PolledAfterActiveTicks {
  private static final Logger logger = LoggerFactory.getLogger(ContikiEEPROM.class);

  public static final int EEPROM_SIZE = 1024; /* Configure EEPROM size here and in eeprom.c. Should really be multiple of 16 */
  private final Mote mote;
  private final VarMemory moteMem;
  private int lastRead;
  private int lastWritten;
  private final LinkedHashMap<JPanel, Updates> labels = new LinkedHashMap<>();

  /**
   * Creates an interface to the EEPROM at mote.
   *
   * @param mote Mote
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiEEPROM(Mote mote) {
    this.mote = mote;
    this.moteMem = new VarMemory(mote.getMemory());
  }

  @Override
  public void doActionsAfterTick() {
    if (moteMem.getByteValueOf("simEEPROMChanged") == 1) {
      lastRead = moteMem.getIntValueOf("simEEPROMRead");
      lastWritten = moteMem.getIntValueOf("simEEPROMWritten");
      moteMem.setIntValueOf("simEEPROMRead", 0);
      moteMem.setIntValueOf("simEEPROMWritten", 0);
      moteMem.setByteValueOf("simEEPROMChanged", (byte) 0);
      if (Cooja.isVisualized()) {
        final var currentTime = mote.getSimulation().getSimulationTime();
        EventQueue.invokeLater(() -> {
          for (var updates : labels.values()) {
            updates.lastTimeLabel.setText("Last change at time: " + currentTime);
            updates.lastReadLabel.setText("Last change read bytes: " + getLastReadCount());
            updates.lastWrittenLabel.setText("Last change wrote bytes: " + getLastWrittenCount());
            redrawDataView(updates.dataArea);
          }
        });
      }
    }
  }

  /**
   * Set EEPROM data.
   *
   * @param data Data
   * @return True if operation successful
   */
  public boolean setEEPROMData(byte[] data) {
    if (data.length > EEPROM_SIZE) {
      logger.error("Error. EEPROM data too large, skipping");
      return false;
    }

    moteMem.setByteArray("simEEPROMData", data);
    return true;
  }

  /**
   * Get EEPROM data.
   *
   * @return Filesystem data
   */
  public byte[] getEEPROMData() {
    return moteMem.getByteArray("simEEPROMData", EEPROM_SIZE);
  }

  /**
   * @return Read bytes count last change.
   */
  public int getLastReadCount() {
    return lastRead;
  }

  /**
   * @return Written bytes count last change.
   */
  public int getLastWrittenCount() {
    return lastWritten;
  }

  static String byteArrayToPrintableCharacters(byte[] data, int offset, int length) {
      StringBuilder sb = new StringBuilder();
      for (int i = offset; i < offset + length; i++) {
        sb.append(data[i] > 31 && data[i] < 128 ? (char) data[i] : '.');
      }
      return sb.toString();
  }
  
  static String byteArrayToHexList(byte[] data, int offset, int length) {
      StringBuilder sb = new StringBuilder();
            
      for (int i = 0; i < length; i++) {
          byte h = (byte) ((int) data[offset + i] >> 4);
          byte l = (byte) ((int) data[offset + i] & 0xf);
          sb.append((char)(h < 10 ? 0x30 + h : 0x61 + h - 10));
          sb.append((char)(l < 10 ? 0x30 + l : 0x61 + l - 10));
          sb.append(' ');
          if (i % 8 == 7 && i != length - 1) {
              sb.append(' ');
          }
      }
    
      return sb.toString();
  }
  
  void redrawDataView(JTextArea textArea) {
      StringBuilder sb = new StringBuilder();
      Formatter fmt = new Formatter(sb);
      byte[] data = getEEPROMData();
      
      for (int i = 0; i < EEPROM_SIZE; i += 16) {
          fmt.format("%04d  %s | %s |\n", i, byteArrayToHexList(data, i, 16), byteArrayToPrintableCharacters(data, i, 16));
      }
      textArea.setText(sb.toString());
      textArea.setCaretPosition(0);
  }
  
  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    var lastTimeLabel = new JLabel("Last change at: ?");
    var lastReadLabel = new JLabel("Last change read bytes: 0");
    var lastWrittenLabel = new JLabel("Last change wrote bytes: 0");
    final JButton uploadButton = new JButton("Upload binary file");
    final JButton clearButton = new JButton("Reset EEPROM to zero");
    var dataViewArea = new JTextArea();
    final JScrollPane dataViewScrollPane = new JScrollPane(dataViewArea);
    
    panel.add(lastTimeLabel);
    panel.add(lastReadLabel);
    panel.add(lastWrittenLabel);
    panel.add(uploadButton);
    panel.add(clearButton);
    
    panel.add(dataViewScrollPane);
    
    uploadButton.addActionListener(e -> {
      try {
        byte[] eepromData = readDialogEEPROMBytes(uploadButton);

        // Write file data to EEPROM
        if (eepromData != null) {
          if (eepromData.length > EEPROM_SIZE) {
            JOptionPane.showMessageDialog(uploadButton, "EEPROM data too large. "
                                                  + eepromData.length + " > " + EEPROM_SIZE + " bytes.",
                                          "Error uploading EEPROM data", JOptionPane.ERROR_MESSAGE);
          } else if (setEEPROMData(eepromData)) {
            logger.info("Done! (" + eepromData.length + " bytes written to EEPROM)");
            redrawDataView(dataViewArea);
          } else {
            JOptionPane.showMessageDialog(uploadButton, "Failed to upload EEPROM data to mote",
                                          "Error uploading EEPROM data", JOptionPane.ERROR_MESSAGE);
          }
        }
      } catch (IOException ex) {
        logger.warn("Error uploading EEPROM data", ex);
        JOptionPane.showMessageDialog(uploadButton, "Failed to read EEPROM data: " + ex.getMessage(),
                                      "Error uploading EEPROM data", JOptionPane.ERROR_MESSAGE);
      }
    });

    clearButton.addActionListener(e -> {
      byte[] eepromData = new byte[EEPROM_SIZE];

      if (setEEPROMData(eepromData)) {
          logger.info("Done! (EEPROM reset to zero)");
      }
      redrawDataView(dataViewArea);
    });

    panel.setMinimumSize(new Dimension(140, 60));
    panel.setPreferredSize(new Dimension(140, 60));

    dataViewArea.setLineWrap(false);
    dataViewArea.setEditable(false);
    dataViewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    dataViewScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    dataViewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    redrawDataView(dataViewArea);
    labels.put(panel, new Updates(lastTimeLabel, lastReadLabel, lastWrittenLabel, dataViewArea));
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }

  @Override
  public Collection<Element> getConfigXML() {
      var data = getEEPROMData();
      if (!isEmpty(data)) {
        var config = new ArrayList<Element>();
        var element = new Element("eeprom");
        element.setText(Base64.getEncoder().encodeToString(data));
        config.add(element);
        return config;
      }
      return null;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
      for (Element element : configXML) {
        if (element.getName().equals("eeprom")) {
          setEEPROMData(Base64.getDecoder().decode(element.getText()));
          break;
        }
      }
  }

  /**
   * Opens a file dialog and returns the contents of the selected file or null if dialog aborted.
   *
   * @param parent Dialog parent, may be null
   * @return Binary contents of user selected file
   */
  public static byte[] readDialogEEPROMBytes(Component parent) throws IOException {
    // Choose file
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setCurrentDirectory(new File("."));
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setDialogTitle("Select binary data");

    if (fileChooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
      return null;
    }

    // Read file data
    File file = fileChooser.getSelectedFile();
    long fileSize = file.length();
    byte[] fileData = new byte[(int) fileSize];

    try (var dataIn = new DataInputStream(new FileInputStream(file))) {
      dataIn.readFully(fileData);
      return fileData;
    } catch (IOException ex) {
      throw new IOException("Failed to read file '" + file + "'", ex);
    }
  }

  private static boolean isEmpty(byte[] data) {
    for (byte b : data) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private record Updates(JLabel lastTimeLabel, JLabel lastReadLabel, JLabel lastWrittenLabel,
                         JTextArea dataArea) {}
}
