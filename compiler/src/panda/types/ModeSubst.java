package panda.types;

import java.util.List;

import polyglot.util.InternalCompilerError;
import polyglot.types.Type;
import polyglot.types.ReferenceType;
import polyglot.types.ConstructorInstance;
import polyglot.types.FieldInstance;
import polyglot.types.MethodInstance;
import polyglot.types.MemberInstance; 

import polyglot.ext.jl5.types.RawClass;
import polyglot.ext.jl5.types.TypeVariable;
import polyglot.ext.jl5.types.JL5ArrayType;
import polyglot.ext.jl5.types.JL5PrimitiveType;

import polyglot.ext.jl5.types.JL5ParsedClassType;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ModeSubst {
  public Type baseType;
  public List<Type> modeTypeArgs;
  public Map<ModeTypeVariable, Type> modeTypeMap;

  public ModeSubst(Type bt, List<Type> mtArgs, Map<ModeTypeVariable, Type> mtMap) {
    this.baseType = bt;
    this.modeTypeArgs = mtArgs;
    this.modeTypeMap = mtMap;
  }

  // Property Methods
  public Type baseType() {
    return this.baseType;
  }

  public List<Type> modeTypeArgs() {
    return this.modeTypeArgs;
  }

  public Map<ModeTypeVariable, Type> modeTypeMap() {
    return this.modeTypeMap;
  }

  public Type substType(Type t) {
    if (t instanceof ModeSubstType) {
      return this.substModeSubstType((ModeSubstType)t);
    }

    if (t instanceof ModeTypeVariable) {
      return this.substModeTypeVariable((ModeTypeVariable)t);
    }

    return t;
  }

  public Type substModeSubstType(ModeSubstType type) {
    ModeSubstType subst = type.deepCopy();
    Type bt = this.substType(subst.baseType());
    Type mt = this.substType(subst.modeType());
    subst.baseType(bt);
    subst.modeType(mt);
    return subst;
  }

  public Type substModeTypeVariable(ModeTypeVariable mtVar) {
    return this.modeTypeMap().get(mtVar);
  }

  /*
  public ReferenceType substContainer(MemberInstance mi,
                                      Map<ModeTypeVariable, Type> mtMap) {
    return this.substType(mi.container(), mtMap).toReference();
  }
  */

  public <T extends FieldInstance> T substField(T fi) {
    //ReferenceType cont = this.substContainer(fi, mtMap);
    Type ft = this.substType(fi.type());
    T subst = (T) fi.copy();
    subst.setType(ft);
    subst.setContainer((ReferenceType) this.baseType());
    return subst;
  }

  public <T extends MethodInstance> T substMethod(T mi) {
    //ReferenceType cont = this.substContainer(mi, mtMap);
    Type retType = this.substType(mi.returnType());
    List<? extends Type> formalTypes = this.substTypeList(mi.formalTypes());
    List<? extends Type> throwTypes = this.substTypeList(mi.throwTypes());
    T subst = (T) mi.copy();
    subst.setReturnType(retType);
    subst.setFormalTypes(formalTypes);
    subst.setThrowTypes(throwTypes);
    subst.setContainer((ReferenceType) this.baseType());
    return subst;
  }

  public <T extends ConstructorInstance> T substConstructor(T ci) {
    //ReferenceType cont = this.substContainer(ci, mtMap);
    List<? extends Type> formalTypes = this.substTypeList(ci.formalTypes());
    List<? extends Type> throwTypes = this.substTypeList(ci.throwTypes());
    T subst = (T) ci.copy();
    subst.setFormalTypes(formalTypes);
    subst.setThrowTypes(throwTypes);
    subst.setContainer((ReferenceType) this.baseType());
    return subst;
  }

  public <T extends Type> List<T> substTypeList(List<T> list) {
    List<T> substList = new ArrayList<>();
    for (T t : list) {
      substList.add((T)this.substType(t));
    }
    return substList;
  }

  public <T extends FieldInstance> List<T> substFieldList(List<T> list) {
    List<T> substList = new ArrayList<>();
    for (T t : list) {
      substList.add(this.substField(t));
    }
    return substList;
  }

  public <T extends MethodInstance> List<T> substMethodList(List<T> list) {
    List<T> substList = new ArrayList<>();
    for (T t : list) {
      substList.add(this.substMethod(t));
    }
    return substList;
  } 

  public <T extends ConstructorInstance> List<T> substConstructorList(List<T> list) {
    List<T> substList = new ArrayList<>();
    for (T t : list) {
      substList.add(this.substConstructor(t));
    }
    return substList;
  }


  
} 