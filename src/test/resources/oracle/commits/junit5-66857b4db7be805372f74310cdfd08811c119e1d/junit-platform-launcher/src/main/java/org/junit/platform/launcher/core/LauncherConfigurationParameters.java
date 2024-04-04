/*
 * Copyright 2015-2017 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.platform.launcher.core;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.platform.commons.util.ClassLoaderUtils;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.ToStringBuilder;
import org.junit.platform.engine.ConfigurationParameters;

/**
 * @since 1.0
 */
class LauncherConfigurationParameters implements ConfigurationParameters {

	private static final Logger LOG = Logger.getLogger(LauncherConfigurationParameters.class.getName());

	private final Map<String, String> explicitConfigParams;
	private final Properties configParamsFromFile;

	LauncherConfigurationParameters(Map<String, String> configParams) {
		this(configParams, ConfigurationParameters.CONFIG_FILE_NAME);
	}

	LauncherConfigurationParameters(Map<String, String> configParams, String configFileName) {
		Preconditions.notNull(configParams, "configuration parameters must not be null");
		Preconditions.notBlank(configFileName, "configFileName must not be null or blank");
		this.explicitConfigParams = configParams;
		this.configParamsFromFile = fromClasspathResource(configFileName.trim());
	}

	private static Properties fromClasspathResource(String configFileName) {
		Properties props = new Properties();

		try {
			ClassLoader classLoader = ClassLoaderUtils.getDefaultClassLoader();
			List<URL> resources = Collections.list(classLoader.getResources(configFileName));

			if (!resources.isEmpty()) {
				if (resources.size() > 1) {
					LOG.warning(() -> String.format(
						"Discovered %d '%s' configuration files in the classpath; only the first will be used.",
						resources.size(), configFileName));
				}

				URL configFileUrl = resources.get(0);
				LOG.info(() -> String.format(
					"Loading JUnit Platform configuration parameters from classpath resource [%s].", configFileUrl));
				try (InputStream inputStream = configFileUrl.openStream()) {
					props.load(inputStream);
				}
			}
		}
		catch (Exception ex) {
			LOG.log(Level.WARNING, ex,
				() -> String.format(
					"Failed to load JUnit Platform configuration parameters from classpath resource [%s].",
					configFileName));
		}

		return props;
	}

	@Override
	public Optional<String> get(String key) {
		return Optional.ofNullable(getProperty(key));
	}

	@Override
	public Optional<Boolean> getBoolean(String key) {
		String property = getProperty(key);
		if (property != null) {
			return Optional.of(Boolean.parseBoolean(property));
		}
		return Optional.empty();
	}

	@Override
	public int size() {
		return this.explicitConfigParams.size();
	}

	private String getProperty(String key) {
		Preconditions.notBlank(key, "key must not be null or blank");

		// 1) Check explicit config param.
		String value = this.explicitConfigParams.get(key);

		// 2) Check system property.
		if (value == null) {
			try {
				value = System.getProperty(key);
			}
			catch (Exception ex) {
				/* ignore */
			}

			// 3) Check config file.
			if (value == null) {
				value = this.configParamsFromFile.getProperty(key);
			}
		}

		return value;
	}

	@Override
	public String toString() {
		ToStringBuilder builder = new ToStringBuilder(this);

		this.explicitConfigParams.forEach(builder::append);

		// @formatter:off
		this.configParamsFromFile.stringPropertyNames().stream()
				.filter(key -> !this.explicitConfigParams.containsKey(key))
				.forEach(key -> builder.append(key, this.configParamsFromFile.getProperty(key)));
		// @formatter:on

		return builder.toString();
	}

}
