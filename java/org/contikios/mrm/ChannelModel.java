/*
 * Copyright (c) 2011, Swedish Institute of Computer Science.
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

package org.contikios.mrm;

import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import javax.swing.tree.DefaultMutableTreeNode;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.DirectionalAntennaRadio;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.cooja.util.EventTriggers;
import org.contikios.mrm.statistics.GaussianWrapper;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The channel model object in MRM is responsible for calulating propagation
 * impact on packets being sent in the radio medium.
 * <p>
 * By registering as a settings observer on this channel model, other parts will
 * be notified if the settings change.
 * <p>
 * TODO Add better support for different signal strengths
 *
 * @author Fredrik Osterlind
 */
public class ChannelModel {
  private static final Logger logger = LoggerFactory.getLogger(ChannelModel.class);

  private static final double C = 299792458; /* m/s */

  enum TransmissionData { SIGNAL_STRENGTH, SIGNAL_STRENGTH_VAR, SNR, SNR_VAR, PROB_OF_RECEPTION, DELAY_SPREAD, DELAY_SPREAD_RMS}

  private final HashMap<Parameter,Object> parametersDefaults;
  private final HashMap<Parameter,Object> parameters = new HashMap<>();

  // Parameters used for speeding up calculations
  private boolean needToPrecalculateFSPL = true;
  private double paramFSPL;

  private ObstacleWorld myObstacleWorld = new ObstacleWorld();

  /* Log mode: visualize signal components */
  private boolean logMode;
  private StringBuilder logInfo;
  private List<Line2D> loggedRays;

  // Ray tracing components temporary vector
  private final List<List<Line2D>> calculatedVisibleSides = new ArrayList<>();
  private final List<Point2D> calculatedVisibleSidesSources = new ArrayList<>();
  private final List<Line2D> calculatedVisibleSidesLines = new ArrayList<>();
  private final List<AngleInterval> calculatedVisibleSidesAngleIntervals = new ArrayList<>();
  private static final int maxSavedVisibleSides = 30; // Max size of lists above

  /**
   * Notifies observers when settings are changed. The parameter is null unless
   * a single setting is changed.
   */
  // TODO: Change Parameter to a type that can also signal adding/removing obstacles, etc.
  private final EventTriggers<EventTriggers.Update, Parameter> settingsTriggers = new EventTriggers<>();
  public enum Parameter {
    apply_random,
    snr_threshold,
    bg_noise_mean,
    bg_noise_var,
    system_gain_mean,
    system_gain_var,
    frequency,
    tx_power,
    tx_with_gain,
    rx_sensitivity,
    rx_with_gain,
    rt_disallow_direct_path,
    rt_ignore_non_direct,
    rt_fspl_on_total_length,
    rt_max_rays,
    rt_max_refractions,
    rt_max_reflections,
    rt_max_diffractions,
    rt_use_scattering,
    rt_refrac_coefficient,
    rt_reflec_coefficient,
    rt_diffr_coefficient,
    rt_scatt_coefficient,
    obstacle_attenuation,
    captureEffect,
    captureEffectPreambleDuration,
    captureEffectSignalTreshold;

    public static Object getDefaultValue(Parameter p) {
      return switch (p) {
        case apply_random -> false;
        case snr_threshold -> 6.0;
        case bg_noise_mean -> AbstractRadioMedium.SS_NOTHING;
        case bg_noise_var -> 1.0;
        case system_gain_mean -> 0.0;
        case system_gain_var -> 4.0;
        case frequency -> 2400.0; // MHz.
        case tx_power -> 1.5;
        case tx_with_gain -> true;
        case rx_sensitivity -> -100.0;
        case rx_with_gain, rt_disallow_direct_path, rt_ignore_non_direct -> false;
        case rt_fspl_on_total_length -> true;
        case rt_max_rays, rt_max_refractions, rt_max_reflections -> 1;
        case rt_max_diffractions -> 0;
        case rt_use_scattering -> false;
        case rt_refrac_coefficient -> -3.0;
        case rt_reflec_coefficient -> -5.0;
        case rt_diffr_coefficient -> -10.0;
        case rt_scatt_coefficient -> -20.0;
        case obstacle_attenuation -> -3.0;
        case captureEffect -> true;
        case captureEffectPreambleDuration -> 1000 * 1000 * 4 * 0.5 * 8 / 250000; // 2 bytes, 250kbit/s, us.
        case captureEffectSignalTreshold -> 3.0; // dB, according to previous 802.15.4 studies.
      };
    }
    
    public static Parameter fromString(String name) {
      /* Backwards compatability */
      return switch (name) {
        case "apply_random" -> apply_random;
        case "snr_threshold" -> snr_threshold;
        case "bg_noise_mean" -> bg_noise_mean;
        case "bg_noise_var" -> bg_noise_var;
        case "system_gain_mean" -> system_gain_mean;
        case "system_gain_var" -> system_gain_var;
        case "tx_power" -> tx_power;
        case "rx_sensitivity" -> rx_sensitivity;
        case "rt_disallow_direct_path" -> rt_disallow_direct_path;
        case "rt_ignore_non_direct" -> rt_ignore_non_direct;
        case "rt_fspl_on_total_length" -> rt_fspl_on_total_length;
        case "rt_max_rays" -> rt_max_rays;
        case "rt_max_refractions" -> rt_max_refractions;
        case "rt_max_reflections" -> rt_max_reflections;
        case "rt_max_diffractions" -> rt_max_diffractions;
        case "rt_use_scattering" -> rt_use_scattering;
        case "rt_refrac_coefficient" -> rt_refrac_coefficient;
        case "rt_reflec_coefficient" -> rt_reflec_coefficient;
        case "rt_diffr_coefficient" -> rt_diffr_coefficient;
        case "rt_scatt_coefficient" -> rt_scatt_coefficient;
        case "obstacle_attenuation" -> obstacle_attenuation;
        case "captureEffect" -> captureEffect;
        case "captureEffectPreambleDuration" -> captureEffectPreambleDuration;
        case "captureEffectSignalTreshold" -> captureEffectSignalTreshold;
        default -> null;
      };
    }

    public static String getDescription(Parameter p) {
      return switch (p) {
        case apply_random -> "(DEBUG) Apply random values";
        case snr_threshold -> "SNR reception threshold (dB)";
        case bg_noise_mean -> "Background noise mean (dBm)";
        case bg_noise_var -> "Background noise variance (dB)";
        case system_gain_mean -> "Extra system gain mean (dB)";
        case system_gain_var -> "Extra system gain variance (dB)";
        case frequency -> "Frequency (MHz)";
        case tx_power -> "Default transmitter output power (dBm)";
        case tx_with_gain -> "Directional antennas: with TX gain";
        case rx_sensitivity -> "Receiver sensitivity (dBm)";
        case rx_with_gain -> "Directional antennas: with RX gain";
        case rt_disallow_direct_path -> "Disallow direct path";
        case rt_ignore_non_direct -> "If existing: return only use direct path";
        case rt_fspl_on_total_length -> "Use FSPL on total path lengths only";
        case rt_max_rays -> "Max path rays";
        case rt_max_refractions -> "Max refractions";
        case rt_max_reflections -> "Max reflections";
        case rt_max_diffractions -> "Max diffractions";
        case rt_use_scattering -> "Use scattering";
        case rt_refrac_coefficient -> "Refraction coefficient (dB)";
        case rt_reflec_coefficient -> "Reflection coefficient (dB)";
        case rt_diffr_coefficient -> "Diffraction coefficient (dB)";
        case rt_scatt_coefficient -> "Scattering coefficient";
        case obstacle_attenuation -> "Obstacle attenuation (dB/m)";
        case captureEffect -> "Use Capture Effect";
        case captureEffectPreambleDuration -> "Capture effect preamble (us)";
        case captureEffectSignalTreshold -> "Capture effect threshold (dB)";
      };
    }
  }
  
