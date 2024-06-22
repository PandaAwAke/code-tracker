/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components.labels;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.UI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * @author kir
 */
public class LinkLabel extends JLabel {
  private boolean myUnderline;

  private LinkListener myLinkListener;
  private Object myLinkData;

  private static Set ourVisitedLinks = new HashSet();

  private boolean myIsLinkActive;

  private String myVisitedLinksKey;
  private int myIconWidth;
  private Icon myHoveringIcon;
  private Icon myInactiveIcon;

  private boolean myClickIsBeingProcessed;
  private boolean myPaintDefaultIcon = false;
  protected final int DEFAULT_ICON_GAP = 2;
  private static final Icon LINK = IconLoader.getIcon("/ide/link.png");

  public LinkLabel() {
    this("", LINK);
  }

  public LinkLabel(String text, Icon icon) {
    this(text, icon, null, null, null);
  }

  public LinkLabel(String text, Icon icon, LinkListener aListener) {
    this(text, icon, aListener, null, null);
  }

  public LinkLabel(String text, Icon icon, LinkListener aListener, Object aLinkData) {
    this(text, icon, aListener, aLinkData, null);
  }

  public LinkLabel(String text, Icon icon, LinkListener aListener, Object aLinkData, String aVisitedLinksKey) {
    super(text, icon, JLabel.LEFT);
    setOpaque(false);

    setListener(aListener, aLinkData);

    myIconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
    myInactiveIcon = getIcon();

    MyMouseHandler mouseHandler = new MyMouseHandler();
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);

