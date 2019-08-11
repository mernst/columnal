package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.TypeManager;
import records.transformations.expression.CanonicalSpan;
import records.gui.lexeditor.UnitLexer.UnitBracket;
import records.gui.lexeditor.UnitLexer.UnitOp;
import records.gui.lexeditor.completion.InsertListener;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.InvalidOperatorUnitExpression;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitDivideExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import records.transformations.expression.UnitRaiseExpression;
import records.transformations.expression.UnitTimesExpression;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class UnitSaver extends SaverBase<UnitExpression, UnitSaver, UnitOp, UnitBracket, Void>// implements ErrorAndTypeRecorder
{
    public static final DataFormat UNIT_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-type");
    
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(UnitOp.MULTIPLY), UnitSaver::makeTimes),
        new OperatorExpressionInfo(UnitOp.DIVIDE, UnitSaver::makeDivide),
        new OperatorExpressionInfo(UnitOp.RAISE, UnitSaver::makeRaise)
    );
    
    private static UnitExpression makeTimes(ImmutableList<@Recorded UnitExpression> expressions, List<Pair<UnitOp, CanonicalSpan>> operators)
    {
        return new UnitTimesExpression(expressions);
    }

    private static UnitExpression makeDivide(@Recorded UnitExpression lhs, CanonicalSpan opNode, @Recorded UnitExpression rhs)
    {
        return new UnitDivideExpression(lhs, rhs);
    }

    private static UnitExpression makeRaise(@Recorded UnitExpression lhs, CanonicalSpan opNode, @Recorded UnitExpression rhs)
    {
        return new UnitRaiseExpression(lhs, rhs);
    }

    @Override
    public BracketAndNodes<UnitExpression, UnitSaver, Void, UnitExpression> expectSingle(@UnknownInitialization(Object.class)UnitSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new BracketAndNodes<>(new ApplyBrackets<Void, UnitExpression, UnitExpression>()
        {
            @Override
            public @Nullable @Recorded UnitExpression apply(@NonNull Void items)
            {
                // Should not be possible anyway
                throw new IllegalStateException();
            }

            @Override
            public @NonNull @Recorded UnitExpression applySingle(@NonNull @Recorded UnitExpression singleItem)
            {
                return singleItem;
            }
        }, location, ImmutableList.of());
    }

    //UnitManager getUnitManager();


    public UnitSaver(TypeManager typeManager, InsertListener insertListener)
    {
        super(typeManager, insertListener);
    }

    @Override
    protected <R extends StyledShowable> @Recorded R makeExpression(List<Either<@Recorded UnitExpression, OpAndNode>> content, BracketAndNodes<UnitExpression, UnitSaver, Void, R> brackets, @CanonicalLocation int innerContentLocation, @Nullable String terminatorDescription)
    {
        if (content.isEmpty())
        {
            if (terminatorDescription != null)
                locationRecorder.addErrorAndFixes(new CanonicalSpan(innerContentLocation, innerContentLocation), StyledString.s("Missing expression before " + terminatorDescription), ImmutableList.of());
            return brackets.applyBrackets.applySingle(record(brackets.location, new InvalidOperatorUnitExpression(ImmutableList.of())));
        }
        CanonicalSpan location = CanonicalSpan.fromTo(getLocationForEither(content.get(0)), getLocationForEither(content.get(content.size() - 1)));
        
        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<@Recorded UnitExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();
            
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return brackets.applyBrackets.applySingle(validOperands.get(0));

            // Raise is a special case as it doesn't need to be bracketed:
            for (int i = 0; i < validOperators.size(); i++)
            {
                if (validOperators.get(i).op.equals(UnitOp.RAISE))
                {
                    if (validOperands.get(i) instanceof SingleUnitExpression && i + 1 < validOperands.size() && validOperands.get(i + 1) instanceof UnitExpressionIntLiteral)
                    {
                        validOperators.remove(i);
                        @Recorded UnitExpressionIntLiteral power = (UnitExpressionIntLiteral) validOperands.remove(i + 1);
                        CanonicalSpan recorder = locationRecorder.recorderFor(validOperands.get(i));
                        validOperands.set(i, record(CanonicalSpan.fromTo(recorder, locationRecorder.recorderFor(power)), new UnitRaiseExpression(validOperands.get(i), power)));
                    }
                }
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable @Recorded R e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded UnitExpression>> arg) ->
                    brackets.applyBrackets.applySingle(makeInvalidOp(brackets.location, arg))
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            if (e != null)
            {
                return e;
            }

        }

        return brackets.applyBrackets.applySingle(collectedItems.makeInvalid(location, InvalidOperatorUnitExpression::new));
    }

    @Override
    protected UnitExpression opToInvalid(UnitOp unitOp)
    {
        return new InvalidSingleUnitExpression(unitOp.getContent());
    }

    @Override
    protected @Nullable Supplier<@Recorded UnitExpression> canBeUnary(OpAndNode operator, UnitExpression followingOperand)
    {
        return null;
    }

    @Override
    protected @Recorded UnitExpression makeInvalidOp(CanonicalSpan location, ImmutableList<Either<OpAndNode, @Recorded UnitExpression>> items)
    {
        return locationRecorder.record(location, new InvalidOperatorUnitExpression(Utility.<Either<OpAndNode, @Recorded UnitExpression>, @Recorded UnitExpression>mapListI(items, x -> x.<@Recorded UnitExpression>either(op -> locationRecorder.record(op.sourceNode, new InvalidSingleUnitExpression(op.op.getContent())), y -> y))));
    }

    private static Pair<UnitOp, @Localized String> opD(UnitOp op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }

    public void saveBracket(UnitBracket bracket, CanonicalSpan errorDisplayer)
    {
        if (bracket == UnitBracket.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, new Terminator(")")
            {
                @Override
                public boolean terminate(FetchContent<UnitExpression, UnitSaver, Void> makeContent, @Nullable UnitBracket terminator, CanonicalSpan keywordErrorDisplayer)
                {
                    BracketAndNodes<UnitExpression, UnitSaver, Void, UnitExpression> brackets = expectSingle(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, keywordErrorDisplayer));
                    if (terminator == UnitBracket.CLOSE_ROUND)
                    {
                        // All is well:
                        @Recorded UnitExpression result = makeContent.fetchContent(brackets);
                        currentScopes.peek().items.add(Either.left(result));
                    } 
                    else
                    {
                        // Error!
                        locationRecorder.addErrorAndFixes(keywordErrorDisplayer, StyledString.concat(StyledString.s("Missing ) before "), terminator == null ? StyledString.s("end") : terminator.toStyledString()), ImmutableList.of());
                        // Important to call makeContent before adding to scope on the next line:
                        ImmutableList.Builder<@Recorded UnitExpression> items = ImmutableList.builder();
                        items.add(record(errorDisplayer, new InvalidSingleUnitExpression(bracket.getContent())));
                        items.add(makeContent.fetchContent(brackets));
                        if (terminator != null)
                            items.add(record(errorDisplayer, new InvalidSingleUnitExpression(terminator.getContent())));
                        @Recorded UnitExpression invalid = record(CanonicalSpan.fromTo(brackets.location, keywordErrorDisplayer), new InvalidOperatorUnitExpression(items.build()));
                        currentScopes.peek().items.add(Either.left(invalid));
                    }
                    return true;
                }
            }));
        }
        else
        {
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope();
            }
            cur.terminator.terminate(new FetchContent<UnitExpression, UnitSaver, Void>()
            {
                @Override
                public <R extends StyledShowable> @Recorded R fetchContent(BracketAndNodes<UnitExpression, UnitSaver, Void, R> brackets)
                {
                    return UnitSaver.this.<R>makeExpression(cur.items, brackets, cur.openingNode.end, cur.terminator.terminatorDescription);
                }
            }, bracket, errorDisplayer);
        }
    }

    @Override
    protected UnitExpression keywordToInvalid(UnitBracket unitBracket)
    {
        return new InvalidSingleUnitExpression(unitBracket.getContent());
    }

    @Override
    protected CanonicalSpan recorderFor(@Recorded UnitExpression unitExpression)
    {
        return locationRecorder.recorderFor(unitExpression);
    }

    @Override
    protected BracketAndNodes<UnitExpression, UnitSaver, Void, UnitExpression> unclosedBrackets(BracketAndNodes<UnitExpression, UnitSaver, Void, UnitExpression> closed)
    {
        return new BracketAndNodes<UnitExpression, UnitSaver, Void, UnitExpression>(new ApplyBrackets<Void, UnitExpression, UnitExpression>()
        {
            @Nullable
            @Override
            public @Recorded UnitExpression apply(@NonNull Void items)
            {
                // Can't happen
                throw new IllegalStateException();
            }

            @NonNull
            @Override
            public @Recorded UnitExpression applySingle(@NonNull @Recorded UnitExpression singleItem)
            {
                return singleItem;
            }
        }, closed.location, ImmutableList.of(closed.applyBrackets));
    }
}
