/*
 * Copyright (c) 2014, TU Braunschweig.
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
 */

package org.contikios.cooja.interfaces;

import static org.contikios.cooja.util.EventTriggers.Update;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.mote.memory.MemoryInterface.SegmentMonitor;
import org.contikios.cooja.mote.memory.MemoryLayout;
import org.contikios.cooja.mote.memory.VarMemory;
import org.contikios.cooja.util.EventTriggers;
import org.contikios.cooja.util.IPUtils;

/**
 * Read-only interface to IPv6 address.
 *
 * @author Fredrik Osterlind
 * @author Enrico Joerns
 */
@ClassDescription("IP Addresses")
public class IPAddress implements MoteInterface {
  private static final int IPv6_MAX_ADDRESSES = 4;

  private enum IPv {
    NONE,
    IPv6
  }

  private final IPv ipVersion;

  private final Mote mote;
  private final VarMemory moteMem;
  private final MemoryLayout memLayout;
  private IPContainer localIPAddr;

  private final SegmentMonitor memMonitor;

  private final List<IPContainer> ipList = new ArrayList<>();

  private int ipv6_addr_size;
  private int ipv6_addr_list_offset;

  private final LinkedHashMap<JPanel, JLabel> labels = new LinkedHashMap<>();
  private final EventTriggers<Update, Mote> triggers = new EventTriggers<>();
  private final ArrayList<MonitorPost> monitors = new ArrayList<>();

  public IPAddress(final Mote mote) {
    this.mote = mote;
    moteMem = new VarMemory(mote.getMemory());
    memLayout = mote.getMemory().getLayout();

    /* If the ip memory sections changed, we recalculate addresses
     * and notify our observers.*/
    memMonitor = new MemoryInterface.SegmentMonitor() {
      int accessCount;
      long lastAccess;
      @Override
      public void memoryChanged(MemoryInterface memory, SegmentMonitor.EventType type, long address) {
        if (type != SegmentMonitor.EventType.WRITE) {
          return;
        }

        /* Wait until size and offset values are set initially,
         * then add memory monitor for each ip field */
        if ((ipv6_addr_list_offset == 0) || (ipv6_addr_size == 0)) {
          ipv6_addr_list_offset = moteMem.getByteValueOf("uip_ds6_netif_addr_list_offset");
          ipv6_addr_size = moteMem.getByteValueOf("uip_ds6_addr_size");
          /* If the variables just updated, add the final ip listeners */
          if ((ipv6_addr_list_offset != 0) && (ipv6_addr_size != 0)) {
            /* Add monitor for each IP region */
            for (int i = 0; i < IPv6_MAX_ADDRESSES; i++) {
              long addr_of_ip = moteMem.getVariableAddress("uip_ds6_if") // start address of interface
                      + ipv6_addr_list_offset // offset to ip address region
                      + i * ((long) ipv6_addr_size) // offset to ith ip address
                      + 1 + memory.getLayout().getPaddingBytesFor(
                              MemoryLayout.DataType.INT8,
                              MemoryLayout.DataType.INT16); // skip 'isused'
              moteMem.addMemoryMonitor(
                      EventType.WRITE,
                      addr_of_ip,
                      16, /* Size of ip address in byte */
                      memMonitor);
              monitors.add(new MonitorPost(addr_of_ip, 16));
            }
            /* Initial scan for IP address */
            updateIPAddresses();
            if (!ipList.isEmpty()) {
              updateUI();
            }
            // TODO: Remove other listeners?
          }
        } else {
          // Works when 'isused' bit is set first and address region is written sequentially

          /* check for sequential reading of 16 byte block */
          if (address == lastAccess + 1) {
            accessCount++;
            lastAccess = address;
            if (accessCount == 16) {
              updateIPAddresses();
              updateUI();
              lastAccess = 0;
            }
          }
          else {
            /* Check if ip write was interrupted unexpectedly last time */
            if (lastAccess != 0) {
              updateIPAddresses();
              updateUI();
            }
            accessCount = 1;
            lastAccess = address;
          }
        }
      }
    };

    /* Determine IP version an add MemoryMonitors */
    if (moteMem.variableExists("uip_ds6_netif_addr_list_offset")
            && moteMem.variableExists("uip_ds6_addr_size")
            && moteMem.variableExists("uip_ds6_if")) {
      ipVersion = IPv.IPv6;
      moteMem.addVarMonitor(
              SegmentMonitor.EventType.WRITE,
              "uip_ds6_netif_addr_list_offset",
              memMonitor);
      moteMem.addVarMonitor(
              SegmentMonitor.EventType.WRITE,
              "uip_ds6_addr_size",
              memMonitor);
    } else {
      ipVersion = IPv.NONE;
    }

    // initially look for IPs we already have
    updateIPAddresses();
  }

