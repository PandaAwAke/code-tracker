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

import static org.junit.gen5.commons.meta.API.Usage.Experimental;

import java.io.File;
import java.net.URI;
import java.util.Objects;

import org.junit.gen5.commons.meta.API;
import org.junit.gen5.commons.util.Preconditions;
import org.junit.gen5.commons.util.ToStringBuilder;

/**
 * @since 5.0
 */
@API(Experimental)
public class DirectorySource implements FileSystemSource {

	private static final long serialVersionUID = 1L;

	private final File directory;

	public DirectorySource(File directory) {
		this.directory = Preconditions.notNull(directory, "directory must not be null").getAbsoluteFile();
	}

	@Override
	public URI getUri() {
		return directory.toURI();
	}

	@Override
	public File getFile() {
		return directory;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringBuilder(this)
				.append("directory", directory)
				.toString();
		// @formatter:on
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DirectorySource that = (DirectorySource) o;
		return Objects.equals(directory, that.directory);
	}

	@Override
	public int hashCode() {
		return Objects.hash(directory);
	}
}
