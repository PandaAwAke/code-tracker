/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 17, 2003
 * Time: 4:25:54 PM
 * To change this template use Options | File Templates.
 */
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
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;

import java.util.Map;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents primitive types of Java language.
 */
public class PsiPrimitiveType extends PsiType {
  protected static final PsiPrimitiveType VOID = new PsiPrimitiveType("void");
  protected static final PsiPrimitiveType BYTE = new PsiPrimitiveType("byte");
  protected static final PsiPrimitiveType CHAR = new PsiPrimitiveType("char");
  protected static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType("double");
  protected static final PsiPrimitiveType FLOAT = new PsiPrimitiveType("float");
  protected static final PsiPrimitiveType LONG = new PsiPrimitiveType("long");
  protected static final PsiPrimitiveType INT = new PsiPrimitiveType("int");
  protected static final PsiPrimitiveType SHORT = new PsiPrimitiveType("short");
  protected static final PsiPrimitiveType BOOLEAN = new PsiPrimitiveType("boolean");
  protected static final PsiPrimitiveType NULL = new PsiPrimitiveType("null");

  private final String myName;

  private PsiPrimitiveType(@NonNls String name) {
    myName = name;
  }

  public String getPresentableText() {
    return myName;
  }

  public String getCanonicalText() {
    return myName;
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  /**
   * Always returns true.
   */
  public boolean isValid() {
    return true;
  }

  public boolean equalsToText(String text) {
    return myName.equals(text);
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitPrimitiveType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    return new PsiType[0];
  }

  /**
   * Returns the primitive type corresponding to a boxed class type.
   *
   * @param type the type to get the unboxed primitive type for.
   * @return the primitive type, or null if the type does not represent a boxed primitive type.
   */
  @Nullable
  public static PsiPrimitiveType getUnboxedType(PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return null;
    if (!psiClass.getManager().getEffectiveLanguageLevel().hasEnumKeywordAndAutoboxing()) return null;
    return ourQNameToUnboxed.get(psiClass.getQualifiedName());
  }

  /**
   * Returns a boxed class type corresponding to the primitive type.
   *
   * @param manager      the manager used to create the class type.
   * @param resolveScope the scope for searching the class.
   * @return the class type, or null if the current language level does not support autoboxing or
   *         it was not possible to resolve the reference to the class.
   */
  @Nullable
  public PsiClassType getBoxedType(PsiManager manager, GlobalSearchScope resolveScope) {
    if (!manager.getEffectiveLanguageLevel().hasEnumKeywordAndAutoboxing()) return null;
    final String boxedQName = ourUnboxedToQName.get(this);

    //[ven]previous call returns null for NULL, VOID
    if (boxedQName == null) return null;

    final PsiClass aClass = manager.findClass(boxedQName, resolveScope);
    if (aClass == null) return null;
    return manager.getElementFactory().createType(aClass);
  }

  private static final Map<String, PsiPrimitiveType> ourQNameToUnboxed = new HashMap<String, PsiPrimitiveType>();
  private static final Map<PsiPrimitiveType, String> ourUnboxedToQName = new HashMap<PsiPrimitiveType, String>();
  static {
    ourQNameToUnboxed.put("java.lang.Boolean", BOOLEAN);
    ourUnboxedToQName.put(BOOLEAN, "java.lang.Boolean");
    ourQNameToUnboxed.put("java.lang.Byte", BYTE);
    ourUnboxedToQName.put(BYTE, "java.lang.Byte");
    ourQNameToUnboxed.put("java.lang.Character", CHAR);
    ourUnboxedToQName.put(CHAR, "java.lang.Character");
    ourQNameToUnboxed.put("java.lang.Short", SHORT);
    ourUnboxedToQName.put(SHORT, "java.lang.Short");
    ourQNameToUnboxed.put("java.lang.Integer", INT);
    ourUnboxedToQName.put(INT, "java.lang.Integer");
    ourQNameToUnboxed.put("java.lang.Long", LONG);
    ourUnboxedToQName.put(LONG, "java.lang.Long");
    ourQNameToUnboxed.put("java.lang.Float", FLOAT);
    ourUnboxedToQName.put(FLOAT, "java.lang.Float");
    ourQNameToUnboxed.put("java.lang.Double", DOUBLE);
    ourUnboxedToQName.put(DOUBLE, "java.lang.Double");
  }
}
