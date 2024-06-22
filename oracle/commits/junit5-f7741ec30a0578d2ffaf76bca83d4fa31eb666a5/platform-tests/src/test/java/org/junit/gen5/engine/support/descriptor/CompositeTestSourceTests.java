/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.support.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.gen5.api.Assertions.assertThrows;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.gen5.api.Test;
import org.junit.gen5.commons.util.PreconditionViolationException;
import org.junit.gen5.engine.TestSource;

/**
 * Unit tests for {@link CompositeTestSource}.
 *
 * @since 5.0
 */
class CompositeTestSourceTests extends AbstractTestSourceTests {

	@Test
	void createCompositeTestSourceFromNullList() {
		assertThrows(PreconditionViolationException.class, () -> new CompositeTestSource(null));
	}

	@Test
	void createCompositeTestSourceFromEmptyList() {
		assertThrows(PreconditionViolationException.class, () -> new CompositeTestSource(Collections.emptyList()));
	}

	@Test
	void createCompositeTestSourceFromClassAndFileSources() {
		FileSource fileSource = new FileSource(new File("example.test"));
		JavaClassSource classSource = new JavaClassSource(getClass());
		List<TestSource> sources = new ArrayList<>(Arrays.asList(fileSource, classSource));
		CompositeTestSource compositeTestSource = new CompositeTestSource(sources);

		assertThat(compositeTestSource.getSources().size()).isEqualTo(2);
		assertThat(compositeTestSource.getSources()).contains(fileSource, classSource);

		// Ensure the supplied sources list was defensively copied.
		sources.remove(1);
		assertThat(compositeTestSource.getSources().size()).isEqualTo(2);

		// Ensure the returned sources list is immutable.
		assertThrows(UnsupportedOperationException.class, () -> compositeTestSource.getSources().add(fileSource));
	}

	@Test
	void equalsAndHashCode() {
		List<TestSource> sources1 = Arrays.asList(new JavaClassSource(Number.class));
		List<TestSource> sources2 = Arrays.asList(new JavaClassSource(String.class));
		assertEqualsAndHashCode(new CompositeTestSource(sources1), new CompositeTestSource(sources1),
			new CompositeTestSource(sources2));
	}

}
