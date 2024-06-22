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

package org.eclipse.jetty.io;

import org.eclipse.jetty.util.thread.Timeout;

public interface AsyncEndPoint extends ConnectedEndPoint
{
    /* ------------------------------------------------------------ */
    /**
     * Dispatch the endpoint to a thread to attend to it.
     * 
     */
    public void asyncDispatch();
    
    /* ------------------------------------------------------------ */
    /** Schedule a write dispatch.
     * Set the endpoint to not be writable and schedule a dispatch when
     * it becomes writable.
     */
    public void scheduleWrite();
    
    /* ------------------------------------------------------------ */
    /** Schedule a call to the idle timeout
     */
    public void scheduleIdle();   
    
    /* ------------------------------------------------------------ */
    /** Cancel a call to the idle timeout
     */
    public void cancelIdle();

    /* ------------------------------------------------------------ */
    public boolean isWritable();

    /* ------------------------------------------------------------ */
    /**
     * @return True if IO has been successfully performed since the last call to {@link #hasProgressed()}
     */
    public boolean hasProgressed();
    
    /* ------------------------------------------------------------ */
    /**
     */
    public void scheduleTimeout(Timeout.Task task, long timeoutMs);

    /* ------------------------------------------------------------ */
    /**
     */
    public void cancelTimeout(Timeout.Task task);
}
