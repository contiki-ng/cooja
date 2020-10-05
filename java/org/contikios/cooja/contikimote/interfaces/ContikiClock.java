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
 */

package org.contikios.cooja.contikimote.interfaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;

import java.text.NumberFormat;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.BoxLayout;
import javax.swing.JTextField;
import javax.swing.JFormattedTextField;
import javax.swing.JComboBox;
import javax.swing.text.NumberFormatter;
import javax.swing.BorderFactory;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.Mote;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.ContikiMote;
import org.contikios.cooja.contikimote.ContikiMoteInterface;
import org.contikios.cooja.interfaces.Clock;
import org.contikios.cooja.interfaces.PolledAfterAllTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.mote.memory.VarMemory;

/**
 * Clock mote interface. Controls Contiki time.
 *
 * Contiki variables:
 * <ul>
 * <li>clock_time_t simCurrentTime
 * <li>rtimer_clock_t simRtimerCurrentTicks
 * <li>clock_time_t simEtimerNextExpirationTime
 * <li>rtimer_clock_t simEtimerNextExpirationTime
 * <li>int simEtimerProcessRunValue
 * <li>int simRtimerProcessRunValue
 * <li>int simEtimerPending
 * </ul>
 *
 * Core interface:
 * <ul>
 * <li>clock_interface
 * </ul>
 * <p>
 *
 * This observable never notifies.
 *
 * @author Fredrik Osterlind
 */
public class ContikiClock extends Clock implements ContikiMoteInterface, PolledBeforeActiveTicks, PolledAfterAllTicks {
  private static Logger logger = Logger.getLogger(ContikiClock.class);

  private Simulation simulation;
  private ContikiMote mote;
  private VarMemory moteMem;

  private long moteTime; /* Microseconds */
  private long timeDrift; /* Microseconds */

  final boolean TRACE_CLOCKDRIFT = false;
  /* mote time rate drift range [ppm*Hz] */
  private int  clockDriftMin_ppm = 0;
  private int  clockDriftMax_ppm = 0;
  /* if drift value > 0 - mote rtc go faster then simulation time,
   *                < 0 - mote go slower 
   */
  private int  clockDrift_ppm    = 0;
  
  private long clockDriftRate = 0; // [tick] period for drifting [simTicks]/(1[driftTick])
  private long clockDrift;       // drift estimated from clockDriftOrigin [Microseconds]
  private long clockDriftOrigin; // last time, since drift 1tick changed at.

  public final long etimerClockDefault = Simulation.MILLISECOND;
  public long   etimerClockSecond = etimerClockDefault;
  public long   etimerPeriod      = (long)(Simulation.MILLISECOND*1000/etimerClockSecond);

  // informer about controls changes
  private class controlsInformer extends Observable {
      public void refresh( Object x ) {
          if (this.countObservers() == 0) {
            return;
          }
          setChanged();
          notifyObservers(x);
      }
  }

  private controlsInformer controlsInform = new controlsInformer();

  /**
   * @param mote Mote
   *
   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiClock(Mote mote) {
    this.simulation = mote.getSimulation();
    this.mote = (ContikiMote) mote;
    this.moteMem = new VarMemory(mote.getMemory());
    timeDrift = 0;
    moteTime = 0;
  }

  public static String[] getCoreInterfaceDependencies() {
    return new String[]{"clock_interface"};
  }

  public void setTime(long newTime) {
    moteTime = newTime;
    if (moteTime > 0) {
      moteMem.setIntValueOf("simCurrentTime", (int)(newTime/etimerPeriod));
    }
  }

  public void setDrift(long drift) {
    //adjust drift so that it simTime+drift == rtcTime(); 
    long currentSimulationTime = simulation.getSimulationTime();
    drift -= (currentSimulationTime - rtcTime());

    this.timeDrift = drift;
    setTime(rtcTime() + timeDrift);
  }

  public long getDrift() {
    return timeDrift;
  }

  public long getTime() {
    return moteTime;
  }
  
  public void setDeviation(double deviation) {
    logger.fatal("Can't change deviation");;
  }

  public double getDeviation() {
  	return 1.0;
  }

  /* @brief assign mote rTime clock drift to random value in specified range
   * @arg min_ppm - if min==max==0, no drift uses
   * @arg max_ppm
   * 
   * if drift value > 0 - mote rtc go faster then simulation time,
   *                < 0 - mote go slower
   * */
  public void setClockDrift(int min_ppm, int max_ppm) {
      clockDriftMin_ppm = min_ppm;
      clockDriftMax_ppm = max_ppm;
      if (min_ppm == max_ppm) {
          clockDrift_ppm = min_ppm;
      }
      else {
          clockDrift_ppm = min_ppm 
                 + simulation.getRandomGenerator().nextInt(max_ppm - min_ppm);
      }

      if (clockDrift_ppm != 0)
          clockDriftRate = 1000000/clockDrift_ppm;
      else
          clockDriftRate = 0;
  }

