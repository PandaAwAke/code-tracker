package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.openapi.util.Pair;

import java.util.Iterator;

import org.jetbrains.annotations.Nullable;

public class PsiResolveHelperImpl implements PsiResolveHelper, Constants {
  private final PsiManager myManager;

  public PsiResolveHelperImpl(PsiManager manager) {
    myManager = manager;
  }

  public JavaResolveResult resolveConstructor(PsiClassType classType, PsiExpressionList argumentList, PsiElement place) {
    JavaResolveResult[] result = multiResolveConstructor(classType, argumentList, place);
    if (result.length != 1) return JavaResolveResult.EMPTY;
    return result[0];
  }

  public JavaResolveResult[] multiResolveConstructor(PsiClassType type, PsiExpressionList argumentList, PsiElement place) {
    final MethodResolverProcessor processor;
    PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
    final PsiClass aClass = classResolveResult.getElement();
    final JavaResolveResult[] result;
    if (aClass == null) {
      result = JavaResolveResult.EMPTY_ARRAY;
    }
    else {
      if (argumentList.getParent() instanceof PsiAnonymousClass) {
        processor = new MethodResolverProcessor((PsiClass)argumentList.getParent(), argumentList, place);
      }
      else {
        processor = new MethodResolverProcessor(aClass, argumentList, place);
      }
      PsiScopesUtil.processScope(aClass, processor, classResolveResult.getSubstitutor(), aClass, place);

      // getting the most suitable myResult
      result = processor.getResult();
    }
    return result;
  }

  public PsiClass resolveReferencedClass(String referenceText, PsiElement context) {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement ref = Parsing.parseJavaCodeReferenceText(myManager, referenceText.toCharArray(), holderElement.getCharTable());
    if (ref == null) return null;
    TreeUtil.addChildren(holderElement, ref);

    return ResolveClassUtil.resolveClass((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref));
  }

