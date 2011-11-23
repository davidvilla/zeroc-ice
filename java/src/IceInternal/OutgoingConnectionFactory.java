// **********************************************************************
//
// Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

package IceInternal;

public final class OutgoingConnectionFactory
{
    interface CreateConnectionCallback
    {
        void setConnection(Ice.ConnectionI connection, boolean compress);
        void setException(Ice.LocalException ex);
    }

    public synchronized void
    destroy()
    {
        if(_destroyed)
        {
            return;
        }

        java.util.Iterator<java.util.List<Ice.ConnectionI> > p = _connections.values().iterator();
        while(p.hasNext())
        {
            java.util.List<Ice.ConnectionI> connectionList = p.next();
                
            java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
            while(q.hasNext())
            {
                Ice.ConnectionI connection = q.next();
                connection.destroy(Ice.ConnectionI.CommunicatorDestroyed);
            }
        }

        _destroyed = true;
        notifyAll();
    }

    public void
    waitUntilFinished()
    {
        java.util.Map<ConnectorInfo, java.util.List<Ice.ConnectionI> > connections = null;

        synchronized(this)
        {
            //
            // First we wait until the factory is destroyed. We also
            // wait until there are no pending connections
            // anymore. Only then we can be sure the _connections
            // contains all connections.
            //
            while(!_destroyed || !_pending.isEmpty() || _pendingConnectCount > 0)
            {
                try
                {
                    wait();
                }
                catch(InterruptedException ex)
                {
                }
            }

            //
            // We want to wait until all connections are finished outside the
            // thread synchronization.
            //
            if(_connections != null)
            {
                connections =
                    new java.util.HashMap<ConnectorInfo, java.util.List<Ice.ConnectionI> >(_connections);
            }
        }
        
        //
        // Now we wait until the destruction of each connection is finished.
        //
        java.util.Iterator<java.util.List<Ice.ConnectionI> > p = connections.values().iterator();
        while(p.hasNext())
        {
            java.util.List<Ice.ConnectionI> connectionList = p.next();
                
            java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
            while(q.hasNext())
            {
                Ice.ConnectionI connection = q.next();
                connection.waitUntilFinished();
            }
        }

        synchronized(this)
        {
            //
            // For consistency with C#, we set _connections to null rather than to a
            // new empty list so that our finalizer does not try to invoke any
            // methods on member objects.
            //
            _connections = null;
            _connectionsByEndpoint = null;
        }
    }

