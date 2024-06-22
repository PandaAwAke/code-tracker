package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class FormPropertyTableCellRenderer extends ColoredTableCellRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.FormPropertyTableCellRenderer");

  private final Palette myPalette;
  private final SimpleTextAttributes myAttrs1;
  private final SimpleTextAttributes myAttrs2;
  private final SimpleTextAttributes myAttrs3;

  FormPropertyTableCellRenderer(final Project project) {
    LOG.assertTrue(project != null);

    myPalette = Palette.getInstance(project);
    myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    myAttrs3 = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY);

    setFocusBorderAroundIcon(true);
  }

  protected void customizeCellRenderer(
    final JTable table,
    final Object value,
    final boolean selected,
    final boolean hasFocus,
    final int row,
    final int column
  ) {
    final FormProperty property = (FormProperty)value;
    LOG.assertTrue(property != null);

    final LwComponent component = property.getLwComponent();

    // Icon
    final Icon icon;
    final String fqClassName = component.getComponentClassName();
    final ComponentItem item = myPalette.getItem(fqClassName);
    if (item != null) {
      icon = item.getSmallIcon();
    }
    else {
      icon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/unknown-small.png");
    }
    setIcon(icon);

    // Binding
    append(component.getBinding(), myAttrs1);

    // Component class name and package
    final String shortClassName;
    final String packageName;
    final int lastDotIndex = fqClassName.lastIndexOf('.');
    if (lastDotIndex != -1) {
      shortClassName = fqClassName.substring(lastDotIndex + 1);
      packageName = fqClassName.substring(0, lastDotIndex);
    }
    else{ // default package
      shortClassName = fqClassName;
      packageName = null;
    }

    LOG.assertTrue(shortClassName.length() > 0);

    append(" ", myAttrs2); /*small gap between icon and class name*/
    append(shortClassName, myAttrs2);

    if(packageName != null){
      append(" (", myAttrs3);
      append(packageName, myAttrs3);
      append(")", myAttrs3);
    }
  }
}
