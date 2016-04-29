package org.rapla.rest.server.provider.xml;

import org.rapla.rest.client.SerializableExceptionInformation;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Provider
@Produces(MediaType.APPLICATION_XML)
public class XmlWriter<T> implements MessageBodyWriter<T>
{

    private final Map<Class<?>, JAXBContext> contexts = new HashMap<>();

    public XmlWriter()
    {
        try
        {
            contexts.put(Throwable.class, JAXBContext.newInstance(SerializableExceptionInformation.class));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Could not generate Exception context: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return type.getAnnotation(XmlRootElement.class) != null;
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
        try
        {
            if (t instanceof Throwable)
            {
                Throwable exception = (Throwable) t;
                final SerializableExceptionInformation exceptionXml = new SerializableExceptionInformation(exception);
                try
                {
                    final JAXBContext jaxbContext = contexts.get(Throwable.class);
                    jaxbContext.createMarshaller().marshal(exceptionXml, entityStream);
                }
                catch (Exception e)
                {
                    entityStream.write(e.getMessage().getBytes("UTF-8"));
                }
            }
            else
            {
                final Class<? extends Object> clazz = t.getClass();
                JAXBContext jaxbContext = contexts.get(clazz);
                if (jaxbContext == null)
                {
                    jaxbContext = JAXBContext.newInstance(clazz);
                    contexts.put(clazz, jaxbContext);
                }
                final Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.marshal(t, entityStream);
            }
        }
        catch (Exception e)
        {
            throw new IOException(e.getMessage(), e);
        }
    }
}
