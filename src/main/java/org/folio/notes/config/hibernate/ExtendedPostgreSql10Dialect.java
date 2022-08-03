package org.folio.notes.config.hibernate;

import java.util.List;
import org.hibernate.QueryException;
import org.hibernate.dialect.PostgreSQL10Dialect;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;

public class ExtendedPostgreSql10Dialect extends PostgreSQL10Dialect {

  public static final String CASE_IN_SENSITIVE_MATCHING_FUNCTION = "caseInSensitiveMatching";

  public ExtendedPostgreSql10Dialect() {
    super();
    registerFunction(CASE_IN_SENSITIVE_MATCHING_FUNCTION, CaseInSensitiveMatchingFunction.INSTANCE);
  }

  private static class CaseInSensitiveMatchingFunction extends StandardSQLFunction {

    public static final CaseInSensitiveMatchingFunction INSTANCE = new CaseInSensitiveMatchingFunction();

    protected CaseInSensitiveMatchingFunction() {
      super(CASE_IN_SENSITIVE_MATCHING_FUNCTION, StandardBasicTypes.BOOLEAN);
    }

    @Override
    public String render(Type firstArgumentType, List arguments, SessionFactoryImplementor factory)
      throws QueryException {

      if (arguments.size() != 2) {
        throw new QueryException(
          String.format("The '%s' function requires exactly two arguments.", CASE_IN_SENSITIVE_MATCHING_FUNCTION)
        );
      }

      return "(" + arguments.get(0) + " ilike " + arguments.get(1) + ")";
    }
  }

}
