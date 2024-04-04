package com.github.javaparser.printer.lexicalpreservation;

import com.github.javaparser.ASTParserConstants;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.printer.ConcreteSyntaxModel;
import com.github.javaparser.printer.concretesyntaxmodel.CsmElement;
import com.github.javaparser.printer.concretesyntaxmodel.CsmToken;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class LexicalDifferenceCalculatorTest extends AbstractLexicalPreservingTest {

    @Test
    public void compilationUnitExampleOriginal() {
        considerCode("class A {}");
        CsmElement element = ConcreteSyntaxModel.forClass(cu.getClass());
        LexicalDifferenceCalculator.CalculatedSyntaxModel csmOriginal = new LexicalDifferenceCalculator().calculatedSyntaxModelForNode(element, cu);
        assertEquals(2, csmOriginal.elements.size());
        assertEquals(new LexicalDifferenceCalculator.CsmChild(cu.getType(0)), csmOriginal.elements.get(0));
        assertEquals(new CsmToken(3), csmOriginal.elements.get(1));
    }

    @Test
    public void compilationUnitExampleWithPackageSet() {
        considerCode("class A {}");
        CsmElement element = ConcreteSyntaxModel.forClass(cu.getClass());
        PackageDeclaration packageDeclaration = new PackageDeclaration(new Name(new Name("foo"), "bar"));
        LexicalDifferenceCalculator.CalculatedSyntaxModel csmChanged = new LexicalDifferenceCalculator().calculatedSyntaxModelAfterPropertyChange(element, cu, ObservableProperty.PACKAGE_DECLARATION, null, packageDeclaration);
        assertEquals(3, csmChanged.elements.size());
        assertEquals(new LexicalDifferenceCalculator.CsmChild(packageDeclaration), csmChanged.elements.get(0));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(cu.getType(0)), csmChanged.elements.get(1));
        assertEquals(new CsmToken(3), csmChanged.elements.get(2));
    }

    @Test
    public void annotationDeclarationModifiersExampleOriginal() throws IOException {
        considerExample("AnnotationDeclaration_Example1_original");
        AnnotationDeclaration annotationDeclaration = (AnnotationDeclaration)cu.getType(0);
        CsmElement element = ConcreteSyntaxModel.forClass(annotationDeclaration.getClass());
        LexicalDifferenceCalculator.CalculatedSyntaxModel csm = new LexicalDifferenceCalculator().calculatedSyntaxModelForNode(element, annotationDeclaration);
        assertEquals(24, csm.elements.size());
        assertEquals(new CsmToken(ASTParserConstants.AT), csm.elements.get(0));
        assertEquals(new CsmToken(ASTParserConstants.INTERFACE), csm.elements.get(1));
        assertEquals(new CsmToken(32), csm.elements.get(2));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getName()), csm.elements.get(3));
        assertEquals(new CsmToken(32), csm.elements.get(4));
        assertEquals(new CsmToken(ASTParserConstants.LBRACE), csm.elements.get(5));
        assertEquals(new CsmToken(3), csm.elements.get(6));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(0)), csm.elements.get(7));
        assertEquals(new CsmToken(3), csm.elements.get(8));
        assertEquals(new CsmToken(3), csm.elements.get(9));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(1)), csm.elements.get(10));
        assertEquals(new CsmToken(3), csm.elements.get(11));
        assertEquals(new CsmToken(3), csm.elements.get(12));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(2)), csm.elements.get(13));
        assertEquals(new CsmToken(3), csm.elements.get(14));
        assertEquals(new CsmToken(3), csm.elements.get(15));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(3)), csm.elements.get(16));
        assertEquals(new CsmToken(3), csm.elements.get(17));
        assertEquals(new CsmToken(3), csm.elements.get(18));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(4)), csm.elements.get(19));
        assertEquals(new CsmToken(3), csm.elements.get(20));
        assertEquals(new CsmToken(3), csm.elements.get(21));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(5)), csm.elements.get(22));
        assertEquals(new CsmToken(ASTParserConstants.RBRACE), csm.elements.get(23));
    }

    @Test
    public void annotationDeclarationModifiersExampleModified() throws IOException {
        considerExample("AnnotationDeclaration_Example1_original");
        AnnotationDeclaration annotationDeclaration = (AnnotationDeclaration)cu.getType(0);
        CsmElement element = ConcreteSyntaxModel.forClass(annotationDeclaration.getClass());
        LexicalDifferenceCalculator.CalculatedSyntaxModel csm = new LexicalDifferenceCalculator().calculatedSyntaxModelAfterPropertyChange(element, annotationDeclaration, ObservableProperty.MODIFIERS, EnumSet.noneOf(Modifier.class), EnumSet.of(Modifier.PUBLIC));
        assertEquals(26, csm.elements.size());
        assertEquals(new CsmToken(ASTParserConstants.PUBLIC), csm.elements.get(0));
        assertEquals(new CsmToken(32), csm.elements.get(1));
        assertEquals(new CsmToken(ASTParserConstants.AT), csm.elements.get(2));
        assertEquals(new CsmToken(ASTParserConstants.INTERFACE), csm.elements.get(3));
        assertEquals(new CsmToken(32), csm.elements.get(4));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getName()), csm.elements.get(5));
        assertEquals(new CsmToken(32), csm.elements.get(6));
        assertEquals(new CsmToken(ASTParserConstants.LBRACE), csm.elements.get(7));
        assertEquals(new CsmToken(3), csm.elements.get(8));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(0)), csm.elements.get(9));
        assertEquals(new CsmToken(3), csm.elements.get(10));
        assertEquals(new CsmToken(3), csm.elements.get(11));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(1)), csm.elements.get(12));
        assertEquals(new CsmToken(3), csm.elements.get(13));
        assertEquals(new CsmToken(3), csm.elements.get(14));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(2)), csm.elements.get(15));
        assertEquals(new CsmToken(3), csm.elements.get(16));
        assertEquals(new CsmToken(3), csm.elements.get(17));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(3)), csm.elements.get(18));
        assertEquals(new CsmToken(3), csm.elements.get(19));
        assertEquals(new CsmToken(3), csm.elements.get(20));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(4)), csm.elements.get(21));
        assertEquals(new CsmToken(3), csm.elements.get(22));
        assertEquals(new CsmToken(3), csm.elements.get(23));
        assertEquals(new LexicalDifferenceCalculator.CsmChild(annotationDeclaration.getMember(5)), csm.elements.get(24));
        assertEquals(new CsmToken(ASTParserConstants.RBRACE), csm.elements.get(25));
    }
}
