// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.rapla.gwtjsonrpc.annotation;

import org.rapla.gwtjsonrpc.RemoteJsonMethod;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;

import java.io.*;
import java.util.*;

public class ProxyCreator implements SerializerClasses
{
    private static final String PROXY_SUFFIX = "_JsonProxy";
    private TypeElement svcInf;
    String futureResultClassName;
    private SerializerCreator serializerCreator;
    private org.rapla.gwtjsonrpc.annotation.ResultDeserializerCreator deserializerCreator;
    private int instanceField;
    private final ProcessingEnvironment processingEnvironment;
    private final NameFactory nameFactory = new NameFactory();
    String generatorName;

    public ProxyCreator(final TypeElement remoteService, ProcessingEnvironment processingEnvironment, String generatorName)
    {
        svcInf = remoteService;
        this.generatorName = generatorName;
        this.processingEnvironment = processingEnvironment;
        serializerCreator = new SerializerCreator(processingEnvironment, nameFactory, generatorName);
        deserializerCreator = new org.rapla.gwtjsonrpc.annotation.ResultDeserializerCreator(serializerCreator, processingEnvironment, generatorName);
        futureResultClassName = FutureResultImpl;
    }

    private String getGeneratorString()
    {
        return "@javax.annotation.Generated(\"" + generatorName + "\")";
    }

    public String create(final TreeLogger logger) throws UnableToCompleteException
    {
        final List<ExecutableElement> methods = getMethods(processingEnvironment);
        checkMethods(logger, processingEnvironment);

        final SourceWriter srcWriter = getSourceWriter(logger);
        if (srcWriter == null)
        {
            return getProxyQualifiedName();
        }

        generateProxyConstructor(logger, srcWriter);
        generateProxyCallCreator(logger, srcWriter);
        generateProxyMethods(logger, srcWriter);
        srcWriter.outdent();
        srcWriter.println("};");
        srcWriter.close();

        return getProxyQualifiedName();
    }

    private void checkMethods(final TreeLogger logger, final ProcessingEnvironment processingEnvironment) throws UnableToCompleteException
    {
        final Set<String> declaredNames = new HashSet<String>();
        final List<ExecutableElement> methods = getMethods(processingEnvironment);
        for (final ExecutableElement m : methods)
        {
            final String methodName = m.getSimpleName().toString();
            if (!declaredNames.add(methodName))
            {
                invalid(logger, "Overloading method " + methodName + " not supported");
            }
            final List<? extends VariableElement> params = m.getParameters();

            final TypeMirror callback = m.getReturnType();

            final Element ele = processingEnvironment.getTypeUtils().asElement(callback);

            for (VariableElement p : params)
            {
                final TreeLogger branch = logger;//logger.branch(TreeLogger.DEBUG, m.getName() + ", parameter " + p.getName());
                //final TypeElement typeP = (TypeElement) processingEnvironment.getTypeUtils().asElement(p.asType());
                TypeMirror typeP = p.asType();
                serializerCreator.checkCanSerialize(branch, typeP);
                if (!SerializerCreator.isPrimitive(typeP) && !SerializerCreator.isBoxedPrimitive(typeP))
                {
                    serializerCreator.create(typeP, branch);
                }
            }
            TypeMirror returnType = getParameters(m.getReturnType()).get(0);
            if (SerializerCreator.isPrimitive(returnType))
            {
                continue;
            }

            final TreeLogger branch = logger;//.branch(TreeLogger.DEBUG, m.getName() + ", result " + resultType.getQualifiedSourceName());
            if (returnType.toString().startsWith(FutureResult) && SerializerCreator.isParameterized( returnType))
            {
                final TypeMirror typeMirror = getParameters(returnType).get(0);
                serializerCreator.checkCanSerialize(branch, typeMirror);
            }
            else
            {
                serializerCreator.checkCanSerialize(branch, returnType);
            }
            if (SerializerCreator.isArray(returnType))
            {
                // Arrays need a special deserializer
                deserializerCreator.create(logger, returnType);
            }
            else if (!SerializerCreator.isPrimitive(returnType) && !SerializerCreator.isBoxedPrimitive(returnType))
            {
                // Non primitives get deserialized by their normal serializer
                serializerCreator.create(returnType, branch);
            }
            // (Boxed)Primitives are left, they are handled specially
        }
    }

