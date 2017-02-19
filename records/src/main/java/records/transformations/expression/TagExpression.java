package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TaggedValue;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.transformations.expression.TypeState.TypeAndTagInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.FXPlatformFunction;
import utility.Pair;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 10/12/2016.
 */
public class TagExpression extends NonOperatorExpression
{
    private final Pair<String, String> tagName;
    private final @Nullable Expression inner;
    private int index;
    private @Nullable DataType innerDerivedType;

    public TagExpression(Pair<String, String> tagName, @Nullable Expression inner)
    {
        this.tagName = tagName;
        this.inner = inner;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        @Nullable TypeAndTagInfo typeAndIndex = state.findTaggedType(tagName, err -> onError.accept(this, err));
        if (typeAndIndex == null)
            return null;
        index = typeAndIndex.tagIndex;

        innerDerivedType = inner == null ? null : inner.check(data, state, onError);
        // We must not pass nulls to checkSame as that counts as failed checking, not optional items
        if ((inner == null && typeAndIndex.innerType == null) || DataType.checkSame(typeAndIndex.innerType, innerDerivedType, err -> onError.accept(this, err)) != null)
        {
            return typeAndIndex.wholeType;
        }
        else
            return null;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return new TaggedValue(index, inner == null ? null : inner.getValue(rowIndex, state));
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return inner == null ? Stream.empty() : inner.allColumnNames();
    }

    @Override
    public String save(boolean topLevel)
    {
        @Nullable String typeName = tagName.getFirst();
        String tag = "\\" + OutputBuilder.quotedIfNecessary(typeName) + ":" + OutputBuilder.quotedIfNecessary(tagName.getSecond());
        if (inner == null)
            return tag;
        else
            return tag + ":" + inner.save(false);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle()
    {
        throw new RuntimeException("TODO");
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return inner == null ? Stream.empty() : inner._test_allMutationPoints().map(p -> p.replaceSecond(e -> new TagExpression(tagName, p.getSecond().apply(e))));
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO could replace with known invalid tag
        if (inner == null)
            // Shouldn't have type; add one:
            return new TagExpression(tagName, newExpressionOfDifferentType.getAnyType());
        else
        {
            if (r.nextBoolean())
                // Should have type; scrap it:
                return new TagExpression(tagName, null);
            else
                // Should have type, but replace with different:
                return new TagExpression(tagName, newExpressionOfDifferentType.getDifferentType(innerDerivedType));
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TagExpression that = (TagExpression) o;

        if (!tagName.equals(that.tagName)) return false;
        return inner != null ? inner.equals(that.inner) : that.inner == null;
    }

    @Override
    public int hashCode()
    {
        int result = tagName.hashCode();
        result = 31 * result + (inner != null ? inner.hashCode() : 0);
        return result;
    }

    public @Nullable Expression getInner()
    {
        return inner;
    }

    public static Expression _testMake(String typeName, String tagName, Expression inner)
    {
        return new TagExpression(new Pair<>(typeName, tagName), inner);
    }
}
