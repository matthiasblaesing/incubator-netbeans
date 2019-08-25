/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.project.ui.convertor;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.netbeans.spi.project.ui.ProjectConvertor;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tomas Zezula
 */
@ServiceProvider(service=Processor.class)
@SupportedAnnotationTypes("org.netbeans.spi.project.ui.ProjectConvertor.Registration") // NOI18N
public class ProjectConvertorProcessor extends LayerGeneratingProcessor {
    private static final SourceVersion SUPPORTED_SOURCE_VERSION;

    static {
        // Determine supported version at runtime. Netbeans supports being build
        // on JDK 8, but also supports JDKs up to 12, the biggest known good
        // source version will be reported
        SourceVersion SUPPORTED_SOURCE_VERSION_BUILDER = null;
        for(String version: new String[] {"RELEASE_12", "RELEASE_11", "RELEASE_10", "RELEASE_9"}) { // NOI18N
            try {
                SUPPORTED_SOURCE_VERSION_BUILDER = SourceVersion.valueOf(version);
                break;
            } catch (IllegalArgumentException ex) {
                // value not present skip it
            }
        }
        if(SUPPORTED_SOURCE_VERSION_BUILDER == null) {
            SUPPORTED_SOURCE_VERSION_BUILDER = SourceVersion.RELEASE_8;
        }
        SUPPORTED_SOURCE_VERSION = SUPPORTED_SOURCE_VERSION_BUILDER;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SUPPORTED_SOURCE_VERSION;
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws LayerGenerationException {
        for (Element e : roundEnv.getElementsAnnotatedWith(ProjectConvertor.Registration.class)) {
            if (!e.getKind().isClass()) {
                throw new LayerGenerationException("Annotated element is not a class", e);   //NOI18N
            }
            final ProjectConvertor.Registration reg = e.getAnnotation(ProjectConvertor.Registration.class);
            final Elements elements = processingEnv.getElementUtils();
            final Types types = processingEnv.getTypeUtils();
            final TypeElement projectConvertor = elements.getTypeElement(ProjectConvertor.class.getName());
            if (types.isSubtype(((TypeElement)e).asType(), projectConvertor.asType())) {
                final LayerBuilder.File f = layer(e).instanceFile("Services/ProjectConvertors", null, null);    //NOI18N
                f.stringvalue("instanceOf", ProjectConvertorAcceptor.class.getName());   //NOI18N
                f.stringvalue("instanceClass", ProjectConvertorAcceptor.class.getName());   //NOI18N
                f.methodvalue("instanceCreate", ProjectConvertor.Result.class.getName(), "create");    //NOI18N
                final int position = reg.position();
                if (position >= 0) {
                    f.intvalue("position", position);   //NOI18N
                }
                f.instanceAttribute(ProjectConvertorAcceptor.ATTR_DELEGATE, ProjectConvertor.class);
                final String pattern = reg.requiredPattern();
                if (pattern == null || pattern.isEmpty()) {
                    throw new LayerGenerationException(
                        String.format("The %s has to be non empty string.", ProjectConvertorAcceptor.ATTR_PATTERN), //NOI18N
                        e);
                }
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException ex) {
                    throw new LayerGenerationException(
                        String.format(
                            "The %s is not valid regular expression: %s.",  //NOI18N
                            ProjectConvertorAcceptor.ATTR_PATTERN,
                            ex.getMessage()),
                        e);
                }
                f.stringvalue(ProjectConvertorAcceptor.ATTR_PATTERN, pattern);
                f.write();
            } else {
                throw new LayerGenerationException("Annoated element is not a subclass of ProjectConvertor.",e); //NOI18N
            }
        }
        return true;
    }
}
