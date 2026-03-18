package com.igancao.bridge.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import java.util.TreeMap

const val BRIDGE_ACTION_ANNOTATION = "com.igancao.hpt_jsbridge.BridgeAction"

class BridgeActionProcessor(
    environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val options: Map<String, String> = environment.options
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val symbols = resolver.getSymbolsWithAnnotation(BRIDGE_ACTION_ANNOTATION)
        val constants = TreeMap<String, String>()
        val declarations = mutableListOf<KSDeclaration>()

        symbols.forEach { symbol ->
            if (symbol !is KSFunctionDeclaration) return@forEach
            declarations += symbol
            val actionName = resolveActionName(symbol)
            val constantName = toUpperSnake(actionName)
            val previous = constants.putIfAbsent(constantName, actionName)
            if (previous != null && previous != actionName) {
                logger.error(
                    "BridgeAction 常量冲突: $constantName -> [$previous] vs [$actionName]",
                    symbol
                )
                return emptyList()
            }
        }

        if (constants.isEmpty()) return emptyList()

        val packageName = options["bridgeActions.package"]?.takeIf { it.isNotBlank() } ?: "com.igancao.hpt_jsbridge.generated"
        val className = options["bridgeActions.className"]?.takeIf { it.isNotBlank() } ?: "BridgeActions"

        val dependencies = Dependencies(
            aggregating = true,
            sources = declarations.mapNotNull { it.containingFile }.toTypedArray()
        )
        val typeBuilder = TypeSpec.objectBuilder(className)
        for ((name, value) in constants) {
            typeBuilder.addProperty(
                PropertySpec.builder(name, STRING)
                    .addModifiers(KModifier.CONST)
                    .initializer("%S", value)
                    .build()
            )
        }
        FileSpec.builder(packageName, className)
            .addType(typeBuilder.build())
            .build()
            .writeTo(codeGenerator, dependencies)

        generated = true
        return emptyList()
    }

    private fun resolveActionName(function: KSFunctionDeclaration): String {
        val annotation = function.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == BRIDGE_ACTION_ANNOTATION
        }
        val configured = annotation?.arguments
            ?.firstOrNull { it.name?.asString() == "value" }
            ?.value
            ?.toString()
            ?.trim()
            .orEmpty()
        return if (configured.isNotEmpty()) configured else function.simpleName.asString()
    }

    private fun toUpperSnake(value: String): String {
        val result = StringBuilder()
        value.forEachIndexed { index, c ->
            when {
                c.isUpperCase() && index > 0 -> {
                    result.append('_').append(c)
                }
                c.isLetterOrDigit() -> {
                    result.append(c.uppercaseChar())
                }
                else -> result.append('_')
            }
        }
        return result.toString().replace(Regex("_+"), "_")
    }
}
