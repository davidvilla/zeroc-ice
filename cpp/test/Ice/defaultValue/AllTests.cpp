// **********************************************************************
//
// Copyright (c) 2003-2011 ZeroC, Inc. All rights reserved.
//
// This copy of Ice is licensed to you under the terms described in the
// ICE_LICENSE file included in this distribution.
//
// **********************************************************************

#include <Ice/Ice.h>
#include <TestCommon.h>
#include <Test.h>

using namespace std;
using namespace Test;

void
allTests()
{
    cout << "testing default values... " << flush;

    {
        Struct1 v;
        test(!v.boolFalse);
        test(v.boolTrue);
        test(v.b == 254);
        test(v.s == 16000);
        test(v.i == 3);
        test(v.l == 4);
        test(v.f == static_cast<float>(5.1));
        test(v.d == 6.2);
        test(v.str == "foo \\ \"bar\n \r\n\t\v\f\a\b? \007 \x07");
        test(v.c1 == red);
        test(v.c2 == green);
        test(v.c3 == blue);
        test(v.nc1 == Nested::red);
        test(v.nc2 == Nested::green);
        test(v.nc3 == Nested::blue);
        test(v.noDefault.empty());
    }

    {
        Struct2 v;
        test(v.boolTrue == ConstBool);
        test(v.b == ConstByte);
        test(v.s == ConstShort);
        test(v.i == ConstInt);
        test(v.l == ConstLong);
        test(v.f == ConstFloat);
        test(v.d == ConstDouble);
        test(v.str == ConstString);
        test(v.c1 == ConstColor1);
        test(v.c2 == ConstColor2);
        test(v.c3 == ConstColor3);
        test(v.nc1 == ConstNestedColor1);
        test(v.nc2 == ConstNestedColor2);
        test(v.nc3 == ConstNestedColor3);
    }

    {
        Struct3Ptr v = new Struct3;
        test(!v->boolFalse);
        test(v->boolTrue);
        test(v->b == 1);
        test(v->s == 2);
        test(v->i == 3);
        test(v->l == 4);
        test(v->f == static_cast<float>(5.1));
        test(v->d == 6.2);
        test(v->str == "foo \\ \"bar\n \r\n\t\v\f\a\b? \007 \x07");
        test(v->c1 == red);
        test(v->c2 == green);
        test(v->c3 == blue);
        test(v->nc1 == Nested::red);
        test(v->nc2 == Nested::green);
        test(v->nc3 == Nested::blue);
        test(v->noDefault.empty());
    }

    {
        BasePtr v = new Base;
        test(!v->boolFalse);
        test(v->boolTrue);
        test(v->b == 1);
        test(v->s == 2);
        test(v->i == 3);
        test(v->l == 4);
        test(v->f == static_cast<float>(5.1));
        test(v->d == 6.2);
        test(v->str == "foo \\ \"bar\n \r\n\t\v\f\a\b? \007 \x07");
        test(v->noDefault.empty());
    }

    {
        DerivedPtr v = new Derived;
        test(!v->boolFalse);
        test(v->boolTrue);
        test(v->b == 1);
        test(v->s == 2);
        test(v->i == 3);
        test(v->l == 4);
        test(v->f == static_cast<float>(5.1));
        test(v->d == 6.2);
        test(v->str == "foo \\ \"bar\n \r\n\t\v\f\a\b? \007 \x07");
        test(v->noDefault.empty());
        test(v->c1 == red);
        test(v->c2 == green);
        test(v->c3 == blue);
        test(v->nc1 == Nested::red);
        test(v->nc2 == Nested::green);
        test(v->nc3 == Nested::blue);
    }

    {
        BaseEx v;
        test(!v.boolFalse);
        test(v.boolTrue);
        test(v.b == 1);
        test(v.s == 2);
        test(v.i == 3);
        test(v.l == 4);
        test(v.f == static_cast<float>(5.1));
        test(v.d == 6.2);
        test(v.str == "foo \\ \"bar\n \r\n\t\v\f\a\b? \007 \x07");
        test(v.noDefault.empty());
    }

    {
        DerivedEx v;
        test(!v.boolFalse);
        test(v.boolTrue);
        test(v.b == 1);
        test(v.s == 2);
        test(v.i == 3);
        test(v.l == 4);
        test(v.f == static_cast<float>(5.1));
        test(v.d == 6.2);
        test(v.str == "foo \\ \"bar\n \r\n\t\v\f\a\b? \007 \x07");
        test(v.noDefault.empty());
        test(v.c1 == red);
        test(v.c2 == green);
        test(v.c3 == blue);
        test(v.nc1 == Nested::red);
        test(v.nc2 == Nested::green);
        test(v.nc3 == Nested::blue);
    }

    cout << "ok" << endl;
}