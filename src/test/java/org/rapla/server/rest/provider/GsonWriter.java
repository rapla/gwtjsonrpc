package org.rapla.server.rest.provider;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.rapla.rest.client.SerializableExceptionInformation;
import org.rapla.rest.client.swing.JSONParserWrapper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class GsonWriter<T> implements MessageBodyWriter<T>
{

    final Gson gson = JSONParserWrapper.defaultGsonBuilder(new Class[]{}).create();
    private final HttpServletRequest request;
    
    public GsonWriter(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return true;
    }

    @Override
    public long getSize(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return -1;
    }

    @Override
    public void writeTo(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException
    {
        final String json;
        if( t instanceof Throwable)
        {
            Throwable exception = (Throwable)t;
            json = gson.toJson(new SerializableExceptionInformation(exception));
        }
        else
        {
            json = gson.toJson(t);
        }
        entityStream.write(json.getBytes("UTF-8"));
    }

}
