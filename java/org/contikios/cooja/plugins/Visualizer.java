/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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
package org.contikios.cooja.plugins;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.basic.BasicInternalFrameUI;

import org.contikios.cooja.mote.BaseContikiMoteType;
import org.contikios.cooja.plugins.skins.DGRMVisualizerSkin;
import org.contikios.cooja.plugins.skins.LogisticLossVisualizerSkin;
import org.contikios.cooja.util.Annotations;
import org.contikios.cooja.util.EventTriggers;
import org.contikios.mrm.MRMVisualizerSkin;
import org.jdom2.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.SerialPort;
import org.contikios.cooja.plugins.skins.AddressVisualizerSkin;
import org.contikios.cooja.plugins.skins.AttributeVisualizerSkin;
import org.contikios.cooja.plugins.skins.GridVisualizerSkin;
import org.contikios.cooja.plugins.skins.IDVisualizerSkin;
import org.contikios.cooja.plugins.skins.LEDVisualizerSkin;
import org.contikios.cooja.plugins.skins.LogVisualizerSkin;
import org.contikios.cooja.plugins.skins.MoteTypeVisualizerSkin;
import org.contikios.cooja.plugins.skins.PositionVisualizerSkin;
import org.contikios.cooja.plugins.skins.TrafficVisualizerSkin;
import org.contikios.cooja.plugins.skins.UDGMVisualizerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulation visualizer supporting different visualizers
 * Motes are painted in the XY-plane, as seen from positive Z axis.
 * <p>
 * Supports drag-n-drop motes, right-click popup menu, and visualizers
 * <p>
 * Observes the simulation and all mote positions.
 *
 * @see #registerMoteMenuAction(Class)
 * @see #registerSimulationMenuAction(Class)
 * @see #registerVisualizerSkin(Class)
 * @see UDGMVisualizerSkin
 * @author Fredrik Osterlind
 * @author Enrico Jorns
 */
@ClassDescription("Network")
@PluginType(PluginType.PType.SIM_STANDARD_PLUGIN)
public class Visualizer extends VisPlugin implements HasQuickHelp {
  private static final Logger logger = LoggerFactory.getLogger(Visualizer.class);

  public static final int MOTE_RADIUS = 8;
  private static final Color[] DEFAULT_MOTE_COLORS = {Color.WHITE};

  private final Simulation simulation;
  private final JPanel canvas;
  private boolean loadedConfig;

  /* Viewport */
  private final AffineTransform viewportTransform;
  public int resetViewport;

  enum MotesActionState {
    NONE,
    // press to select mote
    SELECT_PRESS,
    // press
    DEFAULT_PRESS,
    // press to start panning
    PAN_PRESS,
    // panning the viewport
    PANNING,
    // moving a mote
    MOVING,
    // rectangular select
    SELECTING
  }

  /* All selected motes */
  public final Set<Mote> selectedMotes = new HashSet<>();
  /* Mote that was under curser while mouse press */
  private Mote cursorMote;

  private MotesActionState mouseActionState = MotesActionState.NONE;
  /* Position where mouse button was pressed */
  private Position pressedPos;

  private static final Cursor MOVE_CURSOR = new Cursor(Cursor.MOVE_CURSOR);
  private final Selection selection;

  /* Visualizers */
  private static final ArrayList<Class<? extends VisualizerSkin>> visualizerSkins
          = new ArrayList<>();

  static {
    /* Register default visualizer skins */
    registerVisualizerSkin(IDVisualizerSkin.class);
    registerVisualizerSkin(AddressVisualizerSkin.class);
    registerVisualizerSkin(LogVisualizerSkin.class);
    registerVisualizerSkin(LEDVisualizerSkin.class);
    registerVisualizerSkin(TrafficVisualizerSkin.class);
    registerVisualizerSkin(PositionVisualizerSkin.class);
    registerVisualizerSkin(GridVisualizerSkin.class);
    registerVisualizerSkin(MoteTypeVisualizerSkin.class);
    registerVisualizerSkin(AttributeVisualizerSkin.class);
  }
  private final ArrayList<VisualizerSkin> currentSkins = new ArrayList<>();

  /* Generic visualization */
  private final ArrayList<Mote> highlightedMotes = new ArrayList<>();
  private final static Color HIGHLIGHT_COLOR = Color.CYAN;

  /* Popup menu */
  public interface SimulationMenuAction {

    boolean isEnabled(Visualizer visualizer, Simulation simulation);

    String getDescription(Visualizer visualizer, Simulation simulation);

    void doAction(Visualizer visualizer, Simulation simulation);
  }

  public interface MoteMenuAction {

    boolean isEnabled(Visualizer visualizer, Mote mote);

    String getDescription(Visualizer visualizer, Mote mote);

    void doAction(Visualizer visualizer, Mote mote);
  }

  private final ArrayList<Class<? extends SimulationMenuAction>> simulationMenuActions
          = new ArrayList<>();
  private final ArrayList<Class<? extends MoteMenuAction>> moteMenuActions
          = new ArrayList<>();

