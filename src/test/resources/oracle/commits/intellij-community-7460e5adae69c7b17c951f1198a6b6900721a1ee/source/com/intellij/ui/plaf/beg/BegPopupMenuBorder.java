
package com.intellij.ui.plaf.beg;

import java.awt.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;

/**
 *
 */
public class BegPopupMenuBorder extends AbstractBorder implements UIResource {
  protected static Insets borderInsets = new Insets(3, 2, 2, 2);
  protected static Color color1 = new Color(214, 211, 206);
  protected static Color color2 = Color.white;
  protected static Color color3 = new Color(132, 130, 132);
  protected static Color color4 = new Color(66, 65, 66);

  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);

    g.setColor(color1);
    g.drawLine(0, 0, w - 2, 0);
    g.drawLine(0, 0, 0, h - 2);
    g.setColor(color2);
    g.drawLine(1, 1, w - 3, 1);
    g.drawLine(1, 1, 1, h - 3);
    g.setColor(color3);
    g.drawLine(1, h - 2, w - 2, h - 2);
    g.drawLine(w - 2, 1, w - 2, h - 2);
    g.setColor(color4);
    g.drawLine(0, h - 1, w - 1, h - 1);
    g.drawLine(w - 1, 0, w - 1, h - 1);
    g.translate(-x, -y);
  }

  public Insets getBorderInsets(Component c) {
    return borderInsets;
  }
}
