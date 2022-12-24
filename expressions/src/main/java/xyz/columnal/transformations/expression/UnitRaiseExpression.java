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

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;

public class UnitRaiseExpression extends UnitExpression
{
    private final UnitExpression unit;
    private final UnitExpression power;

    public UnitRaiseExpression(UnitExpression unit, UnitExpression power)
    {
        this.unit = unit;
        this.power = power;
    }

    @Override
    public JellyUnit asUnit(UnitRaiseExpression this, UnitManager unitManager) throws UnitLookupException
    {
        if (!(power instanceof UnitExpressionIntLiteral))
        {
            throw new UnitLookupException(StyledString.s("Units can only be raised to integer powers"), this, ImmutableList.of());
        }
        
        JellyUnit lhs = unit.asUnit(unitManager);

        return lhs.raiseBy(((UnitExpressionIntLiteral)power).getNumber());
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        return unit.save(saveDestination, false) + "^" + power.save(saveDestination, false);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitRaiseExpression that = (UnitRaiseExpression) o;

        if (!power.equals(that.power)) return false;
        return unit.equals(that.unit);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + power.hashCode();
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitRaiseExpression(unit.replaceSubExpression(toReplace, replaceWith), power.replaceSubExpression(toReplace, replaceWith));
    }
}
