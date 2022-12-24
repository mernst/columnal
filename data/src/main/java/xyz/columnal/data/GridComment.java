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

package xyz.columnal.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table.Saver;
import xyz.columnal.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.SaveTag;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GridComment
{
    private final SaveTag saveTag;
    // Note that the fields below are all mutable;
            
    // No escapes
    private String content;
    private CellPosition position;
    private int widthInColumns;
    private int heightInColumns;
    
    private GridCommentDisplayBase display;
    
    //@OnThread(value = Tag.Any, requireSynchronized = true)
    //private @MonotonicNonNull GridCommentDisplayBase display;


    public GridComment(SaveTag saveTag, String content, CellPosition position, int widthInColumns, int heightInColumns)
    {
        this.saveTag = saveTag;
        this.content = content;
        this.position = position;
        this.widthInColumns = widthInColumns;
        this.heightInColumns = heightInColumns;
    }

    public void save(Saver saver)
    {
        String commentSrc;
        synchronized (this)
        {
            String p = saveTag.getTag();
            commentSrc = "COMMENT @BEGIN " + p + "\n" + Stream.of(
                "CONTENT @BEGIN",
                GrammarUtility.escapeChars(content),
                "@END CONTENT",
                "DISPLAY @BEGIN",
                "POSITION " + IntStream.of(position.columnIndex, position.rowIndex, widthInColumns, heightInColumns).mapToObj(Integer::toString).collect(Collectors.joining(" ")),
                "@END DISPLAY").map(s -> p + " " + s).collect(Collectors.joining("\n")) + "\n@END " + p + " COMMENT";
        }
        saver.saveComment(commentSrc);
    }

    public synchronized String getContent()
    {
        return content;
    }

    public synchronized void setContent(String content)
    {
        this.content = content;
    }

    public GridCommentDisplayBase getDisplay()
    {
        return display;
    }
    
    public void setDisplay(GridCommentDisplayBase display)
    {
        this.display = display;
    }

    public synchronized CellPosition getPosition()
    {
        return position;
    }

    public synchronized CellPosition getBottomRight()
    {
        return position.offsetByRowCols(heightInColumns - 1, widthInColumns - 1);
    }

    public synchronized void setBottomRight(CellPosition bottomRight)
    {
        this.widthInColumns = Math.max(1, bottomRight.columnIndex - getPosition().columnIndex + 1);
        this.heightInColumns = Math.max(1, bottomRight.rowIndex - getPosition().rowIndex + 1);
    }

    /**
     * Slightly ham-fisted way to break the data->gui module dependency
     * while still letting Table store a link to its display.
     */
    public static interface GridCommentDisplayBase
    {
        
    }

    @Override
    public synchronized boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GridComment that = (GridComment) o;
        return widthInColumns == that.widthInColumns &&
                heightInColumns == that.heightInColumns &&
                content.equals(that.content) &&
                position.equals(that.position);
    }

    @Override
    public synchronized int hashCode()
    {
        return Objects.hash(content, position, widthInColumns, heightInColumns);
    }
}
