/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.inject.raplainject;

import com.google.gwt.dev.util.collect.HashSet;
import com.google.gwt.editor.client.Editor.Path;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.Disposable;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.rest.server.Injector;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Simple jsr 330 dependency injection container, that supports annotation loading via
 */
public class SimpleRaplaInjector
{

    public static class RaplaContainerContextException extends Exception
    {
        public RaplaContainerContextException(String text)
        {
            super(text);
        }
        public RaplaContainerContextException(String text,Exception ex)
        {
            super(text, ex);
        }
    }

    private List<ComponentHandler> m_componentHandler = Collections.synchronizedList(new ArrayList<ComponentHandler>());
    private Map<String, RoleEntry> m_roleMap = Collections.synchronizedMap(new LinkedHashMap<String, RoleEntry>());
    private Logger logger;
    private Map<String, Object> singletonMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Class, Semaphore> instanciating = new ConcurrentHashMap<>();

    public SimpleRaplaInjector(Logger logger)
    {
        this.logger = logger;
        addComponentInstance(Logger.class, logger);
    }

    public <T> T inject(Class<T> component, Object... params) throws RaplaContainerContextException
    {
        try
        {
            T result = instanciate(component, 0, params);
            return result;
        }
        catch (Exception e)
        {
            throw new RaplaContainerContextException(e.getMessage(), e);
        }
    }

    public <T> void injectMembers(T instance, Class<T> injectorClass, Object... params) throws Exception
    {
        Set<Field> fields = new HashSet<>();
        {
            Class aClass = injectorClass;
            while (aClass != null)
            {
                fields.addAll(Arrays.asList(aClass.getDeclaredFields()));
                aClass = aClass.getSuperclass();
            }
        }
        List<Object> additionalParams = Arrays.asList( params);
        for ( Field field:fields)
        {
            if ( field.getAnnotation(Inject.class) == null)
            {
                continue;
            }
            int depth = 0;
            final Class<?> type = field.getType();

            final Annotation[] annotations = field.getAnnotations();

            final Type genericType = field.getGenericType();
            boolean throwSingletonExceptionOnMatchingParam = false;
            Object p = resolveParam(depth, additionalParams, type, annotations, genericType, throwSingletonExceptionOnMatchingParam);
            field.setAccessible( true );
            field.set( instance,p);
        }

    }

    public Logger getLogger()
    {
        return logger;
    }

    public <T, I extends T> void addComponent(Class<T> roleInterface, Class<I> implementingClass)
    {
        addComponent(roleInterface, implementingClass, null);
    }

    public <T, I extends T> void addComponentInstance(Class<T> roleInterface, I implementingInstance)
    {
        addComponentInstance(roleInterface, implementingInstance, implementingInstance.toString());
    }

    protected <T, I extends T> void addComponentInstance(Class<T> roleInterface, I implementingInstance, String hint)
    {
        addComponentInstance(roleInterface.getName(), implementingInstance, hint);
    }

    protected <T, I extends T> void addComponent(Class<T> roleInterface, Class<I> implementingClass, String hint)
    {
        addComponentPrivate(roleInterface.getName(), implementingClass, hint);
    }

    protected <T> T getInstance(Class<T> componentRole, Object... params) throws RaplaContainerContextException
    {
        String key = componentRole.getName();//+ "/" + hint;
        ComponentHandler<T> handler = getHandler(key);
        if (handler != null)
        {
            try
            {
                if (handler instanceof RequestComponentHandler)
                {
                    RequestComponentHandler<T> handler1 = (RequestComponentHandler) handler;
                    T o = handler1.get(0, params);
                    return o;
                }
                else
                {
                    return handler.get(0);
                }
            }
            catch (RaplaContainerContextException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                throw new RaplaContainerContextException(ex.getMessage(), ex);
            }
        }
        throw new RaplaContainerContextException(key);
    }