    public Ice.ConnectionI
    create(EndpointI[] endpts, boolean hasMore, Ice.EndpointSelectionType selType, Ice.BooleanHolder compress)
    {
        assert(endpts.length > 0);

        //
        // Apply the overrides.
        //
        java.util.List<EndpointI> endpoints = applyOverrides(endpts);

        //
        // Try to find a connection to one of the given endpoints.
        //
        Ice.ConnectionI connection = findConnectionByEndpoint(endpoints, compress);
        if(connection != null)
        {
            return connection;
        }

        Ice.LocalException exception = null;

        //
        // If we didn't find a connection with the endpoints, we create the connectors
        // for the endpoints.
        //
        java.util.List<ConnectorInfo> connectors = new java.util.ArrayList<ConnectorInfo>();
        java.util.Iterator<EndpointI> p = endpoints.iterator();
        while(p.hasNext())
        {
            EndpointI endpoint = p.next();

            //
            // Create connectors for the endpoint.
            //
            try
            {
                java.util.List<Connector> cons = endpoint.connectors();
                assert(cons.size() > 0);
                
                //
                // Shuffle connectors if endpoint selection type is Random.
                //
                if(selType == Ice.EndpointSelectionType.Random)
                {
                    java.util.Collections.shuffle(cons);
                }
                
                java.util.Iterator<Connector> q = cons.iterator();
                while(q.hasNext())
                {
                    connectors.add(new ConnectorInfo(q.next(), endpoint));
                }
            }
            catch(Ice.LocalException ex)
            {
                exception = ex;
                handleException(exception, hasMore || p.hasNext());
            }
        }
        
        if(connectors.isEmpty())
        {
            assert(exception != null);
            throw exception;
        }
        
        //
        // Try to get a connection to one of the connectors. A null result indicates that no
        // connection was found and that we should try to establish the connection (and that
        // the connectors were added to _pending to prevent other threads from establishing
        // the connection).
        //
        connection = getConnection(connectors, null, compress);
        if(connection != null)
        {
            return connection;
        }

        //
        // Try to establish the connection to the connectors.
        //
        DefaultsAndOverrides defaultsAndOverrides = _instance.defaultsAndOverrides();
        java.util.Iterator<ConnectorInfo> q = connectors.iterator();
        while(q.hasNext())
        {
            ConnectorInfo ci = q.next();
            try
            {
                connection = createConnection(ci.connector.connect(), ci);
                connection.start(null);

                if(defaultsAndOverrides.overrideCompress)
                {
                    compress.value = defaultsAndOverrides.overrideCompressValue;
                }
                else
                {
                    compress.value = ci.endpoint.compress();
                }

                break;
            }
            catch(Ice.CommunicatorDestroyedException ex)
            {
                exception = ex;
                handleException(exception, ci, connection, hasMore || p.hasNext());
                connection = null;
                break; // No need to continue
            }
            catch(Ice.LocalException ex)
            {
                exception = ex;
                handleException(exception, ci, connection, hasMore || p.hasNext());
                connection = null;
            }
        }

        //
        // Finish creating the connection (this removes the connectors from the _pending
        // list and notifies any waiting threads).
        //
        finishGetConnection(connectors, null, connection);

        if(connection == null)
        {
            assert(exception != null);
            throw exception;
        }

        return connection;
    }

    public void
    create(EndpointI[] endpts, boolean hasMore, Ice.EndpointSelectionType selType,
           CreateConnectionCallback callback)
    {
        assert(endpts.length > 0);

        //
        // Apply the overrides.
        //
        java.util.List<EndpointI> endpoints = applyOverrides(endpts);

        //
        // Try to find a connection to one of the given endpoints.
        //
        try
        {
            Ice.BooleanHolder compress = new Ice.BooleanHolder();
            Ice.ConnectionI connection = findConnectionByEndpoint(endpoints, compress);
            if(connection != null)
            {
                callback.setConnection(connection, compress.value);
                return;
            }
        }
        catch(Ice.LocalException ex)
        {
            callback.setException(ex);
            return;
        }

        ConnectCallback cb = new ConnectCallback(this, endpoints, hasMore, callback, selType);
        cb.getConnectors();
    }

    public synchronized void
    setRouterInfo(IceInternal.RouterInfo routerInfo)
    {
        if(_destroyed)
        {
            throw new Ice.CommunicatorDestroyedException();
        }

        assert(routerInfo != null);

        //
        // Search for connections to the router's client proxy
        // endpoints, and update the object adapter for such
        // connections, so that callbacks from the router can be
        // received over such connections.
        //
        Ice.ObjectAdapter adapter = routerInfo.getAdapter();
        DefaultsAndOverrides defaultsAndOverrides = _instance.defaultsAndOverrides();
        EndpointI[] endpoints = routerInfo.getClientEndpoints();
        for(int i = 0; i < endpoints.length; i++)
        {
            EndpointI endpoint = endpoints[i];

            //
            // Modify endpoints with overrides.
            //
            if(defaultsAndOverrides.overrideTimeout)
            {
                endpoint = endpoint.timeout(defaultsAndOverrides.overrideTimeoutValue);
            }

            //
            // The Connection object does not take the compression flag of
            // endpoints into account, but instead gets the information
            // about whether messages should be compressed or not from
            // other sources. In order to allow connection sharing for
            // endpoints that differ in the value of the compression flag
            // only, we always set the compression flag to false here in
            // this connection factory.
            //
            endpoint = endpoint.compress(false);

            java.util.Iterator<java.util.List<Ice.ConnectionI> > p = _connections.values().iterator();
            while(p.hasNext())
            {
                java.util.List<Ice.ConnectionI> connectionList = p.next();

                java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
                while(q.hasNext())
                {
                    Ice.ConnectionI connection = q.next();
                    if(connection.endpoint() == endpoint)
                    {
                        try
                        {
                            connection.setAdapter(adapter);
                        }
                        catch(Ice.LocalException ex)
                        {
                            //
                            // Ignore, the connection is being closed or closed.
                            //
                        }
                    }
                }
            }
        }
    }