    private List<? extends TypeMirror> getParameters(TypeMirror returnType)
    {
        if (!(returnType instanceof DeclaredType))
        {
            return Collections.emptyList();
        }
        return ((DeclaredType) returnType).getTypeArguments();
    }

    private List<ExecutableElement> getMethods(ProcessingEnvironment processingEnvironment)
    {
        final List<? extends Element> allMembers = processingEnvironment.getElementUtils().getAllMembers(svcInf);
        List<ExecutableElement> result = new ArrayList<ExecutableElement>();
        for (ExecutableElement r: ElementFilter.methodsIn(allMembers))
        {
            if (!canIgnore( r))
            {
                result.add( r );
            }
        }
        return result;
    }

    private boolean canIgnore(ExecutableElement m)
    {
        Element enclosingElement = m.getEnclosingElement();
        Set<Modifier> modifiers = m.getModifiers();
        String methodClass = enclosingElement.asType().toString();
        return modifiers.contains(Modifier.FINAL) || modifiers.contains(Modifier.PRIVATE) || methodClass.equals("java.lang.Object");
    }

    private boolean returnsCallbackHandle(final ExecutableElement m)
    {
        final Element element = processingEnvironment.getTypeUtils().asElement(m.getReturnType());
        final String superName = element.getEnclosingElement().getSimpleName().toString();
        return superName.equals(CallbackHandle);
    }

    private void invalid(final TreeLogger logger, final String what) throws UnableToCompleteException
    {
        logger.error(what);
        throw new UnableToCompleteException(what);
    }

    private SourceWriter getSourceWriter(final TreeLogger logger) throws UnableToCompleteException
    {
        final String pkgName = processingEnvironment.getElementUtils().getPackageOf(svcInf).toString();
        SourceWriter pw = null;

        final String className = svcInf.getSimpleName().toString() + PROXY_SUFFIX;
        try
        {
            //String pathname = pkgName.replaceAll("\\.", "/") + "/" + className;
            //File file = new File(pathname);
            String name = svcInf.getQualifiedName() + PROXY_SUFFIX;
            JavaFileObject sourceFile = processingEnvironment.getFiler().createSourceFile(name,svcInf);
            pw = new SourceWriter(sourceFile.openWriter());
        }
        catch (IOException e)
        {
            throw new UnableToCompleteException(e.getMessage());
        }
        pw.println("package " + pkgName + ";");
        pw.println("import " + FutureResult + ";");
        pw.println("import " + AbstractJsonProxy + ";");
        pw.println("import " + JsonSerializer + ";");
        pw.println("import " + JavaScriptObject.class.getCanonicalName() + ";");
        pw.println("import " + ResultDeserializer + ";");
        pw.println("import " + FutureResultImpl + ";");
        pw.println("import " + GWT.class.getCanonicalName() + ";");
        pw.println();
        TypeElement erasedType = SerializerCreator.getErasedType(svcInf, processingEnvironment);
        String interfaceName =  erasedType.getQualifiedName().toString();
        pw.println(getGeneratorString());
        pw.println("public class " + className + " extends " + AbstractJsonProxy_simple + " implements " + interfaceName);
        pw.println("{");
        pw.indent();
        return pw;
    }

    private void generateProxyConstructor(@SuppressWarnings("unused") final TreeLogger logger, final SourceWriter w)
    {
        final RemoteJsonMethod relPath = svcInf.getAnnotation(RemoteJsonMethod.class);
        if (relPath != null)
        {
            w.println();
            w.println("public " + getProxySimpleName() + "() {");
            w.indent();
            String path = relPath.path();
            if (path == null || path.isEmpty())
            {
                TypeElement erasedType = SerializerCreator.getErasedType(svcInf, processingEnvironment);
                path = erasedType.getQualifiedName().toString();
            }
            w.println("setPath(\"" + path + "\");");
            w.outdent();
            w.println("}");
        }
    }

    private void generateProxyCallCreator(final TreeLogger logger, final SourceWriter w) throws UnableToCompleteException
    {
        String callName = getJsonCallClassName(logger);
        w.println();
        w.println("@Override");
        w.print("protected <T> ");
        w.print(callName);
        w.print("<T> newJsonCall(final AbstractJsonProxy proxy, ");
        w.print("final String methodName, final String reqData, ");
        w.println("final ResultDeserializer<T> ser) {");
        w.indent();

        w.print("return new ");
        w.print(callName);
        w.println("<T>(proxy, methodName, reqData, ser);");

        w.outdent();
        w.println("}");
    }

