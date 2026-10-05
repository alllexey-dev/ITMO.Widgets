package dev.alllexey.itmowidgets.client.contract

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.json.IsoLocalDateSerializer
import dev.alllexey.itmowidgets.client.json.IsoLocalTimeSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import dev.alllexey.itmowidgets.client.support.RecordedRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.descriptors.nonNullOriginal

/**
 * The checks of [ContractConformanceTest] as functions of their inputs, so that seeded drifts can be fed in. Each
 * returns the problems it found, one line each; an empty list is a pass.
 */
object ContractConformance {

    /**
     * Every recorded request is one OpenAPI operation with the same query parameter names, and every operation is
     * either recorded or covered by exactly one [NotMirroredRoute], never both.
     */
    fun routeProblems(
        spec: OpenApi,
        recorded: List<Pair<String, RecordedRequest>>,
        notMirrored: List<NotMirroredRoute>,
    ): List<String> {
        val problems = mutableListOf<String>()
        val mirrored = mutableSetOf<Operation>()
        for ((name, request) in recorded) {
            val operation = spec.operationOf(request)
            if (operation == null) {
                problems += "$name: ${request.method.value} ${request.path} is no Backend operation"
                continue
            }
            mirrored += operation
            val sent = request.query.map { it.first }.toSet()
            if (sent != operation.queryNames) {
                problems += "$name: $operation takes query ${operation.queryNames.sorted()}, the client sends " +
                    "${sent.sorted()}"
            }
        }
        for (operation in spec.operations) {
            val covering = notMirrored.filter { it.covers(operation) }
            when {
                operation in mirrored && covering.isNotEmpty() ->
                    problems += "$operation is mirrored and listed as not mirrored ($covering)"
                operation !in mirrored && covering.isEmpty() ->
                    problems += "$operation is neither mirrored nor in NotMirrored"
                covering.size > 1 -> problems += "$operation is listed as not mirrored twice ($covering)"
            }
        }
        for (route in notMirrored) {
            if (spec.operations.none { route.covers(it) }) problems += "NotMirrored $route matches no operation"
        }
        return problems
    }

    /** The most specific operation [request] matches, or `null`. */
    private fun OpenApi.operationOf(request: RecordedRequest): Operation? =
        operations.filter { it.matches(request.method, request.path) }.maxByOrNull { it.literalSegments }

    /**
     * Every model matches its component schema: the same property names except [pending] fields, a non-null
     * property without a default is `required`, a property Backend may send as `null` is nullable, enum constants
     * except `UNKNOWN` equal the schema's `enum`, a nested model refers to the same schema, and a sealed model has
     * the schema's `oneOf` with the same discriminator values. A model may only reach listed models and enums.
     */
    fun modelProblems(
        spec: OpenApi,
        models: List<WireModel>,
        enums: List<WireEnum>,
        pending: List<PendingBackendField>,
    ): List<String> = ModelCheck(spec, models, enums, pending).problems()