  /* @brief rTime now - for current simulation time
   * @return mote rTimer value. it may drift from simulation time by clockDrift */
  public long rtcTime() {
      long currentSimulationTime = simulation.getSimulationTime();
      return rtcTimeAt(currentSimulationTime);
  } 

  public long rtcTimeAt(long simulationTime) {
      long rTime = simulationTime + clockDrift;
      if (clockDriftRate == 0)
          return rTime;
      long drift = (simulationTime - clockDriftOrigin)/clockDriftRate;
      return rTime + drift;
  }

  public void doActionsBeforeTick() {
    /* Update time */

    long currentSimulationTime = simulation.getSimulationTime();
    long currentTime = rtcTimeAt(currentSimulationTime);
    setTime(currentTime + timeDrift);
    moteMem.setInt64ValueOf("simRtimerCurrentTicks", currentTime);

    if (clockDriftRate == 0) {
        clockDriftOrigin = currentSimulationTime;
        return;
    }
    else if ( (currentSimulationTime - clockDriftOrigin) > clockDriftRate) {
    //promote rtc drift to new time
    long drift = (currentSimulationTime - clockDriftOrigin)/clockDriftRate;
    clockDriftOrigin += drift * clockDriftRate;
    clockDrift       += drift;
    if (TRACE_CLOCKDRIFT)
    logger.info("mote"+mote.getID()+" clock["+currentSimulationTime + "] drift: +" + drift + " -> "+clockDrift);
    }
  }

  public void doActionsAfterTick() {
    controlsInform.refresh(this);

    long currentTime = rtcTime();//simulation.getSimulationTime();

    /* Always schedule for Rtimer if anything pending */
    if (moteMem.getIntValueOf("simRtimerPending") != 0) {
      long nextTime = moteMem.getInt64ValueOf("simRtimerNextExpirationTime");
      scheduleNextWakeupRTC(nextTime);
    }

    long   rtimerWaitTime = currentTime;
    /* Request next tick for remaining events / timers */
    int processRunValue = moteMem.getIntValueOf("simProcessRunValue");
    if (processRunValue != 0) {
      /* Handle next Contiki event in one millisecond */
            rtimerWaitTime = currentTime + Simulation.MILLISECOND;
      scheduleNextWakeupRTC( rtimerWaitTime );
      return;
    }

    int etimersPending = moteMem.getIntValueOf("simEtimerPending");
    if (etimersPending == 0) {
      /* No timers */
      return;
    }

    /* Request tick next wakeup time for Etimer */
    long etimerNextExpirationTime = (long)moteMem.getInt32ValueOf("simEtimerNextExpirationTime") * etimerPeriod;
    long etimerTimeToNextExpiration = etimerNextExpirationTime - moteTime;
    if (etimerTimeToNextExpiration <= 0) {
      /* logger.warn(mote.getID() + ": Event timer already expired, but has been delayed: " + etimerTimeToNextExpiration); */
      /* Wake up in one millisecond to handle a missed Etimer task
       * which may be blocked by busy waiting such as one in
       * radio_send(). Scheduling it in a shorter time than one
       * millisecond, e.g., one microsecond, seems to be worthless and
       * it would cause unnecessary CPU usage. */
        if (TRACE_CLOCKDRIFT)
            logger.info("mote"+mote.getID()+ " etimer: " + currentTime + " -> "+etimerPeriod);
      scheduleNextWakeupRTC(currentTime + etimerPeriod);
    } else {
        if (TRACE_CLOCKDRIFT)
            logger.info("mote"+mote.getID()+ " etimer: " + currentTime + " + "+etimerTimeToNextExpiration);
      scheduleNextWakeupRTC(currentTime + etimerTimeToNextExpiration);
    }
  }

