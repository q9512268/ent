package panda.types;

import polyglot.types.ClassType;
import polyglot.types.Type;
import polyglot.types.ReferenceType;
import polyglot.util.InternalCompilerError;

import polyglot.ext.param.types.ParamTypeSystem;

import polyglot.ext.jl5.types.JL5ClassType;
import polyglot.ext.jl5.types.JL5Subst_c;
import polyglot.ext.jl5.types.TypeVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PandaSubst_c extends JL5Subst_c implements PandaSubst {

  public PandaSubst_c(ParamTypeSystem<TypeVariable, ReferenceType> ts, 
                      Map<TypeVariable, ? extends ReferenceType> subst) {
    super(ts, subst);
  }

  // FIXME: (From Panda) Probably a bad equals issue with subst types. Bug
  // due to equals hack that allows ModeSubstType and Type to be equals if
  // bases are equal.

  // NOTE : This is an ugly hack to get mode types onto JL5SubstClassTypes,
  // but there is no nice solution without type ext objects.
  @Override
  protected ClassType substClassTypeImpl(ClassType t) {
    PandaTypeSystem ts = (PandaTypeSystem) this.ts;

    // Don't bother trying to substitute into a non-JL5 class.
    if (!(t instanceof JL5ClassType)) {
      return t;
    }

    if (t instanceof ModeSubstClassType) {

      // NOTE: This is here just until I am confident this is the right way to handle
      ModeSubstClassType st = (ModeSubstClassType) t;
      return (ClassType)
        ts.createModeSubst(
          ts.subst(st.baseType(), this.substitutions()),
          new ArrayList<>(st.modeTypeArgs())
          );
    }

    if (t instanceof PandaRawClass) {
      // don't substitute raw classes
      return t;
    }
    if (t instanceof PandaSubstClassType) {
      // this case should be impossible
      throw new InternalCompilerError("Should have no JL5SubstClassTypes");
    }

    if (t instanceof PandaParsedClassType) {
      PandaParsedClassType pct = (PandaParsedClassType) t;
      List<TypeVariable> typeVars =
              ts.classAndEnclosingTypeVariables(pct);
      // are the type variables of pct actually relevant to this subst? If not, then return the pct.
      boolean typeVarsRelevant = false;
      for (TypeVariable tv : typeVars) {
        if (this.substitutions().containsKey(tv)) {
          typeVarsRelevant = true;
          break;
        }
      }
      if (!typeVarsRelevant) {
          // no parameters to be instantiated!
          return pct;
      }

      // NOTE : This is the change
      return new PandaSubstClassType_c(ts, t.position(), pct, this);
    }

    throw new InternalCompilerError("Don't know how to handle class type "
            + t.getClass());
  }



}