    public synchronized void
    removeAdapter(Ice.ObjectAdapter adapter)
    {
        if(_destroyed)
        {
            return;
        }

        java.util.Iterator<java.util.List<Ice.ConnectionI> > p = _connections.values().iterator();
        while(p.hasNext())
        {
            java.util.List<Ice.ConnectionI> connectionList = p.next();
                
            java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
            while(q.hasNext())
            {
                Ice.ConnectionI connection = q.next();
                if(connection.getAdapter() == adapter)
                {
                    try
                    {
                        connection.setAdapter(null);
                    }
                    catch(Ice.LocalException ex)
                    {
                        //
                        // Ignore, the connection is being closed or closed.
                        //
                    }
                }
            }
        }
    }

    public void
    flushBatchRequests()
    {
        java.util.List<Ice.ConnectionI> c = new java.util.LinkedList<Ice.ConnectionI>();

        synchronized(this)
        {
            java.util.Iterator<java.util.List<Ice.ConnectionI> > p = _connections.values().iterator();
            while(p.hasNext())
            {
                java.util.List<Ice.ConnectionI> connectionList = p.next();
                java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
                while(q.hasNext())
                {
                    c.add(q.next());
                }
            }
        }

        java.util.Iterator<Ice.ConnectionI> p = c.iterator();
        while(p.hasNext())
        {
            Ice.ConnectionI conn = p.next();
            try
            {
                conn.flushBatchRequests();
            }
            catch(Ice.LocalException ex)
            {
                // Ignore.
            }
        }
    }

    //
    // Only for use by Instance.
    //
    OutgoingConnectionFactory(Instance instance)
    {
        _instance = instance;
        _destroyed = false;
    }

    protected synchronized void
    finalize()
        throws Throwable
    {
        IceUtilInternal.Assert.FinalizerAssert(_destroyed);
        IceUtilInternal.Assert.FinalizerAssert(_connections == null);
        IceUtilInternal.Assert.FinalizerAssert(_connectionsByEndpoint == null);
        IceUtilInternal.Assert.FinalizerAssert(_pendingConnectCount == 0);
        IceUtilInternal.Assert.FinalizerAssert(_pending.isEmpty());

        super.finalize();
    }

    private java.util.List<EndpointI>
    applyOverrides(EndpointI[] endpts)
    {
        DefaultsAndOverrides defaultsAndOverrides = _instance.defaultsAndOverrides();
        java.util.List<EndpointI> endpoints = new java.util.ArrayList<EndpointI>();
        for(int i = 0; i < endpts.length; i++)
        {
            //
            // Modify endpoints with overrides.
            //
            if(defaultsAndOverrides.overrideTimeout)
            {
                endpoints.add(endpts[i].timeout(defaultsAndOverrides.overrideTimeoutValue));
            }
            else
            {
                endpoints.add(endpts[i]);
            }
        }

        return endpoints;
    }