    protected boolean has(Class componentRole, String hint)
    {
        String key = componentRole.getName();
        if (hint != null)
        {
            key += "/" + hint;
        }
        ComponentHandler handler = getHandler(key);

        if (handler != null)
        {
            return true;
        }
        //        Constructor injectableConstructor = findInjectableConstructor(componentRole);
        //        if ( injectableConstructor != null)
        //        {
        //            return true;
        //        }
        return false;
    }


    protected Object lookupPrivateWithNull(String role, int depth) throws RaplaContainerContextException
    {
        ComponentHandler handler = getHandler(role);
        if (handler != null)
        {
            try
            {
                return handler.get(depth);
            }
            catch (Exception e)
            {
                throw new RaplaContainerContextException(e.getMessage(), e);
            }
        }
        return null;
    }

    protected <T> T lookup(Class<T> clazz) throws RaplaContainerContextException
    {
        return myLookup(clazz, 0);
    }

    private <T> T myLookup(Class<T> clazz, int depth) throws RaplaContainerContextException
    {
        String role = clazz.getName();
        ComponentHandler handler = getHandler(role);
        if (handler != null)
        {
            try
            {
                return (T) handler.get(depth);
            }
            catch (Exception e)
            {
                throw new RaplaContainerContextException(e.getMessage(), e);
            }
        }
        throw new RaplaContainerContextException(" Implementation not found for " +clazz.getName());
    }

    protected <T> Set<T> lookupServicesFor(Class<T> role, int depth) throws RaplaContainerContextException
    {
        Map<String, T> map = lookupServiceMapFor(role, depth);
        Set<T> result = new LinkedHashSet<T>(map.values());
        return result;
    }

    synchronized private void addComponentPrivate(String role, Class implementingClass, String hint)
    {
        if (implementingClass.getAnnotation(Singleton.class) != null)
        {
            ComponentHandler handler = new SingletonComponentHandler(implementingClass);
            addHandler(role, hint, handler);
        }
        else
        {
            ComponentHandler handler = new RequestComponentHandler(implementingClass);
            addHandler(role, hint, handler);
        }

    }

    synchronized private void addComponentInstance(String role, Object componentInstance, String hint)
    {
        addHandler(role, hint, new SingletonComponentHandler(componentInstance));
    }

    synchronized private <T> void addRequestComponent(Class<T> role, Class<? extends T> implementingClass, String hint)
    {
        ComponentHandler handler = new RequestComponentHandler(implementingClass);
        addHandler(role.getCanonicalName(), hint, handler);
    }

    private <T> Map<String, T> lookupServiceMapFor(Class clazz, int depth)
    {
        String className = clazz.getName();
        RoleEntry entry = m_roleMap.get(className);
        if (entry == null)
        {
            return Collections.emptyMap();
        }
        Map<String, T> result = new LinkedHashMap<String, T>();
        Set<String> hintSet = entry.getHintSet();
        for (String hint : hintSet)
        {
            ComponentHandler handler = entry.getHandler(hint);
            try
            {
                Object service = handler.get(depth);
                // we can safely cast here because the method is only called from checked methods
                @SuppressWarnings("unchecked") T casted = (T) service;
                result.put(hint, casted);
            }
            catch (Exception e)
            {
                Throwable ex = e;
                while (ex.getCause() != null)
                {
                    ex = ex.getCause();
                }
                getLogger().error("Could not initialize component " + handler + " due to " + ex.getMessage() + " removing from service list", e);
                entry.remove(hint);
            }
        }
        return result;
    }

    private <T> Set<Provider<T>> lookupServiceSetProviderFor(ParameterizedType paramType)
    {
        final Map<String, Provider<T>> stringProviderMap = lookupServiceMapProviderFor(paramType);
        final Collection<Provider<T>> values = stringProviderMap.values();
        return new LinkedHashSet<>(values);
    }

