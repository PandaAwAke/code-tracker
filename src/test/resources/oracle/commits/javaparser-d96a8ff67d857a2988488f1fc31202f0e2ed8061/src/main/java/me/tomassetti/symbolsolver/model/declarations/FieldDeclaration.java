package me.tomassetti.symbolsolver.model.declarations;

import me.tomassetti.symbolsolver.model.typesystem.TypeUsage;

/**
 * @author Federico Tomassetti
 */
public interface FieldDeclaration extends ValueDeclaration {

    @Override
    default boolean isField() {
        return true;
    }

    @Override
    default FieldDeclaration asField() {
        return this;
    }

    default FieldDeclaration replaceType(TypeUsage fieldType) {
        throw new UnsupportedOperationException();
    }
}
