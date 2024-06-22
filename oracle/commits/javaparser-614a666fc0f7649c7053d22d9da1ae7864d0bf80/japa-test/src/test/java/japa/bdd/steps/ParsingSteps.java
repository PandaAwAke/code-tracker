package japa.bdd.steps;

import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.*;
import japa.parser.ast.expr.AnnotationExpr;
import japa.parser.ast.expr.LambdaExpr;
import japa.parser.ast.expr.MethodReferenceExpr;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.Statement;
import org.jbehave.core.annotations.Then;

import java.util.Map;

import static japa.bdd.steps.SharedSteps.getMemberByTypeAndPosition;
import static japa.bdd.steps.SharedSteps.getMethodByPositionAndClassPosition;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class ParsingSteps {

    private Map<String, Object> state;

    public ParsingSteps(Map<String, Object> state){
        this.state = state;
    }

    @Then("constructor $constructorPosition in class $classPosition declaration as a String is \"$expectedString\"")
    public void thenTheConstructorDeclarationAsAStringIs(int constructorPosition, int classPosition, String expectedString) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration)compilationUnit.getTypes().get(classPosition - 1);
        ConstructorDeclaration constructor = (ConstructorDeclaration)clazz.getMembers().get(constructorPosition - 1);
        assertThat(constructor.getDeclarationAsString(), is(expectedString));
    }

    @Then("constructor $constructorPosition in class $classPosition declaration short form as a String is \"$expectedString\"")
    public void thenConstructorInClassDeclarationShortFormAsAStringIs(int constructorPosition, int classPosition, String expectedString) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration)compilationUnit.getTypes().get(classPosition - 1);
        ConstructorDeclaration constructor = (ConstructorDeclaration)clazz.getMembers().get(constructorPosition - 1);
        assertThat(constructor.getDeclarationAsString(false,false), is(expectedString));
    }

    @Then("method $methodPosition in class $classPosition declaration as a String is \"$expectedString\"")
    public void thenMethod1InClass1DeclarationAsAStringIs(int methodPosition, int classPosition, String expectedString) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration)compilationUnit.getTypes().get(classPosition -1);
        MethodDeclaration method = (MethodDeclaration)clazz.getMembers().get(methodPosition -1);
        assertThat(method.getDeclarationAsString(), is(expectedString));
    }

    @Then("method $methodPosition in class $classPosition declaration as a String short form is \"$expectedString\"")
    public void thenMethodInClassDeclarationAsAStringShortFormIs(int methodPosition, int classPosition, String expectedString) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        ClassOrInterfaceDeclaration clazz = (ClassOrInterfaceDeclaration)compilationUnit.getTypes().get(classPosition -1);
        MethodDeclaration method = (MethodDeclaration)clazz.getMembers().get(methodPosition -1);
        assertThat(method.getDeclarationAsString(false, false), is(expectedString));
    }

    @Then("field $fieldPosition in class $classPosition contains annotation $annotationPosition value is \"$expectedValue\"")
    public void thenFieldInClassContainsAnnotationValueIs(int fieldPosition, int classPosition, int annotationPosition, String expectedValue) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");

        TypeDeclaration classUnderTest = compilationUnit.getTypes().get(classPosition - 1);
        FieldDeclaration fieldUnderTest = (FieldDeclaration) getMemberByTypeAndPosition(classUnderTest, fieldPosition - 1,
                FieldDeclaration.class);
        AnnotationExpr annotationUnderTest = fieldUnderTest.getAnnotations().get(annotationPosition - 1);
        assertThat(annotationUnderTest.getChildrenNodes().get(1).toString(), is(expectedValue));
    }

    @Then("lambda in statement $statementPosition in method $methodPosition in class $classPosition is called $expectedName")
    public void thenLambdaInClassIsCalled(int statementPosition, int methodPosition, int classPosition, String expectedName) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        MethodDeclaration method = getMethodByPositionAndClassPosition(compilationUnit,
                methodPosition, classPosition);

        Statement statement =  method.getBody().getStmts().get(statementPosition-1);
        VariableDeclarator variableDeclarator = (VariableDeclarator)statement.getChildrenNodes().get(0)
                .getChildrenNodes().get(1);
        assertThat(variableDeclarator.getId().getName(), is(expectedName));
    }

    @Then("lambda in statement $statementPosition in method $methodPosition in class $classPosition body is \"$expectedBody\"")
    public void thenLambdaInStatementInMethodInClassBody(int statementPosition, int methodPosition, int classPosition,
                                                         String expectedBody) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        MethodDeclaration method = getMethodByPositionAndClassPosition(compilationUnit,
                methodPosition, classPosition);

        Statement statement =  method.getBody().getStmts().get(statementPosition-1);
        VariableDeclarator variableDeclarator = (VariableDeclarator)statement.getChildrenNodes().get(0)
                .getChildrenNodes().get(1);
        LambdaExpr lambdaExpr = (LambdaExpr) variableDeclarator.getInit();
        assertThat(lambdaExpr.getBody().toString(), is(expectedBody));
    }

    @Then("lambda in statement $statementPosition in method $methodPosition in class $classPosition block statement is null")
    public void thenLambdaInStatementInMethodInClassBlockStatementIsNull(int statementPosition, int methodPosition, int classPosition) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        MethodDeclaration method = getMethodByPositionAndClassPosition(compilationUnit,
                methodPosition, classPosition);

        Statement statement =  method.getBody().getStmts().get(statementPosition-1);
        VariableDeclarator variableDeclarator = (VariableDeclarator)statement.getChildrenNodes().get(0)
                .getChildrenNodes().get(1);
        LambdaExpr lambdaExpr = (LambdaExpr) variableDeclarator.getInit();
        BlockStmt blockStmt = (BlockStmt) lambdaExpr.getBody();
        assertThat(blockStmt.getStmts(), is(nullValue()));
    }

    @Then("lambda in statement $statementPosition in method $methodPosition in class $classPosition block statement is \"$expectedBody\"")
    public void thenLambdaInStatementInMethodInClassBlockStatement(int statementPosition, int methodPosition, int classPosition,
                                                                   String expectedBody) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");
        MethodDeclaration method = getMethodByPositionAndClassPosition(compilationUnit,
                methodPosition, classPosition);

        Statement statement =  method.getBody().getStmts().get(statementPosition-1);
        VariableDeclarator variableDeclarator = (VariableDeclarator)statement.getChildrenNodes().get(0)
                .getChildrenNodes().get(1);
        LambdaExpr lambdaExpr = (LambdaExpr) variableDeclarator.getInit();
        BlockStmt blockStmt = (BlockStmt) lambdaExpr.getBody();
        Statement lambdaStmt = blockStmt.getStmts().get(0);
        assertThat(lambdaStmt.toString(), is(expectedBody));
    }

    @Then("method reference in statement $statementPosition in method $methodPosition in class $classPosition scope is $expectedName")
    public void thenMethodReferenceInStatementInMethodInClassIsScope(int statementPosition, int methodPosition,
                                                                     int classPosition, String expectedName) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");

        MethodDeclaration methodUnderTest = getMethodByPositionAndClassPosition(compilationUnit,
                methodPosition, classPosition);

        Statement statementUnderTest =  methodUnderTest.getBody().getStmts().get(statementPosition-1);
        MethodReferenceExpr methodReferenceUnderTest =
                (MethodReferenceExpr) statementUnderTest.getChildrenNodes().get(0).getChildrenNodes().get(2);

        assertThat(methodReferenceUnderTest.getScope().toString(), is(expectedName));
    }

    @Then("method reference in statement $statementPosition in method $methodPosition in class $classPosition identifier is $expectedName")
    public void thenMethodReferenceInStatementInMethodInClassIdentifierIsCompareByAge(int statementPosition, int methodPosition,
                                                                                      int classPosition, String expectedName) {
        CompilationUnit compilationUnit = (CompilationUnit) state.get("cu1");

        MethodDeclaration methodUnderTest = getMethodByPositionAndClassPosition(compilationUnit,
                methodPosition, classPosition);

        Statement statementUnderTest =  methodUnderTest.getBody().getStmts().get(statementPosition-1);
        MethodReferenceExpr methodReferenceUnderTest =
                (MethodReferenceExpr) statementUnderTest.getChildrenNodes().get(0).getChildrenNodes().get(2);

        assertThat(methodReferenceUnderTest.getIdentifier(), is(expectedName));
    }
}
