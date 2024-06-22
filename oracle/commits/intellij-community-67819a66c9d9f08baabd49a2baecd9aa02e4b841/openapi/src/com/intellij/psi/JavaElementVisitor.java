/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.jsp.*;

public abstract class JavaElementVisitor {
  public void visitElement(PsiElement element) {
  }

  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    visitClass(aClass);
  }

  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    visitExpression(expression);
  }

  public void visitAssertStatement(PsiAssertStatement statement) {
    visitStatement(statement);
  }

  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    visitExpression(expression);
  }

  public void visitBinaryFile(PsiBinaryFile file){
    visitFile(file);
  }

  public void visitBlockStatement(PsiBlockStatement statement) {
    visitStatement(statement);
  }

  public void visitBreakStatement(PsiBreakStatement statement) {
    visitStatement(statement);
  }

  public void visitClass(PsiClass aClass) {
    visitElement(aClass);
  }

  public void visitClassInitializer(PsiClassInitializer initializer) {
    visitElement(initializer);
  }

  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    visitExpression(expression);
  }

  public void visitCodeBlock(PsiCodeBlock block) {
    visitElement(block);
  }

  public void visitComment(PsiComment comment) {
    visitJavaToken(comment);
  }

  public void visitConditionalExpression(PsiConditionalExpression expression) {
    visitExpression(expression);
  }

  public void visitContinueStatement(PsiContinueStatement statement) {
    visitStatement(statement);
  }

  public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    visitStatement(statement);
  }

  public void visitDocComment(PsiDocComment comment) {
    visitComment(comment);
  }

  public void visitDocTag(PsiDocTag tag) {
    visitElement(tag);
  }

  public void visitDocTagValue(PsiDocTagValue value) {
    visitElement(value);
  }

  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitEmptyStatement(PsiEmptyStatement statement) {
    visitStatement(statement);
  }

  public void visitErrorElement(PsiErrorElement element) {
    visitElement(element);
  }

  public void visitExpression(PsiExpression expression) {
    visitElement(expression);
  }

  public void visitExpressionList(PsiExpressionList list) {
    visitElement(list);
  }

  public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    visitStatement(statement);
  }

  public void visitExpressionStatement(PsiExpressionStatement statement) {
    visitStatement(statement);
  }

  public void visitField(PsiField field) {
    visitVariable(field);
  }

  public void visitFile(PsiFile file) {
    visitElement(file);
  }

  public void visitForStatement(PsiForStatement statement) {
    visitStatement(statement);
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    visitStatement(statement);
  }

  public void visitIdentifier(PsiIdentifier identifier) {
    visitJavaToken(identifier);
  }

  public void visitIfStatement(PsiIfStatement statement) {
    visitStatement(statement);
  }

  public void visitImportList(PsiImportList list) {
    visitElement(list);
  }

  public void visitImportStatement(PsiImportStatement statement) {
    visitElement(statement);
  }

  public void visitImportStaticStatement(PsiImportStaticStatement statement) {
    visitElement(statement);
  }

  public void visitInlineDocTag(PsiInlineDocTag tag) {
    visitDocTag(tag);
  }

  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    visitExpression(expression);
  }

  public void visitJavaToken(PsiJavaToken token){
    visitElement(token);
  }

  public void visitKeyword(PsiKeyword keyword) {
    visitJavaToken(keyword);
  }

  public void visitLabeledStatement(PsiLabeledStatement statement) {
    visitStatement(statement);
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    visitExpression(expression);
  }

  public void visitLocalVariable(PsiLocalVariable variable) {
    visitVariable(variable);
  }

  public void visitMethod(PsiMethod method) {
    visitElement(method);
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    visitCallExpression(expression);
  }

  public void visitCallExpression(PsiCallExpression callExpression) {
    visitExpression(callExpression);
  }

  public void visitModifierList(PsiModifierList list) {
    visitElement(list);
  }

  public void visitNewExpression(PsiNewExpression expression) {
    visitCallExpression(expression);
  }

  public void visitPlainText(PsiPlainText content) {
    visitElement(content);
  }

  public void visitDirectory(PsiDirectory dir) {
    visitElement(dir);
  }

  public void visitPackage(PsiPackage aPackage) {
    visitElement(aPackage);
  }

  public void visitPackageStatement(PsiPackageStatement statement) {
    visitElement(statement);
  }

  public void visitParameter(PsiParameter parameter) {
    visitVariable(parameter);
  }

  public void visitParameterList(PsiParameterList list) {
    visitElement(list);
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    visitExpression(expression);
  }

  public void visitPostfixExpression(PsiPostfixExpression expression) {
    visitExpression(expression);
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    visitExpression(expression);
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    visitElement(reference);
  }

  public void visitImportStaticReferenceElement(PsiImportStaticReferenceElement reference) {
    visitElement(reference);
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  public void visitReferenceList(PsiReferenceList list) {
    visitElement(list);
  }

  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    visitElement(list);
  }

  public void visitTypeParameterList(PsiTypeParameterList list) {
    visitElement(list);
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    visitStatement(statement);
  }

  public void visitStatement(PsiStatement statement) {
    visitElement(statement);
  }

  public void visitSuperExpression(PsiSuperExpression expression) {
    visitExpression(expression);
  }

  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    visitStatement(statement);
  }

  public void visitSwitchStatement(PsiSwitchStatement statement) {
    visitStatement(statement);
  }

  public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    visitStatement(statement);
  }

  public void visitThisExpression(PsiThisExpression expression) {
    visitExpression(expression);
  }

  public void visitThrowStatement(PsiThrowStatement statement) {
    visitStatement(statement);
  }

  public void visitTryStatement(PsiTryStatement statement) {
    visitStatement(statement);
  }

  public void visitCatchSection(PsiCatchSection section) {
    visitElement(section);
  }

  public void visitTypeElement(PsiTypeElement type) {
    visitElement(type);
  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    visitExpression(expression);
  }

  public void visitVariable(PsiVariable variable) {
    visitElement(variable);
  }

  public void visitWhileStatement(PsiWhileStatement statement) {
    visitStatement(statement);
  }

  public void visitWhiteSpace(PsiWhiteSpace space) {
    visitElement(space);
  }

  public void visitJavaFile(PsiJavaFile file){
    visitFile(file);
  }

  public void visitPlainTextFile(PsiPlainTextFile file){
    visitFile(file);
  }

  public void visitJspFile(JspFile file){
    visitFile(file);
  }

  public void visitJspElement(JspElement element) {
    visitElement(element);
  }

  public void visitJspAction(JspAction action){
    visitJspElement(action);
  }

  public void visitJspAttribute(JspAttribute attribute){
    visitJspElement(attribute);
  }

  public void visitJspDeclaration(JspDeclaration declaration){
    visitJspElement(declaration);
  }

  public void visitJspDirective(JspDirective directive){
    visitJspElement(directive);
  }

  public void visitJspExpression(JspExpression expression){
    visitJspElement(expression);
  }

  public void visitImplicitVariable(ImplicitVariable variable) {
    visitLocalVariable(variable);
  }

  public void visitJspImplicitVariable(JspImplicitVariable variable){
    visitImplicitVariable(variable);
  }

  public void visitJspImportValue(JspImportValue value){
    visitJspElement(value);
  }

  public void visitJspToken(JspToken token){
    visitJspElement(token);
  }

  public void visitJspAttributeValue(JspAttributeValue value) {
    visitJspElement(value);
  }

  public void visitJspFileReference(JspFileReference jspFileReference) {
    visitJspElement(jspFileReference);
  }

  public void visitDocToken(PsiDocToken token) {
    visitElement(token);
  }

  public void visitTypeParameter(PsiTypeParameter classParameter) {
    visitClass(classParameter);
  }

  public void visitAnnotation(PsiAnnotation annotation) {
    visitElement(annotation);
  }

  public void visitAnnotationParameterList(PsiAnnotationParameterList list) {
    visitElement(list);
  }

  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    visitElement(initializer);
  }

  public void visitNameValuePair(PsiNameValuePair pair) {
    visitElement(pair);
  }

  public void visitAnnotationMethod(PsiAnnotationMethod method) {
    visitMethod(method);
  }

  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    visitField(enumConstant);
  }

  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {
    visitAnonymousClass(enumConstantInitializer);
  }
}
