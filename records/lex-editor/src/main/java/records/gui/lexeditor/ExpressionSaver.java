package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.ExpressionLexer.Keyword;
import records.gui.lexeditor.ExpressionLexer.Op;
import records.gui.lexeditor.ExpressionSaver.BracketContent;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.function.FunctionLookup;
import styled.StyledCSS;
import styled.StyledString;
import styled.StyledString.Builder;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionSaver extends SaverBase<Expression, ExpressionSaver, Op, Keyword, BracketContent>
{
    public class BracketContent
    {
        private final ImmutableList<@Recorded Expression> expressions;

        public BracketContent(ImmutableList<@Recorded Expression> expressions)
        {
            this.expressions = expressions;
        }
    }
    
    private final FunctionLookup functionLookup;

    public ExpressionSaver(FunctionLookup functionLookup)
    {
        this.functionLookup = functionLookup;
    }

    @Override
    protected BracketAndNodes<Expression, ExpressionSaver, BracketContent> expectSingle(@UnknownInitialization(Object.class)ExpressionSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, Expression>()
        {
            @Override
            public @Nullable @Recorded Expression apply(@NonNull BracketContent items)
            {
                if (items.expressions.size() == 1)
                    return items.expressions.get(0);
                else
                    return null;
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, location, ImmutableList.of(tupleBracket(locationRecorder, location), makeList(locationRecorder, location)));
    }
    
    private ApplyBrackets<BracketContent, Expression> makeList(@UnknownInitialization(Object.class)ExpressionSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new ApplyBrackets<BracketContent, Expression>()
        {
            @Override
            public @Nullable @Recorded Expression apply(@NonNull BracketContent items)
            {
                return locationRecorder.record(location, new ArrayExpression(items.expressions));
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return locationRecorder.record(location, new ArrayExpression(ImmutableList.<@Recorded Expression>of(singleItem)));
            }
        };
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveKeyword(Keyword keyword, CanonicalSpan errorDisplayer)
    {
        Supplier<ImmutableList<@Recorded Expression>> prefixKeyword = () -> ImmutableList.of(record(errorDisplayer, new InvalidIdentExpression(keyword.getContent())));
        
        if (keyword == Keyword.QUEST)
        {
            saveOperand(new ImplicitLambdaArg(), errorDisplayer);
        }
        else if (keyword == Keyword.OPEN_ROUND)
        {
            Supplier<ImmutableList<@Recorded Expression>> invalidPrefix = prefixKeyword;
            Function<CanonicalSpan, ApplyBrackets<BracketContent, Expression>> applyBrackets = c -> tupleBracket(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, c));
            ArrayList<Either<@Recorded Expression, OpAndNode>> precedingItems = currentScopes.peek().items;
            // Function calls are a special case:
            if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(ExpressionUtil::isCallTarget, op -> false))
            {
                @Nullable @Recorded Expression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded Expression>either(e -> e, op -> null);
                // Shouldn't ever be null:
                if (callTarget != null)
                {
                    applyBrackets = c -> new ApplyBrackets<BracketContent, Expression>()
                    {
                        @Override
                        public @NonNull @Recorded Expression apply(@NonNull BracketContent args)
                        {
                            if (callTarget instanceof StandardFunction)
                            {
                                StandardFunction function = (StandardFunction) callTarget;
                                ImmutableList<String> paramNames = function.getFunction().getParamNames();
                                if (args.expressions.isEmpty())
                                {
                                    // No params yet, need to add in the empty bit between brackets
                                    locationRecorder.recordEntryPrompt(c, makeParamPrompt(function.getName(), paramNames, 0));
                                }
                                else
                                {
                                    for (int i = 0; i < args.expressions.size() && i < paramNames.size(); i++)
                                    {
                                        locationRecorder.recordEntryPrompt(args.expressions.get(i), makeParamPrompt(function.getName(), paramNames, i));
                                    }
                                }
                            }
                            
                            return locationRecorder.record(CanonicalSpan.fromTo(locationRecorder.recorderFor(callTarget), c), new CallExpression(callTarget, args.expressions));
                        }

                        private StyledString makeParamPrompt(String functionName, ImmutableList<String> paramNames, int paramIndex)
                        {
                            StyledString.Builder r = new Builder();
                            r.append(functionName).append("(");
                            for (int i = 0; i < paramNames.size(); i++)
                            {
                                if (i > 0)
                                    r.append(", ");
                                if (i == paramIndex)
                                {
                                    r.append(StyledString.styled(paramNames.get(i), new StyledCSS("entry-prompt-bold")));
                                }
                                else
                                {
                                    r.append(paramNames.get(i));
                                }
                            }
                            r.append(")");
                            return r.build().withStyle(new StyledCSS("entry-prompt"));
                        }

                        @Override
                        public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
                        {
                            return apply(new BracketContent(ImmutableList.<@Recorded Expression>of(singleItem)));
                        }
                    };
                    invalidPrefix = () -> {
                        return Utility.prependToList(callTarget, prefixKeyword.get());
                    };
                }
            }
            Function<CanonicalSpan, ApplyBrackets<BracketContent, Expression>> applyBracketsFinal = applyBrackets;
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.CLOSE_ROUND), close -> new BracketAndNodes<>(applyBracketsFinal.apply(close), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()), (bracketed, bracketEnd) -> {
                return Either.<@Recorded Expression, Terminator>left(bracketed);
            }, invalidPrefix, null, true)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer,
                expect(ImmutableList.of(Keyword.CLOSE_SQUARE),
                    close -> new BracketAndNodes<Expression, ExpressionSaver, BracketContent>(makeList(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, close)), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()),
                    (e, c) -> Either.<@Recorded Expression, Terminator>left(e), prefixKeyword, null, true)));
        }
        else if (keyword == Keyword.IF)
        {
            ImmutableList.Builder<@Recorded Expression> invalid = ImmutableList.builder();
            invalid.addAll(prefixKeyword.get());
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.THEN, Keyword.ELSE, Keyword.ENDIF), miscBracketsFrom(errorDisplayer), (condition, conditionEnd) -> {
                invalid.add(condition);
                invalid.add(record(conditionEnd, new InvalidIdentExpression(Keyword.THEN.getContent())));
                return Either.right(expect(ImmutableList.of(Keyword.ELSE, Keyword.ENDIF), miscBracketsFrom(conditionEnd), (thenPart, thenEnd) -> {
                    invalid.add(thenPart);
                    invalid.add(record(thenEnd, new InvalidIdentExpression(Keyword.ELSE.getContent())));
                    return Either.right(expect(ImmutableList.of(Keyword.ENDIF), miscBracketsFrom(thenEnd), (elsePart, elseEnd) -> {
                        return Either.<@Recorded Expression, Terminator>left(locationRecorder.record(CanonicalSpan.fromTo(errorDisplayer, elseEnd), new IfThenElseExpression(condition, thenPart, elsePart)));
                    }, invalid::build, ifPrompt(2), false));
                }, invalid::build, ifPrompt(1), false));
            }, invalid::build, ifPrompt(0), false)));
        }
        else if (keyword == Keyword.MATCH)
        {            
            currentScopes.push(new Scope(errorDisplayer, expectOneOf(errorDisplayer, ImmutableList.of(new Case(errorDisplayer)), Stream.<Supplier<@Recorded Expression>>of(() -> record(errorDisplayer, new InvalidIdentExpression(keyword.getContent()))))));
        }
        else if (keyword == Keyword.DEFINE)
        {
            currentScopes.push(new Scope(errorDisplayer, expectOneOf(errorDisplayer, ImmutableList.of(new FurtherDefine(errorDisplayer, ImmutableList.of()), new DefineBody(ImmutableList.of(), errorDisplayer)), Stream.<Supplier<@Recorded Expression>>of(() -> record(errorDisplayer, new InvalidIdentExpression(keyword.getContent()))))));
        }
        else
        {
            // Should be a terminator:
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope();
            }
            cur.terminator.terminate(new FetchContent<Expression, ExpressionSaver, BracketContent>()
            {
                @Override
                public @Recorded Expression fetchContent(BracketAndNodes<Expression, ExpressionSaver, BracketContent> brackets)
                {
                    return ExpressionSaver.this.makeExpression(cur.items, brackets, cur.openingNode.end, cur.terminator.terminatorDescription);
                }
            }, keyword, errorDisplayer);
        }
    }

    private StyledString ifPrompt(int partToBold)
    {
        ArrayList<StyledString> parts = new ArrayList<>();
        parts.add(StyledString.s(" condition "));
        parts.add(StyledString.s(" value if true "));
        parts.add(StyledString.s(" value if false "));
        
        parts.set(partToBold, parts.get(partToBold).withStyle(new StyledCSS("entry-prompt-bold")));
        return StyledString.concat(
            StyledString.s(Keyword.IF.getContent()),
            parts.get(0),
            StyledString.s(Keyword.THEN.getContent()),
            parts.get(1),
            StyledString.s(Keyword.ELSE.getContent()),
            parts.get(2),
            StyledString.s(Keyword.ENDIF.getContent())
        );
    }

    @Override
    protected BracketAndNodes<Expression, ExpressionSaver, BracketContent> unclosedBrackets(BracketAndNodes<Expression, ExpressionSaver, BracketContent> closed)
    {
        return new BracketAndNodes<Expression, ExpressionSaver, BracketContent>(new ApplyBrackets<BracketContent, Expression>()
        {
            @Nullable
            @Override
            public @Recorded Expression apply(@NonNull BracketContent items)
            {
                return record(closed.location, new InvalidOperatorExpression(items.expressions));
            }

            @NonNull
            @Override
            public @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, closed.location, ImmutableList.of(closed.applyBrackets));
    }

    private ApplyBrackets<BracketContent, Expression> tupleBracket(@UnknownInitialization(Object.class)ExpressionSaver this, EditorLocationAndErrorRecorder locationRecorder, CanonicalSpan location)
    {
        return new ApplyBrackets<BracketContent, Expression>()
        {
            @Override
            public @Recorded @Nullable Expression apply(@NonNull BracketContent items)
            {
                if (items.expressions.isEmpty())
                    return null;
                else if (items.expressions.size() == 1)
                    return items.expressions.get(0);
                else
                    return locationRecorder.record(location, new TupleExpression(items.expressions));
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        };
    }

    @Override
    protected @Recorded Expression makeExpression(List<Either<@Recorded Expression, OpAndNode>> content, BracketAndNodes<Expression, ExpressionSaver, BracketContent> brackets, @CanonicalLocation int innerContentLocation, @Nullable String terminatorDescription)
    {
        if (content.isEmpty())
        {
            @Nullable @Recorded Expression bracketedEmpty = brackets.applyBrackets.apply(new BracketContent(ImmutableList.of()));
            if (bracketedEmpty != null)
                return bracketedEmpty;
            else
            {
                if (terminatorDescription != null)
                    locationRecorder.addErrorAndFixes(new CanonicalSpan(innerContentLocation, innerContentLocation), StyledString.s("Missing expression before " + terminatorDescription), ImmutableList.of());
                return locationRecorder.record(new CanonicalSpan(innerContentLocation, innerContentLocation), new InvalidOperatorExpression(ImmutableList.of()));
            }
        }
        CanonicalSpan location = CanonicalSpan.fromTo(getLocationForEither(content.get(0)), getLocationForEither(content.get(content.size() - 1))); 

        CollectedItems collectedItems = processItems(content);

        @Nullable @Recorded Expression e = null;
        if (collectedItems.isValid())
        {
            ArrayList<@Recorded Expression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();

            ArrayList<@Recorded Expression> beforePrevCommas = new ArrayList<>();
            ArrayList<@Recorded Expression> sinceLastCommaOperands = new ArrayList<>();
            ArrayList<OpAndNode> sinceLastCommaOperators = new ArrayList<>();
            // Split by commas
            for (int i = 0; i < validOperands.size(); i++)
            {
                sinceLastCommaOperands.add(validOperands.get(i));
                if (i < validOperators.size() && validOperators.get(i).op == Op.COMMA)
                {
                    BracketAndNodes<Expression, ExpressionSaver, BracketContent> unbracketed = unbracketed(sinceLastCommaOperands);
                    @Recorded Expression made = makeExpressionWithOperators(OPERATORS, locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded Expression>> es) -> makeInvalidOp(location, es), ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.copyOf(sinceLastCommaOperators), unbracketed);
                    if (made != null)
                        beforePrevCommas.add(made);
                    else
                        beforePrevCommas.add(makeInvalidOp(unbracketed.location, interleave(ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.copyOf(sinceLastCommaOperators))));
                    sinceLastCommaOperands.clear();
                    sinceLastCommaOperators.clear();
                }
                else if (i < validOperators.size())
                {
                    sinceLastCommaOperators.add(validOperators.get(i));
                }
            }
            
            // Now we need to check the operators can work together as one group:
            BracketAndNodes<Expression, ExpressionSaver, BracketContent> unbracketed = unbracketed(sinceLastCommaOperands);
            @Recorded Expression made = makeExpressionWithOperators(OPERATORS, locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded Expression>> es) -> makeInvalidOp(location, es), ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.copyOf(sinceLastCommaOperators), unbracketed);
            if (made != null)
                beforePrevCommas.add(made);
            else
                beforePrevCommas.add(makeInvalidOp(unbracketed.location, interleave(ImmutableList.copyOf(sinceLastCommaOperands), ImmutableList.copyOf(sinceLastCommaOperators))));

            BracketContent bracketContent = new BracketContent(ImmutableList.copyOf(beforePrevCommas));
            e = brackets.applyBrackets.apply(bracketContent);
            if (e == null)
            {
                List<@Recorded Expression> possibles = new ArrayList<>();
                for (BracketAndNodes<Expression, ExpressionSaver, BracketContent> alternateBracket : brackets.alternateBrackets())
                {
                    @Nullable @Recorded Expression possible = alternateBracket.applyBrackets.apply(bracketContent);
                    if (possible != null)
                    {
                        possibles.add(possible);
                    }
                }
                if (!possibles.isEmpty())
                {
                    @Recorded Expression invalidOpExpression = brackets.applyBrackets.applySingle(collectedItems.makeInvalid(location, InvalidOperatorExpression::new));
                    locationRecorder.getRecorder().recordError(invalidOpExpression, StyledString.s("Surrounding brackets required"));
                    locationRecorder.getRecorder().<@Recorded Expression>recordQuickFixes(invalidOpExpression, Utility.<@Recorded Expression, QuickFix<@Recorded Expression>>mapList(possibles, fixed -> new QuickFix<@Recorded Expression>("fix.bracketAs", invalidOpExpression, () -> fixed)));
                    return invalidOpExpression;
                }
            }
        }
        
        if (e == null)
        {
            @Recorded Expression invalid = collectedItems.makeInvalid(location, InvalidOperatorExpression::new);
            e = brackets.applyBrackets.applySingle(invalid);
        }
        
        return e;
    }

    private BracketAndNodes<Expression, ExpressionSaver, BracketContent> unbracketed(List<@Recorded Expression> operands)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, Expression>()
        {
            @Nullable
            @Override
            public @Recorded Expression apply(@NonNull BracketContent items)
            {
                return null;
            }

            @NonNull
            @Override
            public @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, CanonicalSpan.fromTo(recorderFor(operands.get(0)), recorderFor(operands.get(operands.size() - 1))), ImmutableList.of());
    }

    @Override
    protected Expression opToInvalid(Op op)
    {
        return new InvalidIdentExpression(op.getContent());
    }

    @Override
    protected @Recorded Expression makeInvalidOp(CanonicalSpan location, ImmutableList<Either<OpAndNode, @Recorded Expression>> items)
    {
        InvalidOperatorExpression invalidOperatorExpression = new InvalidOperatorExpression(Utility.<Either<OpAndNode, @Recorded Expression>, @Recorded Expression>mapListI(items, x -> x.<@Recorded Expression>either(op -> locationRecorder.record(op.sourceNode, new InvalidIdentExpression(op.op.getContent())), y -> y)));
        return locationRecorder.record(location, invalidOperatorExpression);
    }

    @Override
    protected Expression keywordToInvalid(Keyword keyword)
    {
        return new InvalidIdentExpression(keyword.getContent());
    }

    private @Recorded Expression keywordToInvalid(Keyword keyword, CanonicalSpan location)
    {
        return locationRecorder.record(location, keywordToInvalid(keyword));
    }
    
    // Looks for a keyword, then takes the expression before the keyword and gives next step.
    private abstract class Choice
    {
        public final Keyword keyword;

        protected Choice(Keyword keyword)
        {
            this.keyword = keyword;
        }

        public abstract Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid);
    }
    
    // Looks for @case
    private class Case extends Choice
    {
        private final CanonicalSpan matchKeyword;
        // If null, we are the first case.  Otherwise we are a later case,
        // in which case we are Right with the given patterns
        private final @Nullable Pair<@Recorded Expression, ImmutableList<Pattern>> matchAndPatterns;
        // Previous complete clauses
        private final ImmutableList<MatchClause> previousClauses;
        
        // Looks for first @case after a @match
        public Case(CanonicalSpan matchKeyword)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = null;
            previousClauses = ImmutableList.of();
        }
        
        // Matches a later @case, meaning we follow a @then and an outcome
        public Case(CanonicalSpan matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> patternsForCur)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = new Pair<>(matchFrom, patternsForCur);
            this.previousClauses = previousClauses;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            final @Recorded Expression m;
            final ImmutableList<MatchClause> newClauses;
            // If we are first case, use the expression as the match expression:
            if (matchAndPatterns == null)
            {
                m = expressionBefore;
                newClauses = previousClauses;
            }
            else
            {
                // Otherwise this is the outcome for the most recent clause:
                m = matchAndPatterns.getFirst();
                ImmutableList<Pattern> patterns = matchAndPatterns.getSecond();
                newClauses = Utility.appendToList(previousClauses, new MatchClause(patterns, expressionBefore));
            }
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Then(matchKeyword, m, newClauses, ImmutableList.of(), Keyword.CASE),
                new Given(matchKeyword, m, newClauses, ImmutableList.of()),
                new OrCase(matchKeyword, m, newClauses, ImmutableList.of(), null)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @given to add a guard
    private class Given extends Choice
    {
        private final CanonicalSpan matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousCases;

        public Given(CanonicalSpan matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousCases)
        {
            super(Keyword.GIVEN);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            // Expression here is the pattern, which comes before the guard:
            return Either.right(expectOneOf(node, ImmutableList.of(
                new OrCase(matchKeyword, matchFrom, previousClauses, previousCases, expressionBefore),
                new Then(matchKeyword, matchFrom, previousClauses, Utility.appendToList(previousCases, new Pattern(expressionBefore, null)), Keyword.GIVEN)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @orcase
    private class OrCase extends Choice
    {
        private final CanonicalSpan matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousCases;
        private final @Nullable @Recorded Expression curMatch; // if null, nothing so far, if non-null we are a guard

        private OrCase(CanonicalSpan matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousCases, @Nullable @Recorded Expression curMatch)
        {
            super(Keyword.ORCASE);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
            this.curMatch = curMatch;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            ImmutableList<Pattern> newCases = Utility.appendToList(previousCases, curMatch == null ?
                // We are the pattern:
                new Pattern(expressionBefore, null) :
                // We are the guard:    
                new Pattern(curMatch, expressionBefore)
            );
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Given(matchKeyword, matchFrom, previousClauses, newCases),
                new OrCase(matchKeyword, matchFrom, previousClauses, newCases, null),
                new Then(matchKeyword, matchFrom, previousClauses, newCases, Keyword.ORCASE)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @then (in match expressions; if-then-else is handled separately) 
    private class Then extends Choice
    {
        private final CanonicalSpan matchKeywordNode;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousPatterns;
        private final Keyword precedingKeyword;
        

        // Preceding keyword may be CASE, GIVEN or ORCASE:
        private Then(CanonicalSpan matchKeywordNode, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousPatterns, Keyword precedingKeyword)
        {
            super(Keyword.THEN);
            this.matchKeywordNode = matchKeywordNode;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousPatterns = previousPatterns;
            this.precedingKeyword = precedingKeyword;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            final ImmutableList<Pattern> newPatterns;
            if (precedingKeyword == Keyword.GIVEN)
            {
                // The expression is a guard for the most recent pattern:
                newPatterns = Utility.appendToList(previousPatterns.subList(0, previousPatterns.size() - 1), new Pattern(previousPatterns.get(previousPatterns.size() - 1).getPattern(), expressionBefore));
            }
            else //if (precedingKeyword == Keyword.ORCASE || precedingKeyword == Keyword.CASE)
            {
                // The expression is a pattern:
                newPatterns = Utility.appendToList(previousPatterns, new Pattern(expressionBefore, null));
            }
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Case(matchKeywordNode, matchFrom, previousClauses, newPatterns),
                new Choice(Keyword.ENDMATCH) {
                    @Override
                    public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression lastExpression, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
                    {
                        MatchExpression matchExpression = new MatchExpression(matchFrom, Utility.appendToList(previousClauses, new MatchClause(newPatterns, lastExpression)));
                        return Either.<@Recorded Expression, Terminator>left(locationRecorder.record(CanonicalSpan.fromTo(matchKeywordNode, node), matchExpression));
                    }
                }
            ), prefixIfInvalid));
        }
    }

    // Looks for @define after initial
    private class FurtherDefine extends Choice
    {
        private final CanonicalSpan firstDefineKeyword;
        private final ImmutableList<Pair<@Recorded Expression, CanonicalSpan>> definesBeforeThis;
        
        // Looks for first further @define after an initial @define
        public FurtherDefine(CanonicalSpan firstDefine, ImmutableList<Pair<@Recorded Expression, CanonicalSpan>> definesBeforeThis)
        {
            super(Keyword.DEFINE);
            this.firstDefineKeyword = firstDefine;
            this.definesBeforeThis = definesBeforeThis;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            return Either.<@Recorded Expression, Terminator>right(expectOneOf(node, ImmutableList.<Choice>of(new FurtherDefine(firstDefineKeyword, Utility.<Pair<@Recorded Expression, CanonicalSpan>>appendToList(definesBeforeThis, new Pair<>(expressionBefore, node))), new DefineBody(makeDefines(expressionBefore), node)), Stream.<Supplier<@Recorded Expression>>concat(Stream.<Supplier<@Recorded Expression>>of(() -> keywordToInvalid(Keyword.DEFINE, firstDefineKeyword)), definesBeforeThis.stream().<Supplier<@Recorded Expression>>flatMap(p -> Stream.<Supplier<@Recorded Expression>>of(() -> p.getFirst(), () -> keywordToInvalid(Keyword.DEFINE, p.getSecond()))))));
        }

        private ImmutableList<Pair<CanonicalSpan, @Recorded Expression>> makeDefines(@Recorded Expression lastDefine)
        {
            ImmutableList.Builder<Pair<CanonicalSpan, @Recorded Expression>> r = ImmutableList.builder();
            CanonicalSpan lastDefineKW = firstDefineKeyword;
            for (Pair<@Recorded Expression, CanonicalSpan> def : definesBeforeThis)
            {
                r.add(new Pair<>(lastDefineKW, def.getFirst()));
                lastDefineKW = def.getSecond();
            }
            r.add(new Pair<>(lastDefineKW, lastDefine));
            return r.build();
        }
    }
    
    private class DefineBody extends Choice
    {
        private final ImmutableList<Pair<CanonicalSpan, @Recorded Expression>> defines;
        private final CanonicalSpan lastDefine;

        public DefineBody(ImmutableList<Pair<CanonicalSpan, @Recorded Expression>> defines, CanonicalSpan lastDefine)
        {
            super(Keyword.DEFINEBODY);
            this.defines = defines;
            this.lastDefine = lastDefine;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, CanonicalSpan start, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            return Either.<@Recorded Expression, Terminator>right(expect(ImmutableList.of(Keyword.ENDDEFINE), miscBracketsFrom(start), (e, s) -> Either.<@Recorded Expression, Terminator>left(defineOrInvalid(Utility.<Pair<CanonicalSpan, @Recorded Expression>>appendToList(defines, new Pair<>(lastDefine, expressionBefore)), start, e, new CanonicalSpan(defines.get(0).getFirst().start, start.end))), () -> {
                return Stream.<@Recorded Expression>concat(defines.stream().<@Recorded Expression>flatMap(p -> Stream.<@Recorded Expression>of(keywordToInvalid(Keyword.DEFINE, p.getFirst()), p.getSecond())), Stream.<@Recorded Expression>of(keywordToInvalid(Keyword.DEFINE, start), expressionBefore)).collect(ImmutableList.<@Recorded Expression>toImmutableList());
            }, null, false));
        }
    }

    private @Recorded Expression defineOrInvalid(ImmutableList<Pair<CanonicalSpan, @Recorded Expression>> defines, CanonicalSpan beginLocation, @Recorded Expression body, CanonicalSpan endLocation)
    {
        CanonicalSpan overallLocation = new CanonicalSpan(defines.get(0).getFirst().start, endLocation.end);
        if (defines.stream().allMatch(p -> p.getSecond() instanceof EqualExpression))
            return locationRecorder.record(overallLocation, new DefineExpression(Utility.<Pair<CanonicalSpan, @Recorded Expression>, @Recorded EqualExpression>mapListI(defines, p -> (EqualExpression)p.getSecond()), body));
        else
            return locationRecorder.record(overallLocation, new InvalidOperatorExpression(Stream.<@Recorded Expression>concat(defines.stream().<@Recorded Expression>flatMap(p -> Stream.<@Recorded Expression>of(keywordToInvalid(Keyword.DEFINE, p.getFirst()), p.getSecond())), Stream.<@Recorded Expression>of(keywordToInvalid(Keyword.DEFINEBODY, beginLocation), body, keywordToInvalid(Keyword.ENDDEFINE, endLocation))).collect(ImmutableList.<@Recorded Expression>toImmutableList())));
    }


    public Terminator expectOneOf(CanonicalSpan start, ImmutableList<Choice> choices, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
    {
        return new Terminator(choices.stream().map(c -> c.keyword.getContent()).collect(Collectors.joining(" or ")))
        {
        @Override
        public void terminate(FetchContent<Expression, ExpressionSaver, BracketContent> makeContent, @Nullable Keyword terminator, CanonicalSpan keywordErrorDisplayer)
        {
            BracketAndNodes<Expression, ExpressionSaver, BracketContent> brackets = miscBrackets(CanonicalSpan.fromTo(start, keywordErrorDisplayer));
            
            for (Choice choice : choices)
            {
                if (choice.keyword.equals(terminator))
                {
                    // All is well:
                    @Recorded Expression expressionBefore = makeContent.fetchContent(brackets);
                    Either<@Recorded Expression, Terminator> result = choice.foundKeyword(expressionBefore, keywordErrorDisplayer, Stream.<Supplier<@Recorded Expression>>concat(prefixIfInvalid, Stream.<Supplier<@Recorded Expression>>of(() -> expressionBefore, () -> record(keywordErrorDisplayer, new InvalidIdentExpression(choice.keyword.getContent())))));
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                    return;
                }
            }
            
            // Error!
            ImmutableList<TextQuickFix> fixes = Utility.mapListI(choices, c -> new TextQuickFix(StyledString.s("Add missing " + c.keyword.getContent()), ImmutableList.of(), keywordErrorDisplayer.lhs(), () -> new Pair<>(c.keyword.getContent(), StyledString.s(c.keyword.getContent()))));
            locationRecorder.addErrorAndFixes(keywordErrorDisplayer, StyledString.s("Missing " + choices.stream().map(e -> e.keyword.getContent()).collect(Collectors.joining(" or ")) + " before " + (terminator == null ? "end" : terminator.getContent())), fixes);
            // Important to call makeContent before adding to scope on the next line:
            ImmutableList.Builder<@Recorded Expression> items = ImmutableList.builder();
            items.addAll(prefixIfInvalid.<@Recorded Expression>map(s -> s.get()).collect(Collectors.<@Recorded Expression>toList()));
            items.add(makeContent.fetchContent(brackets));
            if (terminator != null)
                items.add(locationRecorder.record(keywordErrorDisplayer, new InvalidIdentExpression(terminator.getContent())));
            @Recorded InvalidOperatorExpression invalid = locationRecorder.<InvalidOperatorExpression>recordG(CanonicalSpan.fromTo(start, keywordErrorDisplayer), new InvalidOperatorExpression(items.build()));
            currentScopes.peek().items.add(Either.left(invalid));
        }};
    }


    @Override
    public void saveOperand(@UnknownIfRecorded Expression singleItem, CanonicalSpan location)
    {
        ArrayList<Either<@Recorded Expression, OpAndNode>> curItems = currentScopes.peek().items;
        if (singleItem instanceof UnitLiteralExpression && curItems.size() >= 1)
        {
            Either<@Recorded Expression, OpAndNode> recent = curItems.get(curItems.size() - 1);
            @Nullable @Recorded NumericLiteral num = recent.<@Nullable @Recorded NumericLiteral>either(e -> e instanceof NumericLiteral ? (NumericLiteral)e : null, o -> null);
            if (num != null && num.getUnitExpression() == null)
            {
                CanonicalSpan recorder = locationRecorder.recorderFor(num);
                curItems.set(curItems.size() - 1, Either.left(locationRecorder.record(CanonicalSpan.fromTo(recorder, location), new NumericLiteral(num.getNumber(), ((UnitLiteralExpression)singleItem).getUnit()))));
                return;
            }
        }
        
        super.saveOperand(singleItem, location);
    }

    @Override
    protected @Nullable Supplier<@Recorded Expression> canBeUnary(OpAndNode operator, @Recorded Expression followingOperand)
    {
        if (ImmutableList.of(Op.ADD, Op.SUBTRACT).contains(operator.op)
                && followingOperand instanceof NumericLiteral)
        {
            @Recorded NumericLiteral numericLiteral = (NumericLiteral) followingOperand;
            if (operator.op == Op.SUBTRACT)
                return () -> record(CanonicalSpan.fromTo(operator.sourceNode, locationRecorder.recorderFor(numericLiteral)), new NumericLiteral(Utility.negate(numericLiteral.getNumber()), numericLiteral.getUnitExpression()));
            else
                return () -> numericLiteral; // No change needed for unary plus
        }
        else
            return null;
    }
    
    /**
     * Get likely types and completions for given child.  For example,
     * if the expression is column Name = _ (where the RHS
     * is the child in question) we might offer Text and most frequent values
     * of the Name column.
     *
     * The completions are not meant to be all possible values of the given
     * type (e.g. literals, available columns, etc), as that can be figured out
     * from the type regardless of context.  This is only items which make
     * particular sense in this particular context, e.g. a commonly passed argument
     * to a function.
     */
    //List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException;
    
    // Remember: earlier means more likely to be inner-bracketed.  Outer list is groups of operators
    // with equal bracketing likelihood/precedence.
    @SuppressWarnings("recorded")
    final ImmutableList<ImmutableList<OperatorExpressionInfo>> OPERATORS = ImmutableList.<ImmutableList<OperatorExpressionInfo>>of(
        // Raise does come above arithmetic, because I think it is more likely that 1 * 2 ^ 3 is actually 1 * (2 ^ 3)
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.RAISE, (lhs, _n, rhs, _b, _e) -> new RaiseExpression(lhs, rhs))
        ),

        // Arithmetic operators are all one group.  I know we could separate +- from */, but if you see
        // an expression like 1 + 2 * 3, I'm not sure either bracketing is obviously more likely than the other.
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.ADD, Op.SUBTRACT), ExpressionSaver::makeAddSubtract),
            new OperatorExpressionInfo(ImmutableList.of(Op.MULTIPLY), ExpressionSaver::makeTimes),
            new OperatorExpressionInfo(Op.DIVIDE, (lhs, _n, rhs, _b, _e) -> new DivideExpression(lhs, rhs))
        ),

        // String concatenation lower than arithmetic.  If you write "val: (" ; 1 * 2; ")" then what you meant
        // is "val: (" ; to.string(1 * 2); ")" which requires an extra function call, but bracketing the arithmetic
        // will be the first step, and much more likely than ("val: (" ; 1) * (2; ")")
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.STRING_CONCAT), ExpressionSaver::makeStringConcat)
        ),

        // It's moot really whether this is before or after string concat, but feels odd putting them in same group:
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.PLUS_MINUS, (lhs, _n, rhs, _b, _e) -> new PlusMinusPatternExpression(lhs, rhs))
        ),

        // Equality and comparison operators:
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.EQUALS), ExpressionSaver::makeEqual),
            new OperatorExpressionInfo(Op.NOT_EQUAL, (lhs, _n, rhs, _b, _e) -> new NotEqualExpression(lhs, rhs)),
            new OperatorExpressionInfo(ImmutableList.of(Op.LESS_THAN, Op.LESS_THAN_OR_EQUAL), ExpressionSaver::makeComparisonLess),
            new OperatorExpressionInfo(ImmutableList.of(Op.GREATER_THAN, Op.GREATER_THAN_OR_EQUAL), ExpressionSaver::makeComparisonGreater)
        ),

        // Boolean and, or expressions come near-last.  If you see a = b & c = d, it's much more likely you wanted (a = b) & (c = d) than
        // a = (b & c) = d.
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(Op.AND), ExpressionSaver::makeAnd),
            new OperatorExpressionInfo(ImmutableList.of(Op.OR), ExpressionSaver::makeOr)
        ),

        // But the very last is the comma separator.  If you see (a & b, c | d), almost certain that you want a tuple
        // like that, rather than a & (b, c) | d.  Especially since tuples can't be fed to any binary operators besides comparison!
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(Op.COMMA, (lhs, _n, rhs, _b, _e) -> /* Dummy, see below: */ lhs)
            {
                @Override
                public OperatorSection makeOperatorSection(EditorLocationAndErrorRecorder locationRecorder, int operatorSetPrecedence, OpAndNode initialOperator, int initialIndex)
                {
                    return new NaryOperatorSection(locationRecorder, operators, operatorSetPrecedence, (args, _ops, brackets, edr) -> {
                        return brackets.applyBrackets.apply(new BracketContent(args));
                    }, initialIndex, initialOperator);

                }
            }
        )
    );

    private static Expression makeAddSubtract(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new AddSubtractExpression(args, Utility.mapList(ops, op -> op.getFirst().equals(Op.ADD) ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
    }

    private static Expression makeTimes(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new TimesExpression(args);
    }

    private static Expression makeStringConcat(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new StringConcatExpression(args);
    }

    private static Expression makeEqual(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new EqualExpression(args);
    }

    private static Expression makeComparisonLess(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.getFirst().equals(Op.LESS_THAN) ? ComparisonOperator.LESS_THAN : ComparisonOperator.LESS_THAN_OR_EQUAL_TO));
    }

    private static Expression makeComparisonGreater(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> ops)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.getFirst().equals(Op.GREATER_THAN) ? ComparisonOperator.GREATER_THAN : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO));
    }

    private static Expression makeAnd(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new AndExpression(args);
    }

    private static Expression makeOr(ImmutableList<@Recorded Expression> args, List<Pair<Op, CanonicalSpan>> _ops)
    {
        return new OrExpression(args);
    }

    @Override
    protected CanonicalSpan recorderFor(@Recorded Expression expression)
    {
        return locationRecorder.recorderFor(expression);
    }

    @Override
    protected @Recorded Expression record(CanonicalSpan location, Expression expression)
    {
        return locationRecorder.record(location, expression);
    }

    @Override
    protected Map<DataFormat, Object> toClipboard(@UnknownIfRecorded Expression expression)
    {
        return ImmutableMap.of(
            ExpressionEditor.EXPRESSION_CLIPBOARD_TYPE, expression.save(true, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY),
            DataFormat.PLAIN_TEXT, expression.save(false, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY)
        );
    }

    @Override
    protected ImmutableList<TextQuickFix> fixesForAdjacentOperands(@Recorded Expression first, @Recorded Expression second)
    {
        // We look for things where people have tried to use
        // C/Java style array indexing of lists.  Because it's invalid
        // we can't know types, so we use syntax.
        // For the list we look for @entire column references
        // (most likely item by far to be a list).
        if (first instanceof ColumnReference && second instanceof ArrayExpression)
        {
            ColumnReference columnReference = (ColumnReference) first;
            ArrayExpression arrayExpression = (ArrayExpression) second;
            
            if (columnReference.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN && arrayExpression.getElements().size() == 1)
            {
                CanonicalSpan location = new CanonicalSpan(locationRecorder.recorderFor(first).start, locationRecorder.recorderFor(second).end);
                
                return ImmutableList.of(new <Expression>TextQuickFix("fix.useElement",location, () -> {
                    Expression callElement = new CallExpression(functionLookup, "element", first, arrayExpression.getElements().get(0));
                    return new Pair<>(callElement.save(false, BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY), callElement.toStyledString());
                }));
            }
        }
        
        return super.fixesForAdjacentOperands(first, second);
    }

    public ImmutableMap<DisplayType, StyledString> getDisplayFor(@CanonicalLocation int canonIndex)
    {
        return locationRecorder.getDisplayFor(canonIndex);
    }

}
