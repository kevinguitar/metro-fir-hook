package com.example.metrofirhook.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

//class MetaAnnotationExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {
//
//    // Define the annotation names
//    private val oldAnnotationFqName = FqName("com.example.OldAnnotation")
//    private val newAnnotationClassId = ClassId.topLevel(FqName("com.example.NewAnnotation"))
//
//    // 1. Register Predicates
//    // This tells the compiler: "Only wake me up for declarations that have @OldAnnotation"
//    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
//        register(LookupPredicate.create { annotated(oldAnnotationFqName) })
//    }
//
//    // 2. Generate Function Names
//    // If the user writes a class, what *names* of functions should we add to it?
//    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> {
//        // Only generate if the class itself is annotated or matches your criteria
//        if (!classSymbol.hasAnnotation(oldAnnotationFqName, session)) return emptySet()
//
//        // We will generate a function named "generatedWrapper"
//        return setOf(Name.identifier("generatedWrapper"))
//    }
//
//    // 3. Generate the Function Bodies
//    override fun generateFunctions(
//        callableId: CallableId,
//        context: MemberGenerationContext?
//    ): List<FirNamedFunctionSymbol> {
//
//        // Check if we are responsible for this specific function name
//        if (callableId.callableName.asString() != "generatedWrapper") return emptyList()
//
//        val owner = context?.owner ?: return emptyList()
//
//        // createMemberFunction is a helper that simplifies building FIR nodes
//        val newFunctionSymbol = createMemberFunction(
//            owner,
//            key = Key, // A unique key for caching
//            callableId.callableName,
//            returnType = session.builtinTypes.unitType.type
//        ) {
//            // --- HERE IS THE REPLACEMENT LOGIC ---
//
//            // Instead of adding the OldAnnotation, we add the NewAnnotation
//            // to this generated function.
//            annotation {
//                this.typeRef = buildResolvedTypeRef {
//                    type = ConeClassLikeTypeImpl(
//                        lookupTag = newAnnotationClassId.toLookupTag(),
//                        typeArguments = emptyArray(),
//                        isNullable = false
//                    )
//                }
//                // You can also set arguments for the annotation here
//            }
//        }
//
//        return listOf(newFunctionSymbol)
//    }
//
//    // 4. Key Definition
//    // Used by FIR to cache the generated declaration
//    object Key : GeneratedDeclarationKey()
//}