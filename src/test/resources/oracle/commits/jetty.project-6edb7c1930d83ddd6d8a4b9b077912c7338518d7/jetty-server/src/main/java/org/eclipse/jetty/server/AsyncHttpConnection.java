package org.eclipse.jetty.server;

import java.io.IOException;

import org.eclipse.jetty.http.HttpException;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.nio.AsyncConnection;
import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class AsyncHttpConnection extends HttpConnection implements AsyncConnection
{
    private final static int NO_PROGRESS_INFO = Integer.getInteger("org.mortbay.jetty.NO_PROGRESS_INFO",100);
    private final static int NO_PROGRESS_CLOSE = Integer.getInteger("org.mortbay.jetty.NO_PROGRESS_CLOSE",200);
    
    private static final Logger LOG = Log.getLogger(AsyncHttpConnection.class);
    private int _total_no_progress;

    public AsyncHttpConnection(Connector connector, EndPoint endpoint, Server server)
    {
        super(connector,endpoint,server);
    }

    public Connection handle() throws IOException
    {
        Connection connection = this;
        boolean some_progress=false; 
        boolean progress=true; 
        
        // Loop while more in buffer
        try
        {
            setCurrentConnection(this);
            
            // While the endpoint is open 
            // AND we are not suspended
            // AND we have more characters to read OR we made some progress 
            // AND the connection has not changed
            while (_endp.isOpen() && 
                   !_request.getAsyncContinuation().isSuspending() && 
                   (_parser.isMoreInBuffer() || _endp.isBufferingInput() || progress) && 
                   connection==this)
            {
                progress=false;
                try
                {
                    // Handle resumed request
                    if (_request._async.isAsync() && !_request._async.isComplete())
                        handleRequest(); 
                    // else Parse more input
                    else if (!_parser.isComplete() && _parser.parseAvailable())
                        progress=true;

                    // Generate more output
                    if (_generator.isCommitted() && !_generator.isComplete() && !_endp.isOutputShutdown())
                        if (_generator.flushBuffer()>0)
                            progress=true;
                    
                    // Flush output from buffering endpoint
                    if (_endp.isBufferingOutput())
                        _endp.flush();
                    
                }
                catch (HttpException e)
                {
                    if (LOG.isDebugEnabled())
                    {
                        LOG.debug("uri="+_uri);
                        LOG.debug("fields="+_requestFields);
                        LOG.debug(e);
                    }
                    _generator.sendError(e.getStatus(), e.getReason(), null, true);
                    _parser.reset();
                }
                finally
                {                    
                    //  Is this request/response round complete and are fully flushed?
                    if (_parser.isComplete() && _generator.isComplete() && !_endp.isBufferingOutput())
                    {
                        // Reset the parser/generator
                        progress=true;
                        reset();
                        
                        // look for a switched connection instance?
                        if (_response.getStatus()==HttpStatus.SWITCHING_PROTOCOLS_101)
                        {
                            Connection switched=(Connection)_request.getAttribute("org.eclipse.jetty.io.Connection");
                            if (switched!=null)
                                connection=switched;
                        }
                        
                        // TODO Is this required?
                        if (!_generator.isPersistent() && !_endp.isOutputShutdown())
                        {
                            System.err.println("Safety net oshut!!!");
                            _endp.shutdownOutput();
                        }
                    }
                    
                    some_progress|=progress|((SelectChannelEndPoint)_endp).isProgressing();
                }
            }
        }
        finally
        {
            setCurrentConnection(null);
            _parser.returnBuffers();
            _generator.returnBuffers();
            
            // Safety net to catch spinning
            if (!some_progress)
            {
                _total_no_progress++;
                if (NO_PROGRESS_INFO>0 && _total_no_progress%NO_PROGRESS_INFO==0 && (NO_PROGRESS_CLOSE<=0 || _total_no_progress< NO_PROGRESS_CLOSE))
                    LOG.info("EndPoint making no progress: "+_total_no_progress+" "+_endp);
                if (NO_PROGRESS_CLOSE>0 && _total_no_progress==NO_PROGRESS_CLOSE)
                {
                    LOG.warn("Closing EndPoint making no progress: "+_total_no_progress+" "+_endp);
                    if (_endp instanceof SelectChannelEndPoint)
                        ((SelectChannelEndPoint)_endp).getChannel().close();
                }
            }
        }
        return connection;
    }

    public void onInputShutdown() throws IOException
    {
        // If we don't have a committed response and we are not suspended
        if (_generator.isIdle() && !_request.getAsyncContinuation().isSuspended())
        {
            // then no more can happen, so close.
            _endp.shutdownOutput();
        }
    }

}
