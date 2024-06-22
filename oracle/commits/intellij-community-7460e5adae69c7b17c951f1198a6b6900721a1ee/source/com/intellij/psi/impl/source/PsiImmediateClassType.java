package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.EmptySubstitutorImpl;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class PsiImmediateClassType extends PsiClassType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiImmediateClassType");
  private final PsiClass myClass;
  private final PsiSubstitutor mySubstitutor;
  private final PsiManager myManager;
  private String myCanonicalText;
  private String myPresentableText;
  private String myInternalCanonicalText;

  private final PsiClassType.ClassResolveResult myClassResolveResult = new PsiClassType.ClassResolveResult() {
    public PsiClass getElement() {
      return myClass;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    public boolean isValidResult() {
      return true;
    }

    public boolean isAccessible() {
      return true;
    }

    public boolean isStaticsScopeCorrect() {
      return true;
    }

    public PsiElement getCurrentFileResolveScope() {
      return null;
    }

    public boolean hasCandidates() {
      return true;
    }

    public boolean isPackagePrefixPackageReference() {
      return false;
    }
  };

  public PsiImmediateClassType(PsiClass aClass, PsiSubstitutor substitutor) {
    myClass = aClass;
    myManager = aClass.getManager();
    mySubstitutor = substitutor;
    LOG.assertTrue(mySubstitutor != null);
  }

  public PsiClass resolve() {
    return myClass;
  }

  public String getClassName() {
    return myClass.getName();
  }

  private PsiClassType myCachedQualiferType;
  private boolean isQualifierTypeCalculated = false;
  public PsiClassType getQualiferType() {
    if (isQualifierTypeCalculated) {
      if (!(myClass.getParent() instanceof PsiClass)) {
        myCachedQualiferType = null;
      } else {
        final PsiClass parentClass = ((PsiClass)myClass.getParent());
        myCachedQualiferType = new PsiImmediateClassType(parentClass, mySubstitutor);
      }
      isQualifierTypeCalculated = true;
    }
    return myCachedQualiferType;
  }

  public PsiType[] getParameters() {
    List<PsiType> lst = new ArrayList<PsiType>();
    final PsiTypeParameterList list = myClass.getTypeParameterList();
    if(list == null) return PsiType.EMPTY_ARRAY;
    final PsiTypeParameter[] parameters = list.getTypeParameters();
    for(int i = 0; parameters != null && i < parameters.length; i++){
      lst.add(mySubstitutor.substitute(parameters[i]));
    }
    return lst.toArray(PsiType.EMPTY_ARRAY);
  }

  public PsiClassType.ClassResolveResult resolveGenerics() {
    return myClassResolveResult;
  }

  public PsiClassType rawType() {
    return myClass.getManager().getElementFactory().createType(myClass);
  }

  public PsiType createUninvalidateableCopy() {
    return this;
  }

  public String getPresentableText() {
    if (myPresentableText == null) {
      final StringBuffer buffer = new StringBuffer();
      buildText(myClass, buffer, false, false);
      myPresentableText = buffer.toString();
    }
    return myPresentableText;
  }

  public String getCanonicalText() {
    if (myCanonicalText == null) {
      final StringBuffer buffer = new StringBuffer();
      buildText(myClass, buffer, true, false);
      myCanonicalText = buffer.toString();
    }
    return myCanonicalText;
  }

  public String getInternalCanonicalText() {
    if (myInternalCanonicalText == null) {
      final StringBuffer buffer = new StringBuffer();
      buildText(myClass, buffer, true, true);
      myInternalCanonicalText = buffer.toString();
    }
    return myInternalCanonicalText;
  }

  private void buildText(PsiClass aClass, StringBuffer buffer, boolean canonical, boolean internal) {
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      if (aClass == null) return;
    }
    PsiClass parentClass = null;
    if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiElement parent = aClass.getParent();
      if (parent instanceof PsiClass) {
        parentClass = (PsiClass)parent;
      }
    }

    if (parentClass != null) {
      buildText(parentClass, buffer, canonical, false);
      buffer.append('.');
      buffer.append(aClass.getName());
    }
    else {
      final String name;
      if (!canonical) {
        name = aClass.getName();
      }
      else {
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName != null) {
          name = qualifiedName;
        }
        else {
          name = aClass.getName();
        }
      }
      buffer.append(name);
    }

    final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    if (typeParameterList != null) {
      final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
      if (typeParameters.length > 0) {
        StringBuffer pineBuffer = new StringBuffer();
        pineBuffer.append('<');
        for (int i = 0; i < typeParameters.length; i++) {
          PsiTypeParameter typeParameter = typeParameters[i];
          if (i > 0) pineBuffer.append(',');
          final PsiType substitutionResult = mySubstitutor.substitute(typeParameter);
          if (substitutionResult == null) {
            pineBuffer = null;
            break;
          }
          if (!canonical) {
            pineBuffer.append(substitutionResult.getPresentableText());
          }
          else {
            if (internal) {
              pineBuffer.append(substitutionResult.getInternalCanonicalText());
            }
            else {
              pineBuffer.append(substitutionResult.getCanonicalText());
            }
          }
        }
        if (pineBuffer != null) {
          buffer.append(pineBuffer);
          buffer.append('>');
        }
      }
    }
  }

  public boolean isValid() {
    if (!myClass.isValid()) return false;
    if (mySubstitutor instanceof EmptySubstitutorImpl) return true;
    return mySubstitutor.isValid();
  }

  public boolean equalsToText(String text) {
    PsiElementFactory factory = myManager.getElementFactory();
    final PsiType patternType;
    try {
      patternType = factory.createTypeFromText(text, null);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return equals(patternType);

  }

  public GlobalSearchScope getResolveScope() {
    return myClass.getResolveScope();
  }
}
