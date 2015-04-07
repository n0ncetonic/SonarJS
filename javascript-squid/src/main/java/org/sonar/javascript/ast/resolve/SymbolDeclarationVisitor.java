/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011 SonarSource and Eriks Nukis
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.javascript.ast.resolve;

import org.sonar.javascript.ast.visitors.BaseTreeVisitor;
import org.sonar.javascript.model.implementations.declaration.MethodDeclarationTreeImpl;
import org.sonar.javascript.model.implementations.declaration.ParameterListTreeImpl;
import org.sonar.javascript.model.implementations.expression.ArrowFunctionTreeImpl;
import org.sonar.javascript.model.implementations.statement.CatchBlockTreeImpl;
import org.sonar.javascript.model.implementations.statement.VariableDeclarationTreeImpl;
import org.sonar.javascript.model.interfaces.Tree;
import org.sonar.javascript.model.interfaces.declaration.FunctionDeclarationTree;
import org.sonar.javascript.model.interfaces.declaration.MethodDeclarationTree;
import org.sonar.javascript.model.interfaces.declaration.ScriptTree;
import org.sonar.javascript.model.interfaces.expression.ArrowFunctionTree;
import org.sonar.javascript.model.interfaces.expression.ClassTree;
import org.sonar.javascript.model.interfaces.expression.FunctionExpressionTree;
import org.sonar.javascript.model.interfaces.expression.IdentifierTree;
import org.sonar.javascript.model.interfaces.statement.CatchBlockTree;
import org.sonar.javascript.model.interfaces.statement.VariableDeclarationTree;

import java.util.List;

/**
 * This visitor records all symbol explicitly declared through a declared statement.
 * i.e: Method Declaration,
 */
public class SymbolDeclarationVisitor extends BaseTreeVisitor {

  private SymbolModel symbolModel;
  private Scope currentScope;

  public SymbolDeclarationVisitor(SymbolModel symbolModel) {
    this.symbolModel = symbolModel;
    this.currentScope = null;
  }

  @Override
  public void visitScript(ScriptTree tree) {
    newScope(tree);
    super.visitScript(tree);
    leaveScope();
  }

  // FIXME: class are not properly handle
  @Override
  public void visitClassDeclaration(ClassTree tree) {
    if (tree.name() != null) {
      addSymbol(tree.name().name(), tree, Symbol.Kind.CLASS);
    }
    newScope(tree);

    super.visitClassDeclaration(tree);
    leaveScope();
  }

  @Override
  public void visitMethodDeclaration(MethodDeclarationTree tree) {
    addSymbol(((MethodDeclarationTreeImpl) tree).nameToString(), tree, Symbol.Kind.FUNCTION);
    newScope(tree);
    addFunctionBuildInSymbols();
    addSymbols(((ParameterListTreeImpl) tree.parameters()).parameterIdentifiers(), Symbol.Kind.PARAMETER);

    super.visitMethodDeclaration(tree);
    leaveScope();
  }

  private void addFunctionBuildInSymbols() {
    createBuildInSymbolForScope("arguments", currentScope, Symbol.Kind.VARIABLE);
  }

  private void createBuildInSymbolForScope(String name, Scope scope, Symbol.Kind kind) {
    Symbol symbol = scope.createBuildInSymbol(name, kind);
    symbolModel.setScopeForSymbol(symbol, scope);
    symbolModel.setScopeFor(scope.getTree(), scope);
  }

  @Override
  public void visitCatchBlock(CatchBlockTree tree) {
    newScope(tree);
    addSymbols(((CatchBlockTreeImpl) tree).parameterIdentifiers(), Symbol.Kind.VARIABLE);

    super.visitCatchBlock(tree);
    leaveScope();
  }

  @Override
  public void visitFunctionDeclaration(FunctionDeclarationTree tree) {
    addSymbol(tree.name().name(), tree, Symbol.Kind.FUNCTION);
    newScope(tree);
    addFunctionBuildInSymbols();
    addSymbols(((ParameterListTreeImpl) tree.parameters()).parameterIdentifiers(), Symbol.Kind.PARAMETER);

    super.visitFunctionDeclaration(tree);
    leaveScope();
  }

  @Override
  public void visitArrowFunction(ArrowFunctionTree tree) {
    newScope(tree);
    addSymbols(((ArrowFunctionTreeImpl) tree).parameterIdentifiers(), Symbol.Kind.PARAMETER);

    super.visitArrowFunction(tree);
    leaveScope();
  }

  /**
   * Detail about <a href="http://people.mozilla.org/~jorendorff/es6-draft.html#sec-function-definitions-runtime-semantics-evaluation">Function Expression scope</a>
   * <blockquote>
   *  The BindingIdentifier in a FunctionExpression can be referenced from inside the FunctionExpression's FunctionBody
   *  to allow the function to call itself recursively. However, unlike in a FunctionDeclaration, the BindingIdentifier
   *  in a FunctionExpression cannot be referenced from and does not affect the scope enclosing the FunctionExpression.
   * </blockquote>
   **/
  @Override
  public void visitFunctionExpression(FunctionExpressionTree tree) {
    newScope(tree);
    addFunctionBuildInSymbols();
    if (tree.name() != null) {
      // Not available in enclosing scope
      addSymbol(tree.name().name(), tree, Symbol.Kind.FUNCTION);
    }
    addSymbols(((ParameterListTreeImpl) tree.parameters()).parameterIdentifiers(), Symbol.Kind.PARAMETER);

    super.visitFunctionExpression(tree);
    leaveScope();
  }

  @Override
  public void visitVariableDeclaration(VariableDeclarationTree tree) {
    addSymbols(((VariableDeclarationTreeImpl) tree).variableIdentifiers(), Symbol.Kind.VARIABLE);
    super.visitVariableDeclaration(tree);
  }

  /*
   * HELPERS
   */
  private void leaveScope() {
    if (currentScope != null) {
      currentScope = currentScope.outer();
    }
  }

  private void setScopeForTree(Tree tree) {
    symbolModel.setScopeFor(tree, currentScope);
  }

  private void addSymbol(String name, Tree tree, Symbol.Kind kind) {
    Symbol symbol = currentScope.createSymbol(name, tree, kind);
    symbolModel.setScopeForSymbol(symbol, currentScope);
    setScopeForTree(tree);
  }

  private void addSymbols(List<IdentifierTree> identifiers, Symbol.Kind kind) {
    for (IdentifierTree identifier : identifiers) {
      addSymbol(identifier.name(), identifier, kind);
    }
  }

  private void newScope(Tree tree) {
    Scope newScope = new Scope(currentScope, tree);

    if (currentScope != null) {
      currentScope.setNext(newScope);
    }

    currentScope = newScope;
    setScopeForTree(tree);
  }

}
