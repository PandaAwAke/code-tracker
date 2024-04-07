package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class StringDescriptorInspection extends BaseFormInspection {
  private static BorderProperty myBorderProperty = new BorderProperty(null);

  public StringDescriptorInspection(@NonNls String inspectionKey) {
    super(inspectionKey);
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    for(IProperty prop: component.getModifiedProperties()) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor) propValue;
        checkStringDescriptor(module, component, prop, descriptor, collector);
      }
    }

    if (component instanceof IContainer) {
      IContainer container = (IContainer) component;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        checkStringDescriptor(module, component, myBorderProperty, descriptor, collector);
      }
    }

    if (component.getParentContainer() instanceof ITabbedPane) {
      ITabbedPane parentTabbedPane = (ITabbedPane) component.getParentContainer();
      StringDescriptor descriptor = parentTabbedPane.getTabProperty(component, ITabbedPane.TAB_TITLE_PROPERTY);
      if (descriptor != null) {
        checkStringDescriptor(module, component, MockTabTitleProperty.INSTANCE, descriptor, collector);
      }
      descriptor = parentTabbedPane.getTabProperty(component, ITabbedPane.TAB_TOOLTIP_PROPERTY);
      if (descriptor != null) {
        checkStringDescriptor(module, component, MockTabToolTipProperty.INSTANCE, descriptor, collector);
      }
    }
  }

  protected abstract void checkStringDescriptor(final Module module,
                                                final IComponent component,
                                                final IProperty prop,
                                                final StringDescriptor descriptor,
                                                final FormErrorCollector collector);

  private static class MockTabTitleProperty implements IProperty {
    public static MockTabTitleProperty INSTANCE = new MockTabTitleProperty();

    public String getName() {
      return ITabbedPane.TAB_TITLE_PROPERTY;
    }

    public Object getPropertyValue(final IComponent component) {
      return null;
    }
  }

  private static class MockTabToolTipProperty implements IProperty {
    public static MockTabToolTipProperty INSTANCE = new MockTabToolTipProperty();

    public String getName() {
      return ITabbedPane.TAB_TOOLTIP_PROPERTY;
    }

    public Object getPropertyValue(final IComponent component) {
      return null;
    }
  }
}
