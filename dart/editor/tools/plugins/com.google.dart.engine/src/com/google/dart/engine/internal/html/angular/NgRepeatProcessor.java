/*
 * Copyright (c) 2013, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.dart.engine.internal.html.angular;

import com.google.dart.engine.ast.Block;
import com.google.dart.engine.ast.DeclaredIdentifier;
import com.google.dart.engine.ast.Expression;
import com.google.dart.engine.ast.ForEachStatement;
import com.google.dart.engine.ast.SimpleIdentifier;
import com.google.dart.engine.ast.Statement;
import com.google.dart.engine.error.AngularCode;
import com.google.dart.engine.html.ast.EmbeddedExpression;
import com.google.dart.engine.html.ast.XmlAttributeNode;
import com.google.dart.engine.html.ast.XmlTagNode;
import com.google.dart.engine.internal.builder.ElementBuilder;
import com.google.dart.engine.internal.builder.ElementHolder;
import com.google.dart.engine.internal.element.LocalVariableElementImpl;
import com.google.dart.engine.scanner.Keyword;
import com.google.dart.engine.scanner.KeywordToken;
import com.google.dart.engine.scanner.Token;
import com.google.dart.engine.scanner.TokenType;

import java.util.ArrayList;

/**
 * {@link NgRepeatProcessor} describes built-in <code>NgRepeatDirective</code> directive.
 */
class NgRepeatProcessor extends NgDirectiveProcessor {
  private static final String NG_REPEAT = "ng-repeat";

  public static final NgRepeatProcessor INSTANCE = new NgRepeatProcessor();

  private NgRepeatProcessor() {
  }

  @Override
  public void apply(AngularHtmlUnitResolver resolver, XmlTagNode node) {
    XmlAttributeNode attribute = node.getAttribute(NG_REPEAT);
    int offset = attribute.getValueToken().getOffset() + 1;
    String spec = attribute.getText();
    // scan attribute as Dart
    Token token = resolver.scanDart(spec, 0, spec.length(), offset);
    // parse item name
    Expression varExpression = resolver.parseExpression(token);
    token = varExpression.getEndToken().getNext();
    if (!(varExpression instanceof SimpleIdentifier)) {
      resolver.reportError(varExpression, AngularCode.EXPECTED_IDENTIFIER);
      return;
    }
    SimpleIdentifier varName = (SimpleIdentifier) varExpression;
    // skip "in"
    if (token.getType() != TokenType.KEYWORD || ((KeywordToken) token).getKeyword() != Keyword.IN) {
      resolver.reportError(varExpression, AngularCode.EXPECTED_IN);
      return;
    }
    token = token.getNext();
    // parse iterable
    Expression iterableExpr = resolver.parseExpression(token);
    // resolve as: for (name in iterable) {}
    DeclaredIdentifier loopVariable = new DeclaredIdentifier(null, null, null, null, varName);
    Block loopBody = new Block(null, new ArrayList<Statement>(), null);
    ForEachStatement loopStatement = new ForEachStatement(
        null,
        null,
        loopVariable,
        null,
        iterableExpr,
        null,
        loopBody);
    new ElementBuilder(new ElementHolder()).visitDeclaredIdentifier(loopVariable);
    resolver.resolveNode(loopStatement);
    // remember expressions
    attribute.setExpressions(new EmbeddedExpression[] {
        newEmbeddedExpression(varExpression), newEmbeddedExpression(iterableExpr)});
    // define variable
    LocalVariableElementImpl variable = (LocalVariableElementImpl) varName.getStaticElement();
    variable.setType(varName.getBestType());
    resolver.defineVariable(variable);
  }

  @Override
  public boolean canApply(XmlTagNode node) {
    return node.getAttribute(NG_REPEAT) != null;
  }
}
