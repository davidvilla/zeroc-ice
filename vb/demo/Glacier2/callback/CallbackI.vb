' **********************************************************************
'
' Copyright (c) 2003-2008 ZeroC, Inc. All rights reserved.
'
' This copy of Ice is licensed to you under the terms described in the
' ICE_LICENSE file included in this distribution.
'
' **********************************************************************

Imports Demo
Imports System

Public NotInheritable Class CallbackI
    Inherits CallbackDisp_

    Public Overloads Overrides Sub initiateCallback(ByVal proxy As CallbackReceiverPrx, ByVal current As Ice.Current)
        Console.WriteLine("initiating callback to: " + current.adapter.getCommunicator().proxyToString(proxy))
        Try
            proxy.callback(current.ctx)
        Catch ex As System.Exception
            Console.Error.WriteLine(ex)
        End Try
    End Sub

    Public Overloads Overrides Sub shutdown(ByVal current As Ice.Current)
        Console.WriteLine("Shutting down...")
        Try
            current.adapter.getCommunicator().shutdown()
        Catch ex As System.Exception
            Console.Error.WriteLine(ex)
        End Try
    End Sub

End Class