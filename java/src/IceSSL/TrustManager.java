// **********************************************************************
//
// Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

package IceSSL;

class TrustManager
{
    TrustManager(Ice.Communicator communicator)
    {
        assert communicator != null;
        _communicator = communicator;
        Ice.Properties properties = communicator.getProperties();
        _traceLevel = properties.getPropertyAsInt("IceSSL.Trace.Security");
        String key = null;
        try
        {
            key = "IceSSL.TrustOnly";
            _all = parse(properties.getProperty(key));
            key = "IceSSL.TrustOnly.Client";
            _client = parse(properties.getProperty(key));
            key = "IceSSL.TrustOnly.Server";
            _allServer = parse(properties.getProperty(key));
            java.util.Map<String, String> dict = properties.getPropertiesForPrefix("IceSSL.TrustOnly.Server.");
            java.util.Iterator<java.util.Map.Entry<String, String> > p = dict.entrySet().iterator();
            while(p.hasNext())
            {
                java.util.Map.Entry<String, String> entry = p.next();
                key = entry.getKey();
                String name = key.substring("IceSSL.TrustOnly.Server.".length());
                _server.put(name, parse(entry.getValue()));
            }
        }
        catch(RFC2253.ParseException e)
        {
            Ice.PluginInitializationException ex = new Ice.PluginInitializationException();
            ex.reason = "IceSSL: invalid property " + key  + ":\n" + e.reason;
            throw ex;
        }
    }

    boolean
    verify(ConnectionInfo info)
    {
        java.util.List<java.util.List<java.util.List<RFC2253.RDNPair> > > trustset =
            new java.util.LinkedList<java.util.List<java.util.List<RFC2253.RDNPair> > >();
        if(!_all.isEmpty())
        {
            trustset.add(_all);
        }

        if(info.incoming)
        {
            if(!_allServer.isEmpty())
            {
                trustset.add(_allServer);
            }
            if(info.adapterName.length() > 0)
            {
                java.util.List<java.util.List<RFC2253.RDNPair> > p = _server.get(info.adapterName);
                if(p != null)
                {
                    trustset.add(p);
                }
            }
        }
        else
        {
            if(!_client.isEmpty())
            {
                trustset.add(_client);
            }
        }

        //
        // If there is nothing to match against, then we accept the cert.
        //
        if(trustset.isEmpty())
        {
            return true;
        }

        //
        // If there is no certificate then we match false.
        //
        if(info.certs != null && info.certs.length > 0)
        {
            javax.security.auth.x500.X500Principal subjectDN = (javax.security.auth.x500.X500Principal)
                ((java.security.cert.X509Certificate)info.certs[0]).getSubjectX500Principal();
            String subjectName = subjectDN.getName(javax.security.auth.x500.X500Principal.RFC2253);
            assert subjectName != null;
            try
            {
                //
                // Decompose the subject DN into the RDNs.
                //
                if(_traceLevel > 0)
                {
                    if(info.incoming)
                    {
                        _communicator.getLogger().trace("Security", "trust manager evaluating client:\n" +
                            "subject = " + subjectName + "\n" +
                            "adapter = " + info.adapterName + "\n" +
                            "local addr = " + IceInternal.Network.addrToString(info.localAddr) + "\n" +
                            "remote addr = " + IceInternal.Network.addrToString(info.remoteAddr));
                    }
                    else
                    {
                        _communicator.getLogger().trace("Security", "trust manager evaluating server:\n" +
                            "subject = " + subjectName + "\n" +
                            "local addr = " + IceInternal.Network.addrToString(info.localAddr) + "\n" +
                            "remote addr = " + IceInternal.Network.addrToString(info.remoteAddr));
                    }
                }
                java.util.List<RFC2253.RDNPair> dn = RFC2253.parseStrict(subjectName);

                //
                // Try matching against everything in the trust set.
                //
                java.util.Iterator<java.util.List<java.util.List<RFC2253.RDNPair> > > p = trustset.iterator();
                while(p.hasNext())
                {
                    java.util.List<java.util.List<RFC2253.RDNPair> > matchSet = p.next();
                    if(_traceLevel > 1)
                    {
                        String s = "trust manager matching PDNs:\n";
                        java.util.Iterator<java.util.List<RFC2253.RDNPair> > q = matchSet.iterator();
                        boolean addSemi = false;
                        while(q.hasNext())
                        {
                            if(addSemi)
                            {
                                s += ';';
                            }
                            addSemi = true;
                            java.util.List<RFC2253.RDNPair> rdnSet = q.next();
                            java.util.Iterator<RFC2253.RDNPair> r = rdnSet.iterator();
                            boolean addComma = false;
                            while(r.hasNext())
                            {
                                if(addComma)
                                {
                                    s += ',';
                                }
                                addComma = true;
                                RFC2253.RDNPair rdn = r.next();
                                s += rdn.key;
                                s += '=';
                                s += rdn.value;
                            }
                        }
                        _communicator.getLogger().trace("Security", s);
                    }
                    if(match(matchSet, dn))
                    {
                        return true;
                    }
                }
            }
            catch(RFC2253.ParseException e)
            {
                _communicator.getLogger().warning(
                    "IceSSL: unable to parse certificate DN `" + subjectName + "'\nreason: " + e.reason);
            }
        }

        return false;
    }

