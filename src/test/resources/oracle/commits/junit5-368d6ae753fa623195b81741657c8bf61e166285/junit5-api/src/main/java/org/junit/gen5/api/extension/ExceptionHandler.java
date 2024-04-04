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
 * {@code ExceptionHandler} defines the API for {@link Extension Extensions}
 * that wish to handle exceptions thrown from tests.
 *
 * <p>Common use cases include swallowing an exception if it's anticipated
 * or rolling back a transaction in certain error scenarios.
 *
 * <p>Implementations must provide a no-args constructor.
 *
 * @since 5.0
 */
@FunctionalInterface
@API(Experimental)
public interface ExceptionHandler extends Extension {

	/**
	 * Handle the supplied {@link Throwable throwable}.
	 *
	 * <p>Implementors must perform one of the following.
	 * <ol>
	 * <li>Swallow the supplied {@code throwable}, thereby preventing propagation.</li>
	 * <li>Rethrow the supplied {@code throwable} <em>as is</em>.</li>
	 * <li>Throw a new exception, potentially wrapping the supplied {@code throwable}.</li>
	 * </ol>
	 *
	 * <p>If the supplied {@code throwable} is swallowed, subsequent
	 * {@link ExceptionHandler ExceptionHandlers} will not be invoked; otherwise,
	 * the next registered {@code ExceptionHandler} (if there is one) will be invoked
	 * with any {@link Throwable} thrown by this handler.
	 */
	void handleException(TestExtensionContext context, Throwable throwable) throws Throwable;

}
