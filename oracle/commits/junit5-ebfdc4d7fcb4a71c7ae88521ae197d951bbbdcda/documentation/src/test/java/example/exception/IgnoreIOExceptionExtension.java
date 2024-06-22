/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package example.exception;

import java.io.IOException;

import org.junit.gen5.api.extension.ExceptionHandler;
import org.junit.gen5.api.extension.TestExtensionContext;

// @formatter:off
// tag::user_guide[]
public class IgnoreIOExceptionExtension implements ExceptionHandler {

	@Override
	public void handleException(TestExtensionContext context, Throwable throwable)
			throws Throwable {

		if (throwable instanceof IOException) {
			return;
		}
		throw throwable;
	}
}
// end::user_guide[]
// @formatter:on
