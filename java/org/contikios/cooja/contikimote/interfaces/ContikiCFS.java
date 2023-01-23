/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;

import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contiki FileSystem (CFS) interface (such as external flash).
 * <p>
 * Contiki variables:
 * <ul>
 * <li>char[] simCFSData
 * <li>char simCFSChanged (1=filesystem has been altered)
 * <li>int simCFSRead (bytes read from filesystem)
 * <li>int simCFSWritten (bytes written to filesystem)
 * </ul>
 * <p>
 * This observable notifies when the filesystem is used (read/write).
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Filesystem (CFS)")
public class ContikiCFS implements MoteInterface, PolledAfterActiveTicks {
  private static final Logger logger = LoggerFactory.getLogger(ContikiCFS.class);

  public static final int FILESYSTEM_SIZE = 4000; /* Configure CFS size here and in cfs-cooja.c */
  private final Mote mote;
  private final VarMemory moteMem;

  private int lastRead;
  private int lastWritten;
  private final LinkedHashMap<JPanel, Updates> labels = new LinkedHashMap<>();

  /**
   * Creates an interface to the filesystem at mote.
   *
   * @param mote Mote
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiCFS(Mote mote) {
    this.mote = mote;
    this.moteMem = new VarMemory(mote.getMemory());
  }

  @Override
  public void doActionsAfterTick() {
    if (moteMem.getByteValueOf("simCFSChanged") == 1) {
      lastRead = moteMem.getIntValueOf("simCFSRead");
      lastWritten = moteMem.getIntValueOf("simCFSWritten");

      moteMem.setIntValueOf("simCFSRead", 0);
      moteMem.setIntValueOf("simCFSWritten", 0);
      moteMem.setByteValueOf("simCFSChanged", (byte) 0);
      if (Cooja.isVisualized()) {
        final var now = mote.getSimulation().getSimulationTime();
        EventQueue.invokeLater(() -> {
          for (var update : labels.values()) {
            update.timeLabel.setText("Last change at time: " + now);
            update.readLabel.setText("Last change read bytes: " + getLastReadCount());
            update.writtenLabel.setText("Last change wrote bytes: " + getLastWrittenCount());
          }
        });
      }
    }
  }

  /**
   * Set filesystem data.
   *
   * @param data Data
   * @return True if operation successful
   */
  public boolean setFilesystemData(byte[] data) {
    if (data.length > FILESYSTEM_SIZE) {
      logger.error("Error. Filesystem data too large, skipping");
      return false;
    }

    moteMem.setByteArray("simCFSData", data);
    moteMem.setIntValueOf("simCFSSize", data.length);
    return true;
  }

  /**
   * Get filesystem data.
   *
   * @return Filesystem data
   */
  public byte[] getFilesystemData() {
    int size = moteMem.getIntValueOf("simCFSSize");
    return moteMem.getByteArray("simCFSData", size);
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

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    final JLabel lastTimeLabel = new JLabel("Last change at: ?");
    final JLabel lastReadLabel = new JLabel("Last change read bytes: 0");
    final JLabel lastWrittenLabel = new JLabel("Last change wrote bytes: 0");
    final JButton uploadButton = new JButton("Upload binary file");
    panel.add(lastTimeLabel);
    panel.add(lastReadLabel);
    panel.add(lastWrittenLabel);
    panel.add(uploadButton);

    uploadButton.addActionListener(e -> {
      byte[] fileData = readDialogFileBytes(null);

      // Write file data to CFS
      if (fileData != null) {
        if (setFilesystemData(fileData)) {
          logger.info("Done! (" + fileData.length + " bytes written to CFS)");
        }
      }
    });

    panel.setMinimumSize(new Dimension(140, 60));
    panel.setPreferredSize(new Dimension(140, 60));
    labels.put(panel, new Updates(lastTimeLabel, lastReadLabel, lastWrittenLabel));
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }

  /**
   * Opens a file dialog and returns the contents of the selected file or null if dialog aborted.
   *
   * @param parent Dialog parent, may be null
   * @return Binary contents of user selected file
   */
  public static byte[] readDialogFileBytes(Component parent) {
    // Choose file
    File file;
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setCurrentDirectory(new java.io.File("."));
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setDialogTitle("Select binary data");

    if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      file = fileChooser.getSelectedFile();
    } else {
      return null;
    }

    // Read file data
    long fileSize = file.length();
    byte[] fileData = new byte[(int) fileSize];

    FileInputStream fileIn;
    DataInputStream dataIn;
    int offset = 0;
    int numRead;
    try {
      fileIn = new FileInputStream(file);
      dataIn = new DataInputStream(fileIn);
      while (offset < fileData.length
          && (numRead = dataIn.read(fileData, offset, fileData.length - offset)) >= 0) {
        offset += numRead;
      }

      dataIn.close();
      fileIn.close();
    } catch (Exception ex) {
      logger.warn("Exception ex: " + ex);
      return null;
    }

    return fileData;
  }

  private record Updates(JLabel timeLabel, JLabel readLabel, JLabel writtenLabel) {}
}
