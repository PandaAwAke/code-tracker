package me.tomassetti.symbolsolver.model.javassist;

import javassist.CtMethod;
import javassist.NotFoundException;
import me.tomassetti.symbolsolver.model.MethodDeclaration;
import me.tomassetti.symbolsolver.model.ParameterDeclaration;
import me.tomassetti.symbolsolver.model.TypeDeclaration;
import me.tomassetti.symbolsolver.model.javaparser.declarations.JavaParserParameterDeclaration;

/**
 * Created by federico on 01/08/15.
 */
public class JavassistMethodDeclaration implements MethodDeclaration {
    public JavassistMethodDeclaration(CtMethod ctMethod) {
        this.ctMethod = ctMethod;
    }

    private CtMethod ctMethod;

    @Override
    public String getName() {
        return ctMethod.getName();
    }

    @Override
    public boolean isField() {
        return false;
    }

    @Override
    public boolean isParameter() {
        return false;
    }

    @Override
    public boolean isType() {
        return false;
    }

    @Override
    public TypeDeclaration asTypeDeclaration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeDeclaration getType() {
        return new JavassistClassDeclaration(ctMethod.getDeclaringClass());
    }

    @Override
    public TypeDeclaration getReturnType() {
        try {
            return new JavassistClassDeclaration(ctMethod.getReturnType());
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getNoParams() {
        try {
            return ctMethod.getParameterTypes().length;
        } catch (NotFoundException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public ParameterDeclaration getParam(int i) {
        try {
            return new JavassistParameterDeclaration(ctMethod.getParameterTypes()[i]);
        } catch (NotFoundException e){
            throw new RuntimeException(e);
        }
    }
}
