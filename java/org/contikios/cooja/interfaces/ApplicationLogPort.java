package org.contikios.cooja.interfaces;

import org.contikios.cooja.Mote;
import org.contikios.cooja.dialogs.LogUI;
import org.contikios.cooja.motes.AbstractApplicationMote;

public class ApplicationLogPort extends LogUI {
  private Mote mote;

  public ApplicationLogPort(Mote mote) {
    this.mote = mote;
  }

  /**
   * @param log Trigger log event from application
   */
  public void triggerLog(String log) {
    byte[] bytes = log.getBytes();
    for (byte b: bytes) {
      dataReceived(b);
    }
    dataReceived('\n');
  }

  public Mote getMote() {
    return mote;
  }
}
