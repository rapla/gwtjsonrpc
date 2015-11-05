package org.rapla.client.gwt.test;

import dagger.Component;
import junit.framework.TestCase;
import org.rapla.inject.EmptyInjectionTest;
import org.rapla.inject.OtherInterface;
import org.rapla.inject.extension.ExampleExtensionPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;

public class MyAppTest extends TestCase
{
    @Component(modules = { org.rapla.dagger.DaggerRaplaGwtModule.class })
    @Singleton
    public interface GwtInjectionTest
    {
        Rapla getRapla();
    }

    public void testApplication() throws Exception
    {
        DaggerMyAppTest_GwtInjectionTest.create().getRapla().print();
    }

    static public class Rapla
    {
        private Map<String, ExampleExtensionPoint> exampelMap;
        private Set<ExampleExtensionPoint> exampleSet;

        @Inject
        public Rapla(Map<String, ExampleExtensionPoint> exampleMap, Set<ExampleExtensionPoint> exampleSet, OtherInterface test, Set<EmptyInjectionTest> emptySet)
        {
            this.exampelMap = exampleMap;
            this.exampleSet = exampleSet;

        }

        public void print()
        {
            System.out.println("Map: " + exampelMap.toString());
            System.out.println("Set: "+exampleSet.toString());
        }
    }
}
