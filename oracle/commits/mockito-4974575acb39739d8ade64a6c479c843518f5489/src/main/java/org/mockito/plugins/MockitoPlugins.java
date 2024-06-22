package org.mockito.plugins;

import org.mockito.MockitoFramework;

/**
 * Instance of this interface is available via {@link MockitoFramework#getPlugins()}.
 *
 * TODO document example use and why this interface was introduced.
 *
 * @since 2.9.0
 */
public interface MockitoPlugins {

    /**
     * Returns the default plugin implementation used by Mockito.
     * Mockito plugins are stateless so it is recommended to keep hold of the returned plugin implementation
     * rather than calling this method multiple times.
     * Each time this method is called, new instance of the plugin is created.
     *
     * @param pluginType
     * @return the plugin instance
     * @since 2.9.0
     */
    <T> T getDefaultPlugin(Class<T> pluginType);

    /**
     * Returns inline mock maker, an optional mock maker that is bundled with Mockito distribution.
     * This method is needed because {@link #getDefaultPlugin(Class)} does not provide an instance of inline mock maker.
     *
     * @return instance of inline mock maker
     * @since 2.9.0
     */
    MockMaker getInlineMockMaker();
}