  public PsiVariable resolveReferencedVariable(String referenceText, PsiElement context) {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement ref = Parsing.parseJavaCodeReferenceText(myManager, referenceText.toCharArray(), holderElement.getCharTable());
    if (ref == null) return null;
    TreeUtil.addChildren(holderElement, ref);
    PsiJavaCodeReferenceElement psiRef = (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
    return ResolveVariableUtil.resolveVariable(psiRef, null, null);
  }

  public boolean isAccessible(PsiMember member, PsiElement place, PsiClass accessObjectClass) {
    return isAccessible(member, member.getModifierList(), place, accessObjectClass, null);
  }


  public boolean isAccessible(PsiMember member,
                              PsiModifierList modifierList,
                              PsiElement place,
                              PsiClass accessObjectClass,
                              final PsiElement currentFileResolveScope) {
    return ResolveUtil.isAccessible(member, member.getContainingClass(), modifierList, place, accessObjectClass, currentFileResolveScope);
  }

  public CandidateInfo[] getReferencedMethodCandidates(PsiCallExpression expr, boolean dummyImplicitConstructor) {
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(expr);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, expr, dummyImplicitConstructor);
    }
    catch (MethodProcessorSetupFailedException e) {
      return CandidateInfo.EMPTY_ARRAY;
    }
    return processor.getCandidates();
  }

  public PsiType inferTypeForMethodTypeParameter(final PsiTypeParameter typeParameter,
                                                 final PsiParameter[] parameters,
                                                 PsiExpression[] arguments,
                                                 PsiSubstitutor partialSubstitutor,
                                                 PsiElement parent,
                                                 final boolean forCompletion) {
    PsiType lowerBound = PsiType.NULL;
    PsiType upperBound = PsiType.NULL;
    if (parameters.length > 0) {
      for (int j = 0; j < arguments.length; j++) {
        PsiExpression argument = arguments[j];
        final PsiParameter parameter = parameters[Math.min(j, parameters.length - 1)];
        if (j >= parameters.length && !parameter.isVarArgs()) break;
        PsiType parameterType = parameter.getType();
        PsiType argumentType = argument.getType();
        if (argumentType == null) continue;

        if (parameterType instanceof PsiEllipsisType) {
          parameterType = ((PsiEllipsisType)parameterType).getComponentType();
          if (arguments.length == parameters.length && argumentType instanceof PsiArrayType && !(((PsiArrayType)argumentType).getComponentType() instanceof PsiPrimitiveType)) {
            argumentType = ((PsiArrayType)argumentType).getComponentType();
          }
        }
        final Pair<PsiType,ConstraintType> currentSubstitution = getSubstitutionForTypeParameterConstraint(typeParameter, parameterType,
                                                                            argumentType, true);
        if (currentSubstitution == null) continue;
        final ConstraintType constraintType = currentSubstitution.getSecond();
        final PsiType type = currentSubstitution.getFirst();
        if (type == null) return null;
        switch(constraintType) {
          case EQUALS:
            return type;
          case SUPERTYPE:
            if (lowerBound == PsiType.NULL) {
              lowerBound = type;
            } else {
              if (!lowerBound.equals(type)) {
                lowerBound = GenericsUtil.getLeastUpperBound(lowerBound, type, typeParameter.getManager());
                if (lowerBound == null) return PsiType.NULL;
              }
            }
            break;
          case SUBTYPE:
            if (upperBound == PsiType.NULL) {
              upperBound = type;
            }
        }
      }
    }

    if (lowerBound == PsiType.NULL) lowerBound = upperBound;

    if (lowerBound == PsiType.NULL) {
      lowerBound = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, forCompletion);
    }
    return lowerBound;
  }

  private static Pair<PsiType, ConstraintType> processArgType(PsiType arg, boolean captureWildcard, final ConstraintType constraintType) {
    if (arg instanceof PsiWildcardType && captureWildcard) {
      return new Pair<PsiType, ConstraintType>(arg, constraintType);
    } else {
      if (arg == null || arg.getDeepComponentType() instanceof PsiPrimitiveType ||
          PsiUtil.resolveClassInType(arg) != null || arg instanceof PsiTypeVariable) {
        return new Pair<PsiType, ConstraintType>(arg, constraintType);
      }
    }

    return null;
  }

  private PsiType inferMethodTypeParameterFromParent(final PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor,
                                                     PsiElement parent,
                                                     final boolean forCompletion) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    PsiType substitution = PsiType.NULL;
    if (owner instanceof PsiMethod) {
      if (parent instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
        substitution = inferMethodTypeParameterFromParent(methodCall.getParent(), methodCall, typeParameter, substitutor,
                                                          forCompletion);
      }
    }
    return substitution;
  }

  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                 PsiType param,
                                                 PsiType arg,
                                                 boolean isContraVariantPosition) {
    final Pair<PsiType, ConstraintType> constraint = getSubstitutionForTypeParameterConstraint(typeParam, param, arg, isContraVariantPosition);
    return constraint == null ? PsiType.NULL : constraint.getFirst();
  }

  public Pair<PsiType, ConstraintType> getSubstitutionForTypeParameterConstraint(PsiTypeParameter typeParam,
                                                           PsiType param,
                                                           PsiType arg,
                                                           boolean isContraVariantPosition) {
    //Ellipsis types are analyzed somewhere else
    LOG.assertTrue(!(param instanceof PsiEllipsisType));

    if (param instanceof PsiArrayType && arg instanceof PsiArrayType) {
      return getSubstitutionForTypeParameterConstraint(typeParam, ((PsiArrayType)param).getComponentType(), ((PsiArrayType)arg).getComponentType(),
                                             isContraVariantPosition);
    }

    if (param instanceof PsiClassType) {
      PsiManager manager = typeParam.getManager();
      if (arg instanceof PsiPrimitiveType) {
        arg = ((PsiPrimitiveType)arg).getBoxedType(manager, typeParam.getResolveScope());
        if (arg == null) return null;
      }

      JavaResolveResult paramResult = ((PsiClassType)param).resolveGenerics();
      PsiClass paramClass = (PsiClass)paramResult.getElement();
      if (typeParam == paramClass) {
        return arg == null || arg.getDeepComponentType() instanceof PsiPrimitiveType ||
               PsiUtil.resolveClassInType(arg) != null ? new Pair<PsiType, ConstraintType> (arg, ConstraintType.SUPERTYPE) : null;
      }
      if (paramClass == null) return null;

      if (arg instanceof PsiClassType) {
        JavaResolveResult argResult = ((PsiClassType)arg).resolveGenerics();
        PsiClass argClass = (PsiClass)argResult.getElement();
        if (argClass == null) return null;

        PsiElementFactory factory = manager.getElementFactory();
        PsiType patternType = factory.createType(typeParam);
        if (isContraVariantPosition) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(paramClass, argClass, argResult.getSubstitutor());
          if (substitutor == null) return null;
          arg = factory.createType(paramClass, substitutor);
        }
        else {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(argClass, paramClass, paramResult.getSubstitutor());
          if (substitutor == null) return null;
          param = factory.createType(argClass, substitutor);
        }

        Pair<PsiType, ConstraintType> substitution = getSubstitutionForTypeParameterInner(param, arg, patternType, false,
                                                                                          ConstraintType.SUPERTYPE);
        return substitution != null
               ? substitution
               : getSubstitutionForTypeParameterInner(param, arg, patternType, true, ConstraintType.SUPERTYPE);
      }
    }

    return null;
  }

  private enum ConstraintType {
    EQUALS,
    SUBTYPE,
    SUPERTYPE;
  }

  @Nullable
  private Pair<PsiType, ConstraintType> getSubstitutionForTypeParameterInner(PsiType param,
                                                                             PsiType arg,
                                                                             PsiType patternType,
                                                                             boolean captureWildcard,
                                                                             final ConstraintType constraintType) {
    if (arg instanceof PsiCapturedWildcardType) arg = ((PsiCapturedWildcardType)arg).getWildcard(); //reopen

    if (patternType.equals(param)) {
      return processArgType(arg, captureWildcard, constraintType);
    }

    if (param instanceof PsiWildcardType) {
      final PsiWildcardType wildcardParam = (PsiWildcardType)param;
      final PsiType paramBound = wildcardParam.getBound();
      if (paramBound == null) return null;
      ConstraintType constrType = wildcardParam.isExtends() ? ConstraintType.SUPERTYPE : ConstraintType.SUBTYPE;
      if (arg instanceof PsiWildcardType) {
        if (((PsiWildcardType)arg).isExtends() == wildcardParam.isExtends()) {
          Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(paramBound, ((PsiWildcardType)arg).getBound(),
                                                                                   patternType, captureWildcard, constrType);
          if (res != null) return res;
        }
      }
      else if (patternType.equals(paramBound)) {
        Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(paramBound, arg,
                                                                                   patternType, captureWildcard, constrType);
        if (res != null) return res;
      }
      else if (paramBound instanceof PsiClassType && arg instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult boundResult = ((PsiClassType)paramBound).resolveGenerics();
        final PsiClass boundClass = boundResult.getElement();
        if (boundClass != null) {
          final PsiClassType.ClassResolveResult argResult = ((PsiClassType)arg).resolveGenerics();
          final PsiClass argClass = argResult.getElement();
          if (argClass != null) {
            if (wildcardParam.isExtends()) {
              PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(boundClass, argClass, argResult.getSubstitutor());
              if (superSubstitutor != null) {
                final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(boundClass);
                while (iterator.hasNext()) {
                  final PsiTypeParameter typeParameter = iterator.next();
                  final PsiType substituted = superSubstitutor.substitute(typeParameter);
                  if (substituted != null) {
                    final Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(boundResult.getSubstitutor().substitute(typeParameter),
                                                                                                   substituted,
                                                                                                   patternType, captureWildcard, ConstraintType.EQUALS);
                    if (res != null) return res;
                  }
                }
              }
            }
            else {
              PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(argClass, boundClass, boundResult.getSubstitutor());
              if (superSubstitutor != null) {
                final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(argClass);
                while (iterator.hasNext()) {
                  final PsiTypeParameter typeParameter = iterator.next();
                  final PsiType substituted = argResult.getSubstitutor().substitute(typeParameter);
                  if (substituted != null) {
                    final Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(superSubstitutor.substitute(typeParameter),
                                                                                                   substituted,
                                                                                                   patternType, captureWildcard, ConstraintType.EQUALS);
                    if (res != null) return res;
                  }
                }
              }
            }
          }
        }
      }
    }

    if (param instanceof PsiArrayType && arg instanceof PsiArrayType) {
      return getSubstitutionForTypeParameterInner(((PsiArrayType)param).getComponentType(), ((PsiArrayType)arg).getComponentType(),
                                                  patternType, captureWildcard, constraintType);
    }

    if (param instanceof PsiClassType && arg instanceof PsiClassType) {
      PsiClass paramClass = ((PsiClassType)param).resolve();
      if (paramClass == null) return null;

      PsiClass argClass = ((PsiClassType)arg).resolve();
      if (argClass != paramClass) return null;

      PsiType[] paramTypes = ((PsiClassType)param).getParameters();
      PsiType[] argTypes = ((PsiClassType)arg).getParameters();
      if (paramTypes.length != argTypes.length) return null;
      Pair<PsiType,ConstraintType> capturedWildcard = null;
      for (int i = 0; i < argTypes.length; i++) {
        Pair<PsiType,ConstraintType> res = getSubstitutionForTypeParameterInner(paramTypes[i], argTypes[i], patternType, captureWildcard, ConstraintType.EQUALS);
        if (res != null) {
          if (!captureWildcard) {
            return res;
          }
          else {
            if (capturedWildcard != null) {
              return null;
            }
            else {
              capturedWildcard = res;
            }
          }
        }
      }

      return capturedWildcard;
    }

    return null;
  }

  private PsiType inferMethodTypeParameterFromParent(PsiElement parent,
                                                     PsiMethodCallExpression methodCall,
                                                     final PsiTypeParameter typeParameter,
                                                     PsiSubstitutor substitutor,
                                                     final boolean forCompletion) {
    PsiType type = null;

    if (parent instanceof PsiVariable && methodCall.equals(((PsiVariable)parent).getInitializer())) {
      type = ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression && methodCall.equals(((PsiAssignmentExpression)parent).getRExpression())) {
      type = ((PsiAssignmentExpression)parent).getLExpression().getType();
    }
    else if (parent instanceof PsiTypeCastExpression && methodCall.equals(((PsiTypeCastExpression)parent).getOperand())) {
      type = ((PsiTypeCastExpression)parent).getType();
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        type = method.getReturnType();
      }
    }

    final PsiManager manager = methodCall.getManager();
    if (type == null) {
      type = forCompletion ?
             PsiType.NULL :
             PsiType.getJavaLangObject(methodCall.getManager(), methodCall.getResolveScope());
    }

    PsiType returnType = ((PsiMethod)typeParameter.getOwner()).getReturnType();
    PsiType guess = getSubstitutionForTypeParameter(typeParameter, returnType, type, false);

    if (guess == PsiType.NULL) {
      PsiType superType = substitutor.substitute(typeParameter.getSuperTypes()[0]);
      if (superType == null) superType = PsiType.getJavaLangObject(manager, methodCall.getResolveScope());
      if (forCompletion) {
        return PsiWildcardType.createExtends(manager, superType);
      }
      else {
        return superType;
      }
    }

    //The following code is the result of deep thought, do not shit it out before discussing with [ven]
    if (returnType instanceof PsiClassType && typeParameter.equals(((PsiClassType)returnType).resolve())) {
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      PsiSubstitutor newSubstitutor = substitutor.put(typeParameter, guess);
      for (PsiClassType extendsType1 : extendsTypes) {
        PsiType extendsType = newSubstitutor.substitute(extendsType1);
        if (!extendsType.isAssignableFrom(guess)) {
          if (guess.isAssignableFrom(extendsType)) {
            guess = extendsType;
            newSubstitutor = substitutor.put(typeParameter, guess);
          }
          else {
            break;
          }
        }
      }
    }

    return guess;
  }
}
