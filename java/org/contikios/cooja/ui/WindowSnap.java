/*
 * Copyright (c) 2026, RISE Research Institutes of Sweden AB.
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
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.ui;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;

/**
 * Computes snapped positions for desktop windows during one move or resize
 * gesture. Window edges snap to the borders of the visible desktop area, to
 * abutting edges of other windows (flush side-by-side), and to aligned edges
 * of nearby windows (left-to-left, top-to-top, and so on). All computations
 * use the proposed, unsnapped bounds, so a window breaks away from a snap
 * once the pointer moves past the snap distance. A snap line the window has
 * broken away from stays disabled, allowing pixel-accurate placement near it,
 * until the window moves at least REARM_DISTANCE away from it. Create one
 * instance per gesture; the disabled state is not kept across gestures.
 */
public final class WindowSnap {
  /** Maximum distance in pixels between edges for a snap to engage. */
  public static final int SNAP_DISTANCE = 10;
  /** Distance in pixels a window must move away from a broken snap line to enable it again. */
  public static final int REARM_DISTANCE = 3 * SNAP_DISTANCE;
  /** Return value of snapAxis when no snap target is close enough. */
  private static final int NO_SNAP = Integer.MIN_VALUE;

  /**
   * Returns the rectangle shrunk by the given insets. Used to get the visible
   * part of a window whose border has a transparent margin, such as a drop shadow.
   */
  public static Rectangle shrink(Rectangle r, Insets insets) {
    return new Rectangle(r.x + insets.left, r.y + insets.top,
            r.width - insets.left - insets.right, r.height - insets.top - insets.bottom);
  }

  /** Returns the rectangle grown by the given insets; the inverse of shrink. */
  public static Rectangle grow(Rectangle r, Insets insets) {
    return new Rectangle(r.x - insets.left, r.y - insets.top,
            r.width + insets.left + insets.right, r.height + insets.top + insets.bottom);
  }

  /** A snap target line: which axis, which edge of the moving window, and the target coordinate. */
  private record SnapLine(boolean xAxis, boolean lowEdge, int coordinate) {}

  private enum State { IN_ZONE, DISARMED }

  /** Snap lines the gesture has visited; a line not in the map is armed. */
  private final HashMap<SnapLine, State> lines = new HashMap<>();

  /**
   * Returns the snapped top-left location for a window being moved.
   *
   * @param proposed proposed window bounds
   * @param desktopVisible visible rectangle of the desktop pane
   * @param others bounds of the other windows on the desktop
   * @return the snapped location, equal to the proposed location if no snap applies
   */
  public Point snapLocation(Rectangle proposed, Rectangle desktopVisible, List<Rectangle> others) {
    updateLineStates(proposed);
    int dx = snapAxis(true, true, true, proposed, desktopVisible, others);
    int dy = snapAxis(false, true, true, proposed, desktopVisible, others);
    return new Point(proposed.x + (dx == NO_SNAP ? 0 : dx), proposed.y + (dy == NO_SNAP ? 0 : dy));
  }

  /**
   * Returns the snapped bounds for a window being resized. Only the edges that
   * differ between the starting and proposed bounds snap; the opposite edge of
   * a snapped edge stays fixed. A snap that would make the window smaller than
   * the minimum size is skipped.
   *
   * @param proposed proposed window bounds
   * @param starting window bounds when the resize gesture started
   * @param desktopVisible visible rectangle of the desktop pane
   * @param others bounds of the other windows on the desktop
   * @param minSize minimum window size
   * @return the snapped bounds, equal to the proposed bounds if no snap applies
   */
  public Rectangle snapBounds(Rectangle proposed, Rectangle starting, Rectangle desktopVisible,
                              List<Rectangle> others, Dimension minSize) {
    updateLineStates(proposed);
    var r = new Rectangle(proposed);
    boolean leftActive = proposed.x != starting.x;
    boolean rightActive = proposed.x + proposed.width != starting.x + starting.width;
    boolean topActive = proposed.y != starting.y;
    boolean bottomActive = proposed.y + proposed.height != starting.y + starting.height;
    int dx = snapAxis(true, leftActive, rightActive, proposed, desktopVisible, others);
    if (dx != NO_SNAP) {
      if (leftActive) {
        // Move the left edge, keep the right edge fixed.
        if (r.width - dx >= minSize.width) {
          r.x += dx;
          r.width -= dx;
        }
      } else if (r.width + dx >= minSize.width) {
        r.width += dx;
      }
    }
    int dy = snapAxis(false, topActive, bottomActive, proposed, desktopVisible, others);
    if (dy != NO_SNAP) {
      if (topActive) {
        if (r.height - dy >= minSize.height) {
          r.y += dy;
          r.height -= dy;
        }
      } else if (r.height + dy >= minSize.height) {
        r.height += dy;
      }
    }
    return r;
  }

