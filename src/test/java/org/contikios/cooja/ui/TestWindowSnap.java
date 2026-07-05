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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestWindowSnap {
  private static final Rectangle DESKTOP = new Rectangle(0, 0, 1000, 800);
  private static final Dimension MIN_SIZE = new Dimension(50, 40);
  private static final int NEAR = WindowSnap.SNAP_DISTANCE - 3;

  /** A 200x100 window at the given x and y = 300, used by the gesture sequence tests. */
  private static Rectangle at(int x) {
    return new Rectangle(x, 300, 200, 100);
  }

  @Test
  void testMoveSnapsToDesktopBorders() {
    // Left border.
    assertEquals(new Point(0, 300),
            new WindowSnap().snapLocation(new Rectangle(NEAR, 300, 200, 100), DESKTOP, List.of()));
    // Right border.
    assertEquals(new Point(800, 300),
            new WindowSnap().snapLocation(new Rectangle(800 - NEAR, 300, 200, 100), DESKTOP, List.of()));
    // Top border.
    assertEquals(new Point(300, 0),
            new WindowSnap().snapLocation(new Rectangle(300, NEAR, 200, 100), DESKTOP, List.of()));
    // Bottom border.
    assertEquals(new Point(300, 700),
            new WindowSnap().snapLocation(new Rectangle(300, 700 - NEAR, 200, 100), DESKTOP, List.of()));
  }

  @Test
  void testNoSnapOutsideDistance() {
    var proposed = new Rectangle(WindowSnap.SNAP_DISTANCE + 1, 300, 200, 100);
    assertEquals(new Point(proposed.x, proposed.y),
            new WindowSnap().snapLocation(proposed, DESKTOP, List.of()));
  }

  @Test
  void testSnapAtExactDistance() {
    assertEquals(new Point(0, 300),
            new WindowSnap().snapLocation(new Rectangle(WindowSnap.SNAP_DISTANCE, 300, 200, 100),
                    DESKTOP, List.of()));
  }

  @Test
  void testMoveSnapsToAbuttingWindow() {
    var other = new Rectangle(400, 250, 200, 200);
    // Right edge of moving window snaps to left edge of the other window.
    assertEquals(new Point(200, 300),
            new WindowSnap().snapLocation(new Rectangle(200 - NEAR, 300, 200, 100), DESKTOP, List.of(other)));
    // Left edge of moving window snaps to right edge of the other window.
    assertEquals(new Point(600, 300),
            new WindowSnap().snapLocation(new Rectangle(600 + NEAR, 300, 200, 100), DESKTOP, List.of(other)));
    // Bottom edge snaps to top edge of the other window.
    assertEquals(new Point(450, 150),
            new WindowSnap().snapLocation(new Rectangle(450, 150 - NEAR, 100, 100), DESKTOP, List.of(other)));
    // Top edge snaps to bottom edge of the other window.
    assertEquals(new Point(450, 450),
            new WindowSnap().snapLocation(new Rectangle(450, 450 + NEAR, 100, 100), DESKTOP, List.of(other)));
  }

  @Test
  void testNoSnapWithoutPerpendicularOverlap() {
    // Same x-distance as in testMoveSnapsToAbuttingWindow, but the moving
    // window is entirely below the other window, so no snap.
    var other = new Rectangle(400, 250, 200, 200);
    var proposed = new Rectangle(200 - NEAR, 500, 200, 100);
    assertEquals(new Point(proposed.x, proposed.y),
            new WindowSnap().snapLocation(proposed, DESKTOP, List.of(other)));
  }

  @Test
  void testNearestCandidateWins() {
    // Desktop left border is 7 px away, the other window's right edge only 3 px.
    var other = new Rectangle(-100, 300, 110, 100);
    assertEquals(new Point(10, 300),
            new WindowSnap().snapLocation(new Rectangle(NEAR, 300, 200, 100), DESKTOP, List.of(other)));
  }

  @Test
  void testAxesSnapIndependently() {
    var other = new Rectangle(400, 250, 200, 200);
    // The x-axis snaps to the other window and the y-axis to the desktop top border.
    assertEquals(new Point(200, 0),
            new WindowSnap().snapLocation(new Rectangle(200 - NEAR, NEAR, 200, 260), DESKTOP, List.of(other)));
  }

  @Test
  void testScrolledDesktopOrigin() {
    var desktop = new Rectangle(200, 150, 1000, 800);
    assertEquals(new Point(200, 150),
            new WindowSnap().snapLocation(new Rectangle(200 + NEAR, 150 + NEAR, 200, 100), desktop, List.of()));
    assertEquals(new Point(1000, 850),
            new WindowSnap().snapLocation(new Rectangle(1000 - NEAR, 850 + NEAR, 200, 100), desktop, List.of()));
  }

  @Test
  void testResizeRightEdge() {
    var starting = new Rectangle(100, 300, 200, 100);
    var other = new Rectangle(400, 250, 200, 200);
    var proposed = new Rectangle(100, 300, 300 - NEAR, 100);
    assertEquals(new Rectangle(100, 300, 300, 100),
            new WindowSnap().snapBounds(proposed, starting, DESKTOP, List.of(other), MIN_SIZE));
  }

  @Test
  void testResizeLeftEdgeKeepsRightEdgeFixed() {
    var starting = new Rectangle(100, 300, 200, 100);
    var proposed = new Rectangle(NEAR, 300, 300 - NEAR, 100);
    // The left edge snaps to the desktop border; the right edge stays at 300.
    assertEquals(new Rectangle(0, 300, 300, 100),
            new WindowSnap().snapBounds(proposed, starting, DESKTOP, List.of(), MIN_SIZE));
  }

  @Test
  void testResizeTopAndBottomEdges() {
    var starting = new Rectangle(100, 300, 200, 100);
    // Top edge snaps to the desktop border; the bottom edge stays at 400.
    assertEquals(new Rectangle(100, 0, 200, 400),
            new WindowSnap().snapBounds(new Rectangle(100, NEAR, 200, 400 - NEAR), starting,
                    DESKTOP, List.of(), MIN_SIZE));
    // Bottom edge snaps to the desktop border; the top edge stays at 300.
    assertEquals(new Rectangle(100, 300, 200, 500),
            new WindowSnap().snapBounds(new Rectangle(100, 300, 200, 500 - NEAR), starting,
                    DESKTOP, List.of(), MIN_SIZE));
  }

  @Test
  void testResizeIgnoresInactiveEdges() {
    // Only the bottom edge is resized; the left edge is near the desktop border
    // but must not snap.
    var starting = new Rectangle(NEAR, 300, 200, 100);
    var proposed = new Rectangle(NEAR, 300, 200, 150);
    assertEquals(proposed,
            new WindowSnap().snapBounds(proposed, starting, DESKTOP, List.of(), MIN_SIZE));
  }

  @Test
  void testResizeSkipsSnapBelowMinimumSize() {
    // Snapping the right edge to the other window's left edge would shrink the
    // window below the minimum width, so the snap is skipped.
    var other = new Rectangle(150 - NEAR, 250, 200, 200);
    var starting = new Rectangle(100, 300, 200, 100);
    var proposed = new Rectangle(100, 300, 50, 100);
    assertEquals(proposed,
            new WindowSnap().snapBounds(proposed, starting, DESKTOP, List.of(other), MIN_SIZE));
  }

  @Test
  void testBrokenSnapStaysDisabledOnSlowReturn() {
    var snap = new WindowSnap();
    // Approach the desktop left border: snaps.
    assertEquals(new Point(0, 300), snap.snapLocation(at(NEAR), DESKTOP, List.of()));
    // Push past the snap zone: free flowing, and the border snap is disabled.
    assertEquals(new Point(15, 300), snap.snapLocation(at(15), DESKTOP, List.of()));
    // Slowly return to 3 px from the border: must not snap.
    assertEquals(new Point(3, 300), snap.snapLocation(at(3), DESKTOP, List.of()));
    // Other snap lines are unaffected: the y-axis still snaps to the top border.
    assertEquals(new Point(3, 0), snap.snapLocation(new Rectangle(3, NEAR, 200, 100), DESKTOP, List.of()));
  }

  @Test
  void testSnapRearmsAfterMovingAway() {
    var snap = new WindowSnap();
    snap.snapLocation(at(NEAR), DESKTOP, List.of());
    snap.snapLocation(at(15), DESKTOP, List.of());
    // Exactly REARM_DISTANCE away is not enough to re-enable the snap.
    snap.snapLocation(at(WindowSnap.REARM_DISTANCE), DESKTOP, List.of());
    assertEquals(new Point(3, 300), snap.snapLocation(at(3), DESKTOP, List.of()));
    // Beyond REARM_DISTANCE the snap is enabled again.
    snap.snapLocation(at(WindowSnap.REARM_DISTANCE + 1), DESKTOP, List.of());
    assertEquals(new Point(0, 300), snap.snapLocation(at(NEAR), DESKTOP, List.of()));
  }

  @Test
  void testBreakAwayIsPerSnapLine() {
    // The other window's left edge is 11 px from the moving window's right
    // edge when the window is at the desktop border, so only the border snap
    // engages at first.
    var other = new Rectangle(218, 300, 100, 100);
    var snap = new WindowSnap();
    assertEquals(new Point(0, 300), snap.snapLocation(at(NEAR), DESKTOP, List.of(other)));
    // Breaking away disables only the border line: the right edge immediately
    // snaps to the other window's left edge, now 3 px away.
    assertEquals(new Point(18, 300), snap.snapLocation(at(15), DESKTOP, List.of(other)));
  }

  @Test
  void testNearbyLinesDisableTogether() {
    // The other window's right edge is 8 px from the desktop left border, so
    // both snap lines are visited on approach. Breaking away past both must
    // disable both, or the return would snap to the losing line.
    var other = new Rectangle(-192, 300, 200, 100);
    var snap = new WindowSnap();
    assertEquals(new Point(8, 300), snap.snapLocation(at(NEAR), DESKTOP, List.of(other)));
    assertEquals(new Point(25, 300), snap.snapLocation(at(25), DESKTOP, List.of(other)));
    assertEquals(new Point(3, 300), snap.snapLocation(at(3), DESKTOP, List.of(other)));
    assertEquals(new Point(9, 300), snap.snapLocation(at(9), DESKTOP, List.of(other)));
  }

  @Test
  void testNewGestureStartsArmed() {
    var snap = new WindowSnap();
    snap.snapLocation(at(NEAR), DESKTOP, List.of());
    snap.snapLocation(at(15), DESKTOP, List.of());
    // A new gesture uses a fresh instance, so the border snap works directly.
    assertEquals(new Point(0, 300), new WindowSnap().snapLocation(at(NEAR), DESKTOP, List.of()));
  }

  @Test
  void testResizeBreakAwayDoesNotResnap() {
    var starting = new Rectangle(100, 300, 200, 100);
    var other = new Rectangle(400, 250, 200, 200);
    var snap = new WindowSnap();
    // The right edge at 393 snaps to the other window's left edge at 400.
    assertEquals(new Rectangle(100, 300, 300, 100),
            snap.snapBounds(new Rectangle(100, 300, 300 - NEAR, 100), starting, DESKTOP, List.of(other), MIN_SIZE));
    // Pull the edge back past the snap zone: free flowing.
    var freed = new Rectangle(100, 300, 285, 100);
    assertEquals(freed, snap.snapBounds(freed, starting, DESKTOP, List.of(other), MIN_SIZE));
    // Slowly return to 3 px from the edge: must not snap.
    var close = new Rectangle(100, 300, 297, 100);
    assertEquals(close, snap.snapBounds(close, starting, DESKTOP, List.of(other), MIN_SIZE));
  }

  @Test
  void testMoveAlignsLeftEdgesWhenStacked() {
    // The moving window is placed just below the other window: the top edge
    // abuts the other window's bottom edge and the left edges align.
    var other = new Rectangle(400, 250, 200, 200);
    assertEquals(new Point(400, 450),
            new WindowSnap().snapLocation(new Rectangle(400 + NEAR, 450 + NEAR, 100, 100), DESKTOP, List.of(other)));
  }

  @Test
  void testMoveAlignsRightEdgesWhenStacked() {
    var other = new Rectangle(400, 250, 200, 200);
    assertEquals(new Point(500, 450),
            new WindowSnap().snapLocation(new Rectangle(500 + NEAR, 450 + NEAR, 100, 100), DESKTOP, List.of(other)));
  }

  @Test
  void testMoveAlignsTopEdgesWhenBeside() {
    var other = new Rectangle(400, 250, 200, 200);
    assertEquals(new Point(600, 250),
            new WindowSnap().snapLocation(new Rectangle(600 + NEAR, 250 + NEAR, 100, 100), DESKTOP, List.of(other)));
  }

  @Test
  void testNoAlignmentBeyondPerpendicularGap() {
    // Same x-offset as in testMoveAlignsLeftEdgesWhenStacked, but the gap to
    // the other window is one more than the snap distance: no snap on any axis.
    var other = new Rectangle(400, 250, 200, 200);
    var proposed = new Rectangle(400 + NEAR, 450 + WindowSnap.SNAP_DISTANCE + 1, 100, 100);
    assertEquals(new Point(proposed.x, proposed.y),
            new WindowSnap().snapLocation(proposed, DESKTOP, List.of(other)));
  }

  @Test
  void testResizeAlignsRightEdge() {
    var starting = new Rectangle(100, 300, 200, 100);
    var other = new Rectangle(400, 250, 200, 200);
    // The right edge at 593 aligns with the other window's right edge at 600.
    assertEquals(new Rectangle(100, 300, 500, 100),
            new WindowSnap().snapBounds(new Rectangle(100, 300, 500 - NEAR, 100), starting,
                    DESKTOP, List.of(other), MIN_SIZE));
  }

  @Test
  void testShrinkAndGrow() {
    var insets = new Insets(1, 2, 3, 4);
    var r = new Rectangle(100, 200, 50, 60);
    assertEquals(new Rectangle(102, 201, 44, 56), WindowSnap.shrink(r, insets));
    assertEquals(r, WindowSnap.grow(WindowSnap.shrink(r, insets), insets));
  }

  @Test
  void testMoveSnapsVisibleEdgesWithTransparentMargins() {
    // Both windows have a 5 px transparent margin around their visible border,
    // as with the FlatLaf internal frame border. The visible edges snap flush,
    // so the full window bounds overlap by the two margins.
    var margin = new Insets(5, 5, 5, 5);
    var other = WindowSnap.shrink(new Rectangle(400, 250, 200, 200), margin);
    var proposed = new Rectangle(210 - NEAR, 300, 200, 100);
    var p = new WindowSnap().snapLocation(WindowSnap.shrink(proposed, margin),
            WindowSnap.shrink(DESKTOP, margin), List.of(other));
    assertEquals(new Point(210, 300), new Point(p.x - margin.left, p.y - margin.top));
  }

  @Test
  void testMoveSnapsFullBoundsToDesktopWithTransparentMargins() {
    // The full window bounds, including the transparent margin, snap to the
    // desktop border.
    var margin = new Insets(5, 5, 5, 5);
    var proposed = new Rectangle(NEAR, 300, 200, 100);
    var p = new WindowSnap().snapLocation(WindowSnap.shrink(proposed, margin),
            WindowSnap.shrink(DESKTOP, margin), List.of());
    assertEquals(new Point(0, 300), new Point(p.x - margin.left, p.y - margin.top));
  }
}