    synchronized private Ice.ConnectionI
    findConnectionByEndpoint(java.util.List<EndpointI> endpoints, Ice.BooleanHolder compress)
    {
        if(_destroyed)
        {
            throw new Ice.CommunicatorDestroyedException();
        }

        DefaultsAndOverrides defaultsAndOverrides = _instance.defaultsAndOverrides();
        assert(!endpoints.isEmpty());

        java.util.Iterator<EndpointI> p = endpoints.iterator();
        while(p.hasNext())
        {
            EndpointI endpoint = p.next();
            java.util.List<Ice.ConnectionI> connectionList = _connectionsByEndpoint.get(endpoint);
            if(connectionList == null)
            {
                continue;
            }
            
            java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
            while(q.hasNext())
            {
                Ice.ConnectionI connection = q.next();
                if(connection.isActiveOrHolding()) // Don't return destroyed or un-validated connections
                {
                    if(defaultsAndOverrides.overrideCompress)
                    {
                        compress.value = defaultsAndOverrides.overrideCompressValue;
                    }
                    else
                    {
                        compress.value = endpoint.compress();
                    }
                    return connection;
                }
            }
        }
        
        return null;
    }

    //
    // Must be called while synchronized.
    //
    private Ice.ConnectionI
    findConnection(java.util.List<ConnectorInfo> connectors, Ice.BooleanHolder compress)
    {
        DefaultsAndOverrides defaultsAndOverrides = _instance.defaultsAndOverrides();
        java.util.Iterator<ConnectorInfo> p = connectors.iterator();
        while(p.hasNext())
        {
            ConnectorInfo ci = p.next();
            java.util.List<Ice.ConnectionI> connectionList = _connections.get(ci);
            if(connectionList == null)
            {
                continue;
            }
            
            java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
            while(q.hasNext())
            {
                Ice.ConnectionI connection = q.next();
                if(connection.isActiveOrHolding()) // Don't return destroyed or un-validated connections
                {
                    if(!connection.endpoint().equals(ci.endpoint))
                    {
                        java.util.List<Ice.ConnectionI> conList = _connectionsByEndpoint.get(ci.endpoint);
                        if(conList == null)
                        {
                            conList = new java.util.LinkedList<Ice.ConnectionI>();
                            _connectionsByEndpoint.put(ci.endpoint, conList);
                        }
                        conList.add(connection);
                    }
                    
                    if(defaultsAndOverrides.overrideCompress)
                    {
                        compress.value = defaultsAndOverrides.overrideCompressValue;
                    }
                    else
                    {
                        compress.value = ci.endpoint.compress();
                    }
                    return connection;
                }
            }
        }
        
        return null;
    }

    synchronized private void
    incPendingConnectCount()
    {
        //
        // Keep track of the number of pending connects. The outgoing connection factory 
        // waitUntilFinished() method waits for all the pending connects to terminate before
        // to return. This ensures that the communicator client thread pool isn't destroyed
        // too soon and will still be available to execute the ice_exception() callbacks for
        // the asynchronous requests waiting on a connection to be established.
        //

        if(_destroyed)
        {
            throw new Ice.CommunicatorDestroyedException();
        }
        ++_pendingConnectCount;
    }

    synchronized private void
    decPendingConnectCount()
    {
        --_pendingConnectCount;
        assert(_pendingConnectCount >= 0);
        if(_destroyed && _pendingConnectCount == 0)
        {
            notifyAll();
        }
    }

