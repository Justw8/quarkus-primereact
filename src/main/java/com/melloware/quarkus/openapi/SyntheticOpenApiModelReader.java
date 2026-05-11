package com.melloware.quarkus.openapi;

import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASModelReader;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.Components;

/**
 * Injects a large synthetic OpenAPI surface so build-time generation can be stressed on demand.
 */
public class SyntheticOpenApiModelReader implements OASModelReader {

	static final String ENABLED_PROPERTY = "app.openapi.synthetic.enabled";
	static final String MULTIPLIER_PROPERTY = "app.openapi.synthetic.multiplier";
	private static final int SCHEMAS_PER_MULTIPLIER = 100;
	private static final int PATHS_PER_MULTIPLIER = 10;
	private static final int FIELDS_PER_SCHEMA = 20;
	private static final String SYNTHETIC_TAG = "Synthetic OpenAPI Load";

	@Override
	public OpenAPI buildModel() {
		if (!ConfigProvider.getConfig().getOptionalValue(ENABLED_PROPERTY, Boolean.class).orElse(false)) {
			return OASFactory.createOpenAPI();
		}

		final int multiplier = Math.max(1,
				ConfigProvider.getConfig().getOptionalValue(MULTIPLIER_PROPERTY, Integer.class).orElse(1));
		final Components components = OASFactory.createComponents();
		final Paths paths = OASFactory.createPaths();

		for (int group = 0; group < multiplier; group++) {
			for (int schemaIndex = 0; schemaIndex < SCHEMAS_PER_MULTIPLIER; schemaIndex++) {
				final String schemaName = schemaName(group, schemaIndex);
				components.addSchema(schemaName, createSyntheticSchema(group, schemaIndex));
			}

			for (int pathIndex = 0; pathIndex < PATHS_PER_MULTIPLIER; pathIndex++) {
				final String requestSchemaName = schemaName(group,
						pathIndex % SCHEMAS_PER_MULTIPLIER);
				final String responseSchemaName = schemaName(group,
						(pathIndex + 1) % SCHEMAS_PER_MULTIPLIER);
				paths.addPathItem(pathName(group, pathIndex),
						createSyntheticPathItem(group, pathIndex, requestSchemaName, responseSchemaName));
			}
		}

		return OASFactory.createOpenAPI().components(components).paths(paths);
	}

	private PathItem createSyntheticPathItem(int group, int pathIndex, String requestSchemaName, String responseSchemaName) {
		return OASFactory.createPathItem()
				.summary(String.format("Synthetic OpenAPI load group %d path %d", group, pathIndex))
				.description(repeatedText("Synthetic path payload for asynchronous OpenAPI and frontend generation load", group,
						pathIndex, 3))
				.GET(createGetOperation(group, pathIndex, responseSchemaName))
				.POST(createPostOperation(group, pathIndex, requestSchemaName, responseSchemaName));
	}

	private Operation createGetOperation(int group, int pathIndex, String responseSchemaName) {
		return OASFactory.createOperation()
				.operationId(String.format("getSyntheticLoadG%03dP%03d", group, pathIndex))
				.summary(String.format("Synthetic load GET %d-%d", group, pathIndex))
				.description(repeatedText("Synthetic GET operation that exists only to amplify OpenAPI generation volume",
						group, pathIndex, 3))
				.addTag(SYNTHETIC_TAG)
				.addParameter(queryParameter("tenant", group, pathIndex))
				.addParameter(queryParameter("cursor", group, pathIndex))
				.addParameter(queryParameter("projection", group, pathIndex))
				.responses(successAndProblemResponses(responseSchemaName));
	}

	private Operation createPostOperation(int group, int pathIndex, String requestSchemaName, String responseSchemaName) {
		return OASFactory.createOperation()
				.operationId(String.format("postSyntheticLoadG%03dP%03d", group, pathIndex))
				.summary(String.format("Synthetic load POST %d-%d", group, pathIndex))
				.description(repeatedText("Synthetic POST operation that exists only to amplify OpenAPI generation volume",
						group, pathIndex, 3))
				.addTag(SYNTHETIC_TAG)
				.requestBody(OASFactory.createRequestBody()
						.description(repeatedText("Synthetic request body for OpenAPI stress testing", group, pathIndex, 2))
						.required(Boolean.TRUE)
						.content(jsonContent(schemaRef(requestSchemaName))))
				.responses(successAndProblemResponses(responseSchemaName));
	}

	private Parameter queryParameter(String name, int group, int pathIndex) {
		return OASFactory.createParameter()
				.name(name)
				.in(Parameter.In.QUERY)
				.description(repeatedText(String.format("Synthetic query parameter '%s' for OpenAPI load generation", name),
						group, pathIndex, 2))
				.required(Boolean.FALSE)
				.schema(OASFactory.createSchema()
						.addType(Schema.SchemaType.STRING)
						.examples(List.of(String.format("%s-%03d-%03d", name, group, pathIndex))));
	}

