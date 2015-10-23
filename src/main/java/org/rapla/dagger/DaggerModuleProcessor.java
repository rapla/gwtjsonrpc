package org.rapla.dagger;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import org.rapla.gwtjsonrpc.annotation.ProxyCreator;
import org.rapla.gwtjsonrpc.annotation.SourceWriter;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.DefaultImplementationRepeatable;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.ExtensionRepeatable;
import org.rapla.inject.InjectionContext;
import org.rapla.inject.generator.AnnotationInjectionProcessor;

import dagger.Module;
import dagger.Provides;
import dagger.internal.Factory;

public class DaggerModuleProcessor
{

    private static class Generated
    {
        private final String interfaceName;
        private final String className;

        protected Generated(String interfaceName, String className)
        {
            this.interfaceName = interfaceName;
            this.className = className;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((className == null) ? 0 : className.hashCode());
            result = prime * result + ((interfaceName == null) ? 0 : interfaceName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Generated other = (Generated) obj;
            if (className == null)
            {
                if (other.className != null)
                    return false;
            }
            else if (!className.equals(other.className))
                return false;
            if (interfaceName == null)
            {
                if (other.interfaceName != null)
                    return false;
            }
            else if (!interfaceName.equals(other.interfaceName))
                return false;
            return true;
        }

    }

    private final Set<Generated> alreadyGenerated = new LinkedHashSet<Generated>();

    private final ProcessingEnvironment processingEnvironment;

    public DaggerModuleProcessor(ProcessingEnvironment processingEnvironment)
    {
        super();
        this.processingEnvironment = processingEnvironment;
    }

    public void process() throws Exception
    {
        generateModuleClass();
    }

    private void generateModuleClass() throws Exception
    {
        final JavaFileObject source = processingEnvironment.getFiler().createSourceFile("org.rapla.dagger.DaggerGwtModule");
        final SourceWriter moduleWriter = new SourceWriter(source.openOutputStream());
        moduleWriter.println("package org.rapla.dagger;");
        moduleWriter.println();
        moduleWriter.println("import " + Provides.class.getCanonicalName() + ";");
        moduleWriter.println("import " + Provides.Type.class.getCanonicalName() + ";");
        moduleWriter.println("import " + Module.class.getCanonicalName() + ";");
        moduleWriter.println("import " + Factory.class.getCanonicalName() + ";");
        moduleWriter.println("import " + DaggerMapKey.class.getCanonicalName() + ";");
        moduleWriter.println("import " + Singleton.class.getCanonicalName() + ";");
        moduleWriter.println("import " + Exception.class.getCanonicalName() + ";");
        moduleWriter.println();
        moduleWriter.println("@Module");
        moduleWriter.println("public class DaggerGwtModule {");
        moduleWriter.indent();
        Set<String> interfaces = new LinkedHashSet<String>();
        final File allserviceList = AnnotationInjectionProcessor.readInterfacesInto(interfaces, processingEnvironment);
        for (String interfaceName : interfaces)
        {
            createMethods(interfaceName, moduleWriter, allserviceList);
        }
        moduleWriter.outdent();
        moduleWriter.println("}");
        moduleWriter.close();
    }

    private void createMethods(String interfaceName, SourceWriter moduleWriter, File allserviceList) throws Exception
    {
        final Set<String> implementingClasses = AnnotationInjectionProcessor.readFileContent(interfaceName, allserviceList);
        for (String implementingClass : implementingClasses)
        {
            final TypeElement implementingClassTypeElement = processingEnvironment.getElementUtils().getTypeElement(implementingClass);
            final Generated generated = new Generated(interfaceName, implementingClass);
            if (implementingClass.endsWith(ProxyCreator.PROXY_SUFFIX) && !alreadyGenerated.contains(generated))
            {// Generated Json Proxies
                alreadyGenerated.add(generated);
                String interaceNameWithoutPackage = extractNameWithoutPackage(interfaceName);
                final String implementingClassWithoutPackage = extractNameWithoutPackage(implementingClass);
                moduleWriter.println();
                moduleWriter.println("@Provides");
                moduleWriter.println("public " + interfaceName + " provide_" + interaceNameWithoutPackage + "_" + implementingClassWithoutPackage + "() {");
                moduleWriter.indent();
                moduleWriter.println("return new " + implementingClass + "();");
                moduleWriter.outdent();
                moduleWriter.println("}");
            }
            else if (implementingClassTypeElement != null)
            {
                final DefaultImplementationRepeatable defaultImplementationRepeatable = implementingClassTypeElement
                        .getAnnotation(DefaultImplementationRepeatable.class);
                if (defaultImplementationRepeatable != null)
                {
                    final DefaultImplementation[] defaultImplementations = defaultImplementationRepeatable.value();
                    for (DefaultImplementation defaultImplementation : defaultImplementations)
                    {
                        generate(implementingClassTypeElement, interfaceName, defaultImplementation, moduleWriter);
                    }
                }
                final DefaultImplementation defaultImplementation = implementingClassTypeElement.getAnnotation(DefaultImplementation.class);
                if (defaultImplementation != null)
                {
                    generate(implementingClassTypeElement, interfaceName, defaultImplementation, moduleWriter);
                }
                final ExtensionRepeatable extensionRepeatable = implementingClassTypeElement.getAnnotation(ExtensionRepeatable.class);
                if (extensionRepeatable != null)
                {
                    final Extension[] extensions = extensionRepeatable.value();
                    for (Extension extension : extensions)
                    {
                        generate(implementingClassTypeElement, interfaceName, extension, moduleWriter);
                    }
                }
                final Extension extension = implementingClassTypeElement.getAnnotation(Extension.class);
                if (extension != null)
                {
                    generate(implementingClassTypeElement, interfaceName, extension, moduleWriter);
                }
            }
        }
    }