  public ChannelModel(Simulation simulation) {
    /* Default values */
    for (Parameter p: Parameter.values()) {
      parameters.put(p, Parameter.getDefaultValue(p));
    }

    parametersDefaults = new HashMap<>(parameters);

    // Ray Tracer - Use scattering
    //parameters.put(Parameters.rt_use_scattering, Parameter.getDefaultValue(Parameters.rt_use_scattering)); // TODO Not used yet
    //parameterDescriptions.put(Parameters.rt_use_scattering, "Use simple scattering");

    // Ray Tracer - Scattering coefficient
    //parameters.put(Parameters.rt_scatt_coefficient, Parameter.getDefaultValue(Parameters.rt_scatt_coefficient)); // TODO Not used yet
    //parameterDescriptions.put(Parameters.rt_scatt_coefficient, "!! Scattering coefficient (dB)");
  }

  /**
   * Get event triggers for changes to this channel model.
   * Every time the settings are changed all triggers will be notified.
   */
  public EventTriggers<EventTriggers.Update, Parameter> getSettingsTriggers() {
    return settingsTriggers;
  }

  /**
   * Remove all previously registered obstacles
   */
  public void removeAllObstacles() {
    myObstacleWorld.removeAll();
    settingsTriggers.trigger(EventTriggers.Update.UPDATE, null);
  }

  /**
   * Add new obstacle with a rectangle shape.
   * Notifies observers of the new obstacle.
   *
   * @param startX Low X coordinate
   * @param startY Low Y coordinate
   * @param width Width of obstacle
   * @param height Height of obstacle
   */
  public void addRectObstacle(double startX, double startY, double width, double height) {
    addRectObstacle(startX, startY, width, height, true);
  }

  /**
   * Add new obstacle with a rectangle shape.
   * Notifies observers depending on given notify argument.
   *
   * @param startX Low X coordinate
   * @param startY Low Y coordinate
   * @param width Width of obstacle
   * @param height Height of obstacle
   * @param notify If true, notifies all observers of this new obstacle
   */
  public void addRectObstacle(double startX, double startY, double width, double height, boolean notify) {
    myObstacleWorld.addObstacle(startX, startY, width, height);

    if (notify) {
      settingsTriggers.trigger(EventTriggers.Update.UPDATE, null);
    }
  }

  /**
   * @return Number of registered obstacles
   */
  public int getNumberOfObstacles() {
    return myObstacleWorld.getNrObstacles();
  }

  /**
   * Returns an obstacle at given position
   * @param i Obstacle position
   * @return Obstacle
   */
  public Rectangle2D getObstacle(int i) {
    return myObstacleWorld.getObstacle(i);
  }

  /**
   * Returns a parameter value
   *
   * @param id Parameter identifier
   * @return Current parameter value
   */
  public Object getParameterValue(Parameter id) {
    Object value = parameters.get(id);
    if (value == null) {
      logger.error("No parameter with id:" + id + ", aborting");
      return null;
    }
    return value;
  }

  /**
   * Returns a double parameter value
   *
   * @param id Parameter identifier
   * @return Current parameter value
   */
  public double getParameterDoubleValue(Parameter id) {
    return (Double) getParameterValue(id);
  }

  /**
   * Returns an integer parameter value
   *
   * @param id Parameter identifier
   * @return Current parameter value
   */
  public int getParameterIntegerValue(Parameter id) {
    return (Integer) getParameterValue(id);
  }

  /**
   * Returns a boolean parameter value
   *
   * @param id Parameter identifier
   * @return Current parameter value
   */
  public boolean getParameterBooleanValue(Parameter id) {
    return (Boolean) getParameterValue(id);
  }

  /**
   * Saves a new parameter value
   *
   * @param id Parameter identifier
   * @param newValue New parameter value
   */
  public void setParameterValue(Parameter id, Object newValue) {
    if (!parameters.containsKey(id)) {
      logger.error("No parameter with id:" + id + ", aborting");
      return;
    }
    parameters.put(id, newValue);

    // Guessing we need to recalculate input to FSPL+Output power
    needToPrecalculateFSPL = true;
    settingsTriggers.trigger(EventTriggers.Update.UPDATE, id);
  }

  /**
   * When this method is called all settings observers
   * will be notified.
   */
  public void notifySettingsChanged() {
    settingsTriggers.trigger(EventTriggers.Update.UPDATE, null);
  }
  
  /**
   * Path loss component from Friis' transmission equation.
   * Uses frequency and distance only.
   *
   * @param distance Transmitter-receiver distance
   * @return Path loss (dB)
   */
  protected double getFSPL(double distance) {
    if (needToPrecalculateFSPL) {
      double f = getParameterDoubleValue(Parameter.frequency);
      paramFSPL = -32.44 -20*Math.log10(f /*mhz*/);
      needToPrecalculateFSPL = false;
    }

    return Math.min(0.0, paramFSPL - 20*Math.log10(distance/1000.0 /*km*/));
  }


  /**
   * Returns the subset of a given line, that is intersecting the given rectangle.
   * This method returns null if the line does not intersect the rectangle.
   * The given line is defined by the given (x1, y1) -> (x2, y2).
   *
   * @param x1 Line start point X
   * @param y1 Line start point Y
   * @param x2 Line end point X
   * @param y2 Line epoint Y
   * @param rectangle Rectangle which line may intersect
   * @return Intersection line of given line and rectangle (or null)
   */
  private static Line2D getIntersectionLine(double x1, double y1, double x2, double y2, Rectangle2D rectangle) {

    // Check if entire line is inside rectangle
    if (rectangle.contains(x1, y1) && rectangle.contains(x2, y2)) {
      return new Line2D.Double(x1, y1, x2, y2);
    }

    // Get rectangle and test lines
    Line2D rectangleLower = new Line2D.Double(rectangle.getMinX(), rectangle.getMinY(), rectangle.getMaxX(), rectangle.getMinY());
    Line2D rectangleUpper = new Line2D.Double(rectangle.getMinX(), rectangle.getMaxY(), rectangle.getMaxX(), rectangle.getMaxY());
    Line2D rectangleLeft = new Line2D.Double(rectangle.getMinX(), rectangle.getMinY(), rectangle.getMinX(), rectangle.getMaxY());
    Line2D rectangleRight = new Line2D.Double(rectangle.getMaxX(), rectangle.getMinY(), rectangle.getMaxX(), rectangle.getMaxY());
    Line2D testLine = new Line2D.Double(x1, y1, x2, y2);

    // Check which sides of the rectangle the test line passes through
    var intersectedSides = new ArrayList<Line2D>();
    if (rectangleLower.intersectsLine(testLine)) {
      intersectedSides.add(rectangleLower);
    }

    if (rectangleUpper.intersectsLine(testLine)) {
      intersectedSides.add(rectangleUpper);
    }

    if (rectangleLeft.intersectsLine(testLine)) {
      intersectedSides.add(rectangleLeft);
    }

    if (rectangleRight.intersectsLine(testLine)) {
      intersectedSides.add(rectangleRight);
    }

    // If no sides are intersected, return null (no intersection)
    if (intersectedSides.isEmpty()) {
      return null;
    }

    // Calculate all resulting line points (should be 2)
    var intersectingLinePoints = new ArrayList<Point2D>();
    for (var intersectedSide : intersectedSides) {
      intersectingLinePoints.add(getIntersectionPoint(testLine, intersectedSide));
    }

    // If only one side was intersected, one point must be inside rectangle
    if (intersectingLinePoints.size() == 1) {
      if (rectangle.contains(x1, y1)) {
        intersectingLinePoints.add(new Point2D.Double(x1, y1));
      } else if (rectangle.contains(x2, y2)) {
        intersectingLinePoints.add(new Point2D.Double(x2, y2));
      } else {
        // Border case, no intersection line
        return null;
      }
    }

    if (intersectingLinePoints.size() != 2) {
      // We should have 2 line points!
      logger.warn("Intersecting points != 2");
      return null;
    }

    if (intersectingLinePoints.get(0).distance(intersectingLinePoints.get(1)) < 0.001) {
      return null;
    }

    return new Line2D.Double(
        intersectingLinePoints.get(0),
        intersectingLinePoints.get(1)
    );
  }