    private <T> Map<String, Provider<T>> lookupServiceMapProviderFor(ParameterizedType paramType)
    {
        Type[] actualTypeArguments = paramType.getActualTypeArguments();
        Class<? extends Type> interfaceClass = null;
        if (actualTypeArguments.length > 0)
        {
            final Type param = actualTypeArguments[0];
            if (param instanceof Class)
            {
                interfaceClass = (Class) param;
            }
            else if (param instanceof ParameterizedType && ((ParameterizedType) param).getRawType() instanceof Class)
            {
                interfaceClass = (Class) ((ParameterizedType) param).getRawType();
            }
            else
            {
                throw new IllegalStateException("Provider can't be created. " + param + " is not a class ");
            }
        }
        else
        {
            throw new IllegalStateException("Provider  can't be created no type specified.");
        }

        RoleEntry entry = m_roleMap.get(interfaceClass.getName());
        if (entry == null)
        {
            return Collections.emptyMap();
        }
        Map<String, Provider<T>> result = new LinkedHashMap<String, Provider<T>>();
        Set<String> hintSet = entry.getHintSet();
        for (String hint : hintSet)
        {
            final ComponentHandler handler = entry.getHandler(hint);
            Provider<T> p = new Provider<T>()
            {
                @Override public T get()
                {
                    try
                    {
                        Object service = handler.get(0);
                        @SuppressWarnings("unchecked") T casted = (T) service;
                        return casted;
                    }
                    catch (Exception e)
                    {
                        Throwable ex = e;
                        while (ex.getCause() != null)
                        {
                            ex = ex.getCause();
                        }
                        final String message = "Could not initialize component " + handler + " due to " + ex.getMessage() + " removing from service list";
                        getLogger().error(message, e);
                        throw new IllegalStateException(message, e);
                    }
                }
            };

            // we can safely cast here because the method is only called from checked methods
            result.put(hint, p);
        }
        return result;
    }

    /**
     * @param roleName
     * @param hint
     * @param handler
     */
    private void addHandler(String roleName, String hint, ComponentHandler handler)
    {
        m_componentHandler.add(handler);
        RoleEntry entry = m_roleMap.get(roleName);
        if (entry == null)
            entry = new RoleEntry(roleName);
        entry.put(hint, handler);
        m_roleMap.put(roleName, entry);
    }

    private ComponentHandler getHandler(String role)
    {
        int hintSeperator = role.indexOf('/');
        String roleName = role;
        String hint = null;
        if (hintSeperator > 0)
        {
            roleName = role.substring(0, hintSeperator);
            hint = role.substring(hintSeperator + 1);
        }
        RoleEntry entry = m_roleMap.get(roleName);
        if (entry == null)
        {
            return null;
        }

        ComponentHandler handler = entry.getHandler(hint);
        if (handler != null)
        {
            return handler;
        }
        if (hint == null || hint.equals("*"))
            return entry.getFirstHandler();
        // Try the first accessible handler
        return null;

    }

    class RoleEntry
    {
        Map<String, ComponentHandler> componentMap = Collections.synchronizedMap(new LinkedHashMap<String, ComponentHandler>());
        ComponentHandler firstEntry;
        int generatedHintCounter = 0;
        String roleName;

        RoleEntry(String roleName)
        {
            this.roleName = roleName;
        }

        String generateHint()
        {
            return roleName + "_" + generatedHintCounter++;
        }

        void put(String hint, ComponentHandler handler)
        {
            if (hint == null)
            {
                hint = generateHint();
            }
            synchronized (this)
            {
                componentMap.put(hint, handler);
            }
            if (firstEntry == null)
                firstEntry = handler;
        }

        void remove(String hint)
        {
            componentMap.remove(hint);
        }

        Set<String> getHintSet()
        {
            // we return a clone to avoid concurrent modification exception
            synchronized (this)
            {
                LinkedHashSet<String> result = new LinkedHashSet<String>(componentMap.keySet());
                return result;
            }
        }

        ComponentHandler getHandler(String hint)
        {
            return componentMap.get(hint);
        }

        ComponentHandler getFirstHandler()
        {
            return firstEntry;
        }