    private void generate(TypeElement implementingClassTypeElement, String interfaceName, DefaultImplementation defaultImplementation,
            SourceWriter moduleWriter)
    {
        final InjectionContext[] context = defaultImplementation.context();
        if (!InjectionContext.isInjectableOnGwt(context))
        {
            return;
        }
        final String defaultImplClassName = implementingClassTypeElement.getSimpleName().toString();
        final Generated generated = new Generated(interfaceName, defaultImplClassName);
        final TypeElement defaultImplementationOf = getDefaultImplementationOf(defaultImplementation);
        if (defaultImplementation != null
                && processingEnvironment.getTypeUtils().isAssignable(implementingClassTypeElement.asType(), defaultImplementationOf.asType())
                && !alreadyGenerated.contains(generated))
        {
            alreadyGenerated.add(generated);
            final String interfaceNameWithoutPackage = extractNameWithoutPackage(interfaceName);
            final ExecutableElement constructor = getConstructor(implementingClassTypeElement);
            final List<? extends VariableElement> parameters = constructor.getParameters();
            final boolean isSingleton = implementingClassTypeElement.getAnnotation(Singleton.class) != null;
            moduleWriter.println();
            moduleWriter.println("@Provides");
            if (isSingleton)
                moduleWriter.println("@Singleton");
            moduleWriter.println("public " + interfaceName + " provide_" + interfaceNameWithoutPackage + "_" + defaultImplClassName + "("
                    + createString(parameters, true) + ") throws Exception {");
            moduleWriter.indent();
            moduleWriter.println("return new " + implementingClassTypeElement.getQualifiedName().toString() + "(" + createString(parameters, false) + ");");
            moduleWriter.outdent();
            moduleWriter.println("}");
        }
    }

    private void generate(TypeElement implementingClassTypeElement, String interfaceName, Extension extension, SourceWriter moduleWriter)
    {
        if (extension == null)
        {
            return;
        }
        final TypeElement interfaceProvided = getProvides(extension);
        final ExtensionPoint extensionPoint = interfaceProvided.getAnnotation(ExtensionPoint.class);
        if (extensionPoint == null)
        {
            return;
        }
        final InjectionContext[] context = extensionPoint.context();
        if (!InjectionContext.isInjectableOnGwt(context))
        {
            return;
        }
        if (!processingEnvironment.getTypeUtils().isAssignable(implementingClassTypeElement.asType(), interfaceProvided.asType()))
        {
            return;
        }
        final String interfaceNameWithoutPackage = extractNameWithoutPackage(interfaceName);
        final String defaultImplClassName = implementingClassTypeElement.getSimpleName().toString();
        final ExecutableElement constructor = getConstructor(implementingClassTypeElement);
        final List<? extends VariableElement> parameters = constructor.getParameters();
        final String qualifiedName = implementingClassTypeElement.getQualifiedName().toString();
        final boolean isSingleton = implementingClassTypeElement.getAnnotation(Singleton.class) != null;
        moduleWriter.println();
        if (isSingleton)
        {
            final String fullQualifiedName = "provide_" + interfaceNameWithoutPackage + "_" + defaultImplClassName;
            moduleWriter.println("private " + qualifiedName + " " + fullQualifiedName + "=null;");
        }
        moduleWriter.println("@Provides(type=Type.MAP)");
        moduleWriter.println("@" + DaggerMapKey.class.getSimpleName() + "(\"" + extension.id() + "\")");
        writeMethod(interfaceName, moduleWriter, interfaceNameWithoutPackage, defaultImplClassName, parameters, qualifiedName, isSingleton, "Map");
        moduleWriter.println();
        moduleWriter.println("@Provides(type=Type.SET)");
        writeMethod(interfaceName, moduleWriter, interfaceNameWithoutPackage, defaultImplClassName, parameters, qualifiedName, isSingleton, "Set");
    }

