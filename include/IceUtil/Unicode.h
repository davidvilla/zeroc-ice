// **********************************************************************
//
// Copyright (c) 2003
// ZeroC, Inc.
// Billerica, MA, USA
//
// All Rights Reserved.
//
// Ice is free software; you can redistribute it and/or modify it under
// the terms of the GNU General Public License version 2 as published by
// the Free Software Foundation.
//
// **********************************************************************

#ifndef ICE_UTIL_UNICODE_H
#define ICE_UTIL_UNICODE_H

#include <IceUtil/Config.h>

namespace IceUtil
{

ICE_UTIL_API std::string wstringToString(const std::wstring&);
ICE_UTIL_API std::wstring stringToWstring(const std::string&);

}

#endif