    private String getJsonCallClassName(final TreeLogger logger) throws UnableToCompleteException
    {
        return JsonCall20HttpPost;
    }

    private void generateProxyMethods(final TreeLogger logger, final SourceWriter srcWriter)
    {
        final List<ExecutableElement> methods = getMethods(processingEnvironment);
        for (final ExecutableElement m : methods)
        {
            generateProxyMethod(logger, m, srcWriter);
        }
    }

    private void generateProxyMethod(@SuppressWarnings("unused") final TreeLogger logger, final ExecutableElement method, final SourceWriter w)
    {
        final List<? extends VariableElement> params = method.getParameters();
        final TypeMirror callback = method.getReturnType();// params[params.length - 1];
        TypeMirror resultType = callback;
        //    final JClassType resultType =
        //        callback.isParameterized().getTypeArgs()[0];
        final String[] serializerFields = new String[params.size()];
        String resultField = "";

        w.println();
        for (int i = 0; i < params.size() /*- 1*/; i++)
        {
            final VariableElement variableElement = params.get(i);
            final TypeMirror pType = variableElement.asType();
            if (SerializerCreator.needsTypeParameter(pType, processingEnvironment))
            {
                serializerFields[i] = "serializer_" + instanceField++;
                w.print("private static final ");
                if (SerializerCreator.isArray(pType))
                    w.print(serializerCreator.serializerFor(pType));
                else
                    w.print(JsonSerializer);
                w.print(" ");
                w.print(serializerFields[i]);
                w.print(" = ");
                serializerCreator.generateSerializerReference(pType, w, false);
                w.println(";");
            }
        }
        TypeMirror parameterizedResult = null;
        if (SerializerCreator.isParameterized( resultType))
        {
            resultField = "serializer_" + instanceField++;
            w.print("private static final ");
            w.print(ResultDeserializer);
            w.print(" ");
            w.print(resultField);
            w.print(" = ");
            final List<? extends TypeMirror> typeArguments = ((DeclaredType) resultType).getTypeArguments();
            parameterizedResult = typeArguments.get(0);
            serializerCreator.generateSerializerReference(parameterizedResult, w, false);
            w.println(";");
        }

        w.print("public ");

        w.print(method.getReturnType().toString());
        w.print(" ");
        w.print(method.getSimpleName().toString());
        w.print("(");
        boolean needsComma = false;
        for (int i = 0; i < params.size(); i++)
        {
            final VariableElement param = params.get(i);
            String pname = param.getSimpleName().toString();
            if (needsComma)
            {
                w.print(", ");
            }
            else
            {
                needsComma = true;
            }
            //final TypeElement paramType = (TypeElement) processingEnvironment.getTypeUtils().asElement(param.asType());
            w.print(param.asType().toString());
            w.print(" ");

            nameFactory.addName(pname);
            w.print(pname);
        }

        w.println(") {");
        w.indent();

        if (returnsCallbackHandle(method))
        {
            w.print("return new ");
            w.print(CallbackHandle);
            w.print("(");
            if (SerializerCreator.needsTypeParameter(resultType, processingEnvironment))
            {
                w.print(resultField);
            }
            else
            {
                deserializerCreator.generateDeserializerReference(resultType, w);
            }
            w.print(", " + "null" // callback.getName()
            );
            w.println(");");
            w.outdent();
            w.println("}");
            return;
        }

        final String reqDataStr;
        if (params.isEmpty())
        {
            reqDataStr = "\"[]\"";
        }
        else
        {
            final String reqData = nameFactory.createName("reqData");
            w.println("final StringBuilder " + reqData + " = new StringBuilder();");
            needsComma = false;
            w.println(reqData + ".append('[');");
            for (int i = 0; i < params.size(); i++)
            {
                if (needsComma)
                {
                    w.println(reqData + ".append(\",\");");
                }
                else
                {
                    needsComma = true;
                }

                final VariableElement param = params.get(i);
                String pName = param.getSimpleName().toString();

                TypeMirror paramType = param.asType();
                //                pType == JPrimitiveType.CHAR ||
                if (SerializerCreator.isBoxedCharacter(paramType))
                {
                    w.println(reqData + ".append(\"\\\"\");");
                    w.println(reqData + ".append(" + JsonSerializer_simple + ".escapeChar(" + pName + "));");
                    w.println(reqData + ".append(\"\\\"\");");
                }
                else if ((SerializerCreator.isJsonPrimitive(paramType) || SerializerCreator.isBoxedPrimitive(paramType)) && !SerializerCreator.isJsonString(paramType))
                {
                    w.println(reqData + ".append(" + pName + ");");
                }
                else
                {
                    w.println("if (" + pName + " != null) {");
                    w.indent();
                    if (SerializerCreator.needsTypeParameter(paramType, processingEnvironment))
                    {
                        w.print(serializerFields[i]);
                    }
                    else
                    {
                        serializerCreator.generateSerializerReference(paramType, w, false);
                    }
                    w.println(".printJson(" + reqData + ", " + pName + ");");
                    w.outdent();
                    w.println("} else {");
                    w.indent();
                    w.println(reqData + ".append(" + JsonSerializer + ".JS_NULL);");
                    w.outdent();
                    w.println("}");
                }
            }
            w.println(reqData + ".append(']');");
            reqDataStr = reqData + ".toString()";
        }

        String resultClass = futureResultClassName;
        if (parameterizedResult != null)
        {
            resultClass += "<" + parameterizedResult.toString() + ">";
        }
        w.println(resultClass + " result = new " + resultClass + "();");
        w.print("doInvoke(");
        w.print("\"" + method.getSimpleName().toString() + "\"");
        w.print(", " + reqDataStr);
        w.print(", ");
        final List<? extends TypeMirror> typeArguments = ((DeclaredType) resultType).getTypeArguments();
        if (typeArguments != null && !typeArguments.isEmpty())
        {
            w.print(resultField);
        }
        else
        {
            deserializerCreator.generateDeserializerReference(resultType, w);
        }

        w.print(", result");

        w.println(");");
        w.println("return result;");

        w.outdent();
        w.println("}");
    }

