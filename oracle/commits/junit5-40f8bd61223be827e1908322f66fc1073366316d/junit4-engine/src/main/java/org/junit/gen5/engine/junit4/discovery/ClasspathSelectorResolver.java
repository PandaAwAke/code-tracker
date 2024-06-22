/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit4.discovery;

import static org.junit.gen5.commons.util.ReflectionUtils.findAllClassesInClasspathRoot;

import org.junit.gen5.engine.discovery.ClasspathSelector;

class ClasspathSelectorResolver extends DiscoverySelectorResolver<ClasspathSelector> {

	ClasspathSelectorResolver() {
		super(ClasspathSelector.class);
	}

	@Override
	void resolve(ClasspathSelector selector, TestClassCollector collector) {
		findAllClassesInClasspathRoot(selector.getClasspathRoot(), classTester).forEach(collector::addCompletely);
	}

}
