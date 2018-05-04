package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.SquareBracketedExpression;
import records.types.MutVar;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An array expression like [0, x, 3].  This could be called an array literal, but didn't want to confuse
 * as the items in the array don't have to be literals.  But this expression is for constructing
 * arrays of a known length from a fixed set of expressions (like [0, y] but not just "xs" which happens
 * to be of array type).
 */
public class ArrayExpression extends Expression
{
    private final ImmutableList<@Recorded Expression> items;
    private @Nullable TypeExp elementType;
    private @MonotonicNonNull List<TypeExp> _test_originalTypes;

    public ArrayExpression(ImmutableList<@Recorded Expression> items)
    {
        this.items = items;
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Empty array - special case:
        if (items.isEmpty())
            return onError.recordType(this, TypeExp.list(this, new MutVar(this)));
        TypeExp[] typeArray = new TypeExp[items.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable TypeExp t = items.get(i).check(dataLookup, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t;
        }
        this.elementType = onError.recordError(this, TypeExp.unifyTypes(ImmutableList.copyOf(typeArray)));
        _test_originalTypes = Arrays.asList(typeArray);
        if (elementType == null)
            return null;
        return onError.recordType(this, TypeExp.list(this, elementType));
    }

    @Override
    public @Nullable Pair<@Recorded TypeExp, TypeState> checkAsPattern(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Empty array - special case:
        if (items.isEmpty())
            return new Pair<>(onError.recordTypeNN(this, TypeExp.list(this, new MutVar(this))), state);
        TypeExp[] typeArray = new TypeExp[items.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable Pair<@Recorded TypeExp, TypeState> t = items.get(i).checkAsPattern(data, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t.getFirst();
            state = t.getSecond();
        }
        this.elementType = onError.recordError(this, TypeExp.unifyTypes(ImmutableList.copyOf(typeArray)));
        _test_originalTypes = Arrays.asList(typeArray);
        if (elementType == null)
            return null;
        return new Pair<>(onError.recordTypeNN(this, TypeExp.list(this, elementType)), state);
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof ListEx)
        {
            ListEx list = (ListEx)value;
            if (list.size() != items.size())
                return null; // Not an exception, just means the value has different size to the pattern, so can't match
            @Nullable EvaluateState curState = state;
            for (int i = 0; i < items.size(); i++)
            {
                curState = items.get(i).matchAsPattern(list.get(i), curState);
                if (curState == null)
                    return null;
            }
            return curState;
        }
        throw new InternalException("Expected array but found " + value.getClass());
    }

    @Override
    public @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        List<@Value Object> values = new ArrayList<>(items.size());
        for (Expression item : items)
        {
            values.add(item.getValue(state));
        }
        return DataTypeUtility.value(values);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return items.stream().flatMap(Expression::allColumnReferences);
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "[" + items.stream().map(e -> e.save(items.size() == 1 ? BracketedStatus.DIRECT_SQUARE_BRACKETED : BracketedStatus.MISC, renames)).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(StyledString.s("["), items.stream().map(e -> e.toDisplay(items.size() == 1 ? BracketedStatus.DIRECT_SQUARE_BRACKETED : BracketedStatus.MISC)).collect(StyledString.joining(", ")), StyledString.s("]"));
    }

    @Override
    public Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>> loadOperands = Utility.mapList(items, x -> x.loadAsSingle());
        List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>> loadCommas = Utility.replicate(Math.max(items.size() - 1, 0), (p, s) -> new OperatorEntry<>(Expression.class, ",", false, p));
        return (p, s) -> new SquareBracketedExpression(p, SingleLoader.withSemanticParent(new Pair<>(loadOperands, loadCommas), s));
    }

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, items.size()).mapToObj(i ->
            items.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> new ArrayExpression(Utility.replaceList(items, i, p.getSecond().apply(newExp)))))).flatMap(s -> s);
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        if (items.size() <= 1)
            return null; // Can't cause a failure with 1 or less items; need 2+ to have a mismatch
        int index = r.nextInt(items.size());
        if (elementType == null || _test_originalTypes == null)
            throw new InternalException("Calling _test_typeFailure despite type-check failure");
        // If all items other than this one are blank arrays, won't cause type error:
        boolean hasOtherNonBlank = false;
        for (int i = 0; i < items.size(); i++)
        {
            if (i == index)
                continue;
            
            // We test if it is non-blank by unifying with an array type of MutVar, and seeing if the MutVat points to anything after pruning:
            MutVar mut = new MutVar(null);
            TypeExp arrayOfMut = TypeExp.list(null, mut);

            Either<StyledString, TypeExp> unifyResult = TypeExp.unifyTypes(_test_originalTypes.get(i), arrayOfMut);
            // If it doesn't match, not an array:
            if (unifyResult.isLeft())
            {
                hasOtherNonBlank = true;
            }
            else
            {
                hasOtherNonBlank = !(mut.prune() instanceof MutVar);
            }
        }
        if (!hasOtherNonBlank)
            return null; // Won't make a failure
        return new ArrayExpression(Utility.replaceList(items, index, newExpressionOfDifferentType.getDifferentType(elementType)));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayExpression that = (ArrayExpression) o;

        return items.equals(that.items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    public ImmutableList<@Recorded Expression> _test_getElements()
    {
        return items;
    }
}
