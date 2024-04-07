// ========================================================================
// Copyright (c) 2004-2011 Mort Bay Consulting Pty. Ltd.
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
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.http.HttpGenerator;
import org.eclipse.jetty.http.HttpGenerator.Action;
import org.eclipse.jetty.http.HttpGenerator.ResponseInfo;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.AbstractAsyncConnection;
import org.eclipse.jetty.io.AsyncConnection;
import org.eclipse.jetty.io.AsyncEndPoint;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * A Connection that handles the HTTP protocol
 */
public class HttpConnection extends AbstractAsyncConnection
{
    public static final Logger LOG = Log.getLogger(HttpConnection.class);

    private static final ThreadLocal<HttpConnection> __currentConnection = new ThreadLocal<HttpConnection>();

    public static final String UPGRADE_CONNECTION_ATTR = "org.eclispe.jetty.server.HttpConnection.UPGRADE";

    private final Object _lock = this;
    private final Server _server;
    private final HttpConnector _connector;
    private final HttpParser _parser;
    private final HttpGenerator _generator;
    private final HttpChannel _channel;
    private final ByteBufferPool _bufferPool;
    private final HttpHttpInput _httpInput;
    
    private volatile Thread _thread;
    private ResponseInfo _info;
    ByteBuffer _requestBuffer=null;
    ByteBuffer _responseHeader=null;
    ByteBuffer _chunk=null;
    ByteBuffer _responseBuffer=null; 
    private int _headerBytes;
    
    
    /* ------------------------------------------------------------ */
    public static HttpConnection getCurrentConnection()
    {
        return __currentConnection.get();
    }

    /* ------------------------------------------------------------ */
    protected static void setCurrentConnection(HttpConnection connection)
    {
        __currentConnection.set(connection);
    }