    private Ice.ConnectionI
    getConnection(java.util.List<ConnectorInfo> connectors, ConnectCallback cb, Ice.BooleanHolder compress)
    {
        synchronized(this)
        {
            if(_destroyed)
            {
                throw new Ice.CommunicatorDestroyedException();
            }

            //
            // Reap connections for which destruction has completed.
            //
            {
                java.util.Iterator<java.util.List<Ice.ConnectionI> > p = _connections.values().iterator();
                while(p.hasNext())
                {
                    java.util.List<Ice.ConnectionI> connectionList = p.next();
                    java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
                    while(q.hasNext())
                    {
                        Ice.ConnectionI con = q.next();
                        if(con.isFinished())
                        {
                            q.remove();
                        }
                    }
                    
                    if(connectionList.isEmpty())
                    {
                        p.remove();
                    }
                }
            }

            {
                java.util.Iterator<java.util.List<Ice.ConnectionI> > p = _connectionsByEndpoint.values().iterator();
                while(p.hasNext())
                {
                    java.util.List<Ice.ConnectionI> connectionList = p.next();
                    java.util.Iterator<Ice.ConnectionI> q = connectionList.iterator();
                    while(q.hasNext())
                    {
                        Ice.ConnectionI con = q.next();
                        if(con.isFinished())
                        {
                            q.remove();
                        }
                    }
                    
                    if(connectionList.isEmpty())
                    {
                        p.remove();
                    }
                }
            }

            //
            // Try to get the connection. We may need to wait for other threads to
            // finish if one of them is currently establishing a connection to one
            // of our connectors.
            //
            while(!_destroyed)
            {
                //
                // Search for a matching connection. If we find one, we're done.
                //
                Ice.ConnectionI connection = findConnection(connectors, compress);
                if(connection != null)
                {
                    if(cb != null)
                    {
                        //
                        // This might not be the first getConnection call for the callback. We need
                        // to ensure that the callback isn't registered with any other pending 
                        // connectors since we just found a connection and therefore don't need to
                        // wait anymore for other pending connectors.
                        // 
                        java.util.Iterator<ConnectorInfo> p = connectors.iterator();
                        while(p.hasNext())
                        {
                            java.util.Set<ConnectCallback> cbs = _pending.get(p.next());
                            if(cbs != null)
                            {
                                cbs.remove(cb);
                            }
                        }
                    }
                    return connection;
                }

                //
                // Determine whether another thread is currently attempting to connect to one of our endpoints;
                // if so we wait until it's done.
                //
                java.util.Iterator<ConnectorInfo> p = connectors.iterator();
                boolean found = false;
                while(p.hasNext())
                {
                    java.util.Set<ConnectCallback> cbs = _pending.get(p.next());
                    if(cbs != null)
                    {
                        found = true;
                        if(cb != null)
                        {
                            cbs.add(cb); // Add the callback to each pending connector.
                        }
                    }
                }
                
                if(!found)
                {
                    //
                    // If no thread is currently establishing a connection to one of our connectors,
                    // we get out of this loop and start the connection establishment to one of the
                    // given connectors.
                    //
                    break;
                }
                else
                {
                    //
                    // If a callback is not specified we wait until another thread notifies us about a 
                    // change to the pending list. Otherwise, if a callback is provided we're done: 
                    // when the pending list changes the callback will be notified and will try to 
                    // get the connection again.
                    //
                    if(cb == null)
                    {
                        try
                        {
                            wait();
                        }
                        catch(InterruptedException ex)
                        {
                        }
                    }
                    else
                    {
                        return null;
                    }
                }
            }
            
            if(_destroyed)
            {
                throw new Ice.CommunicatorDestroyedException();
            }
            
            //
            // No connection to any of our endpoints exists yet; we add the given connectors to
            // the _pending set to indicate that we're attempting connection establishment to 
            // these connectors. We might attempt to connect to the same connector multiple times. 
            //
            java.util.Iterator<ConnectorInfo> p = connectors.iterator();
            while(p.hasNext())
            {
                ConnectorInfo obj = p.next();
                if(!_pending.containsKey(obj))
                {
                    _pending.put(obj, new java.util.HashSet<ConnectCallback>());
                }
            }
        }

        //
        // At this point, we're responsible for establishing the connection to one of 
        // the given connectors. If it's a non-blocking connect, calling nextConnector
        // will start the connection establishment. Otherwise, we return null to get
        // the caller to establish the connection.
        //
        if(cb != null)
        {
            cb.nextConnector();
        }

        return null;
    }

