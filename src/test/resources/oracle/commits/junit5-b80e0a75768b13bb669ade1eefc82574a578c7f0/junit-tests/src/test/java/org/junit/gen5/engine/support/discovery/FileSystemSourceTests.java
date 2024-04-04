/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.support.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.gen5.api.Assertions.assertThrows;

import java.io.File;

import org.junit.gen5.api.Test;
import org.junit.gen5.commons.util.PreconditionViolationException;
import org.junit.gen5.engine.support.descriptor.DirectorySource;
import org.junit.gen5.engine.support.descriptor.FilePosition;
import org.junit.gen5.engine.support.descriptor.FileSource;

class FileSystemSourceTests {

	@Test
	void nullSourceFileOrDirectoryYieldsException() {
		assertThrows(PreconditionViolationException.class, () -> {
			new FileSource(null);
		});
	}

	@Test
	void directory() {
		File directory = new File(".");
		DirectorySource source = new DirectorySource(directory);
		assertThat(source.getFile()).isEqualTo(directory.getAbsoluteFile());
	}

	@Test
	void fileWithoutPosition() throws Exception {
		File file = new File("test.txt");
		FileSource source = new FileSource(file);

		assertThat(source.getFile()).isEqualTo(file.getAbsoluteFile());
		assertThat(source.getPosition()).isEmpty();
	}

	@Test
	void fileWithPosition() throws Exception {
		File file = new File("test.txt");
		FilePosition position = new FilePosition(42, 23);
		FileSource source = new FileSource(file, position);

		assertThat(source.getFile()).isEqualTo(file.getAbsoluteFile());
		assertThat(source.getPosition()).hasValue(position);
	}
}
