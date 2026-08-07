import Formalisation.GradualEffects.Core

namespace GradualEffects

variable {Privilege Tag : Type}
variable [DecidableEq Privilege] [Fintype Privilege] [DecidableEq Tag]

inductive SourceTyped (model : EffectModel Privilege Tag) :
    CPrivSet Privilege → Context Privilege Tag → StoreTyping Privilege Tag →
      SourceTerm Privilege Tag → Ty Privilege Tag → Prop where
  | unit {privileges context store tag} :
      SourceTyped model privileges context store (.unit tag) (.unit {tag})
  | variable {privileges context store index type}
      (lookup : context[index]? = some type) :
      SourceTyped model privileges context store (.variable index) type
  | location {privileges context store index tag type}
      (lookup : store[index]? = some type) :
      SourceTyped model privileges context store (.location index tag) (.ref {tag} type)
  | function {privileges bodyPrivileges context store parameterType body bodyType tag}
      (bodyTyping : SourceTyped model bodyPrivileges (parameterType :: context) store body bodyType) :
      SourceTyped model privileges context store (.function parameterType body tag)
        (.fn {tag} parameterType bodyPrivileges bodyType)
  | application {privileges context store function argument functionTags parameterType
      bodyPrivileges resultType argumentType}
      (functionTyping : SourceTyped model (model.liftAdjust .appFunction privileges)
        context store function (.fn functionTags parameterType bodyPrivileges resultType))
      (argumentTyping : SourceTyped model (model.liftAdjust (.appArgument functionTags) privileges)
        context store argument argumentType)
      (subtyping : ConsistentSubtype
        (.fn functionTags parameterType bodyPrivileges resultType)
        (.fn functionTags argumentType privileges resultType))
      (checking : model.ConsistentCheck (.application functionTags argumentType.tags) privileges) :
      SourceTyped model privileges context store (.application function argument) resultType
  | ascription {privileges ascribed context store body type}
      (bodyTyping : SourceTyped model ascribed context store body type)
      (containment : CPrivSet.ConsistentLE ascribed privileges) :
      SourceTyped model privileges context store (.ascription body ascribed) type
  | reference {privileges context store body type tag}
      (bodyTyping : SourceTyped model (model.liftAdjust .reference privileges) context store body type)
      (checking : model.ConsistentCheck (.reference type.tags) privileges) :
      SourceTyped model privileges context store (.reference body tag) (.ref {tag} type)
  | dereference {privileges context store body referenceTags type}
      (bodyTyping : SourceTyped model (model.liftAdjust .dereference privileges)
        context store body (.ref referenceTags type))
      (checking : model.ConsistentCheck (.dereference referenceTags) privileges) :
      SourceTyped model privileges context store (.dereference body) type
  | assignment {privileges context store reference value referenceTags elementType valueType tag}
      (referenceTyping : SourceTyped model (model.liftAdjust .assignmentLeft privileges)
        context store reference (.ref referenceTags elementType))
      (valueTyping : SourceTyped model (model.liftAdjust (.assignmentRight referenceTags) privileges)
        context store value valueType)
      (checking : model.ConsistentCheck (.assignment referenceTags valueType.tags) privileges)
      (subtyping : ConsistentSubtype valueType elementType) :
      SourceTyped model privileges context store (.assignment reference value tag) (.unit {tag})

inductive InternalTyped (model : EffectModel Privilege Tag) :
    CPrivSet Privilege → Context Privilege Tag → StoreTyping Privilege Tag →
      InternalTerm Privilege Tag → Ty Privilege Tag → Prop where
  | unit {privileges context store tag} :
      InternalTyped model privileges context store (.unit tag) (.unit {tag})
  | variable {privileges context store index type}
      (lookup : context[index]? = some type) :
      InternalTyped model privileges context store (.variable index) type
  | location {privileges context store index tag type}
      (lookup : store[index]? = some type) :
      InternalTyped model privileges context store (.location index tag) (.ref {tag} type)
  | function {privileges bodyPrivileges context store parameterType body bodyType tag}
      (bodyTyping : InternalTyped model bodyPrivileges (parameterType :: context) store body bodyType) :
      InternalTyped model privileges context store (.function parameterType body tag)
        (.fn {tag} parameterType bodyPrivileges bodyType)
  | application {privileges context store function argument functionTags parameterType
      bodyPrivileges resultType argumentType}
      (functionTyping : InternalTyped model (model.liftAdjust .appFunction privileges)
        context store function (.fn functionTags parameterType bodyPrivileges resultType))
      (argumentTyping : InternalTyped model (model.liftAdjust (.appArgument functionTags) privileges)
        context store argument argumentType)
      (checking : model.StrictCheck (.application functionTags argumentType.tags) privileges)
      (subtyping : Subtype
        (.fn functionTags parameterType bodyPrivileges resultType)
        (.fn functionTags argumentType privileges resultType)) :
      InternalTyped model privileges context store (.application function argument) resultType
  | cast {privileges context store body sourceActual sourceClaimed target}
      (bodyTyping : InternalTyped model privileges context store body sourceActual)
      (sourceSubtyping : Subtype sourceActual sourceClaimed)
      (consistentSubtyping : ConsistentSubtype sourceClaimed target) :
      InternalTyped model privileges context store (.cast target sourceClaimed body) target
  | has {privileges context store required body type}
      (bodyTyping : InternalTyped model (privileges.addKnown required) context store body type) :
      InternalTyped model privileges context store (.has required body) type
  | error {privileges context store type} :
      InternalTyped model privileges context store .error type
  | restrict {privileges restricted context store body type}
      (bodyTyping : InternalTyped model restricted context store body type)
      (containment : CPrivSet.StaticLE restricted privileges) :
      InternalTyped model privileges context store (.restrict restricted body) type
  | reference {privileges context store body type tag}
      (bodyTyping : InternalTyped model (model.liftAdjust .reference privileges) context store body type)
      (checking : model.StrictCheck (.reference type.tags) privileges) :
      InternalTyped model privileges context store (.reference body tag) (.ref {tag} type)
  | dereference {privileges context store body referenceTags type}
      (bodyTyping : InternalTyped model (model.liftAdjust .dereference privileges)
        context store body (.ref referenceTags type))
      (checking : model.StrictCheck (.dereference referenceTags) privileges) :
      InternalTyped model privileges context store (.dereference body) type
  | assignment {privileges context store reference value referenceTags elementType valueType tag}
      (referenceTyping : InternalTyped model (model.liftAdjust .assignmentLeft privileges)
        context store reference (.ref referenceTags elementType))
      (valueTyping : InternalTyped model (model.liftAdjust (.assignmentRight referenceTags) privileges)
        context store value valueType)
      (checking : model.StrictCheck (.assignment referenceTags valueType.tags) privileges)
      (subtyping : Subtype valueType elementType) :
      InternalTyped model privileges context store (.assignment reference value tag) (.unit {tag})

