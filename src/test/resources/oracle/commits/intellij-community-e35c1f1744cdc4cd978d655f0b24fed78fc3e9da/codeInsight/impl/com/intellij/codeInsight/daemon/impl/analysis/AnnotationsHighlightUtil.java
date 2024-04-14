package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ven
 */
public class AnnotationsHighlightUtil {
  private static final String DUPLICATE_ANNOTATION = JavaErrorMessages.message("annotation.duplicate.annotation");
  private static final String NONCONSTANT_EXPRESSION = JavaErrorMessages.message("annotation.nonconstant.attribute.value");
  private static final String INVALID_ANNOTATION_MEMBER_TYPE = JavaErrorMessages.message("annotation.invalid.annotation.member.type");
  private static final String CYCLIC_ANNOTATION_MEMER_TYPE = JavaErrorMessages.message("annotation.cyclic.element.type");

  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil");
  private static final @NonNls String ANNOTATION_TARGET_MESSAGE_KEY_PREFIX = "annotation.target.";
  @NonNls private static final String PACKAGE_INFO_JAVA = "package-info.java";

  public static HighlightInfo checkNameValuePair(PsiNameValuePair pair) {
    PsiReference ref = pair.getReference();
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      if (pair.getName() != null) {
        String description = JavaErrorMessages.message("annotation.unknown.method", ref.getCanonicalText());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getElement(), description);
      }
      else {
        String description = JavaErrorMessages.message("annotation.missing.method", ref.getCanonicalText());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref.getElement(), description);
      }
    }
    else {
      PsiType returnType = method.getReturnType();
      PsiAnnotationMemberValue value = pair.getValue();
      HighlightInfo info = checkMemberValueType(value, returnType);
      if (info != null) return info;

      return checkDuplicateAttribute(pair);
    }
  }

  @Nullable
  private static HighlightInfo checkDuplicateAttribute(PsiNameValuePair pair) {
    PsiAnnotationParameterList annotation = (PsiAnnotationParameterList)pair.getParent();
    PsiNameValuePair[] attributes = annotation.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      if (attribute == pair) break;
      if (Comparing.equal(attribute.getName(), pair.getName())) {
        String description = JavaErrorMessages.message("annotation.duplicate.attribute",
                                                       pair.getName() == null
                                                       ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
                                                       : pair.getName());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, pair, description);
      }
    }

    return null;
  }

  private static String formatReference(PsiJavaCodeReferenceElement ref) {
    return ref.getCanonicalText();
  }

  @Nullable
  public static HighlightInfo checkMemberValueType(PsiAnnotationMemberValue value, PsiType expectedType) {
    if (value instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement nameRef = ((PsiAnnotation)value).getNameReferenceElement();
      if (nameRef == null) return null;
      if (expectedType instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)expectedType).resolve();
        if (nameRef.isReferenceTo(aClass)) return null;
      }

      if (expectedType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)expectedType).getComponentType();
        if (componentType instanceof PsiClassType) {
          PsiClass aClass = ((PsiClassType)componentType).resolve();
          if (nameRef.isReferenceTo(aClass)) return null;
        }
      }

      String description = JavaErrorMessages.message("annotation.incompatible.types",
                                                     formatReference(nameRef),
                                                     HighlightUtil.formatType(expectedType));

      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      if (expectedType instanceof PsiArrayType) return null;
      String description = JavaErrorMessages.message("annotation.illegal.array.initializer", HighlightUtil.formatType(expectedType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }
    else if (value instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)value;
      PsiType type = expr.getType();
      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          (expectedType instanceof PsiArrayType && TypeConversionUtil.areTypesAssignmentCompatible(((PsiArrayType)expectedType).getComponentType(),
                                                                                                   expr))) {
        return null;
      }

      String description = JavaErrorMessages.message("annotation.incompatible.types",
                                                     HighlightUtil.formatType(type), HighlightUtil.formatType(expectedType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }

    LOG.error("Unknown annotation member value");
    return null;
  }

  @NotNull public static Collection<HighlightInfo> checkDuplicatedAnnotations(PsiModifierList list) {
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    Set<PsiClass> refInterfaces = new HashSet<PsiClass>();
    PsiAnnotation[] annotations = list.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) return Collections.EMPTY_LIST;
      PsiClass aClass = (PsiClass)nameRef.resolve();
      if (aClass != null) {
        if (refInterfaces.contains(aClass)) {
          result.add(HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, annotation.getNameReferenceElement(),
                                                       DUPLICATE_ANNOTATION));
        }

        refInterfaces.add(aClass);
      }
    }

    return result;
  }

  @Nullable
  public static HighlightInfo checkMissingAttributes(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiClass aClass = (PsiClass)nameRef.resolve();
    if (aClass != null && aClass.isAnnotationType()) {
      Set<String> names = new HashSet<String>();
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        if (attribute.getName() != null) {
          names.add(attribute.getName());
        }
        else {
          names.add(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        }
      }

      PsiMethod[] annotationMethods = aClass.getMethods();
      List<String> missed = new ArrayList<String>();
      for (PsiMethod method : annotationMethods) {
        if (method instanceof PsiAnnotationMethod) {
          PsiAnnotationMethod annotationMethod = (PsiAnnotationMethod)method;
          if (annotationMethod.getDefaultValue() == null) {
            if (!names.contains(annotationMethod.getName())) {
              missed.add(annotationMethod.getName());
            }
          }
        }
      }

      if (missed.size() > 0) {
        StringBuffer buff = new StringBuffer("'" + missed.get(0) + "'");
        for (int i = 1; i < missed.size(); i++) {
          buff.append(", ");
          buff.append("'").append(missed.get(i)).append("'");
        }

        String description = JavaErrorMessages.message("annotation.missing.attribute", buff);
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, annotation.getNameReferenceElement(), description);
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkConstantExpression(PsiExpression expression) {
    if (expression.getParent() instanceof PsiAnnotationMethod || expression.getParent() instanceof PsiNameValuePair) {
      if (expression.getType() == PsiType.NULL || !PsiUtil.isConstantExpression(expression)) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, NONCONSTANT_EXPRESSION);
      }
    }

    return null;
  }

  public static HighlightInfo checkValidAnnotationType(final PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (!type.accept(new PsiTypeVisitor<Boolean>() {
      public Boolean visitType(PsiType type) {
        return Boolean.FALSE;
      }

      public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return Boolean.TRUE;
      }

      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(PsiClassType classType) {
        if (classType.getParameters().length > 0) {
          PsiType javaLangClass = PsiType.getJavaLangClass(typeElement.getManager(), typeElement.getResolveScope());
          if (javaLangClass != null && javaLangClass.equals(classType.rawType())) {
            return Boolean.TRUE;
          }
          return Boolean.FALSE;
        }
        PsiClass aClass = classType.resolve();
        if (aClass != null && (aClass.isAnnotationType() || aClass.isEnum())) return Boolean.TRUE;

        PsiManager manager = typeElement.getManager();
        GlobalSearchScope resolveScope = typeElement.getResolveScope();
        return classType.equals(PsiType.getJavaLangClass(manager, resolveScope)) ||
               classType.equals(PsiType.getJavaLangString(manager, resolveScope)) ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue()) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, INVALID_ANNOTATION_MEMBER_TYPE);
    }

    return null;
  }

  public static HighlightInfo checkApplicability(PsiAnnotation annotation) {
    if (!(annotation.getParent() instanceof PsiModifierList)) return null;
    PsiElement owner = annotation.getParent().getParent();
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef != null) {
      PsiElement resolved = nameRef.resolve();
      if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
        PsiClass annotationType = (PsiClass)resolved;
        PsiAnnotation metaAnnotation = annotationType.getModifierList().findAnnotation("java.lang.annotation.Target");
        if (metaAnnotation != null) {
          PsiNameValuePair[] attributes = metaAnnotation.getParameterList().getAttributes();
          if (attributes.length >= 1) {
            PsiField[] elementTypeFields = getElementTypeFields(owner);
            if (elementTypeFields == null) return null;
            LOG.assertTrue(elementTypeFields.length > 0);
            for (PsiField field : elementTypeFields) {
              PsiAnnotationMemberValue value = attributes[0].getValue();
              if (value instanceof PsiArrayInitializerMemberValue) {
                PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
                for (PsiAnnotationMemberValue initializer : initializers) {
                  if (initializer instanceof PsiReferenceExpression) {
                    PsiReferenceExpression refExpr = (PsiReferenceExpression)initializer;
                    if (refExpr.isReferenceTo(field)) return null;
                  }
                }
              }
              else if (value instanceof PsiReferenceExpression) {
                if (((PsiReferenceExpression)value).isReferenceTo(field)) return null;
              }
            }
            return formatNotApplicableError(elementTypeFields[0], nameRef);
          }
        }
      }
    }

    return null;
  }

  private static HighlightInfo formatNotApplicableError(PsiField elementType, PsiJavaCodeReferenceElement nameRef) {
    String name = elementType.getName();
    String description = JavaErrorMessages.message("annotation.not.applicable",
                                                   nameRef.getText(),
                                                   JavaErrorMessages.message(ANNOTATION_TARGET_MESSAGE_KEY_PREFIX + name));
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameRef, description);
  }

  private static PsiField[] getElementTypeFields(PsiElement owner) {
    PsiManager manager = owner.getManager();
    PsiClass elementTypeClass = manager.findClass("java.lang.annotation.ElementType", owner.getResolveScope());
    if (elementTypeClass == null) return null;

    if (owner instanceof PsiClass) {
      if (((PsiClass)owner).isAnnotationType()) {
        return getFields(elementTypeClass, "ANNOTATION_TYPE", "TYPE");
      }
      else {
        return getFields(elementTypeClass, "TYPE");
      }
    }
    else if (owner instanceof PsiMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return getFields(elementTypeClass, "CONSTRUCTOR");
      }
      else {
        return getFields(elementTypeClass, "METHOD");
      }
    }
    else if (owner instanceof PsiField) {
      return getFields(elementTypeClass, "FIELD");
    }
    else if (owner instanceof PsiParameter) {
      return getFields(elementTypeClass, "PARAMETER");
    }
    else if (owner instanceof PsiLocalVariable) {
      return getFields(elementTypeClass, "LOCAL_VARIABLE");
    }
    else if (owner instanceof PsiPackageStatement) {
      return getFields(elementTypeClass, "PACKAGE");
    }

    return null;
  }

  private static PsiField[] getFields(final PsiClass elementTypeClass, @NonNls final String... names) {
    PsiField[] result = new PsiField[names.length];
    for (int i = 0; i < names.length; i++) {
      final PsiField field = elementTypeClass.findFieldByName(names[i], false);
      if (field == null) return null;
      result[i] = field;
    }

    return result;
  }

  public static HighlightInfo checkAnnotationType(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement != null) {
      PsiElement resolved = nameReferenceElement.resolve();
      if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameReferenceElement, JavaErrorMessages.message("annotation.annotation.type.expected"));
      }
    }
    return null;
  }

  public static HighlightInfo checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
    LOG.assertTrue(aClass.isAnnotationType());
    PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, CYCLIC_ANNOTATION_MEMER_TYPE);
    }
    return null;
  }

  public static HighlightInfo checkAnnotationDeclaration(final PsiElement parent, final PsiReferenceList list) {
    if (parent instanceof PsiAnnotationMethod) {
      PsiAnnotationMethod method = (PsiAnnotationMethod)parent;
      if (list == method.getThrowsList()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("annotation.members.may.not.have.throws.list"));
      }
    }
    else if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      if (PsiKeyword.EXTENDS.equals(list.getFirstChild().getText())) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("annotation.may.not.have.extends.list"));
      }
    }
    return null;
  }


  public static HighlightInfo checkPackageAnnotationContainingFile(final PsiPackageStatement statement) {
    if (statement.getAnnotationList() == null) {
      return null;
    }
    PsiFile file = statement.getContainingFile();
    if (file != null && !PACKAGE_INFO_JAVA.equals(file.getName())) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               statement.getAnnotationList().getTextRange(),
                                               JavaErrorMessages.message("invalid.package.annotation.containing.file"));

    }
    return null;
  }
}