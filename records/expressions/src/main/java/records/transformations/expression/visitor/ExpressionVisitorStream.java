package records.transformations.expression.visitor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager.TagInfo;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DefineExpression.Definition;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.stream.Stream;

/**
 * By default, concatenates streams from visiting all children expressions.
 * Returns empty stream for all terminal nodes.
 */
public class ExpressionVisitorStream<T> implements ExpressionVisitor<Stream<T>>
{
    @Override
    public Stream<T> notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return apply(lhs, rhs);
    }

    private Stream<T> apply(@Recorded Expression... expressions)
    {
        return apply(ImmutableList.copyOf(expressions));
    }
    
    private Stream<T> apply(ImmutableList<@Recorded Expression> expressions)
    {
        return expressions.stream().flatMap(e -> e.visit(this));
    }

    @Override
    public Stream<T> divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return apply(lhs, rhs);
    }

    @Override
    public Stream<T> addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> and(AndExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> or(OrExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> list(ArrayExpression self, ImmutableList<@Recorded Expression> items)
    {
        return apply(items);
    }

    @Override
    public Stream<T> column(ColumnReference self, @Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litBoolean(BooleanLiteral self, @Value Boolean value)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments)
    {
        return apply(Utility.prependToList(callTarget, arguments));
    }

    @Override
    public Stream<T> comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> ident(IdentExpression self, @ExpressionIdentifier String text)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        return apply(condition, thenExpression, elseExpression);
    }

    @Override
    public Stream<T> invalidIdent(InvalidIdentExpression self, String text)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> implicitLambdaArg(ImplicitLambdaArg self)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items)
    {
        return apply(items);
    }

    @Override
    public Stream<T> matchAnything(MatchAnythingExpression self)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return apply(lhs, rhs);
    }

    @Override
    public Stream<T> raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs)
    {
        return apply(lhs, rhs);
    }

    @Override
    public Stream<T> standardFunction(StandardFunction self, StandardFunctionDefinition functionDefinition)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> litText(StringLiteral self, @Value String value)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions)
    {
        return apply(expressions);
    }

    @Override
    public Stream<T> litType(TypeLiteralExpression self, TypeExpression type)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> constructor(ConstructorExpression self, Either<String, TagInfo> tag)
    {
        return Stream.of();
    }

    @Override
    public Stream<T> match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses)
    {
        Stream<T> expStream = expression.visit(this);
        return Stream.<T>concat(expStream, clauses.stream().<T>flatMap(clause -> {
            // Must call patterns first:
            Stream<T> patternsStream = Utility.mapListI(clause.getPatterns(), p -> visitPattern(p)).stream().flatMap(s -> s);
            Stream<T> outcomeStream = clause.getOutcome().visit(this);
            return Stream.<T>concat(patternsStream, outcomeStream);
        }));
    }

    @Override
    public Stream<T> define(DefineExpression self, ImmutableList<Either<@Recorded HasTypeExpression, Definition>> defines, @Recorded Expression body)
    {
        return Stream.<T>concat(defines.stream().<T>flatMap(e -> e.<Stream<T>>either(x -> x.visit(this), x -> Stream.<T>concat(x.lhsPattern.<Stream<T>>visit(this), x.rhsValue.<Stream<T>>visit(this)))), body.<Stream<T>>visit(this));
    }

    @Override
    public Stream<T> hasType(HasTypeExpression self, @ExpressionIdentifier String varName, @Recorded TypeLiteralExpression type)
    {
        return type.visit(this);
    }

    @Override
    public Stream<T> lambda(LambdaExpression self, ImmutableList<@Recorded Expression> parameters, @Recorded Expression body)
    {
        return Stream.<T>concat(parameters.stream().<T>flatMap(e -> e.visit(this)), body.<Stream<T>>visit(this));
    }

    @Override
    public Stream<T> record(RecordExpression self, ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members)
    {
        return members.stream().flatMap(p -> p.getSecond().visit(this));
    }

    @Override
    public Stream<T> field(FieldAccessExpression self, @Recorded Expression lhsRecord, @Recorded Expression fieldName)
    {
        return Stream.<T>concat(lhsRecord.<Stream<T>>visit(this), fieldName.<Stream<T>>visit(this));
    }

    protected Stream<T> visitPattern(Pattern pattern)
    {
        Stream<T> patStream = pattern.getPattern().visit(this);
        return Stream.<T>concat(patStream, Utility.streamNullable(pattern.getGuard()).<T>flatMap(g -> g.visit(this)));
    }
}
