/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.api.extension;

import static org.junit.gen5.commons.meta.API.Usage.Experimental;

import org.junit.gen5.commons.meta.API;

/**
 * {@code BeforeAllCallback} defines the API for {@link Extension Extensions}
 * that wish to provide additional behavior to test containers before all tests
 * are invoked.
 *
 * <p>Concrete implementations often implement {@link AfterAllCallback} as well.
 *
 * <p>Implementations must provide a no-args constructor.
 *
 * @since 5.0
 * @see org.junit.gen5.api.BeforeAll
 * @see AfterAllCallback
 * @see BeforeEachCallback
 * @see AfterEachCallback
 * @see BeforeTestMethodCallback
 * @see AfterTestMethodCallback
 */
@FunctionalInterface
@API(Experimental)
public interface BeforeAllCallback extends Extension {

	/**
	 * Callback that is invoked once <em>before</em> all tests in the current
	 * container.
	 *
	 * @param context the current container extension context
	 */
	void beforeAll(ContainerExtensionContext context) throws Exception;

}
