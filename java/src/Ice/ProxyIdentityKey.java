// **********************************************************************
//
// Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

package Ice;

//
// This class wraps a proxy and supplies the necessary methods to allow
// it to be used as a key in a hashed collection. Only the proxy's
// identity is used in comparisons.
//
public class ProxyIdentityKey
{
    public
    ProxyIdentityKey(Ice.ObjectPrx proxy)
    {
        _proxy = proxy;

        //
        // Cache the identity and its hash code.
        //
        _identity = proxy.ice_getIdentity();
        _hashCode = _identity.hashCode();
    }

    public int
    hashCode()
    {
        return _hashCode;
    }

    public boolean
    equals(java.lang.Object obj)
    {
        if(this == obj)
        {
            return true;
        }

        if(obj instanceof ProxyIdentityKey)
        {
            ProxyIdentityKey other = (ProxyIdentityKey)obj;
            return (_hashCode == other._hashCode) && _identity.equals(other._identity);
        }

        return false;
    }

    public Ice.ObjectPrx
    getProxy()
    {
        return _proxy;
    }

    final private Ice.ObjectPrx _proxy;
    final private Ice.Identity _identity;
    final private int _hashCode;
}