    private void writeMethod(String interfaceName, SourceWriter moduleWriter, final String interfaceNameWithoutPackage, final String defaultImplClassName,
            final List<? extends VariableElement> parameters, final String qualifiedName, final boolean isSingleton, final String methodSuffix)
    {
        final String fullQualifiedName = "provide_" + interfaceNameWithoutPackage + "_" + defaultImplClassName;
        if (isSingleton)
            moduleWriter.println("@Singleton");
        moduleWriter.println("public " + interfaceName + " " + fullQualifiedName + "_" + methodSuffix + "(" + createString(parameters, true) + ") throws Exception {");
        moduleWriter.indent();
        if (isSingleton)
        {
            moduleWriter.println("if (" + fullQualifiedName + "!=null) {");
            moduleWriter.indent();
            moduleWriter.println("return " + fullQualifiedName + ";");
            moduleWriter.outdent();
            moduleWriter.println("}");
        }
        String variable = "result";
        moduleWriter.println(qualifiedName + " " + variable + " = new " + qualifiedName + "(" + createString(parameters, false) + ");");
        if (isSingleton)
        {
            moduleWriter.println(fullQualifiedName + "=" + variable + ";");
        }
        moduleWriter.println("return " + variable + ";");
        moduleWriter.outdent();
        moduleWriter.println("}");
    }

    private static String extractNameWithoutPackage(String className)
    {
        final int lastIndexOf = className.lastIndexOf(".");
        if (lastIndexOf >= 0)
        {
            return className.substring(lastIndexOf + 1);
        }
        return className;
    }

    private TypeElement getProvides(Extension annotation)
    {
        try
        {
            annotation.provides(); // this should throw
        }
        catch (MirroredTypeException mte)
        {
            return asTypeElement(mte.getTypeMirror());
        }
        return null; // can this ever happen ??
    }

    private TypeElement getDefaultImplementationOf(DefaultImplementation annotation)
    {
        try
        {
            annotation.of(); // this should throw
        }
        catch (MirroredTypeException mte)
        {
            return asTypeElement(mte.getTypeMirror());
        }
        return null; // can this ever happen ??
    }

    private TypeElement asTypeElement(TypeMirror typeMirror)
    {
        Types TypeUtils = processingEnvironment.getTypeUtils();
        return (TypeElement) TypeUtils.asElement(typeMirror);
    }

    private String createString(List<? extends VariableElement> parameters, boolean withType)
    {
        final StringBuilder sb = new StringBuilder();
        if (parameters != null)
        {
            boolean first = true;
            for (VariableElement parameter : parameters)
            {
                if (first)
                {
                    first = false;
                }
                else
                {
                    sb.append(", ");
                }
                if (withType)
                {
                    final TypeMirror asType = parameter.asType();
                    if (asType instanceof PrimitiveType)
                    {
                        final String string = asType.toString();
                        if ("int".equalsIgnoreCase(string))
                        {
                            sb.append("java.lang.Integer");
                        }
                        else
                        {
                            sb.append("java.lang.");
                            final char[] charArray = string.toCharArray();
                            charArray[0] = Character.toUpperCase(charArray[0]);
                            sb.append(charArray);
                        }
                    }
                    else
                    {
                        sb.append(asType.toString());
                    }
                    sb.append(" ");
                }
                sb.append(parameter.getSimpleName().toString());
            }
        }
        return sb.toString();
    }

    private ExecutableElement getConstructor(TypeElement element)
    {
        final List<? extends Element> allMembers = processingEnvironment.getElementUtils().getAllMembers(element);
        final List<ExecutableElement> constructors = ElementFilter.constructorsIn(allMembers);
        ExecutableElement foundConstructor = null;
        if (constructors != null)
        {
            for (ExecutableElement constructor : constructors)
            {
                final boolean hasInjectAnnotation = constructor.getAnnotation(Inject.class) != null
                        || constructor.getAnnotation(com.google.inject.Inject.class) != null;
                if (hasInjectAnnotation)
                {
                    foundConstructor = constructor;
                    break;
                }
            }
        }
        if(foundConstructor == null)
        {
            throw new IllegalStateException("No @Inject constructor found for: "+element.getQualifiedName().toString());
        }
        return foundConstructor;
    }
}