    myVisitedLinksKey = aVisitedLinksKey;
  }

  public void setHoveringIcon(Icon iconForHovering) {
    myHoveringIcon = iconForHovering;
  }

  public void setListener(LinkListener listener, Object linkData) {
    myLinkListener = listener;
    myLinkData = linkData;
  }

  public void doClick() {
    if (myClickIsBeingProcessed) return;

    myClickIsBeingProcessed = true;
    if (myLinkListener != null) myLinkListener.linkSelected(this, myLinkData);
    ourVisitedLinks.add(myVisitedLinksKey);
    repaint();
    myClickIsBeingProcessed = false;
  }

  public boolean isVisited() {
    return myVisitedLinksKey != null && ourVisitedLinks.contains(myVisitedLinksKey);
  }

  protected void paintComponent(Graphics g) {
    final Border border = getBorder();
    int shiftX = 0, shiftY = 0;

    if (border != null) {
      shiftX = border.getBorderInsets(this).left;
      shiftY = border.getBorderInsets(this).top;
    }

    setForeground(myIsLinkActive ? getActive() : isVisited() ? getVisited() : getNormal());

    super.paintComponent(g);


    if (getText() != null) {
      g.setColor(myIsLinkActive ? getActive() : isVisited() ? getVisited() : getNormal());
      int x = myIconWidth;
      int y = getTextBaseLine();

      if (myUnderline) {
        int k = 1;
        if (getFont().getSize() > 11) {
          k += (getFont().getSize() - 11);
        }

        y += k;

        if (getHorizontalAlignment() == LEFT) {
          UIUtil.drawLine(g, x + shiftX, y + shiftY, x + getFontMetrics(getFont()).stringWidth(getText()) + shiftX, y + shiftY);
        }
        else {
          UIUtil.drawLine(g, getWidth() - 1 - getFontMetrics(getFont()).stringWidth(getText()) + shiftX, y + shiftY,
                          getWidth() - 1 + shiftX, y + shiftY);
        }
      }
      else {
      }

      if (myPaintDefaultIcon) {
        int endX = myIconWidth + getFontMetrics(getFont()).stringWidth(getText());
        int endY = getHeight() / 2 - LINK.getIconHeight() / 2 + 1;

        LINK.paintIcon(this, g, endX + shiftX + DEFAULT_ICON_GAP, endY);
      }
    }
  }

  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.width += myPaintDefaultIcon ? LINK.getIconWidth() + DEFAULT_ICON_GAP : 0;
    return size;
  }


  public void removeNotify() {
    super.removeNotify();
    disableUnderline();
  }

  private void setActive(boolean isActive) {
    myIsLinkActive = isActive;
    repaint();
  }

  private int getTextBaseLine() {
    FontMetrics fm = getFontMetrics(getFont());
    return getHeight() / 2 + (fm.getHeight() / 2 - fm.getDescent());
  }

  private boolean isInClickableArea(Point pt) {
    if (getIcon() != null) {
      if (pt.getX() < getIcon().getIconWidth() && pt.getY() < getIcon().getIconHeight()) {
        return true;
      }
    }
    if (getText() != null) {
      FontMetrics fm = getFontMetrics(getFont());
      int height = fm.getHeight() + 1;
      int y = getHeight() / 2 - fm.getHeight() / 2;
      int width = fm.stringWidth(getText());
      if (myPaintDefaultIcon) {
        width += LINK.getIconWidth() + DEFAULT_ICON_GAP;
      }

      if (getHorizontalAlignment() == LEFT) {
        return (new Rectangle(myIconWidth, y, width, height).contains(pt));
      }
      else {
        return (new Rectangle(getWidth() - width - 1, y, getWidth() - 1, height).contains(pt));
      }
    }

    return false;
  }

  private void enableUnderline() {
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myUnderline = true;
    if (myHoveringIcon != null) {
      setIcon(myHoveringIcon);
    }
    setStatusBarText(getToolTipText());
    repaint();
  }

  private void disableUnderline() {
    setCursor(Cursor.getDefaultCursor());
    myUnderline = false;
    setIcon(myInactiveIcon);
    setStatusBarText(null);
    setActive(false);
  }

  private void setStatusBarText(String statusBarText) {
    if (ApplicationManager.getApplication() == null) return; // makes this component work in UIDesigner preview.
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < projects.length; i++) {
      WindowManager.getInstance().getStatusBar(projects[i]).setInfo(statusBarText);
    }
  }

  public static void clearVisitedHistory() {
    ourVisitedLinks.clear();
  }

  private static Color getVisited() {
    return UI.getColor("link.visited.foreground");
  }

  private static Color getActive() {
    return UI.getColor("link.pressed.foreground");
  }

  private static Color getNormal() {
    return UI.getColor("link.foreground");
  }

  public void entered(MouseEvent e) {
    enableUnderline();
  }

  public void exited(MouseEvent e) {
    disableUnderline();
  }

  public void pressed(MouseEvent e) {
    doClick();
  }

  private class MyMouseHandler extends MouseAdapter implements MouseMotionListener {
    public void mouseClicked(MouseEvent e) {
      if (isInClickableArea(e.getPoint()) && e.getClickCount() == 1) {
        doClick();
      }
    }

    public void mousePressed(MouseEvent e) {
      if (isInClickableArea(e.getPoint())) {
        setActive(true);
      }
    }

    public void mouseReleased(MouseEvent e) {
      setActive(false);
    }

    public void mouseMoved(MouseEvent e) {
      if (isInClickableArea(e.getPoint())) {
        enableUnderline();
      }
      else {
        disableUnderline();
      }
    }

    public void mouseExited(MouseEvent e) {
      disableUnderline();
    }

    public void mouseDragged(MouseEvent e) {
    }
  }

  public void setDefaultIconPainted(boolean paintDefaultIcon) {
    myPaintDefaultIcon = paintDefaultIcon;
  }

  public static void main(String[] args) {
    final JFrame jFrame = new JFrame();
    final Container contentPane = jFrame.getContentPane();
    contentPane.setLayout(new FlowLayout());

    final LinkLabel linkLabel = new LinkLabel("text", LINK);
    //    linkLabel.setFont(linkLabel.getFont().deriveFont(25f));
    linkLabel.setDefaultIconPainted(true);

    contentPane.add(linkLabel);

    jFrame.setBounds(100, 100, 100, 100);
    jFrame.show();

    linkLabel.setBorder(new EmptyBorder(10, 10, 10, 10));


  }

}
