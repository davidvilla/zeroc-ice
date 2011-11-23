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

name = os.path.join("Ice", "servantLocator")

print "tests with regular server."
TestUtil.clientServerTest(name)
print "tests with AMD server."
TestUtil.clientServerTestWithOptionsAndNames(name, "", "", "ServerAMD.py", "Client.py")
print "tests with collocated server."
TestUtil.collocatedTestWithOptions(name, " --Ice.ThreadPool.Client.SizeMax=2 --Ice.ThreadPool.Client.SizeWarn=0")
sys.exit(0)