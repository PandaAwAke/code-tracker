package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.TypeConversionUtil;

public class PsiConditionalExpressionImpl extends CompositePsiElement implements PsiConditionalExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl");

  public PsiConditionalExpressionImpl() {
    super(CONDITIONAL_EXPRESSION);
  }

  public PsiExpression getCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public PsiExpression getThenExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.THEN_EXPRESSION);
  }

  public PsiExpression getElseExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ELSE_EXPRESSION);
  }

  /**
   * JLS 15.25
   */
  public PsiType getType() {
    PsiExpression expr1 = getThenExpression();
    PsiExpression expr2 = getElseExpression();
    PsiType type1 = expr1 != null ? expr1.getType() : null;
    PsiType type2 = expr2 != null ? expr2.getType() : null;
    if (type1 == null) return type2;
    if (type2 == null) return type1;

    if (type1.equals(type2)) return type1;
    final int typeRank1 = TypeConversionUtil.getTypeRank(type1);
    final int typeRank2 = TypeConversionUtil.getTypeRank(type2);
    if (TypeConversionUtil.isNumericType(typeRank1) && TypeConversionUtil.isNumericType(typeRank2)){
      if (typeRank1 == TypeConversionUtil.BYTE_RANK && typeRank2 == TypeConversionUtil.SHORT_RANK) return type2;
      if (typeRank1 == TypeConversionUtil.SHORT_RANK && typeRank2 == TypeConversionUtil.BYTE_RANK) return type1;
      if (typeRank1 == TypeConversionUtil.BYTE_RANK || typeRank1 == TypeConversionUtil.SHORT_RANK || typeRank1 == TypeConversionUtil.CHAR_RANK){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type1, expr2)) return type1;
      }
      if (typeRank2 == TypeConversionUtil.BYTE_RANK || typeRank2 == TypeConversionUtil.SHORT_RANK || typeRank2 == TypeConversionUtil.CHAR_RANK){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type2, expr1)) return type2;
      }
      return TypeConversionUtil.binaryNumericPromotion(type1, type2);
    }
    if (TypeConversionUtil.isNullType(type1) && !(type2 instanceof PsiPrimitiveType)) return type2;
    if (TypeConversionUtil.isNullType(type2) && !(type1 instanceof PsiPrimitiveType)) return type1;

    if (type1.isAssignableFrom(type2)) return type1;
    if (type2.isAssignableFrom(type1)) return type2;
    if (getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) {
      return null;
    }
    else {
      if (type1 instanceof PsiPrimitiveType) type1 = ((PsiPrimitiveType)type1).getBoxedType(getManager(), getResolveScope());
      if (type1 == null) return null;
      if (type2 instanceof PsiPrimitiveType) type2 = ((PsiPrimitiveType)type2).getBoxedType(getManager(), getResolveScope());
      if (type2 == null) return null;

      return GenericsUtil.getLeastUpperBound(type1, type2, getManager());
    }
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.CONDITION:
        return firstChild;

      case ChildRole.QUEST:
        return TreeUtil.findChild(this, QUEST);

      case ChildRole.THEN_EXPRESSION:
        {
          TreeElement quest = findChildByRole(ChildRole.QUEST);
          TreeElement child = quest.getTreeNext();
          while(true){
            if (child == null) return null;
            if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) break;
            child = child.getTreeNext();
          }
          return child;
        }

      case ChildRole.COLON:
        return TreeUtil.findChild(this, COLON);

      case ChildRole.ELSE_EXPRESSION:
        {
          TreeElement colon = findChildByRole(ChildRole.COLON);
          if (colon == null) return null;
          return EXPRESSION_BIT_SET.isInSet(lastChild.getElementType()) ? lastChild : null;
        }
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (EXPRESSION_BIT_SET.isInSet(child.getElementType())){
      int role = getChildRole(child, ChildRole.CONDITION);
      if (role != ChildRole.NONE) return role;
      role = getChildRole(child, ChildRole.THEN_EXPRESSION);
      if (role != ChildRole.NONE) return role;
      role = getChildRole(child, ChildRole.ELSE_EXPRESSION);
      return role;
    }
    else if (child.getElementType() == QUEST){
      return ChildRole.QUEST;
    }
    else if (child.getElementType() == COLON){
      return ChildRole.COLON;
    }
    else{
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitConditionalExpression(this);
  }

  public String toString() {
    return "PsiConditionalExpression:" + getText();
  }
}

