package me.tomassetti.symbolsolver.model.javaparser.contexts;

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import me.tomassetti.symbolsolver.model.*;
import me.tomassetti.symbolsolver.model.declarations.MethodDeclaration;
import me.tomassetti.symbolsolver.model.declarations.TypeDeclaration;
import me.tomassetti.symbolsolver.model.declarations.ValueDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.JavaParserFactory;
import me.tomassetti.symbolsolver.model.javaparser.UnsolvedSymbolException;
import me.tomassetti.symbolsolver.model.javaparser.UnsolvedTypeException;
import me.tomassetti.symbolsolver.model.javaparser.declarations.JavaParserClassDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.declarations.JavaParserMethodDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.declarations.JavaParserTypeParameter;
import me.tomassetti.symbolsolver.model.usages.TypeUsage;
import me.tomassetti.symbolsolver.model.usages.TypeUsageOfTypeParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Federico Tomassetti
 */
public class ClassOrInterfaceDeclarationContext extends AbstractJavaParserContext<ClassOrInterfaceDeclaration> {

    public ClassOrInterfaceDeclarationContext(ClassOrInterfaceDeclaration wrappedNode) {
        super(wrappedNode);
    }

    @Override
    public SymbolReference<? extends ValueDeclaration> solveSymbol(String name, TypeSolver typeSolver) {
        if (typeSolver == null) throw new IllegalArgumentException();

        // first among declared fields
        for (BodyDeclaration member : wrappedNode.getMembers()){
            if (member instanceof FieldDeclaration) {
                SymbolDeclarator symbolDeclarator = JavaParserFactory.getSymbolDeclarator(member, typeSolver);
                SymbolReference ref = solveWith(symbolDeclarator, name);
                if (ref.isSolved()) {
                    return ref;
                }
            }
        }

        // then among inherited fields
        if (!wrappedNode.isInterface() && wrappedNode.getExtends() != null && wrappedNode.getExtends().size() > 0){
            String superClassName = wrappedNode.getExtends().get(0).getName();
            SymbolReference<TypeDeclaration> superClass = solveType(superClassName, typeSolver);
            if (!superClass.isSolved()) {
                throw new UnsolvedTypeException(this, superClassName);
            }
            SymbolReference ref = superClass.getCorrespondingDeclaration().getContext().solveSymbol(name, typeSolver);
            if (ref.isSolved()) {
                return ref;
            }
        }

        // then to parent
        return getParent().solveSymbol(name, typeSolver);
    }

    @Override
    public Optional<Value> solveSymbolAsValue(String name, TypeSolver typeSolver) {
        if (typeSolver == null) throw new IllegalArgumentException();

        // first among declared fields
        for (BodyDeclaration member : wrappedNode.getMembers()){
            if (member instanceof FieldDeclaration) {
                SymbolDeclarator symbolDeclarator = JavaParserFactory.getSymbolDeclarator(member, typeSolver);
                Optional<Value> ref = solveWithAsValue(symbolDeclarator, name, typeSolver);
                if (ref.isPresent()) {
                    return ref;
                }
            }
        }

        // then among inherited fields
        if (!wrappedNode.isInterface() && wrappedNode.getExtends() != null && wrappedNode.getExtends().size() > 0){
            String superClassName = wrappedNode.getExtends().get(0).getName();
            SymbolReference<TypeDeclaration> superClass = solveType(superClassName, typeSolver);
            if (!superClass.isSolved()) {
                throw new UnsolvedTypeException(this, superClassName);
            }
            Optional<Value> ref = superClass.getCorrespondingDeclaration().getContext().solveSymbolAsValue(name, typeSolver);
            if (ref.isPresent()) {
                return ref;
            }
        }

        // then to parent
        return getParent().solveSymbolAsValue(name, typeSolver);
    }

    @Override
    public Optional<TypeUsage> solveGenericType(String name, TypeSolver typeSolver) {
        for (com.github.javaparser.ast.TypeParameter tp : wrappedNode.getTypeParameters()) {
            if (tp.getName().equals(name)) {
                return Optional.of(new TypeUsageOfTypeParameter(new JavaParserTypeParameter(tp)));
            }
        }
        return Optional.empty();
    }

    @Override
    public SymbolReference<TypeDeclaration> solveType(String name, TypeSolver typeSolver) {
        SymbolReference<TypeDeclaration> ref = getDeclaration().solveType(name, typeSolver);
        if (ref.isSolved()) {
            return ref;
        }
        return getParent().solveType(name, typeSolver);
    }

    private TypeDeclaration getDeclaration() {
        return new JavaParserClassDeclaration(this.wrappedNode);
    }

    @Override
    public SymbolReference<MethodDeclaration> solveMethod(String name, List<TypeUsage> parameterTypes, TypeSolver typeSolver) {
        List<MethodDeclaration> candidateMethods = new ArrayList<>();
        for (BodyDeclaration member : this.wrappedNode.getMembers()) {
            if (member instanceof com.github.javaparser.ast.body.MethodDeclaration) {
                com.github.javaparser.ast.body.MethodDeclaration method = (com.github.javaparser.ast.body.MethodDeclaration)member;
                if (method.getName().equals(name)) {
                    candidateMethods.add(new JavaParserMethodDeclaration(method));
                }
            }
        }

        if (this.wrappedNode.getExtends() != null && !this.wrappedNode.getExtends().isEmpty()) {
            if (this.wrappedNode.getExtends().size() > 1) {
                throw new UnsupportedOperationException();
            }
            String superclassName = this.wrappedNode.getExtends().get(0).getName();
            SymbolReference<TypeDeclaration> superclass = solveType(superclassName, typeSolver);
            if (!superclass.isSolved()) {
                throw new UnsolvedSymbolException(this, superclassName);
            }
            SymbolReference<MethodDeclaration> res = superclass.getCorrespondingDeclaration().getContext().solveMethod(name, parameterTypes, typeSolver);
            if (res.isSolved()) {
                candidateMethods.add(res.getCorrespondingDeclaration());
            }
        }

        return MethodResolutionLogic.findMostApplicable(candidateMethods, name, parameterTypes, typeSolver);
    }
}
