package org.folio.notes.config.hibernate.predicate;

import static org.folio.notes.config.hibernate.ExtendedPostgreSql10Dialect.CASE_IN_SENSITIVE_MATCHING_FUNCTION;

import java.io.Serializable;
import javax.persistence.criteria.Expression;
import org.hibernate.query.criteria.internal.CriteriaBuilderImpl;
import org.hibernate.query.criteria.internal.ParameterRegistry;
import org.hibernate.query.criteria.internal.Renderable;
import org.hibernate.query.criteria.internal.compile.RenderingContext;
import org.hibernate.query.criteria.internal.expression.LiteralExpression;
import org.hibernate.query.criteria.internal.predicate.AbstractSimplePredicate;

public class IlikePredicate extends AbstractSimplePredicate implements Serializable {

  private final Expression<String> matchExpression;

  private final Expression<String> pattern;

  public IlikePredicate(CriteriaBuilderImpl criteriaBuilder, Expression<String> matchExpression,
                        Expression<String> pattern) {
    super(criteriaBuilder);
    this.matchExpression = matchExpression;
    this.pattern = pattern;
  }

  public IlikePredicate(CriteriaBuilderImpl criteriaBuilder, Expression<String> matchExpression, String pattern) {
    this(criteriaBuilder, matchExpression, new LiteralExpression<>(criteriaBuilder, pattern));
  }

  public Expression<String> getMatchExpression() {
    return matchExpression;
  }

  public Expression<String> getPattern() {
    return pattern;
  }

  @Override
  public void registerParameters(ParameterRegistry registry) {
    Helper.possibleParameter(getMatchExpression(), registry);
    Helper.possibleParameter(getPattern(), registry);
  }

  @Override
  public String render(boolean isNegated, RenderingContext renderingContext) {
    var match = ((Renderable) getMatchExpression()).render(renderingContext);
    var patternRendered = ((Renderable) getPattern()).render(renderingContext);
    return String.format("function('%s', %s, %s) = %s",
      CASE_IN_SENSITIVE_MATCHING_FUNCTION, match, patternRendered, !isNegated);
  }
}
