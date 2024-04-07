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
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public abstract class AbstractTreeNode<Value> extends NodeDescriptor implements NavigationItem {
  private AbstractTreeNode myParent;
  private Value myValue;
  private NodeDescriptor myParentDescriptor;
  protected String myLocationString;
  private TextAttributesKey myAttributesKey;

  protected AbstractTreeNode(Project project, Value value) {
    super(project, null);
    setValue(value);
  }

  @NotNull
  public abstract Collection<AbstractTreeNode> getChildren();

  public final boolean update() {
    PresentationData presentation = getUpdatedData();

    Icon openIcon = presentation.getIcon(true);
    Icon closedIcon = presentation.getIcon(false);
    String name = presentation.getPresentableText();
    String locationString = presentation.getLocationString();
    Color color = getFileStatus().getColor();
    TextAttributesKey attributesKey = presentation.getTextAttributesKey();
    if (valueIsCut()) {
      color = CopyPasteManager.CUT_COLOR;
    }

     boolean updated = !Comparing.equal(new Object[]{myOpenIcon, myClosedIcon, myName, myLocationString, myColor, myAttributesKey},
                                        new Object[]{openIcon, closedIcon, name, locationString, color, attributesKey});

    myOpenIcon = openIcon;
    myClosedIcon = closedIcon;
    myName = name;
    myLocationString = locationString;
    myColor = color;
    myAttributesKey = attributesKey;

    return updated;
  }

  protected boolean valueIsCut() {
    return CopyPasteManager.getInstance().isCutElement(getValue());
  }

  private PresentationData getUpdatedData() {
    PresentationData presentation = new PresentationData();
    if (shouldUpdateData()) {
      update(presentation);
    }
    return presentation;
  }

  protected boolean shouldUpdateData() {
    return !myProject.isDisposed() && getValue() != null;
  }

  protected abstract void update(PresentationData presentation);

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAlwaysExpand() {
    return false;
  }

  @Nullable
  public final Object getElement() {
    return getValue() != null ? this : null;
  }

  public final boolean equals(Object object) {
    if (!(object instanceof AbstractTreeNode)) return false;
    return Comparing.equal(getValue(), ((AbstractTreeNode)object).getValue());
  }

  public final int hashCode() {
    return getValue() == null ? 0 : getValue().hashCode();
  }

  public final AbstractTreeNode getParent() {
    return myParent;
  }

  public final void setParent(AbstractTreeNode parent) {
    myParent = parent;
    myParentDescriptor = parent;
  }

  public final AbstractTreeNode setParentDescriptor(NodeDescriptor parentDescriptor) {
    myParentDescriptor = parentDescriptor;
    return this;
  }

  public final NodeDescriptor getParentDescriptor() {
    return myParentDescriptor;
  }

  public final Value getValue() {
    return myValue;
  }

  public final Project getProject() {
    return myProject;
  }

  public final void setValue(Value value) {
    myValue = value;
  }

  public String getTestPresentation() {
    if (myName != null) {
      return myName;
    } else if (getValue() != null){
      return getValue().toString();
    } else {
      return null;
    }
  }

  public ItemPresentation getPresentation() {
    return new PresentationData(myName, myLocationString, myOpenIcon, myClosedIcon,myAttributesKey);
  }

  public FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public String getName() {
    return myName;
  }

  public void navigate(boolean requestFocus) {
  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }

  protected final Object getParentValue() {
    return getParent() == null ? null : getParent().getValue();
  }

  protected String getToolTip() {
    return null;
  }

  public boolean canRepresent(final Object element) {
    return Comparing.equal(getValue(), element);
  }
}
