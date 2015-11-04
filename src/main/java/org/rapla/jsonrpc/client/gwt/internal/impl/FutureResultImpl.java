package org.rapla.jsonrpc.client.gwt.internal.impl;

import org.rapla.jsonrpc.common.AsyncCallback;
import org.rapla.jsonrpc.common.FutureResult;

public class FutureResultImpl<T> implements FutureResult<T> {
	
    JsonCall<T> call;
	
	public T get() throws Exception
	{
	    final T result = call.sendSynchronized( );
        return result;
	}
	
	public void get(AsyncCallback<T> callback)
	{
		call.send(callback);
	}
	
	public void setCall( JsonCall<T> call)
	{
		this.call = call;
	}
}