  public Visualizer(Simulation simulation, Cooja gui) {
    super("Network", gui);
    this.simulation = simulation;

    /* Register external visualizers */
    registerVisualizerSkin(DGRMVisualizerSkin.class);
    registerVisualizerSkin(LogisticLossVisualizerSkin.class);
    registerVisualizerSkin(MRMVisualizerSkin.class);
    registerVisualizerSkin(UDGMVisualizerSkin.class);
    String[] skins = gui.getProjectConfig().getStringArrayValue(Visualizer.class, "SKINS");

    for (String skinClass : skins) {
      Class<? extends VisualizerSkin> skin = gui.tryLoadClass(this, VisualizerSkin.class, skinClass);
      if (registerVisualizerSkin(skin)) {
        logger.info("Registered external visualizer: " + skinClass);
      }
    }


    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    var viewMenu = new JMenu("View");
    viewMenu.addMenuListener(new MenuListener() {
      @Override
      public void menuSelected(MenuEvent e) {
        viewMenu.removeAll();
        // Mote-to-mote relations.
        var moteRelationsItem = new JCheckBoxMenuItem("Mote relations", showMoteToMoteRelations);
        moteRelationsItem.addItemListener(e1 -> {
          JCheckBoxMenuItem menuItem = ((JCheckBoxMenuItem) e1.getItem());
          showMoteToMoteRelations = menuItem.isSelected();
          repaint();
        });
        viewMenu.add(moteRelationsItem);
        viewMenu.add(new JSeparator());
        for (var skinClass : visualizerSkins) {
          // Should skin be enabled in this simulation?
          if (!Annotations.isCompatible(skinClass.getAnnotation(SupportedArguments.class), simulation.getRadioMedium())) {
            continue;
          }

          boolean activated = false;
          for (var skin : currentSkins) {
            if (skin.getClass() == skinClass) {
              activated = true;
              break;
            }
          }
          var item = new JCheckBoxMenuItem(Cooja.getDescriptionOf(skinClass), activated);
          item.putClientProperty("skinclass", skinClass);
          item.addItemListener(e1 -> {
            var menuItem = ((JCheckBoxMenuItem) e1.getItem());
            if (menuItem == null) {
              logger.error("No menu item");
              return;
            }

            var skinClass1 = (Class<VisualizerSkin>) menuItem.getClientProperty("skinclass");
            if (skinClass1 == null) {
              logger.error("Unknown visualizer skin class");
              return;
            }

            if (menuItem.isSelected()) {
              // Create and activate new skin.
              generateAndActivateSkin(skinClass1);
            } else {
              // Deactivate skin.
              VisualizerSkin skinToDeactivate = null;
              for (var skin : currentSkins) {
                if (skin.getClass() == skinClass1) {
                  skinToDeactivate = skin;
                  break;
                }
              }
              if (skinToDeactivate == null) {
                logger.error("Unknown visualizer to deactivate: " + skinClass1);
                return;
              }
              skinToDeactivate.setInactive();
              repaint();
              currentSkins.remove(skinToDeactivate);
            }
          });
          viewMenu.add(item);
        }
      }

      @Override
      public void menuDeselected(MenuEvent e) {
      }

      @Override
      public void menuCanceled(MenuEvent e) {
      }
    });
    JMenu zoomMenu = new JMenu("Zoom");

    menuBar.add(viewMenu);
    menuBar.add(zoomMenu);

    this.setJMenuBar(menuBar);

    Action zoomInAction = new AbstractAction("Zoom in") {
      @Override
      public void actionPerformed(ActionEvent e) {
        zoomToFactor(zoomFactor() * 1.2);
      }
    };
    zoomInAction.putValue(
            Action.ACCELERATOR_KEY,
            KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK)
    );
    JMenuItem zoomInItem = new JMenuItem(zoomInAction);
    zoomMenu.add(zoomInItem);

