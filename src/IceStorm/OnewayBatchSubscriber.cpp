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

#include <Ice/Ice.h>
#include <IceStorm/OnewayBatchSubscriber.h>
#include <IceStorm/TraceLevels.h>
#include <IceStorm/Flusher.h>

using namespace IceStorm;
using namespace std;

OnewayBatchSubscriber::OnewayBatchSubscriber(const SubscriberFactoryPtr& factory, const TraceLevelsPtr& traceLevels,
                                             const FlusherPtr& flusher, const QueuedProxyPtr& obj) :
    OnewaySubscriber(factory, traceLevels, obj),
    _flusher(flusher)
{
    _flusher->add(this);
}

OnewayBatchSubscriber::~OnewayBatchSubscriber()
{
}

void
OnewayBatchSubscriber::unsubscribe()
{
    IceUtil::Mutex::Lock sync(_stateMutex);
    _state = StateUnsubscribed;

    if(_traceLevels->subscriber > 0)
    {
	Ice::Trace out(_traceLevels->logger, _traceLevels->subscriberCat);
	out << "Unsubscribe " << id();
    }

    //
    // If this subscriber has been registered with the flusher then
    // remove it.
    //
    _flusher->remove(this);
}

void
OnewayBatchSubscriber::replace()
{
    IceUtil::Mutex::Lock sync(_stateMutex);
    _state = StateReplaced;

    if(_traceLevels->subscriber > 0)
    {
	Ice::Trace out(_traceLevels->logger, _traceLevels->subscriberCat);
	out << "Replace " << id();
    }

    //
    // If this subscriber has been registered with the flusher then
    // remove it.
    //
    _flusher->remove(this);
}

bool
OnewayBatchSubscriber::inactive() const
{
    return OnewaySubscriber::inactive();
}

void
OnewayBatchSubscriber::flush()
{
    try
    {
	_obj->proxy()->ice_flush();
    }
    catch(const Ice::LocalException& e)
    {
	IceUtil::Mutex::Lock sync(_stateMutex);
	//
	// It's possible that the subscriber was unsubscribed, or
	// marked invalid by another thread. Don't display a
	// diagnostic in this case.
	//
	if(_state == StateActive)
	{
	    if(_traceLevels->subscriber > 0)
	    {
		Ice::Trace out(_traceLevels->logger, _traceLevels->subscriberCat);
		out << id() << ": flush failed: " << e;
	    }
	    _state = StateError;
	}
    }
}

bool
OnewayBatchSubscriber::operator==(const Flushable& therhs) const
{
    const OnewayBatchSubscriber* rhs = dynamic_cast<const OnewayBatchSubscriber*>(&therhs);
    if(rhs != 0)
    {
	return id() == rhs->id();
    }
    return false;
}