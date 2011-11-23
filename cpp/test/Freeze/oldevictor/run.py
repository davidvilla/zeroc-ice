#!/usr/bin/env python
# **********************************************************************
#
# Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
#
# This copy of Ice is licensed to you under the terms described in the
# ICE_LICENSE file included in this distribution.
#
# **********************************************************************

import os, sys

for toplevel in [".", "..", "../..", "../../..", "../../../.."]:
    toplevel = os.path.normpath(toplevel)
    if os.path.exists(os.path.join(toplevel, "config", "TestUtil.py")):
        break
else:
    raise "can't find toplevel directory!"

sys.path.append(os.path.join(toplevel, "config"))
import TestUtil
TestUtil.processCmdLine()

name = os.path.join("Freeze", "oldevictor")
testdir = os.path.dirname(os.path.abspath(__file__))

dbdir = os.path.join(testdir, "db")
TestUtil.cleanDbDir(dbdir)

testOptions = " --Freeze.DbEnv.db.DbHome=" + testdir + "/db" + " --Ice.Config=" + testdir + "/config ";

TestUtil.clientServerTestWithOptions(name, testOptions, testOptions)
sys.exit(0)