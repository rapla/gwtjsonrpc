package org.rapla.common;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.rapla.jsonrpc.common.FutureResult;

@Path("ExampleWithParameterArray")
public interface ExampleWithParameterArray
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    FutureResult<Integer[]> arrayTest(@HeaderParam("ids") String[] ids);
}