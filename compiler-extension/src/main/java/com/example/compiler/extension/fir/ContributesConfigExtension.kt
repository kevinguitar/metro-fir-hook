package com.example.compiler.extension.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
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
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
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

class ContributesConfigExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val contributesConfigPredicate = LookupPredicate.create {
        annotated(FqName("com.example.metrofirhook.ContributesConfig"))
    }

    private val provideConfigName = Name.identifier("provideConfig")
    private val metroPackage = FqName("dev.zacsweers.metro")
    private val configClassId = ClassId(FqName("com.example.metrofirhook"), Name.identifier("Config"))
    private val appScopeClassId = ClassId(metroPackage, Name.identifier("AppScope"))
    private val bindsClassId = ClassId(metroPackage, Name.identifier("Binds"))
    private val intoSetClassId = ClassId(metroPackage, Name.identifier("IntoSet"))

    // FIR cache for provider class ids to original class symbols.
    private val symbols: FirCache<Unit, Map<ClassId, FirRegularClassSymbol>, TypeResolveService?> =
        session.firCachesFactory.createCache { _, _ ->
            session.predicateBasedProvider
                .getSymbolsByPredicate(contributesConfigPredicate)
                .filterIsInstance<FirRegularClassSymbol>()
                .associateBy {
                    ClassId(
                        packageFqName = it.packageFqName(),
                        topLevelName = Name.identifier(it.name.asString() + "Provider")
                    )
                }
        }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(contributesConfigPredicate)
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelClassIds(): Set<ClassId> {
        return symbols.getValue(Unit).keys
    }

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
        val originalSymbol = symbols.getValue(Unit)[classId] ?: return null
        val configProvider = createTopLevelClass(classId, Key, classKind = ClassKind.INTERFACE) {
            modality = Modality.ABSTRACT
        }

        val configProviderAnnotation = buildAnnotation {
            annotationTypeRef = appScopeClassId.firTypeRef()

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

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        return if (classSymbol.classId in symbols.getValue(Unit)) {
            setOf(Name.identifier("provideConfig"))
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
                val originalSymbol = symbols.getValue(Unit)[outerClassId] ?: return emptyList()
                val createFunction = createMemberFunction(
                    owner = owner,
                    key = Key,
                    name = callableId.callableName,
                    returnType = configClassId.constructClassLikeType(arrayOf(ConeStarProjection))
                ) {
                    modality = Modality.ABSTRACT
                    valueParameter(
                        name = Name.identifier("config"),
                        type = originalSymbol.classId.constructClassLikeType(),
                    )
                }

                val targetParam = createFunction.valueParameters.first()
                val targetParamWithProvides = buildValueParameterCopy(targetParam) {
                    symbol = targetParam.symbol
                    annotations += bindsClassId.firAnnotation()
                    annotations += intoSetClassId.firAnnotation()
                }
                createFunction.replaceValueParameters(listOf(targetParamWithProvides))

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