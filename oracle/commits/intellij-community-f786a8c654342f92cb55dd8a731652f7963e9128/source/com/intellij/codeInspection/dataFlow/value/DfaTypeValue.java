/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:32:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.containers.HashMap;
import com.intellij.openapi.util.Comparing;

import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

public class DfaTypeValue extends DfaValue {
  public static class Factory {
    private final DfaTypeValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaTypeValue>> myStringToObject;
    private DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaTypeValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaTypeValue>>();
    }

    public DfaTypeValue create(PsiType type, boolean nullable) {
      mySharedInstance.myType = type;
      mySharedInstance.myCanonicalText = type.getCanonicalText();
      mySharedInstance.myIsNullable = nullable;
      if (mySharedInstance.myCanonicalText == null) {
        mySharedInstance.myCanonicalText = PsiKeyword.NULL;
      }

      String id = mySharedInstance.toString();
      ArrayList<DfaTypeValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaTypeValue>();
        myStringToObject.put(id, conditions);
      } else {
        for (int i = 0; i < conditions.size(); i++) {
          DfaTypeValue aType = conditions.get(i);
          if (aType.hardEquals(mySharedInstance)) return aType;
        }
      }

      DfaTypeValue result = new DfaTypeValue(type, nullable, myFactory);
      conditions.add(result);
      return result;
    }

    public DfaTypeValue create(PsiType type) {
      return create(type, false);
    }
  }

  private PsiType myType;
  private String myCanonicalText;
  private boolean myIsNullable;

  private DfaTypeValue(DfaValueFactory factory) {
    super(factory);
  }

  private DfaTypeValue(PsiType type, boolean isNullable, DfaValueFactory factory) {
    super(factory);
    myType = type;
    myIsNullable = isNullable;
    myCanonicalText = type.getCanonicalText();
    if (myCanonicalText == null) {
      myCanonicalText = PsiKeyword.NULL;
    }
  }

  public PsiType getType() {
    return myType;
  }

  public boolean isNullable() {
    return myIsNullable;
  }

  @NonNls
  public String toString() {
    return myCanonicalText + ", nullable=" + myIsNullable;
  }

  private boolean hardEquals(DfaTypeValue aType) {
    return Comparing.equal(myCanonicalText, aType.myCanonicalText) && myIsNullable == aType.myIsNullable;
  }

  public boolean isAssignableFrom(DfaTypeValue dfaType) {
    if (dfaType == null) return false;
    return myType.isAssignableFrom(dfaType.myType);
  }

  public boolean isConvertibleFrom(DfaTypeValue dfaType) {
    if (dfaType == null) return false;
    return myType.isConvertibleFrom(dfaType.myType);
  }
}
