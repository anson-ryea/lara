import Mathlib

/-!
# Static core for *A Theory of Gradual Effect Systems*

This module contains only the material needed for the translation-preserves-typing
result.  It deliberately excludes reduction, runtime stores, progress and
preservation.
-/

namespace GradualEffects

abbrev PrivSet (Privilege : Type) := Finset Privilege
abbrev TagSet (Tag : Type) := Finset Tag

structure CPrivSet (Privilege : Type) [DecidableEq Privilege] where
  known : PrivSet Privilege
  unknown : Bool
deriving DecidableEq, Repr

namespace CPrivSet

variable {Privilege : Type} [DecidableEq Privilege]

def Subset (left right : CPrivSet Privilege) : Prop :=
  left.known ⊆ right.known ∧ (left.unknown = true → right.unknown = true)

def StaticLE (left right : CPrivSet Privilege) : Prop :=
  left.known ⊆ right.known

def addKnown (set : PrivSet Privilege) (privileges : CPrivSet Privilege) :
    CPrivSet Privilege :=
  ⟨set ∪ privileges.known, privileges.unknown⟩

@[simp] theorem addKnown_known (set : PrivSet Privilege) (privileges : CPrivSet Privilege) :
    (addKnown set privileges).known = set ∪ privileges.known := rfl

@[simp] theorem addKnown_unknown (set : PrivSet Privilege) (privileges : CPrivSet Privilege) :
    (addKnown set privileges).unknown = privileges.unknown := rfl

@[simp] theorem addKnown_empty (privileges : CPrivSet Privilege) :
    addKnown ∅ privileges = privileges := by
  cases privileges
  simp [addKnown]

theorem subset_refl (privileges : CPrivSet Privilege) : Subset privileges privileges := by
  constructor <;> simp

theorem subset_trans {first second third : CPrivSet Privilege}
    (h₁ : Subset first second) (h₂ : Subset second third) : Subset first third := by
  constructor
  · exact h₁.1.trans h₂.1
  · exact fun h => h₂.2 (h₁.2 h)

theorem subset_addKnown (set : PrivSet Privilege) (privileges : CPrivSet Privilege) :
    Subset privileges (addKnown set privileges) := by
  constructor
  · intro privilege hPrivilege
    simp [addKnown, hPrivilege]
  · simp [addKnown]

theorem addKnown_mono {first second : CPrivSet Privilege}
    (set : PrivSet Privilege) (h : Subset first second) :
    Subset (addKnown set first) (addKnown set second) := by
  constructor
  · intro privilege hPrivilege
    simp only [addKnown_known, Finset.mem_union] at hPrivilege ⊢
    exact hPrivilege.elim Or.inl (fun hp => Or.inr (h.1 hp))
  · simpa [addKnown] using h.2

theorem static_trans {first second third : CPrivSet Privilege}
    (h₁ : StaticLE first second) (h₂ : StaticLE second third) : StaticLE first third :=
  h₁.trans h₂

theorem subset_static {first second : CPrivSet Privilege}
    (h : Subset first second) : StaticLE first second := h.1

variable [Fintype Privilege]

def concretize (privileges : CPrivSet Privilege) : Finset (PrivSet Privilege) :=
  if privileges.unknown then
    Finset.univ.powerset.filter fun concrete => privileges.known ⊆ concrete
  else
    {privileges.known}

@[simp] theorem known_mem_concretize (privileges : CPrivSet Privilege) :
    privileges.known ∈ concretize privileges := by
  cases h : privileges.unknown <;> simp [concretize, h]

def ConsistentLE (left right : CPrivSet Privilege) : Prop :=
  ∃ concreteLeft ∈ concretize left,
    ∃ concreteRight ∈ concretize right, concreteLeft ⊆ concreteRight

theorem consistent_refl (privileges : CPrivSet Privilege) :
    ConsistentLE privileges privileges := by
  exact ⟨privileges.known, known_mem_concretize privileges,
    privileges.known, known_mem_concretize privileges, Finset.Subset.rfl⟩

end CPrivSet

inductive AdjustContext (Tag : Type) where
  | appFunction
  | appArgument (functionTags : TagSet Tag)
  | reference
  | dereference
  | assignmentLeft
  | assignmentRight (referenceTags : TagSet Tag)
deriving DecidableEq, Repr

inductive CheckContext (Tag : Type) where
  | application (functionTags argumentTags : TagSet Tag)
  | reference (valueTags : TagSet Tag)
  | dereference (referenceTags : TagSet Tag)
  | assignment (referenceTags valueTags : TagSet Tag)
deriving DecidableEq, Repr

