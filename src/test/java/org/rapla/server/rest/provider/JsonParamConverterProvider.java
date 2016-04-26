package org.rapla.server.rest.provider;

import com.google.gson.Gson;
import org.rapla.rest.client.swing.JSONParserWrapper;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
public class JsonParamConverterProvider implements ParamConverterProvider
{

    private static final class JsonParamConverter<T> implements ParamConverter<T>
    {

        private Gson gson = JSONParserWrapper.defaultGsonBuilder(new Class[] {}).create();
        private Type genericType;

        public JsonParamConverter(Type genericType)
        {
            this.genericType = genericType;
        }

        @Override
        public T fromString(String value)
        {
            return gson.fromJson(value, genericType);
        }

        @Override
        public String toString(T value)
        {
            return gson.toJson(value);
        }

    }

    public JsonParamConverterProvider()
    {
    }

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations)
    {
        if (!rawType.isPrimitive())
        {
            return new JsonParamConverter<T>(genericType);
        }
        return null;
    }

}