  public void scheduleNextWakeupRTC(long rtcTime) {
      long simTime = rtcTime - clockDrift;
      if (clockDriftRate != 0) {
          //evaluate next simulation wake Time, correcting by drift rate
          long drift = 0;

          if ( (simTime - clockDriftOrigin) >= clockDriftRate) {
              drift = (simTime - clockDriftOrigin)/clockDriftRate;
    
              if (TRACE_CLOCKDRIFT)
              logger.info("mote"+mote.getID()+" clock["+simulation.getSimulationTime() 
                          + "] wake:"+rtcTime()+" -> rtc:" + rtcTime 
                          + " > sim:" + simTime + " - "+drift);
    
              simTime -= drift;
          }

          //precise adjust simulation time , to rtcTime
          for ( long rtcNextTime = rtcTimeAt(simTime)
                  ; rtcNextTime != rtcTime
                  ; rtcNextTime = rtcTimeAt(simTime)
                  )
          {
              if (TRACE_CLOCKDRIFT)
              logger.info("mote"+mote.getID()+" clock correct sim:" + simTime
                      + " -> rtc:" + rtcNextTime 
                      + " - " + (rtcNextTime - rtcTime));

              if (drift == -(rtcNextTime - rtcTime))
              if (drift >= 1)
              {
                  // looks ringing around decimated rtc value, so awake before that
                  break;
              }
              drift = (rtcNextTime - rtcTime);
              simTime -= drift;
              if (drift >= clockDriftRate) {
                  simTime -= drift/clockDriftRate;
              }
          }
      }
      mote.scheduleNextWakeup( simTime );
  }