    private synchronized Ice.ConnectionI
    createConnection(Transceiver transceiver, ConnectorInfo ci)
    {
        assert(_pending.containsKey(ci) && transceiver != null);

        //
        // Create and add the connection to the connection map. Adding the connection to the map
        // is necessary to support the interruption of the connection initialization and validation
        // in case the communicator is destroyed.
        //
	try
	{
            if(_destroyed)
            {
                throw new Ice.CommunicatorDestroyedException();
            }

	    Ice.ConnectionI connection = new Ice.ConnectionI(_instance, transceiver, ci.endpoint.compress(false),null);

            java.util.List<Ice.ConnectionI> connectionList = _connections.get(ci);
            if(connectionList == null)
            {
                connectionList = new java.util.LinkedList<Ice.ConnectionI>();
                _connections.put(ci, connectionList);
            }
            connectionList.add(connection);
            connectionList = _connectionsByEndpoint.get(ci.endpoint);
            if(connectionList == null)
            {
                connectionList = new java.util.LinkedList<Ice.ConnectionI>();
                _connectionsByEndpoint.put(ci.endpoint, connectionList);
            }
            connectionList.add(connection);
            return connection;
	}
	catch(Ice.LocalException ex)
	{
	    try
	    {
		transceiver.close();
	    }
	    catch(Ice.LocalException exc)
	    {
		// Ignore
	    }
            throw ex;
	}
    }

    private void
    finishGetConnection(java.util.List<ConnectorInfo> connectors, ConnectCallback cb, Ice.ConnectionI connection)
    {
        java.util.Set<ConnectCallback> callbacks = new java.util.HashSet<ConnectCallback>();

        synchronized(this)
        {
            //
            // We're done trying to connect to the given connectors so we remove the 
            // connectors from the pending list and notify waiting threads. We also 
            // notify the pending connect callbacks (outside the synchronization).
            //

            java.util.Iterator<ConnectorInfo> p = connectors.iterator();
            while(p.hasNext())
            {
                java.util.Set<ConnectCallback> cbs = _pending.remove(p.next());
                if(cbs != null)
                {
                    callbacks.addAll(cbs);
                }
            }
            notifyAll();

            //
            // If the connect attempt succeeded and the communicator is not destroyed,
            // activate the connection!
            //
            if(connection != null && !_destroyed)
            {
                connection.activate();
            }
        }
        
        //
        // Notify any waiting callbacks.
        //
        java.util.Iterator<ConnectCallback> p = callbacks.iterator();
        while(p.hasNext())
        {
            p.next().getConnection();
        }
    }

    private void
    handleException(Ice.LocalException ex, ConnectorInfo ci, Ice.ConnectionI connection, boolean hasMore)
    {
        TraceLevels traceLevels = _instance.traceLevels();
        if(traceLevels.retry >= 2)
        {
            StringBuffer s = new StringBuffer();
            s.append("connection to endpoint failed");
            if(ex instanceof Ice.CommunicatorDestroyedException)
            {
                s.append("\n");
            }
            else
            {
                if(hasMore)
                {
                    s.append(", trying next endpoint\n");
                }
                else
                {
                    s.append(" and no more endpoints to try\n");
                }
            }
            s.append(ex.toString());
            _instance.initializationData().logger.trace(traceLevels.retryCat, s.toString());
        }

        if(connection != null && connection.isFinished())
        {
            //
            // If the connection is finished, we remove it right away instead of
            // waiting for the reaping.
            //
            // NOTE: it's possible for the connection to not be finished yet.
            //
            synchronized(this)
            {
                java.util.List<Ice.ConnectionI> connectionList = _connections.get(ci);
                if(connectionList != null) // It might have already been reaped!
                {
                    connectionList.remove(connection);
                    if(connectionList.isEmpty())
                    {
                        _connections.remove(ci);
                    }
                }

                connectionList = _connectionsByEndpoint.get(ci.endpoint);
                if(connectionList != null) // It might have already been reaped!
                {
                    connectionList.remove(connection);
                    if(connectionList.isEmpty())
                    {
                        _connectionsByEndpoint.remove(ci.endpoint);
                    }
                }
            }
        }
    }