    private String getProxyQualifiedName()
    {
        final String[] name = synthesizeTopLevelClassName(svcInf, PROXY_SUFFIX, nameFactory, processingEnvironment);
        return name[0].length() == 0 ? name[1] : name[0] + "." + name[1];
    }

    private String getProxySimpleName()
    {
        return synthesizeTopLevelClassName(svcInf, PROXY_SUFFIX, nameFactory, processingEnvironment)[1];
    }

    static String[] synthesizeTopLevelClassName(TypeElement type, String suffix, NameFactory nameFactory, ProcessingEnvironment processingEnvironment)
    {
        // Gets the basic name of the type. If it's a nested type, the type name
        // will contains dots.
        //
        String className;
        String packageName;

        TypeMirror typeMirror =getLeafType(type.asType());
        TypeElement leafType = (TypeElement)processingEnvironment.getTypeUtils().asElement( typeMirror);
        if (SerializerCreator.isPrimitive(typeMirror))
        {
            className = leafType.getSimpleName().toString();
            packageName = "";
        }
        else
        {
            //assert(SerializerCreator.isClass(typeMirror) || SerializerCreator.isInterface(typeMirror));
            packageName = processingEnvironment.getElementUtils().getPackageOf(leafType).toString();
            className = typeMirror.toString().substring( packageName.length() + 1);

        }

        boolean isGeneric = SerializerCreator.isParameterized(typeMirror);
        if (isGeneric)
        {
            final List<? extends TypeMirror> typeArguments = ((DeclaredType) typeMirror).getTypeArguments();
            for (TypeMirror param : typeArguments)
            {
                className += "_";
                className += param.toString().replace('.', '_');
            }
        }

        boolean isArray = SerializerCreator.isArray(typeMirror);
        if (isArray)
        {
            int rank = getRank((ArrayType)typeMirror);
            className += "_Array_Rank_" + rank;

        }

        // Add the meaningful suffix.
        //
        className += suffix;

        // Make it a top-level name.
        //
        className = className.replace('.', '_');

        return new String[] { packageName, className };
    }

    private static TypeMirror getLeafType(TypeMirror type)
    {
        if ( type instanceof  ArrayType)
        {
            final TypeMirror componentType = ((ArrayType) type).getComponentType();
            return getLeafType(componentType);
        }
        return type;
    }

    public static int getRank(ArrayType typeMirror)
    {
        final TypeMirror componentType = typeMirror.getComponentType();
        if(typeMirror
                 == componentType)
        {
            return 1;
        }
        if(componentType instanceof  ArrayType)
        {
            return getRank( (ArrayType) componentType) + 1;
        }
        return 1;
    }
}
