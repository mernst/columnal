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

package xyz.columnal.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public abstract class ColumnType
{
    public static final BlankColumnType BLANK = BlankColumnType.INSTANCE;

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract String toString();
}
