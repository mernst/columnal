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

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.NumberDisplayInfo;
import xyz.columnal.data.datatype.NumberDisplayInfo.Padding;
import xyz.columnal.data.unit.Unit;

import java.util.Objects;

/**
 * Created by neil on 30/10/2016.
 */
public class NumericColumnType extends ColumnType
{
    public final Unit unit;
    public final NumberDisplayInfo displayInfo;
    private final String commonPrefix;
    private final String commonSuffix;

    public NumericColumnType(Unit unit, int minDP, String commonPrefix, String commonSuffix)
    {
        this.unit = unit;
        this.displayInfo = new NumberDisplayInfo(minDP, 10, Padding.ZERO);
        this.commonPrefix = commonPrefix;
        this.commonSuffix = commonSuffix;
    }

    /**
     * Removes numeric prefix from the string, and gets rid of commas.
     */
    public String removePrefixAndSuffix(String val)
    {
        val = val.trim();
        if (commonPrefix != null)
            val = StringUtils.removeStart(val, commonPrefix);
        else if (val.startsWith(unit.getDisplayPrefix()))
            val = StringUtils.removeStart(val, unit.getDisplayPrefix());
        
        if (commonSuffix != null)
            val = StringUtils.removeEnd(val, commonSuffix);
        else if (val.endsWith(unit.getDisplaySuffix()))
            val = StringUtils.removeEnd(val, unit.getDisplaySuffix());
        
        val = val.trim().replace(",", "");
        return val;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericColumnType that = (NumericColumnType) o;

        if (!displayInfo.equals(that.displayInfo)) return false;
        if (!Objects.equals(commonPrefix, that.commonPrefix)) return false;
        if (!Objects.equals(commonSuffix, that.commonSuffix)) return false;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + displayInfo.hashCode();
        if (commonPrefix != null)
            result = 31 * result + commonPrefix.hashCode();
        if (commonSuffix != null)
            result = 31 * result + commonSuffix.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "Number{" +
            "unit=" + unit +
            ", displayInfo=" + displayInfo +
            ", commonPrefix='" + commonPrefix + '\'' +
            ", commonSuffix='" + commonSuffix + '\'' +
            '}';
    }
}
