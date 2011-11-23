// **********************************************************************
//
// Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

#ifndef TEST_ICE
#define TEST_ICE

module Test
{

exception TestIntfUserException
{
};

exception TestImpossibleException
{
};

interface TestIntf
{
    void requestFailedException();
    void unknownUserException();
    void unknownLocalException();
    void unknownException();
    void localException();
    //void userException();
    void javaException();

    string impossibleException(bool throw) throws TestImpossibleException;
    string intfUserException(bool throw) throws TestIntfUserException, TestImpossibleException;

    void shutdown();
};

local class Cookie
{
    ["cpp:const"] string message();
};

};

#endif