package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.IdeBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public class SuperTypeGroup implements Group, ItemPresentation, AccessLevelProvider{
  private final SmartPsiElementPointer mySuperClassPointer;
  private final boolean myOverrides;
  private final Collection<TreeElement> myChildren = new ArrayList<TreeElement>();
  private static final Icon OVERRIDING_ICON = IconLoader.getIcon("/general/overridingMethod.png");
  private static final Icon IMLLEMENTING_ICON = IconLoader.getIcon("/general/implementingMethod.png");

  public SuperTypeGroup(PsiClass superClass, boolean overrides) {
    myOverrides = overrides;
    mySuperClassPointer = SmartPointerManager.getInstance(superClass.getProject()).createSmartPsiElementPointer(superClass);
  }

  public Collection<TreeElement> getChildren() {
    return myChildren;
  }

  @Nullable
  private PsiClass getSuperClass() {
    return (PsiClass)mySuperClassPointer.getElement();
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public Icon getIcon(boolean open) {
    if (myOverrides) {
      return OVERRIDING_ICON;
    } else {
      return IMLLEMENTING_ICON;
    }
  }

  public String getLocationString() {
    return null;
  }

  public String getPresentableText() {
    return toString();
  }

  public String toString() {
    final PsiClass superClass = getSuperClass();
    return superClass != null ? superClass.getName() : IdeBundle.message("node.structureview.invalid");
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuperTypeGroup)) return false;

    final SuperTypeGroup superTypeGroup = (SuperTypeGroup)o;

    if (myOverrides != superTypeGroup.myOverrides) return false;
    final PsiClass superClass = getSuperClass();
    if (superClass != null ? !superClass .equals(superTypeGroup.getSuperClass() ) : superTypeGroup.getSuperClass()  != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    final PsiClass superClass = getSuperClass();
    result = (superClass  != null ? superClass .hashCode() : 0);
    result = 29 * result + (myOverrides ? 1 : 0);
    return result;
  }

  public Object getValue() {
    return this;
  }

  public int getAccessLevel() {
    final PsiClass superClass = getSuperClass();
    return superClass != null ? PsiUtil.getAccessLevel(superClass.getModifierList()) : PsiUtil.ACCESS_LEVEL_PUBLIC;
  }

  public int getSubLevel() {
    return 1;
  }

  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  public void addMethod(final TreeElement superMethod) {
     myChildren.add(superMethod);
  }
}
