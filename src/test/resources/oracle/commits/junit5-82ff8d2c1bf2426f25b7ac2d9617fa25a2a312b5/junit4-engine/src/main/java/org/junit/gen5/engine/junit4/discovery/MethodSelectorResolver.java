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

import static org.junit.gen5.engine.junit4.discovery.RunnerTestDescriptorAwareFilter.adapter;
import static org.junit.runner.manipulation.Filter.matchMethodDescription;

import java.lang.reflect.Method;

import org.junit.gen5.engine.specification.MethodSelector;
import org.junit.runner.Description;

class MethodSelectorResolver extends DiscoverySelectorResolver<MethodSelector> {

	MethodSelectorResolver() {
		super(MethodSelector.class);
	}

	@Override
	void resolve(MethodSelector selector, TestClassCollector collector) {
		Class<?> testClass = selector.getTestClass();
		Method testMethod = selector.getTestMethod();
		Description methodDescription = Description.createTestDescription(testClass, testMethod.getName());
		collector.addFiltered(testClass, adapter(matchMethodDescription(methodDescription)));
	}

}
