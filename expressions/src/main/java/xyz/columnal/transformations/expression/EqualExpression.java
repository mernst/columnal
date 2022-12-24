/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeClassRequirements;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

/**
 * Created by neil on 30/11/2016.
 */
public class EqualExpression extends NaryOpShortCircuitExpression
{
    // Is the last item in the operators a pattern?  If so, last operator is =~ rather than =
    private final boolean lastIsPattern;
    
    public EqualExpression(List<Expression> operands, boolean lastIsPattern)
    {
        super(operands);
        this.lastIsPattern = lastIsPattern;
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new EqualExpression(replacements, lastIsPattern);
    }

    @Override
    protected String saveOp(int index)
    {
        if (lastIsPattern && index == expressions.size() - 2)
            return "=~";
        else
            return "=";
    }

    @Override
    public CheckedExp checkNaryOp(EqualExpression this, ColumnLookup dataLookup, TypeState typeState, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (lastIsPattern && expressions.size() > 2)
        {
            // If there is a pattern and an expression, we don't allow two expressions, because it's not
            // easily obvious to the user what the semantics should be e.g. 1 = @anything = 2, or particularly
            // (1, (? + 1)) = (1, @anything) = (1, (? + 2)).
            onError.recordError(this, StyledString.s("Cannot have a pattern in an equals expression with more than two operands."));
            // (This shouldn't really be reachable if all other systems are working right, but good as a sanity check)
            return null;
        }
        
        // The one to be returned, but not used for later operands:
        TypeState retTypeState = typeState;
        
        // The rule is that zero or one args can be a pattern, the rest must be expressions.
        // The type state must not carry over between operands, because otherwise you could write if ($s, $t) = (t, s) which doesn't pan out,
        // because all the non-patterns must be evaluated before the pattern.
        TypeExp type = new MutVar(this);
        List<Integer> invalidIndexes = new ArrayList<>();
        List<Optional<TypeExp>> expressionTypes = new ArrayList<>(expressions.size());
        for (int i = 0; i < expressions.size(); i++)
        {
            boolean invalid = false;
            Expression expression = expressions.get(i);
            CheckedExp checked = expression.check(dataLookup, typeState, (lastIsPattern && i == expressions.size() - 1) ? ExpressionKind.PATTERN : ExpressionKind.EXPRESSION, LocationInfo.UNIT_CONSTRAINED, onError);
            expressionTypes.add(Optional.ofNullable(checked).map(c -> c.typeExp));
            if (checked == null)
            {
                invalid = true;
            }
            else
            {
                checked.requireEquatable();
                if (!invalid && onError.recordError(this, TypeExp.unifyTypes(type, checked.typeExp)) == null)
                {
                    invalid = true;
                }
                
                if (lastIsPattern && i == expressions.size() - 1)
                    retTypeState = checked.typeState;
            }
            
            if (invalid)
            {
                invalidIndexes.add(i);
            }
        }
        if (!invalidIndexes.isEmpty())
        {
            for (Integer index : invalidIndexes)
            {
                TypeProblemDetails tpd = new TypeProblemDetails(ImmutableList.copyOf(expressionTypes), ImmutableList.copyOf(expressions), index);
                onError.recordQuickFixes(expressions.get(index), ExpressionUtil.getFixesForMatchingNumericUnits(typeState, tpd));
            }
            return null;
        }
        
        return onError.recordType(this, retTypeState, TypeExp.bool(this));
    }

    @Override
    public ValueResult getValueNaryOp(EvaluateState state) throws EvaluationException, InternalException
    {
        if (lastIsPattern)
        {
            if (expressions.size() != 2)
                throw new InternalException("Pattern present in equals despite having more than two operands");
            ImmutableList.Builder<ValueResult> lhsrhs = ImmutableList.builderWithExpectedSize(2);
            ValueResult value = fetchSubExpression(expressions.get(0), state, lhsrhs);
            ValueResult matchResult = matchSubExpressionAsPattern(expressions.get(1), value.value, state, lhsrhs);
            boolean matched = Utility.cast(matchResult.value, Boolean.class);
            return result(DataTypeUtility.value(matched), matched ? matchResult.evaluateState : state, ImmutableList.of(value, matchResult));
        }
        else
        {
            ImmutableList.Builder<ValueResult> values = ImmutableList.builderWithExpectedSize(expressions.size());
            Object first = fetchSubExpression(expressions.get(0), state, values).value;
            for (int i = 1; i < expressions.size(); i++)
            {
                Object rhsVal = fetchSubExpression(expressions.get(i), state, values).value;
                try
                {
                    if (0 != Utility.compareValues(first, rhsVal))
                    {
                        return result(DataTypeUtility.value(false), state, values.build());
                    }
                }
                catch (UserException e)
                {
                    throw new EvaluationException(e, this, ExecutionType.VALUE, state, values.build());
                }
            }

            return result(DataTypeUtility.value(true), state, values.build());
        }
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.equal(this, expressions, lastIsPattern);
    }
    
    public ImmutableList<Expression> getOperands()
    {
        return expressions;
    }
    
    public boolean lastIsPattern()
    {
        return lastIsPattern;
    }
}
