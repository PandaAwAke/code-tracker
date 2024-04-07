// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.server;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.AsyncConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.StandardByteBufferPool;
import org.eclipse.jetty.util.Name;
import org.eclipse.jetty.util.component.AggregateLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Abstract Connector implementation. This abstract implementation of the Connector interface provides:
 * <ul>
 * <li>AbstractLifeCycle implementation</li>
 * <li>Implementations for connector getters and setters</li>
 * <li>Buffer management</li>
 * <li>Socket configuration</li>
 * <li>Base acceptor thread</li>
 * <li>Optional reverse proxy headers checking</li>
 * </ul>
 *
 *
 */
public abstract class AbstractConnector extends AggregateLifeCycle implements Connector, Dumpable
{
    static final Logger LOG = Log.getLogger(AbstractConnector.class);

    private final Thread[] _acceptors;

    private String _name;
    private Server _server;
    private Executor _executor;
    private String _host;
    private int _port = 0;
    private int _acceptQueueSize = 0;
    private int _acceptorPriorityOffset = 0;
    private boolean _reuseAddress = true;
    private ByteBufferPool _byteBufferPool=new StandardByteBufferPool(); // TODO should this be server wide? or a thread local one?

    private final Statistics _stats = new ConnectionStatistics();

    protected int _maxIdleTime = 200000;
    protected int _soLingerTime = -1;



    /* ------------------------------------------------------------ */
    /**
     */
    public AbstractConnector()
    {
        this(Math.max(1,(Runtime.getRuntime().availableProcessors())/4));
    }

    /* ------------------------------------------------------------ */
    /**
     */
    public AbstractConnector(@Name("acceptors") int acceptors)
    {
        if (acceptors > 2 * Runtime.getRuntime().availableProcessors())
            LOG.warn("Acceptors should be <=2*availableProcessors: " + this);
        _acceptors=new Thread[acceptors];
    }

    /* ------------------------------------------------------------ */
    @Override
    public Statistics getStatistics()
    {
        return _stats;
    }

    /* ------------------------------------------------------------ */
    /*
     */
    @Override
    public Server getServer()
    {
        return _server;
    }

    /* ------------------------------------------------------------ */
    public void setServer(Server server)
    {
        _server = server;
    }

    /* ------------------------------------------------------------ */
    public Executor findExecutor()
    {
        if (_executor==null && getServer()!=null)
            return getServer().getThreadPool();
        return _executor;
    }

    /* ------------------------------------------------------------ */
    @Override
    public Executor getExecutor()
    {
        return _executor;
    }

    /* ------------------------------------------------------------ */
    public void setExecutor(Executor executor)
    {
        removeBean(_executor);
        _executor=executor;
        addBean(_executor);
    }

    /* ------------------------------------------------------------ */
    @Override
    public ByteBufferPool getByteBufferPool()
    {
        return _byteBufferPool;
    }

    /* ------------------------------------------------------------ */
    public void setByteBufferPool(ByteBufferPool byteBufferPool)
    {
        removeBean(byteBufferPool);
        _byteBufferPool = byteBufferPool;
        addBean(_byteBufferPool);
    }

    /* ------------------------------------------------------------ */
    public void setHost(String host)
    {
        if (this instanceof NetConnector)
            _host = host;
        else
            throw new UnsupportedOperationException();
    }

    /* ------------------------------------------------------------ */
    public String getHost()
    {
        return _host;
    }

    /* ------------------------------------------------------------ */
    public void setPort(int port)
    {
        if (this instanceof NetConnector)
            _port = port;
        else
            throw new UnsupportedOperationException();
    }

    /* ------------------------------------------------------------ */
    public int getPort()
    {
        return _port;
    }

    /* ------------------------------------------------------------ */
    public void open() throws IOException
    {
    }

    /* ------------------------------------------------------------ */
    public void close() throws IOException
    {
    }

