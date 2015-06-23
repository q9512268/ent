package panda.ast;

import panda.translate.*;
import panda.types.*;

import polyglot.ast.*;
import polyglot.types.*;
import polyglot.util.*;
import polyglot.visit.*;
import polyglot.translate.*;
import polyglot.qq.*;

import polyglot.ext.jl5.ast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PandaClassDeclExt extends PandaExt {

  protected List<ModeParamTypeNode> modeParams = Collections.emptyList();

  // Property Methods
  public List<ModeParamTypeNode> modeParams() {
    return this.modeParams;
  }

  public Node modeParams(List<ModeParamTypeNode> modeParams) {
    return this.modeParams(this.node(), modeParams);
  }

  public <N extends Node> N modeParams(N n, List<ModeParamTypeNode> modeParams) {
    PandaClassDeclExt ext = (PandaClassDeclExt) PandaExt.ext(n);
    if (CollectionUtil.equals(ext.modeParams,modeParams)) return n;
    if (this.node() == n) {
      n = Copy.Util.copy(n);
      ext = (PandaClassDeclExt) PandaExt.ext(n);
    }
    ext.modeParams = ListUtil.copy(modeParams, true); 
    return n;
  }

  // Node Methods
  protected <N extends Node> N reconstruct(N n, List<ModeParamTypeNode> modeParams) {
    n = this.modeParams(n, modeParams);
    return n;
  }

  @Override
  public Node visitChildren(NodeVisitor v) {
    Node n = superLang().visitChildren(this.node(), v);
    List<ModeParamTypeNode> modeParams = visitList(this.modeParams(), v);
    return this.reconstruct(n, modeParams);
  }

  @Override
  public Context enterChildScope(Node child, Context c) {
    PandaContext ctx = (PandaContext) superLang().enterChildScope(this.node(), child, c);
    for (ModeParamTypeNode t : this.modeParams()) {
      ctx.addModeTypeVariable((ModeTypeVariable) t.type());
    }
    return ctx;
  }

  @Override
  public Node buildTypes(TypeBuilder tb) throws SemanticException {
    ClassDecl decl = (ClassDecl) superLang().buildTypes(this.node(), tb);

    PandaTypeSystem ts = (PandaTypeSystem) tb.typeSystem();
    PandaParsedClassType ct = (PandaParsedClassType) decl.type();

    if (this.modeParams() != null && !this.modeParams().isEmpty()) {
      List<ModeTypeVariable> mtVars = 
        new ArrayList<ModeTypeVariable>(this.modeParams().size());
      Set<String> mtVarCheck = new HashSet<>();

      for (ModeParamTypeNode n : this.modeParams()) {
        // Check and catch duplicate error as early as possible
        if (mtVarCheck.contains(n.name())) {
          throw new SemanticException("Duplicate mode type variable declaration.",
                                      n.position());
        }
        mtVarCheck.add(n.name());

        ModeTypeVariable mtVar = (ModeTypeVariable) n.type();
        mtVar.declaringClass(ct);
        mtVars.add(mtVar);
      }
      ct.modeTypeVars(mtVars);
    } 

    return decl;
  } 

  @Override
  public Node extRewrite(ExtensionRewriter rw) throws SemanticException {
    PandaRewriter prw = (PandaRewriter) rw;
    NodeFactory nf = (NodeFactory) prw.nodeFactory();
    QQ qq = prw.qq();

    if (!prw.translatePanda()) {
      return super.extRewrite(rw);
    }

    ClassDecl decl = (ClassDecl) this.node();
    PandaParsedClassType ct = (PandaParsedClassType) decl.type();
    ClassDecl n = (ClassDecl) super.extRewrite(rw);

    // 1. Generate the PANDA_Attributable interface
    if (ct.hasAttribute()) {
      List<TypeNode> interfaces = new ArrayList<>(decl.interfaces());
      interfaces.add(qq.parseType("PANDA_Attributable"));
      n = n.interfaces(interfaces);
    }

    // 2. Generate a builtin PANDA_copy method
    if (ct.hasAttribute() && !ct.hasCopy()) {
      List<Stmt> stmts = new ArrayList<>();
      // 2.1. Create a new expression for a shallow copy
      stmts.add(
        qq.parseStmt(
          "%T PANDA_ld = new %T();", 
          qq.parseType(decl.name()),
          qq.parseType(decl.name())
          )
        );

      // 2.2. Copy each member of the class manually
      for (ClassMember m : decl.body().members()) {
        if (!(m instanceof FieldDecl)) {
          continue;
        }
        FieldDecl fd = (FieldDecl) m;
        if (fd.flags().isStatic()) {
          continue;
        }
        stmts.add(qq.parseStmt("PANDA_ld.%s = this.%s;", fd.name(), fd.name()));
      }

      // 2.3. Simply return the shallow copy
      stmts.add(qq.parseStmt("return PANDA_ld;"));

      ClassMember md = qq.parseMember("public PANDA_Attributable PANDA_copy() { %LS }", stmts);

      // Handle the immutable part of polyglot
      ClassBody body = n.body();
      List<ClassMember> members = new ArrayList<>(body.members());
      members.add(md);
      body = body.members(members);
      n = n.body(body);
    } 

    return n;
  }

}
