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

package org.netbeans.modules.options;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.spi.options.AdvancedOption;
import org.netbeans.spi.options.OptionsCategory;
import org.netbeans.spi.options.OptionsPanelController;
import org.netbeans.spi.options.OptionsPanelController.ContainerRegistration;
import org.netbeans.spi.options.OptionsPanelController.Keywords;
import org.netbeans.spi.options.OptionsPanelController.KeywordsRegistration;
import org.netbeans.spi.options.OptionsPanelController.SubRegistration;
import org.netbeans.spi.options.OptionsPanelController.TopLevelRegistration;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerBuilder.File;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service=Processor.class)
@SupportedAnnotationTypes({
    "org.netbeans.spi.options.OptionsPanelController.TopLevelRegistration", //NOI18N
    "org.netbeans.spi.options.OptionsPanelController.ContainerRegistration", //NOI18N
    "org.netbeans.spi.options.OptionsPanelController.SubRegistration", //NOI18N
    "org.netbeans.spi.options.OptionsPanelController.KeywordsRegistration", //NOI18N
    "org.netbeans.spi.options.OptionsPanelController.Keywords" //NOI18N
})
public class OptionsPanelControllerProcessor extends LayerGeneratingProcessor {

    private static final SourceVersion SUPPORTED_SOURCE_VERSION;

    static {
        // Determine supported version at runtime. Netbeans supports being build
        // on JDK 8, but also supports JDKs up to 12, the biggest known good
        // source version will be reported
        SourceVersion SUPPORTED_SOURCE_VERSION_BUILDER = null;
        for(String version: new String[] {"RELEASE_12", "RELEASE_11", "RELEASE_10", "RELEASE_9"}) { //NOI18N
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

    private Element originatingElement;

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(TopLevelRegistration.class)) {
            TopLevelRegistration r = e.getAnnotation(TopLevelRegistration.class);
            if (r == null) {
                continue;
            }
            LayerBuilder builder = layer(e);
            File file = builder.instanceFile("OptionsDialog", r.id().length() > 0 ? r.id() : null, r, null).
                    methodvalue("instanceCreate", OptionsCategory.class.getName(), "createCategory").
                    instanceAttribute("controller", OptionsPanelController.class).
                    bundlevalue("categoryName", r.categoryName()).
                    position(r.position());
            iconBase(e, r.iconBase(), r, file, builder);
            keywords(e, r.keywords(), r.keywordsCategory(), r, file);
            file.write();
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(SubRegistration.class)) {
            SubRegistration r = e.getAnnotation(SubRegistration.class);
            if (r.position() != Integer.MAX_VALUE && r.location().equals("Advanced")) {
                throw new LayerGenerationException("position ignored for Advanced subpanels", e, processingEnv, r, "position");
            }
            File file = layer(e).instanceFile("OptionsDialog/" + r.location(), r.id().length() > 0 ? r.id() : null, r, null).
                    methodvalue("instanceCreate", AdvancedOption.class.getName(), "createSubCategory").
                    instanceAttribute("controller", OptionsPanelController.class).
                    bundlevalue("displayName", r.displayName()).
                    position(r.position());
            keywords(e, r.keywords(), r.keywordsCategory(), r, file);
            file.write();
        }
        
        for (Element e : roundEnv.getElementsAnnotatedWith(Keywords.class)) {
            handleElement(e, e.getAnnotation(Keywords.class), "");
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(KeywordsRegistration.class)) {
            KeywordsRegistration r = e.getAnnotation(KeywordsRegistration.class);
            Keywords[] panels = r.value();
            for (int i = 0; i < panels.length; i++) {
                handleElement(e, panels[i], Integer.toString(-(i + 1)));
            }
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(ContainerRegistration.class)) {
            ContainerRegistration r = e.getAnnotation(ContainerRegistration.class);
            LayerBuilder builder = layer(e);
            File file = builder.file("OptionsDialog/" + r.id() + ".instance").
                    methodvalue("instanceCreate", OptionsCategory.class.getName(), "createCategory").
                    stringvalue("advancedOptionsFolder", "OptionsDialog/" + r.id()).
                    bundlevalue("categoryName", r.categoryName()).
                    position(r.position());
            iconBase(e, r.iconBase(), r, file, builder);
            keywords(e, r.keywords(), r.keywordsCategory(), r, file);
            file.write();
            layer(e).folder("OptionsDialog/" + r.id()).position(0).write();
        }
        return true;
    }