variable {Privilege Tag : Type}
variable [DecidableEq Privilege] [Fintype Privilege] [DecidableEq Tag]

def consistentCheck
    (check : CheckContext Tag → PrivSet Privilege → Bool)
    (context : CheckContext Tag) (privileges : CPrivSet Privilege) : Prop :=
  ∃ concrete ∈ privileges.concretize, check context concrete = true

def strictCheck
    (check : CheckContext Tag → PrivSet Privilege → Bool)
    (context : CheckContext Tag) (privileges : CPrivSet Privilege) : Prop :=
  check context privileges.known = true

def minimalSets (sets : Finset (PrivSet Privilege)) : Finset (PrivSet Privilege) :=
  sets.filter fun candidate =>
    ∀ other ∈ sets, ¬(other ⊂ candidate)

def unionSets (sets : Finset (PrivSet Privilege)) : PrivSet Privilege :=
  sets.biUnion id

def deltaFor
    (check : CheckContext Tag → PrivSet Privilege → Bool)
    (context : CheckContext Tag) (privileges : CPrivSet Privilege) : PrivSet Privilege :=
  let satisfying := privileges.concretize.filter fun concrete => check context concrete
  unionSets (minimalSets satisfying) \ privileges.known

/-!
`EffectModel` records exactly the generic laws consumed by the static translation
proof.  `delta_adequate` is the finite minimal-check lemma described after
Definition 10; keeping it as a field makes every concrete effect discipline prove
the obligation explicitly.
-/
structure EffectModel (Privilege Tag : Type)
    [DecidableEq Privilege] [Fintype Privilege] [DecidableEq Tag] where
  check : CheckContext Tag → PrivSet Privilege → Bool
  liftAdjust : AdjustContext Tag → CPrivSet Privilege → CPrivSet Privilege
  check_mono : ∀ {context smaller larger},
    smaller ⊆ larger → check context smaller = true → check context larger = true
  liftAdjust_mono : ∀ {context smaller larger},
    CPrivSet.Subset smaller larger →
      CPrivSet.Subset (liftAdjust context smaller) (liftAdjust context larger)
  delta_adequate : ∀ {context privileges},
    consistentCheck check context privileges →
      strictCheck check context (privileges.addKnown (deltaFor check context privileges))

namespace EffectModel

variable (model : EffectModel Privilege Tag)

abbrev ConsistentCheck := consistentCheck model.check
abbrev StrictCheck := strictCheck model.check
abbrev delta := deltaFor model.check

theorem strict_mono {context : CheckContext Tag} {smaller larger : CPrivSet Privilege}
    (hSubset : CPrivSet.Subset smaller larger)
    (hCheck : model.StrictCheck context smaller) : model.StrictCheck context larger := by
  exact model.check_mono hSubset.1 hCheck

end EffectModel

inductive Ty (Privilege Tag : Type) [DecidableEq Privilege] [DecidableEq Tag] where
  | unit (tags : TagSet Tag)
  | ref (tags : TagSet Tag) (element : Ty Privilege Tag)
  | fn (tags : TagSet Tag) (domain : Ty Privilege Tag)
      (privileges : CPrivSet Privilege) (codomain : Ty Privilege Tag)
deriving DecidableEq, Repr

namespace Ty

def tags : Ty Privilege Tag → TagSet Tag
  | .unit tags | .ref tags _ | .fn tags _ _ _ => tags

end Ty

inductive SourceTerm (Privilege Tag : Type) [DecidableEq Privilege] where
  | variable (index : Nat)
  | unit (tag : Tag)
  | location (index : Nat) (tag : Tag)
  | function (parameterType : Ty Privilege Tag) (body : SourceTerm Privilege Tag) (tag : Tag)
  | application (function argument : SourceTerm Privilege Tag)
  | ascription (body : SourceTerm Privilege Tag) (privileges : CPrivSet Privilege)
  | reference (body : SourceTerm Privilege Tag) (tag : Tag)
  | dereference (body : SourceTerm Privilege Tag)
  | assignment (reference value : SourceTerm Privilege Tag) (tag : Tag)
deriving DecidableEq, Repr

inductive InternalTerm (Privilege Tag : Type) [DecidableEq Privilege] where
  | variable (index : Nat)
  | unit (tag : Tag)
  | location (index : Nat) (tag : Tag)
  | function (parameterType : Ty Privilege Tag) (body : InternalTerm Privilege Tag) (tag : Tag)
  | application (function argument : InternalTerm Privilege Tag)
  | reference (body : InternalTerm Privilege Tag) (tag : Tag)
  | dereference (body : InternalTerm Privilege Tag)
  | assignment (reference value : InternalTerm Privilege Tag) (tag : Tag)
  | error
  | cast (target source : Ty Privilege Tag) (body : InternalTerm Privilege Tag)
  | has (required : PrivSet Privilege) (body : InternalTerm Privilege Tag)
  | restrict (privileges : CPrivSet Privilege) (body : InternalTerm Privilege Tag)
