package org.rapla.inject.server;

import org.rapla.jsonrpc.server.WebserviceCreator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface TestServer
{
    void service(HttpServletRequest request, HttpServletResponse response, Map<String,WebserviceCreator> webserviceMap) throws Exception;
}