    private void handleElement(Element e, Keywords annotation, String name) throws LayerGenerationException {
	originatingElement = e;
	if (!annotation.location().equals(OptionsDisplayer.GENERAL) && !annotation.location().equals(OptionsDisplayer.KEYMAPS)) {
	    if (annotation.tabTitle().trim().isEmpty()) {
		throw new LayerGenerationException("Must specify tabTitle", e, processingEnv, annotation, "tabTitle");
	    }
	}
        File file = layer(e).
                file("OptionsDialog/Keywords/".concat(e.asType().toString()).concat(name)).
                stringvalue("location", annotation.location()).
		bundlevalue("tabTitle", annotation.tabTitle(), annotation, "tabTitle");
        String[] keywords = annotation.keywords();
        for (int j = 0; j < keywords.length; j++) {
            file = file.bundlevalue("keywords-".concat(Integer.toString(j+1)), keywords[j], annotation, "keywords");
        }
        file.write();
    }

    private String getBundleValue(String label, Annotation annotation, String annotationMethod) throws LayerGenerationException {
        String javaIdentifier = "(?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)";
        Matcher m = Pattern.compile("((?:" + javaIdentifier + "\\.)+[^\\s.#]+)?#(\\S*)").matcher(label);
        if (m.matches()) {
            String bundle = m.group(1);
            String key = m.group(2);
            if (bundle == null) {
                Element referenceElement = originatingElement;
                while (referenceElement != null && referenceElement.getKind() != ElementKind.PACKAGE) {
                    referenceElement = referenceElement.getEnclosingElement();
                }
                if (referenceElement == null) {
                    throw new LayerGenerationException("No reference element to determine package in '" + label + "'", originatingElement);
                }
                bundle = ((PackageElement) referenceElement).getQualifiedName() + ".Bundle";
            }
            return verifyBundleValue(bundle, key, m.group(1) == null, annotation, annotationMethod);
        }
        return label;
    }

    private String verifyBundleValue(String bundle, String key, boolean samePackage, Annotation annotation, String annotationMethod) throws LayerGenerationException {
        if (processingEnv == null) {
            return "";
        }
        if (samePackage) {
            for (Element e = originatingElement; e != null; e = e.getEnclosingElement()) {
                NbBundle.Messages m = e.getAnnotation(NbBundle.Messages.class);
                if (m != null) {
                    for (String kv : m.value()) {
                        if (kv.startsWith(key + "=")) {
                            return bundle.concat("#").concat(key);
                        }
                    }
                }
            }
        }
        try {
            InputStream is = layer(originatingElement).validateResource(bundle.replace('.', '/') + ".properties", originatingElement, null, null, false).openInputStream();
            try {
                Properties p = new Properties();
                p.load(is);
                if (p.getProperty(key) == null) {
                    throw new LayerGenerationException("No key '" + key + "' found in " + bundle, originatingElement, processingEnv, annotation, annotationMethod);
                }
                return bundle.concat("#").concat(key);
            } finally {
                is.close();
            }
        } catch (IOException x) {
            throw new LayerGenerationException("Could not open " + bundle + ": " + x, originatingElement, processingEnv, annotation, annotationMethod);
        }
    }

    private void iconBase(Element e, String iconBase, Annotation r, File file, LayerBuilder builder) throws LayerGenerationException {
        builder.validateResource(iconBase, e, r, "iconBase", true);
        file.stringvalue("iconBase", iconBase);
    }

    private void keywords(Element e, String keywords, String keywordsCategory, Annotation r, File file) throws LayerGenerationException {
        if (keywords.length() > 0) {
            if (keywordsCategory.length() == 0) {
                throw new LayerGenerationException("Must specify both keywords and keywordsCategory", e, processingEnv, r, "keywordsCategory");
            }
            file.bundlevalue("keywords", keywords, r, "keywords").bundlevalue("keywordsCategory", keywordsCategory, r, "keywordsCategory");
        } else {
            if (keywordsCategory.length() > 0) {
                throw new LayerGenerationException("Must specify both keywords and keywordsCategory", e, processingEnv, r, "keywords");
            }
        }
    }

}
