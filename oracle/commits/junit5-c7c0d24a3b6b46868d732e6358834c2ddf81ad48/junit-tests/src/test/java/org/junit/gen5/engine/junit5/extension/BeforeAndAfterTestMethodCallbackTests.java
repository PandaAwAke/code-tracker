/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.extension;

import static java.util.Arrays.asList;
import static org.junit.gen5.api.Assertions.assertEquals;
import static org.junit.gen5.engine.discovery.ClassSelector.forClass;
import static org.junit.gen5.launcher.main.TestDiscoveryRequestBuilder.request;

import java.util.ArrayList;
import java.util.List;

import org.junit.gen5.api.AfterEach;
import org.junit.gen5.api.BeforeEach;
import org.junit.gen5.api.Nested;
import org.junit.gen5.api.Test;
import org.junit.gen5.api.extension.AfterTestMethodCallback;
import org.junit.gen5.api.extension.BeforeTestMethodCallback;
import org.junit.gen5.api.extension.ExtendWith;
import org.junit.gen5.api.extension.TestExtensionContext;
import org.junit.gen5.engine.ExecutionEventRecorder;
import org.junit.gen5.engine.junit5.AbstractJUnit5TestEngineTests;
import org.junit.gen5.engine.junit5.JUnit5TestEngine;
import org.junit.gen5.junit4.runner.JUnit5;
import org.junit.gen5.launcher.TestDiscoveryRequest;
import org.junit.runner.RunWith;

/**
 * Integration tests that verify support for {@link BeforeTestMethodCallback},
 * {@link AfterTestMethodCallback}, {@link BeforeEach}, and {@link AfterEach}
 * in the {@link JUnit5TestEngine}.
 *
 * @since 5.0
 */
@RunWith(JUnit5.class)
public class BeforeAndAfterTestMethodCallbackTests extends AbstractJUnit5TestEngineTests {

	@BeforeEach
	void resetCallSequence() {
		callSequence.clear();
	}

	@Test
	public void beforeEachAndAfterEachCallbacks() {
		TestDiscoveryRequest request = request().select(forClass(OuterTestCase.class)).build();

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(2L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(2L, eventRecorder.getTestSuccessfulCount(), "# tests succeeded");
		assertEquals(0L, eventRecorder.getTestSkippedCount(), "# tests skipped");
		assertEquals(0L, eventRecorder.getTestAbortedCount(), "# tests aborted");
		assertEquals(0L, eventRecorder.getTestFailedCount(), "# tests failed");

		// @formatter:off
		assertEquals(asList(

			// OuterTestCase
			"beforeEachOuter",
				"fooBefore",
				"barBefore",
					"testOuter",
				"barAfter",
				"fooAfter",
			"afterEachOuter",

			// InnerTestCase
			"beforeEachOuter",
				"beforeEachInner",
					"fooBefore",
					"barBefore",
						"fizzBefore",
							"testInner",
						"fizzAfter",
					"barAfter",
					"fooAfter",
				"afterEachInner",
			"afterEachOuter"

		), callSequence, "wrong call sequence");
		// @formatter:on
	}

	@Test
	public void beforeEachAndAfterEachCallbacksDeclaredOnSuperclassAndSubclass() {
		TestDiscoveryRequest request = request().select(forClass(ChildTestCase.class)).build();

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(1L, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(1L, eventRecorder.getTestSuccessfulCount(), "# tests succeeded");
		assertEquals(0L, eventRecorder.getTestSkippedCount(), "# tests skipped");
		assertEquals(0L, eventRecorder.getTestAbortedCount(), "# tests aborted");
		assertEquals(0L, eventRecorder.getTestFailedCount(), "# tests failed");

		// @formatter:off
		assertEquals(asList(
			"fooBefore",
			"barBefore",
				"testChild",
			"barAfter",
			"fooAfter"
		), callSequence, "wrong call sequence");
		// @formatter:on
	}

	@Test
	public void beforeEachAndAfterEachCallbacksDeclaredOnInterfaceAndClass() {
		TestDiscoveryRequest request = request().select(forClass(TestInterfaceTestCase.class)).build();

		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertEquals(2, eventRecorder.getTestStartedCount(), "# tests started");
		assertEquals(2, eventRecorder.getTestSuccessfulCount(), "# tests succeeded");
		assertEquals(0, eventRecorder.getTestSkippedCount(), "# tests skipped");
		assertEquals(0, eventRecorder.getTestAbortedCount(), "# tests aborted");
		assertEquals(0, eventRecorder.getTestFailedCount(), "# tests failed");

		// @formatter:off
		assertEquals(asList(

			// Test Interface
			"fooBefore",
				"barBefore",
					"defaultTestMethod",
				"barAfter",
			"fooAfter",

			// Test Class
			"fooBefore",
				"barBefore",
					"localTestMethod",
				"barAfter",
			"fooAfter"

		), callSequence, "wrong call sequence");
		// @formatter:on
	}

	// -------------------------------------------------------------------

	private static List<String> callSequence = new ArrayList<>();

	@ExtendWith(FooTestMethodCallbacks.class)
	private static class ParentTestCase {
	}

	@ExtendWith(BarTestMethodCallbacks.class)
	private static class ChildTestCase extends ParentTestCase {

		@Test
		void test() {
			callSequence.add("testChild");
		}

	}

	@ExtendWith(FooTestMethodCallbacks.class)
	private interface TestInterface {

		@Test
		default void defaultTest() {
			callSequence.add("defaultTestMethod");
		}

	}

	@ExtendWith(BarTestMethodCallbacks.class)
	private static class TestInterfaceTestCase implements TestInterface {

		@Test
		void localTest() {
			callSequence.add("localTestMethod");
		}
	}

	@ExtendWith({ FooTestMethodCallbacks.class, BarTestMethodCallbacks.class })
	private static class OuterTestCase {

		@BeforeEach
		void beforeEach() {
			callSequence.add("beforeEachOuter");
		}

		@Test
		void testOuter() {
			callSequence.add("testOuter");
		}

		@AfterEach
		void afterEach() {
			callSequence.add("afterEachOuter");
		}

		@Nested
		@ExtendWith(FizzTestMethodCallbacks.class)
		class InnerTestCase {

			@BeforeEach
			void beforeInnerMethod() {
				callSequence.add("beforeEachInner");
			}

			@Test
			void testInner() {
				callSequence.add("testInner");
			}

			@AfterEach
			void afterInnerMethod() {
				callSequence.add("afterEachInner");
			}
		}

	}

	private static class FooTestMethodCallbacks implements BeforeTestMethodCallback, AfterTestMethodCallback {

		@Override
		public void beforeTestMethod(TestExtensionContext context) {
			callSequence.add("fooBefore");
		}

		@Override
		public void afterTestMethod(TestExtensionContext context) {
			callSequence.add("fooAfter");
		}
	}

	private static class BarTestMethodCallbacks implements BeforeTestMethodCallback, AfterTestMethodCallback {

		@Override
		public void beforeTestMethod(TestExtensionContext context) {
			callSequence.add("barBefore");
		}

		@Override
		public void afterTestMethod(TestExtensionContext context) {
			callSequence.add("barAfter");
		}
	}

	private static class FizzTestMethodCallbacks implements BeforeTestMethodCallback, AfterTestMethodCallback {

		@Override
		public void beforeTestMethod(TestExtensionContext context) {
			callSequence.add("fizzBefore");
		}

		@Override
		public void afterTestMethod(TestExtensionContext context) {
			callSequence.add("fizzAfter");
		}
	}

}
