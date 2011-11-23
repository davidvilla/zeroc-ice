# **********************************************************************
#
# Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
#
# This copy of Ice is licensed to you under the terms described in the
# ICE_LICENSE file included in this distribution.
#
# **********************************************************************

import sys, Ice, Test

def test(b):
    if not b:
        raise RuntimeError('test assertion failed')

def testExceptions(obj, collocated):

    try:
        obj.requestFailedException()
        test(False)
    except Ice.ObjectNotExistException, ex:
        if not collocated:
            test(ex.id == obj.ice_getIdentity())
            test(ex.facet == obj.ice_getFacet())
            test(ex.operation == "requestFailedException")
    except:
        test(False)

    try:
        obj.unknownUserException()
        test(False)
    except Ice.UnknownUserException, ex:
        test(ex.unknown == "reason")
    except:
        test(False)

    try:
        obj.unknownLocalException()
        test(False)
    except Ice.UnknownLocalException, ex:
        test(ex.unknown == "reason")
    except:
        test(False)

    try:
        obj.unknownException()
        test(False)
    except Ice.UnknownException, ex:
        test(ex.unknown == "reason")
        pass

    try:
        obj.userException()
        test(False)
    except Ice.UnknownUserException, ex:
        test(ex.unknown.find("Test::TestIntfUserException") >= 0)
    except:
        test(False)

    try:
        obj.localException()
        test(False)
    except Ice.UnknownLocalException, ex:
        test(not collocated)
        test(ex.unknown.find("Ice.SocketException") >= 0)
    except SocketException:
        test(collocated)
    except:
        test(False)

    try:
        obj.pythonException()
        test(False)
    except Ice.UnknownException, ex:
        test(ex.unknown.find("RuntimeError: message") >= 0)
    except:
        test(False)

    try:
        obj.unknownExceptionWithServantException()
        test(False)
    except Ice.UnknownException, ex:
        test(ex.unknown == "reason")
    except:
        test(False)

    try:
        obj.impossibleException(False)
        test(False)
    except Ice.UnknownUserException:
        # Operation doesn't throw, but locate() and finished() throw TestIntfUserException.
        pass
    except:
        test(False)

    try:
        obj.impossibleException(True)
        test(False)
    except Ice.UnknownUserException:
        # Operation doesn't throw, but locate() and finished() throw TestIntfUserException.
        pass
    except:
        test(False)

    try:
        obj.intfUserException(False)
        test(False)
    except Test.TestImpossibleException:
        # Operation doesn't throw, but locate() and finished() throw TestImpossibleException.
        pass
    except:
        test(False)

    try:
        obj.intfUserException(True)
        test(False)
    except Test.TestImpossibleException:
        # Operation throws TestIntfUserException, but locate() and finished() throw TestImpossibleException.
        pass
    except:
        test(False)

def allTests(communicator, collocated):
    print "testing stringToProxy...",
    sys.stdout.flush()
    base = communicator.stringToProxy("asm:default -p 12010 -t 10000")
    test(base)
    print "ok"

    print "testing checked cast...",
    sys.stdout.flush()
    obj = Test.TestIntfPrx.checkedCast(base)
    test(obj)
    test(obj == base)
    print "ok"

    print "testing ice_ids...",
    sys.stdout.flush()
    try:
        obj = communicator.stringToProxy("category/locate:default -p 12010 -t 10000")
        obj.ice_ids()
        test(False)
    except Ice.UnknownUserException, ex:
        test(ex.unknown == "Test::TestIntfUserException")
    except:
        test(False)

    try:
        obj = communicator.stringToProxy("category/finished:default -p 12010 -t 10000")
        obj.ice_ids()
        test(False)
    except Ice.UnknownUserException, ex:
        test(ex.unknown == "Test::TestIntfUserException")
    except:
        test(False)
    print "ok"

    print "testing servant locator...",
    sys.stdout.flush()
    base = communicator.stringToProxy("category/locate:default -p 12010 -t 10000")
    obj = Test.TestIntfPrx.checkedCast(base)
    try:
        Test.TestIntfPrx.checkedCast(communicator.stringToProxy("category/unknown:default -p 12010 -t 10000"))
    except Ice.ObjectNotExistException:
        pass
    print "ok"

    print "testing default servant locator...",
    sys.stdout.flush()
    base = communicator.stringToProxy("anothercat/locate:default -p 12010 -t 10000")
    obj = Test.TestIntfPrx.checkedCast(base)
    base = communicator.stringToProxy("locate:default -p 12010 -t 10000")
    obj = Test.TestIntfPrx.checkedCast(base)
    try:
        Test.TestIntfPrx.checkedCast(communicator.stringToProxy("anothercat/unknown:default -p 12010 -t 10000"))
    except Ice.ObjectNotExistException:
        pass
    try:
        Test.TestIntfPrx.checkedCast(communicator.stringToProxy("unknown:default -p 12010 -t 10000"))
    except Ice.ObjectNotExistException:
        pass
    print "ok"

    print "testing locate exceptions...",
    sys.stdout.flush()
    base = communicator.stringToProxy("category/locate:default -p 12010 -t 10000")
    obj = Test.TestIntfPrx.checkedCast(base)
    testExceptions(obj, collocated)
    print "ok"

    print "testing finished exceptions...",
    sys.stdout.flush()
    base = communicator.stringToProxy("category/finished:default -p 12010 -t 10000")
    obj = Test.TestIntfPrx.checkedCast(base)
    testExceptions(obj, collocated)
    print "ok"

    return obj