    Action zoomOutAction = new AbstractAction("Zoom out") {
      @Override
      public void actionPerformed(ActionEvent e) {
        zoomToFactor(zoomFactor() / 1.2);
      }
    };
    zoomOutAction.putValue(
            Action.ACCELERATOR_KEY,
            KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK)
    );
    JMenuItem zoomOutItem = new JMenuItem(zoomOutAction);
    zoomMenu.add(zoomOutItem);

    JMenuItem resetViewportItem = new JMenuItem("Reset viewport");
    resetViewportItem.addActionListener(e -> {
      resetViewport = 1;
      repaint();
    });
    zoomMenu.add(resetViewportItem);

    selection = new Selection();
    /* Main canvas */
    canvas = new JPanel() {

      {
        ToolTipManager.sharedInstance().registerComponent(this);
      }

      @Override
      public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (resetViewport > 0) {
          resetViewport();
          resetViewport--;
        }

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (VisualizerSkin skin : currentSkins) {
          skin.paintBeforeMotes(g);
        }
        paintMotes(g);
        for (VisualizerSkin skin : currentSkins) {
          skin.paintAfterMotes(g);
        }
        selection.drawSelection(g);
      }

      @Override
      public String getToolTipText(MouseEvent event) {
        Mote[] motes = findMotesAtPosition(event.getX(), event.getY());
        if (motes == null) {
          return null;
        }
        String fileName;
        if (motes[0].getType() instanceof BaseContikiMoteType moteType) {
          var file = moteType.getContikiSourceFile();
          if (file == null) {
            file = moteType.getContikiFirmwareFile();
          }
          fileName = file.getName();
        } else {
          fileName = "<none>";
        }
        return "<html><table cellspacing=\"0\" cellpadding=\"1\">" +
                "<tr><td>Type:</td><td>" +
                motes[0].getType().getIdentifier() +
                "</td></tr>" +
                "<tr><td>Runs:</td><td>" +
                fileName +
                "</td></tr>" +
                "</table></html>";
      }

    };
    canvas.setBackground(Color.WHITE);
    viewportTransform = new AffineTransform();

    this.add(BorderLayout.CENTER, canvas);

    /* Observe simulation and mote positions */
    simulation.getEventCentral().getPositionTriggers().addTrigger(this, (o, m) -> EventQueue.invokeLater(this::repaint));

    simulation.getMoteTriggers().addTrigger(this, (operation, mote) -> EventQueue.invokeLater(() -> {
      if (operation == EventTriggers.AddRemove.ADD) {
        resetViewport = 1;
      }
      repaint();
    }));

    /* Observe mote highlights */
    simulation.getMoteHighlightTriggers().addTrigger(this, (event, mote) -> {
      final Timer timer = new Timer(100, null);
      timer.addActionListener(e -> {
        // Count down.
        if (timer.getDelay() < 90) {
          timer.stop();
          highlightedMotes.remove(mote);
          repaint();
          return;
        }

        // Toggle highlight state.
        if (highlightedMotes.contains(mote)) {
          highlightedMotes.remove(mote);
        } else {
          highlightedMotes.add(mote);
        }
        timer.setDelay(timer.getDelay() - 1);
        repaint();
      });
      timer.start();
    });

    /* Observe mote relations */
    simulation.getMoteRelationsTriggers().addTrigger(this, (k, v) -> repaint());

    canvas.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "abort_action");
    canvas.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "delete_motes");

    canvas.getActionMap().put("abort_action", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        if (mouseActionState == MotesActionState.MOVING) {
          /* Reset positions to those of move start */
          for (Mote m : Visualizer.this.getSelectedMotes()) {
            double[] rstPos = Visualizer.this.moveStartPositions.get(m);
            m.getInterfaces().getPosition().setCoordinates(rstPos[0], rstPos[1], rstPos[2]);
          }
          mouseActionState = MotesActionState.NONE;
        }
        /* Always deselect all */
        Visualizer.this.getSelectedMotes().clear();
        repaint();
      }
    });

    canvas.getActionMap().put("delete_motes", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        Iterator<Mote> iter = Visualizer.this.getSelectedMotes().iterator();
        while (iter.hasNext()) {
          Mote m = iter.next();
          m.getSimulation().removeMote(m);
          iter.remove();
        }
      }
    });

    /* Popup menu */
    canvas.addMouseMotionListener(new MouseMotionAdapter() {
      private void handleMouseDrag(MouseEvent e) {
        Position currPos = transformPixelToPosition(e.getPoint());
        switch (mouseActionState) {
          case DEFAULT_PRESS:
            if (cursorMote == null) {
              mouseActionState = MotesActionState.PANNING;
            } else {
              // If we start moving with on a cursor mote, switch to MOVING.
              mouseActionState = MotesActionState.MOVING;
              // save start position
              for (Mote m : selectedMotes) {
                Position pos = m.getInterfaces().getPosition();
                moveStartPositions.put(m, new double[]{pos.getXCoordinate(), pos.getYCoordinate(), pos.getZCoordinate()});
              }
            }
            break;
          case MOVING:
            canvas.setCursor(MOVE_CURSOR);
            for (Mote moveMote : selectedMotes) {
              moveMote.getInterfaces().getPosition().setCoordinates(
                      moveStartPositions.get(moveMote)[0] + (currPos.getXCoordinate() - pressedPos.getXCoordinate()),
                      moveStartPositions.get(moveMote)[1] + (currPos.getYCoordinate() - pressedPos.getYCoordinate()),
                      moveStartPositions.get(moveMote)[2]);
              repaint();
            }
            break;
          case PAN_PRESS:
            mouseActionState = MotesActionState.PANNING;
            break;
          case PANNING:
            // The current mouse position should correspond to where panning started.
            viewportTransform.translate(currPos.getXCoordinate() - pressedPos.getXCoordinate(),
                    currPos.getYCoordinate() - pressedPos.getYCoordinate());
            repaint();
            break;
          case SELECT_PRESS:
            mouseActionState = MotesActionState.SELECTING;
            selection.setEnabled(true);
            break;
          case SELECTING:
            int pressedX = transformToPixelX(pressedPos.getXCoordinate());
            int pressedY = transformToPixelY(pressedPos.getYCoordinate());
            int currX = transformToPixelX(currPos.getXCoordinate());
            int currY = transformToPixelY(currPos.getYCoordinate());
            int startX = Math.min(pressedX, currX);
            int startY = Math.min(pressedY, currY);
            int width = Math.abs(pressedX - currX);
            int height = Math.abs(pressedY - currY);
            selection.setSelection(startX, startY, width, height);
            selectedMotes.clear();
            selectedMotes.addAll(Arrays.asList(findMotesInRange(startX, startY, width, height)));
            repaint();
            break;
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMouseDrag(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        handleMouseDrag(e);
      }
    });
    canvas.addMouseListener(new MouseAdapter() {
      private void handlePopupRequest(Point point) {
        var menu = new JPopupMenu();
        // Mote specific actions.
        final Mote[] motes = findMotesAtPosition(point.x, point.y);
        if (motes != null && motes.length > 0) {
          menu.add(new JSeparator());
          // Add registered mote actions.
          for (final Mote mote : motes) {
            menu.add(Cooja.createMotePluginsSubmenu(mote));
            for (var menuActionClass : moteMenuActions) {
              try {
                final MoteMenuAction menuAction = menuActionClass.getDeclaredConstructor().newInstance();
                if (menuAction.isEnabled(Visualizer.this, mote)) {
                  JMenuItem menuItem = new JMenuItem(menuAction.getDescription(Visualizer.this, mote));
                  menuItem.addActionListener(e -> menuAction.doAction(Visualizer.this, mote));
                  menu.add(menuItem);
                }
              } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e1) {
                logger.error("Error: " + e1.getMessage(), e1);
              }
            }
          }
        }

        // Simulation specific actions.
        menu.add(new JSeparator());
        var resetItem = new JMenuItem("Reset viewport");
        resetItem.addActionListener(e -> {
          resetViewport = 1;
          repaint();
        });
        menu.add(resetItem);
        if (getUI() instanceof BasicInternalFrameUI myUI) {
          final var northPane = myUI.getNorthPane();
          final var shouldRestore = northPane.getPreferredSize() == null || northPane.getPreferredSize().height == 0;
          var decorationItem = new JMenuItem((shouldRestore ? "Restore" : "Hide") + " window decorations");
          decorationItem.addActionListener(e -> {
            northPane.setPreferredSize(shouldRestore ? null : new Dimension(0, 0));
            revalidate();
            repaint();
          });
          menu.add(decorationItem);
        }
        for (var menuActionClass : simulationMenuActions) {
          try {
            final SimulationMenuAction menuAction = menuActionClass.getDeclaredConstructor().newInstance();
            if (menuAction.isEnabled(Visualizer.this, simulation)) {
              JMenuItem menuItem = new JMenuItem(menuAction.getDescription(Visualizer.this, simulation));
              menuItem.addActionListener(e -> menuAction.doAction(Visualizer.this, simulation));
              menu.add(menuItem);
            }
          } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e1) {
            logger.error("Error: " + e1.getMessage(), e1);
          }
        }

        // Visualizer skin actions.
        menu.add(new JSeparator());
        menu.setLocation(new Point(canvas.getLocationOnScreen().x + point.x, canvas.getLocationOnScreen().y + point.y));
        menu.setInvoker(canvas);
        menu.setVisible(true);
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
          handlePopupRequest(e.getPoint());
          return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
          handleMousePress(e);
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
          handlePopupRequest(e.getPoint());
          return;
        }

        if (SwingUtilities.isLeftMouseButton(e)) {
          switch (mouseActionState) {
            case PAN_PRESS:
              // ignore
              break;
            case SELECT_PRESS:
              if (cursorMote == null) { // Click on free canvas deselects all motes.
                selectedMotes.clear();
              } else { // Toggle selection for mote.
                if (selectedMotes.contains(cursorMote)) {
                  selectedMotes.remove(cursorMote);
                } else {
                  selectedMotes.add(cursorMote);
                }
              }
              break;
            case DEFAULT_PRESS:
              if (cursorMote == null) { // Click on free canvas deselects all motes.
                selectedMotes.clear();
              } else {                 // Click on mote selects single mote.
                selectedMotes.clear();
                selectedMotes.add(cursorMote);
              }
              break;
            case MOVING:
              // Release stops moving.
              canvas.setCursor(Cursor.getDefaultCursor());
              break;
            case SELECTING:
              // Release stops moving.
              selection.setEnabled(false);
              repaint();
              break;
          }
          // Release always stops previous actions.
          mouseActionState = MotesActionState.NONE;
          canvas.setCursor(Cursor.getDefaultCursor());
          repaint();
        }
      }
    });
    canvas.addMouseWheelListener(mwe -> {
      int x = mwe.getX();
      int y = mwe.getY();
      int rot = mwe.getWheelRotation();

      if (rot > 0) {
        zoomToFactor(zoomFactor() / 1.2, new Point(x, y));
      } else {
        zoomToFactor(zoomFactor() * 1.2, new Point(x, y));
      }
    });

    /* Register mote menu actions */
    registerMoteMenuAction(ButtonClickMoteMenuAction.class);
    registerMoteMenuAction(ShowLEDMoteMenuAction.class);
    registerMoteMenuAction(ShowSerialMoteMenuAction.class);

    registerMoteMenuAction(MoveMoteMenuAction.class);
    registerMoteMenuAction(DeleteMoteMenuAction.class);

    /* Drag and drop files to motes */
    DropTargetListener dTargetListener = new DropTargetListener() {
      @Override
      public void dragEnter(DropTargetDragEvent dtde) {
        if (acceptOrRejectDrag(dtde)) {
          dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }
        else {
          dtde.rejectDrag();
        }
      }

      @Override
      public void dragExit(DropTargetEvent dte) {
      }

      @Override
      public void dropActionChanged(DropTargetDragEvent dtde) {
        if (acceptOrRejectDrag(dtde)) {
          dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }
        else {
          dtde.rejectDrag();
        }
      }

      @Override
      public void dragOver(DropTargetDragEvent dtde) {
        if (acceptOrRejectDrag(dtde)) {
          dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }
        else {
          dtde.rejectDrag();
        }
      }

      @Override
      public void drop(DropTargetDropEvent dtde) {
        Transferable transferable = dtde.getTransferable();

        /* Only accept single files */
        File file;
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          dtde.rejectDrop();
          return;
        }

        dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

        try {
          List<File> list = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
          if (list.size() != 1) {
            return;
          }
          file = list.get(0);
        }
        catch (UnsupportedFlavorException | IOException e) {
          return;
        }

        if (file == null || !file.exists()) {
          return;
        }
        // TODO: implement drag and drop.
        logger.error("Drag and drop not implemented: " + file);
      }

      private boolean acceptOrRejectDrag(DropTargetDragEvent dtde) {
        Transferable transferable = dtde.getTransferable();

        /* Make sure one, and only one, mote exists under mouse pointer */
        Point point = dtde.getLocation();
        Mote[] motes = findMotesAtPosition(point.x, point.y);
        if (motes == null || motes.length != 1) {
          return false;
        }

        /* Only accept single files */
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          return false;
        }
        try {
          List<File> list = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
          if (list.size() != 1) {
            return false;
          }
          var file = list.get(0);
        }
        catch (UnsupportedFlavorException | IOException e) {
          return false;
        }

        /* Extract file extension */
        return true;
      }
    };
    canvas.setDropTarget(
            new DropTarget(canvas, DnDConstants.ACTION_COPY_OR_MOVE, dTargetListener, true, null)
    );

    resetViewport = 3; /* XXX Quick-fix */

    /* XXX HACK: here we set the position and size of the window when it appears on a blank simulation screen. */
    this.setLocation(1, 1);
    this.setSize(400, 400);
  }

  private void generateAndActivateSkin(Class<? extends VisualizerSkin> skinClass) {
    for (VisualizerSkin skin : currentSkins) {
      if (skinClass == skin.getClass()) {
        logger.warn("Selected visualizer already active: " + skinClass);
        return;
      }
    }

    if (!Annotations.isCompatible(skinClass.getAnnotation(SupportedArguments.class), simulation.getRadioMedium())) {
      return;
    }

    /* Create and activate new skin */
    try {
      VisualizerSkin newSkin = skinClass.getDeclaredConstructor().newInstance();
      newSkin.setActive(Visualizer.this.simulation, Visualizer.this);
      currentSkins.add(0, newSkin);
    }
    catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e1) {
      logger.error("Could not create skin: {}", skinClass.getName());
    }
    repaint();
  }

  @Override
  public void startPlugin() {
    super.startPlugin();
    if (loadedConfig) {
      return;
    }

    // Activate default skins.
    generateAndActivateSkin(IDVisualizerSkin.class);
    generateAndActivateSkin(GridVisualizerSkin.class);
    generateAndActivateSkin(DGRMVisualizerSkin.class);
    generateAndActivateSkin(TrafficVisualizerSkin.class);
    generateAndActivateSkin(UDGMVisualizerSkin.class);
    generateAndActivateSkin(LogisticLossVisualizerSkin.class);
    generateAndActivateSkin(MRMVisualizerSkin.class);
    String[] defaultSkins = Cooja.getExternalToolsSetting("VISUALIZER_DEFAULT_SKINS", "").split(";");
    for (String skin : defaultSkins) {
      if (skin.isEmpty()) {
        continue;
      }
      Class<? extends VisualizerSkin> skinClass
              = simulation.getCooja().tryLoadClass(this, VisualizerSkin.class, skin);
      generateAndActivateSkin(skinClass);
    }
  }

  public VisualizerSkin[] getCurrentSkins() {
    VisualizerSkin[] skins = new VisualizerSkin[currentSkins.size()];
    return currentSkins.toArray(skins);
  }

  /**
   * Register simulation menu action.
   *
   * @see SimulationMenuAction
   * @param menuAction Menu action
   */
  public void registerSimulationMenuAction(Class<? extends SimulationMenuAction> menuAction) {
    if (simulationMenuActions.contains(menuAction)) {
      return;
    }

    simulationMenuActions.add(menuAction);
  }

  public void unregisterSimulationMenuAction(Class<? extends SimulationMenuAction> menuAction) {
    simulationMenuActions.remove(menuAction);
  }

  /**
   * Register mote menu action.
   *
   * @see MoteMenuAction
   * @param menuAction Menu action
   */
  public void registerMoteMenuAction(Class<? extends MoteMenuAction> menuAction) {
    if (moteMenuActions.contains(menuAction)) {
      return;
    }

    moteMenuActions.add(menuAction);
  }

  public void unregisterMoteMenuAction(Class<? extends MoteMenuAction> menuAction) {
    moteMenuActions.remove(menuAction);
  }

  public static boolean registerVisualizerSkin(Class<? extends VisualizerSkin> skin) {
    if (visualizerSkins.contains(skin)) {
      return false;
    }
    visualizerSkins.add(skin);
    return true;
  }

  public static void unregisterVisualizerSkin(Class<? extends VisualizerSkin> skin) {
    visualizerSkins.remove(skin);
  }

  private boolean showMoteToMoteRelations = true;

  private void handleMousePress(MouseEvent mouseEvent) {
    int x = mouseEvent.getX();
    int y = mouseEvent.getY();

    pressedPos = transformPixelToPosition(mouseEvent.getPoint());

    // if we are in moving, we ignore the press (rest is handled by release)
    if (mouseActionState == MotesActionState.MOVING) {
      return;
    }

    // this is the state we have from pressing button
    final Mote[] foundMotes = findMotesAtPosition(x, y);
    if (foundMotes == null) {
      cursorMote = null;
    }
    else {
      // select top mote
      cursorMote = foundMotes[foundMotes.length - 1];
    }

    int modifiers = mouseEvent.getModifiersEx();

    /* translate input */
    if ((modifiers & MouseEvent.CTRL_DOWN_MASK) != 0) {
      mouseActionState = MotesActionState.SELECT_PRESS;
    } else if ((modifiers & MouseEvent.SHIFT_DOWN_MASK) != 0) {
      // only move viewport
      mouseActionState = MotesActionState.PAN_PRESS;
    } else {
      if (foundMotes == null) {
        // move viewport
        selectedMotes.clear();
      }
      else {
        // if this mote was not selected before, assume a new selection
        if (!selectedMotes.contains(cursorMote)) {
          selectedMotes.clear();
          selectedMotes.add(cursorMote);
        }
      }
      mouseActionState = MotesActionState.DEFAULT_PRESS;
    }
    repaint();
  }

  private final Map<Mote, double[]> moveStartPositions = new HashMap<>();

  private void beginMoveRequest(Mote selectedMote) {
    /* Save start positions and set move-start position to clicked mote */
    for (Mote m : selectedMotes) {
      Position pos = m.getInterfaces().getPosition();
      moveStartPositions.put(m, new double[]{
        pos.getXCoordinate(),
        pos.getYCoordinate(),
        pos.getZCoordinate()});
    }
    pressedPos.setCoordinates(
            selectedMote.getInterfaces().getPosition().getXCoordinate(),
            selectedMote.getInterfaces().getPosition().getYCoordinate(),
            selectedMote.getInterfaces().getPosition().getZCoordinate());

    mouseActionState = MotesActionState.MOVING;
  }

  private double zoomFactor() {
    return viewportTransform.getScaleX();
  }

  private void zoomToFactor(double newZoom) {
    zoomToFactor(newZoom, new Point(canvas.getWidth() / 2, canvas.getHeight() / 2));
  }

  private void zoomToFactor(double newZoom, Point zoomCenter) {
    Position center = transformPixelToPosition(zoomCenter);
    viewportTransform.setToScale(
            newZoom,
            newZoom
    );
    Position newCenter = transformPixelToPosition(zoomCenter);
    viewportTransform.translate(
            newCenter.getXCoordinate() - center.getXCoordinate(),
            newCenter.getYCoordinate() - center.getYCoordinate()
    );
    repaint();
  }

  /**
   * Returns all motes in rectangular range
   *
   * @param startX
   * @param startY
   * @param width
   * @param height
   * @return All motes in range
   */
  public Mote[] findMotesInRange(int startX, int startY, int width, int height) {
    List<Mote> motes = new ArrayList<>();
    for (Mote m : simulation.getMotes()) {
      Position pos = m.getInterfaces().getPosition();
      int moteX = transformToPixelX(pos.getXCoordinate());
      int moteY = transformToPixelY(pos.getYCoordinate());
      if (moteX > startX && moteX < startX + width
              && moteY > startY && moteY < startY + height) {
        motes.add(m);
      }
    }
    Mote[] motesArr = new Mote[motes.size()];
    return motes.toArray(motesArr);
  }

  /**
   * Returns all motes at given position.
   * <p>
   * If multiple motes were found at a position, the motes are returned
   * in the order they are painted on screen.
   * First mote in array is the bottom mote, last mote is the top mote.
   *
   * @param clickedX
   * X coordinate
   * @param clickedY
   * Y coordinate
   * @return All motes at given position
   */
  public Mote[] findMotesAtPosition(int clickedX, int clickedY) {
    double xCoord = transformToPositionX(clickedX);
    double yCoord = transformToPositionY(clickedY);

    ArrayList<Mote> motes = new ArrayList<>();

    // Calculate painted mote radius in coordinates
    double paintedMoteWidth = transformToPositionX(MOTE_RADIUS)
            - transformToPositionX(0);
    double paintedMoteHeight = transformToPositionY(MOTE_RADIUS)
            - transformToPositionY(0);

    for (int i = 0; i < simulation.getMotesCount(); i++) {
      Position pos = simulation.getMote(i).getInterfaces().getPosition();

      // Transform to unit circle before checking if mouse hit this mote
      double distanceX = Math.abs(xCoord - pos.getXCoordinate())
              / paintedMoteWidth;
      double distanceY = Math.abs(yCoord - pos.getYCoordinate())
              / paintedMoteHeight;

      if (distanceX * distanceX + distanceY * distanceY <= 1) {
        motes.add(simulation.getMote(i));
      }
    }
    if (motes.isEmpty()) {
      return null;
    }

    Mote[] motesArr = new Mote[motes.size()];
    return motes.toArray(motesArr);
  }

  public void paintMotes(Graphics g) {
    Mote[] allMotes = simulation.getMotes();

    /* Paint mote relations */
    if (showMoteToMoteRelations) {
      var relations = simulation.getMoteRelations();
      for (var r : relations) {
        Position sourcePos = r.source().getInterfaces().getPosition();
        Position destPos = r.dest().getInterfaces().getPosition();

        Point sourcePoint = transformPositionToPixel(sourcePos);
        Point destPoint = transformPositionToPixel(destPos);

        g.setColor(r.color() == null ? Color.black : r.color());
        drawArrow(g, sourcePoint.x, sourcePoint.y, destPoint.x, destPoint.y);
      }
    }

    for (Mote mote : allMotes) {

      /* Use the first skin's non-null mote colors */
      Color[] moteColors = null;
      for (VisualizerSkin skin : currentSkins) {
        moteColors = skin.getColorOf(mote);
        if (moteColors != null) {
          break;
        }
      }
      if (moteColors == null) {
        moteColors = DEFAULT_MOTE_COLORS;
      }

      Position motePos = mote.getInterfaces().getPosition();

      Point pixelCoord = transformPositionToPixel(motePos);
      int x = pixelCoord.x;
      int y = pixelCoord.y;

      if (!highlightedMotes.isEmpty() && highlightedMotes.contains(mote)) {
        g.setColor(HIGHLIGHT_COLOR);
        g.fillOval(x - MOTE_RADIUS, y - MOTE_RADIUS, 2 * MOTE_RADIUS,
                   2 * MOTE_RADIUS);
      }
      else if (moteColors.length >= 2) {
        g.setColor(moteColors[0]);
        g.fillOval(x - MOTE_RADIUS, y - MOTE_RADIUS, 2 * MOTE_RADIUS,
                   2 * MOTE_RADIUS);

        g.setColor(moteColors[1]);
        g.fillOval(x - MOTE_RADIUS / 2, y - MOTE_RADIUS / 2, MOTE_RADIUS,
                   MOTE_RADIUS);

      }
      else if (moteColors.length == 1) {
        g.setColor(moteColors[0]);
        g.fillOval(x - MOTE_RADIUS, y - MOTE_RADIUS, 2 * MOTE_RADIUS,
                   2 * MOTE_RADIUS);
      }

      if (getSelectedMotes().contains(mote)) {
        /* If mote is selected, highlight with red circle
         and semitransparent gray overlay */
        g.setColor(new Color(51, 102, 255));
        g.drawOval(x - MOTE_RADIUS, y - MOTE_RADIUS, 2 * MOTE_RADIUS,
                   2 * MOTE_RADIUS);
        g.drawOval(x - MOTE_RADIUS - 1, y - MOTE_RADIUS - 1, 2 * MOTE_RADIUS + 2,
                   2 * MOTE_RADIUS + 2);
        g.setColor(new Color(128, 128, 128, 128));
        g.fillOval(x - MOTE_RADIUS, y - MOTE_RADIUS, 2 * MOTE_RADIUS,
                   2 * MOTE_RADIUS);
      } else {
        g.setColor(Color.BLACK);
        g.drawOval(x - MOTE_RADIUS, y - MOTE_RADIUS, 2 * MOTE_RADIUS,
                   2 * MOTE_RADIUS);
      }
    }
  }

  // TODO: check if this can be a local variable inside drawArrow().
  private final Polygon arrowPoly = new Polygon();

  private void drawArrow(Graphics g, int xSource, int ySource, int xDest, int yDest) {
    double dx = xSource - xDest;
    double dy = ySource - yDest;
    double dir = Math.atan2(dx, dy);
    double len = Math.sqrt(dx * dx + dy * dy);
    dx /= len;
    dy /= len;
    len -= 9;
    xDest = xSource - (int) (dx * len);
    yDest = ySource - (int) (dy * len);
    g.drawLine(xDest, yDest, xSource, ySource);

    arrowPoly.reset();
    arrowPoly.addPoint(xDest, yDest);
    arrowPoly.addPoint(xDest + xCor(dir + 0.5), yDest + yCor(dir + 0.5));
    arrowPoly.addPoint(xDest + xCor(dir - 0.5), yDest + yCor(dir - 0.5));
    arrowPoly.addPoint(xDest, yDest);
    g.fillPolygon(arrowPoly);
  }

  private static int yCor(double dir) {
    return (int) (0.5 + 8 * Math.cos(dir));
  }

  private static int xCor(double dir) {
    return (int) (0.5 + 8 * Math.sin(dir));
  }

  /**
   * Reset transform to show all motes.
   */
  protected void resetViewport() {
    Mote[] motes = simulation.getMotes();
    if (motes.length == 0) {
      /* No motes */
      viewportTransform.setToIdentity();
      return;
    }

    final double BORDER_SCALE_FACTOR = 1.1;
    double smallX, bigX, smallY, bigY, scaleX, scaleY;

    /* Init values */
    {
      Position pos = motes[0].getInterfaces().getPosition();
      smallX = bigX = pos.getXCoordinate();
      smallY = bigY = pos.getYCoordinate();
    }

    /* Extremes */
    for (Mote mote : motes) {
      Position pos = mote.getInterfaces().getPosition();
      smallX = Math.min(smallX, pos.getXCoordinate());
      bigX = Math.max(bigX, pos.getXCoordinate());
      smallY = Math.min(smallY, pos.getYCoordinate());
      bigY = Math.max(bigY, pos.getYCoordinate());
    }

    /* Scale viewport */
    if (smallX == bigX) {
      scaleX = 1;
    }
    else {
      scaleX = (bigX - smallX) / (canvas.getWidth());
    }
    if (smallY == bigY) {
      scaleY = 1;
    }
    else {
      scaleY = (bigY - smallY) / (canvas.getHeight());
    }

    viewportTransform.setToIdentity();
    double newZoom = (1.0 / (BORDER_SCALE_FACTOR * Math.max(scaleX, scaleY)));
    viewportTransform.setToScale(
            newZoom,
            newZoom
    );

    /* Center visible motes */
    final double smallXfinal = smallX, bigXfinal = bigX, smallYfinal = smallY, bigYfinal = bigY;
    EventQueue.invokeLater(() -> {
      var viewMid = transformPixelToPosition(canvas.getWidth() / 2, canvas.getHeight() / 2);
      double motesMidX = (smallXfinal + bigXfinal) / 2.0;
      double motesMidY = (smallYfinal + bigYfinal) / 2.0;
      viewportTransform.translate(viewMid.getXCoordinate() - motesMidX, viewMid.getYCoordinate() - motesMidY);
      canvas.repaint();
    });
  }

  /**
   * Transforms a real-world position to a pixel which can be painted onto the
   * current sized canvas.
   *
   * @param pos
   * Real-world position
   * @return Pixel coordinates
   */
  public Point transformPositionToPixel(Position pos) {
    return transformPositionToPixel(
            pos.getXCoordinate(),
            pos.getYCoordinate(),
            pos.getZCoordinate()
    );
  }

  /**
   * Transforms real-world coordinates to a pixel which can be painted onto the
   * current sized canvas.
   *
   * @param x Real world X
   * @param y Real world Y
   * @param z Real world Z (ignored)
   * @return Pixel coordinates
   */
  public Point transformPositionToPixel(double x, double y, double z) {
    return new Point(transformToPixelX(x), transformToPixelY(y));
  }

  /**
   * @return Canvas
   */
  public JPanel getCurrentCanvas() {
    return canvas;
  }

  /**
   * Transforms a pixel coordinate to a real-world. Z-value will always be 0.
   *
   * @param pixelPos
   * On-screen pixel coordinate
   * @return Real world coordinate (z=0).
   */
  public Position transformPixelToPosition(Point pixelPos) {
    return transformPixelToPosition(pixelPos.x, pixelPos.y);
  }

  public Position transformPixelToPosition(int x, int y) {
    Position position = new Position(null);
    position.setCoordinates(
            transformToPositionX(x),
            transformToPositionY(y),
            0.0
    );
    return position;
  }

  private int transformToPixelX(double x) {
    return (int) (viewportTransform.getScaleX() * x + viewportTransform.getTranslateX());
  }

  private int transformToPixelY(double y) {
    return (int) (viewportTransform.getScaleY() * y + viewportTransform.getTranslateY());
  }

  private double transformToPositionX(int x) {
    return (x - viewportTransform.getTranslateX()) / viewportTransform.getScaleX();
  }

  private double transformToPositionY(int y) {
    return (y - viewportTransform.getTranslateY()) / viewportTransform.getScaleY();
  }

  @Override
  public void closePlugin() {
    for (VisualizerSkin skin : currentSkins) {
      skin.setInactive();
    }
    currentSkins.clear();
    simulation.getMoteHighlightTriggers().deleteTriggers(this);
    simulation.getMoteRelationsTriggers().deleteTriggers(this);
    simulation.getEventCentral().getPositionTriggers().deleteTriggers(this);
    simulation.getMoteTriggers().deleteTriggers(this);
  }

  /**
   * @return Selected mote
   */
  public Set<Mote> getSelectedMotes() {
    return selectedMotes;
  }

  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    /* Show mote-to-mote relations */
    if (showMoteToMoteRelations) {
      element = new Element("moterelations");
      element.setText("" + true);
      config.add(element);
    }

    /* Skins */
    for (int i = currentSkins.size() - 1; i >= 0; i--) {
      VisualizerSkin skin = currentSkins.get(i);
      element = new Element("skin");
      element.setText(skin.getClass().getName());
      config.add(element);
    }

    /* Viewport */
    element = new Element("viewport");
    double[] matrix = new double[6];
    viewportTransform.getMatrix(matrix);
    element.setText(
            matrix[0] + " "
            + matrix[1] + " "
            + matrix[2] + " "
            + matrix[3] + " "
            + matrix[4] + " "
            + matrix[5]
    );
    config.add(element);

    /* Hide decorations */
    BasicInternalFrameUI ui = (BasicInternalFrameUI) getUI();
    if (ui.getNorthPane().getPreferredSize() == null
            || ui.getNorthPane().getPreferredSize().height == 0) {
      element = new Element("hidden");
      config.add(element);
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    loadedConfig = true;
    showMoteToMoteRelations = false;

    for (Element element : configXML) {
      switch (element.getName()) {
        case "skin":
          String wanted = element.getText();
          /* Backwards compatibility: se.sics -> org.contikios */
          if (wanted.startsWith("se.sics")) {
            wanted = wanted.replaceFirst("^se\\.sics", "org.contikios");
          }
          for (final var skinClass : visualizerSkins) {
            if (wanted.equals(skinClass.getName()) || wanted.equals(Cooja.getDescriptionOf(skinClass))) {
              generateAndActivateSkin(skinClass);
              wanted = null;
              break;
            }
          }
          if (wanted != null) {
            logger.warn("Could not load visualizer: " + element.getText());
          }
          break;
        case "moterelations":
          showMoteToMoteRelations = true;
          break;
        case "viewport":
          try {
            String[] matrix = element.getText().split(" ");
            viewportTransform.setTransform(
                    Double.parseDouble(matrix[0]),
                  Double.parseDouble(matrix[1]),
                  Double.parseDouble(matrix[2]),
                  Double.parseDouble(matrix[3]),
                  Double.parseDouble(matrix[4]),
                  Double.parseDouble(matrix[5])
            );
            resetViewport = 0;
          }
          catch (NumberFormatException e) {
            logger.warn("Bad viewport: " + e.getMessage());
            resetViewport();
          } break;
        case "hidden":
          BasicInternalFrameUI ui = (BasicInternalFrameUI) getUI();
          ui.getNorthPane().setPreferredSize(new Dimension(0, 0));
          break;
      }
    }
    return true;
  }

  protected static class ButtonClickMoteMenuAction implements MoteMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Mote mote) {
      return mote.getInterfaces().getButton() != null
              && !mote.getInterfaces().getButton().isPressed();
    }

    @Override
    public String getDescription(Visualizer visualizer, Mote mote) {
      return "Click button on " + mote;
    }

    @Override
    public void doAction(Visualizer visualizer, Mote mote) {
      mote.getInterfaces().getButton().clickButton();
    }
  }

  protected static class DeleteMoteMenuAction implements MoteMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Mote mote) {
      return true;
    }

    @Override
    public String getDescription(Visualizer visualizer, Mote mote) {
      if (visualizer.getSelectedMotes().contains(mote) && visualizer.getSelectedMotes().size() > 1) {
        return "Delete selected Motes";
      } else {
        return "Delete " + mote;
      }
    }

    @Override
    public void doAction(Visualizer visualizer, Mote mote) {

      /* If the currently clicked mote is not in the current mote selection,
       * select it exclusively */
      if (!visualizer.getSelectedMotes().contains(mote)) {
        visualizer.getSelectedMotes().clear();
        visualizer.getSelectedMotes().add(mote);
      }

      /* Invoke 'delete_motes' action */
      visualizer.canvas.getActionMap().get("delete_motes").actionPerformed(null);
    }
  }

  protected static class ShowLEDMoteMenuAction implements MoteMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Mote mote) {
      return mote.getInterfaces().getLED() != null;
    }

    @Override
    public String getDescription(Visualizer visualizer, Mote mote) {
      return "Show LEDs on " + mote;
    }

    @Override
    public void doAction(Visualizer visualizer, Mote mote) {
      Simulation simulation = mote.getSimulation();
      LED led = mote.getInterfaces().getLED();
      if (led == null) {
        return;
      }

      /* Extract description (input to plugin) */
      String desc = Cooja.getDescriptionOf(mote.getInterfaces().getLED());

      MoteInterfaceViewer viewer
              = (MoteInterfaceViewer) simulation.getCooja().tryStartPlugin(
                      MoteInterfaceViewer.class,
                      simulation,
                      mote);
      if (viewer == null) {
        return;
      }
      viewer.setSelectedInterface(desc);
      viewer.pack();
    }
  }

  protected static class ShowSerialMoteMenuAction implements MoteMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Mote mote) {
      for (MoteInterface intf : mote.getInterfaces().getInterfaces()) {
        if (intf instanceof SerialPort) {
          return true;
        }
      }
      return false;
    }

    @Override
    public String getDescription(Visualizer visualizer, Mote mote) {
      return "Show serial port on " + mote;
    }

    @Override
    public void doAction(Visualizer visualizer, Mote mote) {
      Simulation simulation = mote.getSimulation();
      SerialPort serialPort = null;
      for (MoteInterface intf : mote.getInterfaces().getInterfaces()) {
        if (intf instanceof SerialPort) {
          serialPort = (SerialPort) intf;
          break;
        }
      }

      if (serialPort == null) {
        return;
      }

      /* Extract description (input to plugin) */
      String desc = Cooja.getDescriptionOf(serialPort);

      MoteInterfaceViewer viewer
              = (MoteInterfaceViewer) simulation.getCooja().tryStartPlugin(
                      MoteInterfaceViewer.class,
                      simulation,
                      mote);
      if (viewer == null) {
        return;
      }
      viewer.setSelectedInterface(desc);
      viewer.pack();
    }
  }

  protected static class MoveMoteMenuAction implements MoteMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Mote mote) {
      return true;
    }

    @Override
    public String getDescription(Visualizer visualizer, Mote mote) {
      if (visualizer.getSelectedMotes().contains(mote) && visualizer.getSelectedMotes().size() > 1) {
        return "Move selected Motes";
      } else {
        return "Move " + mote;
      }
    }

    @Override
    public void doAction(Visualizer visualizer, Mote mote) {
      /* If the currently clicked mote is note in the current mote selection,
       * select it exclusively */
      if (!visualizer.getSelectedMotes().contains(mote)) {
        visualizer.getSelectedMotes().clear();
        visualizer.getSelectedMotes().add(mote);
      }
      visualizer.beginMoveRequest(mote);
    }
  }

  @Override
  public String getQuickHelp() {
    return "<b>Network</b> "
            + "<p>The network window shows the positions of simulated motes. "
            + "<p>"
            + "It is possible to zoom <em>(Mouse wheel)</em> and pan <em>(Shift+Mouse drag)</em> the current view. "
            + "Motes can be moved by dragging them. "
            + "You can add/remove motes to/from selection <em>(CTRL+Left click)</em> "
            + "or use the rectangular selection tool <em>(CTRL+Mouse drag)</em>. "
            + "Mouse right-click motes for options menu. "
            + "<p>"
            + "The network window supports different views. "
            + "Each view provides some specific information, such as the IP addresses of motes. "
            + "Multiple views can be active at the same time. "
            + "Use the View menu to select views. ";
  }

  private static class Selection {

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean enable;

    void setSelection(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    void setEnabled(boolean enable) {
      this.enable = enable;
    }

    void drawSelection(Graphics g) {
      /* only draw if enabled */
      if (!enable) {
        return;
      }
      Graphics2D g2d = (Graphics2D) g;
      g2d.setColor(new Color(64, 64, 64, 10));
      g2d.fillRect(x, y, width, height);

      BasicStroke dashed
              = new BasicStroke(1.0f,
                                BasicStroke.CAP_BUTT,
                                BasicStroke.JOIN_MITER,
                                10.0f, new float[]{5.0f}, 0.0f);
      g2d.setColor(Color.BLACK);
      g2d.setStroke(dashed);
      g2d.drawRect(x, y, width, height);
    }
  }

}
