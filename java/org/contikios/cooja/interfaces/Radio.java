/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja.interfaces;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observable;
import java.util.Observer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import java.lang.Double;
import java.lang.Math;

import javax.swing.BoxLayout;
import javax.swing.JTextField;

import java.util.ArrayList;
import java.util.Collection;
import org.jdom.Element;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.contikimote.interfaces.ContikiRadio;

/**
 * A mote radio transceiver.
 *
 * @see ContikiRadio
 * @see CustomDataRadio
 * @see NoiseSourceRadio
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Radio")
public abstract class Radio extends MoteInterface {
  private static Logger logger = Logger.getLogger(Radio.class);

  /**
   * Events that radios should notify observers about.
   */
  public enum RadioEvent {
    UNKNOWN, HW_OFF, HW_ON,
    RECEPTION_STARTED, RECEPTION_FINISHED, RECEPTION_INTERFERED,
    TRANSMISSION_STARTED, TRANSMISSION_FINISHED,
    PACKET_TRANSMITTED, CUSTOM_DATA_TRANSMITTED
  }

  /**
   * Register the radio packet that is being received during a connection. This
   * packet should be supplied to the radio medium as soon as possible.
   *
   * @param packet Packet data
   */
  public abstract void setReceivedPacket(RadioPacket packet);

  /**
   * @return Last packet transmitted by radio
   */
  public abstract RadioPacket getLastPacketTransmitted();

  /**
   * @return Last packet received by radio
   */
  public abstract RadioPacket getLastPacketReceived();

  /**
   * Signal that a new reception just begun. This method should normally be
   * called from the radio medium.
   *
   * @see #signalReceptionEnd()
   */
  public abstract void signalReceptionStart();

  /**
   * Signal that the current reception was ended. This method should normally be
   * called from the radio medium on both destination and interfered radios.
   *
   * @see #signalReceptionStart()
   */
  public abstract void signalReceptionEnd();

  /**
   * Returns last event at this radio. This method should be used to learn the
   * reason when a radio notifies a change to observers.
   *
   * @return Last radio event
   */
  public abstract RadioEvent getLastEvent();

  /**
   * Returns true if this radio is transmitting, or just finished transmitting,
   * data.
   *
   * @see #isReceiving()
   * @return True if radio is transmitting data
   */
  public abstract boolean isTransmitting();

  /**
   * Returns true if this radio is receiving data.
   *
   * @see #isTransmitting()
   * @return True if radio is receiving data
   */
  public abstract boolean isReceiving();

  /**
   * Returns true if this radio had a connection that was dropped due to
   * interference.
   *
   * @return True if this radio is interfered
   */
  public abstract boolean isInterfered();

  /**
   * @return True if the simulated radio transceiver is on
   */
  public abstract boolean isRadioOn();

  /**
   * Interferes with any current reception. If this method is called, the packet
   * will be dropped. This method can be used to simulate radio interference
   * such as high background noise or radio packet collisions.
   *
   * When the radio is no longer interfered, the {@link #signalReceptionEnd()}
   * method must be called.
   *
   * @see #signalReceptionEnd()
   */
  public abstract void interfereAnyReception();

  /**
   * @return Current output power (dBm)
   */
  public abstract double getCurrentOutputPower();

  /**
   * @return Current output power indicator
   */
  public abstract int getCurrentOutputPowerIndicator();

  /**
   * @return Maximum output power indicator
   */
  public abstract int getOutputPowerIndicatorMax();
  
  /** 
   * Returns the current LQI-value. This might differ from platform to platform 
   * @return Current LQI value.
   *  
   * 
   */
  public int getLQI() throws UnsupportedOperationException {
	  throw new UnsupportedOperationException();
  }

  /**
   * Sets the LQI. This in not supported by all platforms. Also results may differ
   * from platform to platform. 
   *
   * @param lqi The current LQI
   */
  public void setLQI(int lqi){
  }

  /**
   * @return Current surrounding signal strength
   */
  public abstract double getCurrentSignalStrength();

  /**
   * Sets surrounding signal strength. This method should normally be called by
   * the radio medium.
   *
   * @param signalStrength
   *          Current surrounding signal strength
   */
  public abstract void setCurrentSignalStrength(double signalStrength);

  /**
   * Returns the current radio channel number.
   *
   * @return Current channel number
   */
  public abstract int getChannel();

  /**
   * Returns the radio position.
   * This is typically the position of the mote.
   *
   * @return Radio position
   */
  public abstract Position getPosition();

  /**
   * This is a convenience function.
   * It returns the mote, if any, that holds this radio.
   *
   * @return Mote
   */
  public abstract Mote getMote();


  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel(new BorderLayout());
    Box box = Box.createVerticalBox();

    final JLabel statusLabel = new JLabel("");
    final JLabel lastEventLabel = new JLabel("");
    final JLabel channelLabel = new JLabel("");
    final JLabel ssLabel = new JLabel("");
    final JButton updateButton = new JButton("Update SS");

    box.add(statusLabel);
    box.add(lastEventLabel);
    box.add(ssLabel);
    box.add(updateButton);
    box.add(channelLabel);

    updateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ssLabel.setText("Signal strength (not auto-updated): "
            + String.format("%1.1f", getCurrentSignalStrength()) + " dBm");
      }
    });

    final Observer observer = new Observer() {
      public void update(Observable obs, Object obj) {
        if (isTransmitting()) {
          statusLabel.setText("Transmitting");
        } else if (isReceiving()) {
          statusLabel.setText("Receiving");
        } else {
          statusLabel.setText("Listening");
        }

        lastEventLabel.setText("Last event: " + getLastEvent());
        ssLabel.setText("Signal strength (not auto-updated): "
            + String.format("%1.1f", getCurrentSignalStrength()) + " dBm");
        if (getChannel() == -1) {
          channelLabel.setText("Current channel: ALL");
        } else {
          channelLabel.setText("Current channel: " + getChannel());
        }

        
      }
    };

    this.addObserver(observer);
    observer.update(null, null);

    final String bitrate_ID = "bitrate";
    final String powerup_time_ID = "powerup_time_us";
    final String powerdn_time_ID = "powerdn_time_us";
    Collection<Element> cfg = getConfigXML();
    final boolean have_bitrate = (( getXMLText(cfg, bitrate_ID) != null ));
    final boolean have_poweron = (( getXMLText(cfg, powerup_time_ID) != null ));

    JPanel enter_panel = new JPanel();
    enter_panel.setLayout( new BoxLayout(enter_panel, BoxLayout.PAGE_AXIS) );

    if ( have_bitrate ) {
    	enter_panel.add(new DoubleParamField( "bitrate [kBps]:", bitrate_ID));
    }
    if ( have_poweron ) {
    	enter_panel.add(new DoubleParamField( "powerOn time[us]:", powerup_time_ID));
    	enter_panel.add(new DoubleParamField( "powerDn time[us]:", powerdn_time_ID));
    }

    panel.add(BorderLayout.NORTH, box);
    panel.add(BorderLayout.CENTER, enter_panel);
    panel.putClientProperty("intf_obs", observer);

    //this could need for shure DoubleParamField refresh
	//this.setChanged();
    //this.notifyObservers();

    return panel;
  }

  public void releaseInterfaceVisualizer(JPanel panel) {
    Observer observer = (Observer) panel.getClientProperty("intf_obs");
    if (observer == null) {
      logger.fatal("Error when releasing panel, observer is null");
      return;
    }

    this.deleteObserver(observer);
  }

  protected
  class DoubleParamField extends JPanel {
	
	DoubleParamField(final String label_text, final String ID){
	    //super( new BorderLayout() );
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS) );
	    setAlignmentX(Component.LEFT_ALIGNMENT);

        final Collection<Element> config = getConfigXML();
        String value = getXMLText(config, ID);
		//logger.info("radio:motes"+getMote().getID() + ": " + ID + " = " + value);

	    final JTextField field = new JTextField(value, 10);
	    field.addKeyListener( new DoubleConfigKeyAdapter(field, ID) ); //bitrate_field.addKeyListener
	    final JLabel label = new JLabel(label_text);
	    setMaximumSize( new Dimension( 
	    		  //Integer.MAX_VALUE
	    		  field.getPreferredSize().width + label.getPreferredSize().width
	    		, field.getPreferredSize().height ));	
	    add(label);
	    add(field);

	    final Observer observer = new Observer() {
	        public void update(Observable obs, Object obj) {
      		    //logger.info("radio: observed motes"+getMote().getID() + " on " + ID);
		        Collection<Element> config = getConfigXML();
		        String value = getXMLText(config, ID);
		        if (value != null)
		        	field.setText(value);
		    }
	      };
	    addObserver(observer);
		//logger.info("radio: edit motes"+getMote().getID() + " on " + ID);
	}
	
  }

  public
  double as_double(String text) {
      if (text == null) {
        return Double.NaN;
      }
      text = text.trim();
      if (text == null) {
          return Double.NaN;
      }

      return Double.parseDouble(text.replace(",","."));
  }
  
  public 
  boolean is_positive(double value) {
      if ( Double.isNaN(value) ){
		return false;
	  }
      if ( Double.isInfinite(value) ){
  		return false;
	  }
      return value > 0.0;
  }
  
  public 
  class DoubleConfigKeyAdapter extends KeyAdapter {
	  private JTextField field;
	  private String     ID;

	  DoubleConfigKeyAdapter(final JTextField afield, final String aID) {
		field = afield;
		ID    = aID;
		//super.KeyAdapter();
	  }
	  
	  @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {

        case KeyEvent.VK_ENTER: {
      	    double value = as_double( field.getText() );
      	    if (!is_positive(value) ) {
      		    //logger.info("radio: key enter motes"+getMote().getID() + " on " + ID);
      	    	setChanged();
      		    notifyObservers();
        		return;
            }
      	    setConfigAllMotes( ID, value );
        }//case KeyEvent.VK_ENTER

        }//switch (e.getKeyCode())
      }
  }

  // assign config parameter for all Radio Interface on all Motes
  public void setConfigAllMotes(final String ID, double value) {
	  //logger.info("radio: set motes "+ID + " -> "+ value);
      ArrayList<Element> config = new ArrayList<Element>();
      setXMLValue(config, ID, value);

      Mote[] motes = getMote().getSimulation().getMotes();
	  for (Mote m: motes) {
		Radio radio = m.getInterfaces().getRadio();
		if (radio != null) {
			radio.setConfigXML(config, true);
		}
	  }
  }

  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) 
  {
	if (visAvailable) {
		//logger.info("radio: set XML:"+getMote().getID());
		this.setChanged();
	    this.notifyObservers();
	}
  }


}
