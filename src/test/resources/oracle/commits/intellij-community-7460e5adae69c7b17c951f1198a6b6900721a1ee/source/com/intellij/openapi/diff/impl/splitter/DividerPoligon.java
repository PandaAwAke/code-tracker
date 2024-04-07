package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Comparing;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

class DividerPoligon {
  private final Color myColor;
  private final int myStart1;
  private final int myStart2;
  private final int myEnd1;
  private final int myEnd2;

  public DividerPoligon(int start1, int start2, int end1, int end2, Color color) {
    myStart1 = start1;
    myStart2 = start2;
    myEnd1 = end1;
    myEnd2 = end2;
    myColor = color;
  }

  private void paint(Graphics2D g, int width) {
    g.setColor(myColor);
    g.fill(new Polygon(new int[]{0, 0, width, width}, new int[]{myStart1, myEnd1, myEnd2, myStart2}, 4));
    g.setColor(Color.GRAY);
    g.drawLine(0, myStart1, width, myStart2);
    g.drawLine(0, myEnd1, width, myEnd2);
  }

  public int hashCode() {
    return myStart1 ^ myStart2 ^ myEnd1 ^ myEnd2;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof DividerPoligon)) return false;
    DividerPoligon other = (DividerPoligon)obj;
    return myStart1 == other.myStart1 &&
           myStart2 == other.myStart2 &&
           myEnd1 == other.myEnd1 &&
           myEnd2 == other.myEnd2 && Comparing.equal(myColor, other.myColor);
  }

  public String toString() {
    return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> " + myColor;
  }

  Color getColor() { return myColor; }

  public static void paintPoligons(ArrayList<DividerPoligon> poligons, Graphics2D g, int width) {
    //Composite composite = g.getComposite();
    //g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.4f));
    for (Iterator<DividerPoligon> iterator = poligons.iterator(); iterator.hasNext();) {
      DividerPoligon poligon = iterator.next();
      poligon.paint(g, width);
    }
    //g.setComposite(composite);
  }

  public static ArrayList<DividerPoligon> createVisiblePoligons(EditingSides sides, FragmentSide left) {
    Editor editor1 = sides.getEditor(left);
    Editor editor2 = sides.getEditor(left.otherSide());
    LineBlocks lineBlocks = sides.getLineBlocks();
    Trapezium visibleArea = new Trapezium(getVisibleInterval(editor1),
                                          getVisibleInterval(editor2));
    Interval indecies = lineBlocks.getVisibleIndecies(visibleArea);
    Transformation[] transformations = new Transformation[]{getTransformation(editor1),
                                                            getTransformation(editor2)};
    ArrayList<DividerPoligon> poligons = new ArrayList<DividerPoligon>();
    for (int i = indecies.getStart(); i < indecies.getEnd(); i++) {
      Trapezium trapezium = lineBlocks.getTrapezium(i);
      TextDiffType type = lineBlocks.getType(i);
      if (type == null) continue;
      Color color = type.getPoligonColor(editor1);
      poligons.add(createPoligon(transformations, trapezium, color, left));
    }
    return poligons;
  }

  private static Transformation getTransformation(Editor editor) {
//    return new LinearTransformation(editor.getScrollingModel().getVerticalScrollOffset(), editor.getLineHeight());
    return new FoldingTransformation(editor);
  }

  private static DividerPoligon createPoligon(Transformation[] transformations, Trapezium trapezium, Color color, FragmentSide left) {
    Interval base1 = trapezium.getBase(left);
    Interval base2 = trapezium.getBase(left.otherSide());
    Transformation leftTransform = transformations[left.getIndex()];
    Transformation rightTransform = transformations[left.otherSide().getIndex()];
    int start1 = leftTransform.transform(base1.getStart());
    int end1 = leftTransform.transform(base1.getEnd());
    int start2 = rightTransform.transform(base2.getStart());
    int end2 = rightTransform.transform(base2.getEnd());
    return new DividerPoligon(start1, start2, end1, end2, color);
  }

  static Interval getVisibleInterval(Editor editor1) {
    int offset = editor1.getScrollingModel().getVerticalScrollOffset();
    LogicalPosition logicalPosition = editor1.xyToLogicalPosition(new Point(0, offset));
    int line = logicalPosition.line;
    return new Interval(line, editor1.getComponent().getHeight() / editor1.getLineHeight() + 1);
  }
}
