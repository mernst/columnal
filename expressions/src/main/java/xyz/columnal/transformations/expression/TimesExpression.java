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
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 29/11/2016.
 */
public class TimesExpression extends NaryOpTotalExpression
{
    public TimesExpression(List<Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new TimesExpression(replacements);
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Rational running = Rational.ONE;
        for (Expression expression : expressions)
        {
            Optional<Rational> r = expression.constantFold();
            if (r.isPresent())
                running = running.times(r.get());
            else
                return Optional.empty();
        }
        return Optional.of(running);
    }

    @Override
    protected String saveOp(int index)
    {
        return "*";
    }

    @Override
    public CheckedExp checkNaryOp(TimesExpression this, ColumnLookup dataLookup, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        UnitExp runningUnit = UnitExp.SCALAR;
        for (Expression expression : expressions)
        {
            UnitExp unitVar = UnitExp.makeVariable();
            TypeExp expectedType = new NumTypeExp(this, unitVar);
            CheckedExp inferredType = expression.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_MODIFYING, onError);
            if (inferredType == null)
                return null;
            
            // This should unify our unitVar appropriately:
            if (onError.recordError(this, TypeExp.unifyTypes(expectedType, inferredType.typeExp)) == null)
                return null;
            
            runningUnit = runningUnit.times(unitVar);
        }
        return onError.recordType(this, state, new NumTypeExp(this, runningUnit));
    }

    @Override
    public ValueResult getValueNaryOp(ImmutableList<ValueResult> values, EvaluateState state) throws InternalException
    {
        Number n = Utility.cast(values.get(0).value, Number.class);
        for (int i = 1; i < expressions.size(); i++)
            n = Utility.multiplyNumbers(n, Utility.cast(values.get(i).value, Number.class));
        return result(n, state, values);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getNonNumericType()));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.multiply(this, expressions);
    }
}