  /**
   * Returns the intersection point of the two given lines.
   *
   * @param firstLine First line
   * @param secondLine Second line
   * @return Intersection point of the two lines or null
   */
  private static Point2D getIntersectionPoint(Line2D firstLine, Line2D secondLine) {
    double dx1 = firstLine.getX2() - firstLine.getX1();
    double dy1 = firstLine.getY2() - firstLine.getY1();
    double dx2 = secondLine.getX2() - secondLine.getX1();
    double dy2 = secondLine.getY2() - secondLine.getY1();
    double det = (dx2*dy1-dy2*dx1);

    if (det == 0.0) {
      // Lines parallell, not intersecting
      return null;
    }

    double mu = ((firstLine.getX1() - secondLine.getX1())*dy1 - (firstLine.getY1() - secondLine.getY1())*dx1)/det;
    if (mu >= 0.0  &&  mu <= 1.0) {
      return new Point2D.Double((secondLine.getX1() + mu*dx2), (secondLine.getY1() + mu*dy2));
    }

    // Lines not intersecting withing segments
    return null;
  }

  /**
   * Returns the intersection point of the two given lines when streched to infinity.
   *
   * @param firstLine First line
   * @param secondLine Second line
   * @return Intersection point of the two infinite lines or null if parallell
   */
  private static Point2D getIntersectionPointInfinite(Line2D firstLine, Line2D secondLine) {
    double dx1 = firstLine.getX2() - firstLine.getX1();
    double dy1 = firstLine.getY2() - firstLine.getY1();
    double dx2 = secondLine.getX2() - secondLine.getX1();
    double dy2 = secondLine.getY2() - secondLine.getY1();
    double det = (dx2*dy1-dy2*dx1);

    if (det == 0.0) {
      // Lines parallell, not intersecting
      return null;
    }

    double mu = ((firstLine.getX1() - secondLine.getX1())*dy1 - (firstLine.getY1() - secondLine.getY1())*dx1)/det;
    return new Point2D.Double((secondLine.getX1() + mu*dx2), (secondLine.getY1() + mu*dy2));
  }

  /**
   * This method builds a tree structure with all visible lines from a given source.
   * It is recursive and depends on the given ray data argument, which holds information
   * about maximum number of recursions.
   * Each element in the tree is either produced from a refraction, reflection or a diffraction
   * (except for the absolute source which is neither), and holds a point and a line.
   *
   * @param rayData Holds information about the incident ray
   * @return Tree of all visibles lines
   */
  private DefaultMutableTreeNode buildVisibleLinesTree(RayData rayData) {
    DefaultMutableTreeNode thisTree = new DefaultMutableTreeNode();
    thisTree.setUserObject(rayData);

    // If no more rays may be produced there if no need to search for visible lines
    if (rayData.getSubRaysLimit() <= 0) {
      return thisTree;
    }

    Point2D source = rayData.getSourcePoint();
    Line2D line = rayData.getLine();

    // Find all visible lines
    var visibleSides = getAllVisibleSides(
        source.getX(),
        source.getY(),
        null,
        line
    );

    // Create refracted subtrees
    if (rayData.getRefractedSubRaysLimit() > 0 && visibleSides != null) {
      for (var refractingSide : visibleSides) {
        // Keeping old source, but looking through this line to see behind it
        // Recursively build and add subtrees
        RayData newRayData = new RayData(
            RayData.RayType.REFRACTION,
            source,
            refractingSide,
            rayData.getSubRaysLimit() - 1,
            rayData.getRefractedSubRaysLimit() - 1,
            rayData.getReflectedSubRaysLimit(),
            rayData.getDiffractedSubRaysLimit()
        );
        DefaultMutableTreeNode subTree = buildVisibleLinesTree(newRayData);

        thisTree.add(subTree);
      }
    }

    // Create reflection subtrees
    if (rayData.getReflectedSubRaysLimit() > 0 && visibleSides != null) {
      for (var reflectingSide : visibleSides) {
        // Create new pseudo-source
        Rectangle2D bounds = reflectingSide.getBounds2D();
        double newPsuedoSourceX = source.getX();
        double newPsuedoSourceY = source.getY();
        if (bounds.getHeight() > bounds.getWidth()) {
          newPsuedoSourceX = 2*reflectingSide.getX1() - newPsuedoSourceX;
        } else {
          newPsuedoSourceY = 2*reflectingSide.getY1() - newPsuedoSourceY;
        }

        // Recursively build and add subtrees
        RayData newRayData = new RayData(
            RayData.RayType.REFLECTION,
            new Point2D.Double(newPsuedoSourceX, newPsuedoSourceY),
            reflectingSide,
            rayData.getSubRaysLimit() - 1,
            rayData.getRefractedSubRaysLimit(),
            rayData.getReflectedSubRaysLimit() - 1,
            rayData.getDiffractedSubRaysLimit()
        );
        DefaultMutableTreeNode subTree = buildVisibleLinesTree(newRayData);

        thisTree.add(subTree);
      }
    }

    // Get possible diffraction sources
    List<Point2D> diffractionSources = null;
    if (rayData.getDiffractedSubRaysLimit() > 0 && visibleSides != null) {
      diffractionSources = getAllDiffractionSources(visibleSides);
    }

    // Create diffraction subtrees
    if (rayData.getDiffractedSubRaysLimit() > 0 && diffractionSources != null) {
      for (var diffractionSource : diffractionSources) {
        // Recursively build and add subtrees
        RayData newRayData = new RayData(
            RayData.RayType.DIFFRACTION,
            diffractionSource,
            null,
            rayData.getSubRaysLimit() - 1,
            rayData.getRefractedSubRaysLimit(),
            rayData.getReflectedSubRaysLimit(),
            rayData.getDiffractedSubRaysLimit() - 1
        );
        DefaultMutableTreeNode subTree = buildVisibleLinesTree(newRayData);

        thisTree.add(subTree);
      }
    }

    return thisTree;
  }

