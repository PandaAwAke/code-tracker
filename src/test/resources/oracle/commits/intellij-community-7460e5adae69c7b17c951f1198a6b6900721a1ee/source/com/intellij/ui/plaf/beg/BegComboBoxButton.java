
package com.intellij.ui.plaf.beg;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.metal.MetalComboBoxButton;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class BegComboBoxButton extends MetalComboBoxButton {
  public BegComboBoxButton(JComboBox cb, Icon i, boolean onlyIcon, CellRendererPane pane, JList list) {
    super(cb, i, onlyIcon, pane, list);
  }

  public BegComboBoxButton(JComboBox cb, Icon i, CellRendererPane pane, JList list) {
    super(cb, i, pane, list);
  }

/*
  protected JComboBox comboBox;
  protected JList listBox;
  protected CellRendererPane rendererPane;
  protected Icon comboIcon;
  protected boolean iconOnly = false;

  public final JComboBox getComboBox() { return comboBox;}
  public final void setComboBox( JComboBox cb ) { comboBox = cb;}

  public final Icon getComboIcon() { return comboIcon;}
  public final void setComboIcon( Icon i ) { comboIcon = i;}

  public final boolean isIconOnly() { return iconOnly;}
  public final void setIconOnly( boolean isIconOnly ) { iconOnly = isIconOnly;}

  BegComboBoxButton() {
      super( "" );
      DefaultButtonModel model = new DefaultButtonModel() {
          public void setArmed( boolean armed ) {
              super.setArmed( isPressed() ? true : armed );
          }
      };

      setModel( model );
  }

  public BegComboBoxButton( JComboBox cb, Icon i,
                              CellRendererPane pane, JList list ) {
      this();
      comboBox = cb;
      comboIcon = i;
      rendererPane = pane;
      listBox = list;
      setEnabled( comboBox.isEnabled() );
      setRequestFocusEnabled( comboBox.isEnabled() );
  }

  public BegComboBoxButton( JComboBox cb, Icon i, boolean onlyIcon,
                              CellRendererPane pane, JList list ) {
      this( cb, i, pane, list );
      iconOnly = onlyIcon;
  }

  public boolean isFocusTraversable() {
   return (!comboBox.isEditable()) && comboBox.isEnabled();
  }
*/

  public void paintComponent(Graphics g) {
    boolean leftToRight = comboBox.getComponentOrientation().isLeftToRight();

    // Paint the button as usual
    if (comboBox.isEditable()){
      super.paintComponent(g);
    }
    else{
      if (getModel().isPressed()){
        Color selectColor = UIManager.getColor("Button.select");
        g.setColor(selectColor);
      }
      else{
        g.setColor(getBackground());
      }
      Dimension size = getSize();
      g.fillRect(0, 0, size.width, size.height);
    }

    Insets insets = getInsets();

    int width = getWidth() - (insets.left + insets.right);
    int height = getHeight() - (insets.top + insets.bottom);

    if (height <= 0 || width <= 0){
      return;
    }

    int left = insets.left;
    int top = insets.top;
    int bottom = top + (height - 1);

    int iconWidth = 0;

    // Paint the icon
    if (comboIcon != null){
      iconWidth = comboIcon.getIconWidth();
      int iconHeight = comboIcon.getIconHeight();

      int iconTop;
      int iconLeft;
      if (iconOnly){
        iconLeft = (getWidth() / 2) - (iconWidth / 2);
        iconTop = (getHeight() / 2) - (iconHeight / 2);
      }
      else{
        if (leftToRight){
          iconLeft = (left + (width - 1)) - iconWidth;
        }
        else{
          iconLeft = left;
        }
        iconTop = (top + ((bottom - top) / 2)) - (iconHeight / 2);
      }

      comboIcon.paintIcon(this, g, iconLeft, iconTop);

      // Paint the focus
      if (hasFocus()){
        g.setColor(MetalLookAndFeel.getFocusColor());
//            g.drawRect( left - 1, top - 1, width + 3, height + 1 );
        BegTreeHandleUtil.drawDottedRectangle(g, left - 1, top - 1, left + width, top + height);
      }
    }

    // Let the renderer paint
    if (!iconOnly && comboBox != null){
      ListCellRenderer renderer = comboBox.getRenderer();
      Component c;
      boolean renderPressed = getModel().isPressed();
      c = renderer.getListCellRendererComponent(listBox,
        comboBox.getSelectedItem(),
        -1,
        renderPressed,
        false);
      c.setFont(rendererPane.getFont());

      if (model.isArmed() && model.isPressed()){
        if (isOpaque()){
          c.setBackground(UIManager.getColor("Button.select"));
        }
        c.setForeground(comboBox.getForeground());
      }
      else
        if (!comboBox.isEnabled()){
          if (isOpaque()){
            c.setBackground(UIManager.getColor("ComboBox.disabledBackground"));
          }
          c.setForeground(UIManager.getColor("ComboBox.disabledForeground"));
        }
        else{
//          c.setForeground(comboBox.getForeground());
//          c.setBackground(comboBox.getBackground());
        }


      int cWidth = width - (insets.right + iconWidth);
      if (leftToRight){
        rendererPane.paintComponent(g, c, this,
          left, top, cWidth, height);
      }
      else{
        rendererPane.paintComponent(g, c, this,
          left + iconWidth, top, cWidth, height);
      }
    }
  }

  // TODO[vova,anton] replace this code with setFocusable method
  public boolean isFocusTraversable() {
    return !comboBox.isEditable();
  }
}
