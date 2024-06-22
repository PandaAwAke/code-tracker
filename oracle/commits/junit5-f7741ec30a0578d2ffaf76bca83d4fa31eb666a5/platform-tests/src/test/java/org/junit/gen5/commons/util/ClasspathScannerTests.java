/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.commons.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.gen5.api.Assertions.assertFalse;
import static org.junit.gen5.api.Assertions.assertSame;
import static org.junit.gen5.api.Assertions.assertThrows;
import static org.junit.gen5.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.gen5.api.Test;

/**
 * Unit tests for {@link ClasspathScanner}.
 *
 * @since 5.0
 */
class ClasspathScannerTests {

	private final ClasspathScanner classpathScanner = new ClasspathScanner(ReflectionUtils::getDefaultClassLoader,
		ReflectionUtils::loadClass);

	@Test
	void findAllClassesInThisPackage() throws Exception {
		List<Class<?>> classes = classpathScanner.scanForClassesInPackage("org.junit.gen5.commons", clazz -> true);
		assertThat(classes.size()).isGreaterThanOrEqualTo(20);
		assertTrue(classes.contains(NestedClassToBeFound.class));
		assertTrue(classes.contains(MemberClassToBeFound.class));
	}

	@Test
	void findAllClassesInThisPackageWithFilter() throws Exception {
		Predicate<Class<?>> thisClassOnly = clazz -> clazz == ClasspathScannerTests.class;
		List<Class<?>> classes = classpathScanner.scanForClassesInPackage("org.junit.gen5.commons", thisClassOnly);
		assertSame(ClasspathScannerTests.class, classes.get(0));
	}

	@Test
	void scanForClassesInPackageForNullBasePackage() {
		assertThrows(PreconditionViolationException.class,
			() -> classpathScanner.scanForClassesInPackage(null, clazz -> true));
	}

	@Test
	void scanForClassesInPackageForNullClassFilter() {
		assertThrows(PreconditionViolationException.class,
			() -> classpathScanner.scanForClassesInPackage("org.junit.gen5.commons", null));
	}

	@Test
	void scanForClassesInPackageWhenIOExceptionOccurs() {
		ClasspathScanner scanner = new ClasspathScanner(new ThrowingClassLoaderSupplier(), ReflectionUtils::loadClass);
		List<Class<?>> classes = scanner.scanForClassesInPackage("org.junit.gen5.commons", clazz -> true);
		assertThat(classes).isEmpty();
	}

	@Test
	void isPackage() throws Exception {
		assertTrue(classpathScanner.isPackage("org.junit.gen5.commons"));
		assertFalse(classpathScanner.isPackage("org.doesnotexist"));
	}

	@Test
	void isPackageForNullPackageName() {
		assertThrows(PreconditionViolationException.class, () -> classpathScanner.isPackage(null));
	}

	@Test
	void isPackageWhenIOExceptionOccurs() {
		ClasspathScanner scanner = new ClasspathScanner(new ThrowingClassLoaderSupplier(), ReflectionUtils::loadClass);
		assertFalse(scanner.isPackage("org.junit.gen5.commons"));
	}

	@Test
	void findAllClassesInClasspathRoot() throws Exception {
		Predicate<Class<?>> thisClassOnly = clazz -> clazz == ClasspathScannerTests.class;
		File root = getTestClasspathRoot();
		List<Class<?>> classes = classpathScanner.scanForClassesInClasspathRoot(root, thisClassOnly);
		assertSame(ClasspathScannerTests.class, classes.get(0));
	}

	@Test
	void findAllClassesInClasspathRootWithFilter() throws Exception {
		File root = getTestClasspathRoot();
		List<Class<?>> classes = classpathScanner.scanForClassesInClasspathRoot(root, clazz -> true);

		assertThat(classes.size()).isGreaterThanOrEqualTo(20);
		assertTrue(classes.contains(ClasspathScannerTests.class));
	}

	@Test
	void findAllClassesInClasspathRootForNullRoot() throws Exception {
		assertThrows(PreconditionViolationException.class,
			() -> classpathScanner.scanForClassesInClasspathRoot(null, clazz -> true));
	}

	@Test
	void findAllClassesInClasspathRootForNonExistingRoot() throws Exception {
		assertThrows(PreconditionViolationException.class,
			() -> classpathScanner.scanForClassesInClasspathRoot(new File("does_not_exist"), clazz -> true));
	}

	@Test
	void findAllClassesInClasspathRootForNullClassFilter() throws Exception {
		assertThrows(PreconditionViolationException.class,
			() -> classpathScanner.scanForClassesInClasspathRoot(getTestClasspathRoot(), null));
	}

	private File getTestClasspathRoot() throws Exception {
		URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
		return new File(location.toURI());
	}

	class MemberClassToBeFound {
	}

	static class NestedClassToBeFound {
	}

	private static class ThrowingClassLoaderSupplier implements Supplier<ClassLoader> {

		@Override
		public ClassLoader get() {
			return new ThrowingClassLoader();
		}
	}

	private static class ThrowingClassLoader extends ClassLoader {

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			throw new IOException();
		}
	}

}
