package se.sics.mspsim.ui;

import java.awt.Component;

public interface ManagedWindow {

  void setSize(int width, int height);
  void setBounds(int x, int y, int width, int height);
  void pack();

  void add(Component component);
  void removeAll();

  boolean isVisible();

  void setVisible(boolean b);

  String getTitle();

  void setTitle(String string);

}