namespace InternalTyped

variable {model : EffectModel Privilege Tag}

theorem weakenPrivileges {smaller larger : CPrivSet Privilege}
    {context : Context Privilege Tag} {store : StoreTyping Privilege Tag}
    {term : InternalTerm Privilege Tag} {type : Ty Privilege Tag}
    (typing : InternalTyped model smaller context store term type)
    (subset : CPrivSet.Subset smaller larger) :
    InternalTyped model larger context store term type := by
  induction typing generalizing larger with
  | unit => exact .unit
  | variable lookup => exact .variable lookup
  | location lookup => exact .location lookup
  | function bodyTyping _ => exact .function bodyTyping
  | application functionTyping argumentTyping checking subtyping functionIH argumentIH =>
      exact .application
        (functionIH (model.liftAdjust_mono subset))
        (argumentIH (model.liftAdjust_mono subset))
        (model.strict_mono subset checking)
        (subtyping.widenFunctionEffect subset)
  | cast bodyTyping sourceSubtyping consistentSubtyping bodyIH =>
      exact .cast (bodyIH subset) sourceSubtyping consistentSubtyping
  | has bodyTyping bodyIH =>
      exact .has (bodyIH (CPrivSet.addKnown_mono _ subset))
  | error => exact .error
  | restrict bodyTyping containment _ =>
      exact .restrict bodyTyping (CPrivSet.static_trans containment subset.1)
  | reference bodyTyping checking bodyIH =>
      exact .reference
        (bodyIH (model.liftAdjust_mono subset))
        (model.strict_mono subset checking)
  | dereference bodyTyping checking bodyIH =>
      exact .dereference
        (bodyIH (model.liftAdjust_mono subset))
        (model.strict_mono subset checking)
  | assignment referenceTyping valueTyping checking subtyping referenceIH valueIH =>
      exact .assignment
        (referenceIH (model.liftAdjust_mono subset))
        (valueIH (model.liftAdjust_mono subset))
        (model.strict_mono subset checking)
        subtyping

end InternalTyped

def missingPrivileges (required available : CPrivSet Privilege) : PrivSet Privilege :=
  required.known \ available.known

theorem missingPrivileges_staticLE (required available : CPrivSet Privilege) :
    CPrivSet.StaticLE required (available.addKnown (missingPrivileges required available)) := by
  intro privilege hRequired
  by_cases hAvailable : privilege ∈ available.known
  · simp [CPrivSet.addKnown, hAvailable]
  · simp [CPrivSet.addKnown, missingPrivileges, hRequired, hAvailable]

theorem insertHas_typed {model : EffectModel Privilege Tag}
    {privileges : CPrivSet Privilege} {context : Context Privilege Tag}
    {store : StoreTyping Privilege Tag} {required : PrivSet Privilege}
    {body : InternalTerm Privilege Tag} {type : Ty Privilege Tag}
    (typing : InternalTyped model (privileges.addKnown required) context store body type) :
    InternalTyped model privileges context store (insertHas required body) type := by
  by_cases hEmpty : required = ∅
  · subst required
    simpa [insertHas] using typing
  · simpa [insertHas, hEmpty] using InternalTyped.has typing

theorem insertCast_typed {model : EffectModel Privilege Tag}
    {privileges : CPrivSet Privilege} {context : Context Privilege Tag}
    {store : StoreTyping Privilege Tag} {body : InternalTerm Privilege Tag}
    {source target : Ty Privilege Tag}
    (typing : InternalTyped model privileges context store body source)
    (consistent : ConsistentSubtype source target) :
    ∃ actual,
      InternalTyped model privileges context store (insertCast target source body) actual ∧
      Subtype actual target := by
  classical
  by_cases hStatic : Subtype source target
  · exact ⟨source, by simpa [insertCast, hStatic] using typing, hStatic⟩
  · refine ⟨target, ?_, Subtype.refl target⟩
    simpa [insertCast, hStatic] using
      (InternalTyped.cast typing (Subtype.refl source) consistent)

theorem delta_adequate {model : EffectModel Privilege Tag}
    {context : CheckContext Tag} {privileges : CPrivSet Privilege}
    (checking : model.ConsistentCheck context privileges) :
    model.StrictCheck context (privileges.addKnown (model.delta context privileges)) :=
  model.delta_adequate checking

end GradualEffects
