package com.example.metrofirhook.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.buildUnaryArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildGetClassCall
import org.jetbrains.kotlin.fir.expressions.builder.buildResolvedQualifier
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension.TypeResolveService
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

class ContributesConfigExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    private val contributesConfigPredicate = LookupPredicate.create {
        annotated(FqName("com.example.metrofirhook.ContributesConfig"))
    }

    private val appScopeClassId =
        ClassId(FqName("dev.zacsweers.metro"), Name.identifier("AppScope"))

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

    object Key : GeneratedDeclarationKey()
}

private fun ClassId.firTypeRef(): FirTypeRef = buildResolvedTypeRef {
    coneType = constructClassLikeType()
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