    private boolean
    match(java.util.List<java.util.List<RFC2253.RDNPair> > matchSet, java.util.List<RFC2253.RDNPair> subject)
    {
        java.util.Iterator<java.util.List<RFC2253.RDNPair> > r = matchSet.iterator();
        while(r.hasNext())
        {
            if(matchRDNs(r.next(), subject))
            {
                return true;
            }
        }
        return false;
    }

    private boolean
    matchRDNs(java.util.List<RFC2253.RDNPair> match, java.util.List<RFC2253.RDNPair> subject)
    {
        java.util.Iterator<RFC2253.RDNPair> p = match.iterator();
        while(p.hasNext())
        {
            RFC2253.RDNPair matchRDN = p.next();
            boolean found = false;
            java.util.Iterator<RFC2253.RDNPair> q = subject.iterator();
            while(q.hasNext())
            {
                RFC2253.RDNPair subjectRDN = q.next();
                if(matchRDN.key.equals(subjectRDN.key))
                {
                    found = true;
                    if(!matchRDN.value.equals(subjectRDN.value))
                    {
                        return false;
                    }
                }
            }
            if(!found)
            {
                return false;
            }
        }
        return true;
    }

    java.util.List<java.util.List<RFC2253.RDNPair> >
    parse(String value)
        throws RFC2253.ParseException
    {
        //
        // Java X500Principal.getName says:
        //
        // If "RFC2253" is specified as the format, this method emits
        // the attribute type keywords defined in RFC 2253 (CN, L, ST,
        // O, OU, C, STREET, DC, UID). Any other attribute type is
        // emitted as an OID. Under a strict reading, RFC 2253 only
        // specifies a UTF-8 string representation. The String
        // returned by this method is the Unicode string achieved by
        // decoding this UTF-8 representation.
        //
        // This means that things like emailAddress and such will be turned into
        // something like:
        //
        // 1.2.840.113549.1.9.1=#160e696e666f407a65726f632e636f6d
        //
        // The left hand side is the OID (see
        // http://www.columbia.edu/~ariel/ssleay/asn1-oids.html) for a
        // list. The right hand side is a BER encoding of the value.
        //
        // This means that the user input, unless it uses the
        // unfriendly OID format, will not directly match the
        // principal.
        // 
        // Two possible solutions:
        //
        // Have the RFC2253 parser convert anything that is not CN, L,
        // ST, O, OU, C, STREET, DC, UID into OID format, and have it
        // convert the values into a BER encoding.
        //
        // Send the user data through X500Principal to string form and
        // then through the RFC2253 encoder. This uses the
        // X500Principal to do the encoding for us.
        //
        // The latter is much simpler, however, it means we need to
        // send the data through the parser twice because we split the
        // DNs on ';' which cannot be blindly split because of quotes,
        // \ and such.
        //
        java.util.List<java.util.List<RFC2253.RDNPair> > l = RFC2253.parse(value);
        java.util.List<java.util.List<RFC2253.RDNPair> > result =
            new java.util.LinkedList<java.util.List<RFC2253.RDNPair> >();
        java.util.Iterator<java.util.List<RFC2253.RDNPair> > p = l.iterator();
        while(p.hasNext())
        {
            java.util.List<RFC2253.RDNPair> dn = p.next();
            String v = new String();
            boolean first = true;
            java.util.Iterator<RFC2253.RDNPair> q = dn.iterator();
            while(q.hasNext())
            {
                if(!first)
                {
                    v += ",";
                }
                first = false;
                RFC2253.RDNPair pair = q.next();
                v += pair.key;
                v += "=";
                v += pair.value;
            }
            javax.security.auth.x500.X500Principal princ = new javax.security.auth.x500.X500Principal(v);
            String subjectName = princ.getName(javax.security.auth.x500.X500Principal.RFC2253);
            result.add(RFC2253.parseStrict(subjectName));
        }
        return result;
    }

    private Ice.Communicator _communicator;
    private int _traceLevel;

    private java.util.List<java.util.List<RFC2253.RDNPair> > _all;
    private java.util.List<java.util.List<RFC2253.RDNPair> > _client;
    private java.util.List<java.util.List<RFC2253.RDNPair> > _allServer;
    private java.util.Map<String, java.util.List<java.util.List<RFC2253.RDNPair> > > _server =
        new java.util.HashMap<String, java.util.List<java.util.List<RFC2253.RDNPair> > >();
}