  /**
   * Updates the state of all visited snap lines from the proposed, unsnapped
   * bounds: a line whose snap zone the window has left is disabled, and a
   * disabled line the window has moved at least REARM_DISTANCE away from is
   * armed again. Uses only the stored line coordinate, so lines that are
   * temporarily not snap candidates are still re-armed correctly.
   */
  private void updateLineStates(Rectangle proposed) {
    var iterator = lines.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      var line = entry.getKey();
      int edge = line.xAxis()
              ? (line.lowEdge() ? proposed.x : proposed.x + proposed.width)
              : (line.lowEdge() ? proposed.y : proposed.y + proposed.height);
      int distance = Math.abs(edge - line.coordinate());
      if (distance > REARM_DISTANCE) {
        iterator.remove();
      } else if (distance > SNAP_DISTANCE && entry.getValue() == State.IN_ZONE) {
        entry.setValue(State.DISARMED);
      }
    }
  }

  /**
   * Returns the smallest adjustment within SNAP_DISTANCE that makes an active
   * edge on one axis coincide with an armed snap target, or NO_SNAP if none is
   * close enough. Other windows contribute abutting targets (low edge to their
   * high edge and vice versa) and alignment targets (low edge to their low
   * edge, high edge to their high edge), but only when the windows overlap or
   * nearly abut in the perpendicular axis: for the x-axis this is tested on y
   * and vice versa. Desktop borders are considered before other windows,
   * abutting before alignment, and windows in list order, so ties
   * deterministically keep the first candidate found.
   */
  private int snapAxis(boolean xAxis, boolean lowActive, boolean highActive,
                       Rectangle proposed, Rectangle desktopVisible, List<Rectangle> others) {
    int low = xAxis ? proposed.x : proposed.y;
    int high = low + (xAxis ? proposed.width : proposed.height);
    int best = NO_SNAP;
    int desktopLow = xAxis ? desktopVisible.x : desktopVisible.y;
    int desktopHigh = desktopLow + (xAxis ? desktopVisible.width : desktopVisible.height);
    if (lowActive) {
      best = consider(best, xAxis, true, desktopLow, low);
    }
    if (highActive) {
      best = consider(best, xAxis, false, desktopHigh, high);
    }
    for (var other : others) {
      // Only snap to nearby windows: those overlapping in the perpendicular
      // axis, or within SNAP_DISTANCE of abutting there so that a window
      // stacked flush against another can align its side edges with it.
      int perpLow = xAxis ? proposed.y : proposed.x;
      int perpExtent = xAxis ? proposed.height : proposed.width;
      int otherPerpLow = xAxis ? other.y : other.x;
      int otherPerpExtent = xAxis ? other.height : other.width;
      if (perpLow > otherPerpLow + otherPerpExtent + SNAP_DISTANCE
              || perpLow + perpExtent + SNAP_DISTANCE < otherPerpLow) {
        continue;
      }
      int otherLow = xAxis ? other.x : other.y;
      int otherHigh = otherLow + (xAxis ? other.width : other.height);
      if (lowActive) {
        best = consider(best, xAxis, true, otherHigh, low);
        best = consider(best, xAxis, true, otherLow, low);
      }
      if (highActive) {
        best = consider(best, xAxis, false, otherLow, high);
        best = consider(best, xAxis, false, otherHigh, high);
      }
    }
    return best;
  }

  /**
   * Considers snapping a window edge to a target line. A line within the snap
   * zone is marked visited unless it is disabled, in which case it is skipped.
   * Returns the new best adjustment.
   */
  private int consider(int best, boolean xAxis, boolean lowEdge, int target, int edge) {
    int delta = target - edge;
    if (Math.abs(delta) > SNAP_DISTANCE) {
      return best;
    }
    var line = new SnapLine(xAxis, lowEdge, target);
    if (lines.get(line) == State.DISARMED) {
      return best;
    }
    lines.put(line, State.IN_ZONE);
    return best == NO_SNAP || Math.abs(delta) < Math.abs(best) ? delta : best;
  }
}
