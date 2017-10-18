/**
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.extclassgenerator;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.google.auto.service.AutoService;

@SupportedAnnotationTypes({ "ch.rasc.extclassgenerator.Model" })
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions({ "outputFormat", "debug", "includeValidation", "createBaseAndSubclass",
		"useSingleQuotes", "surroundApiWithQuotes", "lineEnding" })
@AutoService(Processor.class)
public class ClassAnnotationProcessor extends AbstractProcessor {

	private static final boolean ALLOW_OTHER_PROCESSORS_TO_CLAIM_ANNOTATIONS = false;

	private static final String OPTION_OUTPUTFORMAT = "outputFormat";

	private static final String OPTION_DEBUG = "debug";

	private static final String OPTION_INCLUDEVALIDATION = "includeValidation";

	private static final String OPTION_CREATEBASEANDSUBCLASS = "createBaseAndSubclass";

	private static final String OPTION_USESINGLEQUOTES = "useSingleQuotes";

	private static final String OPTION_SURROUNDAPIWITHQUOTES = "surroundApiWithQuotes";

	private static final String OPTION_LINEENDING = "lineEnding";

	@Override
	public boolean process(Set<? extends TypeElement> annotations,
			RoundEnvironment roundEnv) {

		this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
				"Running " + getClass().getSimpleName());

		if (roundEnv.processingOver() || annotations.size() == 0) {
			return ALLOW_OTHER_PROCESSORS_TO_CLAIM_ANNOTATIONS;
		}

		if (roundEnv.getRootElements() == null || roundEnv.getRootElements().isEmpty()) {
			this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
					"No sources to process");
			return ALLOW_OTHER_PROCESSORS_TO_CLAIM_ANNOTATIONS;
		}

		OutputConfig outputConfig = new OutputConfig();

		outputConfig.setDebug(
				!"false".equals(this.processingEnv.getOptions().get(OPTION_DEBUG)));
		boolean createBaseAndSubclass = "true".equals(
				this.processingEnv.getOptions().get(OPTION_CREATEBASEANDSUBCLASS));

		String outputFormatString = this.processingEnv.getOptions()
				.get(OPTION_OUTPUTFORMAT);
		outputConfig.setOutputFormat(OutputFormat.EXTJS4);
		if (outputFormatString != null && !outputFormatString.trim().isEmpty()) {
			if (OutputFormat.TOUCH2.name().equalsIgnoreCase(outputFormatString)) {
				outputConfig.setOutputFormat(OutputFormat.TOUCH2);
			}
			else if (OutputFormat.EXTJS5.name().equalsIgnoreCase(outputFormatString)) {
				outputConfig.setOutputFormat(OutputFormat.EXTJS5);
			}
		}

		String includeValidationString = this.processingEnv.getOptions()
				.get(OPTION_INCLUDEVALIDATION);
		outputConfig.setIncludeValidation(IncludeValidation.NONE);
		if (includeValidationString != null
				&& !includeValidationString.trim().isEmpty()) {
			if (IncludeValidation.ALL.name().equalsIgnoreCase(includeValidationString)) {
				outputConfig.setIncludeValidation(IncludeValidation.ALL);
			}
			else if (IncludeValidation.BUILTIN.name()
					.equalsIgnoreCase(includeValidationString)) {
				outputConfig.setIncludeValidation(IncludeValidation.BUILTIN);
			}
		}

		outputConfig.setUseSingleQuotes("true"
				.equals(this.processingEnv.getOptions().get(OPTION_USESINGLEQUOTES)));
		outputConfig.setSurroundApiWithQuotes("true".equals(
				this.processingEnv.getOptions().get(OPTION_SURROUNDAPIWITHQUOTES)));

		outputConfig.setLineEnding(LineEnding.SYSTEM);
		String lineEndingOption = this.processingEnv.getOptions().get(OPTION_LINEENDING);
		if (lineEndingOption != null) {
			try {
				LineEnding lineEnding = LineEnding
						.valueOf(lineEndingOption.toUpperCase());
				outputConfig.setLineEnding(lineEnding);
			}
			catch (Exception e) {
				// ignore an invalid value
			}
		}

		for (TypeElement annotation : annotations) {
			Set<? extends Element> elements = roundEnv
					.getElementsAnnotatedWith(annotation);

			for (Element element : elements) {

				try {
					TypeElement typeElement = (TypeElement) element;
					Elements elementsUtil = this.processingEnv.getElementUtils();
					String packageName = elementsUtil.getPackageOf(typeElement)
							.getQualifiedName().toString();

				 String code = ModelGenerator.generateJavascript(typeElement, elementsUtil,
				 outputConfig);

					Model modelAnnotation = element.getAnnotation(Model.class);
					String modelName = modelAnnotation.value();
					String fileName;
					String outPackageName = "";
					if (modelName != null && !modelName.trim().isEmpty()) {
						int lastDot = modelName.lastIndexOf('.');
						if (lastDot != -1) {
							fileName = modelName.substring(lastDot + 1);
							int firstDot = modelName.indexOf('.');
							if (firstDot < lastDot) {
								outPackageName = modelName.substring(firstDot + 1,
										lastDot);
							}
						}
						else {
							fileName = modelName;
						}
					}
					else {
						fileName = typeElement.getSimpleName().toString();
					}

					if (createBaseAndSubclass) {
						code = code.replaceFirst("(Ext.define\\([\"'].+?)([\"'],)",
								"$1Base$2");
						FileObject fo = this.processingEnv.getFiler().createResource(
								StandardLocation.SOURCE_OUTPUT, outPackageName,
								fileName + "Base.js");
						try (OutputStream os = fo.openOutputStream()) {
							os.write(code.getBytes(StandardCharsets.UTF_8));
						}

						try {
							fo = this.processingEnv.getFiler().getResource(
									StandardLocation.SOURCE_OUTPUT, outPackageName,
									fileName + ".js");
							try (InputStream is = fo.openInputStream()) {
								// nothing here
							}
						}
						catch (FileNotFoundException e) {
							/*
							 * TODO String subClassCode = generateSubclassCode(modelClass,
							 * outputConfig); fo =
							 * this.processingEnv.getFiler().createResource(
							 * StandardLocation.SOURCE_OUTPUT, outPackageName, fileName +
							 * ".js"); os = fo.openOutputStream();
							 * os.write(subClassCode.getBytes(StandardCharsets.UTF_8));
							 * os.close();
							 */
						}

					}
					else {
						FileObject fo = this.processingEnv.getFiler().createResource(
								StandardLocation.SOURCE_OUTPUT, outPackageName,
								fileName + ".js");
						try (OutputStream os = fo.openOutputStream()) {
							os.write(code.getBytes(StandardCharsets.UTF_8));
						}
					}

				}
				catch (Exception e) {
					this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
							e.getMessage());
				}

			}
		}

		return ALLOW_OTHER_PROCESSORS_TO_CLAIM_ANNOTATIONS;
	}

}
