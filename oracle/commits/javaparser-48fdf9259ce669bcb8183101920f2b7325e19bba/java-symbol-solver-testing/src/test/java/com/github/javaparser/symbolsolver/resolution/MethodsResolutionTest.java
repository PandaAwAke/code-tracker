/*
 * Copyright 2016 Federico Tomassetti
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

package com.github.javaparser.symbolsolver.resolution;

import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparser.Navigator;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MethodsResolutionTest extends AbstractResolutionTest {

    @Test
    public void solveMethodAccessThroughSuper() throws ParseException {
        CompilationUnit cu = parseSample("AccessThroughSuper");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "AccessThroughSuper.SubClass");
        MethodDeclaration method = Navigator.demandMethod(clazz, "methodTest");
        ReturnStmt returnStmt = (ReturnStmt) method.getBody().get().getStatements().get(0);
        Expression expression = returnStmt.getExpression().get();

        ResolvedType ref = JavaParserFacade.get(new ReflectionTypeSolver()).getType(expression);
        assertEquals("java.lang.String", ref.describe());
    }

    @Test
    public void solveMethodWithClassExpressionAsParameter() throws ParseException {
        CompilationUnit cu = parseSample("ClassExpression");
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "ClassExpression");
        MethodDeclaration method = Navigator.demandMethod(clazz, "foo");
        MethodCallExpr expression = Navigator.findMethodCall(method, "noneOf");

        MethodUsage methodUsage = JavaParserFacade.get(new ReflectionTypeSolver()).solveMethodAsUsage(expression);
        assertEquals("noneOf", methodUsage.getName());
    }

    @Test
    public void solveMethodInInterfaceParent() throws ParseException {
        CompilationUnit cu = parseSample("MethodCalls");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "MethodCalls");

        MethodDeclaration method = Navigator.demandMethod(clazz, "inheritedInterfaceMethod");
        MethodCallExpr expression = Navigator.findMethodCall(method, "toString");

        TypeSolver typeSolver = new ReflectionTypeSolver();

        JavaParserFacade javaParserFacade = JavaParserFacade.get(typeSolver);
        MethodUsage call1 = javaParserFacade.solveMethodAsUsage(expression);
        assertEquals("java.lang.Object.toString()", call1.getQualifiedSignature());
    }

    @Test
    public void solveMethodWithTypePromotionsToLong() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingLong");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToInt() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingInt");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToShort() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingShort");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToByte() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotions");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingByte");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("byteParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToLongWithExtraParam() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingLong");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("longParam", reference.getCorrespondingDeclaration().getName());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToIntWithExtraParam() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingInt");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("intParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToShortWithExtraParam() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingShort");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("shortParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }

    }

    @Test
    public void solveMethodWithTypePromotionsToByteWithExtraParam() throws ParseException {
        CompilationUnit cu = parseSample("Issue338");
        ClassOrInterfaceDeclaration clazz = Navigator.demandClass(cu, "TypePromotionsWithExtraParam");

        MethodDeclaration method = Navigator.demandMethod(clazz, "callingByte");

        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(0).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(true, reference.isSolved());
            assertEquals("byteParam", reference.getCorrespondingDeclaration().getName());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(1).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(2).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }
        {
            MethodCallExpr expression = method.getBody().get().getStatements().get(3).asExpressionStmt().getExpression().asMethodCallExpr();
            SymbolReference<ResolvedMethodDeclaration> reference = JavaParserFacade.get(new ReflectionTypeSolver()).solve(expression);
            assertEquals(false, reference.isSolved());
        }

    }
}
