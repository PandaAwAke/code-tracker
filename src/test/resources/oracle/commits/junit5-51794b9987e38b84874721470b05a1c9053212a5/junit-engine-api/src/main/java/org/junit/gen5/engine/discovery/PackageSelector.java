/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.discovery;

import org.junit.gen5.engine.DiscoverySelector;

/**
 * @since 5.0
 */
public class PackageSelector implements DiscoverySelector {

	public static PackageSelector forPackageName(String packageName) {
		return new PackageSelector(packageName);
	}

	private final String packageName;

	private PackageSelector(String packageName) {
		this.packageName = packageName;
	}

	public String getPackageName() {
		return packageName;
	}

}
