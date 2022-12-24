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

package xyz.columnal.gui.grid;

import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.error.InternalException;
import xyz.columnal.utility.Utility;

import java.util.Objects;
import java.util.Optional;

public class RectangleBounds
{
    public final CellPosition topLeftIncl;
    public final CellPosition bottomRightIncl;
    
    public RectangleBounds(CellPosition topLeftIncl, CellPosition bottomRightIncl)
    {
        if (bottomRightIncl.rowIndex < topLeftIncl.rowIndex || bottomRightIncl.columnIndex < topLeftIncl.columnIndex)
        {
            try
            {
                throw new InternalException("Bottom right " + bottomRightIncl + " is left/above the top left: " + topLeftIncl);
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
            // For safety:
            this.bottomRightIncl = new CellPosition(Utility.maxRow(topLeftIncl.rowIndex, bottomRightIncl.rowIndex), Utility.maxCol(topLeftIncl.columnIndex, bottomRightIncl.columnIndex));
            this.topLeftIncl = topLeftIncl;
        }
        else
        {
            this.topLeftIncl = topLeftIncl;
            this.bottomRightIncl = bottomRightIncl;
        }
    }

    public boolean contains(CellPosition cellPosition)
    {
        return topLeftIncl.columnIndex <= cellPosition.columnIndex && cellPosition.columnIndex <= bottomRightIncl.columnIndex
            && topLeftIncl.rowIndex <= cellPosition.rowIndex && cellPosition.rowIndex <= bottomRightIncl.rowIndex;
    }

    // Makes sure bottom right isn't left/above the top left
    public static RectangleBounds fixBottomRight(CellPosition topLeft, CellPosition bottomRight)
    {
        return new RectangleBounds(topLeft, new CellPosition(Utility.maxRow(topLeft.rowIndex, bottomRight.rowIndex), Utility.maxCol(topLeft.columnIndex, bottomRight.columnIndex)));
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RectangleBounds that = (RectangleBounds) o;
        return Objects.equals(topLeftIncl, that.topLeftIncl) &&
            Objects.equals(bottomRightIncl, that.bottomRightIncl);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(topLeftIncl, bottomRightIncl);
    }

    @Override
    public String toString()
    {
        return "[" + topLeftIncl + " - " + bottomRightIncl + "]";
    }

    public Optional<RectangleBounds> intersectWith(RectangleBounds rectangleBounds)
    {
        CellPosition topLeft = new CellPosition(
            Utility.maxRow(topLeftIncl.rowIndex, rectangleBounds.topLeftIncl.rowIndex),
            Utility.maxCol(topLeftIncl.columnIndex, rectangleBounds.topLeftIncl.columnIndex)
        );
        CellPosition bottomRight = new CellPosition(
            Utility.minRow(bottomRightIncl.rowIndex, rectangleBounds.bottomRightIncl.rowIndex),
            Utility.minCol(bottomRightIncl.columnIndex, rectangleBounds.bottomRightIncl.columnIndex)
        );
        
        return topLeft.rowIndex <= bottomRight.rowIndex && topLeft.columnIndex <= bottomRight.columnIndex ?
            Optional.of(new RectangleBounds(topLeft, bottomRight)) : Optional.empty();
    }
    
    public boolean touches(RectangleBounds rectangleBounds)
    {
        return intersectWith(rectangleBounds).isPresent();
    }
}
