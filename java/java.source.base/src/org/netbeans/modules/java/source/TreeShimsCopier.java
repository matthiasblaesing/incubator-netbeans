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
package org.netbeans.modules.java.source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Processor.class)
@SupportedAnnotationTypes("*")
public class TreeShimsCopier extends AbstractProcessor {
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
    public boolean process(Set<? extends TypeElement> annos, RoundEnvironment roundEnv) {
        for (Element el : roundEnv.getRootElements()) {
            if (el.getKind() != ElementKind.CLASS)
                continue;
            TypeElement type = (TypeElement) el;
            String qualName = type.getQualifiedName().toString();
            String targetPackage = ALLOWED_CLASSES2TARGET_PACKAGE.get(qualName);
            if (targetPackage != null) {
                try {
                    Filer filer = processingEnv.getFiler();
                    FileObject fo = filer.getResource(StandardLocation.SOURCE_PATH, ((PackageElement) type.getEnclosingElement()).getQualifiedName().toString(), type.getSimpleName() + ".java");
                    URI source = fo.toUri();
                    StringBuilder path2Shims = new StringBuilder();
                    int p = qualName.split("\\.").length;
                    for (int i = 0; i < p; i++) {
                        path2Shims.append("../");
                    }
                    path2Shims.append("../java.source.base/src/org/netbeans/modules/java/source/TreeShims.java");
                    URI treeShims = source.resolve(path2Shims.toString());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream in = treeShims.toURL().openStream()) {
                        int r;

                        while ((r = in.read()) != (-1)) {
                            baos.write(r);
                        }
                    }
                    String content = new String(baos.toByteArray(), "UTF-8");
                    content = content.replace("package org.netbeans.modules.java.source;", "package " + targetPackage + ";");
                    try (OutputStream out = filer.createSourceFile(targetPackage + ".TreeShims", type).openOutputStream()) {
                        out.write(content.getBytes("UTF-8"));
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
        return false;
    }

    private static final Map<String, String> ALLOWED_CLASSES2TARGET_PACKAGE = new HashMap<String, String>() {{
        put("org.netbeans.modules.java.hints.infrastructure.ErrorHintsProvider", "org.netbeans.modules.java.hints");
        put("org.netbeans.modules.java.completion.JavaCompletionTask", "org.netbeans.modules.java.completion");
    }};
}