    /* ------------------------------------------------------------ */
    public int getLocalPort()
    {
        return -1;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the maxIdleTime.
     */
    @Override
    public long getMaxIdleTime()
    {
        return _maxIdleTime;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the maximum Idle time for a connection, which roughly translates to the {@link Socket#setSoTimeout(int)} call, although with NIO implementations
     * other mechanisms may be used to implement the timeout. The max idle time is applied:
     * <ul>
     * <li>When waiting for a new request to be received on a connection</li>
     * <li>When reading the headers and content of a request</li>
     * <li>When writing the headers and content of a response</li>
     * </ul>
     * Jetty interprets this value as the maximum time between some progress being made on the connection. So if a single byte is read or written, then the
     * timeout (if implemented by jetty) is reset. However, in many instances, the reading/writing is delegated to the JVM, and the semantic is more strictly
     * enforced as the maximum time a single read/write operation can take. Note, that as Jetty supports writes of memory mapped file buffers, then a write may
     * take many 10s of seconds for large content written to a slow device.
     * <p>
     * Previously, Jetty supported separate idle timeouts and IO operation timeouts, however the expense of changing the value of soTimeout was significant, so
     * these timeouts were merged. With the advent of NIO, it may be possible to again differentiate these values (if there is demand).
     *
     * @param maxIdleTime
     *            The maxIdleTime to set.
     */
    public void setMaxIdleTime(int maxIdleTime)
    {
        _maxIdleTime = maxIdleTime;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the soLingerTime.
     */
    public int getSoLingerTime()
    {
        return _soLingerTime;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the acceptQueueSize.
     */
    public int getAcceptQueueSize()
    {
        return _acceptQueueSize;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param acceptQueueSize
     *            The acceptQueueSize to set.
     */
    public void setAcceptQueueSize(int acceptQueueSize)
    {
        _acceptQueueSize = acceptQueueSize;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the number of acceptor threads.
     */
    public int getAcceptors()
    {
        return _acceptors.length;
    }


    /* ------------------------------------------------------------ */
    /**
     * @param soLingerTime
     *            The soLingerTime to set or -1 to disable.
     */
    public void setSoLingerTime(int soLingerTime)
    {
        _soLingerTime = soLingerTime;
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStart() throws Exception
    {
        if (_server == null)
            throw new IllegalStateException("No server");

        if (_name==null)
            _name = (getHost() == null?"0.0.0.0":getHost()) + ":" + getPort();

        // open listener port
        open();

        _name=_name+"/"+getLocalPort();

        super.doStart();

        // Start selector thread
        synchronized (this)
        {
            for (int i = 0; i < _acceptors.length; i++)
                findExecutor().execute(new Acceptor(i));
        }

        LOG.info("Started {}",this);
    }

    /* ------------------------------------------------------------ */
    @Override
    protected void doStop() throws Exception
    {
        try
        {
            close();
        }
        catch (IOException e)
        {
            LOG.warn(e);
        }

        super.doStop();

        for (Thread thread : _acceptors)
        {
            if (thread != null)
                thread.interrupt();
        }

        int i=_name.lastIndexOf("/");
        if (i>0)
            _name=_name.substring(0,i);
    }

    /* ------------------------------------------------------------ */
    public void join() throws InterruptedException
    {
        for (Thread thread : _acceptors)
            if (thread != null)
                thread.join();
    }

    /* ------------------------------------------------------------ */
    protected void configure(Socket socket)
    {
        try
        {
            socket.setTcpNoDelay(true);
            if (_soLingerTime >= 0)
                socket.setSoLinger(true,_soLingerTime / 1000);
            else
                socket.setSoLinger(false,0);
        }
        catch (Exception e)
        {
            LOG.ignore(e);
        }
    }

    /* ------------------------------------------------------------ */
    protected abstract void accept(int acceptorID) throws IOException, InterruptedException;

    /* ------------------------------------------------------------ */
    @Override
    public String toString()
    {
        return String.format("%s@%s:%d",
                getClass().getSimpleName(),
                getHost()==null?"0.0.0.0":getHost(),
                getLocalPort()<=0?getPort():getLocalPort());
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class Acceptor implements Runnable
    {
        int _acceptor = 0;

        Acceptor(int id)
        {
            _acceptor = id;
        }

        /* ------------------------------------------------------------ */
        @Override
        public void run()
        {
            Thread current = Thread.currentThread();
            String name;
            synchronized (AbstractConnector.this)
            {
                if (!isRunning())
                    return;

                _acceptors[_acceptor] = current;
                name = _acceptors[_acceptor].getName();
                current.setName(name + " Acceptor" + _acceptor + " " + AbstractConnector.this);
            }
            int old_priority = current.getPriority();

            try
            {
                current.setPriority(old_priority - _acceptorPriorityOffset);
                while (isRunning() && getTransport() != null)
                {
                    try
                    {
                        accept(_acceptor);
                    }
                    catch (IOException | InterruptedException e)
                    {
                        LOG.ignore(e);
                    }
                    catch (Throwable e)
                    {
                        LOG.warn(e);
                    }
                }
            }
            finally
            {
                current.setPriority(old_priority);
                current.setName(name);

                synchronized (AbstractConnector.this)
                {
                    _acceptors[_acceptor] = null;
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    @Override
    public String getName()
    {
        return _name;
    }

    /* ------------------------------------------------------------ */
    public void setName(String name)
    {
        _name = name;
    }

    /* ------------------------------------------------------------ */
    protected void connectionOpened(AsyncConnection connection)
    {
        _stats.connectionOpened();
    }

    /* ------------------------------------------------------------ */
    protected void connectionUpgraded(AsyncConnection oldConnection, AsyncConnection newConnection)
    {
        long duration = System.currentTimeMillis() - oldConnection.getEndPoint().getCreatedTimeStamp();
        int requests = (oldConnection instanceof HttpConnection)?((HttpConnection)oldConnection).getHttpChannel().getRequests():0;
        _stats.connectionUpgraded(duration,requests,requests);
    }

    /* ------------------------------------------------------------ */
    protected void connectionClosed(AsyncConnection connection)
    {
        long duration = System.currentTimeMillis() - connection.getEndPoint().getCreatedTimeStamp();
        // TODO: remove casts to HttpConnection
        int requests = (connection instanceof HttpConnection)?((HttpConnection)connection).getHttpChannel().getRequests():0;
        _stats.connectionClosed(duration,requests,requests);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return True if the the server socket will be opened in SO_REUSEADDR mode.
     */
    public boolean getReuseAddress()
    {
        return _reuseAddress;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param reuseAddress
     *            True if the the server socket will be opened in SO_REUSEADDR mode.
     */
    public void setReuseAddress(boolean reuseAddress)
    {
        _reuseAddress = reuseAddress;
    }
}