    private void
    handleException(Ice.LocalException ex, boolean hasMore)
    {
        TraceLevels traceLevels = _instance.traceLevels();
        if(traceLevels.retry >= 2)
        {
            StringBuffer s = new StringBuffer();
            s.append("couldn't resolve endpoint host");
            if(ex instanceof Ice.CommunicatorDestroyedException)
            {
                s.append("\n");
            }
            else
            {
                if(hasMore)
                {
                    s.append(", trying next endpoint\n");
                }
                else
                {
                    s.append(" and no more endpoints to try\n");
                }
            }
            s.append(ex.toString());
            _instance.initializationData().logger.trace(traceLevels.retryCat, s.toString());
        }
    }

    private static class ConnectorInfo
    {
        public ConnectorInfo(Connector c, EndpointI e)
        {
            connector = c;
            endpoint = e;
        }

        public boolean 
        equals(Object obj)
        {
            ConnectorInfo r = (ConnectorInfo)obj;
            return connector.equals(r.connector);
        }

        public int
        hashCode()
        {
            return connector.hashCode();
        }

        public Connector connector;
        public EndpointI endpoint;
    }

    private static class ConnectCallback implements Ice.ConnectionI.StartCallback, EndpointI_connectors
    {
        ConnectCallback(OutgoingConnectionFactory f, java.util.List<EndpointI> endpoints, boolean more, 
                        CreateConnectionCallback cb, Ice.EndpointSelectionType selType)
        {
            _factory = f;
            _endpoints = endpoints;
            _hasMore = more;
            _callback = cb;
            _selType = selType;
            _endpointsIter = _endpoints.iterator();
        }

        //
        // Methods from ConnectionI.StartCallback
        //
        public void
        connectionStartCompleted(Ice.ConnectionI connection)
        {
            boolean compress;
            DefaultsAndOverrides defaultsAndOverrides = _factory._instance.defaultsAndOverrides();
            if(defaultsAndOverrides.overrideCompress)
            {
                compress = defaultsAndOverrides.overrideCompressValue;
            }
            else
            {
                compress = _current.endpoint.compress();
            }

            _factory.finishGetConnection(_connectors, this, connection);
            _callback.setConnection(connection, compress);
            _factory.decPendingConnectCount(); // Must be called last.
        }

        public void
        connectionStartFailed(Ice.ConnectionI connection, Ice.LocalException ex)
        {
            assert(_current != null);

            _factory.handleException(ex, _current, connection, _hasMore || _iter.hasNext());
            if(ex instanceof Ice.CommunicatorDestroyedException) // No need to continue.
            {
                _factory.finishGetConnection(_connectors, this, null);
                _callback.setException(ex);
                _factory.decPendingConnectCount(); // Must be called last.
            }
            else if(_iter.hasNext()) // Try the next connector.
            {
                nextConnector();
            }
            else
            {
                _factory.finishGetConnection(_connectors, this, null);
                _callback.setException(ex);
                _factory.decPendingConnectCount(); // Must be called last.
            }
        }

        //
        // Methods from EndpointI_connectors
        //
        public void
        connectors(java.util.List<Connector> cons)
        {
            //
            // Shuffle connectors if endpoint selection type is Random.
            //
            if(_selType == Ice.EndpointSelectionType.Random)
            {
                java.util.Collections.shuffle(cons);
            }
                
            java.util.Iterator<Connector> q = cons.iterator();
            while(q.hasNext())
            {
                _connectors.add(new ConnectorInfo(q.next(), _currentEndpoint));
            }

            if(_endpointsIter.hasNext())
            {
                nextEndpoint();
            }
            else
            {
                assert(!_connectors.isEmpty());
                
                //
                // We now have all the connectors for the given endpoints. We can try to obtain the
                // connection.
                //
                _iter = _connectors.iterator();
                getConnection();
            }
        }
        
