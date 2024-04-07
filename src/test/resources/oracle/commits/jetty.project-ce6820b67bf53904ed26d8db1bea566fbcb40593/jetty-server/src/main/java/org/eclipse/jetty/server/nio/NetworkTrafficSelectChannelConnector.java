// ========================================================================
// Copyright (c) 2011 Intalio, Inc.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package org.eclipse.jetty.server.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.NetworkTrafficListener;
import org.eclipse.jetty.io.NetworkTrafficSelectChannelEndPoint;
import org.eclipse.jetty.io.SelectChannelEndPoint;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.server.SelectChannelConnector;
import org.eclipse.jetty.server.Server;

/**
 * <p>A specialized version of {@link SelectChannelConnector} that supports {@link NetworkTrafficListener}s.</p>
 * <p>{@link NetworkTrafficListener}s can be added and removed dynamically before and after this connector has
 * been started without causing {@link ConcurrentModificationException}s.</p>
 */
public class NetworkTrafficSelectChannelConnector extends SelectChannelConnector
{
    private final List<NetworkTrafficListener> listeners = new CopyOnWriteArrayList<NetworkTrafficListener>();


    public NetworkTrafficSelectChannelConnector(Server server)
    {
        super(server);
    }

    /**
     * @param listener the listener to add
     */
    public void addNetworkTrafficListener(NetworkTrafficListener listener)
    {
        listeners.add(listener);
    }

    /**
     * @param listener the listener to remove
     */
    public void removeNetworkTrafficListener(NetworkTrafficListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectorManager.ManagedSelector selectSet, SelectionKey key) throws IOException
    {
        NetworkTrafficSelectChannelEndPoint endPoint = new NetworkTrafficSelectChannelEndPoint(channel, selectSet, key, getScheduler(), getIdleTimeout(), listeners);
        endPoint.notifyOpened();
        return endPoint;
    }

    @Override
    protected void endPointClosed(EndPoint endpoint)
    {
        super.endPointClosed(endpoint);
        ((NetworkTrafficSelectChannelEndPoint)endpoint).notifyClosed();
    }
}
