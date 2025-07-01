package org.contikios.cooja.interfaces;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import javax.swing.JPanel;
import org.contikios.cooja.Mote;
import org.contikios.cooja.util.EventTriggers;

public class ApplicationLED extends LED {
    private final Mote mote;
    private byte currentLedValue;

    public static final byte LEDS_GREEN = 1;
    public static final byte LEDS_YELLOW = 2;
    public static final byte LEDS_RED = 4;
    
    private static final Color DARK_GREEN = new Color(0, 50, 0);
    private static final Color DARK_YELLOW = new Color(50, 50, 0);
    private static final Color DARK_RED = new Color(50, 0, 0);
    private static final Color GREEN = new Color(0, 255, 0);
    private static final Color YELLOW = new Color(255, 255, 0);
    private static final Color RED = new Color(255, 0, 0);

     public ApplicationLED(Mote mote) {
       this.mote = mote;
     }

     @Override
     public boolean isAnyOn() {
       return currentLedValue > 0;
     }

     @Override
     public boolean isGreenOn() {
       return (currentLedValue & LEDS_GREEN) > 0;
     }

     @Override
     public boolean isYellowOn() {
       return (currentLedValue & LEDS_YELLOW) > 0;
     }

     @Override
     public boolean isRedOn() {
       return (currentLedValue & LEDS_RED) > 0;
     }

     public void setLED(int led) {
       boolean ledChanged;
       ledChanged = led != currentLedValue;

       currentLedValue = (byte) led;
       if (ledChanged) {
         triggers.trigger(EventTriggers.Update.UPDATE, mote);
       }
     }

     @Override
     public JPanel getInterfaceVisualizer() {
       final JPanel panel = new JPanel() {
         @Override
         public void paintComponent(Graphics g) {
           super.paintComponent(g);

           int x = 20;
           int y = 25;
           int d = 25;

           if (isGreenOn()) {
             g.setColor(GREEN);
             g.fillOval(x, y, d, d);
             g.setColor(Color.BLACK);
             g.drawOval(x, y, d, d);
           } else {
             g.setColor(DARK_GREEN);
             g.fillOval(x + 5, y + 5, d-10, d-10);
           }

           x += 40;

           if (isRedOn()) {
             g.setColor(RED);
             g.fillOval(x, y, d, d);
             g.setColor(Color.BLACK);
             g.drawOval(x, y, d, d);
           } else {
             g.setColor(DARK_RED);
             g.fillOval(x + 5, y + 5, d-10, d-10);
           }

           x += 40;

           if (isYellowOn()) {
             g.setColor(YELLOW);
             g.fillOval(x, y, d, d);
             g.setColor(Color.BLACK);
             g.drawOval(x, y, d, d);
           } else {
             g.setColor(DARK_YELLOW);
             g.fillOval(x + 5, y + 5, d-10, d-10);
           }
         }
       };
       panel.setMinimumSize(new Dimension(140, 60));
       panel.setPreferredSize(new Dimension(140, 60));
       triggers.addTrigger(panel, (operation, mote) -> EventQueue.invokeLater(panel::repaint));
       return panel;
     }

     @Override
     public void releaseInterfaceVisualizer(JPanel panel) {
       triggers.deleteTriggers(panel);
     }
}
