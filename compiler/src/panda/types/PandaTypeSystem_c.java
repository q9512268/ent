package panda.types;

import panda.ast.PandaLang_c;

import polyglot.ast.Id;
import polyglot.frontend.Source;
import polyglot.types.*;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;

import polyglot.ext.param.types.Subst;
import polyglot.ext.param.types.SubstType;

import polyglot.ext.jl5.types.RawClass;
import polyglot.ext.jl5.types.TypeVariable;
import polyglot.ext.jl5.types.JL5ArrayType;
import polyglot.ext.jl5.types.JL5SubstClassType;
import polyglot.ext.jl5.types.JL5ConstructorInstance;
import polyglot.ext.jl5.types.JL5MethodInstance;
import polyglot.ext.jl5.types.JL5ProcedureInstance;
import polyglot.ext.jl5.types.JL5PrimitiveType;
import polyglot.ext.jl5.types.JL5Subst;
import polyglot.ext.jl5.types.JL5SubstType;

import polyglot.ext.jl5.types.JL5ParsedClassType;
import polyglot.ext.jl7.types.JL7TypeSystem_c;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class PandaTypeSystem_c extends JL7TypeSystem_c implements PandaTypeSystem { 
  private Map<String, ModeType> createdModeTypes = new HashMap<>();

  private ModeType WildcardModeType;
  private ModeType DynamicModeType;

  private final String WILDCARD_MODE_TYPE_ID = "*";
  private final String DYNAMIC_MODE_TYPE_ID = "?";

  private ModeSubstEngine substEngine = new ModeSubstEngine();
  
  public PandaTypeSystem_c() {
    // Setup both the bottom and dynamic mode type instances
    this.WildcardModeType = this.createModeType(this.WILDCARD_MODE_TYPE_ID);
    this.DynamicModeType = this.createModeType(this.DYNAMIC_MODE_TYPE_ID);
  } 

  // Property Methods
  public Map<String, ModeType> createdModeTypes() {
    return this.createdModeTypes;
  } 

  public ModeType WildcardModeType() {
    return this.WildcardModeType;
  }

  public ModeType DynamicModeType() {
    return this.DynamicModeType;
  }

  private ModeSubstEngine substEngine() {
    return this.substEngine;
  }

  // Factory Methods / Previous TypeSystem Methods
  @Override
  public JL5ConstructorInstance constructorInstance(Position pos,
          ClassType container, Flags flags, List<? extends Type> argTypes,
          List<? extends Type> excTypes, List<TypeVariable> typeParams) {
      assert_(container);
      assert_(argTypes);
      assert_(excTypes);
      assert_(typeParams);
      return new PandaConstructorInstance_c(this,
                                            pos,
                                            container,
                                            flags,
                                            argTypes,
                                            excTypes,
                                            typeParams,
                                            null);
    }


  @Override
  public ParsedClassType createClassType(LazyClassInitializer init, 
                                         Source fromSource) {
    return new PandaParsedClassType_c(this, init, fromSource);
  }

  @Override
  public Context createContext() {
    return new PandaContext_c(PandaLang_c.instance, this);
  }

  private boolean inferModeTypeArg(List<ModeTypeVariable> mtVars,
                                   Type baset, 
                                   ModeSubstType actt,
                                   Map<ModeTypeVariable, Mode> mtMap) {
    if (!(baset instanceof ModeSubstType)) {
      return true;
    }

    // TODO : This is an O(n) lookup, keep a hash to avoid
    ModeSubstType st = (ModeSubstType) baset;
    if (st.modeType() instanceof ModeTypeVariable &&
        mtVars.contains(st.modeType())) {

      // This is a mode type var that is part of our inference, so infer it
      ModeTypeVariable mtVar = (ModeTypeVariable) st.modeType();
      Mode inft = actt.modeType();
      if (mtMap.containsKey(mtVar)) {
        if (!mtMap.get(mtVar).typeEqualsImpl(inft)) {
          return false;
        }
      } else {
        mtMap.put(mtVar, inft);
      } 
    }

    // Just like the opposite (in ModeSubst) we need to dip down in the subst
    // over the generic type
    if (st.baseType() instanceof SubstType && 
        actt.baseType() instanceof SubstType) {
      Type t1 = ((SubstType) st.baseType()).base();
      Type t2 = ((SubstType) actt.baseType()).base();

      if (!this.isCastValid(t1, t2)) {
        // Forget the inference, the type system will flag this as invalid
        return false;
      }

      Map<TypeVariable,ReferenceType> stSubsts = 
        ((SubstType) st.baseType()).subst().substitutions();
      Map<TypeVariable,ReferenceType> acttSubsts = 
        ((SubstType) actt.baseType()).subst().substitutions();

      JL5SubstClassType t3 = 
        this.findGenericSupertype((JL5ParsedClassType) t1, (ReferenceType) t2);
      Map<TypeVariable,TypeVariable> tvMap = new HashMap<>();
      if (t3 != null) {
        for (Map.Entry<TypeVariable,ReferenceType> e : t3.subst().substitutions().entrySet()) {
          tvMap.put(e.getKey(), (TypeVariable) e.getValue());
        }
      } else {
        for (Map.Entry<TypeVariable,ReferenceType> e : stSubsts.entrySet()) {
          tvMap.put(e.getKey(), e.getKey());
        }
      }

      for (Map.Entry<TypeVariable,TypeVariable> e : tvMap.entrySet()) {

        if (!acttSubsts.containsKey(e.getValue())) {
          // We cannot infer, the subst are not equal
          return false;
        }

        if (!this.inferModeTypeArg(mtVars,
                                   stSubsts.get(e.getKey()),
                                   (ModeSubstType) acttSubsts.get(e.getValue()),
                                   mtMap)) {
          return false;
        }
      }
    } 

    return true;
  }

  private ModeSubst inferModeTypeArgs(PandaProcedureInstance pi,
                                      List <? extends Type> argTypes,
                                      Type expectedReturnType) {

    // Infer the mode type variable first, error if not possible
    Map<ModeTypeVariable,Mode> mtMap = new HashMap<>();
    for (int i = 0; i < pi.formalTypes().size(); ++i) {
      if (!this.inferModeTypeArg(pi.modeTypeVars(),
                                 pi.formalTypes().get(i),
                                 (ModeSubstType) argTypes.get(i),
                                 mtMap)) {
        return null;
      }
    }

    // Check return type as well
    if (expectedReturnType != null && 
        !this.inferModeTypeArg(pi.modeTypeVars(),
                               ((PandaMethodInstance) pi).returnType(),
                               (ModeSubstType) expectedReturnType,
                               mtMap)) {
      return null;
    }


    ModeSubstClassType sct = (ModeSubstClassType) pi.container();
    ModeSubst subst = sct.modeSubst().deepCopy();
    subst.modeTypeMap().putAll(mtMap);

    // TODO : Not sure if this is right
    if (mtMap.isEmpty()) {
      return subst;
    }

    // We have our inferred map, now check that constraints over the procedures
    // mode type variables are satisfied

    mtMap = subst.modeTypeMap();
    for (ModeTypeVariable mtv : pi.modeTypeVars()) {
      Mode sm = mtMap.get(mtv);

      // Check that all bounds are satisfied
      for (Mode m : mtv.bounds()) {
        if (m instanceof ModeTypeVariable) {
          m = mtMap.get(m);
        }

        if (!sm.isSubtypeOfMode(m)) {
          System.out.println("Attempting to subst with " + sm + " failing on contraint " + m);
          return null;
        }
      }
    }

    return subst; 
  }

  @Override
  public JL5ProcedureInstance callValid(JL5ProcedureInstance mi,
                                        List<? extends Type> argTypes,
                                        List<? extends ReferenceType> actualTypeArgs) {
    if (!(mi.container() instanceof ModeSubstType)) {
      // TODO : Just kick up to parent for now
      return super.callValid(mi, argTypes, actualTypeArgs);
    } 

    // Check from JL5TypeSystem::methodCallValid
    // Repeat it here to avoid some of the calls that could crash
    // our check
    if (argTypes.size() != mi.formalTypes().size()) {
      if (!(mi.isVariableArity() && argTypes.size() >= mi.formalTypes()
              .size() - 1)) {
        return null;
      }
    }

    ModeSubst subst = this.inferModeTypeArgs((PandaProcedureInstance) mi, argTypes, null);
    if (subst == null) {
      // No matter what we should be able to create a valid subst,
      // null indicates error
      return null;
    }

    PandaProcedureInstance smi = (PandaProcedureInstance) subst.substProcedure(mi);

    // Let's perform a subst over modes, a hack for now, just prototyping
    // Can do a subst over the mi container type with a new type map
    return super.callValid(smi, argTypes, actualTypeArgs);
  }

  @Override
  public JL5MethodInstance methodCallValid(JL5MethodInstance mi, 
                                           String name,
                                           List<? extends Type> argTypes,
                                           List<? extends ReferenceType> actualTypeArgs,
                                           Type expectedReturnType) {
    if (!(mi.container() instanceof ModeSubstType)) {
      // TODO : Just kick up to parent for now
      return super.methodCallValid(mi, name, argTypes, actualTypeArgs, expectedReturnType);
    }

    // Check from JL5TypeSystem::methodCallValid
    // Repeat it here to avoid some of the calls that could crash
    // our check
    if (argTypes.size() != mi.formalTypes().size()) {
      if (!(mi.isVariableArity() && argTypes.size() >= mi.formalTypes()
              .size() - 1)) {
        return null;
      }
    } 

    ModeSubst subst = this.inferModeTypeArgs((PandaProcedureInstance) mi, argTypes, expectedReturnType);
    if (subst == null) {
      // No matter what we should be able to create a valid subst,
      // null indicates error
      return null;
    }

    PandaMethodInstance smi = (PandaMethodInstance) subst.substMethod(mi);

    // Let's perform a subst over modes, a hack for now, just prototyping
    // Can do a subst over the mi container type with a new type map
    return super.methodCallValid(smi, name, argTypes, actualTypeArgs, expectedReturnType);
  }


  @Override
  public JL5MethodInstance methodInstance(Position pos, 
                                          ReferenceType container, 
                                          Flags flags, 
                                          Type returnType, 
                                          String name, 
                                          List<? extends Type> argTypes, 
                                          List<? extends Type> excTypes, 
                                          List<TypeVariable> typeParams) {
    return new PandaMethodInstance_c(this,
                                     pos,
                                     container,
                                     flags,
                                     returnType,
                                     name,
                                     argTypes,
                                     excTypes,
                                     typeParams,
                                     null);
  } 

  @Override
  public RawClass rawClass(JL5ParsedClassType base, Position pos) {
    if (!canBeRaw(base)) {
      throw new InternalCompilerError("Can only create a raw class with a parameterized class");
    }

    // TODO : I do not like putting the mode subst logic here for raw class types
    // but they are created not from walking the AST with a TypeNode but instead
    // through the type systems after a JL5SubstClassType has been created
    if (base instanceof ModeSubstParsedClassType) {
      ModeSubstParsedClassType st = (ModeSubstParsedClassType) base;
      PandaRawClass_c rc = 
        new PandaRawClass_c((PandaParsedClassType) st.baseType(), pos);
      return (RawClass) this.createModeSubst(rc, new ArrayList<Mode>(st.modeTypeArgs()));
    } else {
      return new PandaRawClass_c(base, pos);
    }
  }

  @Override 
  protected Subst<TypeVariable, ReferenceType> substImpl(Map<TypeVariable, ? extends ReferenceType> substMap) {
    return new PandaSubst_c(this, substMap);
  }

  // Panda TypeSystem Methods

  public AttributeInstance createAttributeInstance(Position pos, 
      ReferenceType container, 
      Flags flags) {
    return new AttributeInstance_c(this, pos, container, flags);
  }

  public ModeType createModeType(String mode) {
    if (this.createdModeTypes().containsKey(mode)) {
      return this.createdModeTypes().get(mode);
    }
    ModeType modeType = new ModeType_c(this, mode);
    this.createdModeTypes().put(mode, modeType);
    return modeType;
  } 

  public ModeOrderingInstance createModeOrderingInstance() {
    return new ModeOrderingInstance_c(this);
  }

  public ModeTypeVariable createModeTypeVariable(Position pos, String name) {
    return new ModeTypeVariable_c(this, pos, name);
  }

  public ModeValueType createModeValueType(Mode mode) {
    return new ModeValueType_c(this, mode);
  }

  // TypeSystem Methods
  public boolean isSubtypeModes(Mode lb, Mode ub) {
    return lb.isSubtypeOfModeImpl(ub);
  }

  public boolean isSupertypeModes(Mode lb, Mode ub) {
    return lb.isSupertypeOfModeImpl(ub);
  }

  public Type createModeSubst(Type bt, List<Mode> modeTypes) {
    return this.substEngine().createModeSubst(bt, modeTypes);
  }

  public ClassType wrapperClassOfModeSubstPrimitive(ModeSubstPrimitiveType t) {
    // We need to inject a boxed primitive type with a mode
    ClassType ct = this.wrapperClassOfPrimitive(t);
    return (ClassType) this.createModeSubst(ct, t.modeTypeArgs());
  }

}