  /**
   * Returns true if IPv6 is supported by mote
   * @return true if IPv6 was detected
   */
  public boolean hasIP() {
    return !(ipVersion == IPv.NONE);
  }

  /**
   * Get local IP of mote.
   * @return local IP or null if not existing
   */
  public IPContainer getLocalIP() {
    return localIPAddr;
  }

  /**
   * Rereads IP addresses from memory and updates localIP entry.
   */
  private void updateIPAddresses() {
    ipList.clear();
    if (ipVersion == IPv.IPv6) {
      addIPv6Addresses();
      /* look for local ip addr */
      for (IPContainer c : ipList) {
        if (!c.isGlobal) {
          localIPAddr = c;
        }
      }
    }
  }

  /**
   * Rereads IPv6 addresses from memory.
   */
  private void addIPv6Addresses() {

    /* IpV6: Struct sizes and offsets */
    int ipv6NetworkInterfaceAddressOffset = moteMem.getByteValueOf("uip_ds6_netif_addr_list_offset");
    int ipv6AddressStructSize = moteMem.getByteValueOf("uip_ds6_addr_size");
    /* check if addresses were not set yet */
    if (ipv6NetworkInterfaceAddressOffset == 0 || ipv6AddressStructSize == 0) {
      return;
    }

    byte[] structData = moteMem.getByteArray(
            moteMem.getVariableAddress("uip_ds6_if") + ipv6NetworkInterfaceAddressOffset,
            IPv6_MAX_ADDRESSES * ipv6AddressStructSize);
    
    for (int addressIndex = 0; addressIndex < IPv6_MAX_ADDRESSES; addressIndex++) {
      int offset = addressIndex * ipv6AddressStructSize;
      byte isUsed = structData[offset];
      if (isUsed == 0) {
        continue;
      }
      byte[] addressData = new byte[16];
      System.arraycopy(
              structData, offset + 1 + memLayout.getPaddingBytesFor(
                      MemoryLayout.DataType.INT8,
                      MemoryLayout.DataType.INT16),/* ipaddr offset */
              addressData, 0, 16);

      if (((addressData[0] & (byte) 0xFF) == (byte) 0xFE) && ((addressData[1] & (byte) 0xFF) == (byte) 0x80)) {
        ipList.add(new IPContainer(addressIndex, addressData, false));
      }
      else {
        ipList.add(new IPContainer(addressIndex, addressData, true));
      }

    }
  }

  // -- MoteInterface overrides

  @Override
  public void removed() {
    if (memMonitor != null && ipVersion == IPv.IPv6) {
      moteMem.removeVarMonitor("uip_ds6_netif_addr_list_offset", memMonitor);
      moteMem.removeVarMonitor("uip_ds6_addr_size", memMonitor);
      for (var entry : monitors) {
        moteMem.removeMemoryMonitor(entry.address, entry.size, memMonitor);
      }
    }
  }

  // FIXME: Call inside updateIPAddresses() instead.
  private void updateUI() {
    triggers.trigger(Update.UPDATE, mote);
    if (!Cooja.isVisualized()) {
      return;
    }
    EventQueue.invokeLater(() -> {
      for (var label : labels.values()) {
        var ipStr = new StringBuilder();
        ipStr.append("<html>");
        for (IPContainer ipc : ipList) {
          if (ipVersion == IPv.IPv6) {
            ipStr.append(ipc.isGlobal() ? "Global" : "Local")
                    .append(" IPv6 address(#").append(ipc.getAddID()).append("): ").append(ipc).append("<br>");
          } else {
            ipStr.append("Unknown IP<br>");
          }
        }
        ipStr.append("</html>");
        label.setText(ipStr.toString());
      }
    });
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    final JLabel ipLabel = new JLabel();
    panel.add(ipLabel);
    labels.put(panel, ipLabel);
    updateUI();
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }

  public EventTriggers<Update, Mote> getTriggers() {
    return triggers;
  }

  /**
   * Holds a single IP address.
   * <p>
   * Note: The compressed IP version is already computed in constructor
   */
  public class IPContainer {

    private final boolean isGlobal;
    private final int addrIdx;
    private final String cprString;

    public IPContainer(int addidx, byte[] ip, boolean global) {
      this.addrIdx = addidx;
      this.isGlobal = global;
      if (ipVersion == IPv.IPv6) {
        cprString = IPUtils.getCompressedIPv6AddressString(ip);
      } else {
        cprString = "";
      }
    }

    public int getAddID() {
      return addrIdx;
    }

    public boolean isGlobal() {
      return isGlobal;
    }

    @Override
    public String toString() {
      return cprString;
    }
  }

  private record MonitorPost(long address, int size) {}
}