    /**
     * Every file is claimed exactly once, and every claim names a file. [claims] pairs an owner with a
     * `contract/`-relative path; a path ending in `/` claims the directory.
     */
    fun claimProblems(files: List<String>, claims: List<Pair<String, String>>): List<String> {
        val problems = mutableListOf<String>()
        for (file in files) {
            val owners = claims.filter { (_, path) -> path == file || (path.endsWith("/") && file.startsWith(path)) }
            when {
                owners.isEmpty() -> problems += "$file is not claimed"
                owners.size > 1 -> problems += "$file is claimed by ${owners.map { it.first }}"
            }
        }
        for ((owner, path) in claims) {
            if (files.none { it == path || (path.endsWith("/") && it.startsWith(path)) }) {
                problems += "$owner claims $path, no such file"
            }
        }
        return problems
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class ModelCheck(
    private val spec: OpenApi,
    private val models: List<WireModel>,
    enums: List<WireEnum>,
    private val pending: List<PendingBackendField>,
) {
    private val problems = mutableListOf<String>()
    private val enumsBySerialName = enums.associateBy { it.serializer.descriptor.serialName }

    /** Subtype descriptor to its discriminator value, for every sealed model. */
    private val subtypes: Map<SerialDescriptor, String> = models
        .filter { it.serializer.descriptor.kind == PolymorphicKind.SEALED }
        .flatMap { model ->
            val variants = model.serializer.descriptor.getElementDescriptor(1)
            variants.elementNames.zip(variants.elementDescriptors.toList()).map { (value, descriptor) ->
                descriptor to value
            }
        }
        .toMap()

    fun problems(): List<String> {
        for (model in models) check(model)
        for (entry in pending) {
            if (models.none { it.name == entry.schema }) problems += "PendingBackendFields $entry names no model"
        }
        return problems
    }

    private fun check(model: WireModel) {
        val descriptor = model.serializer.descriptor
        val schema = spec.schema(model.name) ?: return run { problems += "$model has no Backend schema" }
        if (descriptor !in subtypes && descriptor.serialName.substringAfterLast('.') != model.name) {
            problems += "$model is serialized as ${descriptor.serialName}"
        }
        when (descriptor.kind) {
            PolymorphicKind.SEALED -> checkSealed(model, descriptor, schema)
            StructureKind.CLASS -> checkClass(model, descriptor, schema)
            else -> problems += "$model is a ${descriptor.kind}, not a class"
        }
    }

    private fun checkSealed(model: WireModel, descriptor: SerialDescriptor, schema: Schema) {
        val discriminator = BackendJson.configuration.classDiscriminator
        if (schema.oneOf.isEmpty() || schema.discriminator != discriminator) {
            return run { problems += "$model is sealed on \"$discriminator\", Backend's schema is no such oneOf" }
        }
        val variants = descriptor.getElementDescriptor(1)
        val clientMapping = variants.elementNames.zip(variants.elementDescriptors.toList()).associate { (value, sub) ->
            value to (models.firstOrNull { it.serializer.descriptor == sub }?.name ?: "an unlisted model")
        }
        if (clientMapping != schema.mapping) {
            problems += "$model maps $discriminator values $clientMapping, Backend ${schema.mapping}"
        }
        if (schema.oneOf.toSet() != schema.mapping.values.toSet()) {
            problems += "$model: Backend's oneOf ${schema.oneOf} and mapping ${schema.mapping} differ"
        }
    }

    private fun checkClass(model: WireModel, descriptor: SerialDescriptor, schema: Schema) {
        if (schema.oneOf.isNotEmpty()) problems += "$model is a class, Backend's schema is a oneOf"
        val skipped = pending.filter { it.schema == model.name }.map { it.property }.toSet()
        for (property in skipped) {
            if (property in schema.properties) {
                problems += "${model.name}.$property is in Backend's schema now; remove it from PendingBackendFields"
            }
            if (property !in descriptor.elementNames) {
                problems += "PendingBackendFields ${model.name}.$property: no such field"
            }
        }
        val discriminatorValue = subtypes[descriptor]
        val discriminator = BackendJson.configuration.classDiscriminator
        val client = descriptor.elementNames.toSet() - skipped
        val backend = schema.properties.keys - setOfNotNull(discriminatorValue?.let { discriminator })
        for (name in (client - backend).sorted()) problems += "${model.name}.$name is not in Backend's schema"
        for (name in (backend - client).sorted()) problems += "${model.name} lacks Backend's property $name"
        if (discriminatorValue != null) {
            val property = schema.properties[discriminator]
            if (property?.enum != setOf(discriminatorValue) || discriminator !in schema.required) {
                problems += "${model.name}: Backend's \"$discriminator\" is not the required \"$discriminatorValue\""
            }
        }
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            val property = schema.properties[name] ?: continue
            checkProperty(model, descriptor, index, property, name in schema.required)
        }
    }

    private fun checkProperty(
        model: WireModel,
        descriptor: SerialDescriptor,
        index: Int,
        property: Property,
        required: Boolean,
    ) {
        val label = "${model.name}.${property.name}"
        val element = descriptor.getElementDescriptor(index)
        if (!element.isNullable && !descriptor.isElementOptional(index) && !required) {
            problems += "$label is non-null without a default, Backend does not mark it required"
        }
        if (property.isNullable && !element.isNullable) problems += "$label may be null on Backend, not in the client"
        val target = element.target()
        val enum = enumsBySerialName[target.serialName]
        when {
            enum != null -> {
                val constants = enum.constants.toSet() - UNKNOWN
                if (constants != property.enum) {
                    problems += "$label: ${enum.name} has ${constants.sorted()}, Backend ${property.enum?.sorted()}"
                }
            }
            target.serialName.startsWith(CLIENT_PACKAGE) && target.kind == StructureKind.CLASS ||
                target.kind == PolymorphicKind.SEALED -> {
                val nested = models.firstOrNull { it.serializer.descriptor == target }
                when {
                    nested == null -> problems += "$label: ${target.serialName} is not in WireModels"
                    nested.name != property.ref ->
                        problems += "$label is a ${nested.name}, Backend's is ${property.ref}"
                }
            }
            target.serialName.startsWith(CLIENT_PACKAGE) && target !in KNOWN_SCALARS ->
                problems += "$label: ${target.serialName} is no listed enum"
        }
    }

    /** The descriptor of a property's value: without nullability and, for a list, its element. */
    private fun SerialDescriptor.target(): SerialDescriptor {
        val value = nonNullOriginal
        return if (value.kind == StructureKind.LIST) value.getElementDescriptor(0).nonNullOriginal else value
    }

    private companion object {
        const val UNKNOWN = "UNKNOWN"
        const val CLIENT_PACKAGE = "dev.alllexey.itmowidgets.client."

        /** The client's string-backed scalar serializers (`json` package); everything else there is an enum. */
        val KNOWN_SCALARS: List<SerialDescriptor> = listOf(
            WireInstantSerializer.descriptor,
            UuidSerializer.descriptor,
            IsoLocalDateSerializer.descriptor,
            IsoLocalTimeSerializer.descriptor,
        )
    }
}
