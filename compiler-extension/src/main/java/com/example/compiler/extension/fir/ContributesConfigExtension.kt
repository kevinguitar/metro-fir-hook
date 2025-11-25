package com.example.compiler.extension.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.outerClassSymbol
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameterCopy
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.buildUnaryArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * This FIR extension generates a multi-binding provider of the config.
 *
 * For example:
 * ```kotlin
 * @ContributesConfig
 * object StringConfig : Config<String> {
 *     override val key: String get() = "string"
 *     override val value: String get() = "Hello Metro!"
 * }
 * ```
 *
 * will generate an inner object
 *
 * ```kotlin
 * @ContributesConfig
 * object StringConfig : Config<String> {
 *     //...
 *
 *     @ContributesTo(scope = AppScope::class)
 *     interface Provider {
 *         @Binds @IntoSet
 *         fun provideConfig(config: StringConfig): Config<*>
 *     }
 * }
 * ```
 */
class ContributesConfigExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val contributesConfigPredicate = LookupPredicate.create {
        annotated(FqName("com.example.metrofirhook.ContributesConfig"))
    }

    private val provideConfigName = Name.identifier("provideConfig")
    private val providerName = Name.identifier("Provider")
    private val metroPackage = FqName("dev.zacsweers.metro")
    private val configClassId =
        ClassId(FqName("com.example.metrofirhook"), Name.identifier("Config"))
    private val appScopeClassId = ClassId(metroPackage, Name.identifier("AppScope"))
    private val contributesToClassId = ClassId(metroPackage, Name.identifier("ContributesTo"))
    private val bindsClassId = ClassId(metroPackage, Name.identifier("Binds"))
    private val intoSetClassId = ClassId(metroPackage, Name.identifier("IntoSet"))

    private val symbols: FirCache<Unit, List<ClassId>, TypeResolveService?> =
        session.firCachesFactory.createCache { _, _ ->
            session.predicateBasedProvider
                .getSymbolsByPredicate(contributesConfigPredicate)
                .filterIsInstance<FirRegularClassSymbol>()
                .map { it.classId }
        }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(contributesConfigPredicate)
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        return if (classSymbol.classId in symbols.getValue(Unit)) {
            setOf(providerName)
        } else {
            emptySet()
        }
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (name != providerName) return null
        val configProvider = createNestedClass(owner, providerName, Key, classKind = ClassKind.INTERFACE) {
            modality = Modality.ABSTRACT
        }

        val configProviderAnnotation = buildAnnotation {
            annotationTypeRef = contributesToClassId.firTypeRef()

            argumentMapping = buildAnnotationArgumentMapping {
                val appScope = session.symbolProvider
                    .getClassLikeSymbolByClassId(appScopeClassId) as FirRegularClassSymbol
                mapping[Name.identifier("scope")] = appScope.getClassCall()
            }
        }
        configProvider.replaceAnnotations(
            listOf(configProviderAnnotation)
        )

        return configProvider.symbol
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        return if (classSymbol.classId.outerClassId in symbols.getValue(Unit)) {
            setOf(provideConfigName)
        } else {
            emptySet()
        }
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()

        when (callableId.callableName) {
            provideConfigName -> {
                val ownerClassId = owner.classId
                val outerClassId = ownerClassId.outerClassId ?: return emptyList()
                val createFunction = createMemberFunction(
                    owner = owner,
                    key = Key,
                    name = callableId.callableName,
                    returnType = configClassId.constructClassLikeType(arrayOf(ConeStarProjection))
                ) {
                    modality = Modality.ABSTRACT
                    valueParameter(
                        name = Name.identifier("config"),
                        type = outerClassId.constructClassLikeType(),
                    )
                }

                createFunction.replaceAnnotations(
                    listOf(
                        bindsClassId.firAnnotation(),
                        intoSetClassId.firAnnotation()
                    )
                )

                return listOf(createFunction.symbol)
            }

            else -> error("Unexpected callable name: ${callableId.callableName.asString()}")
        }
    }

    object Key : GeneratedDeclarationKey()
}

private fun ClassId.firTypeRef(): FirTypeRef = buildResolvedTypeRef {
    coneType = constructClassLikeType()
}

private fun ClassId.firAnnotation(): FirAnnotation = buildAnnotation {
    annotationTypeRef = firTypeRef()
    argumentMapping = FirEmptyAnnotationArgumentMapping
}

/**
 * Returns a `ClassSymbol::class` expression.
 */
private fun FirRegularClassSymbol.getClassCall(): FirExpression = buildGetClassCall {
    argumentList = buildUnaryArgumentList(
        buildResolvedQualifier {
            packageFqName = classId.packageFqName
            relativeClassFqName = classId.relativeClassName
            symbol = this@getClassCall
            resolvedToCompanionObject = false
            coneTypeOrNull = this@getClassCall.defaultType()
        }
    )
    coneTypeOrNull = StandardClassIds.KClass.constructClassLikeType(
        arrayOf(this@getClassCall.defaultType())
    )
}