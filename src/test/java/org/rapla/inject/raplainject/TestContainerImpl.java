package org.rapla.inject.raplainject;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.logger.ConsoleLogger;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public class TestContainerImpl
{
    SimpleRaplaInjector container;

    @Before public void setup()
    {

        Logger logger = new ConsoleLogger()
        {
            @Override protected void write(int logLevel, String message, Throwable cause)
            {
                if (logLevel == LEVEL_ERROR)
                {
                    Assert.fail(message);
                }
                super.write(logLevel, message, cause);
            }
        };
        container = new SimpleRaplaInjector(logger);
    }

    @Test public void testProviderProvider() throws Exception
    {
        final MyClass<Integer> mzClass = container.inject(MyClass.class);
        final Provider<MyOtherClass<Integer>> myOtherClassProvider = mzClass.get();
        final MyOtherClass<Integer> myOtherClass = myOtherClassProvider.get();
        myOtherClass.doSomething(1);
    }

    public interface MyExtensionPoint<T>
    {
        void doSomething(T obj);
    }

    @Test public void testSetProvider() throws Exception
    {
        container.addComponent(MyExtensionPoint.class, MyOtherClass.class);
        container.addComponent(MyExtensionPoint.class, MyOtherClass2.class);
        final MyExtensionSetUserClass myClass = container.inject(MyExtensionSetUserClass.class);
        myClass.doAll();
    }

    @Test public void testMapProvider() throws Exception
    {
        container.addComponent(MyExtensionPoint.class, MyOtherClass.class);
        container.addComponentProvider(MyExtensionPoint.class, MyOtherClass2Provider.class);
        final MyExtensionMapUserClass myClass = container.inject(MyExtensionMapUserClass.class);
        myClass.doAll();
    }


    @Test public void testCylce() throws Exception
    {
        try
        {
            final Dep1 cycleClass = container.inject(Dep1.class);
            Assert.fail("Exception should be throwns");
        } catch (Exception ex)
        {
            Assert.assertTrue(ex.getMessage().toLowerCase().contains("cycle"));
        }
    }
    public static class Dep1
    {
        @Inject public Dep1(Dep2 test4)
        {

        }
    }

    public static class Dep2
    {
        @Inject public Dep2(Provider<Dep1> test4)
        {
            test4.get();
        }
    }

    public static class MyClass<T>
    {
        Provider<Provider<MyOtherClass<T>>> test;

        @Inject public MyClass(Provider<Provider<MyOtherClass<T>>> test)
        {
            this.test = test;
        }

        public Provider<MyOtherClass<T>> get()
        {
            return test.get();
        }
    }

    public static class MyOtherClass<T> implements MyExtensionPoint<T>
    {
        Logger logger;

        @Inject public MyOtherClass(Logger logger)
        {
            this.logger = logger;
        }

        public void doSomething(T ob)
        {
            logger.info("Something " + ob);
        }
    }

    public static class MyOtherClass2<T> implements MyExtensionPoint<T>
    {
        Logger logger;

        @Inject public MyOtherClass2(Logger logger)
        {
            this.logger = logger;
        }

        public void doSomething(T ob)
        {
            logger.info("Something2 " + ob);
        }
    }

    public static class MyOtherClass2Provider implements Provider<MyOtherClass2>
    {
        Logger logger;
        @Inject
        public MyOtherClass2Provider(Logger logger)
        {
            this.logger = logger;
        }

        public MyOtherClass2 get()
        {
            return new MyOtherClass2(logger);
        }
    }

    public static class MyExtensionSetUserClass
    {
        //private final Set<MyExtensionPoint<Integer>> set;
        private final Set<Provider<MyExtensionPoint>> setProvider;
        private final Provider<Set<MyExtensionPoint<String>>> providerSet;

        @Inject
        public MyExtensionSetUserClass(/*Set<MyExtensionPoint<Integer>> set,*/ Set<Provider<MyExtensionPoint>> setProvider,
                Provider<Set<MyExtensionPoint<String>>> providerSet)
        {
            //this.set = set;
            this.setProvider = setProvider;
            this.providerSet = providerSet;
        }

        public void doAll()
        {
            int i=0;
            /*
            for (MyExtensionPoint<Integer> p : set){
                p.doSomething(i++);
            }
            */
            for (Provider<MyExtensionPoint> p : setProvider){
                p.get().doSomething(i++);
            }
            for (MyExtensionPoint<String> p : providerSet.get()){
                p.doSomething("" + i++);
            }
        }
    }

    public static class MyExtensionMapUserClass
    {
        //private final Map<String, MyExtensionPoint<Integer>> set;
        private final Map<String, Provider<MyExtensionPoint>> setProvider;
        private final Provider<Map<String, MyExtensionPoint<String>>> providerSet;

        @Inject
        public MyExtensionMapUserClass(/*Map<String, MyExtensionPoint<Integer>> set,*/ Map<String, Provider<MyExtensionPoint>> setProvider,
                Provider<Map<String, MyExtensionPoint<String>>> providerSet)
        {
            //this.set = set;
            this.setProvider = setProvider;
            this.providerSet = providerSet;
        }

        public void doAll()
        {
            int i=0;
            /*
            for (MyExtensionPoint<Integer> p : set.values()){
                p.doSomething(i++);
            }
            */
            for (Provider<MyExtensionPoint> p : setProvider.values()){
                p.get().doSomething(i++);
            }
            final Map<String, MyExtensionPoint<String>> stringMyExtensionPointMap = providerSet.get();
            for (MyExtensionPoint<String> p : stringMyExtensionPointMap.values()){
                p.doSomething("" + i++);
            }
        }
    }
}
