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

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;

// A semantic error matches an expression which may span multiple children.
public final class CanonicalSpan implements Comparable<CanonicalSpan>
{
    // Start is inclusive char index, end is exclusive char index
    public final int start;
    public final int end;
    
    public CanonicalSpan(int start, int end)
    {
        this.start = start;
        this.end = end;
    }

    public static CanonicalSpan fromTo(CanonicalSpan start, CanonicalSpan end)
    {
        if (start.start <= end.end)
            return new CanonicalSpan(start.start, end.end);
        else
            return new CanonicalSpan(start.start, start.start);
    }
    
    public CanonicalSpan offsetBy(int offsetBy)
    {
        return new CanonicalSpan(start + offsetBy, end + offsetBy);
    }
  
    @SuppressWarnings("units")
    public static final CanonicalSpan START = new CanonicalSpan(0, 0);
    
    // Even though end is typically exclusive, this checks
    // if <= end because for errors etc we still want to display
    // if we touch the extremity.
    public boolean touches(int position)
    {
        return start <= position && position <= end;
    }

    @Override
    public String toString()
    {
        return "[" + start + "->" + end + "]";
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalSpan span = (CanonicalSpan) o;
        return start == span.start &&
                end == span.end;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(start, end);
    }

    @Override
    public int compareTo(CanonicalSpan o)
    {
        int c = Integer.compare(start, o.start);
        if (c != 0)
            return c;
        else
            return Integer.compare(end, o.end);
    }

    public CanonicalSpan lhs()
    {
        return new CanonicalSpan(start, start);
    }

    public CanonicalSpan rhs()
    {
        return new CanonicalSpan(end, end);
    }
}