        public void
        exception(Ice.LocalException ex)
        {
            _factory.handleException(ex, _hasMore || _endpointsIter.hasNext());
            if(_endpointsIter.hasNext())
            {
                nextEndpoint();
            }
            else if(!_connectors.isEmpty())
            {
                //
                // We now have all the connectors for the given endpoints. We can try to obtain the
                // connection.
                //
                _iter = _connectors.iterator();
                getConnection();
            }
            else
            {
                _callback.setException(ex);
                _factory.decPendingConnectCount(); // Must be called last.
            }
        }

        void 
        getConnectors()
        {
            try
            {
                //
                // Notify the factory that there's an async connect pending. This is necessary
                // to prevent the outgoing connection factory to be destroyed before all the
                // pending asynchronous connects are finished.
                //
                _factory.incPendingConnectCount();
            }
            catch(Ice.LocalException ex)
            {
                _callback.setException(ex);
                return;
            }

            nextEndpoint();
        }

        void
        nextEndpoint()
        {
            try
            {
                assert(_endpointsIter.hasNext());
                _currentEndpoint = _endpointsIter.next();
                _currentEndpoint.connectors_async(this);
            }
            catch(Ice.LocalException ex)
            {
                exception(ex);
            }
        }

        void
        getConnection()
        {
            try
            {
                //
                // If all the connectors have been created, we ask the factory to get a 
                // connection.
                //
                Ice.BooleanHolder compress = new Ice.BooleanHolder();
                Ice.ConnectionI connection = _factory.getConnection(_connectors, this, compress);
                if(connection == null)
                {
                    //
                    // A null return value from getConnection indicates that the connection
                    // is being established and that everthing has been done to ensure that
                    // the callback will be notified when the connection establishment is 
                    // done.
                    // 
                    return;
                }
                
                _callback.setConnection(connection, compress.value);
                _factory.decPendingConnectCount(); // Must be called last.
            }
            catch(Ice.LocalException ex)
            {
                _callback.setException(ex);
                _factory.decPendingConnectCount(); // Must be called last.
            }
        }

        void
        nextConnector()
        {
            Ice.ConnectionI connection = null;
            try
            {
                assert(_iter.hasNext());
                _current = _iter.next();
                connection = _factory.createConnection(_current.connector.connect(), _current);
                connection.start(this);
            }
            catch(Ice.LocalException ex)
            {
                connectionStartFailed(connection, ex);
            }
        }

        private final OutgoingConnectionFactory _factory;
        private final boolean _hasMore;
        private final CreateConnectionCallback _callback;
        private final java.util.List<EndpointI> _endpoints;
        private final Ice.EndpointSelectionType _selType;
        private java.util.Iterator<EndpointI> _endpointsIter;
        private EndpointI _currentEndpoint;
        private java.util.List<ConnectorInfo> _connectors = new java.util.ArrayList<ConnectorInfo>();
        private java.util.Iterator<ConnectorInfo> _iter;
        private ConnectorInfo _current;
    }

    private final Instance _instance;
    private boolean _destroyed;

    private java.util.Map<ConnectorInfo, java.util.List<Ice.ConnectionI> > _connections =
        new java.util.HashMap<ConnectorInfo, java.util.List<Ice.ConnectionI> >();
    private java.util.Map<EndpointI, java.util.List<Ice.ConnectionI> > _connectionsByEndpoint =
        new java.util.HashMap<EndpointI, java.util.List<Ice.ConnectionI> >();
    private java.util.Map<ConnectorInfo, java.util.HashSet<ConnectCallback> > _pending =
        new java.util.HashMap<ConnectorInfo, java.util.HashSet<ConnectCallback> >();
    private int _pendingConnectCount = 0;
}