  public JPanel getInterfaceVisualizer() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS ));
      final NumberFormat form = NumberFormat.getNumberInstance();

      JPanel infoPanel = new JPanel();
      infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.PAGE_AXIS ));
      //infoPanel.setBorder( BorderFactory.createMatteBorder(1, 1, 1, 1, Color.blue) );

      JPanel infoSimTime = new JPanel();
      infoSimTime.setLayout(new BoxLayout(infoSimTime, BoxLayout.LINE_AXIS ));
      final JLabel simTimeLabel = new JLabel();
      final JLabel timeDriftLabel = new JLabel();
      infoSimTime.add(simTimeLabel);
      infoSimTime.add(timeDriftLabel);
      infoPanel.add(infoSimTime);

      JPanel infoRTCTime = new JPanel();
      infoRTCTime.setLayout(new BoxLayout(infoRTCTime, BoxLayout.LINE_AXIS ));
      final JLabel moteTimeLabel = new JLabel();
      final JLabel clockDriftLabel = new JLabel();
      infoRTCTime.add(moteTimeLabel);
      infoRTCTime.add(clockDriftLabel);
      infoPanel.add(infoRTCTime);

      final JLabel clockDriftPPMLabel = new JLabel();
      infoPanel.add(clockDriftPPMLabel);

      JComponent[] labels = {infoRTCTime, infoSimTime, clockDriftPPMLabel }; 
      for (JComponent m: labels ) {
          //m.setHorizontalAlignment(JLabel.LEFT);
          m.setAlignmentX(Component.LEFT_ALIGNMENT);
      }

      //FIX: BoxLayout need equal aligns: for page_axis - same horiz align
      infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
      panel.add(infoPanel);

      JPanel panelClockDrift = new JPanel();
      panelClockDrift.setLayout(new BoxLayout(panelClockDrift, BoxLayout.PAGE_AXIS));
      panel.add(panelClockDrift);

      final String PPM_exact    = "X[ppm]";
      final String PPM_simetry  = "+/- X[ppm]";
      final String PPM_range    = "min .. max [ppm]";
      String[] ppm_styles = new String[]{PPM_exact, PPM_simetry, PPM_range};
      JComboBox<String> ppmStyleCombo = new JComboBox<String>( ppm_styles ) ;
      //FIX: BoxLayout need equal aligns: for page_axis - same horiz align
      panelClockDrift.setAlignmentX(Component.LEFT_ALIGNMENT);
      panelClockDrift.add(ppmStyleCombo);

      JPanel panelClockDriftPPMs = new JPanel();
      panelClockDriftPPMs.setLayout(new BoxLayout(panelClockDriftPPMs, BoxLayout.LINE_AXIS));

      ContikiClock.IntegerInputField    ppmMinText = new ContikiClock.IntegerInputField(clockDriftMin_ppm, 6) {
          @Override
          public void on_enter() {
                clockDriftMin_ppm = Integer.parseInt(getText());
                String ppm_style = (String)ppmStyleCombo.getSelectedItem();
                if (ppm_style == PPM_exact) {
                    clockDriftMax_ppm = clockDriftMin_ppm;
                }
                else if (ppm_style == PPM_simetry) {
                    clockDriftMax_ppm = -clockDriftMin_ppm;
                }
                setClockDrift(clockDriftMin_ppm, clockDriftMax_ppm);
          }
      };
      IntegerInputField    ppmMaxText = new ContikiClock.IntegerInputField(clockDriftMax_ppm, 6) {
          @Override
          public void on_enter() {
              clockDriftMax_ppm = Integer.parseInt(getText());
              String ppm_style = (String)ppmStyleCombo.getSelectedItem();
              if (ppm_style == PPM_exact) {
                  clockDriftMin_ppm = clockDriftMax_ppm;
              }
              else if (ppm_style == PPM_simetry) {
                  clockDriftMin_ppm = -clockDriftMax_ppm;
              }
              setClockDrift(clockDriftMin_ppm, clockDriftMax_ppm);
          }
      };
      JLabel        ppmRangeLabel   =  new JLabel(" .. ");
      JLabel        ppmSymetryLabel =  new JLabel("+/-:");
      panelClockDriftPPMs.add(ppmSymetryLabel);
      panelClockDriftPPMs.add(ppmMinText);
      panelClockDriftPPMs.add(ppmRangeLabel);
      panelClockDriftPPMs.add(ppmMaxText);

      panelClockDrift.add(panelClockDriftPPMs);
      panelClockDrift.setMaximumSize( new Dimension(Integer.MAX_VALUE, panelClockDrift.getPreferredSize().height) );

      ppmStyleCombo.setEnabled(true);
      ActionListener refreshPPMAction = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String ppm_style = (String)ppmStyleCombo.getSelectedItem();
          if (ppm_style == PPM_exact) {
              ppmMinText.setVisible(false);
              ppmRangeLabel.setVisible(false);
              ppmSymetryLabel.setVisible(false);
              ppmMaxText.setVisible(true);
              //adjust ppm range as maximum assigned value
              if (clockDriftMax_ppm != clockDriftMin_ppm) {
                      clockDriftMin_ppm = clockDriftMax_ppm;
              }
          }
          else if (ppm_style == PPM_simetry) {
              ppmMinText.setVisible(false);
              ppmRangeLabel.setVisible(false);
              ppmSymetryLabel.setVisible(true);
              ppmMaxText.setVisible(true);
              //adjust ppm range as +/- maximum assigned value
              if (clockDriftMax_ppm != -clockDriftMin_ppm) {
                  clockDriftMin_ppm = -clockDriftMax_ppm;
              }
          }
          else { //PPM_range
              ppmSymetryLabel.setVisible(false);
              ppmMinText.setVisible(true);
              ppmRangeLabel.setVisible(true);
              ppmMaxText.setVisible(true);
          }
          ppmMinText.setValue( clockDriftMin_ppm );
          ppmMaxText.setValue( clockDriftMax_ppm );
        };
      };
      ppmStyleCombo.addActionListener(refreshPPMAction);

      // choose apropriated ppm style from current ppm range
      if (clockDriftMin_ppm == clockDriftMax_ppm)
          ppmStyleCombo.setSelectedItem(PPM_exact);
      else if (-clockDriftMin_ppm == clockDriftMax_ppm)
          ppmStyleCombo.setSelectedItem(PPM_simetry);
      else
          ppmStyleCombo.setSelectedItem(PPM_range);
      //refreshPPMAction.perform();

      Observer observer = new Observer() {
        private long lastTime = -1;

        @Override
        public void update(Observable obs, Object obj) {

            if (System.currentTimeMillis() - lastTime < 250) {
                //do not refresh too frequently
                return;
            }
            lastTime = System.currentTimeMillis();

            simTimeLabel.setText("   sim:" + form.format(simulation.getSimulationTime()) );
            timeDriftLabel.setText("    +drift:" + form.format(timeDrift) );
            moteTimeLabel.setText("   rtc:" + form.format(rtcTime()) );

            boolean isClockDrift = (clockDriftMin_ppm != 0) || (clockDriftMax_ppm != 0);
            clockDriftLabel.setVisible(isClockDrift);
            clockDriftPPMLabel.setVisible(isClockDrift);

            clockDriftLabel.setText("    +drift:" + form.format(clockDrift) );
            if (clockDriftMin_ppm == clockDriftMax_ppm)
                clockDriftPPMLabel.setText("drift[ppm]:" + form.format(clockDriftMin_ppm) );
            else
                clockDriftPPMLabel.setText("drift[ppm]:" 
                                        + form.format(clockDriftMin_ppm)
                                        + " .. "
                                        + form.format(clockDriftMax_ppm)
                                        );
        }
      };
      this.addObserver(observer);

      // Saving observer reference for releaseInterfaceVisualizer
      panel.putClientProperty("intf_obs", observer);
      observer.update(null, null);
      controlsInform.addObserver(observer);

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

    public abstract
    class IntegerInputField extends JFormattedTextField {

        public IntegerInputField(int value, int columns) {
            this();
            setColumns(columns);
            setValue(value);
        }

        public IntegerInputField() {
            super();

            NumberFormat nf = NumberFormat.getIntegerInstance();
            nf.setGroupingUsed(false);
            setFormatter(new NumberFormatter(nf));
            
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                  switch (e.getKeyCode()) {
                  case KeyEvent.VK_ENTER: {
                      on_enter();
                      e.consume();
                      break;
                  }
                  }//case KeyEvent.VK_ENTER
                }
            });
        }

        public abstract void on_enter();

        public void setValue(int x) {
            this.setText(String.valueOf(x));
        }
    };
    
  public Collection<Element> getConfigXML() {
      ArrayList<Element> config = new ArrayList<Element>();
      Element element;
      if ( (clockDriftMax_ppm != 0) || (clockDriftMin_ppm != 0) ) {
          element = new Element("drift_max_ppm");
          element.setText("" + clockDriftMax_ppm);
          config.add(element);
          element = new Element("drift_min_ppm");
          element.setText("" + clockDriftMin_ppm);
          config.add(element);
      }

    if (!config.isEmpty()) {
      return config;
    }
    else
      return null;
  }

  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
      for (Element element : configXML) {

          if (element.getName().equals("drift_max_ppm")) {
              setClockDrift(clockDriftMin_ppm, Integer.parseInt(element.getText()) );
          }
          if (element.getName().equals("drift_min_ppm")) {
              setClockDrift(Integer.parseInt(element.getText()) , clockDriftMax_ppm );
          }
      }
  }
  
}