  /**
   * Returns a vector of ray paths from given origin to given destination.
   * Each ray path consists of a vector of points (including source and destination).
   *
   * @param origin Ray paths origin
   * @param dest Ray paths destination
   * @param visibleLinesTree Information about all visible lines generated by buildVisibleLinesTree()
   * @see #buildVisibleLinesTree(RayData)
   * @return All ray paths from origin to destnation
   */
  private List<RayPath> getConnectingPaths(Point2D origin, Point2D dest, DefaultMutableTreeNode visibleLinesTree) {
    var allPaths = new ArrayList<RayPath>();
    // Analyse the possible paths to find which actually reached destination
    var treeEnum = visibleLinesTree.breadthFirstEnumeration();
    while (treeEnum.hasMoreElements()) {
      // For every element,
      //  check if it is the origin, a diffraction, refraction or a reflection source
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) treeEnum.nextElement();
      RayData rayData = (RayData) treeNode.getUserObject();
      Point2D sourcePoint = rayData.getSourcePoint();
      Line2D line = rayData.getLine();
      RayData.RayType type = rayData.getType();

      Line2D pseudoSourceToDest = new Line2D.Double(sourcePoint, dest);
      boolean directPathExists = false;
      Point2D justBeforeDestination = null;

      // Get ray path point just before destination (if path exists at all)
      if (type == RayData.RayType.ORIGIN) {

        // Check if direct path exists
        justBeforeDestination = sourcePoint;

        if (!getParameterBooleanValue(Parameter.rt_disallow_direct_path)) {
          directPathExists = isDirectPath(justBeforeDestination, dest);
        }
      } else if (type == RayData.RayType.REFRACTION && pseudoSourceToDest.intersectsLine(line)) {

        // Destination is inside refraction interval
        justBeforeDestination = getIntersectionPoint(pseudoSourceToDest, line);

        // Check if direct path exists (but ignore when leaving obstacle)
        directPathExists = isDirectPath(justBeforeDestination, dest);

      } else if (type == RayData.RayType.REFLECTION && pseudoSourceToDest.intersectsLine(line)) {

        // Destination is inside reflection interval
        justBeforeDestination = getIntersectionPoint(pseudoSourceToDest, line);

        // Check if direct path exists (ignore reflection line)
        directPathExists = isDirectPath(justBeforeDestination, dest);

      } else if (type == RayData.RayType.DIFFRACTION) {

        // Check if direct path exists (travelling through object not allowed
        justBeforeDestination = sourcePoint;
        directPathExists = isDirectPath(justBeforeDestination, dest);

      }

      // If a direct path exists, traverse up tree to find entire path
      if (directPathExists) {

        // Create new empty ray path
        boolean pathBroken = false;
        RayPath currentPath = new RayPath();

        // Add those parts we already know
        currentPath.addPoint(dest, RayData.RayType.DESTINATION);
        currentPath.addPoint(justBeforeDestination, type);

        Point2D lastPoint = dest;
        Point2D newestPoint = justBeforeDestination;

        // Check that this ray subpath is long enough to be considered
        if (newestPoint.distance(lastPoint) < 0.01 && type != RayData.RayType.ORIGIN) {
          pathBroken = true;
        }

        // Subpath must be double-direct if from diffraction
        if (type == RayData.RayType.DIFFRACTION && !isDirectPath(lastPoint, newestPoint)) {
          pathBroken = true;
        }

        // Data used when traversing path
        DefaultMutableTreeNode currentlyTracedNode = treeNode;
        RayData currentlyTracedRayData = (RayData) currentlyTracedNode.getUserObject();
        RayData.RayType currentlyTracedNodeType = currentlyTracedRayData.getType();

        // Traverse upwards until origin found
        while (!pathBroken && currentlyTracedNodeType != RayData.RayType.ORIGIN) {

          // Update new ray data
          currentlyTracedNode = (DefaultMutableTreeNode) currentlyTracedNode.getParent();
          currentlyTracedRayData = (RayData) currentlyTracedNode.getUserObject();
          currentlyTracedNodeType = currentlyTracedRayData.getType();
          Point2D currentlyTracedSource = currentlyTracedRayData.getSourcePoint();
          Line2D currentlyTracedLine = currentlyTracedRayData.getLine();

          if (currentlyTracedNodeType == RayData.RayType.ORIGIN) {
            // We finally found the path origin, path ends here
            lastPoint = newestPoint;
            newestPoint = origin;

            currentPath.addPoint(newestPoint, currentlyTracedNodeType);

            // Check that this ray subpath is long enough to be considered
            if (newestPoint.distance(lastPoint) < 0.01) {
              pathBroken = true;
            }

          } else {
            // Trace further up in the tree

            if (currentlyTracedNodeType == RayData.RayType.REFRACTION || currentlyTracedNodeType == RayData.RayType.REFLECTION) {
              // Traced tree element is a reflection/refraction - get intersection point and keep climbing
              lastPoint = newestPoint;

              Line2D newToOldIntersection = new Line2D.Double(currentlyTracedSource, lastPoint);
              newestPoint = getIntersectionPointInfinite(newToOldIntersection, currentlyTracedLine);

            } else {
              // Traced tree element is a diffraction - save point and keep climbing
              lastPoint = newestPoint;
              newestPoint = currentlyTracedSource;
            }

            currentPath.addPoint(newestPoint, currentlyTracedNodeType);

            // Check that this ray subpath is long enough to be considered
            if (newestPoint == null || lastPoint == null || newestPoint.distance(lastPoint) < 0.01) {
              pathBroken = true;
            }
          }

          // Subpath must be double-direct if from diffraction
          if (currentlyTracedNodeType == RayData.RayType.DIFFRACTION && !isDirectPath(lastPoint, newestPoint)) {
            pathBroken = true;
          }

          if (pathBroken) {
            break;
          }
        }

        // Save ray path
        if (!pathBroken) {
          allPaths.add(currentPath);

          // Stop here if no other paths should be considered
          if (type == RayData.RayType.ORIGIN && getParameterBooleanValue(Parameter.rt_ignore_non_direct)) {
            return allPaths;
          }

        }

      }
    }

