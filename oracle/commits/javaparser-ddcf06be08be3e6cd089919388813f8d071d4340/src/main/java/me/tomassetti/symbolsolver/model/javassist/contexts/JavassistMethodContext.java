package me.tomassetti.symbolsolver.model.javassist.contexts;

import javassist.CtClass;
import javassist.CtMethod;
import me.tomassetti.symbolsolver.model.Context;
import me.tomassetti.symbolsolver.model.SymbolReference;
import me.tomassetti.symbolsolver.model.TypeSolver;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;

import java.util.List;
import java.util.Optional;

/**
 * Created by fede on 8/14/15.
 */
public class JavassistMethodContext implements Context {

    private CtMethod wrappedNode;

    public JavassistMethodContext(CtMethod wrappedNode) {
        this.wrappedNode = wrappedNode;
    }

    @Override
    public SymbolReference<ValueDeclaration> solveSymbol(String name, TypeSolver typeSolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SymbolReference<TypeDeclaration> solveType(String name, TypeSolver typeSolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<TypeUsage> solveGenericType(String name, TypeSolver typeSolver) {
        // TODO consider generic parameters of the method
        return getParent().solveGenericType(name, typeSolver);
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Context getParent() {
        CtClass ctClass = wrappedNode.getDeclaringClass();
        return new JavassistClassContext(ctClass);
    }

}
