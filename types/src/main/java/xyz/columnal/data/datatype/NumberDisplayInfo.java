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

package xyz.columnal.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Created by neil on 15/05/2017.
 */
public class NumberDisplayInfo
{
    public static enum Padding
    {
        ZERO, SPACE;
    }

    private final int minimumDP;
    private final int maximumDP;
    private final Padding rightPadding;

    public static final NumberDisplayInfo SYSTEMWIDE_DEFAULT = new NumberDisplayInfo(0, 3, Padding.SPACE);

    public NumberDisplayInfo(int minimumDP, int maximumDP, Padding rightPadding)
    {
        this.minimumDP = minimumDP;
        this.maximumDP = maximumDP;
        this.rightPadding = rightPadding;
    }

    public int getMinimumDP()
    {
        return minimumDP;
    }

    public int getMaximumDP()
    {
        return maximumDP;
    }

    public String getPaddingChar()
    {
        return rightPadding == Padding.ZERO ? "0" : " ";
    }

    public static NumberDisplayInfo merge(NumberDisplayInfo a, NumberDisplayInfo b)
    {
        if (a == null && b == null)
            return null;
        else if (a == null)
            return b;
        else if (b == null)
            return a;
        else
        {
            return new NumberDisplayInfo(Math.max(a.minimumDP, b.minimumDP), Math.max(a.maximumDP, b.maximumDP), a.rightPadding);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberDisplayInfo that = (NumberDisplayInfo) o;

        if (minimumDP != that.minimumDP) return false;
        if (maximumDP != that.maximumDP) return false;
        return rightPadding == that.rightPadding;
    }

    @Override
    public int hashCode()
    {
        int result = minimumDP;
        result = 31 * result + maximumDP;
        result = 31 * result + rightPadding.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "NumberDisplayInfo{" +
            "minimumDP=" + minimumDP +
            ", maximumDP=" + maximumDP +
            ", rightPadding=" + rightPadding +
            '}';
    }
}
