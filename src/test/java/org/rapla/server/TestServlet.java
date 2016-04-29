package org.rapla.server;

import dagger.MembersInjector;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.rapla.server.dagger.DaggerRaplaServerComponent;
import org.rapla.server.dagger.DaggerRaplaServerStartupModule;
import org.rapla.server.dagger.RaplaServerComponent;
import org.rapla.server.rest.RestTestApplication;
import org.rapla.server.rest.ResteasyMembersInjector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestServlet extends HttpServlet
{

    HttpServletDispatcher dispatcher;
    private Map<String, MembersInjector> membersInjector;

    protected String getPrefix()
    {
        return "";
    }
    @Override public void init(ServletConfig config) throws ServletException
    {
        System.out.println("Starting init");

        final ServletContext context = config.getServletContext();
        dispatcher = new HttpServletDispatcher();
        dispatcher.init(new ServletConfig()
        {
            @Override public String getServletName()
            {
                return config.getServletName();
            }

            @Override public ServletContext getServletContext()
            {
                return config.getServletContext();
            }

            @Override public String getInitParameter(String name)
            {
                switch ( name)
                {
                    case "resteasy.servlet.mapping.prefix": return getPrefix() + "/rapla";
                    case "resteasy.use.builtin.providers": return  "false";
                    case "javax.ws.rs.Application": return RestTestApplication.class.getCanonicalName();
                }
                return config.getInitParameter( name);
            }

            @Override public Enumeration<String> getInitParameterNames()
            {
                return config.getInitParameterNames();
            }
        });
        super.init(config);
        System.out.println("Init done ");
        StartupParams params  = new StartupParams();
        final DaggerRaplaServerStartupModule startupModule = new DaggerRaplaServerStartupModule(params);
        final RaplaServerComponent mod = DaggerRaplaServerComponent.builder().daggerRaplaServerStartupModule(startupModule).build();
        membersInjector = mod.getComponentStarter().getMembersInjector();
    }


    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException

    {

        System.out.println( "QueryString " +request.getQueryString() + " Test param " + request.getParameter("param"));
        request.setAttribute(ResteasyMembersInjector.RAPLA_CONTEXT, membersInjector);
        Map<String,String> headers = new LinkedHashMap<>();
        final Enumeration<String> headerNames = request.getHeaderNames();
        while ( headerNames.hasMoreElements())
        {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader( headerName));
        }
        System.out.println("service request full " + request.toString() + " uri: " + request.getRequestURI() + " context: " + request.getContextPath() + " \npathInfo " + request.getPathInfo() + " \nservlet path " + request.getServletPath() + " \nHeaders: " + headers.toString()) ;
        try
        {
            dispatcher.service(request, response);
        } catch (ServletException|IOException ex)
        {
            throw ex;
        } catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }


}