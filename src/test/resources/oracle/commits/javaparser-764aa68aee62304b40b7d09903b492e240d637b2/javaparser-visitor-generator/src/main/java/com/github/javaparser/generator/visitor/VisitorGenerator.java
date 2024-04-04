package com.github.javaparser.generator.visitor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.generator.utils.SourceRoot;
import com.github.javaparser.metamodel.BaseNodeMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A class that makes it easier to generate visitor classes.
 * It will create missing visit methods on the fly,
 * and will ask you to fill in the bodies of the visit methods.
 */
public abstract class VisitorGenerator {
    protected final JavaParser javaParser;
    protected final SourceRoot sourceRoot;
    private final String pkg;
    private final String visitorClassName;
    private final String returnType;
    private final String argumentType;
    private final boolean createMissingVisitMethods;
    protected final JavaParserMetaModel javaParserMetaModel;

    public VisitorGenerator(JavaParser javaParser, SourceRoot sourceRoot, String pkg, String visitorClassName, String returnType, String argumentType, boolean createMissingVisitMethods, JavaParserMetaModel javaParserMetaModel) {
        this.javaParser = javaParser;
        this.sourceRoot = sourceRoot;
        this.pkg = pkg;
        this.visitorClassName = visitorClassName;
        this.returnType = returnType;
        this.argumentType = argumentType;
        this.createMissingVisitMethods = createMissingVisitMethods;
        this.javaParserMetaModel = javaParserMetaModel;
    }

    public void generate() throws IOException {
        CompilationUnit compilationUnit = sourceRoot.parse(pkg, visitorClassName + ".java", javaParser).get();

        Optional<ClassOrInterfaceDeclaration> visitorClassOptional = compilationUnit.getClassByName(visitorClassName);
        if (!visitorClassOptional.isPresent()) {
            visitorClassOptional = compilationUnit.getInterfaceByName(visitorClassName);
        }
        ClassOrInterfaceDeclaration visitorClass = visitorClassOptional.get();

        for (BaseNodeMetaModel node : javaParserMetaModel.getNodeMetaModels()) {
            if (!node.isAbstract()) {
                List<PropertyMetaModel> allPropertyMetaModels = collectAllPropertyMetaModels(node);

                Optional<MethodDeclaration> visitMethod = visitorClass.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals("visit"))
                        .filter(m -> m.getParameter(0).getType().toString().equals(node.getTypeName()))
                        .findFirst();

                if (visitMethod.isPresent()) {
                    generateVisitMethodBody(node, visitMethod.get(), allPropertyMetaModels, compilationUnit);
                } else if (createMissingVisitMethods) {
                    MethodDeclaration methodDeclaration = visitorClass.addMethod("visit")
                            .addParameter(node.getTypeNameGenericsed(), "n")
                            .addParameter(argumentType, "arg")
                            .setType(returnType);
                    generateVisitMethodBody(node, methodDeclaration, allPropertyMetaModels, compilationUnit);
                }
            }
        }
    }

    private List<PropertyMetaModel> collectAllPropertyMetaModels(BaseNodeMetaModel node) {
        List<PropertyMetaModel> allPropertyMetaModels = new ArrayList<>(node.getPropertyMetaModels());
        BaseNodeMetaModel walkNode = node;
        while (walkNode.getSuperNodeMetaModel().isPresent()) {
            walkNode = walkNode.getSuperNodeMetaModel().get();
            allPropertyMetaModels.addAll(walkNode.getPropertyMetaModels());
        }
        return allPropertyMetaModels;
    }

    protected abstract void generateVisitMethodBody(BaseNodeMetaModel node, MethodDeclaration visitMethod, List<PropertyMetaModel> allPropertyMetaModels, CompilationUnit compilationUnit);
}
