package hava.annotation.spring.generators;

import com.squareup.javapoet.*;
import hava.annotation.spring.annotations.CRUD;
import hava.annotation.spring.annotations.Filter;
import hava.annotation.spring.builders.AnnotationBuilder;
import hava.annotation.spring.builders.ParameterBuilder;
import hava.annotation.spring.utils.ElementUtils;
import hava.annotation.spring.utils.MiscUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RepositoryGenerator {

	private ElementUtils eleUtils;
	private MiscUtils miscUtils;
	private AnnotationBuilder annBuilder;
	private ParameterBuilder parBuilder;

	private String suffix;
	private String classesPrefix;

	RepositoryGenerator(CodeGenerator codeGenerator, String suffix, String classesPrefix) {

		this.eleUtils = codeGenerator.eleUtils;
		this.miscUtils = codeGenerator.miscUtils;
		this.annBuilder = codeGenerator.annBuilder;
		this.parBuilder = codeGenerator.parBuilder;
		this.suffix = suffix;
		this.classesPrefix = classesPrefix;
	}

	TypeSpec generate(String prefix, CRUD crud) {

		TypeSpec.Builder builder = TypeSpec.interfaceBuilder(this.classesPrefix + prefix + this.suffix)
			.addModifiers(Modifier.PUBLIC)
			.addSuperinterface(
				getParameterizedTypeName(
					JpaRepository.class,
					eleUtils.elementTypeStr(),
					eleUtils.elementIdTypeStr())
			);

		if (crud.filter().fields().length > 0)
			builder.addMethod(getFilterMethod(crud.filter(), crud.pagination()));

		return builder.build();
	}


	private MethodSpec getFilterMethod(Filter filter, boolean pagination) {

		String[] fields = filter.fields();

		if (fields.length == 1 && "*".equals(fields[0])) {

			fields = eleUtils
				.getNonTransientFieldsNames()
				.toArray(new String[]{});
		}

		MethodSpec.Builder builder = addArguments(MethodSpec.methodBuilder("allByFilter"), fields);

		builder.addAnnotation(
			annBuilder.build(
				Query.class,
				getFilterQuery(fields, filter.likeType())))
			.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

		if (!pagination) {
			builder.returns(
				getParameterizedTypeName(
					List.class,
					eleUtils.elementTypeStr()));
		} else {

			builder
				.returns(
					getParameterizedTypeName(
						Page.class,
						eleUtils.elementTypeStr()))
				.addParameter(
				parBuilder.build("pageable", Pageable.class));
		}

		return builder.build();
	}

	private String getFilterQuery(String[] fields, Filter.LikeType likeType) {

		StringBuilder builder = new StringBuilder("select o from " + eleUtils.elementSimpleName() + " o");

		if (fields.length > 0)
			builder.append(" where ");

		for (int i = 0; i < fields.length; i++) {

			String field = fields[i];
			String line = "";
			String fieldType = eleUtils.getEnclosedTypeStr(field);

			line += "(:" + field + " is null or ";

			if (fieldType.equals("java.lang.String")
				&& likeType != Filter.LikeType.NONE) {

				line += "LOWER(o." + field + ") like LOWER(CONCAT(";

				if (likeType == Filter.LikeType.START  || likeType == Filter.LikeType.BOTH)
					line += "'%', ";

				line += ":" + field;

				if (likeType == Filter.LikeType.END || likeType == Filter.LikeType.BOTH)
					line += ", '%'";

				line += "))";
			} else {

				line += "o." + field + " = :" + field;
			}

			line += ")";

			if (i < fields.length - 1)
				line += " and ";

			builder.append(line);
		}

		return builder.toString();
	}

	private MethodSpec.Builder addArguments(MethodSpec.Builder builder, String[] fields) {

		Arrays.stream(fields)
			.forEach(field -> {

			TypeMirror fieldType = eleUtils.getEnclosedElement(field).asType();

			builder.addParameter(
				parBuilder.build(
					field,
					this.miscUtils.getTypeName(fieldType),
					annBuilder.build(Param.class, field))
			);
			});

		return builder;
	}

	private ParameterizedTypeName getParameterizedTypeName(Class<?> className, String... typesNames) {

		List<TypeVariableName> types = Arrays.stream(typesNames)
			.map(TypeVariableName::get)
			.collect(Collectors.toList());

		try {
			return ParameterizedTypeName.get(ClassName.get(className),
											types.toArray(new TypeVariableName[]{}));

		} catch (Exception e) {

			throw new RuntimeException(
				String.format("Could not get ParameterizedTypeName for %s using generic arguments %s: %s",
					className, Arrays.asList(typesNames), e.getMessage()));
		}
	}
}
