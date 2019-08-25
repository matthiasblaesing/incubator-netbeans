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

package org.netbeans.modules.navigator;

import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Processor.class)
@SupportedAnnotationTypes({
    "org.netbeans.spi.navigator.NavigatorPanel.Registration", // NOI18N
    "org.netbeans.spi.navigator.NavigatorPanel.Registrations" // NOI18N
})
public class NavigatorPanelRegistrationProcessor extends LayerGeneratingProcessor {
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

    @Override protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(NavigatorPanel.Registration.class)) {
            NavigatorPanel.Registration r = e.getAnnotation(NavigatorPanel.Registration.class);
            if (r == null) {
                continue;
            }
            register(e, r);
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(NavigatorPanel.Registrations.class)) {
            NavigatorPanel.Registrations rr = e.getAnnotation(NavigatorPanel.Registrations.class);
            if (rr == null) {
                continue;
            }
            for (NavigatorPanel.Registration r : rr.value()) {
                register(e, r);
            }
        }
        return true;
    }

    private void register(Element e, NavigatorPanel.Registration r) throws LayerGenerationException {
        String suffix = layer(e).instanceFile("dummy", null, null, r, null).getPath().substring("dummy".length()); // e.g. /my-Panel.instance
        layer(e).file(ProviderRegistry.PANELS_FOLDER + r.mimeType() + suffix).
                methodvalue("instanceCreate", LazyPanel.class.getName(), "create").
                instanceAttribute("delegate", NavigatorPanel.class, r, null).
                position(r.position()).
                bundlevalue("displayName", r.displayName()).
                write();
    }

}
