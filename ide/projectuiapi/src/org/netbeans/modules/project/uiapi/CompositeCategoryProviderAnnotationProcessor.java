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

package org.netbeans.modules.project.uiapi;

import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.netbeans.spi.project.ui.support.ProjectCustomizer.CompositeCategoryProvider;
import org.netbeans.spi.project.ui.support.ProjectCustomizer.CompositeCategoryProvider.Registration;
import org.netbeans.spi.project.ui.support.ProjectCustomizer.CompositeCategoryProvider.Registrations;
import org.openide.filesystems.annotations.LayerBuilder.File;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Processor.class)
@SupportedAnnotationTypes({
    "org.netbeans.spi.project.ui.support.ProjectCustomizer.CompositeCategoryProvider.Registration", // NOI18N
    "org.netbeans.spi.project.ui.support.ProjectCustomizer.CompositeCategoryProvider.Registrations" // NOI18N
})
public class CompositeCategoryProviderAnnotationProcessor extends LayerGeneratingProcessor {
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
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(Registration.class)) {
            Registration r = e.getAnnotation(Registration.class);
            if (r == null) {
                continue;
            }
            handle(e, r);
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(Registrations.class)) {
            Registrations rr = e.getAnnotation(Registrations.class);
            if (rr == null) {
                continue;
            }
            for (Registration r : rr.value()) {
                handle(e, r);
            }
        }
        return true;
    }

    private void handle(Element e, Registration r) throws LayerGenerationException {
        String path = "Projects/" + r.projectType() + "/Customizer";
        if (r.category().length() > 0) {
            path += "/" + r.category();
        }
        boolean addsFolder = r.categoryLabel().length() > 0;
        if (addsFolder) {
            handleFolder(path, e, r);
        }
        if (e.getKind() == ElementKind.PACKAGE) {
            if (!addsFolder) {
                throw new LayerGenerationException("Must specify categoryLabel", e, processingEnv, r);
            }
        } else {
            File f = layer(e).instanceFile(path, addsFolder ? "Self" : null, CompositeCategoryProvider.class, r, null);
            f.position(addsFolder ? 0 : r.position());
            f.write();
        }
    }

    private void handleFolder(String path, Element e, Registration r) throws LayerGenerationException {
        if (r.category().length() == 0) {
            throw new LayerGenerationException("Must specify category", e, processingEnv, r);
        }
        layer(e).folder(path).bundlevalue("displayName", r.categoryLabel(), r, "categoryLabel").position(r.position()).write();
    }

}