deriving DecidableEq, Repr

abbrev Context (Privilege Tag : Type) [DecidableEq Privilege] [DecidableEq Tag] :=
  List (Ty Privilege Tag)

abbrev StoreTyping (Privilege Tag : Type) [DecidableEq Privilege] [DecidableEq Tag] :=
  List (Ty Privilege Tag)

inductive Subtype : Ty Privilege Tag → Ty Privilege Tag → Prop where
  | unit {sourceTags targetTags} :
      sourceTags ⊆ targetTags →
      Subtype (.unit sourceTags) (.unit targetTags)
  | ref {sourceTags targetTags sourceElement targetElement} :
      sourceTags ⊆ targetTags → Subtype sourceElement targetElement →
      Subtype (.ref sourceTags sourceElement) (.ref targetTags targetElement)
  | fn {sourceTags targetTags sourceDomain targetDomain sourcePrivileges targetPrivileges
      sourceCodomain targetCodomain} :
      sourceTags ⊆ targetTags →
      Subtype targetDomain sourceDomain →
      CPrivSet.Subset sourcePrivileges targetPrivileges →
      Subtype sourceCodomain targetCodomain →
      Subtype (.fn sourceTags sourceDomain sourcePrivileges sourceCodomain)
        (.fn targetTags targetDomain targetPrivileges targetCodomain)

namespace Subtype

theorem refl (type : Ty Privilege Tag) : Subtype type type := by
  induction type with
  | unit tags => exact .unit Finset.Subset.rfl
  | ref tags element ih => exact .ref Finset.Subset.rfl ih
  | fn tags domain privileges codomain domainIH codomainIH =>
      exact .fn Finset.Subset.rfl domainIH (CPrivSet.subset_refl privileges) codomainIH

theorem widenFunctionEffect {source domain codomain : Ty Privilege Tag}
    {tags : TagSet Tag} {smaller larger : CPrivSet Privilege}
    (hSubtype : Subtype source (.fn tags domain smaller codomain))
    (hSubset : CPrivSet.Subset smaller larger) :
    Subtype source (.fn tags domain larger codomain) := by
  cases hSubtype with
  | fn hTags hDomain hPrivileges hCodomain =>
      exact .fn hTags hDomain (CPrivSet.subset_trans hPrivileges hSubset) hCodomain

end Subtype

inductive ConsistentSubtype : Ty Privilege Tag → Ty Privilege Tag → Prop where
  | unit {sourceTags targetTags} :
      sourceTags ⊆ targetTags →
      ConsistentSubtype (.unit sourceTags) (.unit targetTags)
  | ref {sourceTags targetTags sourceElement targetElement} :
      sourceTags ⊆ targetTags → ConsistentSubtype sourceElement targetElement →
      ConsistentSubtype (.ref sourceTags sourceElement) (.ref targetTags targetElement)
  | fn {sourceTags targetTags sourceDomain targetDomain sourcePrivileges targetPrivileges
      sourceCodomain targetCodomain} :
      sourceTags ⊆ targetTags →
      ConsistentSubtype targetDomain sourceDomain →
      CPrivSet.ConsistentLE sourcePrivileges targetPrivileges →
      ConsistentSubtype sourceCodomain targetCodomain →
      ConsistentSubtype (.fn sourceTags sourceDomain sourcePrivileges sourceCodomain)
        (.fn targetTags targetDomain targetPrivileges targetCodomain)

namespace ConsistentSubtype

theorem refl (type : Ty Privilege Tag) : ConsistentSubtype type type := by
  induction type with
  | unit tags => exact .unit Finset.Subset.rfl
  | ref tags element ih => exact .ref Finset.Subset.rfl ih
  | fn tags domain privileges codomain domainIH codomainIH =>
      exact .fn Finset.Subset.rfl domainIH (CPrivSet.consistent_refl privileges) codomainIH

end ConsistentSubtype

def insertHas (required : PrivSet Privilege) (body : InternalTerm Privilege Tag) :
    InternalTerm Privilege Tag :=
  if required = ∅ then body else .has required body

noncomputable def insertCast (target source : Ty Privilege Tag)
    (body : InternalTerm Privilege Tag) : InternalTerm Privilege Tag :=
  if Subtype source target then body else .cast target source body

end GradualEffects
