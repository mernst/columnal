package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.AddSubtractExpressionContext;
import records.grammar.ExpressionParser.AndExpressionContext;
import records.grammar.ExpressionParser.ArrayExpressionContext;
import records.grammar.ExpressionParser.BooleanLiteralContext;
import records.grammar.ExpressionParser.BracketedCompoundContext;
import records.grammar.ExpressionParser.BracketedMatchContext;
import records.grammar.ExpressionParser.CallExpressionContext;
import records.grammar.ExpressionParser.ColumnRefContext;
import records.grammar.ExpressionParser.DivideExpressionContext;
import records.grammar.ExpressionParser.ExpressionContext;
import records.grammar.ExpressionParser.GreaterThanExpressionContext;
import records.grammar.ExpressionParser.IfThenElseExpressionContext;
import records.grammar.ExpressionParser.LessThanExpressionContext;
import records.grammar.ExpressionParser.MatchClauseContext;
import records.grammar.ExpressionParser.MatchContext;
import records.grammar.ExpressionParser.NotEqualExpressionContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.grammar.ExpressionParser.OrExpressionContext;
import records.grammar.ExpressionParser.PatternContext;
import records.grammar.ExpressionParser.RaisedExpressionContext;
import records.grammar.ExpressionParser.StringLiteralContext;
import records.grammar.ExpressionParser.TableIdContext;
import records.grammar.ExpressionParser.TagExpressionContext;
import records.grammar.ExpressionParser.TimesExpressionContext;
import records.grammar.ExpressionParser.TupleExpressionContext;
import records.grammar.ExpressionParser.VarRefContext;
import records.grammar.ExpressionParserBaseVisitor;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.ExFunction;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression
{
    public static final int MAX_STRING_SOLVER_LENGTH = 8;

    @OnThread(Tag.Simulation)
    public boolean getBoolean(int rowIndex, EvaluateState state, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        Object val = getValue(rowIndex, state);
        if (val instanceof Boolean)
            return (Boolean) val;
        else
            throw new InternalException("Expected boolean but got: " + val.getClass());
    }

    // Checks that all used variable names and column references are defined,
    // and that types check.  Return null if any problems
    public abstract @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException;

    // Like check, but for patterns.  For many expressions this is same as check,
    // unless you are a new-variable declaration or can have one beneath you.
    // If you override this, you should also override matchAsPattern
    public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varDeclAllowed, DataType srcType, RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        // By default, check as normal, and return same TypeState:
        @Nullable DataType type = check(data, state, onError);
        if (type == null || DataType.checkSame(srcType, type, s -> onError.recordError(this, s)) == null)
            return null;
        else
            return new Pair<>(type, state);
    }

    @OnThread(Tag.Simulation)
    public abstract @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException;

    // Note that there will be duplicates if referred to multiple times
    public abstract Stream<ColumnId> allColumnNames();

    public abstract String save(boolean topLevel);

    public static Expression parse(@Nullable String keyword, String src, TypeManager typeManager) throws UserException, InternalException
    {
        if (keyword != null)
        {
            src = src.trim();
            if (src.startsWith(keyword))
                src = src.substring(keyword.length());
            else
                throw new UserException("Missing keyword: " + keyword);
        }
        try
        {
            return Utility.parseAsOne(src.replace("\r", "").replace("\n", ""), ExpressionLexer::new, ExpressionParser::new, p ->
            {
                return new CompileExpression(typeManager).visit(p.completeExpression());
            });
        }
        catch (RuntimeException e)
        {
            throw new UserException("Problem parsing expression \"" + src + "\"", e);
        }
    }

    public abstract Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException;

    @Pure
    public Optional<Rational> constantFold()
    {
        return Optional.empty();
    }

    public abstract Pair<List<FXPlatformFunction<ConsecutiveBase,OperandNode>>, List<FXPlatformFunction<ConsecutiveBase,OperatorEntry>>> loadAsConsecutive();

    public abstract FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle();

    // Vaguely similar to getValue, but instead checks if the expression matches the given value
    // For many expressions, matching means equality, but if a new-variable item is involved
    // it's not necessarily plain equality.
    // Given that the expression has type-checked, you can assume the value is of the same type
    // as the current expression (and throw an InternalException if not)
    // If you override this, you should also override checkAsPattern
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        @Value Object ourValue = getValue(rowIndex, state);
        return Utility.compareValues(value, ourValue) == 0 ? state : null;
    }

    private static class CompileExpression extends ExpressionParserBaseVisitor<Expression>
    {
        private final TypeManager typeManager;

        public CompileExpression(TypeManager typeManager)
        {
            this.typeManager = typeManager;
        }

        @Override
        public Expression visitColumnRef(ColumnRefContext ctx)
        {
            TableIdContext tableIdContext = ctx.tableId();
            if (ctx.columnId() == null)
                throw new RuntimeException("Error processing column reference");
            return new ColumnReference(tableIdContext == null ? null : new TableId(tableIdContext.getText()), new ColumnId(ctx.columnId().getText()), ctx.columnRefType().WHOLECOLUMN() != null ? ColumnReferenceType.WHOLE_COLUMN : ColumnReferenceType.CORRESPONDING_ROW);
        }

        @Override
        public Expression visitNumericLiteral(NumericLiteralContext ctx)
        {
            try
            {
                return new NumericLiteral(Utility.parseNumber(ctx.NUMBER().getText()), ctx.UNIT() == null ? null : typeManager.getUnitManager().loadUse(ctx.UNIT().getText()));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException("Error parsing unit: \"" + ctx.UNIT().getText() + "\"", e);
            }
        }

        @Override
        public Expression visitStringLiteral(StringLiteralContext ctx)
        {
            return new StringLiteral(ctx.getText());
        }

        @Override
        public Expression visitBooleanLiteral(BooleanLiteralContext ctx)
        {
            return new BooleanLiteral(Boolean.valueOf(ctx.getText()));
        }

        @Override
        public Expression visitNotEqualExpression(NotEqualExpressionContext ctx)
        {
            return new NotEqualExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitEqualExpression(ExpressionParser.EqualExpressionContext ctx)
        {
            return new EqualExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitAndExpression(AndExpressionContext ctx)
        {
            return new AndExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitOrExpression(OrExpressionContext ctx)
        {
            return new OrExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitRaisedExpression(RaisedExpressionContext ctx)
        {
            return new RaiseExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitDivideExpression(DivideExpressionContext ctx)
        {
            return new DivideExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)));
        }

        @Override
        public Expression visitAddSubtractExpression(AddSubtractExpressionContext ctx)
        {
            return new AddSubtractExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression), Utility.<TerminalNode, Op>mapList(ctx.ADD_OR_SUBTRACT(), op -> op.getText().equals("+") ? Op.ADD : Op.SUBTRACT));
        }

        @Override
        public Expression visitGreaterThanExpression(GreaterThanExpressionContext ctx)
        {
            try
            {
                return new ComparisonExpression(Utility.mapList(ctx.expression(), this::visitExpression), Utility.mapListExI(ctx.GREATER_THAN(), op -> ComparisonOperator.parse(op.getText())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Expression visitLessThanExpression(LessThanExpressionContext ctx)
        {
            try
            {
                return new ComparisonExpression(Utility.mapList(ctx.expression(), this::visitExpression), Utility.mapListExI(ctx.LESS_THAN(), op -> ComparisonOperator.parse(op.getText())));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Expression visitTimesExpression(TimesExpressionContext ctx)
        {
            return new TimesExpression(Utility.<ExpressionContext, Expression>mapList(ctx.expression(), this::visitExpression));
        }

        @Override
        public Expression visitIfThenElseExpression(IfThenElseExpressionContext ctx)
        {
            return new IfThenElseExpression(visitExpression(ctx.expression(0)), visitExpression(ctx.expression(1)), visitExpression(ctx.expression(2)));
        }

        @Override
        public Expression visitTagExpression(TagExpressionContext ctx)
        {
            String constructorName = ctx.constructor().constructorName().getText();

            String typeName = ctx.constructor().typeName().getText();

            return new TagExpression(new Pair<>(typeName, constructorName), ctx.expression() == null ? null : visitExpression(ctx.expression()));
        }

        @Override
        public Expression visitBracketedCompound(BracketedCompoundContext ctx)
        {
            return visitCompoundExpression(ctx.compoundExpression());
        }

        @Override
        public Expression visitBracketedMatch(BracketedMatchContext ctx)
        {
            return visitMatch(ctx.match());
        }

        @Override
        public Expression visitVarRef(VarRefContext ctx)
        {
            return new VarUseExpression(ctx.getText());
        }

        @Override
        public Expression visitCallExpression(CallExpressionContext ctx)
        {
            // Utility.mapList(ctx.UNIT(), u -> )
            @NonNull Expression args;
            if (ctx.topLevelExpression() != null)
                args = visitTopLevelExpression(ctx.topLevelExpression());
            else
                args = new TupleExpression(ImmutableList.copyOf(Utility.mapList(ctx.expression(), e -> visitExpression(e))));
            return new CallExpression(ctx.functionName().getText(), Collections.emptyList(), args);
        }

        /*
        @Override
        public Expression visitBinaryOpExpression(BinaryOpExpressionContext ctx)
        {
            @Nullable Op op = Op.parse(ctx.binaryOp().getText());
            if (op == null)
                throw new RuntimeException("Broken operator parse: " + ctx.binaryOp().getText());
            return new BinaryOpExpression(visitExpression(ctx.expression().get(0)), op, visitExpression(ctx.expression().get(1)));
        }
        */

        @Override
        public Expression visitMatch(MatchContext ctx)
        {
            List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>();
            for (MatchClauseContext matchClauseContext : ctx.matchClause())
            {
                clauses.add(me -> {
                    List<Pattern> patterns = new ArrayList<>();
                    for (PatternContext patternContext : matchClauseContext.pattern())
                    {
                        @Nullable ExpressionContext guardExpression = patternContext.expression().size() < 2 ? null : patternContext.expression(1);
                        @Nullable Expression guard = guardExpression == null ? null : visitExpression(guardExpression);
                        patterns.add(new Pattern(visitExpression(patternContext.expression(0)), guard));
                    }
                    return me.new MatchClause(patterns, visitExpression(matchClauseContext.expression()));
                });
            }
            return new MatchExpression(visitExpression(ctx.expression()), clauses);
        }

        @Override
        public Expression visitArrayExpression(ArrayExpressionContext ctx)
        {
            return new ArrayExpression(ImmutableList.copyOf(Utility.mapList(ctx.expression(), c -> visitExpression(c))));
        }

        @Override
        public Expression visitTupleExpression(TupleExpressionContext ctx)
        {
            return new TupleExpression(ImmutableList.copyOf(Utility.mapList(ctx.expression(), c -> visitExpression(c))));
        }

        public Expression visitChildren(RuleNode node) {
            @Nullable Expression result = this.defaultResult();
            int n = node.getChildCount();

            for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
                ParseTree c = node.getChild(i);
                Expression childResult = c.accept(this);
                if (childResult == null)
                    break;
                result = this.aggregateResult(result, childResult);
            }
            if (result == null)
                throw new RuntimeException("No rules matched for " + node.getText());
            else
                return result;
        }
    }

    @Override
    public String toString()
    {
        return save(true);
    }

    // This is like a zipper.  It gets a list of all expressions in the tree (i.e. all nodes)
    // and returns them, along with a function.  If you pass that function a replacement,
    // it will build you a new copy of the entire expression with that one node replaced.
    // Used for testing
    public final Stream<Pair<Expression, Function<Expression, Expression>>> _test_allMutationPoints()
    {
        return Stream.concat(Stream.of(new Pair<>(this, e -> e)), _test_childMutationPoints());
    }

    public abstract Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints();

    // If this item can't make a type failure by itself (e.g. a literal) then returns null
    // Otherwise, uses the given generator to make a copy of itself which contains a type failure
    // in this node.  E.g. an equals expression might replace the lhs or rhs with a different type
    public abstract @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException;

    @Override
    public abstract boolean equals(@Nullable Object o);
    @Override
    public abstract int hashCode();

    // Only for testing:
    public static interface _test_TypeVary
    {
        public Expression getDifferentType(@Nullable DataType type) throws InternalException, UserException;
        public Expression getAnyType() throws UserException, InternalException;
        public Expression getNonNumericType() throws InternalException, UserException;

        public Expression getType(Predicate<DataType> mustMatch) throws InternalException, UserException;
        public List<Expression> getTypes(int amount, ExFunction<List<DataType>, Boolean> mustMatch) throws InternalException, UserException;
    }
}