        public String toString()
        {
            return componentMap.toString();
        }

    }

    boolean disposing;

    public void dispose()
    {
        getLogger().info("Shutting down rapla-container");
        // prevent reentrence in dispose
        synchronized (this)
        {
            if (disposing)
            {
                getLogger().warn("Disposing is called twice", new RaplaContainerContextException(""));
                return;
            }
            disposing = true;
        }
        try
        {
            removeAllComponents();
        }
        finally
        {
            disposing = false;
        }
    }

    protected void removeAllComponents()
    {

        ArrayList<ComponentHandler> componentHandlers = new ArrayList<ComponentHandler>(m_componentHandler);
        for (ComponentHandler comp : componentHandlers)
        {
            if (comp instanceof Disposable)
            {
                ((Disposable) comp).dispose();
            }
        }
        m_componentHandler.clear();
        m_roleMap.clear();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" }) private Constructor findInjectableConstructor(Class componentClass)
    {
        Constructor[] constructors = componentClass.getConstructors();
        Constructor emptyPublic = null;
        for (Constructor constructor : constructors)
        {
            Class[] types = constructor.getParameterTypes();
            boolean compatibleParameters = true;
            if (constructor.getAnnotation(Inject.class) != null)
            {
                return constructor;
            }
            if (types.length == 0)
            {
                if (Modifier.isPublic(constructor.getModifiers()))
                {
                    emptyPublic = constructor;
                }
            }
        }
        return emptyPublic;
    }

    /** Instantiates a class and passes the config, logger and the parent context to the object if needed by the constructor.
     * This concept is taken form pico container.*/

    @SuppressWarnings({ "rawtypes", "unchecked" }) protected <T> T instanciate(Class<T> componentClass, int depth, final Object... additionalParamObject)
            throws Exception
    {
        depth++;
        String componentClassName = componentClass.getName();
        final boolean isSingleton = componentClass.getAnnotation(Singleton.class) != null;

        if (depth > 40)
        {
            throw new IllegalStateException("Dependency cycle while injection " + componentClassName + " aborting!");
        }
        if (isSingleton)
        {

            Object singleton = singletonMap.get(componentClassName);
            if (singleton != null)
            {
                return (T) singleton;
            }
            else
            {
                try
                {
                    final Semaphore result = instanciating.putIfAbsent(componentClass, new Semaphore(0));
                    if (result != null)
                    {
                        result.acquire();
                        final Object object = singletonMap.get(componentClassName);
                        if (object != null)
                        {
                            result.release();
                            return (T) object;
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    throw new IllegalStateException("Timeout while waiting for instanciation of " + componentClass.getName());
                }
            }
        }
        try
        {
            final List<Object> additionalParams = new ArrayList<Object>(Arrays.asList(additionalParamObject));
            Constructor c = findInjectableConstructor(componentClass);
            if (c == null)
            {
                throw new IllegalStateException("No javax.inject.Inject Annotation or public default contructor found in class " + componentClass);
            }
            Object[] params = resolveParams(depth, additionalParams, c, isSingleton);
            final Object component = c.newInstance(params);
            if (isSingleton)
            {
                singletonMap.put(componentClassName, component);
            }
            return (T) component;
        }
        catch (IllegalStateException e)
        {
            throw e;
        }
        catch (InvocationTargetException e)
        {
            final Throwable targetException = e.getTargetException();
            if (targetException instanceof Exception)
            {
                throw (Exception) targetException;
            }
            if (targetException instanceof Error)
            {
                throw (Error) targetException;
            }
            else
            {
                throw new IllegalStateException(targetException);
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException(componentClassName + " could not be initialized due to " + e.getMessage(), e);
        }
        finally
        {
            if (isSingleton)
            {
                instanciating.get(componentClass).release();
            }
        }
    }

    private Object[] resolveParams(int depth, List<Object> additionalParams, Constructor c, boolean throwSingletonExceptionOnMatchingParam)
            throws Exception
    {
        Object[] params = null;
        Class[] types = c.getParameterTypes();
        Type[] genericParameterTypes = c.getGenericParameterTypes();
        Annotation[][] parameterAnnotations = c.getParameterAnnotations();
        params = new Object[types.length];
        for (int i = 0; i < types.length; i++)
        {
            final Class type = types[i];
            Annotation[] annotations = parameterAnnotations[i];
            final Type genericType = genericParameterTypes[i];
            Object p = resolveParam(depth, additionalParams, type, annotations, genericType, throwSingletonExceptionOnMatchingParam);
            params[i] = p;
        }
        return params;
    }

    private Object resolveParam(int depth, List<Object> additionalParams, Type type, Annotation[] annotations, Type genericType,
            boolean throwSingletonExceptionOnMatchingParam) throws Exception
    {
        for (Annotation annotation : annotations)
        {
            if (annotation.annotationType().equals(Named.class))
            {
                String value = ((Named) annotation).value();
                final String id = value.intern();
                Object lookup = lookupPrivateWithNull(id, depth);
                if (lookup == null)
                {
                    throw new RaplaContainerContextException("No constant found for id " + id + " with name " + value);
                }
                return lookup;
            }
        }

        final boolean isParameterizedTyped = genericType instanceof ParameterizedType;
        if (isParameterizedTyped)
        {
            final ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Object result = resolveParameterized(depth, parameterizedType);
            if (result != null)
            {
                return result;
            }
            type = parameterizedType.getRawType();
        }
        if (!(type instanceof Class))
        {
            throw new IllegalStateException("Param of type " + type.toString() + " can't be injected it is not a class ");
        }
        Class guessedRole = (Class) type;
        if (has(guessedRole, null))
        {
            Object p = lookup(guessedRole);
            return p;
        }
        else
        {
            for (int j = 0; j < additionalParams.size(); j++)
            {
                Object additional = additionalParams.get(j);
                Class<?> aClass = additional.getClass();
                if (guessedRole.isAssignableFrom(aClass))
                {
                    // we need to remove the additional params we used, to prevent to inject the same param twice
                    // e.g. MyClass(Date startDate, Date endDate)
                    if (throwSingletonExceptionOnMatchingParam)
                    {
                        throw new RaplaContainerContextException("Additional Param can't be injected for singletons");
                    }
                    additionalParams.remove(j);
                    return additional;
                }
            }
        }
        Object p = instanciate(guessedRole, depth);
        return p;
    }

    private Object resolveParameterized(final int depth, ParameterizedType parameterizedType) throws RaplaContainerContextException
    {
        final Object result;
        String typeName = parameterizedType.getRawType().getTypeName();
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments.length == 0)
        {
            throw new IllegalStateException("Paramater of generic " + typeName + " must be specified for injection ");
        }
        //Provider<Provider<Class>>
        if (typeName.equals("javax.inject.Provider"))
        {
            final Type param = actualTypeArguments[0];
            result = new Provider()
            {
                @Override public Object get()
                {
                    try
                    {

                        Annotation[] annotations = new Annotation[0];
                        Type genericType = param;
                        final boolean throwSingletonExceptionOnMatchingParam = false;
                        final List additionalParams = Collections.EMPTY_LIST;
                        // we add the depth here to prevent cycles introduced by calling get in the constructor
                        return resolveParam(depth, additionalParams, param, annotations, genericType, throwSingletonExceptionOnMatchingParam);
                    }
                    catch (IllegalStateException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                }

            };

        }
        else if (typeName.equals("java.util.Set"))
        {
            final Type valueParam = actualTypeArguments[0];
            if (valueParam instanceof Class)
            {
                final Class<? extends Type> class1 = (Class<? extends Type>) valueParam;
                result = lookupServicesFor(class1, depth);
            }
            else if (valueParam instanceof ParameterizedType)
            {
                final ParameterizedType paramType = (ParameterizedType) valueParam;
                if (!paramType.getRawType().toString().equals("javax.inject.Provider"))
                {
                    if (paramType.getRawType() instanceof Class)
                    {
                        final Class<? extends Type> class1 = (Class<? extends Type>) paramType.getRawType();
                        result = lookupServicesFor(class1, depth);
                    }
                    else
                    {
                        throw new RaplaContainerContextException("Can't instanciate parameterized Set or Map for generic type " + paramType);
                    }
                }
                else
                {
                    result = lookupServiceSetProviderFor(paramType);
                }
            }
            else
            {
                throw new IllegalStateException("Can't statisfy constructor dependency  unknown type " + valueParam);
            }
        }
        else if (typeName.equals("java.util.Map"))
        {
            if (actualTypeArguments.length > 1)
            {
                final Type keyParam = actualTypeArguments[0];
                if (!(keyParam.equals(String.class)))
                {
                    throw new IllegalStateException("Can't statisfy constructor dependency java.util.Map is only supported for String keys.");
                }
                {
                    final Type valueParam = actualTypeArguments[1];
                    if (valueParam instanceof Class)
                    {
                        final Class<? extends Type> paramClass = (Class<? extends Type>) valueParam;
                        result = lookupServiceMapFor(paramClass, depth);
                    }
                    else if (valueParam instanceof ParameterizedType)
                    {
                        final ParameterizedType paramType = (ParameterizedType) valueParam;
                        if (!paramType.getRawType().toString().equals("javax.inject.Provider"))
                        {
                            if (paramType.getRawType() instanceof Class)
                            {
                                final Class<? extends Type> class1 = (Class<? extends Type>) paramType.getRawType();
                                result = lookupServiceMapFor(class1, depth);
                            }
                            else
                            {
                                throw new RaplaContainerContextException("Can't instanciate parameterized Set or Map for generic type " + paramType);
                            }
                        }
                        else
                        {
                            result = lookupServiceMapProviderFor(paramType);
                        }
                    }
                    else
                    {
                        throw new IllegalStateException("Can't statisfy constructor dependency for unknown type " + valueParam);
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Can't statisfy constructor dependency untyped java.util.Map is not supported. ");
            }
        }
        else
        {
            result = null;
        }
        return result;
    }

    abstract private class ComponentHandler<T>
    {
        protected Class componentClass;

        abstract T get(int depth) throws Exception;
    }

    private class RequestComponentHandler<T> extends ComponentHandler<T>
    {
        protected RequestComponentHandler(Class<T> componentClass)
        {
            this.componentClass = componentClass;

        }

        @Override T get(int depth) throws Exception
        {
            Object component = instanciate(componentClass, depth);
            return (T) component;
        }

        T get(int depth, Object... params) throws Exception
        {
            Object component = instanciate(componentClass, depth, params);
            return (T) component;
        }
    }

    private class SingletonComponentHandler extends ComponentHandler implements Disposable
    {
        protected Object component;
        boolean dispose = true;

        protected SingletonComponentHandler(Object component)
        {
            this.component = component;
            this.dispose = false;
        }

        protected SingletonComponentHandler(Class componentClass)
        {
            this.componentClass = componentClass;
        }

        Object get(int depth) throws Exception
        {
            if (component != null)
            {
                return component;
            }
            component = instanciate(componentClass, depth);
            return component;
        }

        boolean disposing;

        public void dispose()
        {
            // prevent reentrence in dispose
            synchronized (this)
            {
                if (disposing)
                {
                    getLogger().warn("Disposing is called twice", new RaplaContainerContextException(""));
                    return;
                }
                disposing = true;
            }
            try
            {
                if (component instanceof Disposable)
                {
                    if (component == SimpleRaplaInjector.this)
                    {
                        return;
                    }
                    ((Disposable) component).dispose();
                }
            }
            catch (Exception ex)
            {
                getLogger().error("Error disposing component ", ex);
            }
            finally
            {
                disposing = false;
            }
        }

        public String toString()
        {
            if (component != null)
            {
                return component.toString();
            }
            if (componentClass != null)
            {
                return componentClass.getName();
            }
            return super.toString();
        }
    }

    public void initFromMetaInfService(InjectionContext baseContext) throws IOException
    {
        String folder = org.rapla.inject.generator.AnnotationInjectionProcessor.MODULE_LIST;

        Set<String> interfaces = new TreeSet<String>();
        Collection<URL> resources = find(folder);
        if (resources.isEmpty())
        {
            getLogger().error("Service list " + folder + " not found or empty.");
        }
        for (URL url : resources)
        {
            final InputStream modules = url.openStream();
            final BufferedReader br = new BufferedReader(new InputStreamReader(modules, "UTF-8"));
            String module = null;
            while ((module = br.readLine()) != null)
            {
                interfaces.add(module);
            }
            br.close();
        }
        for (String module : interfaces)
        {
            Class<?> interfaceClass;
            try
            {
                interfaceClass = Class.forName(module);
            }
            catch (ClassNotFoundException e1)
            {
                final int i = module.lastIndexOf(".");
                if (i >= 0)
                {
                    StringBuilder builder = new StringBuilder(module);
                    final StringBuilder innerClass = builder.replace(i, i + 1, "$");
                    try
                    {
                        final String className = innerClass.toString();
                        interfaceClass = Class.forName(className);
                    }
                    catch (ClassNotFoundException e2)
                    {
                        logger.warn("Found interfaceName definition but no class for " + module);
                        continue;
                    }
                }
                else
                {
                    logger.warn("Found interfaceName definition but no class for " + module);
                    continue;
                }
            }
            addImplementations(baseContext,interfaceClass);
        }
    }

    private Collection<URL> find(String fileWithfolder) throws IOException
    {

        List<URL> result = new ArrayList<URL>();
        Enumeration<URL> resources = this.getClass().getClassLoader().getResources(fileWithfolder);
        while (resources.hasMoreElements())
        {
            result.add(resources.nextElement());
        }
        return result;
    }

    private static Collection<String> getImplementingIds(Class interfaceClass, Collection<Extension> clazzAnnot)
    {
        Set<String> ids = new LinkedHashSet<>();
        for (Extension ext : clazzAnnot)
        {
            final Class provides = ext.provides();
            if (provides.equals(interfaceClass))
            {
                String id = ext.id();
                ids.add(id);
            }
        }
        return ids;
    }

    private boolean isImplementing(Class interfaceClass, Collection<DefaultImplementation> clazzAnnot, InjectionContext baseContext)
    {
        for (DefaultImplementation ext : clazzAnnot)
        {
            final Class provides = ext.of();
            final InjectionContext[] context = ext.context();
            if (provides.equals(interfaceClass) && baseContext.isSupported(context))
            {
                return true;
            }
        }
        return false;
    }


    public  Injector getMembersInjector()
    {
        final Injector injector = new Injector()
        {
            @Override public void injectMembers(Object instance) throws  Exception
            {
                final Class aClass = instance.getClass();
                SimpleRaplaInjector.this.injectMembers(instance, aClass);
            }
        };
        return injector;
    }
    public <T> Injector<T> getMembersInjector(Class<T> injectorClass)
    {
        final Injector<T> injector = new Injector<T>()
        {
            @Override public void injectMembers(T instance) throws  Exception
            {
                SimpleRaplaInjector.this.injectMembers(instance, injectorClass);
            }
        };
        return injector;
    }



    private <T> void addImplementations(InjectionContext baseContext,Class<T> interfaceClass) throws IOException
    {
        final ExtensionPoint extensionPointAnnotation = interfaceClass.getAnnotation(ExtensionPoint.class);
        final Path pathAnnotation = interfaceClass.getAnnotation(Path.class);
        final boolean isExtensionPoint = extensionPointAnnotation != null;
        final boolean isRemoteMethod = false;//remoteJsonMethodAnnotation != null;
        if (isExtensionPoint)
        {
            final InjectionContext[] context = extensionPointAnnotation.context();
            if (!baseContext.isSupported(context))
            {
                return;
            }
        }

        final String folder = "META-INF/services/";
        boolean foundExtension = false;
        boolean foundDefaultImpl = false;
        // load all implementations or extensions from service list file
        Set<String> implemantations = new LinkedHashSet<String>();
        final String interfaceName = interfaceClass.getCanonicalName();
        Collection<URL> resources = find(folder + interfaceName);
        for (URL url : resources)
        {
            //final URL def = moduleDefinition.nextElement();
            final InputStream in = url.openStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String implementationClassName = null;
            boolean implOrExtensionFound = false;
            while ((implementationClassName = reader.readLine()) != null)
            {
                try
                {
                    if (implemantations.contains(implementationClassName))
                    {
                        continue;
                    }
                    else
                    {
                        implemantations.add(implementationClassName);
                    }
                    // load class for implementation or extension
                    final Class<T> clazz = (Class<T>) Class.forName(implementationClassName);
                    final Class<Extension> annotationClass = Extension.class;
                    final Collection<Extension> extensions = getAnnotationsByType(clazz, annotationClass);

                    Collection<String> idList = getImplementingIds(interfaceClass, extensions);
                    // add extension implementations
                    if (idList.size() > 0)
                    {

                        for (String id : idList)
                        {
                            foundExtension = true;
                            addComponent(interfaceClass, clazz, id);
                            getLogger().info("Found extension for " + interfaceName + " : " + implementationClassName);
                        }
                    }
                    else
                    {
                        if (isExtensionPoint)
                        {
                            logger.warn(clazz + " provides no extension for " + interfaceName + " but is in the service list of " + interfaceName
                                    + ". You may need run a clean build.");
                        }
                    }
                    // add default implementations
                    final Class<DefaultImplementation> annotationClass2 = DefaultImplementation.class;
                    final Collection<DefaultImplementation> defaultImplementations = getAnnotationsByType(clazz, annotationClass2);
                    final boolean implementing = isImplementing(interfaceClass, defaultImplementations, baseContext);
                    if (implementing)
                    {
                        addComponent(interfaceClass, clazz, null);
                        getLogger().info("Found implementation for " + interfaceName + " : " + implementationClassName);
                        foundDefaultImpl = true;
                        // not necessary in current impl
                        //src.println("binder.bind(" + interfaceName + ".class).to(" + implementationClassName + ".class).in(Singleton.class);");
                    }
                    if (isRemoteMethod && implementationClassName.endsWith("JavaJsonProxy"))
                    {
                        String id = pathAnnotation.value();
                        if (id == null || id.isEmpty())
                        {
                            id = interfaceName;
                        }
                        addRequestComponent(interfaceClass, clazz, id);
                    }

                }
                catch (Throwable e)
                {
                    logger.warn("Error loading implementationClassName (" + implementationClassName + ") for " + interfaceName + " from file " + url);
                }

            }
            reader.close();
        }

        if (isExtensionPoint)
        {
            if (!foundExtension)
            {
                // not necessary to create a default Binding
            }
        }
        else
        {
            if (!foundDefaultImpl)
            {
                logger.warn( "No DefaultImplementation found for " + interfaceName + " Interface will not be available in the supported Contexts " + baseContext  + " ");
            }
        }
    }

    private <T> Collection<T> getAnnotationsByType(final Class clazz, final Class<T> annotationClass2)
    {
        final Annotation[] annotations = clazz.getAnnotations();
        List<T> annotationList = new ArrayList<T>();
        for ( Annotation annotation:annotations)
        {
            final Class<? extends Annotation> aClass = annotation.annotationType();
            if ( aClass.equals( annotationClass2))
            {
                annotationList.add( (T)annotation);
            }
        }
        return annotationList;
    }

}