	private APIResponses successAndProblemResponses(String responseSchemaName) {
		return OASFactory.createAPIResponses()
				.addAPIResponse("200", OASFactory.createAPIResponse()
						.description("Synthetic success response")
						.content(jsonContent(schemaRef(responseSchemaName))))
				.addAPIResponse("400", simpleResponse("Synthetic validation failure response"))
				.addAPIResponse("422", simpleResponse("Synthetic semantic validation failure response"));
	}

	private APIResponse simpleResponse(String description) {
		return OASFactory.createAPIResponse()
				.description(description)
				.content(jsonContent(OASFactory.createSchema()
						.addType(Schema.SchemaType.OBJECT)
						.addProperty("code", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
								.examples(List.of("SYNTHETIC_ERROR")))
						.addProperty("message", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
								.examples(List.of(description)))));
	}

	private Content jsonContent(Schema schema) {
		return OASFactory.createContent()
				.addMediaType("application/json", OASFactory.createMediaType().schema(schema));
	}

	private Schema createSyntheticSchema(int group, int schemaIndex) {
		final Schema schema = OASFactory.createSchema()
				.addType(Schema.SchemaType.OBJECT)
				.description(repeatedText("Synthetic schema used only to enlarge generated OpenAPI documents", group,
						schemaIndex, 4));

		for (int fieldIndex = 0; fieldIndex < FIELDS_PER_SCHEMA; fieldIndex++) {
			final String fieldName = String.format("field%02d", fieldIndex);
			schema.addProperty(fieldName, createSyntheticField(group, schemaIndex, fieldIndex));
			schema.addRequired(fieldName);
		}

		schema.addProperty("status", OASFactory.createSchema()
				.addType(Schema.SchemaType.STRING)
				.description(repeatedText("Synthetic lifecycle state", group, schemaIndex, 2))
				.enumeration(List.of("CREATED", "VALIDATED", "PUBLISHED", "ARCHIVED"))
				.examples(List.of("PUBLISHED")));
		schema.addProperty("labels", OASFactory.createSchema()
				.addType(Schema.SchemaType.ARRAY)
				.description(repeatedText("Synthetic labels array", group, schemaIndex, 2))
				.items(OASFactory.createSchema().addType(Schema.SchemaType.STRING).examples(List.of("label-alpha"))));
		schema.addProperty("metadata", OASFactory.createSchema()
				.addType(Schema.SchemaType.OBJECT)
				.description(repeatedText("Synthetic metadata map", group, schemaIndex, 2))
				.additionalPropertiesSchema(OASFactory.createSchema().addType(Schema.SchemaType.STRING)
						.examples(List.of("value"))));

		return schema;
	}

	private Schema createSyntheticField(int group, int schemaIndex, int fieldIndex) {
		if (fieldIndex % 3 == 0) {
			return OASFactory.createSchema()
					.addType(Schema.SchemaType.STRING)
					.description(repeatedText("Synthetic text field for OpenAPI size amplification", group,
							schemaIndex + fieldIndex, 3))
					.examples(List.of(String.format("g%03d-s%02d-f%02d", group, schemaIndex, fieldIndex)))
					.maxLength(128);
		}

		if (fieldIndex % 3 == 1) {
			return OASFactory.createSchema()
					.addType(Schema.SchemaType.INTEGER)
					.format("int32")
					.description(repeatedText("Synthetic numeric field for OpenAPI size amplification", group,
							schemaIndex + fieldIndex, 3))
					.examples(List.of(group * 1000 + schemaIndex * 100 + fieldIndex))
					.minimum(java.math.BigDecimal.ZERO)
					.maximum(java.math.BigDecimal.valueOf(999999));
		}

		return OASFactory.createSchema()
				.addType(Schema.SchemaType.ARRAY)
				.description(repeatedText("Synthetic nested array field for OpenAPI size amplification", group,
						schemaIndex + fieldIndex, 3))
				.items(OASFactory.createSchema()
						.addType(Schema.SchemaType.OBJECT)
						.addProperty("code", OASFactory.createSchema().addType(Schema.SchemaType.STRING)
								.examples(List.of(String.format("C-%03d-%02d-%02d", group, schemaIndex, fieldIndex))))
						.addProperty("weight", OASFactory.createSchema().addType(Schema.SchemaType.NUMBER)
								.format("double")
								.examples(List.of(fieldIndex + 0.5))));
	}

	private Schema schemaRef(String schemaName) {
		return OASFactory.createSchema().ref("#/components/schemas/" + schemaName);
	}

	private String schemaName(int group, int schemaIndex) {
		return String.format("SyntheticPayloadG%03dS%02d", group, schemaIndex);
	}

	private String pathName(int group, int pathIndex) {
		return String.format("/synthetic/group-%03d/resource-%03d", group, pathIndex);
	}

	private String repeatedText(String prefix, int group, int item, int repeatCount) {
		final String base = String.format("%s [group=%03d item=%03d]", prefix, group, item);
		return String.join(" ", java.util.Collections.nCopies(repeatCount, base + "."));
	}
}
