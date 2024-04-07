/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiMethod;

import java.util.Comparator;

import org.jetbrains.annotations.NonNls;

public class KindSorter implements Sorter {
  public static final Sorter INSTANCE = new KindSorter();

  @NonNls public static final String ID = "KIND";
  private static final Comparator COMPARATOR = new Comparator() {
    public int compare(final Object o1, final Object o2) {

      int weight1 = getWeight(o1);
      int weight2 = getWeight(o2);

      return weight1 - weight2;
    }

    private int getWeight(final Object value) {
      if (value instanceof JavaClassTreeElement) {
        return 10;
      }
      else if (value instanceof SuperTypeGroup) {
        return 20;
      }
      else if (value instanceof PsiMethodTreeElement) {
        final PsiMethodTreeElement methodTreeElement = ((PsiMethodTreeElement)value);
        final PsiMethod method = methodTreeElement.getMethod();

        return method.isConstructor() ? 30 : 35;
      }
      else if (value instanceof PropertyGroup) {
        return 40;
      }
      else if (value instanceof PsiFieldTreeElement) {
        return 50;
      }
      else {
        return 60;
      }
    }
  };

  public Comparator getComparator() {
    return COMPARATOR;
  }

  public ActionPresentation getPresentation() {
    return null;
  }

  public String getName() {
    return ID;
  }
}
