package com.intellij.openapi.roots.ui.componentsList.layout;


import java.awt.*;

public class VerticalStackLayout implements LayoutManager2 {
  private int myDefaultHeight = 200;

  /**
   * Calculates the minimum size dimensions for the specified
   * container, given the components it contains.
   * @param parent the component to be laid out
   * @see #preferredLayoutSize
   */
  public Dimension minimumLayoutSize(Container parent) {
    ComponentOperation.SizeCalculator calculator = new ComponentOperation.SizeCalculator(SizeProperty.MINIMUM_SIZE);
    withAllVisibleDo(parent, calculator);
    OrientedDimensionSum result = calculator.getSum();
    result.addInsets(parent.getInsets());
    return result.getSum();
  }

  public static void withAllVisibleDo(Container container, ComponentOperation operation) {
    Component[] components = container.getComponents();
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      if (!component.isVisible()) continue;
      operation.applyTo(component);
    }
  }

  /**
   * Lays out the specified container.
   * @param parent the container to be laid out
   */
  public void layoutContainer(final Container parent) {
    withAllVisibleDo(parent,
                     new ComponentOperation.InlineLayout(parent, myDefaultHeight, SizeProperty.PREFERED_SIZE,
                                                         Orientation.VERTICAL));
  }

  /**
   * Calculates the preferred size dimensions for the specified
   * container, given the components it contains.
   * @param parent the container to be laid out
   *
   * @see #minimumLayoutSize
   */
  public Dimension preferredLayoutSize(Container parent) {
    ComponentOperation.SizeCalculator calculator =
        new ComponentOperation.SizeCalculator(myDefaultHeight, SizeProperty.PREFERED_SIZE, Orientation.VERTICAL);
    withAllVisibleDo(parent, calculator);
    OrientedDimensionSum result = calculator.getSum();
    result.addInsets(parent.getInsets());
    return result.getSum();
  }

  public void removeLayoutComponent(Component comp) {}
  public Dimension maximumLayoutSize(Container target) { return null; }
  public float getLayoutAlignmentY(Container target) { return 0; }
  public void addLayoutComponent(Component comp, Object constraints) {}
  public void invalidateLayout(Container target) {}
  public void addLayoutComponent(String name, Component comp) {}
  public float getLayoutAlignmentX(Container target) { return 0; }
}