    return allPaths;
  }

  /**
   * True if a line drawn from the given source and given destination does
   * not intersect with any obstacle outer lines in the current obstacle world.
   * This method only checks for intersection with the obstacles lines "visible"
   * from source. Hence, if source is inside an obstacle, that obstacles will
   * not cause this method to return false. (Note that method is not symmetric)
   *
   * @param source Source
   * @param dest Destination
   * @return True if no obstacles between source and destination
   */
  private boolean isDirectPath(Point2D source, Point2D dest) {
    Line2D sourceToDest = new Line2D.Double(source, dest);

    // Get angle
    double deltaX = dest.getX() - source.getX();
    double deltaY = dest.getY() - source.getY();
    double angleSourceToDest = Math.atan2(deltaY, deltaX);

    // Get all visible sides near angle
    var visibleSides = getAllVisibleSides(
        source.getX(),
        source.getY(),
        new AngleInterval(angleSourceToDest - 0.1, angleSourceToDest + 0.1),
        null
    );

    // Check for intersections
    if (visibleSides != null) {
      for (var visibleSide : visibleSides) {
        if (visibleSide.intersectsLine(sourceToDest)) {
          // Check that intersection point is not destination
          var intersectionPoint = getIntersectionPointInfinite(visibleSide, sourceToDest);
          if (dest.distance(intersectionPoint) > 0.01) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /**
   * Returns the Fast fading factor (in dB), which depends on
   * the multiple paths from source to destination via reflections
   * on registered obstacles.
   * TODO Only first-order multipath...
   *
   * @param sourceX Transmitter X coordinate
   * @param sourceY Transmitter Y coordinate
   * @param destX Receiver X coordinate
   * @param destY Receiver Y coordinate
   * @return Slow fading factor
   */
  protected double getFastFading(double sourceX, double sourceY, double destX, double destY) {
    Point2D dest = new Point2D.Double(destX, destY);

    // Destination inside an obstacle? => no reflection factor
    for (int i=0; i < myObstacleWorld.getNrObstacles(); i++) {
      if (myObstacleWorld.getObstacle(i).contains(dest)) {
        return 0;
      }
    }

    return 0;
  }


  /**
   * Returns all possible diffraction sources, by checking which
   * of the endpoints of the given visible lines that are on a corner
   * of an obstacle structure.
   *
   * @param allVisibleLines Lines which may hold diffraction sources
   * @return All diffraction sources
   */
  private List<Point2D> getAllDiffractionSources(List<Line2D> allVisibleLines) {
    var allDiffractionSources = new ArrayList<Point2D>();
    for (var visibleLine : allVisibleLines) {
      // Check both end points of line for possible diffraction point
      if (myObstacleWorld.pointIsNearCorner(visibleLine.getP1())) {
        allDiffractionSources.add(visibleLine.getP1());
      }
      if (myObstacleWorld.pointIsNearCorner(visibleLine.getP2())) {
        allDiffractionSources.add(visibleLine.getP2());
      }
    }

    return allDiffractionSources;
  }

  /**
   * Return all obstacle sides visible from given source when looking
   * in the given angle interval.
   * The sides may partly be shadowed by other obstacles.
   * If the angle interval is null, it will be regarded as the entire interval
   * If the line argument is non-null, all returned lines will be on the far side
   * of this line, as if one was looking through that line.
   *
   * @param sourceX Source X
   * @param sourceY Source Y
   * @param angleInterval Angle interval (or null)
   * @param lookThrough Line to look through (or null)
   * @return All visible sides
   */
  synchronized private List<Line2D> getAllVisibleSides(double sourceX, double sourceY, AngleInterval angleInterval, Line2D lookThrough) {
    // synchronized because a race condition happens in this method when MRMVisualizerSkin accesses this module from another thread
    Point2D source = new Point2D.Double(sourceX, sourceY);

    // Check if results were already calculated earlier
    for (int i=0; i < calculatedVisibleSidesSources.size(); i++) {
      if (
          // Compare sources
          source.equals(calculatedVisibleSidesSources.get(i)) &&

          // Compare angle intervals
          (angleInterval == calculatedVisibleSidesAngleIntervals.get(i) ||
              angleInterval != null && angleInterval.equals(calculatedVisibleSidesAngleIntervals.get(i)) ) &&

              // Compare lines
              (lookThrough == calculatedVisibleSidesLines.get(i) ||
                  lookThrough != null && lookThrough.equals(calculatedVisibleSidesLines.get(i)) )
      ) {
        // Move to top of list
        Point2D oldSource = calculatedVisibleSidesSources.remove(i);
        Line2D oldLine = calculatedVisibleSidesLines.remove(i);
        AngleInterval oldAngleInterval = calculatedVisibleSidesAngleIntervals.remove(i);
        var oldVisibleLines = calculatedVisibleSides.remove(i);

        calculatedVisibleSidesSources.add(0, oldSource);
        calculatedVisibleSidesLines.add(0, oldLine);
        calculatedVisibleSidesAngleIntervals.add(0, oldAngleInterval);
        calculatedVisibleSides.add(0, oldVisibleLines);

        // Return old results
        return oldVisibleLines;
      }
    }

    List<Line2D> visibleLines = new ArrayList<>();
    List<AngleInterval> unhandledAngles = new ArrayList<>();

    if (lookThrough != null) {
      if (angleInterval == null) {
        unhandledAngles.add(AngleInterval.getAngleIntervalOfLine(source, lookThrough));
      } else {
        unhandledAngles.add(AngleInterval.getAngleIntervalOfLine(source, lookThrough).intersectWith(angleInterval));
      }
    } else {
      unhandledAngles.add(Objects.requireNonNullElseGet(angleInterval, () -> new AngleInterval(0, 2 * Math.PI)));
    }

    // Do forever (will break when no more unhandled angles exist)
    while (!unhandledAngles.isEmpty()) {

      // While unhandled angles still exist, keep searching for visible lines
      while (!unhandledAngles.isEmpty()) {
        //logger.info("Beginning of while-loop, unhandled angles left = " + unhandledAngles.size());
        var angleIntervalToCheck = unhandledAngles.get(0);

        // Check that interval is not empty or "infinite small"
        if (angleIntervalToCheck == null || angleIntervalToCheck.isEmpty()) {
          //logger.info("Angle interval (almost) empty, ignoring");
          unhandledAngles.remove(angleIntervalToCheck);
          break;
        }

        // <<<< Get visible obstacle candidates inside this angle interval >>>>
        var visibleObstacleCandidates =
          myObstacleWorld.getAllObstaclesInAngleInterval(source, angleIntervalToCheck);

        //logger.info("Obstacle candidates count = " + visibleObstacleCandidates.size());
        if (visibleObstacleCandidates.isEmpty()) {
          //logger.info("Visible obstacles candidates empty");
          unhandledAngles.remove(angleIntervalToCheck);
          break; // Restart without this angle
        }

        // <<<< Get visible line candidates of these obstacles >>>>
        var visibleLineCandidates = new ArrayList<Line2D>();
        for (var obstacle : visibleObstacleCandidates) {
          int outcode = obstacle.outcode(source);

          if ((outcode & Rectangle2D.OUT_BOTTOM) != 0) {
            visibleLineCandidates.add(
                new Line2D.Double(obstacle.getMinX(), obstacle.getMaxY(), obstacle.getMaxX(), obstacle.getMaxY()));
          }

          if ((outcode & Rectangle2D.OUT_TOP) != 0) {
            visibleLineCandidates.add(
                new Line2D.Double(obstacle.getMinX(), obstacle.getMinY(), obstacle.getMaxX(), obstacle.getMinY()));
          }

          if ((outcode & Rectangle2D.OUT_LEFT) != 0) {
            visibleLineCandidates.add(
                new Line2D.Double(obstacle.getMinX(), obstacle.getMinY(), obstacle.getMinX(), obstacle.getMaxY()));
          }

          if ((outcode & Rectangle2D.OUT_RIGHT) != 0) {
            visibleLineCandidates.add(
                new Line2D.Double(obstacle.getMaxX(), obstacle.getMinY(), obstacle.getMaxX(), obstacle.getMaxY()));
          }
        }
        //logger.info("Line candidates count = " + visibleLineCandidates.size());
        if (visibleLineCandidates.isEmpty()) {
          //logger.info("Visible line candidates empty");
          unhandledAngles.remove(angleIntervalToCheck);
          break; // Restart without this angle
        }

        // <<<< Get cropped visible line candidates of these lines >>>>
        var croppedVisibleLineCandidates = new ArrayList<Line2D>();
        for (var lineCandidate : visibleLineCandidates) {
          // Create angle interval of this line
          AngleInterval lineAngleInterval = AngleInterval.getAngleIntervalOfLine(source, lineCandidate);

          AngleInterval intersectionInterval;

          // Add entire line if it is fully inside our visible angle interval
          if (angleIntervalToCheck.contains(lineAngleInterval)) {

            if (lookThrough != null) {
              // Check if the candidate is "equal" to the see through line
              if (Math.abs(lineCandidate.getX1() - lookThrough.getX1()) +
                  Math.abs(lineCandidate.getY1() - lookThrough.getY1()) +
                  Math.abs(lineCandidate.getX2() - lookThrough.getX2()) +
                  Math.abs(lineCandidate.getY2() - lookThrough.getY2()) < 0.01) {
                // See through line and candidate line are the same - skip this candidate
              }

              // Check if the candidate is on our side of the see through line
              else if (new Line2D.Double(
                  lineCandidate.getBounds2D().getCenterX(),
                  lineCandidate.getBounds2D().getCenterY(),
                  sourceX,
                  sourceY
              ).intersectsLine(lookThrough)) {
                croppedVisibleLineCandidates.add(lineCandidate);
              } // else Skip line
            } else {
              croppedVisibleLineCandidates.add(lineCandidate);
            }

          }

          // Add part of line if it is partly inside our visible angle interval
          else if ((intersectionInterval = lineAngleInterval.intersectWith(angleIntervalToCheck)) != null) {

            // Get lines towards the visible segment
            Line2D lineToStartAngle = AngleInterval.getDirectedLine(
                source,
                intersectionInterval.getStartAngle(),
                1.0
            );
            Line2D lineToEndAngle = AngleInterval.getDirectedLine(
                source,
                intersectionInterval.getEndAngle(),
                1.0
            );

            // Calculate intersection points
            Point2D intersectionStart = getIntersectionPointInfinite(
                lineCandidate,
                lineToStartAngle
            );
            Point2D intersectionEnd = getIntersectionPointInfinite(
                lineCandidate,
                lineToEndAngle
            );

            if (
                intersectionStart != null &&
                intersectionEnd != null &&
                intersectionStart.distance(intersectionEnd) > 0.001 // Rounding error limit (1 mm)
            ) {

              Line2D newCropped = new Line2D.Double(intersectionStart, intersectionEnd);

              if (lookThrough != null) {
                // Check if the candidate is "equal" to the see through line
                if (Math.abs(newCropped.getX1() - lookThrough.getX1()) +
                    Math.abs(newCropped.getY1() - lookThrough.getY1()) +
                    Math.abs(newCropped.getX2() - lookThrough.getX2()) +
                    Math.abs(newCropped.getY2() - lookThrough.getY2()) < 0.01) {
                  // See through line and candidate line are the same - skip this candidate
                }

                // Check if the candidate is on our side of the see through line
                else if (new Line2D.Double(
                    newCropped.getBounds2D().getCenterX(),
                    newCropped.getBounds2D().getCenterY(),
                    sourceX,
                    sourceY
                ).intersectsLine(lookThrough)) {
                  croppedVisibleLineCandidates.add(newCropped);
                } // else Skip line
              } else {
                croppedVisibleLineCandidates.add(newCropped);
              }
            }
          }
          // Skip line completely if not in our visible angle interval
        }
        //logger.info("Cropped line candidates count = " + croppedVisibleLineCandidates.size());
        if (croppedVisibleLineCandidates.isEmpty()) {
          //logger.info("Cropped visible line candidates empty");
          unhandledAngles.remove(angleIntervalToCheck);
          break; // Restart without this angle
        }

        // <<<< Get visible lines from these line candidates >>>>
        for (Line2D visibleLineCandidate : croppedVisibleLineCandidates) {
          AngleInterval visibleLineCandidateAngleInterval =
            AngleInterval.getAngleIntervalOfLine(source, visibleLineCandidate).intersectWith(angleIntervalToCheck);

          //logger.info("Incoming angle interval " + angleIntervalToCheck);
          //logger.info(". => line interval " + visibleLineCandidateAngleInterval);

          // Area to test for shadowing objects
          GeneralPath testArea = new GeneralPath();
          testArea.moveTo((float) sourceX, (float) sourceY);
          testArea.lineTo((float) visibleLineCandidate.getX1(), (float) visibleLineCandidate.getY1());
          testArea.lineTo((float) visibleLineCandidate.getX2(), (float) visibleLineCandidate.getY2());
          testArea.closePath();

          // Does any other line shadow this line?
          boolean unshadowed = true;
          boolean unhandledAnglesChanged = false;
          for (var shadowLineCandidate : croppedVisibleLineCandidates) {

            // Create shadow rectangle
            Rectangle2D shadowRectangleCandidate = shadowLineCandidate.getBounds2D();
            double minDelta = 0.01 * Math.max(
                shadowRectangleCandidate.getWidth(),
                shadowRectangleCandidate.getHeight()
            );
            shadowRectangleCandidate.add(
                shadowRectangleCandidate.getCenterX() + minDelta,
                shadowRectangleCandidate.getCenterY() + minDelta
            );

            // Find the shortest of the two
            double shadowDistance =
              shadowLineCandidate.getP1().distance(source) +
              shadowLineCandidate.getP2().distance(source);

            double visibleDistance =
              visibleLineCandidate.getP1().distance(source) +
              visibleLineCandidate.getP2().distance(source);

            double shadowCloseDistance =
              Math.min(
                  shadowLineCandidate.getP1().distance(source),
                  shadowLineCandidate.getP2().distance(source));

            double visibleFarDistance =
              Math.max(
                  visibleLineCandidate.getP1().distance(source),
                  visibleLineCandidate.getP2().distance(source));

            // Does shadow rectangle intersect test area?
            if (visibleLineCandidate != shadowLineCandidate &&
                testArea.intersects(shadowRectangleCandidate) &&
                shadowCloseDistance <= visibleFarDistance) {

              // Shadow line candidate seems to shadow (part of) our visible candidate
              AngleInterval shadowLineCandidateAngleInterval =
                AngleInterval.getAngleIntervalOfLine(source, shadowLineCandidate).intersectWith(angleIntervalToCheck);

              if (shadowLineCandidateAngleInterval.contains(visibleLineCandidateAngleInterval)) {
                // Covers us entirely, do nothing

                // Special case, both shadow and visible candidate have the same interval
                if (visibleLineCandidateAngleInterval.contains(shadowLineCandidateAngleInterval)) {

                  if (visibleDistance > shadowDistance) {
                    unshadowed = false;
                    break;
                  }
                } else {
                  unshadowed = false;
                  break;
                }

              } else if (visibleLineCandidateAngleInterval.intersects(shadowLineCandidateAngleInterval)) {
                // Covers us partly, split angle interval
                var newIntervalsToAdd = new ArrayList<AngleInterval>();

                // Create angle interval of intersection between shadow and visible candidate
                AngleInterval intersectedInterval =
                  visibleLineCandidateAngleInterval.intersectWith(shadowLineCandidateAngleInterval);
                if (intersectedInterval != null) {
                  var tempVector1 = AngleInterval.intersect(unhandledAngles, intersectedInterval);
                  for (var interval : tempVector1) {
                    if (interval != null && !interval.isEmpty()) {
                      newIntervalsToAdd.add(interval);
                    }
                  }
                }

                // Add angle interval of visible candidate without shadow candidate
                var tempVector2 = visibleLineCandidateAngleInterval.subtract(shadowLineCandidateAngleInterval);
                if (tempVector2 != null) {
                  for (var interval : tempVector2) {
                    if (interval != null && !interval.isEmpty()) {
                      newIntervalsToAdd.addAll(AngleInterval.intersect(unhandledAngles, interval));
                    }
                  }
                }

                // Subtract angle interval of visible candidate
                unhandledAngles = AngleInterval.subtract(unhandledAngles, visibleLineCandidateAngleInterval);
                unhandledAnglesChanged = true;

                // Add new angle intervals
                //logger.info("Split angle interval: " + visibleLineCandidateAngleInterval);
                for (var interval : newIntervalsToAdd) {
                  if (interval != null && !interval.isEmpty()) {
                    //logger.info("> into: " + newIntervalsToAdd.get(k));
                    unhandledAngles.add(interval);
                  }
                }
                unshadowed = false;
                break;
              }
              // Ignore when not intersecting at all.
            }

            if (!unshadowed) {
              break;
            }
          }

          if (unhandledAnglesChanged) {
            //logger.info("Unhandled angles changed, restarting..");
            break;
          }

          if (unshadowed) {
            // No other lines shadow this line => this line must be visible!

            unhandledAngles = AngleInterval.subtract(unhandledAngles, visibleLineCandidateAngleInterval);
            visibleLines.add(visibleLineCandidate);

            break;
          }

        }

      }

    } // End of outer loop

    // Save results in order to speed up later calculations
    int size = calculatedVisibleSides.size();
    // Crop saved sides vectors
    if (size >= maxSavedVisibleSides) {
      calculatedVisibleSides.remove(size-1);
      calculatedVisibleSidesSources.remove(size-1);
      calculatedVisibleSidesAngleIntervals.remove(size-1);
      calculatedVisibleSidesLines.remove(size-1);
    }

    calculatedVisibleSides.add(0, visibleLines);
    calculatedVisibleSidesSources.add(0, source);
    calculatedVisibleSidesAngleIntervals.add(0, angleInterval);
    calculatedVisibleSidesLines.add(0, lookThrough);

    return visibleLines;
  }

  /**
   * Calculates and returns the received signal strength (dBm) of a signal sent
   * from the given source position to the given destination position as a
   * random variable. This method uses current parameters such as transmitted
   * power, obstacles, overall system loss etc.
   *
   * @param txPair Information about the source and destination coordinates
   *               and transmission power.
   * @return Received signal strength (dBm) random variable. The first value is
   *         the random variable mean, and the second is the variance.
   */
  public double[] getReceivedSignalStrength(TxPair txPair) {
    return getTransmissionData(txPair, TransmissionData.SIGNAL_STRENGTH);
  }
  

  // TODO Fix better data type support
  private double[] getTransmissionData(TxPair txPair, TransmissionData dataType) {
    Point2D source = txPair.getFrom();
    Point2D dest = txPair.getTo();
    double accumulatedVariance = 0;

    // - Get all ray paths from source to destination -
    RayData originRayData = new RayData(
        RayData.RayType.ORIGIN,
        source,
        null,
        getParameterIntegerValue(Parameter.rt_max_rays),
        getParameterIntegerValue(Parameter.rt_max_refractions),
        getParameterIntegerValue(Parameter.rt_max_reflections),
        getParameterIntegerValue(Parameter.rt_max_diffractions)
    );

    // Check if origin tree is already calculated and saved
    DefaultMutableTreeNode visibleLinesTree = buildVisibleLinesTree(originRayData);

    // Calculate all paths from source to destination, using above calculated tree
    var allPaths = getConnectingPaths(source, dest, visibleLinesTree);
    if (logMode) {
      logInfo.append("Signal components:\n");
      for (var currentPath : allPaths) {
        logInfo.append("* ").append(currentPath).append("\n");
        for (int i=0; i < currentPath.getSubPathCount(); i++) {
          loggedRays.add(currentPath.getSubPath(i));
        }
      }
    }

    // - Extract length and losses of each path -
    var numPaths = allPaths.size();
    double[] pathLengths = new double[numPaths];
    double[] pathGain = new double[numPaths];
    int bestSignalNr = -1;
    double bestSignalPathLoss = 0;
    for (int i = 0; i < numPaths; i++) {
      RayPath currentPath = allPaths.get(i);
      double accumulatedStraightLength = 0;

      for (int j=0; j < currentPath.getSubPathCount(); j++) {
        Line2D subPath = currentPath.getSubPath(j);
        double subPathLength = subPath.getP1().distance(subPath.getP2());
        RayData.RayType subPathStartType = currentPath.getType(j);

        // Type specific losses
        // TODO Type specific losses depends on angles as well!
        if (subPathStartType == RayData.RayType.REFRACTION) {
          pathGain[i] += getParameterDoubleValue(Parameter.rt_refrac_coefficient);
        } else if (subPathStartType == RayData.RayType.REFLECTION) {
          pathGain[i] += getParameterDoubleValue(Parameter.rt_reflec_coefficient);

          // Add FSPL from last subpaths (if FSPL on individual rays)
          if (!getParameterBooleanValue(Parameter.rt_fspl_on_total_length) && accumulatedStraightLength > 0) {
            pathGain[i] += getFSPL(accumulatedStraightLength);
          }
          accumulatedStraightLength = 0; // Reset straight length
        } else if (subPathStartType == RayData.RayType.DIFFRACTION) {
          pathGain[i] += getParameterDoubleValue(Parameter.rt_diffr_coefficient);

          // Add FSPL from last subpaths (if FSPL on individual rays)
          if (!getParameterBooleanValue(Parameter.rt_fspl_on_total_length) && accumulatedStraightLength > 0) {
            pathGain[i] += getFSPL(accumulatedStraightLength);
          }
          accumulatedStraightLength = 0; // Reset straight length
        }
        accumulatedStraightLength += subPathLength; // Add length, FSPL should be calculated on total straight length

        // If ray starts with a refraction, calculate obstacle attenuation
        if (subPathStartType == RayData.RayType.REFRACTION) {
          // Ray passes through a wall, calculate distance through that wall

          // Fetch attenuation constant
          double attenuationConstant = getParameterDoubleValue(Parameter.obstacle_attenuation);

          var allPossibleObstacles = myObstacleWorld.getAllObstaclesNear(subPath.getP1());
          for (var obstacle : allPossibleObstacles) {
            // Calculate the intersection distance
            Line2D line = getIntersectionLine(
                subPath.getP1().getX(),
                subPath.getP1().getY(),
                subPath.getP2().getX(),
                subPath.getP2().getY(),
                obstacle
            );

            if (line != null) {
              pathGain[i] += attenuationConstant * line.getP1().distance(line.getP2());
              break;
            }
          }
        }

        // Add to total path length
        pathLengths[i] += subPathLength;
      }

      // Add FSPL from last rays (if FSPL on individual rays)
      if (!getParameterBooleanValue(Parameter.rt_fspl_on_total_length) && accumulatedStraightLength > 0) {
        pathGain[i] += getFSPL(accumulatedStraightLength);
      }

      // Free space path loss on total path length?
      if (getParameterBooleanValue(Parameter.rt_fspl_on_total_length)) {
        pathGain[i] += getFSPL(pathLengths[i]);
      }

      if (bestSignalNr < 0 || pathGain[i] > bestSignalPathLoss) {
        bestSignalNr = i;
        bestSignalPathLoss = pathGain[i];
      }
    }

    // - Calculate total path loss (using simple Rician) -
    double delaySpread = 0;
    double delaySpreadRMS = 0;
    double freq = getParameterDoubleValue(Parameter.frequency);
    double wavelength = C/(freq*1000000d);
    double totalPathGain = 0;
    double delaySpreadTotalWeight = 0;
    double speedOfLight = 300; // Approximate value (m/us)
    for (int i = 0; i < numPaths; i++) {
      // Ignore insignificant interfering signals
      if (pathGain[i] > pathGain[bestSignalNr] - 30) {
        double pathLengthDiff = Math.abs(pathLengths[i] - pathLengths[bestSignalNr]);

        // Update delay spread TODO Now considering best signal, should be first or mean?
        if (pathLengthDiff > delaySpread) {
          delaySpread = pathLengthDiff;
        }


        // Update root-mean-square delay spread TODO Now considering best signal time, should be mean delay?
        delaySpreadTotalWeight += pathGain[i]*pathGain[i];
        double rmsDelaySpreadComponent = pathLengthDiff/speedOfLight;
        rmsDelaySpreadComponent *= rmsDelaySpreadComponent * pathGain[i]*pathGain[i];
        delaySpreadRMS += rmsDelaySpreadComponent;

        // OK since cosinus is even function
        var pathModdedLengths = pathLengthDiff % wavelength;

        // Using Rician fading approach, TODO Only one best signal considered - combine these? (need two limits)
        totalPathGain += Math.pow(10, pathGain[i]/10.0)*Math.cos(2*Math.PI * pathModdedLengths/wavelength);
        if (logMode) {
          logInfo.append("Signal component: ").append(String.format("%2.3f", pathGain[i])).append(" dB, phase ").append(String.format("%2.3f", (2 */*Math.PI* */ pathModdedLengths / wavelength))).append(" pi\n");
        }
      } else if (logMode) {
        /* TODO Log mode affects result? */
        var pathModdedLengths = (pathLengths[i] - pathLengths[bestSignalNr]) % wavelength;
        logInfo.append("(IGNORED) Signal component: ").append(String.format("%2.3f", pathGain[i])).append(" dB, phase ").append(String.format("%2.3f", (2 */*Math.PI* */ pathModdedLengths / wavelength))).append(" pi\n");
      }

    }

    // Calculate resulting RMS delay spread
    delaySpread /= speedOfLight;
    delaySpreadRMS /= delaySpreadTotalWeight;


    // Convert back to dB
    totalPathGain = 10*Math.log10(Math.abs(totalPathGain));

    if (logMode) {
        logInfo.append("\nTotal path gain: ").append(String.format("%2.3f", totalPathGain)).append(" dB\n");
        logInfo.append("Delay spread: ").append(String.format("%2.3f", delaySpread)).append("\n");
        logInfo.append("RMS delay spread: ").append(String.format("%2.3f", delaySpreadRMS)).append("\n");
    }

    // - Calculate received power -
    // Using formula (dB)
    //  Received power = Output power + System gain + Transmitter gain + Path Loss + Receiver gain
    // TODO Update formulas
    double outputPower = txPair.getTxPower();
    double systemGain = getParameterDoubleValue(Parameter.system_gain_mean);
    if (getParameterBooleanValue(Parameter.apply_random)) {
      Random random = new Random(); /* TODO Use main random generator? */
      systemGain += Math.sqrt(getParameterDoubleValue(Parameter.system_gain_var)) * random.nextGaussian();
    } else {
      accumulatedVariance += getParameterDoubleValue(Parameter.system_gain_var);
    }

    double transmitterGain = 0;
    if (getParameterBooleanValue(Parameter.tx_with_gain)) {
      transmitterGain = txPair.getTxGain();
    }

    double receivedPower = outputPower + systemGain + transmitterGain + totalPathGain;
    if (logMode) {
        logInfo.append("\nReceived signal strength: ").append(String.format("%2.3f", receivedPower)).append(" dB (variance ").append(accumulatedVariance).append(")\n");
    }

    if (dataType == TransmissionData.DELAY_SPREAD || dataType == TransmissionData.DELAY_SPREAD_RMS) {
      return new double[] {delaySpread, delaySpreadRMS};
    }

    return new double[] {receivedPower, accumulatedVariance};
  }

  public static class TrackedSignalComponents {
    List<Line2D> components;
    String log;
  }
  
  /**
   * Returns all rays from given source to given destination if a transmission
   * were to be made. The resulting rays depend on the current settings and may
   * include rays through obstacles, reflected rays or scattered rays.
   *
   * @param txPair Information about the source and destination coordinates
   *               and transmission power.
   * @return Signal components and printable description
   */
  public TrackedSignalComponents getRaysOfTransmission(TxPair txPair) {
    TrackedSignalComponents tsc = new TrackedSignalComponents();

    logInfo = new StringBuilder();
    loggedRays = new ArrayList<>();

    /* TODO Include background noise? */
    logMode = true;
    getProbability(txPair, -Double.MAX_VALUE);
    logMode = false;

    tsc.log = logInfo.toString();
    tsc.components = loggedRays;
    
    logInfo = null;
    loggedRays = null;
    
    return tsc;
  }

  /**
   * Calculates and returns the signal-to-noise ratio (dB) of a signal sent from
   * the given source position to the given destination position as a random
   * variable. This method uses current parameters such as transmitted power,
   * obstacles, overall system loss etc.
   *
   * @param txPair Information about the source and destination coordinates
   *               and transmission power.
   * @return Received SNR (dB) random variable:
   * The first value in the array is the random variable mean.
   * The second is the variance.
   * The third value is the received signal strength which may be used in comparison with interference etc.
   */
  public double[] getSINR(TxPair txPair, double interference) {
    /* TODO Cache values: called repeatedly with noise sources. */

    // Calculate received signal strength
    double[] signalStrength = getReceivedSignalStrength(txPair);
    double[] snrData = { signalStrength[0], signalStrength[1], signalStrength[0] };

    // Add antenna gain
    if (getParameterBooleanValue(Parameter.rx_with_gain)) {
      snrData[0] += txPair.getRxGain();
    }

    double noiseVariance = getParameterDoubleValue(Parameter.bg_noise_var);
    double noiseMean = getParameterDoubleValue(Parameter.bg_noise_mean);

    if (interference > noiseMean) {
      noiseMean = interference;
    }

    if (getParameterBooleanValue(Parameter.apply_random)) {
      Random random = new Random(); /* TODO Use main random generator? */
      noiseMean += Math.sqrt(noiseVariance) * random.nextGaussian();
      noiseVariance = 0;
    }

    // Applying noise to calculate SNR
    snrData[0] -= noiseMean;
    snrData[1] += noiseVariance;

    if (logMode) {
        logInfo.append("\nReceived SNR: ").append(String.format("%2.3f", snrData[0])).append(" dB (variance ").append(snrData[1]).append(")\n");
    }
    return snrData;
  }


  /**
   * Calculates probability that a receiver at given destination receives 
   * a packet from a transmitter at given source.
   * This method uses current parameters such as transmitted power,
   * obstacles, overall system loss, packet size etc.
   * <p>
   * TODO Packet size
   * TODO External interference/Background noise
   *
   * @param txPair Information about the source and destination coordinates
   *               and transmission power.
   * @param interference Current interference at destination (dBm)
   * @return [Probability of reception, signal strength at destination]
   */
  public double[] getProbability(TxPair txPair, double interference) {
    double[] snrData = getSINR(txPair, interference);
    double snrMean = snrData[0];
    double snrVariance = snrData[1];
    double signalStrength = snrData[2];
    double threshold = getParameterDoubleValue(Parameter.snr_threshold);
    double rxSensitivity = getParameterDoubleValue(Parameter.rx_sensitivity);

    // Check signal strength against receiver sensitivity and interference
    if (rxSensitivity > signalStrength - snrMean && 
                threshold < rxSensitivity + snrMean - signalStrength) {
      if (logMode) {
        logInfo.append("Weak signal: increasing threshold\n");
      }

      // Keeping snr variance but increasing theshold to sensitivity
      threshold = rxSensitivity + snrMean - signalStrength;
    }

    // If not random variable, probability is either 1 or 0
    if (snrVariance == 0) {
      return new double[] {
        threshold - snrMean > 0 ? 0:1, signalStrength
    };
    }
    double snrStdDev = Math.sqrt(snrVariance);


    // "Missing" signal strength in order to receive packet is probability that
    // random variable with mean snrMean and standard deviance snrStdDev is above
    // current threshold.

    // (Using error algorithm method, much faster than taylor approximation!)
    double probReception = 1 - GaussianWrapper.cdfErrorAlgo(threshold, snrMean, snrStdDev);

    if (logMode) {
      logInfo.append("Reception probability: ").append(String.format("%1.1f%%", 100 * probReception)).append("\n");
    }

    // Returns probabilities
    return new double[] { probReception, signalStrength };
  }

  /**
   * Calculates and returns root-mean-square delay spread when given destination receives a packet from a transmitter at given source.
   * This method uses current parameters such as transmitted power,
   * obstacles, overall system loss, packet size etc. TODO Packet size?!
   *
   * @param txPair Information about the source and destination coordinates
   *               and transmission power.
   * @return RMS delay spread
   */
  public double getRMSDelaySpread(TxPair txPair) {
    return getTransmissionData(txPair, TransmissionData.DELAY_SPREAD)[1];
  }

  /**
   * Returns XML elements representing the current configuration.
   *
   * @see #setConfigXML(Collection)
   * @return XML element collection
   */
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();
    Element element;

    for (var entry : parameters.entrySet()) {
      var p = entry.getKey();
      if (parametersDefaults.get(p).equals(entry.getValue())) {
        /* Default value */
        continue;
      }
      element = new Element(p.toString());
      element.setAttribute("value", entry.getValue().toString());
      config.add(element);
    }

    element = new Element("obstacles");
    element.addContent(myObstacleWorld.getConfigXML());
    config.add(element);

    return config;
  }

  /**
   * Sets the configuration depending on the given XML elements.
   *
   * @see #getConfigXML()
   * @param configXML
   *          Config XML elements
   * @return True if config was set successfully, false otherwise
   */
  public boolean setConfigXML(Collection<Element> configXML) {
    for (Element element : configXML) {
      if (element.getName().equals("obstacles")) {
        myObstacleWorld = new ObstacleWorld();
        myObstacleWorld.setConfigXML(element.getChildren());
      } else /* Parameter values */ {
        String name = element.getName();
        String value;
        Parameter param;
    
        if (name.equals("wavelength")) {
          /* Backwards compatability: ignored parameters */
          value = element.getAttributeValue("value");
          if (value == null) {
            value = element.getText();
          }
//          private static final double C = 299792458; /* m/s */
          double frequency = C/Double.parseDouble(value);
          frequency /= 1000000.0; /* mhz */
          parameters.put(Parameter.frequency, frequency); /* mhz */

          logger.warn("MRM parameter converted from wavelength to frequency: " + String.format("%1.1f MHz", frequency));
          continue;
        } else if (name.equals("tx_antenna_gain") || name.equals("rx_antenna_gain")) {
          logger.warn("MRM parameter \"" + name + "\" was removed");
          continue;
        } else if (Parameter.fromString(name) != null) {
          /* Backwards compatability: renamed parameters */
          param = Parameter.fromString(name);
        } else {
          param = Parameter.valueOf(name);
        }

        value = element.getAttributeValue("value");
        if (value == null || value.isEmpty()) {
          /* Backwards compatability: renamed parameters */
          value = element.getText();
        }
        
        Class<?> paramClass = parameters.get(param).getClass();
        if (paramClass == Double.class) {
          parameters.put(param, Double.parseDouble(value));
        } else if (paramClass == Boolean.class) {
          parameters.put(param, Boolean.parseBoolean(value));
        } else if (paramClass == Integer.class) {
          parameters.put(param, Integer.parseInt(value));
        } else {
          logger.error("Unsupported class type: " + paramClass);
        }
      }
    }
    needToPrecalculateFSPL = true;
    settingsTriggers.trigger(EventTriggers.Update.UPDATE, null);
    return true;
  }

  public static abstract class TxPair {
    public abstract double getFromX();
    public abstract double getFromY();
    public abstract double getToX();
    public abstract double getToY();
    public abstract double getTxPower();

    public double getDistance() {
      double w = getFromX() - getToX();
      double h = getFromY() - getToY();
      return Math.sqrt(w*w+h*h);
    }

    /**
     * @return Radians
     */
    public double getAngle() {
      return Math.atan2(getToY()-getFromY(), getToX()-getFromX());
    }
    public Point2D getFrom() {
      return new Point2D.Double(getFromX(), getFromY());
    }
    public Point2D getTo() {
      return new Point2D.Double(getToX(), getToY());
    }
    
    /**
     * @return Relative transmitter gain (zero for omnidirectional radios)
     */
    public abstract double getTxGain();
    
    /**
     * @return Relative receiver gain (zero for omnidirectional radios)
     */
    public abstract double getRxGain();
  }
  public static abstract class RadioPair extends TxPair {
    public abstract Radio getFromRadio();
    public abstract Radio getToRadio();
    
    @Override
    public double getDistance() {
      double w = getFromX() - getToX();
      double h = getFromY() - getToY();
      return Math.sqrt(w*w+h*h);
    }
    @Override
    public double getFromX() {
      return getFromRadio().getPosition().getXCoordinate();
    }
    @Override
    public double getFromY() {
      return getFromRadio().getPosition().getYCoordinate();
    }
    @Override
    public double getToX() {
      return getToRadio().getPosition().getXCoordinate();
    }
    @Override
    public double getToY() {
      return getToRadio().getPosition().getYCoordinate();
    }
    @Override
    public double getTxPower() {
      return getFromRadio().getCurrentOutputPower();
    }
    @Override
    public double getTxGain() {
      if (!(getFromRadio() instanceof DirectionalAntennaRadio r)) {
        return 0;
      }
      return r.getRelativeGain(r.getDirection() + getAngle(), getAngle());
    }
    @Override
    public double getRxGain() {
      if (!(getToRadio() instanceof DirectionalAntennaRadio r)) {
        return 0;
      }
      return r.getRelativeGain(r.getDirection() + getAngle() + Math.PI, getDistance());
    }
  }
  
}