    /* ------------------------------------------------------------ */
    /** Constructor
     *
     */
    public HttpConnection(HttpConnector connector, AsyncEndPoint endpoint, Server server)
    {
        super(endpoint,connector.findExecutor());
        
        _connector = connector;
        _bufferPool=_connector.getByteBufferPool();
        if (_bufferPool==null)
            new Throwable().printStackTrace();
        
        _server = server;

        _httpInput = new HttpHttpInput();
        _channel = new HttpChannelOverHttp(server);
       
        _parser = new HttpParser(_channel.getEventHandler());
        _generator = new HttpGenerator();
        _generator.setSendServerVersion(_server.getSendServerVersion());
        
        LOG.debug("New HTTP Connection {}",this);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the parser used by this connection
     */
    public HttpParser getParser()
    {
        return _parser;
    }


    /* ------------------------------------------------------------ */
    public Server getServer()
    {
        return _server;
    }

    /* ------------------------------------------------------------ */
    public Thread getThread()
    {
        return _thread;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the connector.
     */
    public Connector getConnector()
    {
        return _connector;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the HttpChannel.
     */
    public HttpChannel getHttpChannel()
    {
        return _channel;
    }

    /* ------------------------------------------------------------ */
    public void reset()
    {
        if (_generator.isPersistent())
            _parser.reset();
        else
            _parser.close();
        
        _generator.reset();
        _channel.reset();
        _httpInput.recycle();
        releaseRequestBuffer();
        if (_responseHeader!=null && !_responseHeader.hasRemaining())
        {
            _bufferPool.release(_responseHeader);
            _responseHeader=null;
        }
        if (_responseBuffer!=null && !_responseBuffer.hasRemaining())
        {    
            _bufferPool.release(_responseBuffer);
            _responseBuffer=null;
        }
        if (_chunk!=null)
            _bufferPool.release(_chunk);
        _chunk=null;
        _info=null;
    }

    
    /* ------------------------------------------------------------ */
    public HttpGenerator getGenerator()
    {
        return _generator;
    }


    /* ------------------------------------------------------------ */
    @Override
    public String toString()
    {
        return String.format("%s,g=%s,p=%s",
                super.toString(),
                _generator,
                _parser);
    }

    /* ------------------------------------------------------------ */
    @Override
    public void onOpen()
    {
        LOG.debug("Opened HTTP Connection {}",this);
        super.onOpen();
        fillInterested();
    }
    
    /* ------------------------------------------------------------ */
    private void releaseRequestBuffer()
    {
        if (_requestBuffer!=null && !_requestBuffer.hasRemaining())
        {
            _bufferPool.release(_requestBuffer);
            _requestBuffer=null;
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Parse and handle HTTP messages.
     * <p>
     * This method is normally called as the {@link AbstractAsyncConnection} onReadable callback. 
     * However, it can also be called {@link HttpChannelOverHttp#completed()} if there is unconsumed 
     * data in the _requestBuffer, as a result of resuming a suspended request when there is a pipelined 
     * request already read into the buffer.
     * <p>
     * This method will fill data and parse it until either: EOF is filled; 0 bytes are filled; 
     * the HttpChannel becomes !idle; or the connection has been changed
     */
    @Override
    public synchronized void onFillable()
    {        
        LOG.debug("{} onReadable {}",this,_channel.isIdle());
        
        try
        {
            _thread=Thread.currentThread();
            setCurrentConnection(this);
            
            // TODO try to generalize this loop into AbstractAsyncConnection
            while (true)
            {
                // Fill the request buffer with data only if it is totally empty.
                if (BufferUtil.isEmpty(_requestBuffer))
                {
                    if (_requestBuffer==null)
                        _requestBuffer=_bufferPool.acquire(_connector.getRequestHeaderSize(),false);
           
                    int filled=getEndPoint().fill(_requestBuffer);
                    
                    LOG.debug("{} filled {}",this,filled);
                                        
                    // If we failed to fill
                    if (filled==0)
                    {                        
                        // Somebody wanted to read, we didn't so schedule another attempt
                        fillInterested();
                        releaseRequestBuffer();
                        return;
                    }
                    else if (filled<0)
                    {
                        _parser.inputShutdown();
                        // We were only filling if fully consumed, so if we have 
                        // read -1 then we have nothing to parse and thus nothing that
                        // will generate a response.  If we had a suspended request pending
                        // a response or a request waiting in the buffer, we would not be here.
                        getEndPoint().shutdownOutput();
                        // buffer must be empty and the channel must be idle, so we can release.
                        releaseRequestBuffer();
                        return;
                    }
                    else
                    {
                        _headerBytes+=filled;
                    }
                }

                // Parse the buffer
                if (_parser.parseNext(_requestBuffer))
                {                    
                    // For most requests, there will not be a body, so we can try to recycle the buffer now
                    releaseRequestBuffer();
                    
                    _headerBytes=0;
                    // The parser returned true, which indicates the channel is ready 
                    // to handle a request. Call the channel and this will either handle the 
                    // request/response to completion OR if the request suspends, the channel
                    // will be left in !idle state so our outer loop will exit.
                    if (!_parser.isPersistent())
                        _generator.setPersistent(false);
                    _channel.handle();
                    
                    // Return if the channel is still processing the request
                    if (_channel.isSuspended())
                    {
                        // release buffer if no input being held
                        if (_httpInput.available()==0)
                            releaseRequestBuffer();
                        return;
                    }
                    
                    // return if the connection has been changed
                    if (getEndPoint().getAsyncConnection()!=this)
                    {
                        getEndPoint().getAsyncConnection().onOpen();
                        return;
                    }
                } 
                else if (_headerBytes>= _connector.getRequestHeaderSize())
                {
                    _parser.reset();
                    _parser.close();
                    _channel.getEventHandler().badMessage(HttpStatus.REQUEST_ENTITY_TOO_LARGE_413,null);
                }
            }
        }
        catch(IOException e)
        {
            if (_parser.isIdle())
                LOG.debug(e);
            else
                LOG.warn(this.toString(),e);
            getEndPoint().close();
        }
        catch(Exception e)
        {
            LOG.warn(this.toString(),e);
            getEndPoint().close();
        }
        finally
        {   
            _thread=null;
            setCurrentConnection(null);
        }
    }


    /* ------------------------------------------------------------ */
    @Override
    public void onClose()
    {
        _channel.onClose();
    }
    
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class HttpChannelOverHttp extends HttpChannel implements Runnable
    {
        private HttpChannelOverHttp(Server server)
        {
            super(server,HttpConnection.this,_httpInput);
        }
        
        @Override
        protected int write(ByteBuffer content) throws IOException
        {
            return generate(content,Action.PREPARE);
        }
        
        @Override
        protected void resetBuffer()
        {
            if (_responseBuffer!=null)
                BufferUtil.clear(_responseBuffer);
        }
        
        @Override
        protected void increaseContentBufferSize(int size)
        {
            if (_responseBuffer!=null && _responseBuffer.capacity()>=size)
                return;
            if (_responseBuffer==null && _connector.getResponseBufferSize()>=size)
                return;

            ByteBuffer r=_bufferPool.acquire(size,false);
            if (_responseBuffer!=null)
            {
                BufferUtil.append(_responseBuffer,r);
                _bufferPool.release(_responseBuffer);
            }
            _responseBuffer=r;
        }
        
        @Override
        protected int getContentBufferSize()
        {
            ByteBuffer buffer=_responseBuffer;
            if (buffer!=null)
                return buffer.capacity();
            
            return _connector.getResponseBufferSize();
        }
        
        @Override
        public HttpConnector getHttpConnector()
        {
            return _connector;
        }
        
        @Override
        protected void flushResponse() throws IOException
        {
            generate(null,Action.FLUSH);
        }
        
        @Override
        protected void completeResponse() throws IOException
        {
            generate(null,Action.COMPLETE);
        }
        
        
        @Override
        protected synchronized boolean commitError(int status, String reason, String content)
        {
            if (!super.commitError(status,reason,content))
            {
                // We could not send the error, so a sudden close of the connection will at least tell
                // the client something is wrong

                getEndPoint().close();
                
                if (BufferUtil.hasContent(_responseBuffer))
                {
                    BufferUtil.clear(_responseBuffer);
                    releaseRequestBuffer();
                }
                _generator.abort();
                return false;
            }
            return true;
        }

        @Override
        protected synchronized void completed()
        {
            // This is called by HttpChannel#handle when it knows that it's handling of the request/response cycle 
            // is complete.  This may be in the original thread dispatched to the connection that has called process from
            // the connection#onReadable method, or it may be from a thread dispatched to call process as the result
            // of a resumed suspended request.
            // At this point the HttpChannel will have completed the generation of any response (although it might remain to
            // be asynchronously flushed TBD), but it may not have consumed the entire 
            
            LOG.debug("{} completed");
                
            
            // Handle connection upgrades
            if (getResponse().getStatus()==HttpStatus.SWITCHING_PROTOCOLS_101)
            {
                AsyncConnection connection=(AsyncConnection)getRequest().getAttribute(UPGRADE_CONNECTION_ATTR);
                if (connection!=null)
                {
                    LOG.debug("Upgrade from {} to {}",this,connection);
                    getEndPoint().setAsyncConnection(connection);
                    HttpConnection.this.reset();
                    return;
                }
            }
            
            
            // Reset everything for the next cycle.
            HttpConnection.this.reset();
            
            // are called from non connection thread (ie dispatched from a resume)
            if (getThread()!=Thread.currentThread())
            {                
                if (_parser.isStart())
                {
                    // it wants to eat more
                    if (_requestBuffer==null)
                        fillInterested();
                    else if (getConnector().isStarted())
                    {
                        LOG.debug("{} pipelined",this);
                        
                        try
                        {
                            execute(this);
                        }
                        catch(RejectedExecutionException e)
                        {
                            if (getConnector().isStarted())
                                LOG.warn(e);
                            else
                                LOG.ignore(e);
                            getEndPoint().close();
                        }
                    }
                    else
                        getEndPoint().close();
                }

                if (_parser.isClosed()&&!getEndPoint().isOutputShutdown())
                {
                    // TODO This is a catch all indicating some protocol handling failure
                    // Currently needed for requests saying they are HTTP/2.0.
                    // This should be removed once better error handling is in place
                    LOG.warn("Endpoint output not shutdown when seeking EOF");
                    getEndPoint().shutdownOutput();
                }
            }
        }

        /* ------------------------------------------------------------ */
        @Override
        public void run()
        {
            if (getThread()!=null)
            {
                // dispatched thread is still executing, try again later
                // TODO - this probably should not be able to occur as the resume dispatch is not done until an unhandle
                LOG.warn("Dispatch while dispatched???");
                execute(this);
            }
            else
                onFillable();
        }
        
        /* ------------------------------------------------------------ */
        private int generate(ByteBuffer content, Action action) throws IOException
        {
            long prepared_before=0;
            long prepared_after;
            synchronized(_lock)
            {
                try
                {
                    if (_generator.isComplete())
                    {
                        if (Action.COMPLETE==action)
                            return 0;
                        throw new EofException();
                    }

                    prepared_before=_generator.getContentPrepared();
                    loop: while (true)
                    {
                        HttpGenerator.Result result=_generator.generate(_info,_responseHeader,_chunk,_responseBuffer,content,action);
                        if (LOG.isDebugEnabled())
                            LOG.debug("{} generate: {} ({},{},{})@{}",
                                    this,
                                    result,
                                    BufferUtil.toSummaryString(_responseHeader),
                                    BufferUtil.toSummaryString(_responseBuffer),
                                    BufferUtil.toSummaryString(content),
                                    _generator.getState());
                        
                        switch(result)
                        {
                            case NEED_INFO:
                                if (_info==null)
                                    _info=_channel.getEventHandler().commit();
                                LOG.debug("{} Gcommit {}",this,_info);
                                if (_responseHeader==null)
                                    _responseHeader=_bufferPool.acquire(_connector.getResponseHeaderSize(),false);
                                continue;
                                
                            case NEED_HEADER:
                                _responseHeader=_bufferPool.acquire(_connector.getResponseHeaderSize(),false);
                                continue;

                            case NEED_BUFFER:
                                _responseBuffer=_bufferPool.acquire(_connector.getResponseBufferSize(),false);
                                continue;

                            case NEED_CHUNK:
                                _responseHeader=null;
                                _chunk=_bufferPool.acquire(HttpGenerator.CHUNK_SIZE,false);
                                continue;

                            case FLUSH:
                                if (_info.isHead())
                                {
                                    if (_chunk!=null)
                                        BufferUtil.clear(_chunk);
                                    if (_responseBuffer!=null)
                                        BufferUtil.clear(_responseBuffer);
                                }
                                write(_responseHeader,_chunk,_responseBuffer).get();
                                continue;

                            case FLUSH_CONTENT:
                                if (_info.isHead())
                                {
                                    if (_chunk!=null)
                                        BufferUtil.clear(_chunk);
                                    if (_responseBuffer!=null)
                                        BufferUtil.clear(content);
                                }
                                write(_responseHeader,_chunk,content).get();
                                break;

                            case SHUTDOWN_OUT:
                                getEndPoint().shutdownOutput();
                                break loop;

                            case OK:
                                if (!BufferUtil.hasContent(content))
                                    break loop;
                        }
                    }
                }
                catch(InterruptedException e)
                {
                    LOG.debug(e);
                }
                catch(ExecutionException e)
                {
                    LOG.debug(e);
                    if (e.getCause() instanceof IOException)
                        throw (IOException)e.getCause();
                    throw new RuntimeException(e);
                }
                finally
                {
                    prepared_after=_generator.getContentPrepared();
                }
            }
            return (int)(prepared_after-prepared_before);
        }

        @Override
        protected void commit(ResponseInfo info, ByteBuffer content) throws IOException
        {            
            _info=info;

            LOG.debug("{} commit {}",this,_info);
            
            synchronized (_lock)
            {
                try
                {
                    if (_generator.isCommitted())
                        throw new IllegalStateException("committed");
                    if (BufferUtil.hasContent(_responseBuffer))
                        throw new IllegalStateException("!empty");
                    if (_generator.isComplete())
                        throw new EofException();

                    loop: while (true)
                    {
                        HttpGenerator.Result result=_generator.generate(_info,_responseHeader,null,_responseBuffer,content,Action.COMPLETE);
                        if (LOG.isDebugEnabled())
                            LOG.debug("{} commit: {} ({},{},{})@{}",
                                    this,
                                    result,
                                    BufferUtil.toDetailString(_responseHeader),
                                    BufferUtil.toSummaryString(_responseBuffer),
                                    BufferUtil.toSummaryString(content),
                                    _generator.getState());

                        switch(result)
                        {
                            case NEED_INFO:
                                _info=_channel.getEventHandler().commit();
                                if (_responseHeader==null)
                                    _responseHeader=_bufferPool.acquire(_connector.getResponseHeaderSize(),false);
                                break;
                                
                            case NEED_HEADER:
                                _responseHeader=_bufferPool.acquire(_connector.getResponseHeaderSize(),false);
                                break;

                            case NEED_BUFFER:
                                _responseBuffer=_bufferPool.acquire(_connector.getResponseBufferSize(),false);
                                break;

                            case NEED_CHUNK:
                                throw new IllegalStateException("!chunk when content length known");

                            case FLUSH:
                                if (_info.isHead())
                                {
                                    if (_chunk!=null)
                                        BufferUtil.clear(_chunk);
                                    if (_responseBuffer!=null)
                                        BufferUtil.clear(_responseBuffer);
                                }
                                write(_responseHeader,_chunk,_responseBuffer).get();
                                break;

                            case FLUSH_CONTENT:
                                if (_info.isHead())
                                {
                                    if (_chunk!=null)
                                        BufferUtil.clear(_chunk);
                                    if (_responseBuffer!=null)
                                        BufferUtil.clear(content);
                                }
                                // TODO need a proper call back to complete.
                                write(_responseHeader,_chunk,content);
                                break loop;

                            case SHUTDOWN_OUT:
                                getEndPoint().shutdownOutput();
                                break loop;

                            case OK:
                                if (_info!=null && _info.isInformational())
                                    _info=null;
                                break loop;
                        }
                    }
                }
                catch(InterruptedException e)
                {
                    LOG.debug(e);
                }
                catch(ExecutionException e)
                {
                    LOG.debug(e);
                    FutureCallback.rethrow(e);
                }
            }
            
        }

        @Override
        public Timer getTimer()
        {
            return _connector.getTimer();
        }

        @Override
        protected void execute(Runnable task)
        {
            _connector.findExecutor().execute(task);
        }

        private FutureCallback<Void> write(ByteBuffer b0,ByteBuffer b1,ByteBuffer b2)
        {
            FutureCallback<Void> fcb=new FutureCallback<>();
            if (BufferUtil.hasContent(b0))
            {
                if (BufferUtil.hasContent(b1))
                {
                    if (BufferUtil.hasContent(b2))
                        getEndPoint().write(null,fcb,b0,b1,b2);
                    else
                        getEndPoint().write(null,fcb,b0,b1);
                }
                else
                {
                    if (BufferUtil.hasContent(b2))
                        getEndPoint().write(null,fcb,b0,b2);
                    else
                        getEndPoint().write(null,fcb,b0);
                }
            }
            else
            {
                if (BufferUtil.hasContent(b1))
                {
                    if (BufferUtil.hasContent(b2))
                        getEndPoint().write(null,fcb,b1,b2);
                    else
                        getEndPoint().write(null,fcb,b1);
                }
                else
                {
                    if (BufferUtil.hasContent(b2))
                        getEndPoint().write(null,fcb,b2);
                    else
                        fcb.completed(null);
                }
            }
            return fcb;
        }

    };
    
    private class HttpHttpInput extends HttpInput
    {
        @Override
        protected void blockForContent() throws IOException
        {
            /* We extend the blockForContent method to replace the
            default implementation of a blocking queue with an implementation
            that uses the calling thread to block on a readable callback and
            then to do the parsing before before attempting the read.
            */
                        
            // While progress and the connection has not changed
            boolean parsed_event=_parser.parseNext(_requestBuffer==null?BufferUtil.EMPTY_BUFFER:_requestBuffer);
            while (!parsed_event && !getEndPoint().isInputShutdown())
            {
                try
                {                    
                    // Do we have content ready to parse?
                    if (BufferUtil.isEmpty(_requestBuffer))
                    {
                        // Wait until we can read
                        FutureCallback<Void> block=new FutureCallback<>();
                        getEndPoint().fillInterested(null,block);
                        LOG.debug("{} block readable on {}",this,block);
                        block.get();

                        // We will need a buffer to read into
                        if (_requestBuffer==null)
                            _requestBuffer=_bufferPool.acquire(_connector.getRequestBufferSize(),false);

                        int filled=getEndPoint().fill(_requestBuffer);
                        LOG.debug("{} block filled {}",this,filled);
                        if (filled<0)
                            _parser.inputShutdown();
                    }
                    
                    // If we parse to an event, return
                    while (BufferUtil.hasContent(_requestBuffer) && _parser.inContentState())
                            parsed_event|=_parser.parseNext(_requestBuffer);
                    if (parsed_event)
                        return;
                }
                catch (InterruptedException e)
                {
                    LOG.debug(e);
                }
                catch (ExecutionException e)
                {
                    LOG.debug(e);
                    FutureCallback.rethrow(e);
                }
            }
        }

        @Override
        public int available()
        {
            int available=super.available();
            if (available==0 && _parser.isInContent() && BufferUtil.hasContent(_requestBuffer))
                return 1;
            return available;
        }

        @Override
        public void consumeAll()
        {
            // Consume content only if the connection is persistent
            if (!_generator.isPersistent())
            {
                _parser.setState(HttpParser.State.CLOSED);
                synchronized (_inputQ.lock())
                {
                    _inputQ.clear();
                }
            }
            else
            {
                while (true)
                {
                    synchronized (_inputQ.lock())
                    {
                        _inputQ.clear();
                    }
                    if (_parser.isComplete() || _parser.isClosed())
                        return;
                    try
                    {
                        blockForContent();
                    }
                    catch(IOException e)
                    {
                        LOG.warn(e);
                    }
                }
            }
        }

        
        @Override
        protected void onContentQueued(ByteBuffer ref)
        {
            /* This callback could be used to tell the connection
             * that the request did contain content and thus the request
             * buffer needs to be held until a call to #onAllContentConsumed
             * 
             * However it turns out that nothing is needed here because either a
             * request will have content, in which case the request buffer will be
             * released by a call to onAllContentConsumed; or it will not have content.
             * If it does not have content, either it will complete quickly and the 
             * buffers will be released in completed() or it will be suspended and 
             * onReadable() contains explicit handling to release if it is suspended.
             * 
             * We extend this method anyway, to turn off the notify done by the 
             * default implementation as this is not needed by our implementation
             * of blockForContent
             */
        }


        @Override
        protected void onAllContentConsumed()
        {
            /* This callback tells the connection that all content that has
             * been parsed has been consumed. Thus the request buffer may be
             * released if it is empty.
             */
            releaseRequestBuffer();
        }
    }
}
