/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2016 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package com.github.javaparser.ast.type;

import com.github.javaparser.Range;
import com.github.javaparser.ast.AllFieldsConstructor;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.VoidVisitor;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.github.javaparser.metamodel.WildcardTypeMetaModel;
import com.github.javaparser.metamodel.JavaParserMetaModel;

/**
 * A wildcard type argument.
 * <br/><code>void printCollection(Collection&lt;<b>?</b>> c) { ... }</code>
 * <br/><code>boolean addAll(Collection&lt;<b>? extends E</b>> c)</code>
 * <br/><code>Reference(T referent, ReferenceQueue&lt;<b>? super T</b>> queue)</code>
 *
 * @author Julio Vilmar Gesser
 */
public final class WildcardType extends Type implements NodeWithAnnotations<WildcardType> {

    private ReferenceType extendedTypes;

    private ReferenceType superTypes;

    public WildcardType() {
        this(null, null, null);
    }

    public WildcardType(final ReferenceType extendedTypes) {
        this(null, extendedTypes, null);
    }

    @AllFieldsConstructor
    public WildcardType(final ReferenceType extendedTypes, final ReferenceType superTypes) {
        this(null, extendedTypes, superTypes);
    }

    public WildcardType(final Range range, final ReferenceType extendedTypes, final ReferenceType superTypes) {
        super(range, new NodeList<>());
        setExtendedTypes(extendedTypes);
        setSuperTypes(superTypes);
    }

    @Override
    public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(final VoidVisitor<A> v, final A arg) {
        v.visit(this, arg);
    }

    public Optional<ReferenceType> getExtendedTypes() {
        return Optional.ofNullable(extendedTypes);
    }

    public Optional<ReferenceType> getSuperTypes() {
        return Optional.ofNullable(superTypes);
    }

    /**
     * Sets the extends
     *
     * @param ext the extends, can be null
     * @return this, the WildcardType
     */
    public WildcardType setExtendedTypes(final ReferenceType extendedTypes) {
        notifyPropertyChange(ObservableProperty.EXTENDED_TYPES, this.extendedTypes, extendedTypes);
        if (this.extendedTypes != null)
            this.extendedTypes.setParentNode(null);
        this.extendedTypes = extendedTypes;
        setAsParentNodeOf(extendedTypes);
        return this;
    }

    /**
     * Sets the super
     *
     * @param sup the super, can be null
     * @return this, the WildcardType
     */
    public WildcardType setSuperTypes(final ReferenceType superTypes) {
        notifyPropertyChange(ObservableProperty.SUPER_TYPES, this.superTypes, superTypes);
        if (this.superTypes != null)
            this.superTypes.setParentNode(null);
        this.superTypes = superTypes;
        setAsParentNodeOf(superTypes);
        return this;
    }

    @Override
    public WildcardType setAnnotations(NodeList<AnnotationExpr> annotations) {
        return (WildcardType) super.setAnnotations(annotations);
    }

    @Override
    public List<NodeList<?>> getNodeLists() {
        return Arrays.asList(getAnnotations());
    }

    @Override
    public boolean remove(Node node) {
        if (node == null)
            return false;
        if (extendedTypes != null) {
            if (node == extendedTypes) {
                removeExtendedTypes();
                return true;
            }
        }
        if (superTypes != null) {
            if (node == superTypes) {
                removeSuperTypes();
                return true;
            }
        }
        return super.remove(node);
    }

    public WildcardType removeExtendedTypes() {
        return setExtendedTypes((ReferenceType) null);
    }

    public WildcardType removeSuperTypes() {
        return setSuperTypes((ReferenceType) null);
    }

    @Override
    public WildcardType clone() {
        return (WildcardType) accept(new CloneVisitor(), null);
    }

    @Override
    public WildcardTypeMetaModel getMetaModel() {
        return JavaParserMetaModel.wildcardTypeMetaModel;
    }
}

