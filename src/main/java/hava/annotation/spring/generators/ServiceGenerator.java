package hava.annotation.spring.generators;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import hava.annotation.spring.annotations.CRUD;
import hava.annotation.spring.annotations.Filter;
import hava.annotation.spring.utils.ElementUtils;
import hava.annotation.spring.builders.ParameterBuilder;
import hava.annotation.spring.utils.MiscUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Arrays;

public class ServiceGenerator {


	private ElementUtils eleUtils;
	private MiscUtils miscUtils;
	private ParameterBuilder parBuilder;

	private String suffix;
	private String repSuffix;
	private String classesPrefix;
	private String name;

	private boolean pagination;

	public ServiceGenerator(CodeGenerator codeGenerator, String suffix, String repSuffix, String classesPrefix) {

		this.eleUtils = codeGenerator.eleUtils;
		this.miscUtils = codeGenerator.miscUtils;
		this.parBuilder = codeGenerator.parBuilder;
		this.suffix = suffix;
		this.repSuffix = repSuffix;
		this.classesPrefix = classesPrefix;
	}

	public TypeSpec generate(String name, CRUD crud) {

		this.pagination = crud.pagination();
		this.name = name;

		MethodSpec save = this.createMethod("save")
			.addParameter(this.eleUtils.elementParam())
			.addStatement("return $T.ok(this.repository.save(entity))", ResponseEntity.class)
			.build();

		MethodSpec one = this.createMethod("one")
			.addParameter(this.eleUtils.elementIdParam())
			.addStatement("return $T.ok(this.repository.findById(id))", ResponseEntity.class)
			.build();

		MethodSpec all = crud.filter().fields().length > 0 ? this.filterableAll(crud.filter()) : this.simpleAll();

		MethodSpec delete = this.createMethod("delete")
			.addParameter(this.eleUtils.elementIdParam())
			.addStatement("this.repository.deleteById(id)")
			.addStatement("return new $T($T.NO_CONTENT)", ResponseEntity.class, HttpStatus.class)
			.build();

		return TypeSpec.classBuilder(this.classesPrefix + name + this.suffix)
			.addModifiers(Modifier.PUBLIC)
			.addField(
				this.miscUtils.autowire("repository",
					repositoryClassName()))
			.addAnnotation(Component.class)
			.addMethod(save)
			.addMethod(one)
			.addMethod(all)
			.addMethod(delete)
			.build();
	}


	private MethodSpec simpleAll() {

		MethodSpec.Builder builder = this.createMethod("all");

		if (!this.pagination)
			builder.addStatement("return $T.ok(this.repository.findAll())", ResponseEntity.class);
		else {
			addPageability(builder);
			builder
				.addStatement("return $T.ok(this.repository.findAll(pageable))", ResponseEntity.class);
		}

		return builder
			.build();
	}

	private MethodSpec filterableAll(Filter filter) {

		String[] fields = filter.fields();

		if (fields.length == 1 && "*".equals(fields[0])) {

			fields = this.eleUtils
				.getNonTransientFieldsNames()
				.toArray(new String[]{});
		}

		MethodSpec.Builder builder = this.addArguments(
			this.createMethod("allByFilter"), fields);

		if (this.pagination)
			addPageability(builder);

		return builder
			.addStatement(getReturn(fields), ResponseEntity.class)
			.build();
	}

	private String getReturn(String[] fields) {

		StringBuilder builder = new StringBuilder("return $T.ok(this.repository.allByFilter(");

		for (int i = 0; i < fields.length; i++) {
			String field = fields[i];
			builder.append(field);
			if (i < fields.length - 1)
				builder.append(", ");
		}

		if (this.pagination)
			builder.append(", pageable");

		builder.append("))");
		return builder.toString();
	}

	private MethodSpec.Builder addArguments(MethodSpec.Builder builder, String[] fields) {

		Arrays.stream(fields)
			.forEach(field -> {
			TypeMirror fieldType = this.eleUtils.getEnclosedElement(field).asType();

			builder.addParameter(
				this.parBuilder.build(
					field,
					this.miscUtils.getTypeName(fieldType)));
			});

		return builder;
	}

	private void addPageability(MethodSpec.Builder builder) {

		builder.addParameter(this.parBuilder.build("page", Integer.class))
			.addParameter(this.parBuilder.build("pageSize", Integer.class));

		builder.addStatement("$T pageable", Pageable.class)
			.beginControlFlow("if (page == null || pageSize == null)")
			.addStatement("pageable = $T.of(0, $T.MAX_VALUE)", PageRequest.class, Integer.class)
			.nextControlFlow("else")
			.addStatement("pageable = $T.of(page, pageSize)", PageRequest.class)
			.endControlFlow();
	}


	private MethodSpec.Builder createMethod(String name) {

		return MethodSpec.methodBuilder(name)
			.addModifiers(Modifier.PUBLIC)
			.returns(ResponseEntity.class);
	}

	private String repositoryClassName() {

		return this.eleUtils.packageName() + "." + this.classesPrefix + this.name + this.repSuffix;
	}
}