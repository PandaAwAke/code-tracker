package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.IdeFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.jetbrains.annotations.NonNls;

public class ActionButton extends JComponent implements ActionButtonComponent {
  private static final Insets ICON_INSETS = new Insets(2, 2, 2, 2);

  private static final Icon ourEmptyIcon = EmptyIcon.create(18, 18);

  private Dimension myMinimumButtonSize;
  private PropertyChangeListener myActionButtonSynchronizer;
  private Icon myDisabledIcon;
  private Icon myIcon;
  protected Presentation myPresentation;
  protected AnAction myAction;
  private String myPlace;
  private ActionButtonLook myLook = ActionButtonLook.IDEA_LOOK;
  protected boolean myMouseDown;
  protected boolean myRollover;
  private static boolean ourGlobalMouseDown = false;

  public ActionButton(
    final AnAction action,
    final Presentation presentation,
    final String place,
    final Dimension minimumSize
    ) {
    if (minimumSize == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("minimumSize cannot be null");
    }
    setMinimumButtonSize(minimumSize);
    myRollover = false;
    myMouseDown = false;
    myAction = action;
    myPresentation = presentation;
    myPlace = place;
    setFocusable(false);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    myMinimumButtonSize = minimumSize;
  }

  public void setMinimumButtonSize(Dimension size) {
    if (size == null) {
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("size cannot be null");
    }
    myMinimumButtonSize = size;
  }

  public void paintChildren(Graphics g) {}

  public int getPopState() {
    if (myAction instanceof ToggleAction) {
      Boolean selected = (Boolean)myPresentation.getClientProperty(ToggleAction.SELECTED_PROPERTY);
      boolean flag1 = selected != null && selected.booleanValue();
      return getPopState(flag1);
    }
    else {
      return getPopState(false);
    }
  }

  protected boolean isButtonEnabled() {
    return myPresentation.isEnabled();
  }

  protected void onMousePresenceChanged(boolean setInfo) {
    IdeFrame frame = (IdeFrame)SwingUtilities.getAncestorOfClass(IdeFrame.class, this);
    if (frame != null) {
      frame.getStatusBar().setInfo(setInfo ? myPresentation.getDescription() : null);
    }
  }

  protected void performAction(MouseEvent e) {
    AnActionEvent event = new AnActionEvent(
      e,
      DataManager.getInstance().getDataContext(),
      myPlace,
      myPresentation,
      ActionManager.getInstance(),
      e.getModifiers()
    );
    myAction.update(event);
    if (isButtonEnabled()) {
      ActionManagerEx.getInstanceEx().fireBeforeActionPerformed(myAction, event.getDataContext());
      Component component = ((Component)event.getDataContext().getData(DataConstantsEx.CONTEXT_COMPONENT));
      if (component != null && !component.isShowing()) {
        return;
      }
      myAction.actionPerformed(event);
    }
  }

  public void removeNotify() {
    if (myActionButtonSynchronizer != null) {
      myPresentation.removePropertyChangeListener(myActionButtonSynchronizer);
      myActionButtonSynchronizer = null;
    }
    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();
    if (myActionButtonSynchronizer == null) {
      myActionButtonSynchronizer = new ActionButtonSynchronizer();
      myPresentation.addPropertyChangeListener(myActionButtonSynchronizer);
    }
    updateToolTipText();
    updateIcon();
  }

  public void setToolTipText(String s) {
    String tooltipText = AnAction.createTooltipText(s, myAction);
    super.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
  }

  public Dimension getPreferredSize() {
    Icon icon = getIcon();
    if (
      icon.getIconWidth() < myMinimumButtonSize.width &&
      icon.getIconHeight() < myMinimumButtonSize.height
    ) {
      return myMinimumButtonSize;
    }
    else {
      return new Dimension(
        icon.getIconWidth() + ICON_INSETS.left + ICON_INSETS.right,
        icon.getIconHeight() + ICON_INSETS.top + ICON_INSETS.bottom
      );
    }
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /**
   * @return button's icon. Icon depends on action's state. It means that the method returns
   *         disabled icon if action is disabled. If the action's icon is <code>null</code> then it returns
   *         an empty icon.
   */
  protected Icon getIcon() {
    Icon icon = isButtonEnabled() ? myIcon : myDisabledIcon;
    if (icon == null) {
      icon = ourEmptyIcon;
    }
    return icon;
  }

  private void updateIcon() {
    myIcon = myPresentation.getIcon();
    if (myPresentation.getDisabledIcon() != null) { // set disabled icon if it is specified
      myDisabledIcon = myPresentation.getDisabledIcon();
    }
    else {
      myDisabledIcon = IconLoader.getDisabledIcon(myIcon);
    }
  }

  private void setDisabledIcon(Icon icon) {
    myDisabledIcon = icon;
  }

  void updateToolTipText() {
    setToolTipText(myPresentation.getText());
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    ActionButtonLook look = getButtonLook();
    look.paintBackground(g, this);
    look.paintIcon(g, this, getIcon());
    look.paintBorder(g, this);
  }

  private ActionButtonLook getButtonLook() {
    return myLook;
  }

  public void setLook(ActionButtonLook look) {
    if (look != null) {
      myLook = look;
    }
    else {
      myLook = ActionButtonLook.IDEA_LOOK;
    }
    repaint();
  }

  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
    if (e.isConsumed()) return;
    boolean flag = e.isMetaDown();
    switch (e.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        {
          if (flag || !isButtonEnabled()) return;
          myMouseDown = true;
          ourGlobalMouseDown = true;
          repaint();
          break;
        }

      case MouseEvent.MOUSE_RELEASED:
        {
          if (flag || !isButtonEnabled()) return;
          myMouseDown = false;
          ourGlobalMouseDown = false;
          if (myRollover) {
            performAction(e);
          }
          repaint();
          break;
        }

      case MouseEvent.MOUSE_ENTERED:
        {
          if (!myMouseDown && ourGlobalMouseDown) break;
          myRollover = true;
          repaint();
          onMousePresenceChanged(true);
          break;
        }

      case MouseEvent.MOUSE_EXITED:
        {
          myRollover = false;
          if (!myMouseDown && ourGlobalMouseDown) break;
          repaint();
          onMousePresenceChanged(false);
          break;
        }
    }
  }

  protected int getPopState(boolean isPushed) {
    if (isPushed || myRollover && myMouseDown && isButtonEnabled()) {
      return PUSHED;
    }
    else {
      return !myRollover || !isButtonEnabled() ? NORMAL : POPPED;
    }
  }

  private class ActionButtonSynchronizer implements PropertyChangeListener {
    @NonNls protected static final String SELECTED_PROPERTY_NAME = "selected";

    public void propertyChange(PropertyChangeEvent e) {
      String propertyName = e.getPropertyName();
      if (Presentation.PROP_TEXT.equals(propertyName)) {
        updateToolTipText();
      }
      else if (Presentation.PROP_ENABLED.equals(propertyName)) {
        repaint();
      }
      else if (Presentation.PROP_ICON.equals(propertyName)) {
        updateIcon();
        repaint();
      }
      else if (Presentation.PROP_DISABLED_ICON.equals(propertyName)) {
        setDisabledIcon(myPresentation.getDisabledIcon());
        repaint();
      }
      else if (Presentation.PROP_VISIBLE.equals(propertyName)) {
      }
      else if (SELECTED_PROPERTY_NAME.equals(propertyName)) {
        repaint();
      }
    }
  }
}
